(ns webserver
  (:require
    ["fs" :as fs]
    [promesa.core :as p]
    [nbb.core :refer [*file*]]
    [sitefox.web :as web]
    [sitefox.html :refer [render-into]]
    [sitefox.reloader :refer [nbb-reloader]]))

(def template (fs/readFileSync "index.html"))

(defn component-main []
  [:div
   [:h1 "Your Sitefox site"]
   [:p "Welcome to your new sitefox site.
       The code for this site is in " [:code "server.cljs"] "."]
   [:p "Check out "
    [:a {:href "https://github.com/chr15m/sitefox#batteries-included"} "the documentation"]
    " to start building."]])

(defn setup-routes [app]
  (web/reset-routes app)
  (web/static-folder app "/css" "node_modules/minimal-stylesheet/")
  (.get app "/"
        (fn [_req res]
          (->> (render-into template "main" [component-main])
               (.send res)))))

(defonce init
  (p/let [self *file*
          [app host port] (web/start)]
    (setup-routes app)
    (nbb-reloader self #(setup-routes app))
    (print "Serving at" (str host ":" port))))
