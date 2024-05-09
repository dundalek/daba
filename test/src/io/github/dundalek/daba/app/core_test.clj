(ns io.github.dundalek.daba.app.core-test
  (:require
   [clojure.test :refer [deftest is]]
   [io.github.dundalek.daba.app.core :as core]))

(deftest parse-db-spec-test
  (is (= {:jdbcUrl "jdbc:"} (core/parse-db-spec "")))

  (is (= {:jdbcUrl "jdbc:sqlite:path/to/db"} (core/parse-db-spec "jdbc:sqlite:path/to/db")))
  (is (= {:jdbcUrl "jdbc:sqlite:path/to/db"} (core/parse-db-spec "sqlite:path/to/db")))
  (is (= {:jdbcUrl "jdbc:}"} (core/parse-db-spec "}")))
  (is (= {:jdbcUrl "jdbc:123"} (core/parse-db-spec "123")))
  (is (= {:a 1} (core/parse-db-spec "{:a 1}")))
  (is (= {:a 1} (core/parse-db-spec {:a 1}))))

(deftest coerce-query-test
  (is (= {:statement "SELECT * FROM table" :offset 0 :limit core/default-page-size}
         (core/coerce-query "SELECT * FROM table")))
  (is (= {:statement "SELECT * FROM table" :offset 0 :limit core/default-page-size}
         (core/coerce-query {:statement "SELECT * FROM table"}))))
