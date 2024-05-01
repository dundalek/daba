(ns io.github.dundalek.daba.app
  (:require
   [io.github.dundalek.daba.app.event :as event]
   [io.github.dundalek.daba.app.fx :as fx]
   [io.github.dundalek.daba.app.state :as state]
   [io.github.dundalek.daba.internal.jdbc :as dbc]
   [io.github.dundalek.daba.internal.miniframe :as mf]
   [next.jdbc :as jdbc]
   [next.jdbc.sql :as sql]
   [portal.api :as p]
   [portal.viewer :as pv]))

(def fx
  {::fx/inspect-database #'fx/inspect-database
   ::fx/inspect-tables #'fx/inspect-tables
   ::fx/inspect-columns #'fx/inspect-columns
   ::fx/inspect-table-data #'fx/inspect-table-data
   ::fx/open-query-editor #'fx/open-query-editor})

(def event
  {::event/source-added (mf/db-handler #'event/source-added)
   ::event/database-inspected (mf/fx-handler #'event/database-inspected)
   ::event/tables-inspected (mf/fx-handler #'event/tables-inspected)
   ::event/columns-inspected (mf/fx-handler #'event/columns-inspected)
   ::event/table-data-inspected (mf/fx-handler #'event/table-data-inspected)
   ::event/query-editor-opened (mf/fx-handler #'event/query-editor-opened)
   ::event/query-executed (mf/fx-handler #'event/query-executed)})

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

  (dispatch [::event/query-editor-opened dsid])

  (dispatch [::event/query-executed dsid "select * from Artist limit 100"])
  (dispatch [::event/query-executed dsid "select count(*) from Artist"])

  (->> (dbc/get-columns ds "pushes")
       count)

  (p/docs)

  (->> (sql/find-by-keys ds "Album" :all)
       count)

  (tap>
   (pv/default
    ["select * from Artist limit 100"

     "select count(*) from Artist"

     "select Artist.ArtistId, Artist.Name, count(*) as AlbumCount
    from Artist
    left join Album using (ArtistId)
    group by Artist.ArtistId
    order by AlbumCount desc"]

    ::pv/inspector)))
