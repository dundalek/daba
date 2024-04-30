(ns io.github.dundalek.daba.api
  (:require
   [clojure.string :as str]
   [portal.api :as p]))

(defn open
  ([] (open nil))
  ([opts]
   (p/open (merge {:on-load (fn [])}
                  opts))))

(defn inspect [db-spec]
  (let [db-spec (if (and (string? db-spec) (not (str/starts-with? db-spec "jdbc:")))
                  (str "jdbc:" db-spec)
                  db-spec)]
    ((requiring-resolve 'io.github.dundalek.daba.app/inspect-database!) db-spec)))
