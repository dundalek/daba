# Table of contents
-  [`daba.api`](#daba.api) 
    -  [`inspect`](#daba.api/inspect) - Add a datasource view value that can be used as an entrypoint to explore the database.
    -  [`open`](#daba.api/open) - Open a Portal window.
    -  [`query->columns`](#daba.api/query->columns) - Extract sequence of columns with order based on the query result.
    -  [`submit`](#daba.api/submit) - Submit a value.

-----
# <a name="daba.api">daba.api</a>






## <a name="daba.api/inspect">`inspect`</a><a name="daba.api/inspect"></a>
``` clojure

(inspect)
(inspect db-spec)
```

Add a datasource view value that can be used as an entrypoint to explore the database. Pass a next.jdbc db-spec value.
<p><sub><a href="https://github.com/dundalek/daba/blob/master/components/core/src/daba/api.clj#L16-L19">Source</a></sub></p>

## <a name="daba.api/open">`open`</a><a name="daba.api/open"></a>
``` clojure

(open)
(open opts)
```

Open a Portal window. This is a wrapper over `portal.api/open` which loads custom viewers.
<p><sub><a href="https://github.com/dundalek/daba/blob/master/components/core/src/daba/api.clj#L5-L9">Source</a></sub></p>

## <a name="daba.api/query->columns">`query->columns`</a><a name="daba.api/query->columns"></a>
``` clojure

(query->columns value)
```

Extract sequence of columns with order based on the query result.
<p><sub><a href="https://github.com/dundalek/daba/blob/master/components/core/src/daba/api.clj#L21-L24">Source</a></sub></p>

## <a name="daba.api/submit">`submit`</a><a name="daba.api/submit"></a>
``` clojure

(submit value)
```

Submit a value. Regular `portal.api/submit` would not work with individually removable items.
<p><sub><a href="https://github.com/dundalek/daba/blob/master/components/core/src/daba/api.clj#L11-L14">Source</a></sub></p>
