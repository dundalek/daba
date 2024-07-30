(ns io.github.dundalek.daba.datomic.adapter
  (:require
   [datomic.client.api :as d]
   [io.github.dundalek.daba.datomic.port :as port]))

(deftype DatomicAdapter []
  port/Datomic
  (client [_ arg-map]
    (d/client arg-map))
  (connect [_ client arg-map]
    (d/connect client arg-map))
  (db [_ conn]
    (d/db conn))
  (pull [_ db arg-map]
     (d/pull db arg-map))
  (list-databases [_ client arg-map]
     (d/list-databases client arg-map))
  (q [_ arg-map]
    (d/q arg-map)))

(defn make []
  (->DatomicAdapter))
