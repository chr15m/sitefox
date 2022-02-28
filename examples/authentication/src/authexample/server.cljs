(ns authexample.server
  (:require
    ["fs" :as fs]
    ["crypto" :refer [createHash createHmac]]
    ["node-input-validator" :refer [Validator]]
    ["html-to-text" :refer [htmlToText]]
    [applied-science.js-interop :as j]
    [promesa.core :as p]
    [sitefox.html :refer [render-into render]]
    [sitefox.web :as web]
    [sitefox.util :refer [env]]
    [sitefox.ui :refer [log]]
    [sitefox.mail :refer [send-email]]
    [sitefox.logging :refer [bind-console-to-file]]))

(bind-console-to-file)

(defonce server (atom nil))

;***** views *****;

(defn component-error [errors k]
  (let [err (aget errors (name k))]
    (when err [:p.warning (-> err (aget "message"))])))

(defn view:sign-in [csrf-token data errors]
  [:div
   [:h1 "Sign in"]
   [:div.card
    [:p "Enter your email and password to sign in."]
    [:form {:method "POST"}
     [:p [:input.fit {:name "email" :placeholder "Your email" :default-value (aget data "email")}]]
     [component-error errors :email]
     [:p [:input.fit {:name "password" :type "password" :placeholder "password" :default-value (aget data "password")}]]
     [component-error errors :password]
     [:input {:name "_csrf" :type "hidden" :value csrf-token}]
     [:div.actions
      [:ul
       [:li [:a {:href "/sign-up"} "Sign up"]]
       [:li [:a {:href "/forgot-password"} "Forgot password?"]]]
      [:button.primary {:type "submit"} "Sign in"]]]]])

(defn view:sign-up [csrf-token data errors]
  [:div
   [:h1 "Sign up"]
   [:div.card
    [:p "Enter your email to sign up."]
    [:form {:method "POST"}
     [:p [:input.fit {:name "email" :placeholder "Your email" :default-value (aget data "email")}]]
     [component-error errors :email]
     [:p "Verify email:"]
     [:p [:input.fit {:name "email2" :placeholder "Your email (again)" :default-value (aget data "email2")}]]
     [component-error errors :email2]
     [:input {:name "_csrf" :type "hidden" :value csrf-token}]
     [:div.actions
      [:ul
       [:li [:a {:href "/sign-in"} "Sign in"]]]
      [:button.primary {:type "submit"} "Sign up"]]]]])

(defn view:sign-up-sent [data]
  [:div
   [:h1 "Verification sent"]
   [:div.card
    [:p "Thanks for signing up. A verification has been sent to " (aget data "email") "."]
    [:p "Please check your email and follow the activation link to verify your account."]]])

(defn view:forgot-password [csrf-token]
  [:div
   [:h1 "Sign up"]
   [:div.card
    [:p "Enter your email to reset your password."]
    [:form {:method "POST"}
     [:p [:input.fit {:name "email" :placeholder "Your email"}]]
     [:input {:name "_csrf" :type "hidden" :value csrf-token}]
     [:div.actions
      [:ul
       [:li [:a {:href "/sign-in"} "Sign in"]]]
      [:button.primary {:type "submit"} "Reset"]]]]])

(defn view:sign-up-email [req verify-url]
  [:div
   [:h1 {:align "center"} "Signup verification"]
   [:p {:align "center"} "Click the link to verify your signup at " (aget req "hostname")]
   [:p {:align "center"}
    [:a {:href verify-url} verify-url]]])

(defn view:error []
  [:div.warning "The form was tampered with."])

;***** functions *****;

(defn is-post [req]
  (= (aget req "method") "POST"))

(defn serve-form [req res template selector view]
  (.send res (render-into template selector [view (j/call req :csrfToken)])))

(defn validate-post-data [req fields warnings]
  (p/let [data (aget req "body")
          validator (Validator. data (clj->js fields) (clj->js (or warnings {})))
          validated (.check validator)
          validation-errors (aget validator "errors")]
    [data validated validation-errors]))

(defn check-form [req res template selector view fields & [warnings]]
  (p/let [is-post (= (aget req "method") "POST")
          [data validated validation-errors] (when is-post (validate-post-data req fields warnings))
          passed-validation (and is-post validated)
          rendered-html (render-into template selector [view (j/call req :csrfToken) (or data #js {}) (or validation-errors #js {})])]
    (if (or (not is-post)
            (not passed-validation))
      (do (.send res rendered-html) nil)
      data)))

(defn make-hmac-token [secret size & materials]
  (let [s (-> (createHash "sha512") (.update secret) .digest)
        h (createHmac "sha512" s)]
    (doseq [v materials]
      (.update h (str v)))
    (-> h
        (.digest "hex")
        (.slice 0 size))))

;***** handlers *****;

(defn handle-csrf-error [err _req res n template selector view]
  (if (= (aget err "code") "EBADCSRFTOKEN")
    (-> res
        (.status 403)
        (.send (render-into template selector view)))
    (n err)))

(defn csrf-handler [template selector view]
  (fn [err req res n]
    (handle-csrf-error err req res n template selector view)))

(defn sign-in [req res _n template selector view]
  (p/let [fields {:email ["required" "email"]
                  :password ["required"]}
          data (check-form req res template selector view fields)]
    (when data
      (log "data:" data)
      (.send res "VALID"))))

(defn sign-up [req res _n template selector view [view-done email-view email-subject]]
  (p/let [fields {:email ["required" "email"]
                  :email2 ["required" "email" "same:email"]}
          messages {:email2.same "Email addresses must match."}
          data (check-form req res template selector view fields messages)]
    ; emit a warning if SECRET is not set
    (when (nil? (env "SECRET")) (js/console.error "Warning: env var SECRET is not set."))
    (when data
      (p/let [time-window (-> (js/Date.) .getTime)
              token (make-hmac-token (env "SECRET" "DEVMODE") 8 "sign-up" time-window (aget data "email"))
              verify-url (str (web/build-absolute-uri req "/verify-sign-up") "?h=" token)
              email-html (render [email-view req verify-url])
              email-text (htmlToText email-html #js {:hideLinkHrefIfSameAsText true
                                                     :uppercaseHeadings false})
              email-subject (or email-subject (str (aget req "hostname") " sign-up verification"))
              mail-result (send-email (aget data "email")
                                      (env "FROM_EMAIL" (str "no-reply@" (aget req "hostname")))
                                      email-subject
                                      :text email-text
                                      :html email-html)]
        (print "mail-result" mail-result)
        (print "verify-url" verify-url)
        (.send res (render-into template selector [view-done data]))))))

(defn forgot-password [req res n template selector view]
  (case (aget req "method")
    "GET" (serve-form req res template selector view)
    "POST" (.send res "Sign in")
    (n)))

(defn change-password [req res n template selector view])

(defn magic-link [req res n template selector view])

(defn auth-view [view-function template selector view & args]
  (fn [err req res]
    (view-function err req res template selector view args)))

(defn setup-auth-routes
  "Add authentication to your app. This function adds /sign-in /sign-up and /forgot-password routes to your app."
  ([app template]
   (setup-auth-routes app template "main"))
  ([app template selector]
   (j/call app :use "/sign-up" (auth-view sign-up template selector view:sign-up view:sign-up-sent view:sign-up-email))
   (j/call app :use "/verify-sign-up" (fn []))
   (j/call app :use "/forgot-password" (auth-view forgot-password template selector view:forgot-password))
   (j/call app :use "/sign-in" (auth-view sign-in template selector view:sign-in))
   (j/call app :use "/magic-link" (fn []))))

(defn setup-routes [app]
  (let [template (fs/readFileSync "index.html")]
    (web/reset-routes app)
    (web/static-folder app "/css" "node_modules/minimal-stylesheet/")
    (j/call app :use (csrf-handler template "main" view:error))
    (setup-auth-routes app template "main")))

(defn main! []
  (p/let [[app host port] (web/start)]
    (reset! server app)
    (setup-routes app)
    (println "Server started on " (str host ":" port))))

(defn ^:dev/after-load reload []
  (js/console.log "Reloading")
  (setup-routes @server))
