(ns io.github.dundalek.daba.xtdb1.event
  (:require
   [daba.viewer :as-alias dv]
   [io.github.dundalek.daba.app.core :as app]
   [io.github.dundalek.daba.app.event :as event]
   [io.github.dundalek.daba.app.frame :as frame]
   [io.github.dundalek.daba.lib.miniframe :refer [def-event-db fx!]]
   [io.github.dundalek.daba.xtdb1.lib :as xtdb1-lib]
   [io.github.dundalek.daba.xtdb1.core :as xtdb1]
   [portal.viewer :as pv]))

(declare fx-execute-query)

(def-event-db query-execution-completed [db {:keys [cell-id result query dsid]}]
  (app/set-cell db cell-id
                (xtdb1/query-editor-viewer result {:query query :dsid dsid})))

(def-event-db query-editor-executed [_db {:keys [cell-id dsid query]}]
  ;; dsid might be redundant, likely could read it from cell value
  (let [query (xtdb1/coerce-query query)]
    (fx!
     (fx-execute-query {:cell-id cell-id
                        :dsid dsid
                        :query query}))))

(def-event-db query-editor-changed [_db {:keys [cell-id dsid query]}]
  (fx!
   (fx-execute-query {:cell-id cell-id
                      :dsid dsid
                      :query query})))

(def-event-db schemas-request-completed [db {:keys [result dsid]}]
  (app/create-cell db (xtdb1/attribute-list-viewer result {:dsid dsid})))

(def-event-db request-completed [db cell-value]
  (app/create-cell db cell-value))

(def-event-db datasource-schema-triggered [_db {:keys [dsid]}]
  (fx!
   (event/schedule-async
    (frame/dispatch
     (schemas-request-completed
      {:result (xtdb1-lib/get-schema dsid)
       :dsid dsid})))))

(def-event-db attribute-inspected [_db {:keys [dsid attribute]}]
  (let [query (xtdb1/coerce-query
               (app/datomic-inspect-attribute-query attribute))]
    (fx!
     (event/schedule-async
      (frame/dispatch
       (request-completed
        (xtdb1/query-editor-viewer
         (xtdb1-lib/query dsid query)
         {:dsid dsid
          :query query})))))))

(defn fx-execute-query [{:keys [cell-id dsid query]}]
  (event/schedule-async
   (frame/dispatch
    (query-execution-completed
     {:cell-id cell-id
      :result (try
                (xtdb1-lib/query dsid query)
                (catch Throwable e
                  (app/wrap-exception e)))
      ;; TODO query and dsid redundant
      :query query
      :dsid dsid}))))
