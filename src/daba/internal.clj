(ns daba.internal
  (:require
   [clojure.datafy :as d :refer [datafy]]
   [next.jdbc :as jdbc]
   [next.jdbc.datafy]
   [next.jdbc.result-set :as result-set]
   [portal.api :as p]))

(defn query-table-data [ds table-name]
  (with-meta
    ;; TODO: escape table-name
    (->> (jdbc/execute! ds [(str "select * from " table-name)])
         (take 10))
    {:portal.viewer/default :portal.viewer/table}))

(defn inspect-tables [ds]
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

(defn inspect [db-spec]
  (let [ds (jdbc/get-datasource db-spec)]
    (inspect-tables ds)))

(comment
  (def db-spec
    "jdbc:sqlite:tmp/Chinook_Sqlite_AutoIncrementPKs.sqlite")

  (inspect db-spec)

  (def ds (jdbc/get-datasource db-spec))

  (def con (jdbc/get-connection ds))

  (jdbc/execute! ds ["select * from Artist limit 10"])

  (bean ds)

  (tap> (datafy (:metaData (datafy con))))

  (let [mdata (.getMetaData con)
        #_(-> con
              datafy
              :metaData)]
    ; (.getTables mdata)
    ; (nav con :metaData mdata))
    (->> (.getTables mdata nil nil nil nil)
         (result-set/datafiable-result-set)
         ; (filter (comp #{"TABLE"} :TABLE_TYPE)) ; there is also "SYSTEM TABLE"
         ; (map :sqlite_master/TABLE_NAME)))
         (map :TABLE_TYPE)
         frequencies))

  ;; https://docs.oracle.com/javase/8/docs/api/java/sql/DatabaseMetaData.html
  ;; list columns
  (let [table-name nil ;"Customer"
        dbmeta (.getMetaData con)]
    (->> (.getColumns dbmeta nil nil table-name nil)
         result-set/datafiable-result-set
         (group-by :TABLE_NAME)
         (keys)
         (sort))
         ; (map :TABLE_NAME)
         ; frequencies))
    (p/docs)))
