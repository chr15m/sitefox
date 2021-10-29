(ns webserver
  (:require
    ["fs" :as fs]
    [promesa.core :as p]
    [sitefox.web :as web]
    [sitefox.html :refer [render-into]]
    [sitefox.reloader :refer [nbb-reloader]]))

(def t (fs/readFileSync "index.html"))

(defn component-main []
  [:div
   [:h1 "Hello world!"]
   [:p "This is my content."]])

(defn setup-routes [app]
  (web/reset-routes app)
  (web/static-folder app "/css" "node_modules/minimal-stylesheet/")
  (.get app "/"
        (fn [_req res]
          (->> (render-into t "main" [component-main])
               (.send res)))))

(defonce init
  (p/let [self *file*
          [app host port] (web/start)]
    (setup-routes app)
    (nbb-reloader self #(setup-routes app))
    (println "Serving on" (str "https://" host ":" port))))
