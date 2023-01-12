(def version "0.4.4")

(defproject org.clojars.huahaiy/symspell-clj version
  :description "SymSpell spell checker in Clojure"
  :url "https://github.com/juji-io/symspell-clj"
  :dependencies [[org.clojure/clojure "1.11.1" :scope "provided"]]
  :scm {:name "git" :url "https://github.com/juji-io/symspell-clj"}
  :java-source-paths ["src/java"]
  :deploy-repositories [["clojars" {:url           "https://repo.clojars.org"
                                    :username      :env/clojars_username
                                    :password      :env/clojars_password
                                    :sign-releases false}]]
  )
