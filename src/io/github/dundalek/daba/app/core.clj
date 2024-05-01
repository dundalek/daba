(ns io.github.dundalek.daba.app.core
  (:require
   [io.github.dundalek.daba.app.state :as state]))

(defn get-source [db dsid]
  (get-in db [::state/sources dsid]))
