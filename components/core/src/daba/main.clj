(ns daba.main
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [daba.api :as api]
   [io.github.dundalek.daba.app :as app]))

(defn escape-sql-string [s]
  (str "'" (str/replace s "'" "''") "'"))

(defn -main [& args]
  (api/open)
  (when-some [path (first args)]
    (cond
      (not (.isFile (io/file path)))
      (api/inspect path)

      (str/ends-with? path ".csv")
      (app/query-editor {:dsid {:jdbcUrl "jdbc:duckdb:"}
                         :statement (format "SELECT * FROM read_csv(%s);" (escape-sql-string path))})

      (str/ends-with? path ".duckdb")
      (api/inspect (str "duckdb://" path))

      :else
      (api/inspect (str "sqlite://" path)))))
