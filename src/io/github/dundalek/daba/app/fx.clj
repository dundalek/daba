(ns io.github.dundalek.daba.app.fx
  (:require
   [clojure.string :as str]
   [daba.viewer :as-alias dv]
   [io.github.dundalek.daba.app.event :as-alias event]
   [io.github.dundalek.daba.app.frame :as frame]
   [io.github.dundalek.daba.app.state :as state]
   [io.github.dundalek.daba.internal.jdbc :as dbc]
   [io.github.dundalek.daba.internal.miniframe :refer [def-fx]]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]
   [next.jdbc.sql :as sql]
   [portal.viewer :as pv]))

;; Helpers

(defn submit [value]
  (frame/dispatch [::event/removable-tap-submitted value]))

(defn execute-structured-query [source query-map]
  (let [{::state/keys [ds dsid]} source
        {:keys [table where limit offset]} query-map
        results (sql/find-by-keys ds table where
                                  {:limit limit
                                   :offset offset
                                   :builder-fn dbc/as-maps-with-columns-meta})
        {:keys [columns]} (meta results)]
    (with-meta
      results
      {::pv/default ::dv/datagrid
       ::dv/datagrid {:viewer {::pv/default ::pv/table
                               ::pv/table {:columns columns}}
                      :query-map query-map}
       ::dv/dsid dsid})))

(defn- execute-string-query [source {:keys [statement offset limit] :as query}]
  (let [{::state/keys [ds dsid]} source
        results (if (str/blank? statement)
                  []
                  ;; We want to prevent bringing the viewer down in case there is a select with millions of rows.
                  ;; We still iterate over them on the client runtime, but at least do not realize everything.
                  ;; Using plan with reductinos is more efficient than execute! which realizes all results.
                  ;; Maybe consider a heuristic and only try to limit SELECT queries.
                  (dbc/reduce-with-columns-meta
                   (jdbc/plan ds [statement] {:builder-fn rs/as-maps})
                   (comp (drop offset)
                         (take limit)
                         (map #(rs/datafiable-row % ds {})))))
        {:keys [columns]} (meta results)]
    (with-meta
      results
      {::pv/default ::dv/query-editor
       ::pv/table {:columns columns}
       ::dv/query-editor {:query query}
       ::dv/dsid dsid})))

(defn- inspect-tables! [{:keys [source schema-name]}]
  (let [{::state/keys [ds dsid]} source
        tables (dbc/get-tables ds schema-name)]
    (submit
     (-> tables
         (pv/default ::dv/table-list)
         (vary-meta assoc ::dv/dsid dsid)))))

;; Effects

(def-fx inspect-columns [{:keys [source table-name]}]
  (let [{::state/keys [ds dsid]} source
        columns (dbc/get-columns ds table-name)]
    (submit
     (-> columns
         (pv/default ::dv/column-list)
         (vary-meta assoc ::dv/dsid dsid)))))

(def-fx inspect-tables [arg]
  (inspect-tables! arg))

(def-fx inspect-database [source]
  (let [{::state/keys [ds dsid]} source
        schemas (dbc/get-schemas ds)]
    (if (seq schemas)
      (submit
       (-> schemas
           (pv/default ::dv/schema-list)
           (vary-meta assoc ::dv/dsid dsid)))
      (inspect-tables! {:source source
                        :schema-name nil}))))

(def-fx open-query-editor [{:keys [source query]}]
  (submit
   (atom
    (execute-string-query source query))))

(def-fx execute-query [{:keys [source query !query-atom]}]
  ;; Prone to race condition, consider some kind of queue in the future
  (reset! !query-atom
          (execute-string-query source query)))

(def-fx execute-query-map [{:keys [source query-map !query-atom]}]
  ;; Prone to race condition, consider some kind of queue in the future
  (reset! !query-atom
          (execute-structured-query source query-map)))
