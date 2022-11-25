(ns sitefox.tracebacks
  "Server side error handling. Get tracebacks from live sites emailed to you."
  (:require
    [promesa.core :as p]
    [sitefox.util :refer [env]]
    [sitefox.logging :refer [bail]]
    [sitefox.mail :refer [send-email]]
    [sitefox.web :refer [build-absolute-uri]]))

(defn handle-traceback [email-address error]
  (js/console.error "handle-traceback")
  (p/let [result (when (= (env "NODE_ENV") "production")
                   (send-email email-address email-address "Sitefox traceback" :text (or (aget error "stack") (str error))))]
    (when result
      (js/console.error "Emailed traceback:" result))
    (bail error)))

(defn install-traceback-emailer
  "Send any unhandledRejections or uncaughtExceptions to
  the email address specified and exit."
  [email-address]
  (when (nil? (aget js/process "sitefox-handler-installed"))
    (aset js/process "sitefox-handler-installed" true)
    (.on js/process "unhandledRejection" #(handle-traceback email-address %))
    (.on js/process "uncaughtException" #(handle-traceback email-address %))))

(defn make-500-error-emailer
  "Creates an error handler to email 500 errors. To be used in `web/setup-error-handler`.

  * `email-address` is where you want the errors to be sent e.g. `(env \"ADMIN_EMAIL\")`.
  * `build-id` is an optional build-id to pass in such as a `git` reference.

  You can get a `build-id` using `git rev-parse HEAD | cut -b -8 > build-id.txt` and including it with `(rc/inline)`."
  [email-address & [build-id]]
  (fn [req error]
    (when email-address
      (js/console.error error)
      (send-email email-address email-address
                  (str "Sitefox traceback on " (build-absolute-uri req "/"))
                  :text (str
                          "A traceback occurred at " (build-absolute-uri req (aget req "path")) "\n"
                          (when build-id (str "Build: " build-id "\n"))
                          "\n"
                          (js->clj (or (aget error "stack") error))
                          "\n")))))
