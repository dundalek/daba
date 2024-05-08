(ns daba.viewer
  (:require
   [io.github.dundalek.daba.app :as-alias app]
   [io.github.dundalek.daba.app.event :as-alias event]
   [io.github.dundalek.daba.app.frame :as-alias frame]
   [portal.colors :as c]
   [portal.ui.api :as p]
   [portal.ui.inspector :as ins]
   [portal.ui.rpc :as rpc]
   [portal.ui.styled :as s]
   [portal.ui.theme :as theme]
   [portal.viewer :as-alias pv]))

;; Inspired by and could later drop in https://github.com/taoensso/tempura
(defn tr [[message]]
  message)

(defn dispatch [event-sym & args]
  (rpc/call `frame/dispatch
            (into [(keyword event-sym)] args)))

(defn button-style [theme]
  ;; Portal does not seem to provide components ala design system
  ;; Following is based on the "Open command palette." button
  {:font-family (:font-family theme)
   :background (::c/background theme)
   :border-radius (:border-radius theme)
   :border [1 :solid (::c/border theme)]
   :box-sizing :border-box
   :padding-top (:padding theme)
   :padding-bottom (:padding theme)
   :padding-left (* 3 (:padding theme))
   :padding-right (* 3 (:padding theme))
   :color (::c/tag theme)
   :font-size (:font-size theme)
   :font-weight :bold
   :cursor :pointer})

(defn input-style [theme]
  ;; Based on the filter input "Select a value to enable filtering"
  (let [color ::c/text #_::c/border]
    {:flex "1"
     :background (::c/background theme)
     :padding (:padding theme)
     :box-sizing :border-box
     :font-family (:font-family theme)
     :font-size (:font-size theme)
     :color (get theme color)
     :border [1 :solid (::c/border theme)]
     :border-radius (:border-radius theme)}))

(defn merge-style [props style]
  (update props :style #(merge style %)))

(defn textarea [props]
   ;; Using input instead of textarea for now because global shortcuts interfere with typing in textarea
   ;; https://github.com/djblue/portal/pull/224
  (let [theme (theme/use-theme)
        stop-propagation (fn [ev]
                           ;; stop propagation so that portal selection does not steal input focus
                           (.stopPropagation ev))]
    [s/input (->
              {:type "text"
               :on-click stop-propagation
               :on-double-click stop-propagation}
              (merge props)
              (merge-style (input-style theme)))]))

(defn button [props & children]
  (let [theme (theme/use-theme)]
    (into [s/button
           (-> props (merge-style (button-style theme)))]
          children)))

(defn paginator [{:keys [offset limit on-offset-change]}]
  (let [theme (theme/use-theme)
        page (/ offset limit)]
    [:div {:style {:display "flex"
                   :justify-content "center"
                   :gap 12
                   :padding 6}}
     [button {:on-click (fn [ev]
                          (.stopPropagation ev)
                          (on-offset-change (max 0 (- offset limit))))}
      (tr ["prev"])]
     [s/span {:style {:font-family (:font-family theme)
                      :font-size (:font-size theme)
                      :padding (:padding theme)}}
      (inc page)]
     [button {:on-click (fn [ev]
                          (.stopPropagation ev)
                          (on-offset-change (+ offset limit)))}
      (tr ["next"])]]))

(defn datagrid-component [coll]
  (let [{::keys [datagrid dsid]} (meta coll)
        {:keys [query-map viewer]} datagrid
        {:keys [limit offset]} query-map
        {:keys [path]} (ins/use-context)
        paginate (fn [new-offset]
                   (dispatch `event/datagrid-query-changed
                             {:dsid dsid
                              :path path
                              :query-map (assoc query-map :offset new-offset)}))]
    (if (and (map coll) (::error coll))
      [ins/inspector (::error coll)]
      [:div
       [ins/inspector
        (with-meta
          (into [] coll)
          viewer)]
       [paginator {:offset offset
                   :limit limit
                   :on-offset-change paginate}]])))

(defn schema-list-actions [{:keys [dsid schema]}]
  (let [{:keys [table-schem]} schema]
    [:div {:style {:display "flex"
                   :gap 6}}
     [button
      {:on-click (fn [ev]
                   (.stopPropagation ev)
                   (dispatch `event/tables-inspected {:dsid dsid :schema table-schem}))}
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
        {:keys [table-name table-type]} item]
    [:div {:style {:display "flex"
                   :flex-direction "row"
                   :align-items "flex-start"
                   :gap 6}}
     [:div {:style {:flex-grow 1}}
      (if (#{"TABLE" "BASE TABLE"} table-type)
        table-name
        (str table-name " (" table-type ")"))]
     [button
      {:on-click (fn [ev]
                   (.stopPropagation ev)
                   (dispatch `event/columns-inspected {:dsid dsid :table table-name}))}
      (tr ["columns"])]
     [button
      {:on-click (fn [ev]
                   (.stopPropagation ev)
                   (dispatch `event/table-data-inspected {:dsid dsid :table table-name}))}
      (tr ["data"])]]))

(defn table-list-component [value]
  (let [{::keys [dsid]} (meta value)]
    [ins/inspector
     (for [item value]
       (with-meta
         item
         {::pv/default ::table-item
          ::dsid dsid}))]))

(defn column-list-component [value]
  (let [{::keys [dsid]} (meta value)
        item-meta {::pv/for {:column-name ::pv/hiccup
                             :type-name ::pv/hiccup
                             :nullable ::pv/hiccup}}]
    [ins/inspector
     (with-meta
       (for [column value]
         (let [{:keys [column-name type-name is-nullable nullable]} column]
           (with-meta
             {:column-name [:span column-name]
              :type-name [:span type-name]
              :nullable [:span (if (zero? nullable)
                                 ""
                                 is-nullable)]
              :info column}
             item-meta)))
       {::pv/default ::pv/table
        ::pv/table {:columns [:column-name :type-name :nullable :info]}})]))

(defn query-editor-component [value]
  (let [[query results] (if (string? value)
                          [{:statement value} nil]
                          [(-> value meta ::query-editor :query) value])
        {:keys [statement offset limit]} query
        {:keys [path]} (ins/use-context)
        {::keys [dsid]} (meta value)
        execute-query (fn [q]
                        (dispatch `event/query-executed
                                  {:path path
                                   :dsid dsid
                                   :query q}))]
    [ins/inspector
     {::pv/default ::pv/hiccup}
     [:div
      [:form {:on-submit (fn [ev]
                           (.preventDefault ev)
                           (let [statement (-> ev .-target .-query .-value)]
                             (if (= (-> ev .-nativeEvent .-submitter .-name) "execute")
                               (execute-query {:statement statement})
                               (dispatch `event/new-query-executed {:dsid dsid
                                                                    :query statement}))))
              :style {:display "flex"
                      :gap 6}}
       [textarea {:name "query"
                  :default-value statement
                  :style {:flex-grow 1}}]
       [button {:type "submit"
                :name "execute"}
        (tr ["execute"])]
       [button {:type "submit"
                :name "execute-new"}
        (tr ["execute as new"])]]
      (if (and (map? results) (::error results))
        [::pv/inspector (::error results)]
        [:div
         (when (seq results)
           [::pv/inspector
            (with-meta results
              {::pv/default ::pv/table
               ::pv/table (::pv/table (meta value))})])
         (when (or (seq results) (pos? offset))
           [paginator {:offset offset
                       :limit limit
                       :on-offset-change #(execute-query (assoc query :offset %))}])])]]))

(defn removable-item-component [value]
  (let [{:keys [path]} (ins/use-context)]
    [:div {:style {:display "flex"
                   :flex-direction "row"
                   :align-items "flex-start"
                   :gap 6
                   ;; Add extra padding to avoid overlapping with portal's atom indicator
                   :padding-right 36}}
     [:div {:style {:flex-grow 1}}
      [ins/inspector
       (with-meta
         value
         (-> value meta ::removable-item :wrapped-meta))]]
     [:div
      ;; margin to offset inspector border to make the remove button look aligned
      {:style {:margin 1}}
      [button
       {:on-click (fn [ev]
                    (.stopPropagation ev)
                     ;; last segment seems to be extra 0, dropping it
                    (dispatch `event/tap-removed path))}
       "x"]]]))

(defn table-item? [value]
  (and (map? value)
       (string? (:table-name value))))

(defn datasource? [value]
  (or (string? value)
      (map? value)))

(defn datasource-component [value]
  ;; Values can be wrapped in a map with ::db-spec key as a workaround for not being able to attach metadata to strings
  (let [db-spec (or (when (map? value) (::db-spec value))
                    value)
        default-value (or (cond
                            (string? db-spec) db-spec
                            (map? db-spec) (pr-str db-spec))
                          "")
        {:keys [path]} (ins/use-context)]
    [:form {:on-submit (fn [ev]
                         (.preventDefault ev)
                         (let [datasource (-> ev .-target .-datasource .-value)
                               action (-> ev .-nativeEvent .-submitter .-name)
                               payload {:path path
                                        :value datasource
                                        :action action}]
                           (if (= datasource default-value)
                             (dispatch `event/datasource-input-submitted payload)
                             (dispatch `event/datasource-input-changed payload))))
            :style {:display "flex"
                    :gap 6}}
     [textarea {:name "datasource"
                :placeholder "connection string like postgres://user@host:port/dbname"
                :style {:flex-grow 1}
                :default-value default-value}]
     [button {:type "submit"
              :name "schema"}
      (tr ["schema"])]
     [button {:type "submit"
              :name "query"}
      (tr ["query"])]]))

(defn datasource-list-component [value]
  [ins/inspector
   (for [item value]
     (if (map? value)
       (with-meta
         item
         {::pv/default ::datasource})
       (with-meta
         [::datasource item]
         {::pv/default ::pv/hiccup})))])

(p/register-viewer!
 {:name ::datasource
  :predicate datasource?
  :component datasource-component})

(p/register-viewer!
 {:name ::datasource-list
  :predicate (fn [value]
               (and (sequential? value)
                    (datasource? (first value))))
  :component datasource-list-component})

(p/register-viewer!
 {:name ::removable-item
  :predicate (constantly true)
  :component removable-item-component})

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

(p/register-viewer!
 {:name ::datagrid
  :predicate (fn [value]
               (map? (::datagrid (meta value))))
  :component datagrid-component})
