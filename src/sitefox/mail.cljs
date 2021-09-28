(ns sitefox.mail
  (:require
    [applied-science.js-interop :as j]
    [sitefox.util :refer [env]]
    ["nodemailer" :as nm]))

(defn create []
  (let [smtp-url (env "SMTP_SERVER" nil)]
    (if smtp-url
      (js/Promise. (fn [res err] (res (.createTransport nm smtp-url))))
      (-> (.createTestAccount nm)
          (.then (fn [account]
                   (.createTransport
                     nm
                     #js {:host "smtp.ethereal.email"
                          :port 587
                          :secure false
                          :auth #js {:user (aget account "user")
                                     :pass (aget account "pass")}})))))))

(defn send-mail [mail-transport to from subject html text unsubscribe-url]
  ; main().catch(console.error);
  (->
    (j/call
      mail-transport
      :sendMail
      (clj->js {:from from
                :to to
                :subject subject
                :text text
                :html html
                :list {:unsubscribe {:url unsubscribe-url}}}))
    (.catch (fn [err] #js {:error err}))
    (.then (fn [info]
             (aset info "url" (.getTestMessageUrl nm info))
             info))))
