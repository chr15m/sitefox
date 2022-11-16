(ns sitefox.mail
  "Functions for sending email from web services using node-mailer."
  (:require
    [promesa.core :as p]
    [applied-science.js-interop :as j]
    [sitefox.util :refer [env]]
    ["fs" :as fs]
    ["nodemailer" :as nm]
    ["rotating-file-stream" :as rfs]
    ["path" :as path]
    ["tmp" :as tmp]))

(def ^:no-doc server-dir (or js/__dirname "./"))

(def console-transport
  (j/obj :sendMail
         (fn [mail]
           (j/let [text (j/get mail :text)
                   html (j/get mail :html)
                   text-file (.fileSync tmp #js {:postfix ".txt"})
                   html-file (.fileSync tmp #js {:postfix ".html"})
                   text-file-fd (j/get text-file :fd)
                   html-file-fd (j/get html-file :fd)]
             (when text
               (.writeFileSync fs text-file-fd text)
               (j/assoc! mail :text (j/get text-file :name)))
             (.close fs text-file-fd)
             (when html
               (.writeFileSync fs html-file-fd html)
               (j/assoc! mail :html (j/get html-file :name)))
             (.close fs html-file-fd)
             (js/console.error "smtp-console-transport:" mail)
             (p/do! nil)))))

(defn smtp-transport
  "Create the SMTP mail transport to be used by `send-email`.

  The `SMTP_SERVER` environment variable specifies the connection settings.
  If unset a test account at ethereal.email will be used."
  []
  (let [smtp-url (env "SMTP_SERVER" nil)]
    (cond
      ; default to logging emails to console
      (nil? smtp-url) (js/Promise. (fn [res _err] (res console-transport)))
      ; if the user has specified to use ethereal mail
      (= (.toLowerCase smtp-url) "ethereal")
      (-> (.createTestAccount nm)
          (.then (fn [account]
                   (.createTransport
                     nm
                     #js {:host "smtp.ethereal.email"
                          :port 587
                          :secure false
                          :auth #js {:user (aget account "user")
                                     :pass (aget account "pass")}}))))
      ; if the user has specified an actual SMTP server
      :else (js/Promise. (fn [res _err] (res (.createTransport nm smtp-url)))))))

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
          mail-params (clj->js (merge {:from from
                                       :to to
                                       :subject subject
                                       :text text
                                       :html html
                                       :headers headers}))
          send-result (-> (j/call transport :sendMail mail-params)
                          (.catch (fn [err] (js/console.error err) err)))
          result-url (.getTestMessageUrl nm send-result)
          logs (path/join server-dir "/logs")
          mail-log (.createStream rfs "mail.log" #js {:interval "7d" :path logs})
          log-result (->
                      (.createTransport nm #js {:jsonTransport true})
                      (j/call :sendMail mail-params))
          log-entry (j/get log-result :message)]
    ;(js/console.log (j/get log-entry :message))
    (when log-entry
      (j/call mail-log :write (str log-entry "\n")))
    (when result-url
      (aset send-result "url" result-url))
    (or send-result log-entry)))
