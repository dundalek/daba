(ns io.github.dundalek.daba.datomic.lib
  (:require
   [clojure.string :as str]
   [io.github.dundalek.daba.datomic.port :as d]))

;; Indirection of the adapter, so that Datomic can be loaded optionally.
;; A potential way to improve error messages is to implement null adapter that would throw exceptions with more descriptive messages when protocol methods are invoked.
(def ^:dynamic *adapter*
  (try
    ((requiring-resolve 'io.github.dundalek.daba.datomic.adapter/make))
    (catch Exception _ignore
      ;; consider logging exception at debug level
      nil)))

(defn- datasource->db [{:keys [client-args connection-args]}]
  (let [client (d/client *adapter* client-args)
        conn (d/connect *adapter* client connection-args)]
    (d/db *adapter* conn)))

(defn- get-db-schema [db]
  (->> (d/pull *adapter* db '{:eid 0 :selector [{:db.install/attribute [*]}]})
       :db.install/attribute
       (remove (fn [m]
                 (or (some-> m :db/ident namespace (str/starts-with? "db"))
                     (some-> m :db/ident namespace (str/starts-with? "fressian")))))
       (map #(update % :db/valueType :db/ident))
       (map #(update % :db/cardinality :db/ident))
       ;; also :db/unique ?
       (sort-by :db/ident)))

(defn get-schema [{:keys [client-args connection-args]}]
  (let [client (d/client *adapter* client-args)
        conn (d/connect *adapter* client connection-args)]
    (get-db-schema (d/db *adapter* conn))))

(defn get-databases [client-args]
  (let [client (d/client *adapter* client-args)]
    (d/list-databases *adapter* client {})))

(defn query [datasource-spec arg-map]
  (let [db (datasource->db datasource-spec)]
    (d/q *adapter* (assoc arg-map :args [db]))))

