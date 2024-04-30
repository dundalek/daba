(ns io.github.dundalek.daba.viewer
  (:require
   [portal.ui.api :as p]
   [portal.ui.inspector :as ins]
   [io.github.dundalek.daba.app.state :as-alias state]
   [io.github.dundalek.daba.app :as-alias app]))

(defn schema-list-component [value]
  [ins/inspector (with-meta value
                   {:portal.viewer/default :portal.viewer/table
                    :portal.viewer/table {:columns [:table-schem]}})])

(defn table-list-component [value]
  [ins/inspector (with-meta value
                   {:portal.viewer/default :portal.viewer/table
                    :portal.viewer/table {:columns [:table-name :table-type]}})])

(p/register-viewer!
 {:name ::table-list
  :predicate sequential?
  :component table-list-component})

(p/register-viewer!
 {:name ::schema-list
  :predicate sequential?
  :component schema-list-component})
