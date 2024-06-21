(ns io.github.dundalek.daba.xtdb1.core
  (:require
   [clojure.edn :as edn]
   [daba.viewer :as-alias dv]
   [io.github.dundalek.daba.app.core :as core]
   [io.github.dundalek.daba.app.core :as app]
   [portal.viewer :as pv]))

(defn coerce-query [query]
  (merge {:limit core/default-page-size :offset 0}
         (if (string? query)
           (try
             (let [parsed (edn/read-string query)]
               parsed)
             (catch Exception _ignore
               nil))
           query)))

(defn attribute-list-viewer [attributes {:keys [dsid]}]
  (-> attributes
      (pv/default ::dv/xtdb1-attribute-list)
      (vary-meta assoc ::dv/dsid dsid)))

(defn query-editor-viewer [results {:keys [query dsid]}]
  (-> results
      (vary-meta (fn [m]
                   (assoc m
                          ::pv/default ::dv/xtdb1-query-editor
                          ::dv/xtdb1-query-editor {:query query}
                          ::dv/dsid dsid)))))

(defn create-query-editor [db dsid]
  (app/create-cell db (query-editor-viewer
                       []
                       {:dsid dsid
                        :query (coerce-query app/datomic-default-query)})))
