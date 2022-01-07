(ns sitefox.reloader
  (:require
    [promesa.core :as p]
    [sitefox.util :refer [env]]
    [sitefox.deps :refer [cljs-loader]]))

(defn nbb-reloader
  "Sets up filewatcher for development. Automatically re-evaluates this
  file on changes. Uses browser-sync to push changes to the browser."
  [current-file callback]
  (when (or (env "DEV") (= (env "NODE_ENV") "development"))
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
                                (cljs-loader file)
                                (callback)
                                (println "Done reloading!"))
                              (.catch (fn [err]
                                        (.log js/console err)))
                              (.finally (fn []
                                          (reset! is-loading false))))
                          (do (println "Load already in progress, retrying in 500ms")
                              (js/setTimeout #(on-change file) 500))))]
      (.add watcher current-file)
      (.on watcher "change" (fn [file _stat]
                              (on-change file))))))

(defn sync-browser
  "Sets up browser-sync for development. Hot-loads CSS and automatically
  refreshes on server code change."
  [host port & [files]]
  (when (or (env "DEV") (= (env "NODE_ENV") "development"))
    (p/let [bs (-> (js/import "browser-sync")
                   (.catch (fn [err]
                             (println "Error while loading browser-sync.")
                             (println "Try: npm install browser-sync --save-dev")
                             (.log js/console err)
                             (js/process.exit 1))))
            init (aget bs "init")]
      (init nil (clj->js {:files (or files ["public"])
                          :proxy (str host ":" port)})))))
