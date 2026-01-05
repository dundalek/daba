(ns daba.main
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [daba.api :as api]
   [honey.sql :as sql]
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

(defn- load-file-to-duckdb-connection [conn reader-fn file-path]
  (let [table-name (derive-table-name file-path)
        sql-vec (sql/format {:create-table [[(keyword table-name) :as
                                             {:select [:*]
                                              :from [[[reader-fn file-path]]]}]]})]

    (jdbc/execute! conn sql-vec)))

(defn- load-file-to-duckdb [reader-fn file-path]
  (let [{:keys [dsid conn]} (create-duckdb-connection)]
    (load-file-to-duckdb-connection conn reader-fn file-path)
    dsid))

(defn- inspect-duckdb [dsid]
  (api/inspect dsid)
  (frame/dispatch (event/schema-tables-inspected {:dsid dsid :schema "main"})))

(defn load-jsonl-to-duckdb [jsonl-path]
  (load-file-to-duckdb :read_json_auto jsonl-path))

(defn load-csv-to-duckdb [csv-path]
  (load-file-to-duckdb :read_csv_auto csv-path))

(defn load-xlsx-to-duckdb [xlsx-path]
  (let [{:keys [dsid conn]} (create-duckdb-connection)]
    (jdbc/execute! conn ["INSTALL excel"])
    (jdbc/execute! conn ["LOAD excel"])
    (load-file-to-duckdb-connection conn :read_xlsx xlsx-path)
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
