(ns io.github.dundalek.daba.app.core
  (:require
   [clojure.edn :as edn]
   [clojure.string :as str]
   [io.github.dundalek.daba.app.state :as state]
   [next.jdbc :as jdbc]))

(defn get-source [_db dsid]
  ;; The initial idea was to create datasource once, and use generated DSID
  ;; (Data Source ID) to pass around between portal client and server process
  ;; and use it for lookup. The challenge is to keep the datasource as long as
  ;; there exist taps referencing it.
  ;; So to make things easier for now we use db-spec (since it is just a value)
  ;; as DSID and we don't need to keep extra state. This has an overhead that
  ;; we construct a datasource for every request. It should be tolerable for
  ;; now and can be optimized in the future.
  ; (get-in db [::state/sources dsid])
  (let [db-spec dsid
        ds (jdbc/get-datasource db-spec)]
    {::state/ds ds
     ::state/db-spec dsid
     ::state/dsid dsid}))

(defn append-tap [db value]
  (update db ::state/taps conj value))

(defn parse-db-spec [db-spec]
  (or (try
        (let [parsed (edn/read-string db-spec)]
          (when (map? parsed)
            parsed))
        (catch Exception _ignore))
      (if (not (str/starts-with? db-spec "jdbc:"))
        (str "jdbc:" db-spec)
        db-spec)))
