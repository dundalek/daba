(ns io.github.dundalek.daba.lib.drivers
  (:require
   [clojure.repl.deps :as deps]
   [clojure.string :as str]
   [next.jdbc :as jdbc]
   [next.jdbc.connection :as connection]))

;; dbtypes defined in https://github.com/seancorfield/next-jdbc/blob/f03b1ba3168168d1cad2580a42fcf4b604310c49/src/next/jdbc/connection.clj#L69
(def libs
  '{"duckdb" {org.duckdb/duckdb_jdbc {:mvn/version "0.10.2"}}
    "h2" {com.h2database/h2 {:mvn/version "2.2.224"}}
    "postgresql" {org.postgresql/postgresql {:mvn/version "42.7.3"}}
    "sqlite" {org.xerial/sqlite-jdbc {:mvn/version "3.43.0.0"}}})

(defn db-spec->dbtype [db-spec]
  (let [normalized-db-spec
        (cond
          (string? db-spec) (connection/uri->db-spec db-spec)

          (and (map? db-spec) (string? (:jdbcUrl db-spec)))
          (connection/uri->db-spec (:jdbcUrl db-spec))

          (map? db-spec) db-spec)]
    (:dbtype normalized-db-spec)))

(defn ensure-loaded! [db-spec]
  (try
    (with-open [_con (jdbc/get-connection db-spec)])
    (catch java.sql.SQLException e
      (if-not (str/starts-with? (ex-message e) "No suitable driver found")
        (throw e)
        (let [dbtype (->> (db-spec->dbtype db-spec))]
          (when-some [coords (get libs dbtype)]
            (binding [*repl* true]
              (deps/add-libs coords))))))))
