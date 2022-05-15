(ns sitefox.reloader
  "Functions to ensuring live-reloading works during development mode."
  (:require
    [promesa.core :as p]
    [applied-science.js-interop :as j]
    [sitefox.util :refer [env]]
    [sitefox.deps :refer [cljs-loader]]
    [sitefox.ui :refer [log]]))

(defn nbb-reloader
  "Sets up filewatcher for development. Automatically re-evaluates this
  file on changes. Uses browser-sync to push changes to the browser.
  `file` can be a path (string) or a vec of strings."
  [file callback]
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
                        (log "File changed:" file)
                        (if-not @is-loading
                          (-> (p/do!
                                (println "Reloading!")
                                (reset! is-loading true)
                                (cljs-loader file)
                                (callback)
                                (println "Done reloading!"))
                              (.catch (fn [err]
                                        (.log js/console err)
                                        (reset! is-loading false)))
                              (.finally (fn []
                                          (reset! is-loading false))))
                          (do (println "Load already in progress, retrying in 500ms")
                              (js/setTimeout #(on-change file) 500))))]
      (if (= (type file) js/String)
        (.add watcher file)
        (doseq [f file]
          (.add watcher f)))
      (j/call watcher :on "change" (fn [file _stat] (on-change file)))
      (j/call watcher :on "fallback"
              (fn [limit]
                (print "Reloader hit file-watcher limit: " limit))))))

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
