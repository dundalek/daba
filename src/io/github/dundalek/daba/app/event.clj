(ns io.github.dundalek.daba.app.event
  (:require
   [io.github.dundalek.daba.app.state :as state]
   [io.github.dundalek.daba.app.fx :as-alias fx]))

(defn source-added [db [_ dsid source]]
  (update db ::state/sources assoc dsid source))

(defn database-inspected [{:keys [db]} [_ dsid]]
  (let [source (get-in db [::state/sources dsid])]
    {:fx [[::fx/inspect-database source]]}))

(defn tables-inspected [{:keys [db]} [_ dsid schema-name]]
  (let [source (get-in db [::state/sources dsid])]
    {:fx [[::fx/inspect-tables {:source source
                                :schema-name schema-name}]]}))

(defn columns-inspected [{:keys [db]} [_ dsid table-name]]
  (let [source (get-in db [::state/sources dsid])]
    {:fx [[::fx/inspect-columns {:source source
                                 :table-name table-name}]]}))

(defn table-data-inspected [{:keys [db]} [_ dsid table-name]]
  (let [source (get-in db [::state/sources dsid])]
    {:fx [[::fx/inspect-table-data {:source source
                                    :table-name table-name}]]}))

(defn query-editor-opened [{:keys [db]} [_ dsid]]
  (let [source (get-in db [::state/sources dsid])]
    {:fx [[::fx/open-query-editor {:source source
                                   :query ""}]]}))

(defn query-executed [{:keys [db]} [_ dsid query]]
  (let [source (get-in db [::state/sources dsid])]
    {:fx [[::fx/open-query-editor {:source source
                                   :query query}]]}))
