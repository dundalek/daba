(ns io.github.dundalek.daba.app.event
  (:require
   [daba.viewer :as-alias dv]
   [io.github.dundalek.daba.app.core :as core]
   [io.github.dundalek.daba.app.fx :as fx]
   [io.github.dundalek.daba.app.state :as state]
   [io.github.dundalek.daba.internal.miniframe :refer [def-event-db def-event-fx]]
   [portal.viewer :as-alias pv]))

(def default-page-size 100)

;; Helpers

(defn remove-nth [coll n]
  (concat
   (take n coll)
   (drop (inc n) coll)))

(comment
  [(remove-nth [1 2 3] 0)
   (remove-nth [1 2 3] 1)
   (remove-nth [1 2 3] 2)])

(defn coerce-query [query]
  (merge {:offset 0 :limit default-page-size}
         (if (string? query)
           {:statement query}
           query)))

;; Events

(def-event-db source-added [db {:keys [dsid source]}]
  (update db ::state/sources assoc dsid source))

(def-event-fx database-inspected
  ([{:keys [db]} dsid]
   {:fx [(fx/inspect-database (core/get-source db dsid))]}))

(def-event-fx tables-inspected [{:keys [db]} {:keys [dsid schema]}]
  {:fx [(fx/inspect-tables {:source (core/get-source db dsid)
                            :schema-name schema})]})

(def-event-fx columns-inspected [{:keys [db]} {:keys [dsid table]}]
  {:fx [(fx/inspect-columns {:source (core/get-source db dsid)
                             :table-name table})]})

(def-event-fx table-data-inspected [{:keys [db]} {:keys [dsid table]}]
  {:fx [(fx/inspect-table-data {:source (core/get-source db dsid)
                                :query-map {:table table
                                            :where :all
                                            :limit default-page-size
                                            :offset 0}})]})

(def-event-fx datagrid-query-changed [{:keys [db]} {:keys [dsid path query-map]}]
  (assert (= (count path) 1) "Only supporting top level list for now")
  (let [source (core/get-source db dsid)
        !query-atom (nth (::state/taps db) (first path))]
    {:fx [(fx/execute-query-map {:source source
                                 :query-map query-map
                                 :!query-atom !query-atom})]}))

(def-event-fx query-editor-opened [{:keys [db]} dsid]
  {:fx [(fx/open-query-editor {:source (core/get-source db dsid)
                               :query (coerce-query "")})]})

(def-event-fx new-query-executed [{:keys [db]} {:keys [dsid query]}]
  {:fx [(fx/open-query-editor {:source (core/get-source db dsid)
                               :query (coerce-query query)})]})

(def-event-fx query-executed [{:keys [db]} {:keys [dsid query path]}]
  (let [source (or (core/get-source db dsid)
                   ;; It might be better to use last used source or offer choice
                   (first (vals (::state/sources db))))
        !query-atom (nth (::state/taps db) (first path))]
    {:fx [(fx/execute-query {:source source
                             :query (coerce-query query)
                             :!query-atom !query-atom})]}))

(def-event-db tap-submitted [db value]
  (core/append-tap db value))

(def-event-db removable-tap-submitted [db value]
  (let [wrap-with-meta (fn [value]
                         (with-meta
                           value
                           {::pv/default ::dv/removable-item
                            ::dv/removable-item {:wrapped-meta (meta value)}}))
        wrapped (if (instance? clojure.lang.IAtom value)
                  (swap! value wrap-with-meta)
                  (wrap-with-meta value))]
    (core/append-tap db wrapped)))

(def-event-db tap-removed [db path]
  (assert (= (count path) 1) "Only supporting top level list for now")
  (update db ::state/taps
          (fn [coll]
            (with-meta
              (remove-nth coll (first path))
              (meta coll)))))

(def-event-db datasource-input-opened [db _]
  (removable-tap-submitted
   db
   (with-meta {}
     {::pv/default ::dv/new-datasource})))
