(ns io.github.dundalek.daba.datomic.port)

(defprotocol Datomic
  (client [_ arg-map])
  (connect [_ client arg-map])
  (db [_ conn])
  (pull [_ db arg-map])
  (list-databases [_ client arg-map])
  (q [_ arg-map]))
