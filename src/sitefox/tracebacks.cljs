(ns sitefox.tracebacks
  (:require
    [promesa.core :as p]
    [sitefox.util :refer [env]]
    [sitefox.logging :refer [bail]]
    [sitefox.mail :refer [send-email]]))

(defn handle-traceback [email-address error]
  (js/console.error "handle-traceback")
  (p/let [result (when (= (env "NODE_ENV") "production")
                   (send-email email-address email-address "Sitefox traceback" :text (str error)))]
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
