(ns io.github.dundalek.daba.app.fx
  (:require
   [clojure.string :as str]
   [daba.viewer :as-alias dv]
   [io.github.dundalek.daba.app.state :as state]
   [io.github.dundalek.daba.internal.jdbc :as dbc]
   [next.jdbc :as jdbc]
   [next.jdbc.sql :as sql]
   [portal.api :as p]
   [portal.viewer :as pv]))

(defn inspect-table-data [{:keys [source table-name]}]
  (let [{::state/keys [ds dsid]} source
        rows (sql/find-by-keys ds table-name :all)]
    (p/submit
     (with-meta
       rows
       {::pv/default ::dv/paginator
        ::dv/paginator {:viewer {::pv/default ::pv/table}}
        ::dv/dsid dsid}))))

(defn inspect-columns [{:keys [source table-name]}]
  (let [{::state/keys [ds dsid]} source
        columns (dbc/get-columns ds table-name)]
    (p/submit
     (-> columns
         (pv/default ::dv/column-list)
         (vary-meta assoc ::dv/dsid dsid)))))

(defn inspect-tables [{:keys [source schema-name]}]
  (let [{::state/keys [ds dsid]} source
        tables (dbc/get-tables ds schema-name)]
    (p/submit
     (-> tables
         (pv/default ::dv/table-list)
         (vary-meta assoc ::dv/dsid dsid)))))

(defn inspect-database [source]
  (let [{::state/keys [ds dsid]} source
        schemas (dbc/get-schemas ds)]
    (if (seq schemas)
      (p/submit
       (-> schemas
           (pv/default ::dv/schema-list)
           (vary-meta assoc ::dv/dsid dsid)))
      (inspect-tables {:source source
                       :schema-name nil}))))

(defn open-query-editor [{:keys [source query]}]
  (let [{::state/keys [ds dsid]} source
        results (if (str/blank? query)
                  []
                  (jdbc/execute! ds [query] {:builder-fn dbc/as-maps-with-columns-meta}))
        {:keys [columns]} (meta results)]
    (p/submit
     (with-meta
       results
       {::pv/default ::dv/query-editor
        ::pv/table {:columns columns}
        ::dv/query-editor {:query query}
        ::dv/dsid dsid}))))

