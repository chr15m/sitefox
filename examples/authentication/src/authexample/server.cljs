(ns authexample.server
  (:require
    [promesa.core :as p]
    [sitefox.html :refer [render]]
    [sitefox.web :as web]
    [sitefox.reloader :refer [reloader]]
    [sitefox.logging :refer [bind-console-to-file]]))

(bind-console-to-file)

(defn home-page [req res]
  (.send res (render [:h1 "Hello world!"])))

(defn setup-routes [app]
  (web/reset-routes app)
  (.get app "/" home-page)
  #_ (web/static-folder app "/" (if (env "NGINX_SERVER_NAME") "build" "public")))

(defn main! []
  (p/let [[app host port] (web/start)]
    (reloader (partial #'setup-routes app))
    (setup-routes app)
    (println "Server main.")))

