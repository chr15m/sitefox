(ns shadowtest.server
  (:require
    [promesa.core :as p]
    [reagent.dom.server :refer [render-to-static-markup] :rename {render-to-static-markup r}]
    [sitefox.web :as web]
    [sitefox.reloader :refer [reloader]]
    [sitefox.logging :refer [bind-console-log-to-file]]))

(bind-console-log-to-file)

(defn home-page [req res]
  (.send res (r [:h1 "Hello world!"])))

(defn setup-routes [app]
  (web/reset-routes app)
  (.get app "/" home-page)
  #_ (web/static-folder app "/" (if (env "NGINX_SERVER_NAME") "build" "public")))

(defn main! []
  (p/let [[app host port] (web/start)]
    (reloader (partial #'setup-routes app))
    (setup-routes app)
    (println "Server main.")))

