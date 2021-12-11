(ns symspell-clj.core
  "SymSpell Spell checker"
  (:require [clojure.java.io :as io]
            [clojure.string :as s])
  (:import [symspell Bigram SymSpell Verbosity SuggestItem
            DamerauLevenshteinOSA]
           [java.util.concurrent ConcurrentHashMap]))

(defprotocol ISpellChecker
  (suggest [this tokens distance-threshold] "return suggested terms"))

(deftype SpellChecker [^SymSpell symspell
                       unigram-file
                       bigram-file]
  ISpellChecker
  (suggest [_ input distance-threshold]
    (.lookup symspell input Verbosity/ALL false)
    ;; (.lookupCompound symspell input distance-threshold false)
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
   (->SpellChecker
     (SymSpell. (read-unigram unigram-file)
                (read-bigram bigram-file)
                max-edit-distance
                prefix-length)
     unigram-file
     bigram-file)))

(comment

  (def sc (time (new-spellchecker)))
  (suggest sc "hel" 2)
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
