(ns email-example
  (:require
    [promesa.core :as p]
    [sitefox.html :refer [render]]
    [sitefox.mail :as mail]))

(print "This demo uses ethereal.email unless SMTP_URL is configured.")
(print "Results will be printed to the console and not sent.")
(print)

(p/do! 
  (print "Sending a basic text email.")
  (-> (mail/send-email
        "test-to@example.com"
        "test@example.com"
        "This is my test email."
        :text "Hello, This is my first email from **Sitefox**. Thank you.")
      (.then js/console.log))

  (print "Sending with multiple recipients, Reagent HTML, and custom X-Hello header.")
  (-> (mail/send-email
        ["test-to@example.com"
         "goober@goober.com"]
        "test@example.com"
        "This is my test email."
        :text "Hello, This is my second email from **Sitefox**. Thank you."
        :html (render [:p
                       [:strong "Hello,"] [:br]
                       "This is my second email from Sitefox." [:br]
                       [:strong "Thank you."]])
        :headers {:X-Hello "Hello world!"})
      (.then js/console.log)))

