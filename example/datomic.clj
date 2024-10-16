(ns datomic
  (:require
   [clojure.java.io :as io]
   [daba.api :as db]
   [daba.viewer :as-alias dv]
   [datomic.client.api :as d]
   [portal.api :as p]))

(comment
  ;; Open Portal window with Daba extensions and set up tap>
  (do
    (def p (db/open))
    (add-tap #'db/submit))

  ;; Close the window and remove tap>
  (do
    (remove-tap #'db/submit)
    (p/close))

  ;; Download sample datasets and unzip to tmp/
  ;; https://datomic-samples.s3.amazonaws.com/datomic-samples-2020-07-07.zip
  ;; Source: https://docs.datomic.com/datomic-local.html#samples
  (db/inspect
   {:server-type :datomic-local
    :system "datomic-samples"
    :storage-dir (.getAbsolutePath (io/file "tmp/"))})

  ;; Click "schema", then "query" on movies and paste the query
  '{:find [?movie-title]
    :where [[_ :movie/title ?movie-title]]})

(comment
  (def client-opts {:server-type :datomic-local
                    :system "datomic-dev"
                    :storage-dir (.getAbsolutePath (io/file "tmp/"))})

  ;; Create sample database and populate data
  (do
    (def client (d/client client-opts))

    (d/delete-database client {:db-name "movies"})
    (d/create-database client {:db-name "movies"})
    (def conn (d/connect client {:db-name "movies"}))

    (def movie-schema [{:db/ident :movie/title
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
                        :db/doc "The year the movie was released in theaters"}])

    (d/transact conn {:tx-data movie-schema})

    (def movies-data [{:movie/title "The Goonies"
                       :movie/genre "action/adventure"
                       :movie/release-year 1985}
                      {:movie/title "Commando"
                       :movie/genre "thriller/action"
                       :movie/release-year 1985}
                      {:movie/title "Repo Man"
                       :movie/genre "punk dystopia"
                       :movie/release-year 1984}])

    (d/transact conn {:tx-data movies-data}))

  (db/inspect client-opts)

  ;; Quering the database directlys for testing
  (def conn (d/connect client {:db-name "movies"}))
  (def db (d/db conn))

  (d/q '[:find ?movie-title
         :where [_ :movie/title ?movie-title]]
       db)

  (d/db-stats db))
