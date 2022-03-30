(ns authexample.server
  (:require
    ["fs" :as fs]
    ["passport" :as passport]
    ["passport-local" :as LocalStrategy]
    ["node-input-validator" :refer [Validator]]
    ["html-to-text" :refer [htmlToText]]
    [applied-science.js-interop :as j]
    [promesa.core :as p]
    [sitefox.html :refer [render render-into]]
    [sitefox.web :as web]
    [sitefox.ui :refer [log]]
    [sitefox.db :refer [kv]]
    [sitefox.util :refer [env]]
    [sitefox.auth :refer [make-hmac-token]]
    [sitefox.mail :refer [send-email]]
    [sitefox.logging :refer [bind-console-to-file]]))

(bind-console-to-file)

(defonce server (atom nil))

;***** views *****;

(defn component:error [errors k]
  (let [err (j/get-in errors [(name k) "message"])]
    (when err [:p.error err])))

(defn component:messages [req]
  (let [messages (j/get-in req [:auth :messages])]
    [:ul.messages
     (for [m (range (count messages))]
       (let [message (nth messages m)]
         [:li {:key m
               :class (j/get message :class)}
          (j/get message :message)]))]))

; sign in view ;

(defn component:sign-in-form [req]
  (let [csrf-token (j/call req :csrfToken)
        errors (j/get req :errors)
        data (j/get req :body)]
    [:section.auth
     [:p "Enter your email and password to sign in."]
     [:form {:method "POST"}
      [:p [:input.fit {:name "email" :placeholder "Your email" :default-value (j/get data :email)}]]
      [component:error errors :email]
      [:p [:input.fit {:name "password" :type "password" :placeholder "password" :default-value (j/get data :password)}]]
      [:input {:name "_csrf" :type "hidden" :value csrf-token}]
      [component:messages req]
      [:div.actions
       [:ul
        [:li [:a {:href "/auth/sign-up"} "Sign up"]]
        [:li [:a {:href "/auth/forgot-password"} "Forgot password?"]]]
       [:button.primary {:type "submit"} "Sign in"]]]]))

; sign up view ;

(defn component:sign-up-form [req]
  (let [csrf-token (j/call req :csrfToken)
        data (j/get req :body)
        errors (j/get req :errors)]
    [:section.auth
     [:p "Enter your email to sign up."]
     [:form {:method "POST"}
      [:p [:input.fit {:name "email" :placeholder "Your email" :default-value (j/get data :email)}]]
      [component:error errors :email]
      [:p "Verify email:"]
      [:p [:input.fit {:name "email2" :placeholder "Your email (again)" :default-value (j/get data :email2)}]]
      [component:error errors :email2]
      [component:messages req]
      [:input {:name "_csrf" :type "hidden" :value csrf-token}]
      [:div.actions
       [:ul
        [:li [:a {:href "/auth/sign-in"} "Sign in"]]]
       [:button.primary {:type "submit"} "Sign up"]]]]))

(defn component:sign-up-email [req verify-url]
  [:div
   [:h1 {:align "center"} "Signup verification"]
   [:p {:align "center"} "Click the link to verify your signup at " (aget req "hostname")]
   [:p {:align "center"}
    [:a {:href verify-url} verify-url]]])

(defn component:sign-up-form-done [req]
  (let [data (j/get req :body)]
    [:section.auth
     [:h3 "Verification sent"]
     [:p "Thanks for signing up. A verification has been sent to " [:strong (j/get data :email)] "."]
     [:p "Please check your email and follow the activation link to verify your account."]
     [:p "Don't forget to check your spam folder if you can't find the activation email."]]))

; forgot password view ;

;***** non-auth functions *****;

(defn render-into-template
  "Render `selector` `component` pairs into the template and return the resulting HTML string as a response."
  [res template & selector-component-pairs]
  (.send res
         (reduce
           (fn [html [selector component]]
             (render-into html selector component))
           template
           (partition 2 selector-component-pairs))))

(defn is-post? [req]
  (= (aget req "method") "POST"))

(defn validate-post-data [req fields & [warnings]]
  (p/let [data (j/get req :body)
          validator (Validator. data (clj->js fields) (clj->js (or warnings {})))
          validated (.check validator)
          validation-errors (j/get validator :errors)]
    (when (not validated)
      validation-errors)))

;***** user data functions *****;

(defn serialize-user [user cb]
  (log "serialize-user" user)
  (cb nil user))

(defn deserialize-user [user cb]
  (log "deserialize-user" user)
  (cb nil user))

(defn user-from-req [req]
  (j/get-in req [:session :passport :user]))

(defn get-user-by-key
  "Auth key is the lookup token such as email or username."
  [auth-key-type auth-key]
  (p/let [user-ids-table (kv "user-ids")
          users-table (kv "users")
          user-id (.get user-ids-table (str auth-key-type ":" auth-key))
          user (when user-id
                 (.get users-table user-id))]
    (when user
      (j/assoc! user :id user-id))))

(defn verify-kv-email-user [email password cb]
  (p/let [user (clj->js {:auth {:email email}}) ;(get-user-by-key "email" email)
          auth (j/get-in user [:auth :email])
          invalid #js {:message "Invalid email or password."}]
    (cond
      (nil? auth) (cb nil false invalid)
      (not= password "hello") (cb nil false invalid)
      :else (cb nil user))))

; ***** route handling functions ***** ;

; sign in middleware ;

(defn middleware:sign-in [req res done]
  (if (is-post? req)
    ((passport/authenticate "local"
                            (fn [err user info]
                              (cond
                                err (done err)
                                (not user) (do (j/assoc-in! req [:auth :messages] #js [(j/assoc! info :class :error)])
                                               (done))
                                :else (j/call req :logIn user done))))
     req res done)
    (done)))

(defn make-middleware:sign-in-redirect [post-sign-in-redirect]
  (fn [req res done]
    (if (user-from-req req)
      (.redirect res (or post-sign-in-redirect "/"))
      (done))))

; sign up middleware ;

(defn middleware:sign-up [req _res done]
  (if (is-post? req)
    (p/let [fields {:email ["required" "email"]
                    :email2 ["required" "email" "same:email"]}
            warnings {:email2.same "Email addresses must match."}
            validation-errors (validate-post-data req fields warnings)]
      (when validation-errors
        (j/assoc! req :errors validation-errors))
      (done))
    (done)))

(defn make-middleware:sign-up-email [email-view-component email-subject from-address]
  (fn [req _res done]
    (let [data (j/get req :body)
          validation-errors (j/get req :errors)]
      (if (and (is-post? req) (not validation-errors))
        (do
          (when (nil? (env "SECRET")) (js/console.error "Warning: env var SECRET is not set."))
          (p/let [hostname (j/get req :hostname)
                  email-address (j/get data :email)
                  from-address (or from-address (env "FROM_EMAIL" (str "no-reply@" hostname)))
                  email-view-component (or email-view-component component:sign-up-email)
                  time-stamp (-> (js/Date.) .getTime)
                  token (make-hmac-token (env "SECRET" "DEVMODE") 8 (str time-stamp) "sign-up" email-address)
                  verify-url (str (web/build-absolute-uri req "/auth/verify-sign-up") "?h=" token "&e=" email-address "&t=" time-stamp)
                  email-html (render [email-view-component req verify-url])
                  email-text (htmlToText email-html #js {:hideLinkHrefIfSameAsText true
                                                         :uppercaseHeadings false})
                  email-subject (or email-subject (str hostname " sign-up verification"))
                  mail-result (send-email email-address
                                          from-address
                                          email-subject
                                          :text email-text
                                          :html email-html)]
            (print "mail-result" mail-result)
            (print "verify-url" verify-url)
            (j/assoc-in! req [:auth :sign-up-email-sent] true)
            (done)))
        (done)))))

; ***** route installing functions ***** ;

(defn setup-auth
  "Set up passport based authentication. The `sign-out-redirect-url` defaults to '/'."
  [app & [sign-out-redirect-url]]
  (j/call app :use (passport/authenticate "session"))
  (j/call app :get "/auth/sign-out" (fn [req res]
                                      (j/call req :logout)
                                      (.redirect res (or sign-out-redirect-url "/"))))
  (when (not (j/get passport :_sitefox_setup_auth))
    (passport/serializeUser serialize-user)
    (passport/deserializeUser deserialize-user)
    (j/assoc! passport :_sitefox_setup_auth true)))

(defn setup-email-based-auth [app template selector
                              & [{:keys [post-sign-in-redirect
                                         sign-up-email-component email-subject from-address]}]]
  (j/call passport :use (LocalStrategy. #js {:usernameField "email"} verify-kv-email-user))
  (j/call app :use "/auth/sign-in"
          middleware:sign-in
          (make-middleware:sign-in-redirect post-sign-in-redirect)
          (fn [req res] (render-into-template res template selector [component:sign-in-form req])))
  (j/call app :use "/auth/sign-up"
          middleware:sign-up
          (make-middleware:sign-up-email sign-up-email-component email-subject from-address)
          (fn [req res]
            (let [sign-up-email-sent (j/get-in req [:auth :sign-up-email-sent])
                  view-component (if sign-up-email-sent
                                   component:sign-up-form-done
                                   component:sign-up-form)]
              (render-into-template res template "main" [view-component req])))))

; user-space calls

(defn homepage [req res template]
  (let [user (user-from-req req)]
    (render-into-template
      res template "main"
      [:div
       [:h1 "Auth demo"]
       (if user
         [:<>
           [:p "Signed in as " (pr-str user)]
           [:p [:a {:href "/auth/sign-out"} "Sign out"]]]
         [:p [:a {:href "/auth/sign-in"} "Sign in"]])])))

(defn setup-routes [app]
  (let [template (fs/readFileSync "index.html")]
    ; (j/call app :use (csrf-handler template "main" view:error))
    (web/reset-routes app)
    (web/static-folder app "/css" "node_modules/minimal-stylesheet/")
    (j/call app :get "/" (fn [req res] (homepage req res template)))
    (setup-auth app)
    (setup-email-based-auth app template "main")
    ; from-email defaults to env var FROM_EMAIL
    ; sign-up-email-component defaults to component:sign-up-email and takes two args: `req` and `verify-url`
    ; email-subject defaults to "req.hostname sign-up verification"
    ; from-address defaults to no-reply@req.hostname
    #_ (setup-email-based-auth app template "main"
                               {:post-sign-in-redirect "/"
                                :sign-up-email-component component:sign-up-email
                                :email-subject "Welcome to thingo!"
                                :from-address "no-reply@jones.com"})))

(defn main! []
  (p/let [[app host port] (web/start)]
    (reset! server app)
    (setup-routes app)
    (println "Server started on " (str host ":" port))))

(defn ^:dev/after-load reload []
  (js/console.log "Reloading")
  (setup-routes @server))
