(ns io.github.dundalek.daba.app.event
  (:require
   [daba.viewer :as-alias dv]
   [io.github.dundalek.daba.app.core :as core]
   [io.github.dundalek.daba.app.state :as state]
   [io.github.dundalek.daba.app.fx :as-alias fx]
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

(defn source-added [db [_ dsid source]]
  (update db ::state/sources assoc dsid source))

(defn database-inspected [{:keys [db]} [_ dsid]]
  {:fx [[::fx/inspect-database (core/get-source db dsid)]]})

(defn tables-inspected [{:keys [db]} [_ dsid schema-name]]
  {:fx [[::fx/inspect-tables {:source (core/get-source db dsid)
                              :schema-name schema-name}]]})

(defn columns-inspected [{:keys [db]} [_ dsid table-name]]
  {:fx [[::fx/inspect-columns {:source (core/get-source db dsid)
                               :table-name table-name}]]})

(defn table-data-inspected [{:keys [db]} [_ dsid table-name]]
  {:fx [[::fx/inspect-table-data {:source (core/get-source db dsid)
                                  :query-map {:table table-name
                                              :where :all
                                              :limit default-page-size
                                              :offset 0}}]]})

(defn datagrid-query-changed [{:keys [db]} [_ {:keys [dsid path query-map]}]]
  (assert (= (count path) 1) "Only supporting top level list for now")
  (let [source (core/get-source db dsid)
        !query-atom (nth (::state/taps db) (first path))]
    {:fx [[::fx/execute-query-map {:source source
                                   :query-map query-map
                                   :!query-atom !query-atom}]]}))

(defn query-editor-opened [{:keys [db]} [_ dsid]]
  {:fx [[::fx/open-query-editor {:source (core/get-source db dsid)
                                 :query (coerce-query "")}]]})

(defn new-query-executed [{:keys [db]} [_ {:keys [dsid query]}]]
  {:fx [[::fx/open-query-editor {:source (core/get-source db dsid)
                                 :query (coerce-query query)}]]})

(defn query-executed [{:keys [db]} [_ {:keys [dsid query path]}]]
  (let [source (or (core/get-source db dsid)
                   ;; It might be better to use last used source or offer choice
                   (first (vals (::state/sources db))))
        !query-atom (nth (::state/taps db) (first path))]
    {:fx [[::fx/execute-query {:source source
                               :query (coerce-query query)
                               :!query-atom !query-atom}]]}))

(defn tap-submitted [db [_ value]]
  (core/append-tap db value))

(defn removable-tap-submitted [db [_ value]]
  (let [wrap-with-meta (fn [value]
                         (with-meta
                           value
                           {::pv/default ::dv/removable-item
                            ::dv/removable-item {:wrapped-meta (meta value)}}))
        wrapped (if (instance? clojure.lang.IAtom value)
                  (swap! value wrap-with-meta)
                  (wrap-with-meta value))]
    (core/append-tap db wrapped)))

(defn tap-removed [db [_ path]]
  (assert (= (count path) 1) "Only supporting top level list for now")
  (update db ::state/taps
          (fn [coll]
            (with-meta
              (remove-nth coll (first path))
              (meta coll)))))
