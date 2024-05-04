(ns io.github.dundalek.daba.app
  (:require
   [daba.viewer :as-alias dv]
   [io.github.dundalek.daba.app.event :as event]
   [io.github.dundalek.daba.app.frame :as frame]
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
   ::fx/open-query-editor #'fx/open-query-editor
   ::fx/execute-query #'fx/execute-query
   ::fx/execute-query-map #'fx/execute-query-map})

(def event
  {::event/source-added (mf/db-handler #'event/source-added)
   ::event/database-inspected (mf/fx-handler #'event/database-inspected)
   ::event/tables-inspected (mf/fx-handler #'event/tables-inspected)
   ::event/columns-inspected (mf/fx-handler #'event/columns-inspected)
   ::event/table-data-inspected (mf/fx-handler #'event/table-data-inspected)
   ::event/datagrid-query-changed (mf/fx-handler #'event/datagrid-query-changed)
   ::event/query-editor-opened (mf/fx-handler #'event/query-editor-opened)
   ::event/new-query-executed (mf/fx-handler #'event/new-query-executed)
   ::event/query-executed (mf/fx-handler #'event/query-executed)
   ::event/tap-submitted (mf/db-handler #'event/tap-submitted)
   ::event/removable-tap-submitted (mf/db-handler #'event/removable-tap-submitted)
   ::event/tap-removed (mf/db-handler #'event/tap-removed)})

(defonce !app-db (atom state/default-state))

(alter-var-root #'frame/*frame*
                (constantly
                 (mf/make-frame
                  {:event event
                   :fx fx
                   :app-db !app-db})))

(def dispatch frame/dispatch)

(p/register! #'frame/dispatch)

(defn inspect-database! [db-spec]
  (let [dsid (str (gensym "dsid-"))
        ds (jdbc/get-datasource db-spec)
        source {::state/ds ds
                ::state/db-spec db-spec
                ::state/dsid dsid}]
    (dispatch [::event/source-added dsid source])
    (dispatch [::event/database-inspected dsid])))

(defonce !taps
  (let [!taps (atom nil)
        watcher (fn [_ _ _ new-state]
                  (let [new-taps (::state/taps new-state)]
                    (when-not (identical? @!taps new-taps)
                      (reset! !taps new-taps))))]
    ;; Poor man's subscription
    (add-watch !app-db ::taps watcher)
    (watcher nil nil nil @!app-db)
    !taps))

(defn submit [value]
  (frame/dispatch [::event/tap-submitted value]))

(defn load-viewers []
  (p/eval-str (slurp "src/daba/viewer.cljs")))

(comment
  (def db-spec "jdbc:duckdb:tmp/duck-data") ; on disk
  (def db-spec "jdbc:sqlite:tmp/Chinook_Sqlite_AutoIncrementPKs.sqlite")
  (def ds (jdbc/get-datasource db-spec))

  (do
    (p/open {:value !taps
             :on-load load-viewers})
    (add-tap #'submit))

  (load-viewers)

  (inspect-database! db-spec)

  (p/docs)

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

  (dispatch [::event/new-query-executed {:dsid dsid :query "select * from Artist limit 10"}])
  (dispatch [::event/new-query-executed {:dsid dsid :query "select count(*) from Artist"}])

  (->> (dbc/get-columns ds "pushes")
       count)

  (->> (sql/find-by-keys ds "Album" :all)
       count)

  (doseq [i (range 5)]
    (tap> (str "Item " i)))

  (tap>
   (pv/default
    ["select * from Artist limit 100"

     "select count(*) from Artist"

     "select Artist.ArtistId, Artist.Name, count(*) as AlbumCount
    from Artist
    left join Album using (ArtistId)
    group by Artist.ArtistId
    order by AlbumCount desc"]

    ::pv/inspector))

  (dispatch [::event/query-executed dsid
             "select Artist.ArtistId, Artist.Name, count(*) as AlbumCount
    from Artist
    left join Album using (ArtistId)
    group by Artist.ArtistId
    order by AlbumCount desc"]))
