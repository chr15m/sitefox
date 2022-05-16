(ns webserver
  (:require
    ["fs" :as fs]
    [promesa.core :as p]
    [nbb.core :refer [*file*]]
    ["browser-sync" :as browser-sync]
    ["fast-glob$default" :as fg]
    [sitefox.web :as web]
    [sitefox.util :refer [env]]
    [sitefox.html :refer [render-into]]
    [sitefox.reloader :refer [nbb-reloader]]
    [sitefox.tracebacks :refer [install-traceback-emailer]]))

(when-let [admin-email (env "ADMIN_EMAIL")]
  (install-traceback-emailer admin-email))

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
  (.get app "/"
        (fn [_req res]
          (->> (render-into template "main" [component-main])
               (.send res))))
  (web/static-folder app "/" "public"))

(defonce init
  (p/let [self *file*
          [app host port] (web/start)
          sync-options {:files ["public/**/**"]
                        :proxy (str host ":" port)}
          watch-files (fg #js [self "src/**/*.cljs"])
          bs (when (env "DEV") (browser-sync/init nil (clj->js sync-options)))]
    (setup-routes app)
    (nbb-reloader watch-files (fn []
                                (setup-routes app)
                                (when bs (.reload bs))))
    (print "Serving at" (str host ":" port))))
