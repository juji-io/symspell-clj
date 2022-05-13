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
    (is (not (sut/match-prefix? sc "xg")))
    (is (not (sut/match-prefix? sc "xgg")))

    (is (not (sut/match-prefix? sc "%")))
    (is (not (sut/match-prefix? sc "%a")))))

(deftest custom-dict-test
  (let [sc (sut/new-spellchecker nil nil {:custom-dictionary ["juji"]})]
    (is (= "juji" (sut/get-suggestion sc "juji")))))

(deftest get-suggestion-test
  (let [sc (sut/new-spellchecker)]
    (is (= (sut/get-suggestion sc "Wht is juji?") "What is fuji?"))
    (is (= (sut/get-suggestion sc "OK!") "OK!"))
    (is (= (sut/get-suggestion sc "Wht is tht?") "What is that?"))

    (is (= (sut/get-suggestion sc "Tom li-yang's hp0 got 123.")
           "Tom li-yang's hp0 got 123."))
    (is (= (sut/get-suggestion sc "OK,  here's my email: hyang@eab.edu")
           "OK, here's my email: hyang@eab.edu"))

    (is (= "fuji" (sut/get-suggestion sc "juji")))
    (is (= "what is fuji" (sut/get-suggestion sc "wht is juji")))
    (sut/add-word sc "juji")
    (is (= "juji" (sut/get-suggestion sc "juji")))
    (is (= "what is juji" (sut/get-suggestion sc "wht is juji")))

    (is (= "where is the love he had dated for much of the past who couldn't read in sixth grade and inspired him"
           (sut/get-suggestion
             sc
             "whereis th elove hehad dated forImuch of thepast who couqdn'tread in sixtgrade and ins pired him"
             )))

    ))
