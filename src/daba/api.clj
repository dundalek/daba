(ns daba.api
  (:require
   [clojure.repl.deps :as deps]
   [clojure.string :as str]))

(def !ensure-next-jdbc
  (delay
    (deps/add-libs
     '{com.github.seancorfield/next.jdbc {:mvn/version "1.3.925"}})))

(def !ensure-sqlite-jdbc
  (delay
    (deps/add-libs
     '{org.xerial/sqlite-jdbc {:mvn/version "3.43.0.0"}})))

(def !ensure-postresql-jdbc
  (delay
    (deps/add-libs
     '{org.postgresql/postgresql {:mvn/version "42.7.3"}})))

(defn inspect [db-spec]
  (let [db-spec (if (and (string? db-spec) (not (str/starts-with? db-spec "jdbc:")))
                  (str "jdbc:" db-spec)
                  db-spec)]
    @!ensure-next-jdbc
    (when (and (string? db-spec) (str/starts-with? db-spec "jdbc:sqlite:"))
      @!ensure-sqlite-jdbc)
    (when (and (string? db-spec) (str/starts-with? db-spec "jdbc:postgresql:"))
      @!ensure-postresql-jdbc)
    ((requiring-resolve 'daba.internal/inspect-database) db-spec)))

(comment
  (inspect "jdbc:sqlite:tmp/Chinook_Sqlite_AutoIncrementPKs.sqlite")

  (inspect (System/getenv "POSTGRES_URI"))

  (require '[portal.api :as p])

  (def p (p/open))
  (p/clear)
  (p/close)

  (add-tap #'p/submit)
  (remove-tap #'p/submit)
  (tap> :hello)
  (prn @p)

  (p/docs))
