(ns sitefox.mail
  (:require
    [promesa.core :as p]
    [applied-science.js-interop :as j]
    [sitefox.util :refer [env]]
    ["nodemailer" :as nm]))

(defn smtp-transport
  "Create the SMTP mail transport to be used by `send-email`.
  
  The `SMTP_SERVER` environment variable specifies the connection settings.
  If unset a test account at ethereal.email will be used."
  []
  (let [smtp-url (env "SMTP_SERVER" nil)]
    (if smtp-url
      (js/Promise. (fn [res _err] (res (.createTransport nm smtp-url))))
      (-> (.createTestAccount nm)
          (.then (fn [account]
                   (.createTransport
                     nm
                     #js {:host "smtp.ethereal.email"
                          :port 587
                          :secure false
                          :auth #js {:user (aget account "user")
                                     :pass (aget account "pass")}})))))))

(defn send-email
  "Send an email.

  Uses the `SMTP_SERVER` environment variable to configure the server to use for sending.
  For example: SMTP_SERVER=smtps://username:password@mail.someserver.com/?pool=true

  If you don't specify a server ethereal.email will be used and the viewing link will be returned in the `url` property of the result.
  You can use this for testing your emails in dev mode.

  * `to` and `from` are valid email addresses.
  * `to` can be an array of valid email addresses for multiple recipients.
  * `subject` is the text of the subject line.
  * Use `:text` for the body of the email in text format.
  * Use `:html` for the body of the email in html format.
  * Use `:smtp-transport` keyword argument if you want to re-use the transport to send multiple emails.
  * Use `:headers` to pass a map of additional headers."
  [to from subject & {:keys [transport headers text html]}]
  (p/let [transport (or transport (smtp-transport))
          send-result (-> (j/call
                            transport
                            :sendMail
                            (clj->js (merge {:from from
                                             :to to
                                             :subject subject
                                             :text text
                                             :html html
                                             :headers headers})))
                          (.catch (fn [err] (js/console.error err) err)))
          result-url (.getTestMessageUrl nm send-result)]
    (when result-url
      (aset send-result "url" result-url))
    send-result))
