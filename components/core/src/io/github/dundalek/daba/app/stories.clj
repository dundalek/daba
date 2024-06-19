(ns io.github.dundalek.daba.app.stories
  (:require
   [io.github.dundalek.daba.app :as app]
   [io.github.dundalek.daba.app.event :as event]
   [io.github.dundalek.daba.app.frame :as frame]
   [io.github.dundalek.daba.app.state :as state]
   [portal.viewer :as pv]))

(defn- latest-cell-id []
  (-> @app/!app-db ::state/cells first key))

(def ^:private section-style
  {:gap 9
   :max-width 896
   :padding 40
   :display :flex
   :flex-direction :column
   :box-sizing :border-box})

(defn generate-doc-tree []
  (with-redefs [event/schedule-async-call (fn [f] (f))]
    (frame/dispatch (event/values-cleared))
    (let [jdbc-url "jdbc:sqlite:tmp/Chinook_Sqlite_AutoIncrementPKs.sqlite"
          dsid {:jdbcUrl jdbc-url}
          datasource-cell-id (latest-cell-id)
          doc! (fn [label] (frame/dispatch (event/tap-submitted label)))]

      (doc! "Inspect Database Tables")
      (frame/dispatch
       (event/datasource-input-schema-triggered
        {:cell-id datasource-cell-id :value jdbc-url}))

      (doc! "Inspect Table Columns")
      (frame/dispatch (event/table-columns-inspected {:dsid dsid :table "Artist"}))

      (doc! "Inspect Table Data")
      (frame/dispatch (event/table-data-inspected {:dsid dsid :table "Album"}))

      (doc! "Edit and Execute Query")
      (frame/dispatch (event/datasource-input-query-triggered {:cell-id datasource-cell-id :value jdbc-url}))

      (frame/dispatch (event/query-editor-executed {:cell-id (latest-cell-id)
                                                    :dsid dsid
                                                    :query {:statement "select * from Artist"}}))

      (into ["SQL"]
            (for [[label v] (->> @app/!app-db ::state/cells vals reverse
                                 (cons "Datasource Input")
                                 (partition 2))]
              (let [viewer (some-> v meta ::pv/default)]
                [label
                 {:hiccup
                  [:div {:style section-style}
                   [:h2 label]
                   [:div (pr-str viewer)]
                   [::pv/inspector {} v]]}]))))))

(comment
  (tap>
   {:cljdoc.doc/tree
    ["Daba"
     (generate-doc-tree)
     ["Datomic"]]}))
