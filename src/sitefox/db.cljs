(ns sitefox.db
  "Lightweight database access.
  The environment variable `DATABASE_URL` configures which database to connect to.

  By default it is set to use a local sqlite database: `sqlite://./database.sqlite`

  `DATABASE_URL` for Postgres: `postgresql://[user[:password]@][netloc][:port][,...][/dbname][?param1=value1&...]`"
  (:require
    [sitefox.util :refer [env]]
    [sitefox.deps :refer [Keyv]]))

(def database-url (env "DATABASE_URL" "sqlite://./database.sqlite"))

(defn kv
  "Access a database backed key-value store ([Keyv](https://github.com/lukechilds/keyv) instance).
  `kv-ns` is a namespace (i.e. table).
  The database to use can be configured with the `DATABASE_URL` environment variable.
  See the Keyv documentation for details.
  The promise based API has methods like `(.set kv ...)` and `(.get kv ...)` which do what you'd expect."
  [kv-ns]
  (Keyv. database-url (clj->js {:namespace kv-ns})))

(defn client
  "The database client that is connected to `DATABASE_URL`.
  This allows you to make raw queries against the database."
  []
  (->
    (Keyv. database-url)
    (aget "opts" "store")))

(defn ls
  "List all key-value entries matching a particular namespace and prefix.
   Returns a promise that resolves to rows of JSON."
  [kv-ns & [pre db]]
  (->
    (.query (or db (client)) (str "select * from keyv where key like '" kv-ns ":" (or pre "") "%'"))
    (.then #(.map % (fn [row]
                      (let [k (aget row "key")
                            v (aget (js/JSON.parse (aget row "value")) "value")]
                        (aset v "kind" k)
                        v))))))
