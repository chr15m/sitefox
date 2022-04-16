(ns sitefox.auth
  (:require
    [sitefox.deps :refer [passport LocalStrategy]]
    ["node-input-validator" :refer [Validator]]
    ["html-to-text" :refer [htmlToText]]
    [clojure.test :refer-macros [is async]]
    [applied-science.js-interop :as j]
    [promesa.core :as p]
    ["crypto" :refer [createHash createHmac randomBytes pbkdf2Sync scryptSync createCipheriv createDecipheriv]]
    [sitefox.util :refer [env]]
    [sitefox.html :refer [render direct-to-template]]
    [sitefox.db :refer [kv]]
    [sitefox.mail :refer [send-email]]
    [sitefox.web :refer [is-post? build-absolute-uri name-route get-named-route]]))

(defn make-hmac-token
  "Create an HMAC token to be used for verifying data was generated by the server and is unmodified."
  [secret size & materials]
  (let [s (-> (createHash "sha512") (.update secret) .digest)
        h (createHmac "sha512" s)]
    (doseq [v materials]
      (.update h (str v)))
    (-> h
        (.digest "hex")
        (.slice 0 size))))

(defn hash-password
  "Hash a password for storage in a database.
  If `salt` (hex string) is not supplied it will be generated (it should be passed when comparing but not when generating)."
  {:test (fn []
           (let [[e s] (hash-password "goober")
                 [e2 s2] (hash-password "goober" s)
                 [e3] (hash-password "something" s)
                 [e4] (hash-password "goober")
                 [e5] (hash-password "goober" "deadc0de")]
             (is (= e e2))
             (is (= s s2))
             (is (not= e e3))
             (is (not= e e4))
             (is (not= e e5))))}
  [pw & [salt]]
  (let [salt (if salt
               (js/Buffer.from salt "hex")
               (randomBytes 16))]
    [(.toString (pbkdf2Sync pw salt 310000 32 "sha512") "hex")
     (.toString salt "hex")]))

(defn encrypt-for-transit
  "Encrypts a piece of data for transit using symmetric key cryptography and the server's own secret."
  [materials]
  (js/Promise.
    (fn [res _err]
      (let [secret (env "SECRET" "DEVMODE")
            k (scryptSync secret "encrypt-for-transit" 32)
            iv (randomBytes 16)
            encoded (-> materials clj->js js/JSON.stringify)]
        (when (= secret "DEVMODE") (js/console.error "Warning: env var SECRET is not set."))
        (let [cipher (createCipheriv "aes-256-gcm" k iv #js {:authTagLength 16})
              encrypted-buffer (js/Buffer.concat
                                 #js [(.update cipher encoded) (.final cipher)])
              auth-tag (.getAuthTag cipher)
              assembled (js/Buffer.concat
                          #js [iv encrypted-buffer auth-tag])]
          (-> assembled
              (.toString "base64")
              (.replaceAll "/" "_")
              (.replaceAll "+" "-")
              res))))))

(defn decrypt-for-transit
  "Decrypts a piece of data using symmetric key cryptography and the server's own secret."
  {:test (fn []
           (async done
                  (p/let [vi "some string of data"
                          vx (encrypt-for-transit vi)
                          vo (decrypt-for-transit vx)
                          vi2 (clj->js {:something-else 42 :h [1 2 4]})
                          vx2 (encrypt-for-transit vi2)
                          vo2 (decrypt-for-transit vx2)]
                    (is (= vi vo))
                    (is (= (js->clj vi2) (js->clj vo2)))
                    ; test modified iv
                    (p/let [vi "something"
                            vx (encrypt-for-transit vi)
                            l (.slice vx 0 1)
                            r (.slice vx 2)
                            decrypted (decrypt-for-transit (str l "X" r))]
                      (is (= decrypted nil)))
                    ; test modified encrypted packet
                    (p/let [vi "another thing"
                            vx (encrypt-for-transit vi)
                            l (.slice vx 0 18)
                            r (.slice vx 19)
                            decrypted (decrypt-for-transit (str l "Z" r))]
                      (is (= decrypted nil)))
                    ; test truncated packet / iv
                    (p/let [vi "something else"
                            vx (encrypt-for-transit vi)
                            decrypted (decrypt-for-transit (.slice vx 1))]
                      (is (= decrypted nil))))))}
  [encrypted]
  (js/Promise.
    (fn [res _err]
      (let [secret (env "SECRET" "DEVMODE")
            k (scryptSync secret "encrypt-for-transit" 32)
            data (js/Buffer.from encrypted "base64")
            auth-tag (.slice data -16)
            iv (.slice data 0 16)
            msg (.slice data 16 -16)
            cipher (-> (createDecipheriv "aes-256-gcm" k iv #js {:authTagLength 16})
                       (.setAuthTag auth-tag))
            raw (try (str
                       (.update cipher msg "utf8")
                       (.final cipher "utf8"))
                     (catch :default _e nil))
            decoded (-> raw js/JSON.parse)]
        (res decoded)))))

(defn timestamp-expired?
  "Check if a timestamp (ms) has expired."
  {:test (fn []
           (let [now (-> (js/Date.) (.getTime))]
             (is (timestamp-expired? nil 1))
             (is (timestamp-expired? "BLAH" 1))
             (is (timestamp-expired? (- now 3000) 2000))
             (is (timestamp-expired? now -1))
             (is (not (timestamp-expired? now 500)))
             (is (not (timestamp-expired? now 2000)))
             (is (not (timestamp-expired? (- now 1500) 2000)))
             (is (not (timestamp-expired? (+ now 1000) 2000)))))}
  [time-stamp milliseconds]
  (let [time-stamp (js/parseInt time-stamp)]
    (or
      (js/isNaN time-stamp)
      (< time-stamp (-> (js/Date.) (.getTime) (- milliseconds))))))

;***** authentication helper functions *****;

(defn validate-post-data [req fields & [warnings]]
  (p/let [data (j/get req :body)
          validator (Validator. data (clj->js fields) (clj->js (or warnings {})))
          validated (.check validator)
          validation-errors (j/get validator :errors)]
    (when (not validated)
      validation-errors)))

(defn add-messages! [req messages]
  (j/update-in! req [:auth :messages] #(.concat (or % #js []) (clj->js messages))))

;***** user data functions *****;

(defn serialize-user [user cb]
  (cb nil #js {:id (j/get user :id)}))

(defn deserialize-user [user cb]
  (p/let [users-table (kv "users")
          user-id (j/get user :id)
          user (.get users-table user-id)]
    (cb nil user)))

(defn get-user-by-key
  "Get a user object from the user's kv table. The `auth-key` is the lookup token such as email or username."
  [auth-key-type auth-key]
  (p/let [user-ids-table (kv "user-ids")
          users-table (kv "users")
          user-id (.get user-ids-table (str (name auth-key-type) ":" auth-key))
          user (when user-id (.get users-table user-id))]
    (when user
      (j/assoc! user :id user-id))))

(defn create-user
  "Creates a new user object in the user's kv table.
   Creates a lookup from `auth-key` to `user-id` for convenient retrieval using `get-user-by-key`."
  [auth-key-type auth-key & [user-data]]
  (p/let [user-data (or user-data #js {})
          user-ids-table (kv "user-ids")
          users-table (kv "users")
          user-id (str (random-uuid))
          user-data (j/assoc! user-data :id user-id)]
    (.set user-ids-table (str (name auth-key-type) ":" auth-key) user-id)
    (.set users-table user-id user-data)
    user-data))

(defn get-or-create-user-by-key
  "Try to find a user object by it's `auth-key` (i.e. username/email) and create a new user with that `auth-key` if it can't be found."
  [auth-key-type auth-key & [user-data]]
  (p/let [existing-user (get-user-by-key auth-key-type auth-key)]
    (or existing-user (create-user auth-key-type auth-key user-data))))

(defn save-user
  "Persist a user object back into the users kv table, overwriting the existing object."
  [user-data]
  (p/let [user-id (j/get user-data :id)
          users-table (kv "users")
          user-data (j/assoc! user-data :id user-id)]
    (.set users-table user-id user-data)
    user-data))

(defn verify-kv-email-user [email password cb]
  (p/let [user (get-user-by-key "email" email)
          hashed-password (j/get-in user [:auth :password])
          salt (j/get-in user [:auth :salt])
          invalid #js {:message "Invalid email or password."}]
    (cond
      (nil? user) (cb nil false invalid)
      (not= (first (hash-password password salt)) hashed-password) (cb nil false invalid)
      :else (cb nil user))))

; ***** route handling functions ***** ;

; sign in middleware ;

(defn middleware:sign-in-submit [req res done]
  (if (is-post? req)
    ((.authenticate passport "local"
                    (fn [err user info]
                      (cond
                        err (done err)
                        (not user) (do (j/assoc-in! req [:auth :messages] #js [(j/assoc! info :class :error)])
                                       (done))
                        :else (j/call req :logIn user done))))
     req res done)
    (done)))

(defn make-middleware:signed-in-redirect [redirect-url]
  (fn [req res done]
    (if (j/get req :user)
      (.redirect res (or redirect-url "/"))
      (done))))

; sign up middleware ;

(defn middleware:sign-up-submit [req _res done]
  (if (is-post? req)
    (p/let [fields {:email ["required" "email"]
                    :email2 ["required" "email" "same:email"]
                    :password ["required"]
                    :password2 ["required" "same:password"]}
            warnings {:email2.same "Email addresses must match."
                      :password2.same "Passwords must match."}
            validation-errors (validate-post-data req fields warnings)]
      (when validation-errors
        (j/assoc! req :errors validation-errors))
      (done))
    (done)))

(defn make-middleware:sign-up-email [email-view-component email-subject from-address]
  (fn [req _res done]
    (p/let [data (j/get req :body)
            validation-errors (j/get req :errors)]
      (when (and (is-post? req) (not validation-errors))
        (when (nil? (env "SECRET")) (js/console.error "Warning: env var SECRET is not set."))
        (p/let [hostname (j/get req :hostname)
                email-address (j/get data :email)
                password (j/get data :password)
                from-address (or from-address (env "FROM_EMAIL" (str "no-reply@" hostname)))
                time-stamp (-> (js/Date.) .getTime)
                packet {:e email-address
                        :p password
                        :t time-stamp}
                encrypted-packet (encrypt-for-transit (-> packet clj->js js/JSON.stringify))
                verify-url (str (build-absolute-uri req (get-named-route req "auth:verify-sign-up")) "?v=" encrypted-packet)
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
          (j/assoc-in! req [:auth :sign-up-email-sent] true)))
      (done))))

(defn middleware:verify-sign-up [req _res done]
  (p/let [encrypted-packet (j/get-in req [:query :v])
          packet (when encrypted-packet (decrypt-for-transit encrypted-packet))
          q (js/JSON.parse packet)
          time-stamp (j/get q :t)
          token-expired? (timestamp-expired? time-stamp (* 1000 60 60 24))]
    (cond
      (nil? q)
      (add-messages! req [{:message "There was a problem verifying the link. Please try again."}])
      token-expired?
      (add-messages! req {:message "This verification link has expired. Please try to sign up again."
                          :class :error})
      :else
      (j/assoc-in! req [:auth :sign-up-data] q))
    (done)))

(defn middleware:finalize-sign-up [req _res done]
  (let [sign-up-data (j/get-in req [:auth :sign-up-data])]
    (if sign-up-data
      (p/let [password (j/get sign-up-data :p)
              email (j/get sign-up-data :e)
              [hashed-password salt] (hash-password password)
              user-data (clj->js {:auth {:email email
                                         :password hashed-password
                                         :salt salt}})
              user (get-or-create-user-by-key :email email user-data)]
        (j/call req :logIn user done))
      (done))))

; reset password middleware ;

(defn middleware:reset-password-email-submit [req _res done]
  (if (is-post? req)
    (p/let [fields {:email ["required" "email"]}
            validation-errors (validate-post-data req fields)]
      (when validation-errors
        (j/assoc! req :errors validation-errors))
      (done))
    (done)))

(defn make-middleware:reset-password-send-email [reset-password-email-component email-subject from-address]
  (fn [req _res done]
    (p/let [data (j/get req :body)
            validation-errors (j/get req :errors)]
      (when (and (is-post? req) (not validation-errors))
        (when (nil? (env "SECRET")) (js/console.error "Warning: env var SECRET is not set."))
        (p/let [hostname (j/get req :hostname)
                email-address (j/get data :email)
                from-address (or from-address (env "FROM_EMAIL" (str "no-reply@" hostname)))
                time-stamp (-> (js/Date.) .getTime)
                packet {:e email-address
                        :t time-stamp}
                encrypted-packet (encrypt-for-transit (-> packet clj->js js/JSON.stringify))
                verify-url (str (build-absolute-uri req (get-named-route req "auth:reset-password-form")) "?v=" encrypted-packet)
                email-html (render [reset-password-email-component req verify-url])
                email-text (htmlToText email-html #js {:hideLinkHrefIfSameAsText true
                                                       :uppercaseHeadings false})
                email-subject (or email-subject (str hostname " reset password link"))
                mail-result (send-email email-address
                                        from-address
                                        email-subject
                                        :text email-text
                                        :html email-html)]
          (print "mail-result" mail-result)
          (print "verify-url" verify-url)
          (j/assoc-in! req [:auth :sign-up-email-sent] true)))
      (done))))

(defn middleware:verify-reset-password [req _res done]
  ; skip verification if the user is already authenticated
  (if (j/get req :user)
    (done)
    (p/let [encrypted-packet (j/get-in req [:query :v])
            packet (when encrypted-packet (decrypt-for-transit encrypted-packet))
            q (js/JSON.parse packet)
            time-stamp (j/get q :t)
            token-expired? (timestamp-expired? time-stamp (* 1000 60 60 24))
            email (j/get q :e)
            user (get-user-by-key :email email)]
      (cond
        (nil? q)
        (add-messages! req [{:message "There was a problem verifying the link. Please try again."}])
        token-expired?
        (add-messages! req {:message "This password reset link has expired. Please try to again."
                            :class :error})
        (nil? user)
        (add-messages! req {:message (str "No user exists with the email " email ".")})
        :else
        (j/assoc-in! req [:auth :reset-password-data] q))
      (done))))

(defn middleware:reset-password-submit [req _res done]
  (if (is-post? req)
    (p/let [fields {:password ["required"]
                    :password2 ["required" "same:password"]}
            warnings {:password2.same "Passwords must match."}
            validation-errors (validate-post-data req fields warnings)]
      (when validation-errors
        (j/assoc! req :errors validation-errors))
      (done))
    (done)))

(defn middleware:update-password [req _res done]
  (p/let [reset-password-data (j/get-in req [:auth :reset-password-data])
          password (j/get-in req [:body :password])
          user (or
                 (j/get req :user)
                 (get-user-by-key :email (j/get reset-password-data :e)))]
    (if password
      (if user
        (p/let [email (j/get-in user [:auth :email])
                [hashed-password salt] (hash-password password)
                user (j/assoc! user :auth (clj->js {:email email
                                                    :password hashed-password
                                                    :salt salt}))
                user (save-user user)]
          (j/call req :logIn user done))
        (do
          (add-messages! req {:message "No user with this email address exists. Please sign up instead."
                              :class :error}) 
          (done)))
      (done))))

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

(defn component:simple-message [req]
  [:section.auth
   [component:messages req]])

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
      [component:error errors :password]
      [:input {:name "_csrf" :type "hidden" :value csrf-token}]
      [component:messages req]
      [:div.actions
       [:ul
        [:li [:a {:href (get-named-route req "auth:sign-up")} "Sign up"]]
        (when-let [reset-password-url (get-named-route req "auth:reset-password")]
          [:li [:a {:href reset-password-url} "Forgot password?"]])
        (when-let [magic-link-url (get-named-route req "auth:magic-link")]
          [:li [:a {:href magic-link-url} "Get a magic login link"]])]
       [:button.primary {:type "submit"} "Sign in"]]]]))

; sign up view ;

(defn component:sign-up-form [req]
  (let [csrf-token (j/call req :csrfToken)
        data (j/get req :body)
        errors (j/get req :errors)]
    [:section.auth
     [:p "Enter your email and desired password to sign up."]
     [:form {:method "POST"}
      [:p [:input.fit {:name "email" :placeholder "Your email" :default-value (j/get data :email)}]]
      [component:error errors :email]
      [:p "Verify email:"]
      [:p [:input.fit {:name "email2" :placeholder "Your email (again)" :default-value (j/get data :email2)}]]
      [component:error errors :email2]
      [:p "Enter your desired password:"]
      [:p [:input.fit {:name "password" :type "password" :placeholder "Password" :default-value (j/get data :password)}]]
      [component:error errors :password]
      [:p "Verify password:"]
      [:p [:input.fit {:name "password2" :type "password" :placeholder "Password (again)" :default-value (j/get data :password2)}]]
      [component:error errors :password2]
      [component:messages req]
      [:input {:name "_csrf" :type "hidden" :value csrf-token}]
      [:div.actions
       [:ul
        [:li [:a {:href (get-named-route req "auth:sign-in")} "Sign in"]]]
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
     [:p "Don't forget to check your spam folder if you can't find the email."]]))

(defn component:sign-up-success [_req]
  [:section.auth
   [:h3 "Sign up complete"]
   [:p "You are all signed up and signed in. Welcome to the site."]])

; reset password view ;

(defn component:reset-password-email-form [req]
  (let [csrf-token (j/call req :csrfToken)
        errors (j/get req :errors)
        data (j/get req :body)]
    [:section.auth
     [:p "Please enter your email to receive a password reset link."]
     [:form {:method "POST"}
      [:p [:input.fit {:name "email" :placeholder "Your email" :default-value (j/get data :email)}]]
      [component:error errors :email]
      [:input {:name "_csrf" :type "hidden" :value csrf-token}]
      [component:messages req]
      [:div.actions
       [:ul
        [:li [:a {:href (get-named-route req "auth:sign-in")} "Sign in"]]
        [:li [:a {:href (get-named-route req "auth:sign-up")} "Sign up"]]]
       [:button.primary {:type "submit"} "Reset password"]]]]))

(defn component:reset-password-email [req verify-url]
  [:div
   [:h1 {:align "center"} "Reset password link"]
   [:p {:align "center"} "Click the link to reset your password at " (aget req "hostname")]
   [:p {:align "center"}
    [:a {:href verify-url} verify-url]]])

(defn component:reset-password-email-form-done [req]
  (let [data (j/get req :body)]
    [:section.auth
     [:h3 "Reset password link sent"]
     [:p "A reset password link has been sent to " [:strong (j/get data :email)] "."]
     [:p "Please check your email and follow the link to reset your password."]
     [:p "Don't forget to check your spam folder if you can't find the email."]]))

(defn component:reset-password-form [req]
  (let [csrf-token (j/call req :csrfToken)
        errors (j/get req :errors)
        data (j/get req :body)]
    [:section.auth
     [:p "Please enter a new password."]
     [:form {:method "POST"}
      [:p [:input.fit {:name "password" :type "password" :placeholder "Password" :default-value (j/get data :password)}]]
      [component:error errors :password]
      [:p [:input.fit {:name "password2" :type "password" :placeholder "Password (again)" :default-value (j/get data :password2)}]]
      [component:error errors :password2]
      [:input {:name "_csrf" :type "hidden" :value csrf-token}]
      [component:messages req]
      [:div.actions
       [:ul
        [:li [:a {:href (get-named-route req "auth:sign-in")} "Sign in"]]
        [:li [:a {:href (get-named-route req "auth:sign-up")} "Sign up"]]]
       [:button.primary {:type "submit"} "Update password"]]]]))

; ***** route installing functions ***** ;

(defn setup-auth
  "Set up passport based authentication. The `sign-out-redirect-url` defaults to '/'."
  [app & [sign-out-redirect-url]]
  (j/call app :use (.authenticate passport "session"))
  (j/call app :get (name-route app "/auth/sign-out" "auth:sign-out") (fn [req res]
                                      (j/call req :logout)
                                      (.redirect res (or sign-out-redirect-url "/"))))
  (when (not (j/get passport :_sitefox_setup_auth))
    (.serializeUser passport serialize-user)
    (.deserializeUser passport deserialize-user)
    (j/assoc! passport :_sitefox_setup_auth true)))

(defn setup-email-based-auth
  "Set up passport email based authentication with all of the required forms and views.
  Pass in an HTML `template` string and `selector` where the auth UI should be be mounted.

  You can override various aspects of the UI using these keys:

  * `:sign-in-redirect` is the URL to redirect to after signing in (defaults to `/`).
  * `:sign-up-redirect` is the URL to redirect to after signing up successfully (defaults to `/`).
  * `:sign-in-form-component` is a Reagent component to render the sign-in form (defaults to `component:sign-in-form`).
  * `:sign-up-email-component` is a Reagent component to render the sign-up validation email (defaults to `component:sign-up-email`).
  * `:sign-up-email-subject` the subject line of the sign up verification email (defaults to `req.hostname + ' signup email.'`).
  * `:sign-up-form-component` is a Reagent component to render the sign-up form (defaults to `component:sign-up-form`).
  * `:sign-up-form-done-component` is a Reagent component to render the sign-up done page (defaults to `component:sign-up-form-done`).
  * `:simple-message-component` is a Reagent component to render error messages during the verification stage (defaults to `component:simple-message`)."
  [app template selector
   & [{:keys [sign-in-redirect
              sign-in-form-component
              sign-up-redirect
              sign-up-email-component sign-up-email-subject sign-up-from-address
              sign-up-form-component sign-up-form-done-component
              simple-message-component]}]]
  (j/call passport :use (LocalStrategy. #js {:usernameField "email"} verify-kv-email-user))
  (j/call app :use (name-route app "/auth/sign-in" "auth:sign-in")
          middleware:sign-in-submit
          (make-middleware:signed-in-redirect sign-in-redirect)
          (fn [req res] (direct-to-template res template selector [(or sign-in-form-component component:sign-in-form) req])))
  (j/call app :use (name-route app "/auth/sign-up" "auth:sign-up")
          middleware:sign-up-submit
          (make-middleware:sign-up-email (or sign-up-email-component component:sign-up-email) sign-up-email-subject sign-up-from-address)
          (fn [req res]
            (let [sign-up-email-sent (j/get-in req [:auth :sign-up-email-sent])
                  view-component (if sign-up-email-sent
                                   (or sign-up-form-done-component component:sign-up-form-done)
                                   (or sign-up-form-component component:sign-up-form))]
              (direct-to-template res template "main" [view-component req]))))
  (j/call app :use (name-route app "/auth/verify-sign-up" "auth:verify-sign-up")
          middleware:verify-sign-up
          middleware:finalize-sign-up
          (make-middleware:signed-in-redirect sign-up-redirect)
          (fn [req res] (direct-to-template res template selector [(or simple-message-component component:simple-message) req]))))

(defn setup-reset-password
  "Add a 'reset password' flow to the app. Covers both 'change password' and 'forgot password' functionality.
  Pass in an HTML `template` string and `selector` where the auth UI should be mounted.

  You can override various aspects of the UI using these keys:

  * `:reset-redirect` is the URL to redirect to after the password has been reset successfully (defaults to `/`).
  * `:reset-password-email-form-component` is a Reagent component to render the reset-password email form (defaults to `component:reset-password-email-form`).
  * `:reset-password-form-component` is a Reagent component to render the reset-password form (defaults to `component:reset-password-form`).
  * `:simple-message-component` is a Reagent component to render error messages during the password reset process (defaults to `component:simple-message-component`)."
  [app template selector
   & [{:keys [reset-password-redirect
              reset-password-email-subject
              reset-password-from-address
              reset-password-email-component
              reset-password-email-form-component
              reset-password-email-form-done-component
              reset-password-form-component
              simple-message-component]}]]
  (j/call app :use (name-route app "/auth/reset-password" "auth:reset-password")
          middleware:reset-password-email-submit
          (make-middleware:reset-password-send-email
            (or reset-password-email-component component:reset-password-email)
            reset-password-email-subject reset-password-from-address)
          (fn [req res done]
            (let [validation-errors (j/get req :errors)]
              (if (and (is-post? req) (not validation-errors))
                (done)
                (direct-to-template res template selector [(or reset-password-email-form-component component:reset-password-email-form) req]))))
          (fn [req res] (direct-to-template res template selector [(or reset-password-email-form-done-component component:reset-password-email-form-done) req])))
  (j/call app :use (name-route app "/auth/reset-password-form" "auth:reset-password-form")
          middleware:verify-reset-password
          middleware:reset-password-submit
          (fn [req res done]
            (let [validation-errors (j/get req :errors)]
              (if (and (or (not (is-post? req))
                           validation-errors)
                       (or (j/get-in req [:auth :reset-password-data])
                           (j/get req :user)))
                (direct-to-template res template selector [(or reset-password-form-component component:reset-password-form) req])
                (done))))
          middleware:update-password
          (make-middleware:signed-in-redirect reset-password-redirect)
          (fn [req res] (direct-to-template res template selector [(or simple-message-component component:simple-message) req]))))
