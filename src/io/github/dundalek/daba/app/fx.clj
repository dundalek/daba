(ns io.github.dundalek.daba.app.fx
  (:require
   [io.github.dundalek.daba.viewer :as-alias dv]
   [io.github.dundalek.daba.app.state :as state]
   [next.jdbc :as jdbc]
   [next.jdbc.datafy]
   [next.jdbc.result-set :as result-set]
   [portal.api :as p]
   [portal.viewer :as pv]))

(defn metadata-result-set [rs]
  (result-set/datafiable-result-set rs {:builder-fn result-set/as-unqualified-kebab-maps}))

(defn get-tables [ds schema-name]
  (with-open [con (jdbc/get-connection ds)]
    (-> (.getMetaData con)
        (.getTables nil schema-name nil nil)
        (metadata-result-set))))

(defn get-schemas [ds]
  (with-open [con (jdbc/get-connection ds)]
    (->> (-> (.getMetaData con)
             (.getSchemas)
             (metadata-result-set))
         (map #(select-keys % [:table-schem]))
         (distinct)
         (sort-by :table-schem))))

(defn get-columns [ds table-name]
  (with-open [con (jdbc/get-connection ds)]
    (-> (.getMetaData con)
        (.getColumns nil nil table-name nil)
        (metadata-result-set))))

(defn inspect-columns [{:keys [source table-name]}]
  (let [{::state/keys [ds dsid]} source
        columns (get-columns ds table-name)]
    (p/submit
     (-> columns
         (pv/default ::dv/column-list)
         (vary-meta assoc ::state/dsid dsid)))))

(defn inspect-tables [{:keys [source schema-name]}]
  (let [{::state/keys [ds dsid]} source
        tables (get-tables ds schema-name)]
    (p/submit
     (-> tables
         (pv/default ::dv/table-list)
         (vary-meta assoc ::state/dsid dsid)))))

(defn inspect-database [source]
  (let [{::state/keys [ds dsid]} source
        schemas (get-schemas ds)]
    (if (seq schemas)
      (p/submit
       (-> schemas
           (pv/default ::dv/schema-list)
           (vary-meta assoc ::state/dsid dsid)))
      (inspect-tables {:source source
                       :schema-name nil}))))
