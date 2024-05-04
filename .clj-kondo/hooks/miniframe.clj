(ns hooks.miniframe
  (:require [clj-kondo.hooks-api :as api]))

(defn def-event-db [{:keys [node]}]
  ;; TODO: do some checking instead of just returning two arity fn
  (let [[_ fn-name & args] (:children node)
        single-arity? (api/vector-node? (first args))
        handler-body (if single-arity?
                       (api/list-node args)
                       (last args))
        new-node (api/list-node
                  [(api/token-node 'defn)
                   fn-name
                   (api/list-node
                    [(api/vector-node [(api/token-node '_)])])
                   handler-body])]
    {:node new-node}))

(def def-event-fx def-event-db)

(comment
  ;; single arity
  (def node
    (api/list-node
     [(api/token-node 'def-event-db)
      (api/token-node 'foo)
      (api/vector-node [(api/token-node 'db) (api/token-node 'arg)])
      (api/token-node 1)
      (api/token-node 2)]))

  ;; multi arity
  (def node
    (api/list-node
     [(api/token-node 'def-event-db)
      (api/token-node 'foo)
      (api/list-node
       [(api/vector-node [(api/token-node '_)])])
      (api/list-node
       [(api/vector-node [(api/token-node 'db) (api/token-node 'arg)])
        (api/token-node 1)
        (api/token-node 2)])]))

  (api/sexpr (:node (def-event-db {:node node}))))


