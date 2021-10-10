(ns email-example
  (:require
    [promesa.core :as p]
    [reagent.dom.server :refer [render-to-static-markup] :rename {render-to-static-markup r}]
    [sitefox.mail :as mail]))

(p/do! 
  (print "Sending a basic text email.")
  (-> (mail/send-email
        "chris@mccormick.cx"
        "test@example.com"
        "This is my test email."
        :text "Hello, This is my first email from **Sitefox**. Thank you.")
      (.then js/console.log))

  (print "Sending with multiple recipients, Reagent HTML, and custom X-Hello header.")
  (-> (mail/send-email
        ["chris@mccormick.cx"
         "goober@goober.com"]
        "test@example.com"
        "This is my test email."
        :text "Hello, This is my second email from **Sitefox**. Thank you."
        :html (r [:p
                  [:strong "Hello,"] [:br]
                  "This is my second email from Sitefox." [:br]
                  [:strong "Thank you."]])
        :headers {:X-Hello "Hello world!"})
      (.then js/console.log)))

