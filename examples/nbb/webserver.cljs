(ns webserver
  (:require
    [promesa.core :as p]
    [reagent.dom.server :refer [render-to-static-markup] :rename {render-to-static-markup r}]
    [nbb.core :refer [*file*]]
    [sitefox.reloader :refer [nbb-reloader]]
    [sitefox.web :as web]))

(defn root-view [_req res]
  (.send res (r [:h1 "Hello world!"])))

(defn setup-routes [app]
  (web/reset-routes app)
  (.get app "/" root-view))

(defonce init
  (p/let [self *file*
          [app host port] (web/start)]
    (setup-routes app)
    (nbb-reloader self #(setup-routes app))
    (println "Serving on" (str "http://" host ":" port))))
