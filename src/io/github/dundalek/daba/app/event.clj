(ns io.github.dundalek.daba.app.event
  (:require
   [io.github.dundalek.daba.app.core :as core]
   [io.github.dundalek.daba.app.state :as state]
   [io.github.dundalek.daba.app.fx :as-alias fx]))

;; Helpers

(defn remove-nth [coll n]
  (concat
   (take n coll)
   (drop (inc n) coll)))

(comment
  [(remove-nth [1 2 3] 0)
   (remove-nth [1 2 3] 1)
   (remove-nth [1 2 3] 2)])

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
                                  :table-name table-name}]]})

(defn query-editor-opened [{:keys [db]} [_ dsid]]
  {:fx [[::fx/open-query-editor {:source (core/get-source db dsid)
                                 :query ""}]]})

(defn new-query-executed [{:keys [db]} [_ {:keys [dsid query]}]]
  {:fx [[::fx/open-query-editor {:source (core/get-source db dsid)
                                 :query query}]]})

(defn query-executed [{:keys [db]} [_ {:keys [dsid query path]}]]
  (let [source (or (core/get-source db dsid)
                   ;; It might be better to use last used source or offer choice
                   (first (vals (::state/sources db))))
        !query-atom (nth (::state/taps db) (first path))]
    {:fx [[::fx/execute-query {:source source
                               :query query
                               :!query-atom !query-atom}]]}))

(defn tap-submitted [db [_ value]]
  (update db ::state/taps conj value))

(defn tap-removed [db [_ path]]
  (assert (= (count path) 1) "Only supporting top level list for now")
  (update db ::state/taps
          (fn [coll]
            (with-meta
              (remove-nth coll (first path))
              (meta coll)))))
