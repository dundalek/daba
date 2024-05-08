(ns io.github.dundalek.daba.app.event2
  (:require
   [io.github.dundalek.daba.app.core2 :as core2]
   [io.github.dundalek.daba.app.frame :as frame]
   [io.github.dundalek.daba.internal.jdbc :as dbc]
   [io.github.dundalek.daba.internal.miniframe :refer [def-event-db fx!]]))

(declare fx-execute-string-query)
(declare fx-request-schemas)

(def-event-db tap-submitted [db value]
  (core2/create-cell db value))

(def-event-db tap-removed [db cell-id]
  (core2/remove-cell db cell-id))

(def-event-db values-cleared [db _]
  ;; TODO leave datasource input
  (core2/clear-cells db))

(def-event-db datasource-edit-triggered [db value]
  (core2/create-cell db (core2/datasource-input-viwer value)))

(def-event-db tables-request-completed [db {:keys [result dsid]}]
  (core2/create-cell db (core2/table-list-viewer result {:dsid dsid})))

(def-event-db schemas-request-completed [db {:keys [result dsid]}]
  (core2/create-cell db (core2/schema-list-viewer result {:dsid dsid})))

(def-event-db datasource-schema-triggered [_db value]
  (fx!
   (fx-request-schemas (core2/parse-db-spec value))))

(def-event-db datasource-query-triggered [db value]
  (let [dsid (core2/parse-db-spec value)
        query (core2/coerce-query "")
        viewer (core2/empty-query-editor-viewer {:query query :dsid dsid})]
    (core2/create-cell db viewer)))

(def-event-db datasource-input-schema-triggered [db {:keys [cell-id value]}]
  (fx!
   (fx-request-schemas (core2/parse-db-spec value)))
  (-> db
      (core2/set-cell cell-id (core2/datasource-input-viwer value))))

(def-event-db datasource-input-query-triggered [db {:keys [cell-id value]}]
  (let [dsid (core2/parse-db-spec value)
        query (core2/coerce-query "")
        viewer (core2/empty-query-editor-viewer {:query query :dsid dsid})]
    (-> db
        (core2/set-cell cell-id (core2/datasource-input-viwer value))
        (core2/create-cell viewer))))

(def-event-db query-execution-completed [db {:keys [cell-id result query dsid]}]
  (core2/set-cell db cell-id
                  (core2/query-editor-viewer result {:query query :dsid dsid})))

(def-event-db query-editor-executed [_db {:keys [cell-id dsid query]}]
  ;; dsid might be redundant, likely could read it from cell value
  (let [query (core2/coerce-query query)]
    (fx!
     (fx-execute-string-query {:cell-id cell-id :dsid dsid :query query}))))

(def-event-db query-edited [db statement]
  (let [dsid (core2/last-used-dsid db)
        query (core2/coerce-query statement)
        viewer (core2/empty-query-editor-viewer {:query query :dsid dsid})]
    (core2/create-cell db viewer)))

(def-event-db query-executed [db statement]
  (let [dsid (core2/last-used-dsid db)
        query (core2/coerce-query statement)
        viewer (core2/empty-query-editor-viewer {:query query :dsid dsid})
        [db cell-id] (core2/next-cell-id db)]
    (fx!
     (fx-execute-string-query {:cell-id cell-id :dsid dsid :query query}))
    (core2/set-cell db cell-id viewer)))

(defn fx-execute-string-query [{:keys [cell-id dsid query]}]
  (frame/dispatch
   (query-execution-completed
    {:cell-id cell-id
     :result (try (dbc/execute-string-query dsid query)
                  (catch Exception e
                    (core2/wrap-exception e)))
     ;; TODO query and dsid redundant
     :query query
     :dsid dsid})))

(defn fx-request-schemas [dsid]
  (try
    (let [schemas (dbc/get-schemas dsid)]
      (if (seq schemas)
        (frame/dispatch (schemas-request-completed {:result schemas :dsid dsid}))
        (let [tables (dbc/get-tables dsid nil)]
          (frame/dispatch (tables-request-completed {:result tables :dsid dsid})))))
    (catch Exception e
      (frame/dispatch (tap-submitted e)))))
