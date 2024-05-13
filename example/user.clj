(ns user
  (:require
   [clojure.java.shell :refer [sh]]
   [clojure.string :as str]
   [daba.api :as db]
   [daba.viewer :as-alias dv]
   [portal.api :as p]
   [portal.viewer :as pv]))

(comment
  (do
    (def p (db/open))
    (add-tap #'db/submit))

  (do
    (remove-tap #'db/submit)
    (p/close))

  ;; Download sample dataset
  (sh "curl"
      "--output-dir" "tmp" "--create-dirs"
      "-O" "https://github.com/lerocha/chinook-database/raw/master/ChinookDatabase/DataSources/Chinook_Sqlite_AutoIncrementPKs.sqlite")

  (db/inspect "jdbc:sqlite:tmp/Chinook_Sqlite_AutoIncrementPKs.sqlite")

  (p/clear)

  (prn @p)

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
         [x y] (db/query->columns @p)]
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
         [category value] (db/query->columns @p)]
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
         [x y] (db/query->columns @p)]
     (pv/vega-lite
      {:$schema "https://vega.github.io/schema/vega-lite/v5.json"
       :data {:values values}
       :encoding {:x {:field x :type "nominal" #_"quantitative"}
                  :y {:field y :type "quantitative"}}
       :mark "line"}))))
