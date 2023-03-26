(ns sitefox.web
  "Functions to start the webserver and create routes."
  (:require
    [sitefox.util :refer [env]]
    [sitefox.db :as db]
    [promesa.core :as p]
    [applied-science.js-interop :as j]
    ["http" :as http]
    ["path" :as path]
    ["rotating-file-stream" :as rfs]
    ["express-session" :refer [Store]]
    [sitefox.deps :refer [express cookies body-parser serve-static session morgan csrf]]
    [sitefox.html :refer [direct-to-template]]))

(def ^:no-doc server-dir (or js/__dirname "./"))

(defn ^:no-doc create-store [kv]
  (let [e (Store.)]
    (aset e "destroy" (fn [sid callback]
                        (p/let [result (j/call kv :delete sid)]
                          (when callback (callback))
                          result)))
    (aset e "get" (fn [sid callback]
                    (p/let [result (j/call kv :get sid)]
                      (callback nil result)
                      result)))
    (aset e "set" (fn [sid session callback]
                    (p/let [result (j/call kv :set sid session)]
                      (when callback (callback))
                      result)))
    (aset e "touch" (fn [sid session callback]
                      (p/let [result (j/call kv :set sid session)]
                        (when callback (callback))
                        result)))
    (aset e "clear" (fn [callback]
                      (p/let [result (js/call kv :clear)]
                        (when callback (callback))
                        result)))
    e))

(defn is-post?
  "Check whether an express request uses the POST method."
  [req]
  (= (aget req "method") "POST"))

(defn add-default-middleware
  "Set up default express middleware for:

  * Writing rotating logs to `logs/access.log`.
  * Setting up sessions in the configured database.
  * Parse cookies and body."
  [app]
  (let [logs (path/join server-dir "/logs")
        access-log (.createStream rfs "access.log" #js {:interval "7d" :path logs})
        kv-session (db/kv "session")
        store (create-store kv-session)]
    ; set up sessions table
    (.use app (session #js {:secret (env "SECRET" "DEVMODE")
                            :saveUninitialized false
                            :resave true
                            :cookie #js {:secure "auto"
                                         :httpOnly true
                                         ; 10 years
                                         :maxAge (* 10 365 24 60 60 1000)}
                            :store store}))
    ; set up logging
    (.use app (morgan "combined" #js {:stream access-log})))
  ; configure sane server defaults
  (.set app "trust proxy" "loopback")
  (.use app (cookies (env "SECRET" "DEVMODE")))
  ; json body parser
  (.use app (.json body-parser #js {:limit "10mb" :extended true :parameterLimit 1000}))
  (.use app (.urlencoded body-parser #js {:extended true}))
  (.use app (csrf #js {:cookie #js {:httpOnly true :sameSite "Strict" :secure true}}))
  (.use app (fn [req res done] (j/call res :cookie "XSRF-TOKEN" (j/call req :csrfToken) #js {:secure true :sameSite "Strict"}) (done)))
  ; emit a warning if SECRET is not set
  (when (nil? (env "SECRET")) (js/console.error "Warning: env var SECRET is not set."))
  app)

(defn static-folder
  "Express middleware to statically serve a `dir` on a `route` relative to working dir."
  [app route dir]
  (.use app route (serve-static (path/join server-dir dir)))
  app)

(defn reset-routes
  "Remove all routes in the current app and re-add the default middleware.
  Useful for hot-reloading code."
  [app]
  (let [router (aget app "_router")]
    (when router
      (js/console.error (str "Deleting " (aget router "stack" "length") " routes"))
      (aset app "_router" nil))
    (add-default-middleware app)))

(defn create
  "Create a new express app and add the default middleware."
  []
  (-> (express)
      (add-default-middleware)))

(defn serve
  "Start serving an express app.

  Configure `BIND_ADDRESS` and `PORT` with environment variables.
  They default to `127.0.0.1:8000`."
  [app]
  (let [host (env "BIND_ADDRESS" "127.0.0.1")
        port (env "PORT" "8000")
        server (.createServer http app)]
    (->
      (js/Promise.
        (fn [res _err]
          (.listen server port host
                   #(res [host port server])))))))

(defn start
  "Create a new express app and start serving it.
  Runs (create) and then (serve) on the result.
  Returns a promise which resolves with [app host port server] once the server is running."
  []
  (let [app (create)]
    (->
      (serve app)
      (.then (fn [[host port server]] [app host port server])))))

(defn build-absolute-uri
  "Creates an absolute URL including host and port.
  Use inside a route: `(build-absolute-uri req \"/somewhere\")`"
  [req path]
  (let [hostname (aget req "hostname")
        host (aget req "headers" "host")]
    (str (aget req "protocol") "://"
         (if (not= hostname "localhost") hostname host)
         (when (and path (not= (aget path 0) "/")) "/")
         path)))

(defn strip-slash-redirect
  "Express middleware to strip slashes from the end of any URL by redirecting to the non-slash version.
  Use: `(.use app strip-slash-redirect)"
  [req res n]
  (let [path (aget req "path")
        url (aget req "url")]
    (if (and
          path
          (= (last path) "/")
          (> (aget path "length") 1))
      (.redirect res 301 (str (.slice path 0 -1) (.slice url (aget path "length"))))
      (n))))

(defn name-route
  "Attach a name to a route that can be recalled with `get-named-route`."
  [app route route-name]
  (j/assoc-in! app [:named-routes route-name] route)
  route)

(defn get-named-route
  "Retrieve a route that has previously been named."
  [req route-name]
  (j/get-in req [:app :named-routes route-name]))

(defn setup-error-handler
  "Sets up an express route to handle 404 and 500 errors.
  This must be the last handler in your server:

  ```clojure
  (defn setup-routes [app]
    (web/reset-routes app)
    ; ... other routes
    ; 404 and 500 error handling
    (web/setup-error-handler app template \"main\" component-error-page email-error-callback))
  ```

  Pass it the express `app`, an HTML string `template`, query `selector` and a Reagent `view-component`.
  Optionally pass `error-handler-fn` which will be called on every 500 error with args `req`, `error`.
  The error `view-component` will be rendered and inserted into the template at `selector`.

  The error component will receive three arguments:
  * `req` - the express request object.
  * `error-code` - the exact error code that occurred (e.g. 404, 500 etc.).
  * `error` - the error object that was propagated (if any).

  Example:

  `(make-error-handler app my-template \"main\" my-error-component)`

  To have all 500 errors emailed to you and logged use `tracebacks/install-traceback-handler` and pass it as error-handler-fn."
  [app template selector view-component & [error-handler-fn]]
  ; handle 404 errors
  (j/call app :use
          (fn [req res]
            (-> res
                (.status 404)
                (direct-to-template template selector [view-component req 404]))))
  ; handle 500 errors
  (j/call app :use
          (fn [error req res done]
            (p/catch
              (p/let [_error-handler-result (when error-handler-fn
                                              (error-handler-fn req error))]
                (if (aget res "headersSent")
                  (done error)
                  (-> res
                      (.status 500)
                      ;(.json res (clj->js (js->clj error)))
                      ;(.send (str "BADNESS " (aget req "path")))
                      (direct-to-template template selector [view-component req 500 error]))))
              (fn [error]
                (-> res
                    (.status 500)
                    (.type "text/plain")
                    (.send (str "Error in error handler:\n\n"
                                (.toString (or (aget error "stack") error))))))))))
