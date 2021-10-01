(ns sitefox.util
  (:require
    ["caller-id" :as caller-id]
    ["chokidar" :as file-watcher]))

(defn env [k & [default]]
  (or (aget js/process.env k) default))

(defn env-required [k]
  (or (env k) (bail "Required environment variable is missing:" k)))

(defn error-to-json [err]
  (let [e (js/JSON.parse (js/JSON.stringify err))]
    (aset e "message" (str err))
    #js {:error e}))

(defn btoa [s]
  (-> s js/Buffer. (.toString "base64")))

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

