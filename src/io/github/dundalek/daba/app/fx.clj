(ns io.github.dundalek.daba.app.fx
  (:require
   [clojure.string :as str]
   [daba.viewer :as-alias dv]
   [io.github.dundalek.daba.app.event :as-alias event]
   [io.github.dundalek.daba.app.frame :as frame]
   [io.github.dundalek.daba.app.state :as state]
   [io.github.dundalek.daba.internal.jdbc :as dbc]
   [io.github.dundalek.daba.internal.miniframe :refer [def-fx]]
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
      (let [results (dbc/execute-structured-query ds query-map)
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

(defn execute-string-query [source {:keys [statement] :as query}]
  (let [{::state/keys [ds dsid]} source]
    (-> (try
          (if (str/blank? statement)
            []
            (dbc/execute-string-query ds query))
          (catch Exception e
            {::dv/error e}))
        (vary-meta (fn [m]
                     (let [{:keys [columns]} m]
                       (cond-> (assoc m
                                      ::pv/default ::dv/query-editor
                                      ::dv/query-editor {:query query}
                                      ::dv/dsid dsid)
                         columns (assoc ::pv/table {:columns columns}))))))))

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
