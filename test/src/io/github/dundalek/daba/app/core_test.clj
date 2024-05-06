(ns io.github.dundalek.daba.app.core-test
  (:require
   [clojure.test :refer [deftest is]]
   [io.github.dundalek.daba.app.core :as core]))

(deftest parse-db-spec-test
  (is (= "jdbc:sqlite:path/to/db" (core/parse-db-spec "jdbc:sqlite:path/to/db")))
  (is (= "jdbc:sqlite:path/to/db" (core/parse-db-spec "sqlite:path/to/db")))
  (is (= "jdbc:}" (core/parse-db-spec "}")))
  (is (= "jdbc:123" (core/parse-db-spec "123")))
  (is (= {:a 1} (core/parse-db-spec "{:a 1}"))))
