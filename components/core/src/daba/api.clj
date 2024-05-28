(ns daba.api
  (:require
   [io.github.dundalek.daba.app :as app]))

(defn open
  ([] (open nil))
  ([opts]
   (app/open opts)))

(defn submit [value]
  (app/submit value))

(defn inspect
  ([] (inspect ""))
  ([db-spec] (app/inspect-datasource db-spec)))

(defn query->columns [value]
  (app/query->columns value))
