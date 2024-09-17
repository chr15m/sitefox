Web framework for ClojureScript on Node.

<p align="center">
  <img src="docs/logo.svg?sanitize=true" alt="Sitefox logo"><br/>
</p>

In the tradition of Django, Flask, and Rails.
Designed for indie devs who ship fast.
Battle tested on real sites.

[Philosophy](#philosophy) | [Quick start](#quick-start) | [Documentation](https://chr15m.github.io/sitefox/) | [API](#api) | [Examples](https://github.com/chr15m/sitefox/tree/main/examples) | [Community](#community)

```clojure
(ns webserver
  (:require
    [promesa.core :as p]
    [sitefox.html :refer [render]]
    [sitefox.web :as web]))

(defn root [_req res]
  (->> (render [:h1 "Hello world!"])
       (.send res)))

(p/let [[app host port] (web/start)]
  (.get app "/" root)
  (print "Serving on" (str "http://" host ":" port)))
```

## Philosophy

 * [12 factor](https://12factor.net/).
 * 👇 Batteries included.

### Batteries included

 * [Routing](#web-server--routes)
 * [Templates](#templates)
 * [Database + Key-value store](#database)
 * [Sessions](#sessions)
 * [Authentication](#authentication)
 * [Email](#email)
 * [Forms](#forms)
 * [Logging](#logging-and-errors)
 * [Live reloading](#live-reloading)

### Environment variables

 * `PORT` - configure the port Sitefox web server binds to.
 * `BIND_ADDRESS` - configure the IP address Sitefox web server binds to.
 * `SMTP_SERVER` - configure the outgoing SMTP server e.g. `SMTP_SERVER=smtps://username:password@mail.someserver.com/?pool=true`.
 * `DATABASE_URL` - configure the database to connect to. Defaults to `sqlite://./database.sqlite`.

## Quick start

The quickest way to start is using one of the `create` scripts which will set up an example project for you with one command.
If you're building a simple website without much front-end interactivity beyond form submission, the [nbb](https://github.com/babashka/nbb) create script is the way:

```
npm init sitefox-nbb mywebsite
```

This will create a folder called `mywebsite` containing your new project.
Note you can use [Scittle](https://github.com/borkdude/scittle) to run cljs client-side.

If you're building a full-stack ClojureScript application the [shadow-cljs](https://github.com/shadow-cljs) create script is the way:

```
npm init sitefox-shadow-fullstack myapp
```

That will create a folder called `myapp` containing your new project.

### Manually installing Sitefox

Add Sitefox to your project as a dependency:

```
{:deps
 {io.github.chr15m/sitefox {:git/tag "v0.0.22" :git/sha "6c4da4ee90833b5eccb583079a6848e7a86ace8e"}}}
```

If you're using `npm` you can install sitefox as a dependency that way.
If you do that you will need to add `node_modules/sitefox/src` to your classpath somehow.

```
npm i sitefox
```

**Note**: M1 Mac users may need to set the Python version in npm like this:

```
npm config set python python3
```

This is because the `node-sqlite3` build sometimes fails without the setting.
See [this issue](https://github.com/chr15m/sitefox/issues/20#issuecomment-1248719076) for more details.


### Example server

An example server with two routes, one of which writes values to the key-value database.

```clojure
(ns my.server
  (:require
    [promesa.core :as p]
    [sitefox.web :as web]
    [sitefox.db :refer [kv]]))

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
  (web/static-folder app "/" "public"))

(defn main! []
  ; create an express server and start serving
  ; BIND_ADDRESS & PORT env vars set host & port.
  (p/let [[app _host _port] (web/start)]
    ; set up the routes for the first time
    (setup-routes app)))
```

More [Sitefox examples here](https://github.com/chr15m/sitefox/tree/main/examples).

## Community

If you need support with Sitefox you can:

 * Join the [Clojure Slack #sitefox channel](https://app.slack.com/client/T03RZGPFR/C02LB2842UA).
 * You can also [ask a question on the GitHub discussions page](https://github.com/chr15m/sitefox/discussions).

## API

### Web server & routes

Sitefox uses the [express](https://expressjs.com) web server with sensible defaults for sessions and logging.
See the [express routing documentation](http://expressjs.com/en/guide/routing.html) for details.

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

### Database

Sitefox makes it easy to start storing key-value data with no configuration.
You can transition to more structured data later if you need it.
It bundles [Keyv](https://github.com/lukechilds/keyv) which is a database backed key-value store.
You can access the key-value store through `db/kv` and the underlying database through `db/client`.

See the full [documentation for the db module](https://chr15m.github.io/sitefox/sitefox.db.html).

By default a local sqlite database is used and you can start persisting data on the server immediately without any configuration.
Once you move to production you can configure another database using the environment variable `DATABASE_URL`.
For example, to use a postgres database called "DBNAME" you can access it as follows (depending on your network/local setup):

```
DATABASE_URL="postgres://%2Fvar%2Frun%2Fpostgresql/DBNAME"
DATABASE_URL=postgres://someuser:somepassword@somehost:5432/DBNAME
DATABASE_URL=postgres:///somedatabase
```

Note that you will also need to `npm install @keyv/postgres` if you want to use the Postgres backend.

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
  (-> (.query c "select * from sometable WHERE x = 1")
    (.then (fn [rows] (print rows)))))
```

Again, [promesa](https://github.com/funcool/promesa) is recommended for managing control flow during database operations.

To explore key-value data from the command line use sqlite and jq to filter data like this:

```
sqlite3 database.sqlite "select * from keyv where key like 'SOMEPREFIX%';" | cut -f 2 -d "|" | jq '.'
```

#### Sqlite3 full stack traces

By default the `node-sqlite3` module does not provide full stack traces with line numbers etc. when a database error occurs.
It's possible to [turn on verbose stack traces](https://github.com/TryGhost/node-sqlite3/wiki/Debugging) with a small performance penalty as follows:

```clojure
(ns yourapp
  (:require
    ["sqlite3" :as sqlite3]))

(.verbose sqlite3)
```

#### Enabling Sqlite3 WAL mode

If you want to run sqlite3 in production you may run into the error `SQLITE_BUSY: database is locked` when performing simultaneous database operations from different clients.
It is possible to resolve these concurrency and locking issues by enabling [write-ahead logging mode in sqlite3](https://www.sqlite.org/wal.html) as follows:

```
(ns yourapp
  (:require
    [sitefox.db :refer [client]]))

(p/let [c (client)
        wal-mode-enabled (.query c "PRAGMA journal_mode=WAL;")]
  (js/console.log wal-mode-enabled))
```

This code can safely be placed in the main function of your server code.

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

Sitefox wraps the Passport library to implement authentication.
You can add simple email and password based authentication to your app with three function calls:

```clojure
(defn setup-routes [app]
  (let [template (fs/readFileSync "index.html")]
    (web/reset-routes app)
    ; three calls to set up email based authentication
    (auth/setup-auth app)
    (auth/setup-email-based-auth app template "main")
    (auth/setup-reset-password app template "main")
    ; ... add your additional routes here ... ;
    ))
```

The `template` string passed in is an HTML document and `"main"` is the selector specifying where to mount the auth UI.
This will set up the following routes by default where you can send users to sign up, sign in, and reset their password:

 * `/auth/sign-in`
 * `/auth/sign-up`
 * `/auth/reset-password`

It is also possible to override the default auth UI Reagent forms and the redirect URLs to customise them with your own versions.
See the [auth documentation](https://chr15m.github.io/sitefox/sitefox.auth.html#var-setup-auth) for detail about how to supply your own Reagent forms.
Also see the [source code for the default Reagent auth forms](https://github.com/chr15m/sitefox/blob/main/src/sitefox/auth.cljs#L401) if you want to make your own.

When a user signs up their data is persisted into the default Keyv database used by Sitefox.
You can retrieve the currently authenticated user's datastructure on the request object:

```clojure
(let [user (aget req "user")] ...)
```

You can then update the user's data and save their data back to the database.
The `applied-science.js-interop` library is convenient for this (required here as `j`):

```clojure
(p/let [user (aget req "user")]
  (j/assoc! user :somekey 42)
  (auth/save-user user))
```

If you want to create a new table it is useful to key it on the user's uuid which you can obtain with `(aget user "id")`.

See the [authentication example](https://github.com/chr15m/sitefox/tree/main/examples/authentication) for more detail.

To add a new authentication scheme such as username based, or 3rd party oauth, consult the [Passport docs](https://www.passportjs.org/) and [auth.cljs](https://github.com/chr15m/sitefox/blob/main/src/sitefox/auth.cljs#L210). Pull requests most welcome!

### Email

Sitefox bundles [nodemailer](https://nodemailer.com) for sending emails.
Configure your outgoing SMTP server:

```
SMTP_SERVER=smtps://username:password@mail.someserver.com/?pool=true
```

Then you can use the `send-email` function as follows:

```clojure
(-> (mail/send-email
      "test-to@example.com"
      "test@example.com"
      "This is my test email."
      :text "Hello, This is my first email from **Sitefox**. Thank you.")
    (.then js/console.log))
```

By default sent emails are logged to `./logs/mail.log` in json-lines format.

If you don't specify an SMTP server, the email module will be in debug mode.
No emails will be sent, outgoing emails will be written to `/tmp` for inspection,
and `send-email` outcomes will also be logged to the console.

If you set `SMTP_SERVER=ethereal` the ethereal.email service will be used.
After running `send-email` you can print the `url` property of the result.
You can use the links that are printed for testing your emails in dev mode.

Also see the [send-email example](https://github.com/chr15m/sitefox/tree/main/examples/send-email) project.

### Forms

See the [form validation example](https://github.com/chr15m/sitefox/tree/main/examples/form-validation) which uses [node-input-validator](https://www.npmjs.com/package/node-input-validator) and checks for CSRF problems.

#### CSRF protection

To ensure you can `POST` without CSRF warnings you should create a hidden element like this (Reagent syntax):

```clojure
[:input {:name "_csrf" :type "hidden" :default-value (.csrfToken req)}]
```

If you're making an ajax `POST` request from the client side, you should pass the CSRF token as a header.
A valid token is available as a string at the JSON endpoint `/_csrf-token` and you can fetch it using `fetch-csrf-token`
and add it to the headers of a fetch request as follows:

```clojure
(ns n (:require [sitefox.ui :refer [fetch-csrf-token]]))

(-> (fetch-csrf-token)
    (.then (fn [token]
             (js/fetch "/api/endpoint"
                       #js {:method "POST"
                            :headers #js {:Content-Type "application/json"
                                          :X-XSRF-TOKEN token} ; <- use token here
                            :body (js/JSON.stringify (clj->js some-data))}))))
```

**Note**: you can fetch the CSRF token from a client side cookie instead if you set the environment variable `SEND-CSRF-TOKEN`.
This was the default in previous Sitefox versions.
When set, Sitefox will send the token on every GET request in the client side cookie
`XSRF-TOKEN` and this can be retrieved with the `ui/csrf-token` function.
This is a valid, but less secure form of CSRF protection.

In some rare circumstances you may wish to turn off CSRF checks (for example posting to an API from a non-browser device).
If you know what you are doing you can use the `pre-csrf-router` to add routes which bypass the CSRF checking:

```clojure
(defn setup-routes [app]
  ; flush all routes from express
  (web/reset-routes app)
  ; set up an API route bypassing CSRF checks
  (.post (j/get app "pre-csrf-router") "/api/endpoint" endpoint-unprotected-by-csrf)
  ; set up an express route for "/hello" which is protected as normal
  (.post app "/hello" hello))
```

### Logging and errors

By default the web server will write to log files in the `./logs` folder.
These files are automatically rotated by the server. There are two types of logs:

 * `logs/access.log` which are standard web access logs in "combined" format.
 * `logs/error.log` where tracebacks are written using `tracebacks/install-traceback-handler`.

To send uncaught exceptions to the error log:

```
(def admin-email (env-required "ADMIN_EMAIL"))
(def build-id (try (fs/readFileSync "build-id.txt") (catch :default _e "dev")))

(install-traceback-handler admin-email build-id)
```

Create `build-id.txt` based on the current git commit as follows:

```
git rev-parse HEAD | cut -b -8 > build-id.txt
```

If you want to get correct ClojureScript line numbers in tracebacks require `["source-maps-support" :as sourcemaps]` and then:

```
(.install sourcemaps)
```

#### 404 and 500 errors

You can use the [`web/setup-error-handler`](https://chr15m.github.io/sitefox/sitefox.db.html#var-setup-error-handler)
function to serve a page for those errors based on a Reagent component you define:

```clojure
(defn component-error-page [_req error-code error]
  [:section.error
   [:h2 error-code " Error"]
   (case error-code
     404 [:p "We couldn't find the page you're looking for."]
     500 [:<> [:p "An error occurred:"] [:p (.toString error)]]
     [:div "An unknown error occurred."])])

(web/setup-error-handler app my-html-template "main" component-error-page)
```

You can combine these to catch both 500 Internal Server errors and uncaught exceptions as follow:

```
(let [traceback-handler (install-traceback-handler admin-email build-id)]
    (web/setup-error-handler app template-app "main" component-error-page traceback-handler))
```

### Live reloading

Live reloading is supported using both `nbb` and `shadow-cljs`.
It is enabled by default when using the npm create scripts.
Examples have more details.

## Who

Sitefox was made by [Chris McCormick](https://mccormick.cx)
([@mccrmx on Twitter](https://twitter.com/mccrmx) and [@chris@mccormick.cx on Mastodon](https://mccormick.cx/@chris)).
I iterated on it while building sites for myself and for clients.
