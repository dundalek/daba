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
        datagrid-meta {::pv/default ::dv/datagrid
                       ::dv/datagrid {:query-map query-map}
                       ::dv/dsid dsid}]
    (try
      (let [{:keys [table where limit offset]} query-map
            results (sql/find-by-keys ds table where
                                      {:limit limit
                                       :offset offset
                                       :builder-fn dbc/as-maps-with-columns-meta})
            {:keys [columns]} (meta results)]
        (with-meta
          results
          (assoc-in datagrid-meta [::dv/datagrid :viewer]
                    {::pv/default ::pv/table
                     ::pv/table {:columns columns}})))
      (catch Exception e
        (with-meta
          {::dv/error e}
          datagrid-meta)))))

(defn execute-string-query [source {:keys [statement offset limit] :as query}]
  (let [{::state/keys [ds dsid]} source
        editor-meta {::pv/default ::dv/query-editor
                     ::dv/query-editor {:query query}
                     ::dv/dsid dsid}]
    (try
      (let [results (if (str/blank? statement)
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
          (assoc editor-meta ::pv/table {:columns columns})))
      (catch Exception e
        (with-meta
          {::dv/error e}
          editor-meta)))))

(defn- get-table-list [{:keys [source schema-name]}]
  (let [{::state/keys [ds dsid]} source]
    (-> (dbc/get-tables ds schema-name)
        (pv/default ::dv/table-list)
        (vary-meta assoc ::dv/dsid dsid))))

;; Effects

(def-fx inspect-columns [{:keys [source table-name]}]
  (submit
   (try
     (let [{::state/keys [ds dsid]} source]
       (-> (dbc/get-columns ds table-name)
           (pv/default ::dv/column-list)
           (vary-meta assoc ::dv/dsid dsid)))
     (catch Exception e
       {::dv/error e}))))

(def-fx inspect-tables [arg]
  (submit
   (try
     (get-table-list arg)
     (catch Exception e
       {::dv/error e}))))

(def-fx inspect-database [source]
  (submit
   (try
     (let [{::state/keys [ds dsid]} source
           schemas (dbc/get-schemas ds)]
       (if (seq schemas)
         (-> schemas
             (pv/default ::dv/schema-list)
             (vary-meta assoc ::dv/dsid dsid))
         (get-table-list {:source source
                          :schema-name nil})))
     (catch Exception e
       {::dv/error e}))))

(def-fx open-query-editor [{:keys [source query]}]
  (submit
   (atom (execute-string-query source query))))
