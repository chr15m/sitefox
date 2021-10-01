(ns sitefox.util
  (:require
    ["path" :refer [basename]]
    ["util" :as util]
    ["caller-id" :as caller-id]
    ["chokidar" :as file-watcher]
    ["rotating-file-stream" :as rfs]))

(defn bind-console-log-to-file []
  (let [logs (str js/__dirname "/logs")
        error-log (.createStream rfs "error.log" (clj->js {:interval "7d" :path logs :teeToStdout true}))
        log-fn (fn [& args]
                 (let [date (.toISOString (js/Date.))
                       [d t] (.split date "T")
                       [t _] (.split t ".")
                       out (str d " " t " " (apply util/format (clj->js args)) "\n")]
                   (.write error-log out)))]
    (aset js/console "log" log-fn)
    (aset js/console "error" log-fn)
    (aset js/console "_logstream" error-log)
    log-fn))

(defn flush-bound-console [cb]
  (let [error-log (aget js/console "_logstream")]
    (if error-log
      (do
        (.on error-log "finish" cb)
        (aset js/console "log" (fn []))
        (aset js/console "error" (fn []))
        (.end error-log))
      (cb))))

(defn bail [& msgs]
  (apply js/console.error msgs)
  (js/console.error "Server exit.")
  (flush-bound-console #(js/process.exit 1)))

(defn now []
  (-> (js/Date.)
      (.toISOString)
      (.split ".")
      first
      (.replace "T" " ")))

(defn log [file-path & args]
  (apply print (conj (conj args (str (basename file-path) ":")) (now))))

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

(defn build-absolute-uri [req path]
  (let [hostname (aget req "hostname")
        host (aget req "headers" "host")]
    (str (aget req "protocol") "://"
         (if (not= hostname "localhost") hostname host)
         (if (not= (aget path 0) "/") "/")
         path)))

(defn strip-slash-redirect [req res n]
  (let [path (aget req "path")
        url (aget req "url")]
    (if (and
          (= (last path) "/")
          (> (aget path "length") 1))
      (.redirect res 301 (str (.slice path 0 -1) (.slice url (aget path "length"))))
      (n))))
