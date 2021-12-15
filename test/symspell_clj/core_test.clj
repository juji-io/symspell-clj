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

    (is (not (sut/match-prefix? sc "%")))
    (is (not (sut/match-prefix? sc "%a")))
    ))

(deftest lookup-test
  (let [sc (sut/new-spellchecker)]
    (is (= (sut/lookup sc "Hel") [["Hel" 0]]))
    (is (= (sut/lookup sc "Hel" {:verbosity :top}) [["Hel" 0]]))
    (is (= (count (sut/lookup sc "Hel" {:verbosity :all})) 480))

    (is (= (count (sut/lookup sc "xel")) 9))
    (is (= (count (sut/lookup sc "xel" {:verbosity :all})) 334))
    (is (= (take 9 (sut/lookup sc "xel" {:verbosity :top}))
           (sut/lookup sc "xel")))

    (is (= (sut/lookup sc "juji") [["fuji" 1]]))
    (sut/add-word sc "juji")
    (is (= (sut/lookup sc "juji") [["juji" 0]]))
    ))

(deftest lookup-compound-test
  (let [sc (sut/new-spellchecker)]
    (is (= (sut/lookup-compound sc "whereis th elove hehad dated forImuch of thepast who couqdn'tread in sixtgrade and ins pired him")
           [["where is the love he had dated for much of the past who couldn't read in sixth grade and inspired him" 10]]))

    (is (= (sut/lookup-compound sc "wht is juji")
           [["what is fuji" 2]]))
    (sut/add-word sc "juji")
    (is (= (sut/lookup-compound sc "wht is juji")
           [["what is juji" 1]]))
    ))
