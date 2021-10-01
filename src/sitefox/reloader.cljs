(ns sitefox.reloader
  (:require
    ["caller-id" :as caller-id]
    ["chokidar" :as file-watcher]))

(defn reloader [reload-function]
  (let [caller (.getData caller-id)
        caller-path (aget caller "filePath")]
    (->
      (.watch file-watcher caller-path)
      (.on "change"
           (fn [path]
             (js/console.error (str "Reload triggered by " path))
             (js/setTimeout
               reload-function
               500))))))
