(ns io.github.dundalek.daba.internal.miniframe)

(defonce !event-registry (atom {}))
(defonce !fx-registry (atom {}))

(defn- run-effects! [ctx frame]
  (let [{:keys [app-db] effects :fx} frame
        {:keys [db fx]} ctx]
    (when (and (some? db) (not (identical? @app-db db)))
      (reset! app-db db))
    (doseq [[fx-name arg] fx]
      (let [fx-handler (get effects fx-name)]
        (fx-handler arg)))))

(defn db-handler [handler]
  (fn [ctx event]
    (update ctx :db handler event)))

(defn fx-handler [handler]
  handler)

(defn dispatch [frame event]
  (let [{:keys [app-db] events :event} frame
        ctx {:db @app-db}
        handler (get events (first event))]
    (-> ctx
        (handler event)
        (run-effects! frame))))

(defn make-frame [{:keys [events fx app-db] :as frame-map}]
  frame-map)

(defmacro def-handler
  {:clj-kondo/lint-as 'clojure.core/defn}
  [handler fn-name & args]
  (let [single-arity? (vector? (first args))
        handler-kw (keyword (str *ns*) (str fn-name))
        handler-body (if single-arity?
                       args
                       (last args))]
    ;; TODO: maybe call the action creator fn if it has body - could be used for validation
    `(do
       (defn ~fn-name
         ([value#] [~handler-kw value#])
         ~handler-body)
       (swap! ~`!event-registry assoc ~handler-kw (~handler ~fn-name)))))

(defmacro def-event-db
  {:clj-kondo/lint-as 'clojure.core/defn}
  [& args]
  `(def-handler db-handler ~@args))

(defmacro def-event-fx
  {:clj-kondo/lint-as 'clojure.core/defn}
  [& args]
  `(def-handler fx-handler ~@args))

(defmacro def-fx
  {:clj-kondo/lint-as 'clojure.core/defn}
  [fn-name bindings & body]
  (assert (vector? bindings))
  (assert (= (count bindings) 1))
  (let [handler-kw (keyword (str *ns*) (str fn-name))]
    `(do
       (defn ~fn-name
         ([value#] [~handler-kw value#])
         ([ignored# ~(first bindings)] ~@body))
       (swap! ~`!fx-registry assoc ~handler-kw
              (fn [arg#] (~fn-name nil arg#))))))
