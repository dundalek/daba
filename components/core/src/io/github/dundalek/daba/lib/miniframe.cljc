(ns io.github.dundalek.daba.lib.miniframe
  #?(:clj (:import (java.util.concurrent.locks ReentrantLock)))
  #?(:cljs (:require-macros [io.github.dundalek.daba.lib.miniframe])))

(defonce !event-registry (atom {}))
(defonce !fx-registry (atom {}))

(def ^:dynamic *queued-fx* ::unbound)

(defn- run-effects! [ctx frame]
  (let [{:keys [app-db] effects :fx} frame
        {:keys [db fx]} ctx]
    (when (and (some? db) (not (identical? @app-db db)))
      (reset! app-db db))
    (doseq [[fx-name arg] fx]
      (let [fx-handler (get effects fx-name)]
        (fx-handler arg)))))

(defn- drain-event-queue! [frame]
  (let [{:keys [app-db] ::keys [!event-queue] event-handlers :event} frame]
    (loop []
      (when-some [event (peek (first (swap-vals! !event-queue pop)))]
        (let [[event-name arg] event
              handler (get event-handlers event-name)
              ctx {:db @app-db}]
          (binding [*queued-fx* nil]
            (let [ctx (handler ctx arg)
                  f *queued-fx*]
              (run-effects! ctx frame)
              (when (some? f)
                (f)))))
        (recur)))))

(defn db-handler [handler]
  (fn [ctx arg]
    (let [db (handler (:db ctx) arg)]
      (cond-> ctx
        (some? db) (assoc :db db)))))

(defn fx-handler [handler]
  handler)

(defn dispatch [frame event]
  (let [{::keys [processing-lock !event-queue]} frame]
    (swap! !event-queue conj event)
    (prn event)

    #?(:clj (when (.tryLock processing-lock)
              (try
                (when (= (.getHoldCount processing-lock) 1)
                  (drain-event-queue! frame))
                (finally
                  (.unlock processing-lock))))
       :cljs (drain-event-queue! frame))))

(defn make-frame [{:keys [event fx app-db] :as frame-map}]
  (merge {:event @!event-registry
          :fx @!fx-registry}
         frame-map
         {;; Warning: unbounded and no backpressure
          ::!event-queue (atom #?(:clj (clojure.lang.PersistentQueue/EMPTY)
                                  :cljs #queue []))
          ::processing-lock #?(:clj (ReentrantLock.)
                               :cljs nil)}))

(defmacro def-handler
  [handler fn-name & args]
  (let [single-arity? (vector? (first args))
        handler-kw (keyword (str *ns*) (str fn-name))
        [bindings & body] (if single-arity?
                            args
                            (last args))
        ;; TODO: maybe call the action creator fn if it has body - could be used for validation
        event-creator (if (= (second bindings) '_)
                        `([] [~handler-kw nil])
                        `([value#] [~handler-kw value#]))]
    `(do
       (defn ~fn-name
         ~event-creator
         (~bindings ~@body))
       (swap! ~`!event-registry assoc ~handler-kw (~handler ~fn-name)))))

(defmacro def-event-db
  [& args]
  `(def-handler db-handler ~@args))

(defmacro def-event-fx
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

(defn queue-fx! [f]
  (when (some? *queued-fx*)
    (if (*queued-fx* ::unbound)
      (throw "Trying to queue effect outside of handler scope")
      (throw "Effect already bound, can only queue one fx inside a handler")))
  (set! *queued-fx* f)
  nil)

(defmacro fx! [& body]
  `(queue-fx! (fn [] ~@body)))
