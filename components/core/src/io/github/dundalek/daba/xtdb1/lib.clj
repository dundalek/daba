(ns io.github.dundalek.daba.xtdb1.lib
  (:require
   [io.github.dundalek.daba.xtdb1.port :as xt]))

;; Workaroud: XT creates exclusive locks, we must ensure only one node
;; In the future it would be cleaner to implement some sort of connection manager
(defonce !node-cache (atom {}))

;; Indirection of the adapter, so that XTDB can be loaded optionally.
;; A potential way to improve error messages is to implement null adapter that would throw exceptions with more descriptive messages when protocol methods are invoked.
(def ^:dynamic *adapter*
  (try
    ((requiring-resolve 'io.github.dundalek.daba.xtdb1.adapter/make))
    (catch Exception _ignore
      ;; consider logging exception at debug level
      nil)))

(defn get-node [opts]
  (or (get @!node-cache opts)
      (let [node (xt/start-node *adapter* opts)]
        (swap! !node-cache assoc opts node)
        node)))

(defn get-schema [node-opts]
  (->> (xt/attribute-stats *adapter* (get-node node-opts))
       (sort-by (comp str key))
       (map (fn [[attr-name item-count]]
              {:attr-name attr-name
               :item-count item-count}))))

(defn query [node-opts query]
  (xt/q *adapter*
        (xt/db *adapter* (get-node node-opts))
        query))
