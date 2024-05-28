(ns io.github.dundalek.daba.lib.drivers
  "This namespace is an attempt to speed up startup by not loading all JDBC drivers on startup,
but instead loading them lazily only when used by calling `ensure-loaded!` before making a query.

The idea sounded simple: Use `clojure.repl.deps/add-libs` under the hood to dynamically fetch libs and add them to classpath.

But there is a challenge with dynamically loaded JDBC drivers when there are multiple threads involved.
It makes sense to offload driver loading and querying to another thread to not block processing the UI events.
However, JDBC DriverManager does not allow using drivers loaded using another classloader (`isDriverAllowed` method).
And spawning a thread with `future` thread can end up with different classloaders.

Workaround approaches:

1) Wrap calls to `ensure-loaded!` with `future-call-conveying-classloader` that preserves thread context loader.
   Therefore JDBC DriverManager check will succeed.
   This is brittle as threads spawn using a regular `future` or a different mechanism can end up broken.
2) Workaround by creating `DriverShim` class as described in https://www.kfu.com/~nsayer/Java/dyn-jdbc.html and manually register driver after dynamically loaded with `DriverManager.registerDriver`.
   The shim delegates to the actual driver and JDBC is happy.
   But it's a hassle having to distribute a binary class.
3) We could bypass `DriverManager` completely by reifying `DataSource` and implementing `getConnection` to use the driver manually which would be used by next.jdbc.
   We would still need some fallback handling to handle drivers that were included legitimately on the static classpath.

For now decided to throw in a towel and bundle common drivers by default and additional ones need to be added to deps."
  (:require
   [clojure.repl.deps :as deps]
   [clojure.string :as str]
   [next.jdbc :as jdbc]
   [next.jdbc.connection :as connection])
  (:import
   ; [daba.jdbc DriverShim]
   [java.sql Driver DriverManager]))

;; dbtypes defined in https://github.com/seancorfield/next-jdbc/blob/f03b1ba3168168d1cad2580a42fcf4b604310c49/src/next/jdbc/connection.clj#L69
(def libs
  '{"duckdb" {org.duckdb/duckdb_jdbc {:mvn/version "0.10.2"}}
    "h2" {com.h2database/h2 {:mvn/version "2.2.224"}}
    "postgresql" {org.postgresql/postgresql {:mvn/version "42.7.3"}}
    "sqlite" {org.xerial/sqlite-jdbc {:mvn/version "3.43.0.0"}}})

(defn db-spec->dbtype [db-spec]
  (let [normalized-db-spec
        (cond
          (string? db-spec) (connection/uri->db-spec db-spec)

          (and (map? db-spec) (string? (:jdbcUrl db-spec)))
          (connection/uri->db-spec (:jdbcUrl db-spec))

          (map? db-spec) db-spec)]
    (:dbtype normalized-db-spec)))

(defn detect-driver-available-using-connection? [db-spec]
  (try
    (.close (jdbc/get-connection db-spec))
    true
    (catch java.sql.SQLException e
      (if (str/starts-with? (ex-message e) "No suitable driver found")
        false
        true))))

(defn load-driver-class [dbtype]
  (or (get @@#'connection/driver-cache dbtype) ; reaching into internals as an optimization
      (let [classname (:classname (get connection/dbtypes dbtype))
            classnames (if (string? classname) [classname] classname)]
        (some #(clojure.lang.RT/loadClassForName %) classnames))))

(defn detect-driver-available-using-class-loader? [db-spec]
  (some? (load-driver-class (db-spec->dbtype db-spec))))

(defn print-manager-drivers []
  (let [drivers (DriverManager/getDrivers)]
    (loop []
      (when (.hasMoreElements drivers)
        (println (.nextElement drivers))
        (recur)))))

(defn print-classloader-stack []
  (println "*use-context-classloader*" *use-context-classloader*)
  (loop [cl (.getContextClassLoader (Thread/currentThread))]
    (when cl
      (println cl)
      (recur (.getParent cl)))))

(defn driver-registered? [driver-class-name]
  (let [drivers (DriverManager/getDrivers)]
    (loop []
      (when (.hasMoreElements drivers)
        (let [driver (.nextElement drivers)
              #_#_driver (if (instance? DriverShim driver)
                           (.getDriver driver)
                           driver)]
          (if (= driver-class-name (-> driver .getClass .getName))
            true
            (recur)))))))

(defn ensure-driver-registered! [dbtype]
  #_(when-some [driver-class (load-driver-class dbtype)]
      (when-not (driver-registered? (.getName driver-class))
        (let [^Driver driver (.newInstance driver-class)
              shim (DriverShim. driver)]
          (DriverManager/registerDriver shim)))))

(defn ensure-dynamic-context-classloader! []
  (let [cl (.getContextClassLoader (Thread/currentThread))]
    (when-not (instance? clojure.lang.DynamicClassLoader cl)
      (.setContextClassLoader (Thread/currentThread) (clojure.lang.DynamicClassLoader. cl)))))

(defn ensure-loaded! [db-spec]
  ;; wrap in future-call-conveying-classloader
  (when-not (detect-driver-available-using-connection? db-spec)
    (let [dbtype (->> (db-spec->dbtype db-spec))]
      (when-some [coords (get libs dbtype)]
        ;; extra insurance to avoid "Context classloader is not a DynamicClassLoader" error
        ; (ensure-dynamic-context-classloader!)
        (binding [*repl* true]
          ;; could consider `deps/add-lib` with single argument to always fetch latest
          (deps/add-libs coords))

        ; (print-manager-drivers)
        (ensure-driver-registered! dbtype)))))
        ; (print-manager-drivers)

(defn future-call-conveying-classloader [f]
  ;; Similarly to how `future` and `future-call` convey dynamic bindings
  ;; this wrapper explicitly conveys thread context classloader.
  ; (println "parent classloader stack")
  ; (print-classloader-stack)
  (let [parent-thread-cl (.getContextClassLoader (Thread/currentThread))]
    (future
      ; (println "in future")
      ; (print-classloader-stack)
      (.setContextClassLoader (Thread/currentThread) parent-thread-cl)
      (f))))

(comment
  (print-manager-drivers)

  ;; Compare that classloader stack are different
  (print-classloader-stack)
  (future print-classloader-stack)

  (future-call-conveying-classloader print-classloader-stack)

  (ensure-loaded! "sqlite:./tmp/some-sqlite.db")

  (clojure.lang.RT/loadClassForName "org.sqlite.JDBC")
  (clojure.lang.RT/loadClassForName "org.h2.Driver"))
