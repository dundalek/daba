(ns io.github.dundalek.daba.app.event
  (:require
   [io.github.dundalek.daba.app.core :as core]
   [io.github.dundalek.daba.app.frame :as frame]
   [io.github.dundalek.daba.app.state :as state]
   [io.github.dundalek.daba.lib.datomic :as datomic]
   [io.github.dundalek.daba.lib.jdbc :as dbc]
   [io.github.dundalek.daba.lib.miniframe :refer [def-event-db fx!]]
   [io.github.dundalek.daba.xtdb1.core :as xtdb1]))

(declare fx-execute-string-query)
(declare fx-query-table-data)
(declare fx-request-tables)
(declare fx-request-schemas)
(declare fx-datomic-execute-query)

(def-event-db tap-submitted [db value]
  (core/create-cell db value))

(def-event-db tap-removed [db cell-id]
  (core/remove-cell db cell-id))

(def-event-db values-cleared [db _]
  ;; Preserving etatada to keep selected root viewer
  (with-meta
    (-> state/default-state
        ;; Always have one datasource input when values are cleared
        (core/create-cell (core/datasource-input-viwer "")))
    (meta db)))

(def-event-db task-started [db _]
  (update db ::state/running-tasks inc))

(def-event-db task-completed [db _]
  (update db ::state/running-tasks dec))

(defmacro with-loading-indicator [& body]
  `(do (frame/dispatch (task-started))
       (try
         ~@body
         (finally
           (frame/dispatch (task-completed))))))

(defn schedule-async-call [f]
  ;; For now just firing off futures, might consider some thread pooling later.
  ;; Also there are potential races of queries within cells,
  ;; might consider queuing tasks by cell id to execute them serialy.
  (future
    (with-loading-indicator
      (try
        (f)
        (catch Throwable e
          (frame/dispatch (tap-submitted e)))))))

(defmacro schedule-async [& body]
  `(schedule-async-call (fn [] ~@body)))

(defn create-query-editor [db value]
  (let [dsid (core/parse-db-spec value)]
    (if (core/xtdb1-datasource? dsid)
      (xtdb1/create-query-editor db dsid)
      (let [query (core/coerce-query core/default-input-query)
            viewer (core/empty-query-editor-viewer {:query query :dsid dsid})]
        (core/create-cell db viewer)))))

(def-event-db tables-request-completed [db {:keys [result dsid]}]
  (core/create-cell db (core/table-list-viewer result {:dsid dsid})))

(def-event-db schemas-request-completed [db {:keys [result dsid]}]
  (core/create-cell db (core/schema-list-viewer result {:dsid dsid})))

(def-event-db datomic-databases-request-completed [db {:keys [result dsid]}]
  (core/create-cell db (core/datomic-databases-viewer result {:dsid dsid})))

(def-event-db datomic-request-completed [db cell-value]
  (core/create-cell db cell-value))

(def-event-db datomic-database-inspected [_db {:keys [dsid db-name]}]
  (let [dsid {:client-args dsid
              :connection-args {:db-name db-name}}]
    (fx!
     (schedule-async
      (frame/dispatch
       (datomic-request-completed
        (core/datomic-database-namespaces-viewer
         (datomic/get-schema dsid)
         {:dsid dsid})))))))

(def-event-db datomic-attribute-inspected [_db {:keys [dsid attribute]}]
  (let [query (core/datomic-coerce-query
               (core/datomic-inspect-attribute-query attribute))]
    (fx!
     (schedule-async
      (frame/dispatch
       (datomic-request-completed
        (core/datomic-query-editor-viewer
         (datomic/query dsid query)
         {:dsid dsid
          :query query})))))))

(def-event-db datomic-database-attributes-inspected [_db {:keys [dsid db-name]}]
  (let [dsid {:client-args dsid
              :connection-args {:db-name db-name}}]
    (fx!
     (schedule-async
      (frame/dispatch
       (datomic-request-completed
        (core/datomic-database-attributes-viewer
         (datomic/get-schema dsid)
         {:dsid dsid})))))))

(def-event-db datomic-namespace-attributes-inspected [db {:keys [dsid attributes]}]
  (core/create-cell db (core/datomic-database-attributes-viewer attributes {:dsid dsid})))

(def-event-db datomic-query-triggered [db {:keys [dsid db-name]}]
  (let [dsid {:client-args dsid
              :connection-args {:db-name db-name}}]
    (core/create-cell db (core/datomic-query-editor-viewer
                          []
                          {:dsid dsid
                           :query (core/datomic-coerce-query core/datomic-default-query)}))))

(def-event-db datomic-query-execution-completed [db {:keys [cell-id result query dsid]}]
  (core/set-cell db cell-id
                 (core/datomic-query-editor-viewer result {:query query :dsid dsid})))

(def-event-db datomic-query-editor-executed [_db {:keys [cell-id dsid query]}]
  ;; dsid might be redundant, likely could read it from cell value
  (let [query (core/datomic-coerce-query query)]
    (fx!
     (fx-datomic-execute-query {:cell-id cell-id
                                :dsid dsid
                                :query query}))))

(def-event-db datomic-query-editor-changed [_db {:keys [cell-id dsid query]}]
  (fx!
   (fx-datomic-execute-query {:cell-id cell-id
                              :dsid dsid
                              :query query})))

(def-event-db datasource-edit-triggered [db value]
  (core/create-cell db (core/datasource-input-viwer value)))

(def-event-db datasource-schema-triggered [_db value]
  (fx!
   (fx-request-schemas (core/parse-db-spec value))))

(def-event-db datasource-query-triggered [db value]
  (create-query-editor db value))

(def-event-db datasource-input-schema-triggered [db {:keys [cell-id value]}]
  (fx!
   (fx-request-schemas (core/parse-db-spec value)))
  (-> db
      (core/set-cell cell-id (core/datasource-input-viwer value))))

(def-event-db datasource-input-query-triggered [db {:keys [cell-id value]}]
  (-> db
      (create-query-editor value)
      (core/set-cell cell-id (core/datasource-input-viwer value))))

(def-event-db schema-tables-inspected [_db {:keys [dsid schema]}]
  (fx!
   (fx-request-tables {:dsid dsid :schema schema})))

(def-event-db columns-request-completed [db {:keys [result dsid]}]
  (core/create-cell db (core/column-list-viewer result {:dsid dsid})))

(def-event-db table-columns-inspected [_db {:keys [dsid table]}]
  (fx!
   (schedule-async
    (let [tables (dbc/get-columns dsid table)]
      (frame/dispatch (columns-request-completed {:result tables :dsid dsid}))))))

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

(def-event-db root-viewer-switched [db _]
  ;; Changing metadata does not make Portal re-render
  ;; As a workaround incrementing next-cell-id for the change to take effect
  (let [[db _] (core/next-cell-id db)]
    (vary-meta db update :portal.viewer/default
               (fn [viewer]
                 (if (= viewer :daba.viewer/root)
                   :daba.viewer/root-docking
                   :daba.viewer/root)))))

(defn fx-execute-string-query [{:keys [cell-id dsid query]}]
  (schedule-async
   (frame/dispatch
    (query-execution-completed
     {:cell-id cell-id
      :result (try
                #_(drivers/ensure-loaded! dsid)
                (dbc/execute-string-query dsid query)
                (catch Throwable e
                  (core/wrap-exception e)))
       ;; TODO query and dsid redundant
      :query query
      :dsid dsid}))))

(defn fx-query-table-data [{:keys [cell-id dsid query]}]
  (schedule-async
   (frame/dispatch
    (table-data-query-completed
     {:cell-id cell-id
      :result (try (dbc/execute-structured-query dsid query)
                   (catch Throwable e
                     (core/wrap-exception e)))
       ;; TODO query and dsid redundant
      :query query
      :dsid dsid}))))

(defn fx-request-tables [{:keys [dsid schema]}]
  (schedule-async
   (let [tables (dbc/get-tables dsid schema)]
     (frame/dispatch (tables-request-completed {:result tables :dsid dsid})))))

(defn fx-request-schemas [dsid]
  (schedule-async
   #_(drivers/ensure-loaded! dsid)
   (cond
     (core/datomic-datasource? dsid)
     (frame/dispatch (datomic-databases-request-completed
                      {:dsid dsid
                       :result (datomic/get-databases dsid)}))

     (core/xtdb1-datasource? dsid)
     ;; hacky workaround, need to untangle dependencies
     (frame/dispatch [:io.github.dundalek.daba.xtdb1.event/datasource-schema-triggered {:dsid dsid}])

     :else (let [schemas (dbc/get-schemas dsid)]
             (if (seq schemas)
               (frame/dispatch (schemas-request-completed {:result schemas :dsid dsid}))
               (let [tables (dbc/get-tables dsid nil)]
                 (frame/dispatch (tables-request-completed {:result tables :dsid dsid}))))))))

(defn fx-datomic-execute-query [{:keys [cell-id dsid query]}]
  (schedule-async
   (frame/dispatch
    (datomic-query-execution-completed
     {:cell-id cell-id
      :result (try
                (datomic/query dsid query)
                (catch Throwable e
                  (core/wrap-exception e)))
      ;; TODO query and dsid redundant
      :query query
      :dsid dsid}))))
