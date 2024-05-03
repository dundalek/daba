(ns io.github.dundalek.daba.app.frame
  (:require
   [io.github.dundalek.daba.internal.miniframe :as mf]))

(def ^:dynamic *frame* nil)

(defn dispatch [event]
  (mf/dispatch *frame* event))
