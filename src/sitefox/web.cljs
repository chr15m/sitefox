(ns sitefox.web
  (:require
    [sitefox.util :refer [env]]
    [sitefox.db :as db]
    [promesa.core :as p]
    [applied-science.js-interop :as j]
    ["path" :as path]
    ["process" :as process]
    ["express$default" :as express]
    ["cookie-parser$default" :as cookies]
    ["body-parser$default" :as body-parser]
    ["serve-static" :as serve-static]
    ["express-session$default" :as session]
    ["morgan$default" :as morgan]
    ["rotating-file-stream" :as rfs]))

(def dir (or js/__dirname "./"))

(defn create-store [kv]
  (let [e (session/Store.)]
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

(defn add-default-middleware [app]
  ; set up logging
  (let [logs (path/join dir "/logs")
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
  app)

(defn static-folder [app route folder]
  (.use app route (serve-static (path/join dir folder)))
  app)

(defn reset-routes [app]
  (let [router (aget app "_router")]
    (when router
      (js/console.error (str "Deleting " (aget router "stack" "length") " routes"))
      (aset app "_router" nil))
    (add-default-middleware app)))

(defn create []
  (-> (express)
      (add-default-middleware)))

(defn serve [app]
  (let [host (env "BIND_ADDRESS" "127.0.0.1")
        port (env "PORT" "8000")
        srv (.bind (aget app "listen") app port host)]
    (js/Promise.
      (fn [res err]
        (srv (fn []
               (js/console.log "Web server started: " (str "http://" host ":" port))
               (res #js [host port])))))))

(defn start []
  (let [app (create)]
    (->
      (serve app)
      (.then (fn [host port] [app host port])))))

(defn build-absolute-uri [req path]
  (let [hostname (aget req "hostname")
        host (aget req "headers" "host")]
    (str (aget req "protocol") "://"
         (if (not= hostname "localhost") hostname host)
         (if (not= (aget path 0) "/") "/")
         path)))

(defn strip-slash-redirect [req res n]
  (let [path (aget req "path")
        url (aget req "url")]
    (if (and
          (= (last path) "/")
          (> (aget path "length") 1))
      (.redirect res 301 (str (.slice path 0 -1) (.slice url (aget path "length"))))
      (n))))
