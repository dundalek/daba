(ns hooks.miniframe
  (:require [clj-kondo.hooks-api :as api]))

(defn def-event-db [{:keys [node]}]
  ;; TODO: do some checking instead of just returning two arity fn
  (let [[_ fn-name & args] (:children node)
        single-arity? (api/vector-node? (first args))
        [bindings & body] (if single-arity?
                            args
                            (:children (last args)))
        event-creator  (api/list-node
                        [(api/vector-node
                          (if (= (:value (second (:children bindings))) '_)
                            []
                            [(api/token-node '_)]))])
        new-node (api/list-node
                  [(api/token-node 'defn)
                   fn-name
                   event-creator
                   (api/list-node
                    (list* bindings body))])]
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

  ;; zero arity
  (def node
    (api/list-node
     [(api/token-node 'def-event-db)
      (api/token-node 'foo)
      (api/vector-node [(api/token-node 'db) (api/token-node '_)])
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

  (def node (api/parse-string "(defn foo [db arg] 1 2)"))

  (def node (api/parse-string (pr-str '(defn foo [db arg] 1 2))))

  (api/sexpr (:node (def-event-db {:node node}))))
