(ns webserver
  (:require
    [promesa.core :as p]
    ["fs" :as fs]
    ["node-input-validator" :refer [Validator]]
    [sitefox.html :refer [render-into render]]
    [sitefox.web :as web]
    [sitefox.mail :as mail]))

(def t (fs/readFileSync "index.html"))

(def fields
  {:name ["required" "minLength:5" "maxLength:20"]
   :date ["required" "date"]
   :count ["required" "min:5" "max:10" "integer"]})

(def warnings
  {:name "You must enter a name between 5 and 20 characters."
   :date "You must enter a valid date in YYYY-MM-DD format."
   :count "You must enter a quantity between 5 and 10."})

(defn view:form [csrf-token data validation-errors]
  (let [ve (or validation-errors #js {})
        data (or data #js {})]
    [:div
     [:h3 "Please fill out the form"]
     [:form {:method "POST"}
      [:p [:input.full {:name "name" :placeholder "Your name" :value (aget data "name") :on-change nil}]]
      (when (aget ve "name")
        [:p.warning (aget ve "name" "message")])
      [:p [:input.full {:name "date" :placeholder "Today's date YYYY-MM-DD" :value (aget data "date") :on-change nil}]]
      (when (aget ve "date")
        [:p.warning (aget ve "date" "message")])
      [:p [:input.full {:name "count" :placeholder "How many pets do you have?" :value (aget data "count") :on-change nil}]]
      (when (aget ve "count")
        [:p.warning (aget ve "count" "message")])
      [:input {:name "_csrf" :type "hidden" :value csrf-token}]
      [:button {:type "submit"} "Submit"]]]))

(defn view:thank-you []
  [:div
   [:h3 "Form complete."]
   [:p "Thank you for filling out the form. It has been emailed home safely."]])

(defn validate-post-data [req]
  (p/let [data (aget req "body")
          validator (Validator. data (clj->js fields) (clj->js warnings))
          validated (.check validator)]
    [data validated (aget validator "errors")]))

(defn email-form [data]
  (-> (mail/send-email
        "test@example.com"
        "test@example.com"
        "Form results."
        :text (str
                "Here is the result of the form:\n\n"
                (js/JSON.stringify data nil 2)))
      (.then #(js/console.log "Email: " (aget % "url")))))

(defn serve-form [req res]
  (p/let [is-post (= (aget req "method") "POST")
          [data validated validation-errors] (when is-post (validate-post-data req))
          passed-validation (and is-post validated)
          view (if passed-validation view:thank-you view:form)
          rendered-html (render-into t "main" [view (.csrfToken req) data validation-errors])]
    ; if the form was completed without errors send it
    (when passed-validation
      (print "Form validated. Sending email.")
      (email-form data))
    (.send res rendered-html)))

(defn handle-csrf-error [err req res n]
  (if (= (aget err "code") "EBADCSRFTOKEN")
    (-> res
        (.status 403)
        (.send (render-into t "main" [:div.warning "The form was tampered with."])))
    (n err)))

(defn setup-routes [app]
  (web/reset-routes app)
  (web/static-folder app "/css" "node_modules/minimal-stylesheet/")
  (.use app handle-csrf-error)
  (.use app "/" serve-form))

(p/let [app (web/create)
        [_host _port] (web/serve app)]
  (setup-routes app)
  (println "Serving."))
