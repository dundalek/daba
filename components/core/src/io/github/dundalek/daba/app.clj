(ns io.github.dundalek.daba.app
  (:require
   [clojure.java.io :as io]
   [daba.viewer :as-alias dv]
   [io.github.dundalek.daba.app.core :as core]
   [io.github.dundalek.daba.app.event :as event]
   [io.github.dundalek.daba.app.frame :as frame]
   [io.github.dundalek.daba.app.state :as state]
   [io.github.dundalek.daba.lib.jdbc :as dbc]
   [io.github.dundalek.daba.lib.miniframe :as mf]
   io.github.dundalek.daba.xtdb1.event ; for side-effects
   [next.jdbc :as jdbc]
   [next.jdbc.sql :as sql]
   [portal.api :as p]
   [portal.runtime :as pruntime]
   [portal.viewer :as pv]))

(defonce !app-db (atom state/default-state))

(alter-var-root #'frame/*frame*
                (constantly (mf/make-frame {:app-db !app-db})))

(defn submit [value]
  (frame/dispatch (event/tap-submitted value)))

(def flexlayout-react-asset-path "vendor/flexlayout-react/v0.7.15")

(defn load-viewers []
  (p/eval-str (pr-str '(do
                         (require '["react" :as react])
                         (require '["react-dom" :as react-dom])
                         (set! (.-React js/window) react)
                         (set! (.-ReactDOM js/window) react-dom))))
  (p/eval-str (pr-str (list 'js/eval (slurp (io/resource (str flexlayout-react-asset-path "/dist/flexlayout_min.js"))))))
  (p/eval-str (slurp (io/resource "io/github/dundalek/daba/ui/components/loading_indicator.cljs")))
  (p/eval-str (slurp (io/resource "daba/viewer.cljs")))
  (p/eval-str (slurp (io/resource "io/github/dundalek/daba/ui/viewers/root_docking.cljs")))
  (p/eval-str (pr-str (list 'daba.viewer/set-style-content!
                            "flexlayout-dark"
                            (slurp (io/resource (str flexlayout-react-asset-path "/style/dark.css"))))))
  (p/eval-str (pr-str (list 'daba.viewer/set-style-content!
                            "flexlayout-light"
                            (slurp (io/resource (str flexlayout-react-asset-path "/style/light.css")))))))

(defn query->columns [value]
  (-> (meta value)
      ::pv/table :columns))

(defn inspect-datasource [db-spec]
  (frame/dispatch (event/datasource-edit-triggered db-spec)))

(defn clear-values
  ([] (clear-values nil identity))
  ([_request done]
   (pruntime/clear-values
    nil
    (fn [_]
      (frame/dispatch (event/values-cleared))
      (done nil)))))

(defn open
  ([] (open nil))
  ([opts]
   (frame/dispatch (event/tap-submitted (core/root-action-bar-viewer)))
   (p/open (merge {:value !app-db
                   :on-load load-viewers}
                  opts))))

(pruntime/register! #'frame/dispatch)
(pruntime/register! #'clear-values {:name `pruntime/clear-values})

(defn after-ns-reload []
  ;; hook for clj-reload
  (load-viewers))

(comment
  (def dsid "jdbc:sqlite:tmp/Chinook_Sqlite_AutoIncrementPKs.sqlite")
  (def dsid "jdbc:duckdb:tmp/duck-data") ; on disk
  (def dsid {:dbtype "h2" :dbname "./tmp/example"})

  (def ds (jdbc/get-datasource dsid))

  (jdbc/execute! ds ["
create table address (
  id int auto_increment primary key,
  name varchar(32),
  email varchar(255)
)"])

  (do
    (def p (open))
    (add-tap #'submit)
    (tap> (pv/default "jdbc:sqlite:tmp/Chinook_Sqlite_AutoIncrementPKs.sqlite" ::dv/datasource)))

  (load-viewers)

  (frame/dispatch (event/root-viewer-switched))

  (frame/dispatch (event/tap-submitted [1 2 3]))
  (frame/dispatch (event/tap-submitted 456))

  (p/docs)

  (reset! !app-db state/default-state)
  (swap! !app-db assoc ::state/running-tasks 1)
  (swap! !app-db assoc ::state/running-tasks 0)
  @!app-db

  (tap> (pv/default dsid ::dv/datasource))
  (tap> (pv/default {:jdbcUrl dsid} ::dv/datasource))
  (tap> (pv/default {:jdbcUrl ""} ::dv/datasource))

  (tap> (pv/default dsid ::dv/datasource-input))
  (tap> (pv/default "" ::dv/datasource-input))

  (tap>
   ["sqlite:tmp/Chinook_Sqlite_AutoIncrementPKs.sqlite"
    {:dbtype "h2" :dbname "tmp/example"}])

  (frame/dispatch (event/datasource-schema-triggered dsid))
  (frame/dispatch (event/schema-tables-inspected {:dsid dsid :schema "main"}))
  (frame/dispatch (event/table-columns-inspected {:dsid dsid :table "Artist"}))

  (frame/dispatch (event/datasource-query-triggered dsid))

  (frame/dispatch (event/query-executed "select * from Artist limit 10"))
  (frame/dispatch (event/query-executed "select count(*) from Artist"))
  (frame/dispatch (event/query-executed "select"))

  (dbc/get-schemas ds)

  (dbc/get-tables ds "main")

  (->> (dbc/get-columns ds "pushes")
       count)

  (->> (sql/find-by-keys ds "Album" :all)
       count)

  (doseq [i (range 5)]
    (tap> (str "Item " i))))
