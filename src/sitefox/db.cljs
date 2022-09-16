(ns sitefox.db
  "Lightweight database access.
  The environment variable `DATABASE_URL` configures which database to connect to.

  By default it is set to use a local sqlite database: `sqlite://./database.sqlite`

  `DATABASE_URL` for Postgres: `postgresql://[user[:password]@][netloc][:port][,...][/dbname][?param1=value1&...]`"
  (:require
    [clojure.test :refer-macros [is async]]
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
                     v (.query c "SELECT sqlite_version()")]
               (is (aget c "query"))
               (is (-> v (aget 0) (aget "sqlite_version()")))
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
                        (aset v "kind" k)
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
