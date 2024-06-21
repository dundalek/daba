(ns io.github.dundalek.daba.app.stories-test
  (:require
   [clojure.test :refer [deftest is]]
   [io.github.dundalek.daba.app :as app]
   [io.github.dundalek.daba.app.state :as state]
   [io.github.dundalek.daba.app.stories :as stories]))

(deftest sql-stories
  (stories/sql-doc-tree)
  (is (= 9 (-> @app/!app-db ::state/cells count))))

(deftest datomic-stories
  (stories/datomic-doc-tree)
  (is (= 11 (-> @app/!app-db ::state/cells count))))

(deftest xtdb-stories
  (stories/xtdb-doc-tree)
  (is (= 7 (-> @app/!app-db ::state/cells count))))
