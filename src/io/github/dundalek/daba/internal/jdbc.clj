(ns io.github.dundalek.daba.internal.jdbc
  (:require
   [next.jdbc :as jdbc]
   [next.jdbc.datafy]
   [next.jdbc.result-set :as rs])
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
