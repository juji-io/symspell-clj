(ns symspell-clj.core
  "SymSpell Spell checker"
  (:require [clojure.java.io :as io]
            [clojure.string :as s])
  (:import [symspell Bigram SymSpell Verbosity SuggestItem]
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
  (match-prefix? [this input] "return true if input matches a prefix in dictionary")
  (suggest [this input distance-threshold] "return suggested terms"))

(deftype SpellChecker [^SymSpell symspell
                       prefix-trie
                       unigram-file
                       bigram-file]
  ISpellChecker
  (match-prefix? [_ input]
    (match-prefix prefix-trie input))
  (suggest [_ input distance-threshold]
    ;; (.lookup symspell input Verbosity/ALL false)
    ;; (.lookup symspell input Verbosity/CLOSEST false)
    (.lookupCompound symspell input distance-threshold false)
    ))

(defn- read-unigram
  "unigram file is relative to the classpath. The file should be plain text,
  without BOM. Each line contains a word, a space, then followed by its
  frequency"
  [file]
  (let [m (ConcurrentHashMap.)]
    (with-open [rdr (io/reader (io/resource file))]
      (doseq [line (line-seq rdr)]
        (let [[token freq] (s/split line #" ")]
          (.put m (s/trim token) (Long/parseLong freq)))))
    m))

(defn- read-bigram
  [file]
  (let [m (ConcurrentHashMap.)]
    (with-open [rdr (io/reader (io/resource file))]
      (doseq [line (line-seq rdr)]
        (let [[t1 t2 freq] (s/split line #" ")]
          (.put m (Bigram. t1 t2) (Long/parseLong freq)))))
    m))

(defn new-spellchecker
  ([]
   (new-spellchecker "en_unigrams.txt" "en_bigrams.txt" {}))
  ([unigram-file]
   (new-spellchecker unigram-file nil {}))
  ([unigram-file bigram-file]
   (new-spellchecker unigram-file bigram-file {}))
  ([unigram-file bigram-file {:keys [max-edit-distance
                                     prefix-length]
                              :or   {max-edit-distance 2
                                     prefix-length     10}}]
   (let [unigram (read-unigram unigram-file)]
     (->SpellChecker
       (SymSpell. unigram
                  (read-bigram bigram-file)
                  max-edit-distance
                  prefix-length)
       (reduce add-to-trie {} (.keySet unigram))
       unigram-file
       bigram-file))))

(comment

  (def sc (time (new-spellchecker)))

  (time (suggest sc "hel" 2))
  (time (suggest sc "whereis th elove hehad dated forImuch of thepast who couqdn'tread in sixtgrade and ins pired him" 2))

  (def sm (.-symspell sc))
  (.size (.getDeletes sm))
  (.size (.getUnigramLexicon sm))
  (.size (.getBigramLexicon sm))


  (def ug (.getUnigramLexicon sm))

  (.lookup sm "hel" Verbosity/ALL 2 false)

  (.containsKey ug "hel")

  (def ed (DamerauLevenshteinOSA.))

  (.distanceWithEarlyStop ed "hel" "hello" 2)

  )
