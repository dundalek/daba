(ns io.github.dundalek.daba.app.event
  (:require
   [io.github.dundalek.daba.app.core :as core]
   [io.github.dundalek.daba.app.frame :as frame]
   [io.github.dundalek.daba.lib.drivers :as drivers]
   [io.github.dundalek.daba.lib.jdbc :as dbc]
   [io.github.dundalek.daba.lib.miniframe :refer [def-event-db fx!]]))

(declare fx-execute-string-query)
(declare fx-query-table-data)
(declare fx-request-tables)
(declare fx-request-schemas)

(def-event-db tap-submitted [db value]
  (core/create-cell db value))

(def-event-db tap-removed [db cell-id]
  (core/remove-cell db cell-id))

(def-event-db values-cleared [db _]
  ;; TODO leave datasource input
  (core/clear-cells db))

(def-event-db tables-request-completed [db {:keys [result dsid]}]
  (core/create-cell db (core/table-list-viewer result {:dsid dsid})))

(def-event-db schemas-request-completed [db {:keys [result dsid]}]
  (core/create-cell db (core/schema-list-viewer result {:dsid dsid})))

(def-event-db datasource-edit-triggered [db value]
  (core/create-cell db (core/datasource-input-viwer value)))

(def-event-db datasource-schema-triggered [_db value]
  (fx!
   (fx-request-schemas (core/parse-db-spec value))))

(def-event-db datasource-query-triggered [db value]
  (let [dsid (core/parse-db-spec value)
        query (core/coerce-query core/default-input-query)
        viewer (core/empty-query-editor-viewer {:query query :dsid dsid})]
    (core/create-cell db viewer)))

(def-event-db datasource-input-schema-triggered [db {:keys [cell-id value]}]
  (fx!
   (fx-request-schemas (core/parse-db-spec value)))
  (-> db
      (core/set-cell cell-id (core/datasource-input-viwer value))))

(def-event-db datasource-input-query-triggered [db {:keys [cell-id value]}]
  (let [dsid (core/parse-db-spec value)
        query (core/coerce-query core/default-input-query)
        viewer (core/empty-query-editor-viewer {:query query :dsid dsid})]
    (-> db
        (core/set-cell cell-id (core/datasource-input-viwer value))
        (core/create-cell viewer))))

(def-event-db schema-tables-inspected [_db {:keys [dsid schema]}]
  (fx!
   (fx-request-tables {:dsid dsid :schema schema})))

(def-event-db columns-request-completed [db {:keys [result dsid]}]
  (core/create-cell db (core/column-list-viewer result {:dsid dsid})))

(def-event-db table-columns-inspected [_db {:keys [dsid table]}]
  (fx!
   (try
     (let [tables (dbc/get-columns dsid table)]
       (frame/dispatch (columns-request-completed {:result tables :dsid dsid})))
     (catch Exception e
       (frame/dispatch (tap-submitted e))))))

(def-event-db table-data-query-completed [db {:keys [cell-id result query dsid]}]
  (core/set-cell db cell-id
                 (core/datagrid-viewer result {:query query :dsid dsid})))

(def-event-db table-data-inspected [db {:keys [dsid table]}]
  (let [query (core/table-data-query table)
        [db cell-id] (core/next-cell-id db)]
    (fx!
     (fx-query-table-data {:cell-id cell-id :dsid dsid :query query}))
    (core/set-cell db cell-id
                   (core/datagrid-viewer [] {:query query :dsid dsid}))))

(def-event-db table-data-query-changed [_db {:keys [cell-id dsid query]}]
  (fx!
   (fx-query-table-data {:cell-id cell-id :dsid dsid :query query})))

(def-event-db query-execution-completed [db {:keys [cell-id result query dsid]}]
  (core/set-cell db cell-id
                 (core/query-editor-viewer result {:query query :dsid dsid})))

(def-event-db query-editor-executed [_db {:keys [cell-id dsid query]}]
  ;; dsid might be redundant, likely could read it from cell value
  (let [query (core/coerce-query query)]
    (fx!
     (fx-execute-string-query {:cell-id cell-id :dsid dsid :query query}))))

(def-event-db query-edited [db statement]
  (let [dsid (core/last-used-dsid db)
        query (core/coerce-query statement)
        viewer (core/empty-query-editor-viewer {:query query :dsid dsid})]
    (core/create-cell db viewer)))

(def-event-db query-executed [db statement]
  (let [dsid (core/last-used-dsid db)
        query (core/coerce-query statement)
        viewer (core/empty-query-editor-viewer {:query query :dsid dsid})
        [db cell-id] (core/next-cell-id db)]
    (fx!
     (fx-execute-string-query {:cell-id cell-id :dsid dsid :query query}))
    (core/set-cell db cell-id viewer)))

(defn fx-execute-string-query [{:keys [cell-id dsid query]}]
  (frame/dispatch
   (query-execution-completed
    {:cell-id cell-id
     :result (try
               (drivers/ensure-loaded! dsid)
               (dbc/execute-string-query dsid query)
               (catch Exception e
                 (core/wrap-exception e)))
     ;; TODO query and dsid redundant
     :query query
     :dsid dsid})))

(defn fx-query-table-data [{:keys [cell-id dsid query]}]
  (frame/dispatch
   (table-data-query-completed
    {:cell-id cell-id
     :result (try (dbc/execute-structured-query dsid query)
                  (catch Exception e
                    (core/wrap-exception e)))
     ;; TODO query and dsid redundant
     :query query
     :dsid dsid})))

(defn fx-request-tables [{:keys [dsid schema]}]
  (try
    (let [tables (dbc/get-tables dsid schema)]
      (frame/dispatch (tables-request-completed {:result tables :dsid dsid})))
    (catch Exception e
      (frame/dispatch (tap-submitted e)))))

(defn fx-request-schemas [dsid]
  (try
    (drivers/ensure-loaded! dsid)
    (let [schemas (dbc/get-schemas dsid)]
      (if (seq schemas)
        (frame/dispatch (schemas-request-completed {:result schemas :dsid dsid}))
        (let [tables (dbc/get-tables dsid nil)]
          (frame/dispatch (tables-request-completed {:result tables :dsid dsid})))))
    (catch Exception e
      (frame/dispatch (tap-submitted e)))))
