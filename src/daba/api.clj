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

(defn inspect [db-spec]
  @!ensure-next-jdbc
  (when (and (string? db-spec)
             (str/starts-with? db-spec "jdbc:sqlite:"))
    @!ensure-sqlite-jdbc)
  ((requiring-resolve 'daba.internal/inspect) db-spec))

(comment
  (inspect "jdbc:sqlite:tmp/Chinook_Sqlite_AutoIncrementPKs.sqlite")

  (require '[portal.api :as p])

  (def p (p/open))
  (p/clear)
  (p/close)

  (add-tap #'p/submit)
  (remove-tap #'p/submit)
  (tap> :hello)
  (prn @p)

  (p/docs))
