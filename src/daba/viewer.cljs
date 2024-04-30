(ns daba.viewer
  (:require
   [portal.ui.inspector :as ins]
   [reagent.core :as r]
   [portal.ui.api :as p]))

(def default-page-size 20)

(defn paginable? [value]
  (sequential? value))

(defn paginator []
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

(p/register-viewer!
 {:name ::paginator
  :predicate paginable?
  :component paginator})
