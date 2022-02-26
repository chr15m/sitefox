(ns authexample.server
  (:require
    ["fs" :as fs]
    ["node-input-validator" :refer [Validator]]
    [applied-science.js-interop :as j]
    [promesa.core :as p]
    [sitefox.html :refer [render-into]]
    [sitefox.web :as web]
    [sitefox.ui :refer [log]]
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
    [:form {:method "POST"}
     [:p [:input.fit {:name "email" :placeholder "Your email" :default-value (aget data "email")}]]
     [component-error errors :email]
     [:p [:input.fit {:name "password" :placeholder "password" :default-value (aget data "password")}]]
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
     [:p [:input.fit {:name "email" :placeholder "Your email"}]]
     [component-error errors :email]
     [:input {:name "_csrf" :type "hidden" :value csrf-token}]
     [:div.actions
      [:ul
       [:li [:a {:href "/sign-in"} "Sign in"]]]
      [:button.primary {:type "submit"} "Sign up"]]]]])

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

(defn view:error []
  [:div.warning "The form was tampered with."])

;***** functions *****;

(defn is-post [req]
  (= (aget req "method") "POST"))

(defn serve-form [req res template selector view]
  (.send res (render-into template selector [view (j/call req :csrfToken)])))

(defn validate-post-data [req fields warnings]
  (p/let [data (aget req "body")
          validator (Validator. data (clj->js fields) (clj->js warnings))
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

;***** handlers *****;

(defn handle-csrf-error [err _req res n template selector view]
  (if (= (aget err "code") "EBADCSRFTOKEN")
    (-> res
        (.status 403)
        (.send (render-into template selector view)))
    (n err)))

(defn sign-in [req res _n template selector view]
  (p/let [fields {:email ["required" "email"]
                  :password ["required"]}
          data (check-form req res template selector view fields)]
    (when data
      (log "data:" data)
      (.send res "VALID"))))

(defn sign-up [req res _n template selector view]
  (p/let [fields {:email ["required" "email"]}
          data (check-form req res template selector view fields)]
    (when data
      (log "data:" data)
      (.send res "VALID"))))

(defn forgot-password [req res n template selector view]
  (case (aget req "method")
    "GET" (serve-form req res template selector view)
    "POST" (.send res "Sign in")
    (n)))

(defn setup-routes [app]
  (let [template (fs/readFileSync "index.html")]
    (web/reset-routes app)
    (web/static-folder app "/css" "node_modules/minimal-stylesheet/")
    (j/call app :use #(handle-csrf-error %1 %2 %3 %4 template "main" view:error))
    (j/call app :use "/sign-up" #(sign-up %1 %2 %3 template "main" view:sign-up))
    (j/call app :use "/forgot-password" #(forgot-password %1 %2 %3 template "main" view:forgot-password))
    (j/call app :use "/" #(sign-in %1 %2 %3 template "main" view:sign-in))))

(defn main! []
  (p/let [[app host port] (web/start)]
    (reset! server app)
    (setup-routes app)
    (println "Server started on " (str host ":" port))))

(defn ^:dev/after-load reload []
  (js/console.log "Reloading")
  (setup-routes @server))


; sending email example
#_ (-> (mail/send-email
        "test@example.com"
        "test@example.com"
        "Form results."
        :text (str
                "Here is the result of the form:\n\n"
                (js/JSON.stringify data nil 2)))
      (.then #(js/console.log "Email: " (aget % "url"))))

