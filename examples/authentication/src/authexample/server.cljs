(ns authexample.server
  (:require
    ["fs" :as fs]
    [applied-science.js-interop :as j]
    [promesa.core :as p]
    [sitefox.html :as html]
    [sitefox.web :as web]
    [sitefox.logging :refer [bind-console-to-file]]
    [sitefox.auth :as auth]))

(bind-console-to-file)

(defonce server (atom nil))

; user-space calls

(defn homepage [req res template]
  (p/let [user (j/get req :user)]
    (html/direct-to-template
      res template "main"
      [:div
       [:h3 "Homepage"]
       (if user
         [:<>
          [:p "Signed in as " (j/get-in user [:auth :email])]
          [:p [:a {:href (web/get-named-route req "auth:sign-out")} "Sign out"]]]
         [:p [:a {:href (web/get-named-route req "auth:sign-in")} "Sign in"]])])))

(defn setup-routes [app]
  (let [template (fs/readFileSync "index.html")]
    (web/reset-routes app)
    ; (j/call app :use (csrf-handler template "main")) ; view:simple-message
    (web/static-folder app "/css" "node_modules/minimal-stylesheet/")
    (auth/setup-auth app) ; optional argument `sign-out-redirect-url` which defaults to "/".
    (auth/setup-email-based-auth app template "main")
    (auth/setup-reset-password app template "main")
    #_ (setup-email-based-auth app template "main"
                               {:sign-in-redirect "/"
                                :sign-in-form-component component:sign-in-form
                                :sign-up-redirect "/"
                                :sign-up-email-component component:sign-up-email
                                :sign-up-email-subject "Please verify your email"
                                :sign-up-from-address "no-reply@example.com"
                                :sign-up-form-component component:sign-up-form
                                :sign-up-form-done-component component:sign-up-form-done
                                :simple-message-component component:simple-message})
    (j/call app :get "/" (fn [req res] (homepage req res template)))))

(defn main! []
  (p/let [[app host port] (web/start)]
    (reset! server app)
    (setup-routes app)
    (println "Server started on " (str host ":" port))))

(defn ^:dev/after-load reload []
  (js/console.log "Reloading")
  (setup-routes @server))
