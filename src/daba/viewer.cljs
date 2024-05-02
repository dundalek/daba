(ns daba.viewer
  (:require
   [io.github.dundalek.daba.app :as-alias app]
   [io.github.dundalek.daba.app.event :as-alias event]
   [portal.ui.api :as p]
   [portal.ui.inspector :as ins]
   [portal.ui.rpc :as rpc]
   [portal.viewer :as-alias pv]
   [reagent.core :as r]))

;; Inspired by and could later drop in https://github.com/taoensso/tempura
(defn tr [[message]]
  message)

(defn dispatch [event]
  (rpc/call `app/dispatch event))

(def default-page-size 20)

(defn paginator-component []
  (let [page (r/atom 0)]
    (fn [coll]
      (let [{:keys [viewer page-size]} (::paginator (meta coll))
            page-size (or page-size default-page-size)
            paginated (->> coll
                           (drop (* @page page-size))
                           (take page-size)
                           vec)]
        [:<>
         [:div {:style {:display "flex"
                        :justify-content "center"
                        :gap 12
                        :padding 6}}
          [:button {:on-click (fn [] (swap! page #(max 0 (dec %))))} (tr ["prev"])]
          [:span (inc @page)]
          [:button {:on-click #(swap! page inc)} (tr ["next"])]]
         [ins/inspector
          (with-meta
            paginated
            viewer)]]))))

(defn schema-list-actions [{:keys [dsid schema]}]
  (let [{:keys [table-schem]} schema]
    [:div {:style {:display "flex"
                   :gap 6}}
     [:button
      {:on-click (fn [ev]
                   (.stopPropagation ev)
                   (dispatch [::event/tables-inspected dsid table-schem]))}
      (tr ["tables"])]]))

(defn schema-list-component [value]
  (let [{::keys [dsid]} (meta value)]
    [ins/inspector
     (for [item value]
       (with-meta
         [:div {:style {:display "flex"
                        :flex-direction "row"
                        :align-items "flex-start"}}
          [:div {:style {:flex-grow 1}}
           (:table-schem item)]
          [schema-list-actions {:schema item :dsid dsid}]]
         {::pv/default ::pv/hiccup}))]))

(defn table-item-component [item]
  (let [{::keys [dsid]} (meta item)
        {:keys [table-name]} item]
    [:div {:style {:display "flex"
                   :flex-direction "row"
                   :align-items "flex-start"
                   :gap 6}}
     [:div {:style {:flex-grow 1}}
      table-name]
     [:button
      {:on-click (fn [ev]
                   (.stopPropagation ev)
                   (dispatch [::event/table-data-inspected dsid table-name]))}
      (tr ["data"])]
     [:button
      {:on-click (fn [ev]
                   (.stopPropagation ev)
                   (dispatch [::event/columns-inspected dsid table-name]))}
      (tr ["columns"])]]))

(defn table-list-component [value]
  (let [{::keys [dsid]} (meta value)]
    [ins/inspector
     (for [item value]
       (with-meta
         item
         {::pv/default ::table-item
          ::dsid dsid}))]))

(defn column-list-component [value]
  (let [{::keys [dsid]} (meta value)]
    [ins/inspector
     (for [item value]
       (with-meta
         [:div {:style {:display "flex"
                        :flex-direction "row"
                        :align-items "flex-start"}}
          [:div {:style {:flex-grow 1}}
           (:column-name item)]]
         {::pv/default ::pv/hiccup}))]))

(defn query-editor-component [value]
  (let [[query results] (if (string? value)
                          [value nil]
                          [(-> value meta ::query-editor :query) value])
        {::keys [dsid]} (meta value)]
    [ins/inspector
     {::pv/default ::pv/hiccup}
     [:div
      [:form {:on-submit (fn [ev]
                           (.preventDefault ev)
                             ;; do RPC here
                           (let [query (-> ev .-target .-query .-value)]
                             (dispatch [::event/query-executed dsid query])))}
       ;; Using input instead of textarea for now because global shortcuts interfere with typing in textarea
       ;; https://github.com/djblue/portal/pull/224
       [:input {:name "query"
                :type "text"
                :default-value query}]
       [:button {:type "submit"} (tr ["execute"])]]
      (when (seq results)
        [::pv/inspector
         (with-meta results
           {::pv/default ::paginator
            ::paginator {:viewer {::pv/default ::pv/table
                                  ::pv/table (::pv/table (meta value))}}})])]]))

(defn table-item? [value]
  (and (map? value)
       (string? (:table-name value))))

(p/register-viewer!
 {:name ::paginator
  :predicate sequential?
  :component paginator-component})

(p/register-viewer!
 {:name ::table-item
  :predicate table-item?
  :component table-item-component})

(p/register-viewer!
 {:name ::table-list
  :predicate (fn [value]
               (and (sequential? value)
                    (table-item? (first value))))
  :component table-list-component})

(p/register-viewer!
 {:name ::schema-list
  :predicate (fn [value]
               (and (sequential? value)
                    (map? (first value))
                    (string? (:table-schem (first value)))))
  :component schema-list-component})

(p/register-viewer!
 {:name ::column-list
  :predicate (fn [value]
               (and (sequential? value)
                    (map? (first value))
                    (string? (:column-name (first value)))))
  :component column-list-component})

(p/register-viewer!
 {:name ::query-editor
  :predicate (fn [value]
               (or (string? value)
                   (contains? (meta value) ::query-editor)))
  :component query-editor-component})
