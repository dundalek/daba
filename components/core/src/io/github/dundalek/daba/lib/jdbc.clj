(ns io.github.dundalek.daba.lib.jdbc
  (:require
   [honey.sql :as hsql]
   [next.jdbc :as jdbc]
   [next.jdbc.datafy]
   [next.jdbc.result-set :as rs]
   [next.jdbc.sql :as sql])
  (:import
   (java.sql PreparedStatement ResultSet)))

;; === Beginning of hack

;; We cannot override result set builder to include columns metadata when using
;; jdbc/plan to realize subset of results. This is a hack reaching into private internals.
;; In `reduce-with-columns-meta` we push a binding frame, run the reduction which
;; calls `rs/reduce-stmt` that has access to ResultSet, and read out the columns meta.
;; We set columns binding by replacing `rs/reduce-stmt` with `reuce-stmt-with-columns-hack`
;; which extracts the column names and sets them on the binding frame.

(def ^:dynamic *columns* nil)

(defn reduce-with-columns-meta [reducible xform]
  (binding [*columns* true]
    (-> (with-meta
          (->> reducible
               (into [] xform))
          {:columns *columns*}))))

(defn reduce-stmt-with-columns-hack
  [^PreparedStatement stmt f init opts]
  (if-let [rs (#'rs/stmt->result-set stmt opts)]
    (do
      (when *columns*
        (let [rsmeta (.getMetaData rs)
              columns (rs/get-column-names rsmeta opts)]
          (set! *columns* columns)))
      (#'rs/reduce-result-set rs f init opts))
    (f init {:next.jdbc/update-count (.getUpdateCount stmt)})))

(alter-var-root #'rs/reduce-stmt (constantly reduce-stmt-with-columns-hack))

;; === End of hack

;; Delegates to passed row-builder to create rows, wraps final result with passed metadata
(defrecord CustomResultSetBuilder [row-builder ^ResultSet metadata]
  rs/RowBuilder
  (->row [_]
    (rs/->row row-builder))
  (column-count [_]
    (rs/column-count row-builder))
  (with-column [_ row i]
    (rs/with-column row-builder row i))
  (with-column-value [_ row col v]
    (rs/with-column-value row-builder row col v))
  (row! [_ row]
    (rs/row! row-builder row))

  rs/ResultSetBuilder
  (->rs [_]
    (transient []))
  (with-row [_ mrs row]
    (conj! mrs row))
  (rs! [_ mrs]
    (with-meta
      (persistent! mrs)
      metadata)))

;; Returns results as maps, adds column order as metadata so that UI can display columns in query order
(defn as-maps-with-columns-meta [^ResultSet rs opts]
  (let [rsmeta (.getMetaData rs)
        cols (rs/get-column-names rsmeta opts)
        row-builder (rs/->MapResultSetBuilder rs rsmeta cols)
        metadata {:columns cols}]
    (->CustomResultSetBuilder row-builder metadata)))

(defn metadata-result-set [rs]
  (rs/datafiable-result-set rs {:builder-fn rs/as-unqualified-kebab-maps}))

(defn get-tables [ds schema-name]
  (with-open [con (jdbc/get-connection ds)]
    (-> (.getMetaData con)
        (.getTables nil schema-name nil nil)
        (metadata-result-set))))

(defn get-schemas [ds]
  (with-open [con (jdbc/get-connection ds)]
    (->> (-> (.getMetaData con)
             (.getSchemas)
             (metadata-result-set))
         (map #(select-keys % [:table-schem]))
         (distinct)
         (sort-by :table-schem))))

(defn get-columns [ds table-name]
  (with-open [con (jdbc/get-connection ds)]
    (-> (.getMetaData con)
        (.getColumns nil nil table-name nil)
        (metadata-result-set))))

(defn execute-string-query [ds query]
  (let [{:keys [statement offset limit]} query]
    ;; We want to prevent bringing the viewer down in case there is a select with millions of rows.
    ;; We still iterate over them on the client runtime, but at least do not realize everything.
    ;; Using plan with reductinos is more efficient than execute! which realizes all results.
    ;; Maybe consider a heuristic and only try to limit SELECT queries.
    (reduce-with-columns-meta
     (jdbc/plan ds [statement] {:builder-fn rs/as-maps})
     (comp (drop offset)
           (take limit)
           (map #(rs/datafiable-row % ds {}))))))

(defn execute-honey-query [ds query]
  (let [sql (hsql/format query)]
    (jdbc/execute! ds sql {:builder-fn as-maps-with-columns-meta})))

(defn execute-structured-query [ds query]
  (let [{:keys [table where limit offset]} query]
    (sql/find-by-keys ds table where
                      {:limit limit
                       :offset offset
                       :builder-fn as-maps-with-columns-meta})))

(comment
  (get-tables "jdbc:sqlite:tmp/Chinook_Sqlite_AutoIncrementPKs.sqlite" nil))
