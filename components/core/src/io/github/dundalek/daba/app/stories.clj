(ns io.github.dundalek.daba.app.stories
  (:require
   [io.github.dundalek.daba.app :as app]
   [io.github.dundalek.daba.app.event :as event]
   [io.github.dundalek.daba.app.frame :as frame]
   [io.github.dundalek.daba.app.state :as state]
   [portal.viewer :as pv]))

(defn- latest-cell-id []
  (-> @app/!app-db ::state/cells first key))

(defn doc! [label]
  (frame/dispatch (event/tap-submitted label)))

(def ^:private section-style
  {:gap 9
   :max-width 896
   :padding 40
   :display :flex
   :flex-direction :column
   :box-sizing :border-box})

(defn cells->docs []
  (for [[label v] (->> @app/!app-db ::state/cells vals reverse
                       (cons "Datasource Input")
                       (partition 2))]
    (let [viewer (some-> v meta ::pv/default)]
      [label
       {:hiccup
        [:div {:style section-style}
         [:h2 label]
         [:div (pr-str viewer)]
         [::pv/inspector {} v]]}])))

(defn sql-doc-tree []
  (with-redefs [event/schedule-async-call (fn [f] (f))]
    (frame/dispatch (event/values-cleared))
    (let [jdbc-url "jdbc:sqlite:tmp/Chinook_Sqlite_AutoIncrementPKs.sqlite"
          dsid {:jdbcUrl jdbc-url}
          datasource-cell-id (latest-cell-id)]

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
                                                    :query {:statement "select * from Artist"}})))))

(defn datomic-doc-tree []
  (with-redefs [event/schedule-async-call (fn [f] (f))]
    (frame/dispatch (event/values-cleared))
    (let [client-args {:server-type :datomic-local
                       :system "datomic-samples"
                       :storage-dir "/home/me/projects/daba/tmp/datomic-data"}
          connection-args {:db-name "movies"}
          dsid {:client-args client-args
                :connection-args connection-args}
          datasource-cell-id (latest-cell-id)]

      (doc! "Inspect Databases")
      (frame/dispatch
       (event/datasource-input-schema-triggered
        {:cell-id datasource-cell-id :value client-args}))

      (doc! "Inspect Attribute Namespaces")
      (frame/dispatch (event/datomic-database-inspected {:dsid client-args :db-name "movies"}))

      (doc! "Inspect Attributes")
      (frame/dispatch (event/datomic-database-attributes-inspected {:dsid client-args :db-name "movies"}))

      (doc! "Browse Attribute Data")
      (frame/dispatch (event/datomic-attribute-inspected {:dsid dsid :attribute :movie/title}))

      (doc! "Edit and Execute Datalog Query")
      (frame/dispatch (event/datomic-query-triggered {:dsid dsid :db-name "movies"}))
      (frame/dispatch (event/datomic-query-editor-executed
                       {:cell-id (latest-cell-id)
                        :dsid dsid
                        :query "{:find [?entity-id ?attr-value], :where [[?entity-id :movie/genre ?attr-value]]}"})))))

(comment
  (tap>
   {:cljdoc.doc/tree
    ["Daba"
     (into ["SQL"]
           (do
             (sql-doc-tree)
             (cells->docs)))
     (into ["Datomic"]
           (do
             (datomic-doc-tree)
             (cells->docs)))]}))
