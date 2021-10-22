(ns webserver
  (:require
    ["fs" :as fs]
    [promesa.core :as p]
    [sitefox.web :as web]
    [sitefox.html :refer [render render-into]]
    [nbb.core :refer [slurp *file* load-file]]))

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

(def current-file *file*)

(defn dev
  "Sets up filewatcher for development. Automatically re-evaluates this
  file on changes."
  [app]
  (p/let [watcher
          (-> (js/import "filewatcher")
              (.catch (fn [err]
                        (println "Error while loading filewatcher.")
                        (println "Try: npm install filewatcher --save-dev")
                        (.log js/console err)
                        (js/process.exit 1))))
          watcher (.-default watcher)
          watcher (watcher)
          is-loading (atom false)
          on-change (fn on-change [file]
                      (if-not @is-loading
                        (-> (p/do!
                             (println "Reloading!")
                             (reset! is-loading true)
                             (load-file file)
                             (setup-routes app)
                             (println "Done reloading!")
                             (reset! is-loading false))
                            (.catch (fn [err]
                                      (.log js/console err))))
                        (do (println "Load already in progress, retrying in 500ms")
                            (js/setTimeout #(on-change file) 500))))]
    (.add watcher current-file)
    (.on watcher "change" (fn [file _stat]
                            (on-change file)))))

(defonce init
  (p/let [dev? (= "true" js/process.env.DEV)
          app (web/create)
          _ (when dev?
              (dev app))
          [host port] (web/serve app)]
    (setup-routes app)
    (print "Serving at" (str host ":" port))
    (when-not dev?
      (println "Start with the DEV=true env variable to enable filewatcher."))))
