(ns io.github.dundalek.daba.xtdb1.lib
  (:require
   [io.github.dundalek.daba.xtdb1.port :as port]
   [io.github.dundalek.daba.xtdb1.adapter :as adapter]))

;; Workaroud: XT creates exclusive locks, we must ensure only one node
;; In the future it would be cleaner to implement some sort of connection manager
(defonce !node-cache (atom {}))

(def adapter (adapter/->XDTBAdapter))

(defn get-node [opts]
  (or (get @!node-cache opts)
      (let [node (port/start-node adapter opts)]
        (swap! !node-cache assoc opts node)
        node)))

(defn get-schema [node-opts]
  (->> (port/attribute-stats adapter (get-node node-opts))
       (sort-by (comp str key))
       (map (fn [[attr-name item-count]]
              {:attr-name attr-name
               :item-count item-count}))))

(defn query [node-opts query]
  (port/q adapter
          (port/db adapter (get-node node-opts))
          query))
