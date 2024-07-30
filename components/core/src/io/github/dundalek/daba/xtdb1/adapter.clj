(ns io.github.dundalek.daba.xtdb1.adapter
  (:require
   [xtdb.api :as xt]
   [io.github.dundalek.daba.xtdb1.port :as port]))

(deftype XDTBAdapter []
  port/XTDB
  (start-node [_ options]
    (xt/start-node options))

  (attribute-stats [_ node]
    (xt/attribute-stats node))

  (db [_ node]
    (xt/db node))

  (q [_ db q]
    (xt/q db q)))

(defn make []
  (->XDTBAdapter))
