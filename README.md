Web framework for ClojureScript on Node. **WIP**.

In the tradition of Django, Flask, and Rails.
Designed for indie devs who ship fast.
Battle tested on real sites.

[Quick start](#quick-start) | [Examples](https://github.com/chr15m/sitefox/tree/main/examples) | [API](#api)

## Philosophy

 * Minimal.
 * [12 factor](https://12factor.net/).
 * 👇 Batteries included.

## Batteries included

 * [Routing (express)](#web-server-routes)
 * [Sessions + authentication](#sessions)
 * [Templates](#templates)
 * [Database + Key-value store](#database)
 * [Email](#email)
 * [Logging](#logging)

## Quick start

Add Sitefox to your project as a dependency.

```
{:deps
 {io.github.chr15m/sitefox {:git/tag "v0.0.1" :git/sha "????"}}}
```

An example server with two routes, one of which writes values to the key-value database.

```clojure
(ns my.server
  (:require
    [sitefox.web :as web]
    [sitefox.db :refer [kv]]
    [sitefox.reloader :refer [reloader]]))

(defn home-page [req res]
  ; send a basic hello world response
  (.send res "Hello world!"))

(defn hello [req res]
  ; write a value to the db key value database
  (-> (kv "sometable")
    (.write "key" "42")
    (.then
      (fn []
        ; database write is done
        ; send a basic hello world response
        (.json res true))))

(defn setup-routes [app]
  ; flush all routes from express
  (web/reset-routes app)
  ; set up an express route for "/"
  (.get app "/" home-page)
  ; set up an express route for "/hello"
  (.post app "/hello" hello)
  ; statically serve files from the "public" dir on "/"
  ; (or from "build" dir in PROD mode)
  (web/static-folder app "/" (if (env "PROD") "build" "public")))

(defn main! []
  ; create an express server and start serving
  ; BIND_ADDRESS & PORT env vars set host & port.
  (-> (web/start)
    (.then (fn [app host port]
      ; reload the routes when the server js is modified (recompiled)
      (reloader (partial #'setup-routes app))
      ; set up the routes for the first time
      (setup-routes app)))))
```

More [Sitefox examples here](./examples).

## API

### Web server & routes

Sitefox uses the `express` web server with sensible defaults for sessions and logging.
Create a new server with `web/start` and set up a route which responds with "Hello world!" as follows:

```clojure
(-> (web/start)
  (.then (fn [app host port]
    (.get app "/myroute"
      (fn [req res]
        (.send res "Hello world!"))))
```

Sitefox comes with an optional system to reload routes when the server is changed.
Your express routes will be reloaded every time your server code is refreshed (e.g. by a shadow-cljs build).
In this example the function `setup-routes` will be called when a rebuild occurs.

```clojure
(defn setup-routes [app]
  ; flush all routes from express
  (web/reset-routes app)
  ; ask express to handle the route "/"
  (.get app "/" (fn [req res] (.send res "Hello world!"))))

; during the server setup hook up the reloader
(reloader (partial #'setup-routes app))
```

I recommend the [promesa](https://github.com/funcool/promesa) library for managing promise control flow.
This example assumes require `[promesa.core :as p]`:

```clojure
(p/let [[app host port] (web/start)]
  ; now use express `app` to set up routes and middleware
  )
```

### Database

Sitefox makes it easy to start storing key-value data with no configuration.
You can then transition to more structured data later if you need.
It bundles [Keyv](https://github.com/lukechilds/keyv) which is a database backed key-value store.

By default a local sqlite database is used so you can start persisting data on the server immediately without any configuration.
Once you move to production you can configure another database using the environment variable `DATABASE_URL`.
For example, to use a postgres database called "somedatabase": `DATABASE_URL=postgres://someuser:somepassword@somehost:5432/somedatabase`.
Or simply `postgres:///somedatabase` if your user has local access on the deploy server.

Use the database and key-value interface as follows.

Require `kv` from the database module:

```clojure
[sitefox.db :as db]
```

Now you can use `db/kv` to write a key-value to a namespaced "table":

```clojure
(let [table (db/kv "sometable")]
  (.set table "key" "42"))
```

Retrieve the value again:

```clojure
(-> (.get table "key)
  (.then (fn [val] (print val))))
```

You can use `db/client` to access the underlying database connection.
For example to make a query against the configured database:

```clojure
(let [c (db/client)]
  (-> (.query db "select * from sometable WHERE x = 1")
    (.then (fn [rows] (print rows)))))
```

Again, [promesa](https://github.com/funcool/promesa) is recommended for managing control flow during database operations.

### Sessions + authentication

TBD.

### Templates

TBD.

### Email

TBD.

### Logging

TBD.

## Who

Sitefox was made by [Chris McCormick](https://mccormick.cx) ([@mccrmx](https://twitter.com/mccrmx)).
I iterated on it while building sites for myself and for clients.
