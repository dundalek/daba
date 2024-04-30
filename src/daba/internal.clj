(ns daba.internal
  (:require
   [next.jdbc :as jdbc]
   [next.jdbc.datafy]
   [next.jdbc.result-set :as result-set]
   [portal.api :as p]
   [portal.viewer :as v]))

(defn inspector-viewer
  ;; Specifying default viewer with metadata did not seem to work reliably, perhaps due to equality not considering metadata?
  ;; As a workaround always wrapping in a hiccup viewer seems to work better.
  ([value] (inspector-viewer value {}))
  ([value opts]
   (v/hiccup
    [::v/inspector
     opts
     value])))

(defn inspector-seq-viewer
  ([coll] (inspector-seq-viewer {} coll))
  ([opts coll] (inspector-viewer coll opts)))

(defn query-table-data [ds table-name]
  ;; TODO: escape table-name
  (->> (with-meta
         (jdbc/execute! ds [(str "select * from " table-name)])
         {:daba.viewer/paginator {:viewer {:portal.viewer/default :portal.viewer/table}}})
       (inspector-seq-viewer
        {:portal.viewer/default :daba.viewer/paginator})))

#_(defn inspect-tables [ds]
    (with-open [con (jdbc/get-connection ds)]
      (let [table-name nil ;"Customer"
            dbmeta (.getMetaData con)
            table-list (->> (.getColumns dbmeta nil nil table-name nil)
                            result-set/datafiable-result-set
                            (group-by :TABLE_NAME)
                            (sort-by key)
                            (map (fn [[table-name columns]]
                                   (with-meta
                                     {:aname table-name
                                      :columns (count columns)}
                                     {`clojure.core.protocols/nav
                                      (fn [_coll key _value]
                                        (case key
                                          :columns columns
                                          :aname (query-table-data ds table-name)
                                          nil))}))))]
        (p/submit (with-meta table-list {:portal.viewer/default :portal.viewer/table})))))

(defn inspect-schema [ds columns]
  (let [grouped (->> columns
                     (group-by :TABLE_NAME))]
    (inspector-viewer
     (with-meta
       (->> grouped
            keys
            sort
            (map (fn [aname]
                   (v/hiccup [:div aname]))))
       {`clojure.core.protocols/nav
        (fn [_coll key [_ value]]
          (query-table-data ds value))}))))

(defn inspect-schemas [ds columns]
  (let [grouped (->> columns
                     (group-by :TABLE_SCHEM))]
    (if (and (= (count grouped) 1)
             (nil? (first (keys grouped))))
      ;; no schemas, e.g. sqlite
      (inspect-schema ds columns)
      ;; schemas, e.g. postgres, duckdb
      (with-meta
        (->> grouped
             keys
             sort
             (map (fn [schema]
                    (v/hiccup [:div schema]))))
        {`clojure.core.protocols/nav
         (fn [_coll _key [_ value]]
           (inspect-schema ds (get grouped value)))}))))

(defn inspect-database [db-spec]
  (p/submit
   (let [ds (jdbc/get-datasource db-spec)]
     (with-open [con (jdbc/get-connection ds)]
       (let [dbmeta (.getMetaData con)
             columns (->> (.getColumns dbmeta nil nil nil nil)
                          result-set/datafiable-result-set)]
         (inspect-schemas ds columns))))))

(comment
  (p/submit (inspect-schemas ds columns))

  (inspect-database db-spec)

  (tap> [(v/hiccup [:div "Hello"])
         (v/hiccup [:div "world"])])

  (def db-spec
    "jdbc:sqlite:tmp/Chinook_Sqlite_AutoIncrementPKs.sqlite")

  (def db-spec
    (str "jdbc:" (System/getenv "POSTGRES_URI")))

  (def ds (jdbc/get-datasource db-spec))
  (def con (jdbc/get-connection ds))

  (jdbc/execute! ds ["select * from Artist limit 10"])

  (def db-spec "jdbc:duckdb:") ; in-memory
  (def db-spec "jdbc:duckdb:tmp/duck-data") ; on disk

  (jdbc/execute! ds ["SELECT * FROM read_csv('tmp/githut-csv/gh-push-event.json.csv') limit 10;"])
  (jdbc/execute! ds ["CREATE TABLE pushes AS SELECT * FROM read_csv('tmp/githut-csv/gh-push-event.json.csv');"])
  (jdbc/execute! ds ["select * from pushes limit 10"])
  (jdbc/execute! ds ["show tables"])
  (jdbc/execute! ds ["describe pushes"])

  (let [mdata (.getMetaData con)]
    (def tables
      (->> (.getTables mdata nil nil nil nil)
           (result-set/datafiable-result-set))))
           ; (filter (comp #{"TABLE"} :TABLE_TYPE)) ; there is also "SYSTEM TABLE"
           ; (map :sqlite_master/TABLE_NAME)))
           ; (map :TABLE_TYPE)
           ; frequencies))

  (->> tables
       (map :TABLE_TYPE)
       frequencies)

  (->> tables
       count)

  ; :pg_class/TABLE_NAME "playing_with_neon",
  (->> tables
       (filter (comp #{"TABLE"} :TABLE_TYPE)))

  ;; https://docs.oracle.com/javase/8/docs/api/java/sql/DatabaseMetaData.html
  ;; list columns
  (let [table-name nil ;"Customer"
        dbmeta (.getMetaData con)]
    (def columns
      (->> (.getColumns dbmeta nil nil table-name nil)
           result-set/datafiable-result-set)))
           ; (group-by :TABLE_NAME)
           ; (keys)
           ; (sort))
           ; (map :TABLE_NAME)
           ; frequencies))

  ; :TABLE_SCHEM
  ; {"information_schema" 697, "pg_catalog" 1358, "public" 3}
  (->> columns
       (filter (comp #{"public"} :TABLE_SCHEM)))

  (->> columns
       (group-by :TABLE_SCHEM)
       keys))
