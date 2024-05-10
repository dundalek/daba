(ns io.github.dundalek.daba.app
  (:require
   [clojure.string :as str]
   [daba.viewer :as-alias dv]
   [io.github.dundalek.daba.app.event :as event]
   [io.github.dundalek.daba.app.frame :as frame]
   [io.github.dundalek.daba.app.state :as state]
   [io.github.dundalek.daba.lib.jdbc :as dbc]
   [io.github.dundalek.daba.lib.miniframe :as mf]
   [next.jdbc :as jdbc]
   [next.jdbc.sql :as sql]
   [portal.api :as p]
   [portal.runtime :as pruntime]
   [portal.viewer :as pv]))

(defonce !app-db (atom state/default-state))

(defonce frame (mf/make-frame {:app-db !app-db}))

(alter-var-root #'frame/*frame*
                (constantly
                 (assoc frame
                        :event @mf/!event-registry
                        :fx @mf/!fx-registry)))

(defn submit [value]
  (frame/dispatch (event/tap-submitted value)))

(defn load-viewers []
  (p/eval-str (slurp "src/daba/viewer.cljs")))

(defn query->columns [value]
  (-> (meta value)
      ::pv/table :columns))

(defn clear-values
  ([] (clear-values nil identity))
  ([_request done]
   (pruntime/clear-values
    nil
    (fn [_]
      (frame/dispatch (event/values-cleared))
      (done nil)))))

(p/register! #'frame/dispatch)
(pruntime/register! #'clear-values {:name `pruntime/clear-values})

(defn open
  ([] (open nil))
  ([opts]
   (p/open (merge {:value !app-db
                   :on-load load-viewers}
                  opts))))

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
