(ns daba.api
  (:require
   [portal.api :as p]))

(defn on-load []
  (p/eval-str (slurp "examples/portal-present/src/portal_present/viewer.cljs")))

(defn open
  ([] (open nil))
  ([opts]
   (p/open (merge {:on-load (fn [])}
                  opts))))

(comment
  (inspect "jdbc:sqlite:tmp/Chinook_Sqlite_AutoIncrementPKs.sqlite")

  (inspect (System/getenv "POSTGRES_URI"))

  (require '[portal.api :as p])

  (def p (p/open))
  (p/clear)
  (p/close)

  (add-tap #'p/submit)
  (remove-tap #'p/submit)
  (tap> :hello)
  (prn @p)

  (p/docs))


