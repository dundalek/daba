(ns io.github.dundalek.daba.lib.drivers-test
  (:require
   [clojure.test :refer [deftest is]]
   [io.github.dundalek.daba.lib.drivers :as drivers]))

(deftest db-spec->dbtype-test
  (is (= "sqlite" (drivers/db-spec->dbtype "jdbc:sqlite:tmp")))
  (is (= "duckdb" (drivers/db-spec->dbtype {:jdbcUrl "jdbc:duckdb:tmp"})))
  (is (= "h2" (drivers/db-spec->dbtype {:dbtype "h2"})))
  (is (= nil (drivers/db-spec->dbtype nil))))
