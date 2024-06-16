(ns io.github.dundalek.daba.lib.datomic
  (:require
   [clojure.string :as str]
   [datomic.client.api :as d]))

(defn- datasource->db [{:keys [client-args connection-args]}]
  (let [client (d/client client-args)
        conn (d/connect client connection-args)]
    (d/db conn)))

(defn- get-db-schema [db]
  (->> (d/pull db '{:eid 0 :selector [{:db.install/attribute [*]}]})
       :db.install/attribute
       (remove (fn [m]
                 (or (some-> m :db/ident namespace (str/starts-with? "db"))
                     (some-> m :db/ident namespace (str/starts-with? "fressian")))))
       (map #(update % :db/valueType :db/ident))
       (map #(update % :db/cardinality :db/ident))
       ;; also :db/unique ?
       (sort-by :db/ident)))

(defn get-schema [{:keys [client-args connection-args]}]
  (let [client (d/client client-args)
        conn (d/connect client connection-args)]
    (get-db-schema (d/db conn))))

(defn get-databases [client-args]
  (let [client (d/client client-args)]
    (d/list-databases client {})))

(defn query [datasource-spec query]
  (let [db (datasource->db datasource-spec)]
    (d/q {:query query
          ;; FIXME: parameterize
          :limit 25
          :args [db]})))

(defn inspect-attribute [datasource-spec attribute]
  (query datasource-spec
         {:find '[?entity-id ?attr-value]
          :where [['?entity-id attribute '?attr-value]]}))
