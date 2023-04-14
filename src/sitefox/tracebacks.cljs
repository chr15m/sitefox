(ns sitefox.tracebacks
  "Server side error handling. Get tracebacks from live sites emailed to you."
  (:require
    ["util" :as util]
    ["rotating-file-stream" :as rfs]
    [applied-science.js-interop :as j]
    [promesa.core :as p]
    [sitefox.mail :refer [send-email]]
    [sitefox.web :refer [build-absolute-uri]]))

(defn ^:no-doc write-error-to-logfile [logfile & args]
  (let [date (.toISOString (js/Date.))
        [d t] (.split date "T")
        [t _] (.split t ".")
        out (str d " " t " " (apply util/format (clj->js args)) "\n")]
    (.write logfile out)))

(defn ^:no-doc flush-error-log [log]
  ; https://github.com/winstonjs/winston/issues/228
  (js/Promise.
    (fn [res _err]
      (if log
        (do
          (j/call log :on "finish" res)
          (.end log))
        (res)))))

(defn ^:no-doc handle-traceback [email-address log build-id error req]
  (p/let [error-message (str
                          (if req
                            (str "Sitefox traceback at " (build-absolute-uri req (aget req "path")) "\n")
                            (str "Sitefox traceback at unknown URL \n"))
                          (when build-id (str "Build: " build-id "\n"))
                          "\n"
                          (js->clj (or (aget error "stack") error))
                          "\n")]
    (js/console.error error-message)
    (js/console.log error-message)
    (when log
      (write-error-to-logfile log error-message))
    (when email-address
      (send-email email-address email-address
                  (if req
                    (str "Sitefox traceback at " (build-absolute-uri req "/"))
                    (str "Sitefox traceback"))
                  :text error-message))))

(defn install-traceback-handler
  "Handle any unhandledRejections or uncaughtExceptions that occur outside request handlers.
   * If `email-address` is set, errors will be sent to the supplied address.
   * If `build-id` is set, it will be added to the log.

  Errors are also written to the rotated file `logs/error.log` and stderr.

  You can get a `build-id` using `git rev-parse HEAD | cut -b -8 > build-id.txt` and including it with `(rc/inline)`."
  [email-address & [build-id]]
  (let [sitefox-traceback-singleton (aget js/process "sitefox-traceback-handler")]
    (if (nil? sitefox-traceback-singleton)
      (let [log (.createStream rfs "error.log" (clj->js {:interval "7d" :path (str js/__dirname "/logs") :teeToStdout true}))
            error-handler-fn (partial handle-traceback email-address log build-id)
            error-fn (fn [error]
                       (p/do!
                         (error-handler-fn error nil)
                         (flush-error-log log)
                         (js/process.exit 1)))]
        (aset js/process "sitefox-traceback-handler" error-handler-fn)
        (.on js/process "unhandledRejection" error-fn)
        (.on js/process "uncaughtException" error-fn)
        error-handler-fn)
      sitefox-traceback-singleton)))
