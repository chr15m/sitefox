(ns sitefox.logging
  "Functions to help with writing log files."
  (:require
    ["path" :refer [basename]]
    ["util" :as util]
    [applied-science.js-interop :as j]
    ["rotating-file-stream" :as rfs]))

(defn flush-bound-console
  "This is deprecated in favour of `tracebacks/install-traceback-handler`."
  [cb]
  ; https://github.com/winstonjs/winston/issues/228
  (let [error-log (aget js/console "_logstream")]
    (if error-log
      (do
        (j/call error-log :on "finish" cb)
        (aset js/console "log" (fn []))
        (aset js/console "error" (fn []))
        (.end error-log))
      (cb))))

(defn bind-console-to-file
  "This is deprecated in favour of `tracebacks/install-traceback-handler`.
  Rebinds `console.log` and `console.error` so that they write to `./logs/error.log` as well as stdout."
  []
  (when (not (aget js/console "_logstream"))
    (let [logs (str (or js/__dirname ".") "/logs")
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
      ; make sure the final errors get caught
      (j/call js/process :on "unhandledRejection"
              (fn [reason p]
                (js/console.error reason "Unhandled Rejection at Promise" p)
                (flush-bound-console #(js/process.exit 1))))
      (j/call js/process :on "uncaughtException"
              (fn [err]
                (js/console.error err)
                (flush-bound-console #(js/process.exit 1))))
      log-fn)))

(defn bail
  "Print a message and then kill the current process."
  [& msgs]
  (apply js/console.error msgs)
  (js/console.error "Server exit.")
  (flush-bound-console #(js/process.exit 1)))

(defn now []
  (-> (js/Date.)
      (.toISOString)
      (.split ".")
      first
      (.replace "T" " ")))

(defn log
  "Console log with built-in file and time reference."
  [file-path & args]
  (apply print (conj (conj args (str (basename file-path) ":")) (now))))

