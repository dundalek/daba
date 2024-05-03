(ns io.github.dundalek.daba.app.fx
  (:require
   [clojure.string :as str]
   [daba.viewer :as-alias dv]
   [io.github.dundalek.daba.app.event :as-alias event]
   [io.github.dundalek.daba.app.frame :as frame]
   [io.github.dundalek.daba.app.state :as state]
   [io.github.dundalek.daba.internal.jdbc :as dbc]
   [next.jdbc :as jdbc]
   [next.jdbc.sql :as sql]
   [portal.viewer :as pv]))

;; Helpers

(defn submit [value]
  (frame/dispatch [::event/tap-submitted value]))

(defn- execute-structured-query [source query-map]
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
                  ;; This is inefficient because whole result set will be realized,
                  ;; but at least that will be on JVM side and won't bog down the browser.
                  ;; Consider using jdbc/plan to realize less things as an optimization.
                  (into []
                        (comp (drop offset)
                              (take limit))
                        (jdbc/execute! ds [statement] {:builder-fn dbc/as-maps-with-columns-meta})))
        {:keys [columns]} (meta results)]
    (with-meta
      results
      {::pv/default ::dv/query-editor
       ::pv/table {:columns columns}
       ::dv/query-editor {:query query}
       ::dv/dsid dsid})))

;; Effects

(defn inspect-columns [{:keys [source table-name]}]
  (let [{::state/keys [ds dsid]} source
        columns (dbc/get-columns ds table-name)]
    (submit
     (-> columns
         (pv/default ::dv/column-list)
         (vary-meta assoc ::dv/dsid dsid)))))

(defn inspect-tables [{:keys [source schema-name]}]
  (let [{::state/keys [ds dsid]} source
        tables (dbc/get-tables ds schema-name)]
    (submit
     (-> tables
         (pv/default ::dv/table-list)
         (vary-meta assoc ::dv/dsid dsid)))))

(defn inspect-database [source]
  (let [{::state/keys [ds dsid]} source
        schemas (dbc/get-schemas ds)]
    (if (seq schemas)
      (submit
       (-> schemas
           (pv/default ::dv/schema-list)
           (vary-meta assoc ::dv/dsid dsid)))
      (inspect-tables {:source source
                       :schema-name nil}))))

(defn open-query-editor [{:keys [source query]}]
  (submit
   (atom
    (execute-string-query source query))))

(defn execute-query [{:keys [source query !query-atom]}]
  ;; Prone to race condition, consider some kind of queue in the future
  (reset! !query-atom
          (execute-string-query source query)))

(defn inspect-table-data [{:keys [source query-map]}]
  (submit
   (atom
    (execute-structured-query source query-map))))

(defn execute-query-map [{:keys [source query-map !query-atom]}]
  ;; Prone to race condition, consider some kind of queue in the future
  (reset! !query-atom
          (execute-structured-query source query-map)))
