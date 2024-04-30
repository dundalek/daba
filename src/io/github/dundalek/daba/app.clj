(ns io.github.dundalek.daba.app
  (:require
   [io.github.dundalek.daba.app.event :as event]
   [io.github.dundalek.daba.app.fx :as fx]
   [io.github.dundalek.daba.app.state :as state]
   [next.jdbc :as jdbc]
   [portal.api :as p]
   [io.github.dundalek.daba.internal.jdbc :as dbc]))

(defonce !app-db (atom state/default-state))

(def effects
  {::fx/inspect-database fx/inspect-database
   ::fx/inspect-tables fx/inspect-tables
   ::fx/inspect-columns fx/inspect-columns})

(defn fx! [[fx-name arg]]
  (let [fx-handler (get effects fx-name)]
    (fx-handler arg)))

(defn db-handle! [handler]
  (fn [event]
    (swap! !app-db handler event)))

(defn fx-handle! [handler]
  (fn [event]
    (let [{:keys [db fx]} (handler {:db @!app-db} event)]
      (when db
        (reset! !app-db db))
      (run! fx! fx))))

(def events
  {::event/source-added (db-handle! event/source-added)
   ::event/database-inspected (fx-handle! event/database-inspected)
   ::event/tables-inspected (fx-handle! event/tables-inspected)
   ::event/columns-inspected (fx-handle! event/columns-inspected)})

(defn dispatch [[event-name :as event]]
  (let [handler (get events event-name)]
    (handler event)
    nil))

(defn inspect-database! [db-spec]
  (let [dsid (str (gensym "dsid-"))
        ds (jdbc/get-datasource db-spec)
        source {::state/ds ds
                ::state/db-spec db-spec
                ::state/dsid dsid}]
    (dispatch [::event/source-added dsid source])
    (dispatch [::event/database-inspected dsid])))

(p/register! #'dispatch)

(comment
  (def db-spec "jdbc:duckdb:tmp/duck-data") ; on disk
  (def db-spec "jdbc:sqlite:tmp/Chinook_Sqlite_AutoIncrementPKs.sqlite")
  (def ds (jdbc/get-datasource db-spec))

  (p/eval-str (slurp "src/io/github/dundalek/daba/viewer.cljs"))

  (inspect-database! db-spec)

  (dbc/get-schemas ds)

  (dbc/get-tables ds "main")

  (reset! !app-db state/default-state)
  @!app-db
  (def dsid (-> @!app-db
                ::state/sources
                first
                key))

  (dispatch [::event/database-inspected dsid])

  (dispatch [::event/tables-inspected dsid "main"])

  (dispatch [::event/columns-inspected dsid "pushes"])

  (->> (dbc/get-columns ds "pushes")
       count)

  (p/docs))
