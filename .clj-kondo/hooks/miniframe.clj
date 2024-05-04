(ns hooks.miniframe
  (:require [clj-kondo.hooks-api :as api]))

(defn def-event-db [{:keys [node]}]
  ;; TODO: do some checking instead of just returning two arity fn
  (let [new-node (api/list-node
                  [(api/token-node 'defn)
                   (second (:children node))
                   (api/list-node
                    [(api/vector-node [(api/token-node '_)])])
                   (api/list-node
                    [(api/vector-node
                      [(api/token-node '_db) (api/token-node '_event)])])])]
    {:node new-node}))

(def def-event-fx def-event-db)

(comment
  (def node
    (api/list-node
     [(api/token-node 'def-event-db)
      (api/token-node 'foo)]))

  (api/sexpr (:node (def-event-db {:node node}))))

