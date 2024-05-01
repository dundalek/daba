(ns io.github.dundalek.daba.app
  (:require
   [io.github.dundalek.daba.app.event :as event]
   [io.github.dundalek.daba.app.fx :as fx]
   [io.github.dundalek.daba.app.state :as state]
   [next.jdbc :as jdbc]
   [portal.api :as p]
   [io.github.dundalek.daba.internal.jdbc :as dbc]
   [io.github.dundalek.daba.internal.miniframe :as mf]))

(def fx
  {::fx/inspect-database fx/inspect-database
   ::fx/inspect-tables fx/inspect-tables
   ::fx/inspect-columns fx/inspect-columns})

(def event
  {::event/source-added (mf/db-handler event/source-added)
   ::event/database-inspected (mf/fx-handler event/database-inspected)
   ::event/tables-inspected (mf/fx-handler event/tables-inspected)
   ::event/columns-inspected (mf/fx-handler event/columns-inspected)})

(defonce !app-db (atom state/default-state))

(def frame (mf/make-frame
            {:event event
             :fx fx
             :app-db !app-db}))

(defn dispatch [event]
  (mf/dispatch frame event))

(p/register! #'dispatch)

(defn inspect-database! [db-spec]
  (let [dsid (str (gensym "dsid-"))
        ds (jdbc/get-datasource db-spec)
        source {::state/ds ds
                ::state/db-spec db-spec
                ::state/dsid dsid}]
    (dispatch [::event/source-added dsid source])
    (dispatch [::event/database-inspected dsid])))

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
