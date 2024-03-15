(ns sitefox.db
  "Lightweight database access.
  The environment variable `DATABASE_URL` configures which database to connect to.

  By default it is set to use a local sqlite database: `sqlite://./database.sqlite`

  `DATABASE_URL` for Postgres: `postgresql://[user[:password]@][netloc][:port][,...][/dbname][?param1=value1&...]`"
  (:require
    [clojure.test :refer-macros [is async use-fixtures]]
    [promesa.core :as p]
    [sitefox.util :refer [env]]
    [sitefox.deps :refer [Keyv]]))

(def database-url (env "DATABASE_URL" "sqlite://./database.sqlite"))

(defn kv
  "Access a database backed key-value store ([Keyv](https://github.com/lukechilds/keyv) instance).
  `kv-ns` is a namespace (i.e. table).
  The database to use can be configured with the `DATABASE_URL` environment variable.
  See the Keyv documentation for details.
  The promise based API has methods like `(.set kv ...)` and `(.get kv ...)` which do what you'd expect."
  {:test (fn []
           (when (env "TESTING")
             (async done
                    (p/let [d (kv "tests")
                            v #js [1 2 3]
                            _ (.set d "hello" v)
                            y (.get d "hello")]
                      (is (= (js->clj v) (js->clj y))))
                    (done))))}
  [kv-ns]
  (Keyv. database-url (clj->js {:namespace kv-ns})))

(defn client
  "The database client that is connected to `DATABASE_URL`.
  This allows you to make raw queries against the database."
  {:test (fn []
           (async done
             (p/let [c (client)
                     dialect (aget c "opts" "dialect")
                     version-fn (case dialect
                                  "sqlite" "sqlite_version()"
                                  "version()")
                     v (.query c (str "SELECT " version-fn " AS v"))]
               (is (aget c "query"))
               (is (-> v (aget 0) (aget "v")))
               (done))))}
  []
  (->
    (Keyv. database-url)
    (aget "opts" "store")))

(defn ls
  "List all key-value entries matching a particular namespace and prefix.
  Returns a promise that resolves to rows of JSON."
  {:test (fn []
           (when (env "TESTING")
             (async done
                    (p/let [d (kv "tests")
                            fixture [["first:a" 1] ["first:b" 2] ["second:c" 3] ["second:d" 4]]
                            _ (p/all (map #(.set d (first %) (second %)) fixture))
                            one (ls "tests" "first")
                            two (ls "tests" "second")
                            one-test (set (map second (subvec fixture 0 2)))
                            two-test (set (map second (subvec fixture 2 4)))]
                      (is (= (set one) one-test))
                      (is (= (set two) two-test))
                      (.clear d)
                      (done)))))}
  [kv-ns & [pre db filter-function]]
  ; TODO: run the map & filter over each row streaming out
  (->
    (.query (or db (client)) (str "select * from keyv where key like '" kv-ns ":" (or pre "") "%'"))
    (.then #(.map % (fn [row]
                      (let [k (aget row "key")
                            v (aget (js/JSON.parse (aget row "value")) "value")]
                        (when (= (type v) js/Object)
                          (aset v "kind" k))
                        v))))
    (.then (if filter-function
             #(.filter % filter-function)
             identity))))

(defn f
  "Filter all key-value entries matching a particular namespace and prefix,
  through the supplied"
  {:test (fn []
           (when (env "TESTING")
             (async done
                    (p/let [d (kv "tests")
                            fixture [["first:a" 1] ["first:b" 2] ["first:c" 3]
                                     ["second:c" 3] ["second:d" 4] ["second:e" 5]]
                            _ (p/all (map #(.set d (first %) (second %)) fixture))
                            filter-fn #(= (mod % 2) 1)
                            one (f "tests" filter-fn "first")
                            two (f "tests" filter-fn "second")
                            one-test (set (filter filter-fn (map second (subvec fixture 0 3))))
                            two-test (set (filter filter-fn (map second (subvec fixture 3 6))))]
                      (is (= (set one) one-test))
                      (is (= (set two) two-test))
                      (.clear d)
                      (done)))))}
  [kv-ns filter-function & [pre db]]
  (ls kv-ns pre db filter-function))

(defn ensure-local-postgres-db-exists
  "If DATABASE_URL is for a postgres url then create it if it does not already exist.
  An example of an on-disk db URL: 'postgres://%2Fvar%2Frun%2Fpostgresql/DBNAME'."
  [& [db-url]]
  (let [db-url (or db-url database-url)]
    (when (.startsWith (js/decodeURIComponent db-url) "postgres:///")
      (let [execSync (aget (js/require "child_process") "execSync")
            db-name (-> db-url (.split "/") last)
            cmd (str "psql '" db-name "' -c ';' 2>/dev/null && echo 'Database " db-name " exists.' || createdb '" db-name "'")]
        (execSync cmd #js {:shell true :stdio "inherit"})
        ; connect once to initiate table creation
        (p/let [db (Keyv. db-url)
                query (aget db "opts" "store" "query")
                version-query (query "SELECT version();")]
          version-query)))))

; make sure postgres is set up to run the tests
(use-fixtures
  :once {:before #(async done (p/do! (ensure-local-postgres-db-exists) (done)))
         #_#_ :after #(async done (done))})
