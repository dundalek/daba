(ns io.github.dundalek.daba.ui.viewers.root-docking
  (:require
   ["react" :as react]
   [clojure.set :as set]
   [clojure.string :as str]
   [io.github.dundalek.daba.app.event :as-alias event]
   [io.github.dundalek.daba.app.frame :as-alias frame]
   [io.github.dundalek.daba.app.state :as-alias state]
   [io.github.dundalek.daba.ui.components.loading-indicator :refer [loading-indicator]]
   [portal.ui.api :as p]
   [portal.ui.inspector :as ins]
   [portal.ui.rpc :as rpc]
   [portal.ui.theme :as theme]
   [portal.viewer :as-alias pv]
   [reagent.core :as r]))

;; We require a functional compiler to avoid invalid hook errors when passing portal components as React elements with `r/as-element`.
;; This is because portal is built using basic syntax `[component]` instead of `[:f> component]` for functional components with hooks.
(def functional-compiler (r/create-compiler {:function-components true}))

(defn as-element [el]
  (r/as-element el functional-compiler))

(def FlexLayout js/window.FlexLayout)

(defn dispatch [event-sym & args]
  (rpc/call `frame/dispatch
            (into [(keyword event-sym)] args)))

(def empty-layout
  (clj->js {:global {}
            :layout {:type "row"
                     :weight 100
                     :children [{:type "tabset"
                                 :weight 100
                                 :selected 0
                                 :children []
                                 #_[{:type "tab"
                                     :name "One"
                                     :component "panel"}]}]}}))

(defn node-seq [^js model]
  (let [!nodes (atom (transient []))]
    (.visitNodes model (fn [node] (swap! !nodes conj! node)))
    (persistent! @!nodes)))

(defn tabset-with-most-children [model]
  (->> (node-seq model)
       (filter (fn [^js node]
                 (= (.getType node) FlexLayout.TabSetNode.TYPE)))
       (reduce (fn [^js selected-node ^js node]
                 (if (or (nil? selected-node)
                         (< (count (.getChildren selected-node))
                            (count (.getChildren node))))
                   node
                   selected-node))
               nil)))

(defn cell-panel [{:keys [cells cell-id]}]
  (let [value (get @cells cell-id)
        value (try
                 ;; Pass cell-id so complex widgets can reference state when dispatching events.
                 ;; Wrapping in try-catch to prevent erroring out on primitive values which don't support metadata.
                (vary-meta value assoc :daba.viewer/cell-id cell-id)
                (catch :default _ignore
                  value))]
    [ins/with-key cell-id [ins/inspector {} value]]))

(defn light-theme? [theme]
  (str/starts-with? (:portal.colors/background theme) "#f"))

(defn use-flexlayout-theme! []
  (let [theme (theme/use-theme)
        light? (light-theme? theme)]

    (react/useEffect
     (fn []
       (set! (.-disabled (js/document.getElementById "flexlayout-light")) (not light?))
       (set! (.-disabled (js/document.getElementById "flexlayout-dark")) light?)
       js/undefined)
     #js [light?])))

(defn cell-tab-name [value]
  (let [viewer (::pv/default (meta value))
        viewer (or (when (= viewer ::pv/hiccup)
                     (some-> value second ::pv/default))
                   viewer)]
    (some-> viewer name)))

(defn root-docking-component [app-db]
  (let [{::state/keys [running-tasks cells]} app-db
        [model] (react/useState #(FlexLayout.Model.fromJson empty-layout))
        [!cells] (react/useState #(r/atom {}))
        factory (fn [^js node]
                  (let [component (.getComponent node)]
                    (when (= component "cell")
                      ;; TODO: pass subscription to ocell value for better granularity
                      (as-element [cell-panel {:cells !cells
                                               :cell-id (.getId node)}]))))
        handle-action (fn [^js action]
                        (if (= FlexLayout.Actions.DELETE_TAB (.-type action))
                          (let [cell-id (-> action .-data .-node)]
                            (dispatch `event/tap-removed cell-id)
                            js/undefined)
                          action))]

    (react/useEffect
     (fn []
       (let [keys-before (set (map #(.getId ^js %) (node-seq model)))
             keys-after (set (keys cells))]

         (when-some [removed-cell-ids (seq (set/difference keys-before keys-after))]
           (doseq [cell-id removed-cell-ids]
             (.doAction ^js model (FlexLayout.Actions.deleteTab cell-id))))

         (when-some [added-cell-ids (seq (set/difference keys-after keys-before))]
           (doseq [cell-id added-cell-ids]
             (let [^js tabset-node
                   (tabset-with-most-children model)
                   ; (.getActiveTabset ^js model)
                   ; (.getFirstTabSet ^js model)

                   ;; TODO: make name reactive
                   tab-name (str (cell-tab-name (get cells cell-id)) " [" cell-id "]")
                   node #js {:id cell-id
                             :name tab-name
                             :type "tab"
                             :component "cell"}
                   add-node-action (FlexLayout.Actions.addNode node (.getId tabset-node) (.-CENTER FlexLayout.DockLocation) -1)]
               (.doAction ^js model add-node-action)))))

       (reset! !cells cells)
       js/undefined)
     #js [(hash cells)])

    (use-flexlayout-theme!)

    [:div
     (when (pos? running-tasks)
       [loading-indicator])
     [:div {:style {:position "relative"
                    :width "100%"
                    ;; expand view to full height, subtract offset for Portal header and footer UI
                    :height "calc(100vh - 160px)"}}
      [:> FlexLayout.Layout
       {:model model
        :factory factory
        :onAction handle-action}]]]))

(p/register-viewer!
 {:name :daba.viewer/root-docking
  :predicate (fn [value]
               (and (map? value)
                    (contains? value ::state/cells)))
  :component root-docking-component})
