(ns symspell-clj.core
  "SymSpell Spell checker"
  (:require [clojure.java.io :as io]
            [clojure.string :as s])
  (:import [symspell Bigram SymSpell Verbosity SuggestItem]
           [java.util HashMap]
           [java.util.concurrent ConcurrentHashMap]))

(defn- add-to-trie
  [trie s]
  (assoc-in trie s (merge (get-in trie s) {:end true})))

(defn- match-prefix
  [trie s]
  (when-not (s/blank? s)
    (loop [t trie ks (seq s)]
      (if ks
        (let [k (first ks)]
          (if-let [t (t k)]
            (recur t (next ks))
            false))
        true))))

(defprotocol ISpellChecker
  (add-word [this word]
    "Add a word to the spell checker at run time. It is responsibility of user
     to persist the added words, pass them to `:custom-dictionary` when the
     spellchcker is initialized next time.")
  (match-prefix? [this input]
    "Return true if the input matches a prefix in the dictionary")
  (lookup [this input] [this input opts]
    "Return suggested terms for a single word input, along with the edit distance
     for each. Possible option map keys are:
     * `:verbosity`, a keyword value, with possible values:
        - `:all`, return all possible suggestions
        - `:closest`, return the closest (w/ minimal edit distance) suggestions
        - `:top`, return the top suggestion
     * `:threshold`, edit distance threshold, default is 2. This must be no larger
       than `max-edit-distance` of the spell checker
     * `:include-unknown?`, whether to include unknown word in suggestion")
  (lookup-compound [this input] [this input opts]
    "Return suggestions for multi-word input, along with the edit distance for each.
     Option map keys may include:
     * `:threshold`, edit distance threshold per word, default is 2. This must be
       no larger than `max-edit-distance` of the spell checker
     * `:include-unknown?`, whether to include unknown word in suggestion")
  (get-suggestion [this input]
    "Return a single spell-checked input. This is a user level function that tries
     to do the sensible things, such as preserving the cases, dealing with punctuation
     and numbers."))

(def ^:no-doc key->verbosity {:all     Verbosity/ALL
                              :closest Verbosity/CLOSEST
                              :top     Verbosity/TOP})

(def ^:no-doc custom-word-default-freq 100000)

(defn- punc-split
  [input]
  (re-seq
    #"[-;\\,\\!\"\\#\\$%\\&\\(\\)\\*\\+\\:\\/\\<\\.\\=\\>\\?@\\[\\]\\\\\\^_`\\{\\|\\}~]|[a-zA-Z0-9' ]+"
    input) )

(defn- email? [s] (re-find #"^[a-zA-Z0-9_!#$%&'*+/=?`{|}~^.-]+@[a-zA-Z0-9.-]+$" s))

(defn- non-word?
  [input]
  (re-find
    #"[0-9-;\\,\\!\"\\#\\$%\\&\\(\\)\\*\\+\\:\\/\\<\\.\\=\\>\\?@\\[\\]\\\\\\^_`\\{\\|\\}~]"
    input))

(defn- breakup
  "break up a consecutive seq of non-space characters into pieces, unless it
  should be kept whole, e.g. email"
  [s]
  (cond
    (email? s) [s]
    :else      (punc-split s)))

(defn- trim-space
  "separate preceding and/or trailing space from the word"
  [s]
  (let [space-start? (s/starts-with? s " ")
        space-end?   (s/ends-with? s " ")
        len          (.length s)]
    (cond
      (= " " s)                     [s]
      (and space-start? space-end?) [" " (subs s 1 (dec len)) " "]
      space-start?                  [" " (subs s 1)]
      space-end?                    [(subs s 0 (dec len)) " "]
      :else                         [s])))

(defn- split-parts
  "Split input into parts that can be independently spell-checked, trying to
  make each part as large as possible"
  [input]
  (into []
        (comp (mapcat breakup)
           (partition-by #(if (non-word? %) true false))
           (map s/join)
           (mapcat trim-space))
        (s/split input #"((?<= )|(?= ))")))

(defn- captialized-input? [input] (Character/isUpperCase (first input)))

(defn- all-cap-input? [input] (every? #(Character/isUpperCase %) input))

(defn- normalize-suggestion
  [^SuggestItem si all-cap? capitalized?]
  (let [suggestion (.getSuggestion si)]
    (cond
      all-cap?     (s/upper-case suggestion)
      capitalized? (s/capitalize suggestion)
      :else        suggestion)))

(declare lookup lookup-compound)

(defn- spell-check
  [spell-checker input]
  (cond
    (= " " input)     input
    (non-word? input) input
    :else             (let [capitalized? (captialized-input? input)
                            all-cap?     (all-cap-input? input)
                            input        (s/lower-case input)]
                        (normalize-suggestion
                          (first (if (re-find #" " input)
                                   (lookup-compound spell-checker input)
                                   (lookup spell-checker input)))
                          all-cap? capitalized?))))

(deftype SpellChecker [^SymSpell symspell
                       ^:volatile-mutable prefix-trie]
  ISpellChecker
  (add-word [_ word]
    (let [word (s/lower-case word)]
      (set! prefix-trie (add-to-trie prefix-trie word))
      (.addUnigrams symspell (doto (HashMap.)
                               (.put word custom-word-default-freq)))))

  (match-prefix? [_ input]
    (match-prefix prefix-trie input))

  (lookup [this input]
    (.lookup this input {}))
  (lookup [_ input {:keys [verbosity threshold include-unknown?]
                    :or   {verbosity        :closest
                           threshold        2
                           include-unknown? false}}]
    (.lookup symspell input (key->verbosity verbosity) threshold
             include-unknown?))

  (lookup-compound [this input]
    (.lookup-compound this input {}))
  (lookup-compound [_ input {:keys [threshold include-unknown?]
                             :or   {threshold        2
                                    include-unknown? false}}]
    (.lookupCompound symspell input threshold include-unknown?))

  (get-suggestion [this input]
    (->> input
         split-parts
         (map #(spell-check this %))
         flatten
         s/join)))

(defn- read-unigram
  [file]
  (let [m (ConcurrentHashMap.)]
    (with-open [rdr (io/reader (io/resource file))
                ;; for creating new-unigram.txt
                ;; wrt (io/writer "new-unigram.txt")
                ]
      (doseq [line (line-seq rdr)]
        (let [[token freq] (s/split line #" ")
              token        (s/trim token)]
          (.put m token
                (if freq (Long/parseLong freq) custom-word-default-freq))
          ;; for creating new-unigram.txt
          #_(when (re-matches #"^[a-zA-Z']+" token)
              (when-not (.get m token)
                (.write wrt token)
                (when freq (.write wrt (str " " freq)))
                (.write wrt "\n")
                (.put m token
                      (if freq (Long/parseLong freq) custom-word-default-freq)))))))
    m))

;; (read-unigram "en_unigrams.txt")

(defn- read-bigram
  [file]
  (let [m (ConcurrentHashMap.)]
    (with-open [rdr (io/reader (io/resource file))]
      (doseq [line (line-seq rdr)]
        (let [[t1 t2 freq] (s/split line #" ")]
          (.put m (Bigram. t1 t2) (Long/parseLong freq)))))
    m))

(defn new-spellchecker
  "Create a spell checker.
   Unigram file is relative to the classpath. The file should be plain text,
   UTF-8 encoded without BOM. Each line contains a word and an optional frequency
   with a space in between. The default file is a built-in English dictionary.
   Bigram file is similar, but with two words instead, Bigram file is optional.
   Possible option map keys are
   * `:max-edit-distance` is the maximum possible edit distance considered by
    this spell checker, default 2.
   * `:prefix-length` is the length of prefix considered by this spell checker,
    default 10.
   * `:custom-dictionary` is a vector of additional words for the dictionary."
  ([]
   (new-spellchecker "en_unigrams.txt" "en_bigrams.txt" {}))
  ([unigram-file]
   (new-spellchecker unigram-file nil {}))
  ([unigram-file bigram-file]
   (new-spellchecker unigram-file bigram-file {}))
  ([unigram-file bigram-file {:keys [max-edit-distance
                                     prefix-length
                                     custom-dictionary]
                              :or   {max-edit-distance 2
                                     prefix-length     10
                                     custom-dictionary []}}]
   (let [unigram-file (or unigram-file "en_unigrams.txt")
         bigram-file  (or bigram-file "en_bigrams.txt")
         unigram      (read-unigram unigram-file)]
     (doseq [w custom-dictionary]
       (.put unigram (s/lower-case w) custom-word-default-freq))
     (->SpellChecker
       (SymSpell. unigram
                  (read-bigram bigram-file)
                  max-edit-distance
                  prefix-length)
       (reduce add-to-trie {} (.keySet unigram))))))

(comment

  (def sc (time (new-spellchecker)))

  (spell-check sc "wht")
  (spell-check sc "Wht")
  (spell-check sc "WHT")
  (spell-check sc "WHT is it")
  (spell-check sc
               "whereis th elove hehad dated forImuch of thepast who couqdn'tread in sixtgrade and ins pired him")

  (->> "Tom li-yang's hp0 got 123."
       split-parts
       (map #(spell-check sc %))
       ;; flatten
       ;; s/join
       )

  (count (time (lookup sc "xel," {:include-unknown? true})))
  (count (time (lookup sc "xel" {:verbosity :all :include-unknown? true})))
  (count (time (lookup sc "xel" {:verbosity :top :include-unknown? true})))
  (time (lookup-compound sc "whereis th elove hehad dated forImuch of thepast who couqdn'tread in sixtgrade and ins pired him"))

  (def sm (.-symspell sc))
  (.size (.getDeletes sm))
  (.size (.getUnigramLexicon sm))
  (.size (.getBigramLexicon sm))




  )
