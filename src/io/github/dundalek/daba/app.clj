(ns io.github.dundalek.daba.app
  (:require
   [io.github.dundalek.daba.app.event :as event]
   [io.github.dundalek.daba.app.fx :as fx]
   [io.github.dundalek.daba.app.state :as state]
   [next.jdbc :as jdbc]
   [portal.api :as p]))

(defonce !app-db (atom state/default-state))

(defn fx! [[fx-name arg]]
  (let [fx-handler (case fx-name
                     ::fx/inspect-database fx/inspect-database
                     ::fx/inspect-tables fx/inspect-tables)]
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

(defn dispatch [[event-name :as event]]
  (let [handler (case event-name
                  ::event/source-added (db-handle! event/source-added)
                  ::event/database-inspected (fx-handle! event/database-inspected)
                  ::event/tables-inspected (fx-handle! event/tables-inspected))]
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

(comment
  (def db-spec "jdbc:duckdb:tmp/duck-data") ; on disk
  (def ds (jdbc/get-datasource db-spec))

  (p/eval-str (slurp "src/io/github/dundalek/daba/viewer.cljs"))

  (inspect-database! db-spec)

  (fx/get-schemas ds)

  (fx/get-tables ds "main")

  (reset! !app-db state/default-state)
  @!app-db
  (def dsid (-> @!app-db
                ::state/sources
                first
                key))

  (dispatch [::event/database-inspected dsid "main"])

  (dispatch [::event/tables-inspected dsid "main"]))
