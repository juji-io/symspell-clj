(ns symspell-clj.core-test
  (:require [symspell-clj.core :as sut]
            [clojure.test :refer [is deftest]]))

(deftest match-prefix-test
  (let [sc (sut/new-spellchecker)]
    (is (sut/match-prefix? sc "h"))
    (is (sut/match-prefix? sc "hw"))
    (is (sut/match-prefix? sc "hwy"))
    (is (not (sut/match-prefix? sc "hwyi")))
    (is (sut/match-prefix? sc "x"))
    (is (not (sut/match-prefix? sc "xw")))
    (is (not (sut/match-prefix? sc "xww")))
    ))
