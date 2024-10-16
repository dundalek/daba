(ns xtdb
  (:require
   [daba.api :as db]
   [portal.api :as p]
   [xtdb.api :as xt]))

(comment
  ;; Open Portal window with Daba extensions and set up tap>
  (do
    (def p (db/open))
    (add-tap #'db/submit))

  ;; Close the window and remove tap>
  (do
    (remove-tap #'db/submit)
    (p/close))

  (def connection-args
    (let [base-dir "tmp/xtdb-dev"
          kv-store (fn [dir] {:kv-store {:xtdb/module 'xtdb.rocksdb/->kv-store
                                         :db-dir (str base-dir dir)
                                         :sync? true}})]
      {:xtdb/tx-log (kv-store "/tx-log")
       :xtdb/document-store (kv-store "/doc-store")
       :xtdb/index-store (kv-store "/index-store")}))

  (def movies-data [{:movie/title "The Goonies"
                     :movie/genre "action/adventure"
                     :movie/release-year 1985
                     :xt/id 1}
                    {:movie/title "Commando"
                     :movie/genre "thriller/action"
                     :movie/release-year 1985
                     :xt/id 2}
                    {:movie/title "Repo Man"
                     :movie/genre "punk dystopia"
                     :movie/release-year 1984
                     :xt/id 3}])

  ;; Insert sample data
  (with-open [xtdb-node (xt/start-node connection-args)]
    (xt/submit-tx xtdb-node (for [doc movies-data]
                              [:xtdb.api/put doc]))
    (xt/sync xtdb-node))

  ;; Open connection for inspection
  (db/inspect connection-args)

  ;; Querying manually
  (with-open [xtdb-node (xt/start-node connection-args)]
    (xt/q (xt/db xtdb-node)
          '{:find [title]
            :where [[_ :movie/title title]]})))
