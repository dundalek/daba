(ns daba.internal
  (:require
   [next.jdbc :as jdbc]
   [next.jdbc.datafy]
   [next.jdbc.result-set :as result-set]
   [portal.api :as p]
   [portal.viewer :as pv]))

(defn inspector-viewer
  ;; Specifying default viewer with metadata did not seem to work reliably, perhaps due to equality not considering metadata?
  ;; As a workaround always wrapping in a hiccup viewer seems to work better.
  ([value] (inspector-viewer value {}))
  ([value opts]
   (pv/hiccup
    [::pv/inspector
     opts
     value])))

(defn inspector-seq-viewer
  ([coll] (inspector-seq-viewer {} coll))
  ([opts coll] (inspector-viewer coll opts)))

(defn query-table-data [ds table-name]
  ;; TODO: escape table-name
  (->> (with-meta
         (jdbc/execute! ds [(str "select * from " table-name)])
         {:daba.viewer/paginator {:viewer {:portal.viewer/default :portal.viewer/table}}})
       (inspector-seq-viewer
        {:portal.viewer/default :daba.viewer/paginator})))

#_(defn inspect-tables [ds]
    (with-open [con (jdbc/get-connection ds)]
      (let [table-name nil ;"Customer"
            dbmeta (.getMetaData con)
            table-list (->> (.getColumns dbmeta nil nil table-name nil)
                            result-set/datafiable-result-set
                            (group-by :TABLE_NAME)
                            (sort-by key)
                            (map (fn [[table-name columns]]
                                   (with-meta
                                     {:aname table-name
                                      :columns (count columns)}
                                     {`clojure.core.protocols/nav
                                      (fn [_coll key _value]
                                        (case key
                                          :columns columns
                                          :aname (query-table-data ds table-name)
                                          nil))}))))]
        (p/submit (with-meta table-list {:portal.viewer/default :portal.viewer/table})))))

(defn inspect-schema [ds columns]
  (let [grouped (->> columns
                     (group-by :TABLE_NAME))]
    (inspector-viewer
     (with-meta
       (->> grouped
            keys
            sort
            (map (fn [aname]
                   (pv/hiccup [:div aname]))))
       {`clojure.core.protocols/nav
        (fn [_coll key [_ value]]
          (query-table-data ds value))}))))

(defn inspect-schemas [ds columns]
  (let [grouped (->> columns
                     (group-by :TABLE_SCHEM))]
    (if (and (= (count grouped) 1)
             (nil? (first (keys grouped))))
      ;; no schemas, e.g. sqlite
      (inspect-schema ds columns)
      ;; schemas, e.g. postgres, duckdb
      (with-meta
        (->> grouped
             keys
             sort
             (map (fn [schema]
                    (pv/hiccup [:div schema]))))
        {`clojure.core.protocols/nav
         (fn [_coll _key [_ value]]
           (inspect-schema ds (get grouped value)))}))))

(defn inspect-database [db-spec]
  (p/submit
   (let [ds (jdbc/get-datasource db-spec)]
     (with-open [con (jdbc/get-connection ds)]
       (let [dbmeta (.getMetaData con)
             columns (->> (.getColumns dbmeta nil nil nil nil)
                          result-set/datafiable-result-set)]
         (inspect-schemas ds columns))))))

(defn metadata-result-set [rs]
  (result-set/datafiable-result-set rs {:builder-fn result-set/as-unqualified-kebab-maps}))

(comment
  (defn on-query [query]
    (p/submit
     ; {:query query}
     (->> (with-meta
            (jdbc/execute! ds [query])
            {:daba.viewer/paginator {:viewer {:portal.viewer/default :portal.viewer/table}}})
          (inspector-seq-viewer
           {:portal.viewer/default :daba.viewer/paginator}))))

  (p/submit (inspect-schemas ds columns))

  (inspect-database db-spec)

  (tap> [(pv/hiccup [:div "Hello"])
         (pv/hiccup [:div "world"])])

  (def db-spec
    "jdbc:sqlite:tmp/Chinook_Sqlite_AutoIncrementPKs.sqlite")

  (def db-spec
    (str "jdbc:" (System/getenv "POSTGRES_URI")))

  (def ds (jdbc/get-datasource db-spec))
  (def con (jdbc/get-connection ds))

  (jdbc/execute! ds ["select * from Artist limit 10"])

  (def db-spec "jdbc:duckdb:") ; in-memory
  (def db-spec "jdbc:duckdb:tmp/duck-data") ; on disk

  (jdbc/execute! ds ["SELECT * FROM read_csv('tmp/githut-csv/gh-push-event.json.csv') limit 10;"])
  (jdbc/execute! ds ["CREATE TABLE pushes AS SELECT * FROM read_csv('tmp/githut-csv/gh-push-event.json.csv');"])
  (jdbc/execute! ds ["select * from pushes limit 10"])
  (jdbc/execute! ds ["show tables"])
  (jdbc/execute! ds ["describe pushes"])

  (with-open [con (.getConnection ds)]
    (->> (.getCatalogs (.getMetaData con))
         (metadata-result-set)))

  (with-open [con (.getConnection ds)]
    (->> (.getSchemas (.getMetaData con))
         (metadata-result-set)))

  (with-open [con (.getConnection ds)]
    (->> (.getTables (.getMetaData con) nil nil nil nil)
         (metadata-result-set)
         (map :table-type)
         frequencies))

  (with-open [con (.getConnection ds)]
    (->> (.getTables (.getMetaData con) nil "public" nil (into-array ["TABLE"]))
         (metadata-result-set)
         (take 10)))

  (with-open [con (.getConnection ds)]
    (->> (.getColumns (.getMetaData con) nil "public" nil nil)
         (metadata-result-set)
         (take 10)))

  (->> tables
       (map :TABLE_TYPE)
       frequencies)

  (->> tables
       count)

  ; :pg_class/TABLE_NAME "playing_with_neon",
  (->> tables
       (filter (comp #{"TABLE"} :TABLE_TYPE)))

  ;; https://docs.oracle.com/javase/8/docs/api/java/sql/DatabaseMetaData.html
  ;; list columns
  (let [table-name nil ;"Customer"
        dbmeta (.getMetaData con)]
    (def columns
      (->> (.getColumns dbmeta nil nil table-name nil)
           result-set/datafiable-result-set)))
           ; (group-by :TABLE_NAME)
           ; (keys)
           ; (sort))
           ; (map :TABLE_NAME)
           ; frequencies))

  ; :TABLE_SCHEM
  ; {"information_schema" 697, "pg_catalog" 1358, "public" 3}
  (->> columns
       (filter (comp #{"public"} :TABLE_SCHEM)))

  (->> columns
       (group-by :TABLE_SCHEM)
       keys)

  (p/open {:mode :dev
           :on-load on-load})
  (add-tap p/submit)

  (p/eval-str (slurp "src/daba/viewer.cljs"))

  (tap>
   (with-meta
     (->> (range 100)
          (map  #(str "Slide " %)))
     {:portal.viewer/default :daba.viewer/paginator
      :daba.viewer/paginator {:viewer {:portal.viewer/default :portal.viewer/tree}
                              :page-size 5}}))

  (tap>
   (with-meta
     {}
     {:portal.viewer/default :daba.viewer/query-input}))

  (p/register! #'on-query)

  (defn row-meta [value]
    (with-meta
      value
      {:portal.viewer/for {:columns :portal.viewer/pr-str}}))

  (p/submit
   (with-meta
     (->> [{:name "foo"}
           {:name "bar"}]
          (map #(-> %
                    (assoc ::actions (:name %))
                    (with-meta {:portal.viewer/for {::actions :daba.viewer/hello}}))))
     {:portal.viewer/default :portal.viewer/table
      :portal.viewer/table {:columns [:name ::actions]}}))

  (p/submit
   (->> [{:name "foo"}
         {:name "bar"}]
        (map #(-> %
                  (with-meta {::pv/default :daba.viewer/action-row
                              :daba.viewer/action-row
                              {:row-meta {::pv/default :daba.viewer/name-label}}})))))

  (p/submit
   (with-meta
     (->> [{:name "foo"}
           {:name "bar"}]))))
