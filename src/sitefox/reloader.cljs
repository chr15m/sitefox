(ns sitefox.reloader
  (:require
    [promesa.core :as p]
    ["filewatcher" :as watcher]
    [sitefox.util :refer [env]]
    [sitefox.deps :refer [cljs-loader]]))

(defn nbb-reloader
  "Sets up filewatcher for development. Automatically re-evaluates this
  file on changes."
  [current-file callback]
  (when (env "DEV")
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
