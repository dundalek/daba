(ns io.github.dundalek.daba.app.core
  (:require
   [clojure.edn :as edn]
   [clojure.string :as str]
   [daba.viewer :as-alias dv]
   [io.github.dundalek.daba.app.state :as state]
   [portal.viewer :as pv]))

(def default-page-size 25)

;; Trying out if some sample query would be useful instead of empty string?
(def default-input-query "select * from mytable")

(def datomic-default-query '{:find [?e] :where [[?e :my/attr "foo"]]})

(defn datomic-inspect-attribute-query [attribute]
  {:find '[?entity-id ?attr-value]
   :where [['?entity-id attribute '?attr-value]]})

(defn next-cell-id [db]
  (let [{::state/keys [next-cell-id]} db]
    [(assoc db ::state/next-cell-id (inc next-cell-id))
     next-cell-id]))

; (defn update-cell [db cell-id f & args]
;   (apply swap! (get-in db [::state/cells cell-id]) f args)
;   db)

(defn set-cell [db cell-id value]
  (assoc-in db [::state/cells cell-id] value))

(defn create-cell [db value]
  (let [[db cell-id] (next-cell-id db)]
    (set-cell db cell-id value)))

(defn remove-cell [db cell-id]
  (update db ::state/cells dissoc cell-id))

(defn clear-cells [db]
  (update db ::state/cells empty))

;; The initial idea was to create datasource once, and use generated DSID
;; (Data Source ID) to pass around between portal client and server process
;; and use it for lookup. The challenge is to keep the datasource as long as
;; there exist taps referencing it.
;; So to make things easier for now we use db-spec (since it is just a value)
;; as DSID and we don't need to keep extra state. This has an overhead that
;; we construct a datasource for every request. It should be tolerable for
;; now and can be optimized in the future.
(defn last-used-dsid [db]
  ;; Probably would be better to offer some kind of select box
  (->> db
       ::state/cells
       vals
       (some (comp ::dv/dsid meta))))

(defn parse-db-spec [db-spec]
  (assert (or (string? db-spec) (map? db-spec)))
  (if (map? db-spec)
    db-spec
    (or (try
          (let [parsed (edn/read-string db-spec)]
            (when (map? parsed)
              parsed))
          (catch Exception _ignore))
        {:jdbcUrl (if (not (str/starts-with? db-spec "jdbc:"))
                    (str "jdbc:" db-spec)
                    db-spec)})))

(defn datomic-datasource? [db-spec]
  (some? (#{:datomic-local :cloud} (:server-type db-spec))))

(defn xtdb1-datasource? [opts]
  (contains? opts :xtdb/tx-log))

(defn coerce-query [query]
  (merge {:offset 0 :limit default-page-size}
         (if (string? query)
           {:statement query}
           query)))

(defn datomic-coerce-query [query]
  {:query (if (string? query)
            (try
              (let [parsed (edn/read-string query)]
                parsed)
              (catch Exception _ignore
                nil))
            query)
   :limit default-page-size
   :offset 0})

(defn table-data-query [table]
  {:table table
   :where :all
   :limit default-page-size
   :offset 0})

(defn wrap-exception [e]
  {::dv/error e})

(defn datasource-input-viwer [value]
  (pv/default
   (if (string? value)
     {:jdbcUrl value}
     value)
   ::dv/datasource-input))

(defn query-editor-viewer [results {:keys [query dsid]}]
  (-> results
      (vary-meta (fn [m]
                   (let [{:keys [columns]} m]
                     (cond-> (assoc m
                                    ::pv/default ::dv/query-editor
                                    ::dv/query-editor {:query query}
                                    ::dv/dsid dsid)
                       columns (assoc ::pv/table {:columns columns})))))))

(defn datagrid-viewer [results {:keys [dsid query]}]
  (-> results
      (vary-meta (fn [m]
                   (let [{:keys [columns]} m]
                     (cond-> (assoc m
                                    ::pv/default ::dv/datagrid
                                    ::dv/datagrid {:query query}
                                    ::dv/dsid dsid)
                       columns (assoc ::pv/table {:columns columns})))))))

(defn empty-query-editor-viewer [opts]
  (query-editor-viewer [] opts))

(defn schema-list-viewer [schemas {:keys [dsid]}]
  (-> schemas
      (pv/default ::dv/schema-list)
      (vary-meta assoc ::dv/dsid dsid)))

(defn table-list-viewer [tables {:keys [dsid]}]
  (-> tables
      (pv/default ::dv/table-list)
      (vary-meta assoc ::dv/dsid dsid)))

(defn column-list-viewer [columns {:keys [dsid]}]
  (-> columns
      (pv/default ::dv/column-list)
      (vary-meta assoc ::dv/dsid dsid)))

(defn datomic-database-attributes-viewer [attributes {:keys [dsid]}]
  (-> attributes
      (pv/default ::dv/datomic-attribute-list)
      (vary-meta assoc ::dv/dsid dsid)))

(defn datomic-databases-viewer [databases {:keys [dsid]}]
  (-> databases
      (pv/default ::dv/datomic-database-list)
      (vary-meta assoc ::dv/dsid dsid)))

(defn datomic-database-namespaces-viewer [databases {:keys [dsid]}]
  (-> databases
      (pv/default ::dv/datomic-namespace-list)
      (vary-meta assoc ::dv/dsid dsid)))

(defn datomic-query-editor-viewer [results {:keys [query dsid]}]
  (-> results
      (vary-meta (fn [m]
                   (assoc m
                          ::pv/default ::dv/datomic-query-editor
                          ::dv/datomic-query-editor {:query query}
                          ::dv/dsid dsid)))))

(defn root-action-bar-viewer []
  (pv/default {} ::dv/root-action-bar))
