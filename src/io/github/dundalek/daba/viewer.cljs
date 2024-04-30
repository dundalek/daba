(ns io.github.dundalek.daba.viewer
  (:require
   [portal.ui.api :as p]
   [portal.ui.inspector :as ins]
   [io.github.dundalek.daba.app.state :as-alias state]
   [io.github.dundalek.daba.app.event :as-alias event]
   [io.github.dundalek.daba.app :as-alias app]
   [portal.viewer :as-alias pv]
   [portal.ui.rpc :as rpc]))

(defn dispatch [event]
  (rpc/call `app/dispatch event))

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
  (let [{::state/keys [dsid]} (meta value)]
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
     [:button "data"]
     [:button
      {:on-click (fn [ev]
                   (.stopPropagation ev)
                   (dispatch [::event/columns-inspected dsid table-name]))}
      "columns"]]))

(defn table-list-component [value]
  (let [{::state/keys [dsid]} (meta value)]
    [ins/inspector
     (->> value
          (map (fn [item]
                 (with-meta item
                   {::pv/default ::action-row
                    ::action-row {:value-meta {::pv/default ::name-label
                                               ::name-label-fn :table-name}
                                  :action-bar [table-list-actions {:table item :dsid dsid}]}}))))]))

(defn column-list-component [value]
  (let [{::state/keys [dsid]} (meta value)]
    [ins/inspector
     (->> value
          (map (fn [item]
                 (with-meta item
                   {::pv/default ::action-row
                    ::action-row {:value-meta {::pv/default ::name-label
                                               ::name-label-fn :column-name}}}))))]))
                                  ; :action-bar [table-list-actions {:table item :dsid dsid}]}}))))]))

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
