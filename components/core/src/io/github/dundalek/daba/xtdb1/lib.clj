(ns io.github.dundalek.daba.xtdb1.lib
  (:require
   [xtdb.api :as xt]))

;; Workaroud: XT creates exclusive locks, we must ensure only one node
;; In the future it would be cleaner to implement some sort of connection manager
(defonce !node-cache (atom {}))

(defn get-node [opts]
  (or (get @!node-cache opts)
      (let [node (xt/start-node opts)]
        (swap! !node-cache assoc opts node)
        node)))

(defn get-schema [node-opts]
  (->> (xt/attribute-stats (get-node node-opts))
       (sort-by (comp str key))
       (map (fn [[attr-name item-count]]
              {:attr-name attr-name
               :item-count item-count}))))

(defn query [node-opts query]
  (xt/q (xt/db (get-node node-opts))
        query))
