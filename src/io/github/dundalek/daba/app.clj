(ns io.github.dundalek.daba.app
  (:require
   [clojure.string :as str]
   [daba.viewer :as-alias dv]
   [io.github.dundalek.daba.app.event :as event]
   [io.github.dundalek.daba.app.frame :as frame]
   [io.github.dundalek.daba.app.fx]
   [io.github.dundalek.daba.app.state :as state]
   [io.github.dundalek.daba.internal.jdbc :as dbc]
   [io.github.dundalek.daba.internal.miniframe :as mf]
   [next.jdbc :as jdbc]
   [next.jdbc.sql :as sql]
   [portal.api :as p]
   [portal.runtime :as pruntime]
   [portal.viewer :as pv]))

(defonce !app-db (atom state/default-state))

(alter-var-root #'frame/*frame*
                (constantly
                 (mf/make-frame
                  {:event @mf/!event-registry
                   :fx @mf/!fx-registry
                   :app-db !app-db})))

(def dispatch frame/dispatch)

(p/register! #'frame/dispatch)

(defn inspect-database! [db-spec]
  (dispatch (event/database-inspected db-spec))
  #_(let [dsid (str (gensym "dsid-"))
          ds (jdbc/get-datasource db-spec)
          source {::state/ds ds
                  ::state/db-spec db-spec
                  ::state/dsid dsid}]
      (dispatch (event/source-added {:dsid dsid :source source}))
      (dispatch (event/database-inspected dsid))))

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
  (frame/dispatch (event/removable-tap-submitted value)))

(defn load-viewers []
  (p/eval-str (slurp "src/daba/viewer.cljs")))

(defn query->columns [value]
  (-> (meta value)
      ::dv/removable-item :wrapped-meta
      ::pv/table :columns))

(defn clear-values
  ([] (clear-values nil identity))
  ([_request done]
   (pruntime/clear-values
    nil
    (fn [_]
      (dispatch (event/values-cleared))
      (done nil)))))

(pruntime/register! #'clear-values {:name `pruntime/clear-values})

(comment
  (def dsid "jdbc:sqlite:tmp/Chinook_Sqlite_AutoIncrementPKs.sqlite")
  (def dsid "jdbc:duckdb:tmp/duck-data") ; on disk
  (def dsid {:dbtype "h2" :dbname "tmp/example"})

  (def ds (jdbc/get-datasource dsid))

  (jdbc/execute! ds ["
create table address (
  id int auto_increment primary key,
  name varchar(32),
  email varchar(255)
)"])

  (do
    (def p (p/open {:value !taps
                    :on-load load-viewers}))
    (add-tap #'submit))

  (load-viewers)

  (inspect-database! dsid)

  (p/docs)

  (reset! !app-db state/default-state)
  @!app-db

  (dispatch (event/database-inspected dsid))

  (dispatch (event/datasource-input-opened dsid))
  (dispatch (event/datasource-input-opened ""))

  (tap>
   ["sqlite:tmp/Chinook_Sqlite_AutoIncrementPKs.sqlite"
    {:dbtype "h2" :dbname "tmp/example"}])

  (dispatch (event/tables-inspected {:dsid dsid :schema "main"}))
  (dispatch (event/columns-inspected {:dsid dsid :table "pushes"}))

  (dispatch (event/query-editor-opened dsid))

  (dispatch (event/new-query-executed {:dsid dsid :query "select * from Artist limit 10"}))
  (dispatch (event/new-query-executed {:dsid dsid :query "select count(*) from Artist"}))
  (dispatch (event/new-query-executed {:dsid dsid :query "select"}))

  (dbc/get-schemas ds)

  (dbc/get-tables ds "main")

  (->> (dbc/get-columns ds "pushes")
       count)

  (->> (sql/find-by-keys ds "Album" :all)
       count)

  (doseq [i (range 5)]
    (tap> (str "Item " i)))

  (tap>
   (pv/default
    ["select * from Artist"

     "select count(*) from Artist"]

    ::pv/inspector))

  (tap>
   (->> (str/split (slurp "example/queries.sql") #"\n\n+")
        (map #(pv/default % ::dv/query-editor))))

  (tap>
   (pv/vega-lite
    {:$schema "https://vega.github.io/schema/vega-lite/v5.json"
     :data
     {:values
      [{:a "A", :b 28}
       {:a "B", :b 55}
       {:a "C", :b 43}
       {:a "D", :b 91}
       {:a "E", :b 81}
       {:a "F", :b 53}]}
     :mark "bar"
     :encoding
     {:x
      {:field "a"
       :type "nominal"
       :axis {:labelAngle 0}}
      :y {:field "b", :type "quantitative"}}}))

  (tap>
   (let [values @p
         [x y] (query->columns @p)]
     (pv/vega-lite
      {:$schema "https://vega.github.io/schema/vega-lite/v5.json"
       :data {:values values}
       :mark "bar"
       :encoding {:x
                  {:field x
                   :type "nominal"
                   :axis {:labelAngle 0}}
                  :y {:field y :type "quantitative"}}})))

  (tap>
   (let [values @p
         [category value] (query->columns @p)]
     (pv/vega-lite
      {:$schema "https://vega.github.io/schema/vega-lite/v5.json"
       ; :description "A simple pie chart with labels."
       :data
       {:values values}
       :encoding
       {:theta {:field value
                :type "quantitative"
                :stack true}
        :color {:field category
                :type "nominal"
                :legend nil}}
       :layer
       [{:mark {:type "arc", :outerRadius 80}}
        {:mark {:type "text", :radius 110}
         :encoding {:text
                    {:field category :type "nominal"}}}]
       :view {:stroke nil}})))

  (tap>
   (let [values @p
         [x y] (query->columns @p)]
     (pv/vega-lite
      {:$schema "https://vega.github.io/schema/vega-lite/v5.json"
       :data {:values values}
       :encoding {:x {:field x :type "nominal" #_"quantitative"}
                  :y {:field y :type "quantitative"}}
       :mark "line"}))))
