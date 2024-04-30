(ns io.github.dundalek.daba.internal.jdbc
  (:require
   [next.jdbc :as jdbc]
   [next.jdbc.datafy]
   [next.jdbc.result-set :as result-set]))

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
