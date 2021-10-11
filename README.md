Web framework for ClojureScript on Node. **WIP**.

<p align="center">
  <img src="docs/logo.svg?sanitize=true" alt="Sitefox logo"><br/>
</p>

In the tradition of Django, Flask, and Rails.
Designed for indie devs who ship fast.
Battle tested on real sites.

[Philosophy](#philosophy) | [Quick start](#quick-start) | [API](#api) | [Examples](https://github.com/chr15m/sitefox/tree/main/examples)

## Philosophy

 * Minimal.
 * [12 factor](https://12factor.net/).
 * 👇 Batteries included.

### Batteries included

 * [Routing](#web-server-routes)
 * [Sessions](#sessions)
 * [Authentication](#authentication)
 * [Templates](#templates)
 * [Database + Key-value store](#database)
 * [Email](#email)
 * [Forms](#forms)
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
    [promesa.core :as p]
    [sitefox.web :as web]
    [sitefox.db :refer [kv]]
    [sitefox.reloader :refer [reloader]]))

(defn home-page [req res]
  ; send a basic hello world response
  (.send res "Hello world!"))

(defn hello [req res]
  ; write a value to the key-value database
  (p/let [table (kv "sometable")
          r (.write table "key" 42)]
    (.json res true)))

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
  (p/let [[app _host _port] (web/start)]
    ; reload the routes when the server js is modified (recompiled)
    (reloader (partial #'setup-routes app))
    ; set up the routes for the first time
    (setup-routes app)))
```

More [Sitefox examples here](./examples).

## API

### Web server & routes

Sitefox uses the [express](https://expressjs.com) web server with sensible defaults for sessions and logging.
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

Also see these examples:

 * [shadow-cljs server example](https://github.com/chr15m/sitefox/tree/main/examples/shadow-cljs).
 * [nbb server example](https://github.com/chr15m/sitefox/tree/main/examples/nbb).

### Database

Sitefox makes it easy to start storing key-value data with no configuration.
You can transition to more structured data later if you need it.
It bundles [Keyv](https://github.com/lukechilds/keyv) which is a database backed key-value store.
You can access the key-value store through `db/kv` and the underlying database through `db/client`.

By default a local sqlite database is used and you can start persisting data on the server immediately without any configuration.
Once you move to production you can configure another database using the environment variable `DATABASE_URL`.
For example, to use a postgres database called "somedatabase":

```
DATABASE_URL=postgres://someuser:somepassword@somehost:5432/somedatabase
```

Or simply `DATABASE_URL=postgres:///somedatabase` if your user has local access on the deploy server.

To use the database and key-value interface first require the database module:

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
(-> (.get table "key")
  (.then (fn [val] (print val))))
```

You can use `db/client` to access the underlying database client.
For example to make a query against the configured database:

```clojure
(let [c (db/client)]
  (-> (.query db "select * from sometable WHERE x = 1")
    (.then (fn [rows] (print rows)))))
```

Again, [promesa](https://github.com/funcool/promesa) is recommended for managing control flow during database operations.

### Sessions

Sessions are enabled by default and each visitor to your server will have their own session.
The session data is persisted server side across page loads so you can use it to store authentication status for example.
Sessions are backed into a namespaced `kv` table (see the database section above).
You can read and write arbitrary JS data structures to the session using `req.session`.

To write a value to the session store (inside a route handler function):

```clojure
(let [session (aget req "session")]
  (aset session "myvalue" 42))
```

To read a value from the session store:

```clojure
(aget req "session" "myvalue")
```

### Authentication

TBD.

### Templates

Instead of templates, Sitefox offers shortcuts for server side Reagent rendering, merged wth HTML documents.

```clojure
[sitefox.html :refer [render-into]]
```

You can load an HTML document and render Reagent forms into a selected element:

```clojure
(def index-html (fs/readFileSync "index.html"))

(defn component-main []
  [:div
   [:h1 "Hello world!"]
   [:p "This is my content."]])

; this returns a new HTML string that can be returned
; e.g. with (.send res)
(render-into index-html "main" [component-main])
```

Sitefox uses [node-html-parser](https://www.npmjs.com/package/node-html-parser) and offers shortcuts for working with HTML & Reagent:

 * `html/parse` is shorthand for `node-html-parser/parse`.
 * `html/render` is shorthand for Reagent's `render-to-static-markup`.
 * `html/$` is shorthand for the parser's `querySelector`.
 * `html/$$` is shorthand for the parser's `querySelectorAll`.

Also see the [templates example](https://github.com/chr15m/sitefox/tree/main/examples/templates) project.

### Email

Sitefox bundles [nodemailer](https://nodemailer.com) for sending emails.
Configure your outgoing SMTP server:

```
SMTP_SERVER=smtps://username:password@mail.someserver.com/?pool=true
```

If you don't specify a server debug setup will be used.
No emails will be sent and the ethereal.email service will be used.
After running `send-email` you can print the `url` property of the result.
You can use the links for testing your emails in dev mode.

Also see the [send-email example](https://github.com/chr15m/sitefox/tree/main/examples/send-email) project.

### Forms

See the [form validation example](https://github.com/chr15m/sitefox/tree/main/examples/form-validation) which uses [node-input-validator](https://www.npmjs.com/package/node-input-validator) and checks for CSRF problems.

### Logging

By default the web server will write to log files in the `./logs` folder.
These files are automatically rotated by the server. There are two types of logs:

 * `logs/access.log` which are standard web access logs in "combined" format.
 * `logs/error.log` where `console.log` and `console.error` is written (and duplicated to stdout).

Note: the `error.log` is not written by default, you need to enable it by calling `(logging/bind-console-to-file)`.
This will rebind stdout to "tee" into the logfile `./logs/error.log` as well as printing to stdout.

## Who

Sitefox was made by [Chris McCormick](https://mccormick.cx) ([@mccrmx](https://twitter.com/mccrmx)).
I iterated on it while building sites for myself and for clients.
