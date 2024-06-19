(ns io.github.dundalek.daba.app.stories-test
  (:require
   [clojure.test :refer [deftest is]]
   [io.github.dundalek.daba.app :as app]
   [io.github.dundalek.daba.app.state :as state]
   [io.github.dundalek.daba.app.stories :as stories]))

(deftest foo
  (stories/generate-doc-tree)
  (is (= 9 (-> @app/!app-db ::state/cells count))))
