(ns symspell-clj.core
  "Spell checker"
  (:require [clojure.java.io :as io]
            [clojure.string :as s])
  (:import [symspell SymSpell SuggestItem]))

(defprotocol ISpellChecker
  (suggest [this tokens] "return suggested terms"))

(deftype SpellChecker [^SymSpell symspell
                       unigram-file
                       bigram-file])

(defn- read-unigram
  "unigram file is relative to the classpath. The file should be plain text,
  without BOM. Each line contains a word, a space, then followed by its
  frequency"
  [file]
  (let [m (transient {})]
    (with-open [rdr (io/reader (io/resource file))]
      (doseq [line (line-seq rdr)]
        (let [[token freq] (s/split line #" ")]
          (assoc! m (s/trim token) (Long/parseLong freq)))))
    (persistent! m)))
(read-unigram "en_unigrams.txt")

(defn new-spellchecker
  ([])
  ([unigram-file])
  ([unigram-file bigram-file]
   (let [unigrams (read-unigram unigram-file)])))
