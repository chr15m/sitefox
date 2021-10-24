(ns sitefox.reloader
  (:require
    [promesa.core :as p]
    ["caller-id" :as caller-id]
    ["chokidar" :as file-watcher]
    [sitefox.deps :refer [cljs-loader]]))

(defn reloader
  "Runs `reload-function` every time the callee's file is changed.
  Useful for re-mounting express routes during development mode when the build updates."
  [reload-function & [source-file]]
  (let [caller (.getData caller-id)
        caller-path (aget caller "filePath")
        source-file (or source-file caller-path)]
    (->
      (.watch file-watcher source-file)
      (.on "change"
           (fn [path]
             (js/console.error (str "Reload triggered by " path))
             (js/setTimeout
               #(reload-function path)
               500))))))

(defn nbb-reloader
  "Runs an nbb-friendly verson of the reloader which asks nbb to `load-file` first."
  [reload-function parent-file]
  (reloader
    (fn [f]
      (p/do!
        (cljs-loader f)
        (reload-function)
        (print "Server reloaded.")))
    parent-file))
