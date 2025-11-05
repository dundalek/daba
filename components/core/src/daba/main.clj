(ns daba.main
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [daba.api :as api]
   [next.jdbc :as jdbc]))

(defonce !file-connections (atom {}))

(defn- derive-table-name [file-path]
  (-> (io/file file-path)
      (.getName)
      (str/replace #"\.[^.]+$" "")
      (str/replace #"[^a-zA-Z0-9_]" "_")))

(defn- load-file-to-duckdb [file-path reader-fn]
  (let [db-name (str "file_" (System/currentTimeMillis))
        dsid (str "jdbc:duckdb::memory:" db-name)
        table-name (derive-table-name file-path)
        create-stmt (format "CREATE TABLE %s AS SELECT * FROM %s(?);" table-name reader-fn)
        conn (jdbc/get-connection (jdbc/get-datasource dsid))]
    (jdbc/execute! conn [create-stmt file-path])
    ;; We have to keep connection open so that temporary table does not disappear.
    ;; TODO: Should implement better connection management - connecting/disconnecting.
    (swap! !file-connections assoc dsid conn)
    dsid))

(defn load-jsonl-to-duckdb [jsonl-path]
  (load-file-to-duckdb jsonl-path "read_json_auto"))

(defn load-csv-to-duckdb [csv-path]
  (load-file-to-duckdb csv-path "read_csv_auto"))

(defn -main [& args]
  (api/open)
  (when-some [path (first args)]
    (cond
      (not (.isFile (io/file path)))
      (api/inspect path)

      (str/ends-with? path ".csv")
      (api/inspect (load-csv-to-duckdb path))

      (str/ends-with? path ".jsonl")
      (api/inspect (load-jsonl-to-duckdb path))

      (str/ends-with? path ".duckdb")
      (api/inspect (str "duckdb://" path))

      :else
      (api/inspect (str "sqlite://" path)))))
