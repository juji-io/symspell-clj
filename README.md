# symspell-clj

[![Clojars Project](https://img.shields.io/clojars/v/org.clojars.huahaiy/symspell-clj.svg?color=success)](https://clojars.org/org.clojars.huahaiy/symspell-clj)
[![cljdoc badge](https://cljdoc.org/badge/org.clojars.huahaiy/symspell-clj)](https://cljdoc.org/d/org.clojars.huahaiy/symspell-clj)


SymSpell spell checker for Clojure.

SymSpell works by generating delete-only candidates and indexing them, so the
searching and fuzzy matching are extremely fast.

This is based on a [Java port](https://github.com/rxp90/jsymspell) of the
[SymSpell](https://github.com/wolfgarbe/SymSpell) spell checker. A few
convenient functions are also provided.

## Usage

```clojure
(require '[symspell-clj.core :as sp])

(def sc (sp/new-spellchecker))

;; get a suggested corrected string
(sp/get-suggestion sc "Wht")
;;==> "What"

;; can do multiple words
(sp/get-suggestion sc "Wht is tht?")
;;==> "What is that?"

;; also does word segmentation
(sp/get-suggestion sc "whereis th elove hehad dated forImuch of thepast who couqdn'tread in sixtgrade and ins pired him")
;;==> "where is the love he had dated for much of the past who couldn't read in sixth grade and inspired him"

```

## Status

SymSpell is widely used and ported to many languages. This Clojure library is new, but is already used in production at [Juji](https://juji.io).
