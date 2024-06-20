(ns daba.viewer
  (:require
   [io.github.dundalek.daba.app.event :as-alias event]
   [io.github.dundalek.daba.app.frame :as-alias frame]
   [io.github.dundalek.daba.app.state :as-alias state]
   [portal.colors :as c]
   [portal.ui.api :as p]
   [portal.ui.inspector :as ins]
   [portal.ui.rpc :as rpc]
   [portal.ui.select :as select]
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

(defn set-style-content! [id content]
  (if-let [style-elem (js/document.getElementById id)]
    (set! (.-textContent style-elem) content)
    (let [style-elem (js/document.createElement "style")]
      (set! (.-id style-elem) id)
      (set! (.-textContent style-elem) content)
      (-> (.-head js/document)
          (.appendChild style-elem)))))

(set-style-content!
 "io-github-dundalek-daba-styles"
 "@keyframes io-github-dundalek-daba-loading-animation {
    0% {
      left: -60%;
    }
    100% {
      left: 100%;
    }
}")

(defn loading-indicator []
  (let [theme (theme/use-theme)]
    [s/div {:style {:position "relative"
                    :overflow "hidden"
                    :height 3
                    :margin-top -9
                    :margin-bottom 6
                    :background (::c/background2 theme)}}
     [s/div {:style {:position "absolute"
                     :height "100%"
                     :width "40%"
                     :animation "io-github-dundalek-daba-loading-animation 1s linear infinite"
                     :background (::c/tag theme)}}]]))

(def s-textarea (partial s/styled :textarea))

(defn textarea [props]
  (let [theme (theme/use-theme)
        stop-propagation (fn [ev]
                           ;; stop propagation so that portal selection does not steal input focus
                           (.stopPropagation ev))]
    [s-textarea
     (-> {:type "text"
          :auto-focus true
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

(defn wrapped-error? [value]
  (and (map? value) (::error value)))

(defn error-viewer [value]
  [ins/inspector {} (::error value)])

(defn paginated-table-results [{:keys [on-offset-change offset limit value]}]
  (if (wrapped-error? value)
    [error-viewer value]
    [:div
     (when (seq value)
       [ins/inspector
        {}
        (with-meta value
          {::pv/default ::pv/table
           ::pv/table (::pv/table (meta value))})])
     (when (or (seq value) (pos? offset))
       [paginator {:offset offset
                   :limit limit
                   :on-offset-change on-offset-change}])]))

(defn datagrid-component [value]
  (let [{::keys [datagrid cell-id dsid]} (meta value)
        {:keys [query]} datagrid
        {:keys [limit offset]} query
        on-offset-change (fn [new-offset]
                           (dispatch `event/table-data-query-changed
                                     {:cell-id cell-id
                                      :dsid dsid
                                      :query (assoc query :offset new-offset)}))]
    [:div
     [paginated-table-results {:offset offset
                               :limit limit
                               :on-offset-change on-offset-change
                               :value value}]]))

(defn query-editor-component [value]
  (let [{::keys [query-editor cell-id dsid]} (meta value)
        {:keys [query]} query-editor
        {:keys [statement offset limit]} query
        execute-query (fn [query]
                        (dispatch `event/query-editor-executed
                                  {:cell-id cell-id
                                   :dsid dsid
                                   :query query}))
        on-offset-change #(execute-query (assoc query :offset %))]
    ;; extra inspector wrapping otherwise seems to cause UI freezes
    [ins/inspector
     {::pv/default ::pv/hiccup}
     [:div
      [:form {:on-submit (fn [ev]
                           (.preventDefault ev)
                           (.stopPropagation ev)
                           (let [statement (-> ev .-target .-query .-value)]
                             (execute-query {:statement statement})))
              :style {:display "flex"
                      :gap 6}}
       [textarea {:name "query"
                  :default-value statement
                  :style {:flex-grow 1}}]
       [button {:type "submit"
                :name "execute"}
        (tr ["execute"])]]
      [paginated-table-results {:offset offset
                                :limit limit
                                :on-offset-change on-offset-change
                                :value value}]]]))

(defn datomic-query-editor-component [value]
  (let [{::keys [datomic-query-editor cell-id dsid]} (meta value)
        {query-map :query} datomic-query-editor
        {:keys [query limit offset]} query-map
        on-offset-change (fn [new-offset]
                           (dispatch `event/datomic-query-editor-changed
                                     {:cell-id cell-id
                                      :dsid dsid
                                      :query (assoc query-map :offset new-offset)}))]
    [ins/inspector
     {::pv/default ::pv/hiccup}
     [:div
      [:form {:on-submit (fn [ev]
                           (.preventDefault ev)
                           (.stopPropagation ev)
                           (let [query (-> ev .-target .-query .-value)]
                             (dispatch `event/datomic-query-editor-executed
                                       {:cell-id cell-id
                                        :dsid dsid
                                        :query query})))
              :style {:display "flex"
                      :gap 6}}
       [textarea {:name "query"
                  :default-value (pr-str query)
                  :style {:flex-grow 1}}]
       [button {:type "submit"
                :name "execute"}
        (tr ["execute"])]]
      [paginated-table-results {:offset offset
                                :limit limit
                                :on-offset-change on-offset-change
                                :value value}]]]))

(defn query-component [statement]
  [:form {:on-submit (fn [ev]
                       (.preventDefault ev)
                       (.stopPropagation ev)
                       (case (-> ev .-nativeEvent .-submitter .-name)
                         "execute" (dispatch `event/query-executed statement)
                         "edit" (dispatch `event/query-edited statement)))
          :style {:display "flex"
                  :gap 6}}
   [:div {:style {:flex-grow 1}}
    statement]
   [button {:type "submit"
            :name "execute"}
    (tr ["execute"])]
   [button {:type "submit"
            :name "edit"}
    (tr ["edit"])]])

(defn schema-list-actions [{:keys [dsid schema]}]
  (let [{:keys [table-schem]} schema]
    [:div {:style {:display "flex"
                   :gap 6}}
     [button
      {:on-click (fn [ev]
                   (.stopPropagation ev)
                   (dispatch `event/schema-tables-inspected {:dsid dsid :schema table-schem}))}
      (tr ["tables"])]]))

(defn schema-list-component [value]
  (let [{::keys [dsid]} (meta value)]
    [ins/toggle-bg
     [ins/inspector
      {}
      (for [item value]
        (with-meta
          [:div {:style {:display "flex"
                         :flex-direction "row"
                         :align-items "center"}}
           [:div {:style {:flex-grow 1}}
            (:table-schem item)]
           [schema-list-actions {:schema item :dsid dsid}]]
          {::pv/default ::pv/hiccup}))]]))

(defn table-item-component [item]
  (let [{::keys [dsid]} (meta item)
        {:keys [table-name table-type]} item]
    [:div {:style {:display "flex"
                   :flex-direction "row"
                   :align-items "center"
                   :gap 6}}
     [:div {:style {:flex-grow 1}}
      (if (#{"TABLE" "BASE TABLE"} table-type)
        table-name
        (str table-name " (" table-type ")"))]
     [button
      {:on-click (fn [ev]
                   (.stopPropagation ev)
                   (dispatch `event/table-columns-inspected {:dsid dsid :table table-name}))}
      (tr ["columns"])]
     [button
      {:on-click (fn [ev]
                   (.stopPropagation ev)
                   (dispatch `event/table-data-inspected {:dsid dsid :table table-name}))}
      (tr ["data"])]]))

(defn table-list-component [value]
  (let [{::keys [dsid]} (meta value)]
    ;; Adding toggle-bg to follow convention that expanded nested collections
    ;; have inverted backgrounds.
    ;; This results in white background with default light theme (nord-light)
    ;; that is consistent with rest of the values.
    [ins/toggle-bg
     [ins/inspector
      {}
      (for [item value]
        (with-meta
          item
          {::pv/default ::table-item
           ::dsid dsid}))]]))

(defn column-list-component [value]
  (let [{::keys [dsid]} (meta value)
        item-meta {::pv/for {:column-name ::pv/hiccup
                             :type-name ::pv/hiccup
                             :nullable ::pv/hiccup}}]
    [ins/inspector
     {}
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

(def ^:private datomic-schema-table-opts
  {:columns [;; Showing the action column first so it does not get hidden due to horizontal scrolling
             :action

             :db/ident
             :db/valueType
             :db/cardinality

             :db/unique
             :db/isComponent
             :db/fulltext

             :db/doc
             #_:db/id]})

(defn datomic-attribute-list [value]
  (let [item-meta {::pv/for {:action ::pv/hiccup}}]
    [ins/inspector
     {}
     (with-meta
       (for [item value]
         (with-meta
           (assoc item :action
                  [:div
                   [button
                    {:on-click (fn [ev]
                                 (let [{::keys [dsid]} (meta value)]
                                   (.stopPropagation ev)
                                   (dispatch `event/datomic-attribute-inspected
                                             {:dsid dsid :attribute (:db/ident item)})))}
                    (tr ["data"])]])
           item-meta))
       {::pv/default ::pv/table
        ::pv/table datomic-schema-table-opts})]))

(defn datomic-namespace-list [value]
  (let [item-meta {::pv/for {:action ::pv/hiccup}}]
    [ins/inspector
     {}
     (with-meta
       (->> value
            (group-by (fn [x]
                        (or (namespace (:db/ident x)) "")))
            (sort-by key)
            (map (fn [[k v]]
                   (with-meta
                     {:namespace k
                      :attributes v
                      :action [:div {:style {:display "flex"
                                             :justify-content "center"}}
                               [button
                                {:on-click (fn [ev]
                                             (let [{::keys [dsid]} (meta value)]
                                               (.stopPropagation ev)
                                               (dispatch `event/datomic-namespace-attributes-inspected
                                                         {:dsid dsid :attributes v})))}
                                (tr ["attributes"])]]}
                     item-meta))))
       (assoc (meta value)
              ::pv/default ::pv/table
              ::pv/table {:columns [:namespace :attributes :action]}))]))

(defn datomic-database-list-actions [{:keys [dsid db-name]}]
  [:div {:style {:display "flex"
                 :gap 6}}
   [button
    {:on-click (fn [ev]
                 (.stopPropagation ev)
                 (dispatch `event/datomic-database-inspected {:dsid dsid :db-name db-name}))}
    (tr ["namespaces"])]
   [button
    {:on-click (fn [ev]
                 (.stopPropagation ev)
                 (dispatch `event/datomic-database-attributes-inspected {:dsid dsid :db-name db-name}))}
    (tr ["attributes"])]
   [button
    {:on-click (fn [ev]
                 (.stopPropagation ev)
                 (dispatch `event/datomic-query-triggered {:dsid dsid :db-name db-name}))}
    (tr ["query"])]])

(defn datomic-database-list [value]
  (let [{::keys [dsid]} (meta value)]
    [ins/toggle-bg
     [ins/inspector
      {}
      (for [item value]
        (with-meta
          [:div {:style {:display "flex"
                         :flex-direction "row"
                         :align-items "center"}}
           [:div {:style {:flex-grow 1}}
            item]
           [datomic-database-list-actions {:db-name item :dsid dsid}]]
          {::pv/default ::pv/hiccup}))]]))

(defn table-item? [value]
  (and (map? value)
       (string? (:table-name value))))

(defn datasource? [value]
  (or (string? value)
      (map? value)))

(defn datasource-input-component [value]
  (let [{::keys [cell-id]} (meta value)
        default-value (or (cond
                            (string? value) value
                            (map? value) (if (and (= (count value) 1)
                                                  (string? (:jdbcUrl value)))
                                           (:jdbcUrl value)
                                           (pr-str value)))
                          "")]
    [:form {:on-submit (fn [ev]
                         (.preventDefault ev)
                         (.stopPropagation ev)
                         (let [datasource (-> ev .-target .-datasource .-value)
                               payload {:cell-id cell-id
                                        :value datasource}]
                           (case (-> ev .-nativeEvent .-submitter .-name)
                             "schema" (dispatch `event/datasource-input-schema-triggered payload)
                             "query" (dispatch `event/datasource-input-query-triggered payload))))
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

(defn datasource-component [value]
  [:div {:style {:display "flex"
                 :align-items "center"
                 :gap 6}}
   [:div {:style {:flex-grow 1}}
    ;; Would be nice to use inspector, but we need to reset default viewer.
    ;; However, the convenient `portal.viewer/default` helper can't be used in sci.
    [ins/inspector
     {}
     (if (string? value)
       value
       (vary-meta value assoc ::pv/default ::pv/pr-str))]]
   [button {:on-click (fn [ev]
                        (.stopPropagation ev)
                        (dispatch `event/datasource-schema-triggered value))}
    (tr ["schema"])]
   [button {:on-click (fn [ev]
                        (.stopPropagation ev)
                        (dispatch `event/datasource-query-triggered value))}
    (tr ["query"])]
   [button {:on-click (fn [ev]
                        (.stopPropagation ev)
                        (dispatch `event/datasource-edit-triggered value))}
    (tr ["edit"])]])

(defn datasource-list-component [value]
  [ins/inspector
   {}
   (for [item value]
     ;; Would be nicer to use (pv/default), but it can't be required
     (if (map? value)
       (with-meta
         item
         {::pv/default ::datasource})
       (with-meta
         [::datasource item]
         {::pv/default ::pv/hiccup})))])

(defn removable-item [props child]
  (let [{:keys [cell-id]} props]
    [:div {:style {:display "flex"
                   :flex-direction "row"
                   :align-items "flex-start"
                   :gap 6
                   ;; Add extra padding to avoid overlapping with portal's atom indicator
                   :padding-right 36
                   :max-width "calc(100vw - 80px)"}}
     [:div {:style {:flex-grow 1
                    :overflow-x "auto"}}
      child]
     [:div
      ;; margin to offset inspector border to make the remove button look aligned
      {:style {:margin 1}}
      [button
       {:on-click (fn [ev]
                    (.stopPropagation ev)
                    (dispatch `event/tap-removed cell-id))}
       "x"]]]))

;; Copy of portal.ui.inspector/container-coll because it is private
(defn container-coll [values child]
  (let [theme (theme/use-theme)]
    [ins/with-collection
     values
     [s/div
      ;; collection-header is private, but does not seem that useful on top-level, so just not showing it
      #_[collection-header values]
      [s/div
       {:style
        {:width "100%"
         :text-align :left
         :display :grid
         :background (ins/get-background)
         :grid-gap (:padding theme)
         :padding (:padding theme)
         :box-sizing :border-box
         :color (::c/text theme)
         :font-size  (:font-size theme)
         :border-bottom-left-radius (:border-radius theme)
         :border-bottom-right-radius (:border-radius theme)
         :border [1 :solid (::c/border theme)]}}
       child]]]))

;; Based on portal.ui.inspector/inspect-coll*
;; Differences:
;; - using cell-id for key to keep stable state when re-rendering
;; - wrapping in removable-item to be able to clear individual items
;; - no search matcher, portal does not filter on subcollections and top-level filtering of UI cards does not seem that useful
;; - not wrapping with [l/lazy-seq], portal.ui.lazy causes circular depedency, is it useful in top-level?
(defn root-component [app-db]
  (let [{::state/keys [running-tasks cells]} app-db]
    [:div
     (when (pos? running-tasks)
       [loading-indicator])
     [container-coll
      cells
      (map-indexed
       (fn [index [cell-id value]]
         (let [value (try
                        ;; Pass cell-id so complex widgets can reference state when dispatching events.
                        ;; Wrapping in try-catch to prevent erroring out on primitive values which don't support metadata.
                       (vary-meta value assoc ::cell-id cell-id)
                       (catch :default _ignore
                         value))]
           ^{:key cell-id}
           [removable-item {:cell-id cell-id}
            [select/with-position
             {:row index :column 0}
             [ins/with-key cell-id [ins/inspector value]]]]))
       cells)]]))

(p/register-viewer!
 {:name ::datasource
  :predicate datasource?
  :component datasource-component})

(p/register-viewer!
 {:name ::datasource-input
  :predicate #(= ::datasource-input (::pv/default (meta %)))
  :component datasource-input-component})

(p/register-viewer!
 {:name ::datasource-list
  :predicate (fn [value]
               (and (sequential? value)
                    (datasource? (first value))))
  :component datasource-list-component})

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
 {:name ::datomic-attribute-list
  :predicate (fn [value]
               (and (sequential? value)
                    (= (::pv/default (meta value)) ::datomic-attribute-list)))
  :component datomic-attribute-list})

(p/register-viewer!
 {:name ::datomic-namespace-list
  :predicate (fn [value]
               (and (sequential? value)
                    (= (::pv/default (meta value)) ::datomic-namespace-list)))
  :component datomic-namespace-list})

(p/register-viewer!
 {:name ::datomic-database-list
  :predicate (fn [value]
               (and (sequential? value)
                    (= (::pv/default (meta value)) ::datomic-database-list)))
  :component datomic-database-list})

(p/register-viewer!
 {:name ::query
  :predicate (fn [value]
               (string? value))
  :component query-component})

(p/register-viewer!
 {:name ::query-editor
  :predicate (fn [value]
               (contains? (meta value) ::query-editor))
  :component query-editor-component})

(p/register-viewer!
 {:name ::datomic-query-editor
  :predicate (fn [value]
               (contains? (meta value) ::datomic-query-editor))
  :component datomic-query-editor-component})

(p/register-viewer!
 {:name ::datagrid
  :predicate (fn [value]
               (map? (::datagrid (meta value))))
  :component datagrid-component})

(p/register-viewer!
 {:name ::root
  :predicate (fn [value]
               (and (map? value)
                    (contains? value ::state/cells)))
  :component root-component})
