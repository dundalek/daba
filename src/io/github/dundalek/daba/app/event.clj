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
