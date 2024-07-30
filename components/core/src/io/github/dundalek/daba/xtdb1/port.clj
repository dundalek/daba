(ns io.github.dundalek.daba.xtdb1.port)

(defprotocol XTDB
  (start-node [_ options])

  (attribute-stats [_ node])

  (db [_ node])

  (q [_ db q]))
