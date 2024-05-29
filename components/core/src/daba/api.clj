(ns daba.api
  (:require
   [io.github.dundalek.daba.app :as app]))

(defn open
  "Open a Portal window. This is a wrapper over `portal.api/open` which loads custom viewers."
  ([] (open nil))
  ([opts]
   (app/open opts)))

(defn submit
  "Submit a value. Regular `portal.api/submit` would not work with individually removable items."
  [value]
  (app/submit value))

(defn inspect
  "Add a datasource view value that can be used as an entrypoint to explore the database. Pass a next.jdbc db-spec value."
  ([] (inspect ""))
  ([db-spec] (app/inspect-datasource db-spec)))

(defn query->columns
  "Extract sequence of columns with order based on the query result."
  [value]
  (app/query->columns value))
