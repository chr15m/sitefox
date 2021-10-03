(ns webserver
  (:require
    [promesa.core :as p]
    [sitefox.web :as web]
    [reagent.dom.server :refer [render-to-static-markup] :rename {render-to-static-markup r}]))

(defn setup-routes [app]
  (web/reset-routes app)
  (.get app "/" (fn [req res] (.send res (r [:h1 "Hello world!"])))))

(p/let [app (web/create)
        [host port] (web/serve app)]
  (setup-routes app)
  (println "Serving."))
