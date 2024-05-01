(ns io.github.dundalek.daba.app.event
  (:require
   [io.github.dundalek.daba.app.core :as core]
   [io.github.dundalek.daba.app.state :as state]
   [io.github.dundalek.daba.app.fx :as-alias fx]))

(defn source-added [db [_ dsid source]]
  (update db ::state/sources assoc dsid source))

(defn database-inspected [{:keys [db]} [_ dsid]]
  {:fx [[::fx/inspect-database (core/get-source db dsid)]]})

(defn tables-inspected [{:keys [db]} [_ dsid schema-name]]
  {:fx [[::fx/inspect-tables {:source (core/get-source db dsid)
                              :schema-name schema-name}]]})

(defn columns-inspected [{:keys [db]} [_ dsid table-name]]
  {:fx [[::fx/inspect-columns {:source (core/get-source db dsid)
                               :table-name table-name}]]})

(defn table-data-inspected [{:keys [db]} [_ dsid table-name]]
  {:fx [[::fx/inspect-table-data {:source (core/get-source db dsid)
                                  :table-name table-name}]]})

(defn query-editor-opened [{:keys [db]} [_ dsid]]
  {:fx [[::fx/open-query-editor {:source (core/get-source db dsid)
                                 :query ""}]]})

(defn query-executed [{:keys [db]} [_ dsid query]]
  (let [source (or (core/get-source db dsid)
                   ;; It might be better to use last used source or offer choice
                   (first (vals (::state/sources db))))]
    {:fx [[::fx/open-query-editor {:source source
                                   :query query}]]}))
