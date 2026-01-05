(ns daba.main
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [daba.api :as api]
   [io.github.dundalek.daba.app.event :as event]
   [io.github.dundalek.daba.app.frame :as frame]
   [next.jdbc :as jdbc]))

(defonce !file-connections (atom {}))

(defn- derive-table-name [file-path]
  (-> (io/file file-path)
      (.getName)
      (str/replace #"\.[^.]+$" "")
      (str/replace #"[^a-zA-Z0-9_]" "_")))

(defn- create-duckdb-connection []
  (let [db-name (str "file_" (System/currentTimeMillis))
        dsid (str "jdbc:duckdb::memory:" db-name)
        conn (jdbc/get-connection (jdbc/get-datasource dsid))]
    ;; We have to keep connection open so that temporary table does not disappear.
    ;; TODO: Should implement better connection management - connecting/disconnecting.
    (swap! !file-connections assoc dsid conn)
    {:dsid dsid :conn conn}))

(defn- load-file-to-duckdb [file-path reader-fn]
  (let [{:keys [dsid conn]} (create-duckdb-connection)
        table-name (derive-table-name file-path)
        create-stmt (format "CREATE TABLE %s AS SELECT * FROM %s(?);" table-name reader-fn)]
    (jdbc/execute! conn [create-stmt file-path])
    dsid))

(defn- inspect-duckdb [dsid]
  (api/inspect dsid)
  (frame/dispatch (event/schema-tables-inspected {:dsid dsid :schema "main"})))

(defn load-jsonl-to-duckdb [jsonl-path]
  (load-file-to-duckdb jsonl-path "read_json_auto"))

(defn load-csv-to-duckdb [csv-path]
  (load-file-to-duckdb csv-path "read_csv_auto"))

(defn load-xlsx-to-duckdb [xlsx-path]
  (let [{:keys [dsid conn]} (create-duckdb-connection)
        table-name (derive-table-name xlsx-path)]
    (jdbc/execute! conn ["INSTALL excel"])
    (jdbc/execute! conn ["LOAD excel"])
    (jdbc/execute! conn [(format "CREATE TABLE %s AS SELECT * FROM read_xlsx(?)" table-name) xlsx-path])
    dsid))

(defn -main [& args]
  (api/open)
  (when-some [path (first args)]
    (cond
      (not (.isFile (io/file path)))
      (api/inspect path)

      (str/ends-with? path ".csv")
      (inspect-duckdb (load-csv-to-duckdb path))

      (str/ends-with? path ".jsonl")
      (inspect-duckdb (load-jsonl-to-duckdb path))

      (str/ends-with? path ".xlsx")
      (inspect-duckdb (load-xlsx-to-duckdb path))

      (str/ends-with? path ".duckdb")
      (inspect-duckdb (str "duckdb://" path))

      :else
      (api/inspect (str "sqlite://" path)))))

(comment
  (-main "tmp/sbirka.xlsx"))
