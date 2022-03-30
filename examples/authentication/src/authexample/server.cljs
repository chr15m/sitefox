(ns authexample.server
  (:require
    ["fs" :as fs]
    ["passport" :as passport]
    ["passport-local" :as LocalStrategy]
    [applied-science.js-interop :as j]
    [promesa.core :as p]
    [sitefox.html :refer [render-into]]
    [sitefox.web :as web]
    [sitefox.ui :refer [log]]
    [sitefox.db :refer [kv]]
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
    [:div
     [:h1 "Sign in"]
     [:div.card
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
        [:button.primary {:type "submit"} "Sign in"]]]]]))

; sign up view ;

(defn component:sign-up-form [req]
  (let [csrf-token (j/call req :csrfToken)
        data (j/get req :body)
        errors (j/get req :errors)]
    [:div
     [:h1 "Sign up"]
     [:div.card
      [:p "Enter your email to sign up."]
      [:form {:method "POST"}
       [:p [:input.fit {:name "email" :placeholder "Your email" :default-value (aget data "email")}]]
       [component:error errors :email]
       [:p "Verify email:"]
       [:p [:input.fit {:name "email2" :placeholder "Your email (again)" :default-value (aget data "email2")}]]
       [component:error errors :email2]
       [component:messages req]
       [:input {:name "_csrf" :type "hidden" :value csrf-token}]
       [:div.actions
        [:ul
         [:li [:a {:href "/sign-in"} "Sign in"]]]
        [:button.primary {:type "submit"} "Sign up"]]]]]))

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

;***** auth functions *****;

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

; ***** route installing functions ***** ;

(defn setup-auth
  "Set up passport based authentication. The `logout-redirect-url` defaults to '/'."
  [app & [logout-redirect-url]]
  (j/call app :use (passport/authenticate "session"))
  (passport/serializeUser serialize-user)
  (passport/deserializeUser deserialize-user)
  (j/call app :get "/auth/logout" (fn [req res]
                                    (j/call req :logout)
                                    (.redirect res (or logout-redirect-url "/")))))

(defn setup-email-based-auth [app template selector & [{:keys [post-login-redirect]}]]
  (j/call passport :use (LocalStrategy. #js {:usernameField "email"} verify-kv-email-user))
  (j/call app :get "/auth/login"
          (fn [req res]
            (render-into-template res template selector [component:sign-in-form req])))
  (j/call app :post "/auth/login"
          (fn [req res done]
            ((passport/authenticate "local"
                                    (fn [err user info]
                                      (cond
                                        err (done err)
                                        (not user) (do (j/assoc-in! req [:auth :messages] #js [(j/assoc! info :class :error)])
                                                       (done))
                                        :else (j/call req :logIn user
                                                      (fn [err]
                                                        (if err
                                                          (done err)
                                                          (.redirect res (or post-login-redirect "/"))))))))
             req res done))
          (fn [req res] (render-into-template res template selector [component:sign-in-form req])))
  #_ (j/call app :get "/auth/sign-up"
             (fn [req res] (render-into-template res template "main" [component:sign-up-form req]))))

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
           [:p [:a {:href "/auth/logout"} "Sign out"]]]
         [:p [:a {:href "/auth/login"} "Sign in"]])])))

(defn setup-routes [app]
  (let [template (fs/readFileSync "index.html")]
    ; (j/call app :use (csrf-handler template "main" view:error))
    (web/reset-routes app)
    (web/static-folder app "/css" "node_modules/minimal-stylesheet/")
    (j/call app :get "/" (fn [req res] (homepage req res template)))
    (setup-auth app)
    (setup-email-based-auth app template "main")))

(defn main! []
  (p/let [[app host port] (web/start)]
    (reset! server app)
    (setup-routes app)
    (println "Server started on " (str host ":" port))))

(defn ^:dev/after-load reload []
  (js/console.log "Reloading")
  (setup-routes @server))
