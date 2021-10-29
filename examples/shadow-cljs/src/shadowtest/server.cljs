(ns shadowtest.server
  (:require
    [promesa.core :as p]
    [sitefox.html :refer [render]]
    [sitefox.web :as web]
    [sitefox.logging :refer [bind-console-to-file]]))

(bind-console-to-file)

(defonce server (atom nil))

(defn home-page [req res]
  (.send res (render [:h1 "Hello world! yes"])))

(defn setup-routes [app]
  (web/reset-routes app)
  (.get app "/" home-page))

(defn main! []
  (p/let [[app host port] (web/start)]
    (reset! server app)
    (setup-routes app)
    (println "Server listening on" (str "http://" host ":" port))))

(defn ^:dev/after-load reload []
  (js/console.log "reload")
  (setup-routes @server))
