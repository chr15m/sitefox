(ns webserver
  (:require
    [promesa.core :as p]
    [sitefox.web :as web]))

(defn setup-routes [app]
  (web/reset-routes app)
  (.get app "/" (fn [req res] (.send res "Hello world!"))))

(p/let [app (web/create)
        [host port] (web/serve app)]
  (setup-routes app)
  (println "Serving."))
