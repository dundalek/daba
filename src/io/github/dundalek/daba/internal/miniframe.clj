(ns io.github.dundalek.daba.internal.miniframe)

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
