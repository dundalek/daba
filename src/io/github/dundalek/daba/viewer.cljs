(ns io.github.dundalek.daba.viewer
  (:require
   [io.github.dundalek.daba.app :as-alias app]
   [io.github.dundalek.daba.app.event :as-alias event]
   [portal.ui.api :as p]
   [portal.ui.inspector :as ins]
   [portal.ui.rpc :as rpc]
   [portal.viewer :as-alias pv]
   [reagent.core :as r]))

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
                           (take page-size))]
        [:<>
         [:div {:style {:display "flex"
                        :justify-content "center"
                        :gap 12
                        :padding 6}}
          [:button {:on-click (fn [] (swap! page #(max 0 (dec %))))} "prev"]
          [:span (inc @page)]
          [:button {:on-click #(swap! page inc)} "next"]]
         [ins/inspector
          viewer
          paginated]]))))

(defn action-row-component [value]
  (let [{::keys [action-row]} (meta value)
        {:keys [value-meta action-bar]} action-row]
    [:div {:style {:display "flex"
                   :flex-direction "row"
                   :align-items "flex-start"}}
     [ins/inspector (with-meta value value-meta)]
     action-bar]))

(defn name-label-predicate [value]
  (::name-label-fn (meta value)))

(defn name-label [value]
  (let [label-fn (::name-label-fn (meta value))]
    [:div (label-fn value)]))

(defn schema-list-actions [{:keys [dsid schema]}]
  (let [{:keys [table-schem]} schema]
    [:div {:style {:display "flex"
                   :gap 6}}
     [:button
      {:on-click (fn [ev]
                   (.stopPropagation ev)
                   (dispatch [::event/tables-inspected dsid table-schem]))}
      "tables"]]))

(defn schema-list-component [value]
  (let [{::keys [dsid]} (meta value)]
    [ins/inspector
     (->> value
          (map (fn [item]
                 (with-meta item
                   {::pv/default ::action-row
                    ::action-row {:value-meta {::pv/default ::name-label
                                               ::name-label-fn :table-schem}
                                  :action-bar [schema-list-actions {:schema item :dsid dsid}]}}))))]))

(defn table-list-actions [{:keys [dsid table]}]
  (let [{:keys [table-name]} table]
    [:div {:style {:display "flex"
                   :gap 6}}
     [:button
      {:on-click (fn [ev]
                   (.stopPropagation ev)
                   (dispatch [::event/table-data-inspected dsid table-name]))}
      "data"]
     [:button
      {:on-click (fn [ev]
                   (.stopPropagation ev)
                   (dispatch [::event/columns-inspected dsid table-name]))}
      "columns"]]))

(defn table-list-component [value]
  (let [{::keys [dsid]} (meta value)]
    [ins/inspector
     (->> value
          (map (fn [item]
                 (with-meta item
                   {::pv/default ::action-row
                    ::action-row {:value-meta {::pv/default ::name-label
                                               ::name-label-fn :table-name}
                                  :action-bar [table-list-actions {:table item :dsid dsid}]}}))))]))

(defn column-list-component [value]
  (let [{::keys [dsid]} (meta value)]
    [ins/inspector
     (->> value
          (map (fn [item]
                 (with-meta item
                   {::pv/default ::action-row
                    ::action-row {:value-meta {::pv/default ::name-label
                                               ::name-label-fn :column-name}}}))))]))
                                  ; :action-bar [table-list-actions {:table item :dsid dsid}]}}))))]))

(p/register-viewer!
 {:name ::paginator
  :predicate sequential?
  :component paginator-component})

(p/register-viewer!
 {:name ::action-row
  :predicate (fn [value] (contains? (meta value) ::action-row))
  :component action-row-component})

(p/register-viewer!
 {:name ::name-label
  :predicate name-label-predicate
  :component name-label})

(p/register-viewer!
 {:name ::table-list
  :predicate sequential?
  :component table-list-component})

(p/register-viewer!
 {:name ::schema-list
  :predicate sequential?
  :component schema-list-component})

(p/register-viewer!
 {:name ::column-list
  :predicate sequential?
  :component column-list-component})
