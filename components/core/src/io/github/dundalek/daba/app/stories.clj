(ns io.github.dundalek.daba.app.stories
  (:require
   [datomic.client.api :as d]
   [io.github.dundalek.daba.app :as app]
   [io.github.dundalek.daba.app.event :as event]
   [io.github.dundalek.daba.app.frame :as frame]
   [io.github.dundalek.daba.app.state :as state]
   [io.github.dundalek.daba.lib.datomic :as datomic]
   [io.github.dundalek.daba.xtdb1.event :as xtdb1.event]
   [io.github.dundalek.daba.xtdb1.lib :as xtdb1-lib]
   [portal.viewer :as pv]
   [xtdb.api :as xt]))

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

(defn datomic-ensure-sample-data! [{:keys [client-args connection-args]}]
  (let [{:keys [db-name]} connection-args
        client (d/client client-args)
        _ (when (empty? (datomic/get-databases client-args))
            (d/create-database client {:db-name db-name}))
        conn (d/connect client {:db-name db-name})
        movie-schema [{:db/ident :movie/title
                       :db/valueType :db.type/string
                       :db/cardinality :db.cardinality/one
                       :db/doc "The title of the movie"}
                      {:db/ident :movie/genre
                       :db/valueType :db.type/string
                       :db/cardinality :db.cardinality/one
                       :db/doc "The genre of the movie"}
                      {:db/ident :movie/release-year
                       :db/valueType :db.type/long
                       :db/cardinality :db.cardinality/one
                       :db/doc "The year the movie was released in theaters"}]
        _ (d/transact conn {:tx-data movie-schema})
        first-movies [{:movie/title "The Goonies"
                       :movie/genre "action/adventure"
                       :movie/release-year 1985}
                      {:movie/title "Commando"
                       :movie/genre "thriller/action"
                       :movie/release-year 1985}
                      {:movie/title "Repo Man"
                       :movie/genre "punk dystopia"
                       :movie/release-year 1984}]]
    (d/transact conn {:tx-data first-movies})))

(defn datomic-doc-tree []
  (with-redefs [event/schedule-async-call (fn [f] (f))]
    (frame/dispatch (event/values-cleared))
    (let [client-args {:server-type :datomic-local
                       :storage-dir :mem
                       :system "stories"}
          connection-args {:db-name "movies"}
          dsid {:client-args client-args
                :connection-args connection-args}
          datasource-cell-id (latest-cell-id)]
      (datomic-ensure-sample-data! dsid)

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

(defn xtdb-ensure-sample-data! [node-opts]
  (when (empty (xtdb1-lib/query node-opts '{:find [?e] :where [[?e :database/name _]]}))
    (let [my-docs [{:xt/id -1
                    :database/name "PostgreSQL"
                    :database/query-language "SQL"}
                   {:xt/id -2
                    :database/name "SQLite"
                    :database/query-language "SQL"}
                   {:xt/id -3
                    :database/name "DuckDB"
                    :database/query-language "SQL"}
                   {:xt/id -4
                    :database/name "Datomic"
                    :database/query-language "Datalog"}
                   {:xt/id -5
                    :database/name "XTDB1"
                    :database/query-language "Datalog"}]]
      (xt/submit-tx (xtdb1-lib/get-node node-opts)
                    (for [doc my-docs]
                      [:xtdb.api/put doc])))))

(defn xtdb-doc-tree []
  (with-redefs [event/schedule-async-call (fn [f] (f))]
    (frame/dispatch (event/values-cleared))
    (let [dsid {:xtdb/tx-log {}}
          datasource-cell-id (latest-cell-id)]
      (xtdb-ensure-sample-data! dsid)

      (doc! "Inspect Databases")
      (frame/dispatch
       (event/datasource-input-schema-triggered
        {:cell-id datasource-cell-id :value (pr-str dsid)}))

      (doc! "Browse Attribute Data")
      (frame/dispatch (xtdb1.event/attribute-inspected {:dsid dsid :attribute :database/name}))

      (doc! "Edit and Execute Datalog Query")
      (frame/dispatch (event/datasource-input-query-triggered {:cell-id datasource-cell-id :value (pr-str dsid)}))
      (frame/dispatch (xtdb1.event/query-editor-executed
                       {:cell-id (latest-cell-id)
                        :dsid dsid
                        :query "{:find [?name (count ?name)], :where [[_ :database/query-language ?name]]}"})))))

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
             (cells->docs)))
     (into ["XTDB"]
           (do
             (xtdb-doc-tree)
             (cells->docs)))]}))
