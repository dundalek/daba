(ns user
  (:require
   [clojure.edn :as edn]
   [clojure.java.shell :refer [sh]]
   [clojure.string :as str]
   [daba.api :as db]
   [daba.viewer :as-alias dv]
   [portal.api :as p]
   [portal.viewer :as pv]))

;; REPL primer
(comment
  (+ 1 2)

  (edn/read-string (slurp "deps.edn"))

  (tap> (edn/read-string (slurp "deps.edn"))))

(comment
  ;; Open Portal window with Daba extensions and set up tap>
  (do
    (def p (db/open))
    (add-tap #'db/submit))

  ;; Close the window and remove tap>
  (do
    (remove-tap #'db/submit)
    (p/close))

  ;; Download sample sqlite dataset
  (sh "curl"
      "--output-dir" "tmp" "--create-dirs"
      "-O" "https://github.com/lerocha/chinook-database/raw/master/ChinookDatabase/DataSources/Chinook_Sqlite_AutoIncrementPKs.sqlite")

  ;; Alternative way to inspect database besides typing into datasource input
  (db/inspect "jdbc:sqlite:tmp/Chinook_Sqlite_AutoIncrementPKs.sqlite")

  ;; Getting selected value
  @p

  ;; "Saved" queries in code
  (tap>
   (pv/default
    ["select * from Artist"

     "select count(*) from Artist"]
    ::pv/inspector))

  ;; Saved queries from file
  (tap>
   (->> (str/split (slurp "example/queries.sql") #"\n\n+")
        (map #(pv/default % ::dv/query))))

  ;; Bar chart
  (tap>
   (let [values @p
         [x y] (db/query->columns @p)]
     (pv/vega-lite
      {:$schema "https://vega.github.io/schema/vega-lite/v5.json"
       :data {:values values}
       :mark "bar"
       :encoding {:x {:field x
                      :type "nominal"
                      :axis {:labelAngle 0}}
                  :y {:field y :type "quantitative"}}})))

  ;; Pie chart
  (tap>
   (let [values @p
         [category value] (db/query->columns @p)]
     (pv/vega-lite
      {:$schema "https://vega.github.io/schema/vega-lite/v5.json"
       :data {:values values}
       :encoding {:theta {:field value
                          :type "quantitative"
                          :stack true}
                  :color {:field category
                          :type "nominal"
                          :legend nil}}
       :layer [{:mark {:type "arc", :outerRadius 80}}
               {:mark {:type "text", :radius 110}
                :encoding {:text {:field category :type "nominal"}}}]
       :view {:stroke nil}})))

  ;; Line chart
  (tap>
   (let [values @p
         [x y] (db/query->columns @p)]
     (pv/vega-lite
      {:$schema "https://vega.github.io/schema/vega-lite/v5.json"
       :data {:values values}
       :encoding {:x {:field x :type "nominal" #_"quantitative"}
                  :y {:field y :type "quantitative"}}
       :mark "line"})))

  (tap>
   (pv/markdown
    "# Conlusion

- Generic data viewer + REPL = composable workflows
- Enables form of end-user programming
  - Bypass application silos by accessing data directly
  - Customize workflows beyond imagination of vendors

# Source code

## [github.com/dundalek/daba](https://github.com/dundalek/daba)")))
