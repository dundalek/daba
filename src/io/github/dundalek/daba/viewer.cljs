(ns io.github.dundalek.daba.viewer
  (:require
   [portal.ui.api :as p]
   [portal.ui.inspector :as ins]
   [io.github.dundalek.daba.app.state :as-alias state]
   [io.github.dundalek.daba.app :as-alias app]
   [portal.viewer :as-alias pv]))

(defn action-row-component [value]
  (let [{:keys [row-meta]} (::action-row (meta value))]
    [:div {:style {:display "flex"
                   :flex-direction "row"
                   :align-items "flex-start"}}
     [ins/inspector (with-meta value row-meta)]
     [:div {:style {:display "flex"
                    :gap 6}}
      ; [:span "value:" (pr-str value)]
      ; [:button "data"]
      [:button "tables"]]]))

(defn name-label-predicate [value]
  (::name-label-fn (meta value)))

(defn name-label [value]
  (let [label-fn (::name-label-fn (meta value))]
    [:div (label-fn value)]))

(defn schema-list-component [value]
  [ins/inspector
   (->> value
        (map (fn [item]
               (with-meta item
                 {::pv/default ::action-row
                  ::action-row {:row-meta {::pv/default ::name-label
                                           ::name-label-fn :table-schem}}}))))])

(defn table-list-component [value]
  [ins/inspector (with-meta value
                   {:portal.viewer/default :portal.viewer/table
                    :portal.viewer/table {:columns [:table-name :table-type]}})])

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
