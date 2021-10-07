ClojureScript on Node backend web framework. **WIP**.

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
 * [Sessions](#sessions)
 * [Database + Key-value store](#database)
 * [Email](#email)
 * [Logging](#logging)
 * Forms (soon)

## Quick start

```
{:deps
 {io.github.chr15m/sitefox {:git/tag "v0.0.1" :git/sha "????"}}}
```

```clojure
(ns my.server
  (:require
    [sitefox.web :as web]
    [sitefox.db :refer [kv]]
    [sitefox.reloader :refer [reloader]]))

(defn home-page [req res]
  ; write a value to the db key value store
  ; (defaults to sqlite)
  (-> (kv "sometable")
    (.write "key" "42")
    (.then
      (fn []
        ; database write is done
        ; send a basic hello world response
        (.send res (r "Hello world!")))))

(defn setup-routes [app]
  ; flush all routes from express
  (web/reset-routes app)
  ; set up an express route for "/"
  (.get app "/" home-page)
  ; statically serve files from "public" on "/"
  ; (or from "build" in PROD mode)
  (web/static-folder app "/" (if (env "PROD") "build" "public")))

(defn main! []
  ; create an express server and start serving
  ; BIND_ADDRESS & PORT env vars set host & port.
  (-> (web/start)
    (.then (fn [app host port]
      ; reload the routes when the server js is recompiled
      (reloader (partial #'setup-routes app))
      ; set up the routes for the first time
      (setup-routes app)))))
```

More [Sitefox examples here](./examples).

## API

### Web server & routes

Sitefox uses `express` with sensible defaults. Routing is done in the same way as express.

### Database

TBD.

### Sessions

TBD.

### Email

TBD.

### Logging

TBD.

## Who

Sitefox was made by [Chris McCormick](https://mccormick.cx) ([@mccrmx](https://twitter.com/mccrmx)).
I iterated on it while building sites for myself and for clients.
