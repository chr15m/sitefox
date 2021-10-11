(ns authexample.server
  (:require
    ["fs" :as fs]
    [applied-science.js-interop :as j]
    [promesa.core :as p]
    [sitefox.html :refer [render-into]]
    [sitefox.web :as web]
    [sitefox.reloader :refer [reloader]]
    [sitefox.logging :refer [bind-console-to-file]]))

(bind-console-to-file)

(def template (fs/readFileSync "index.html"))

(defn view:sign-in [csrf-token]
  [:div
   [:h1 "Sign in"]
   [:div.card
    [:form {:method "POST"}
     [:p [:input.fit {:name "email" :placeholder "Your email"}]]
     [:p [:input.fit {:name "password" :placeholder "password"}]]
     [:input {:name "_csrf" :type "hidden" :value csrf-token}]
     [:div.side-by-side
      [:span
       [:a {:href "/sign-up"} "Sign up"]
       [:br]
       [:a {:href "/forgot-password"} "Forgot password"]]
      [:button.primary {:type "submit"} "Sign in"]]]]])

(defn view:sign-up [csrf-token]
  [:div
   [:h1 "Sign up"]
   [:div.card
    [:p "Enter your email to sign up."]
    [:form {:method "POST"}
     [:p [:input.fit {:name "email" :placeholder "Your email"}]]
     [:input {:name "_csrf" :type "hidden" :value csrf-token}]
     [:div.side-by-side
      [:span]
      [:button.primary {:type "submit"} "Sign in"]]]]])

(defn view:forgot-password [csrf-token])

(defn is-post [req]
  (= (aget req "method") "POST"))

(defn serve-form [req res view]
  (.send res (render-into template "main" [view (j/call req :csrfToken)])))

(defn sign-in [req res])

(defn sign-up [req res])

(defn forgot-password [req res])

(defn handle-csrf-error [err req res n]
  (if (= (aget err "code") "EBADCSRFTOKEN")
    (-> res
        (.status 403)
        (.send (render-into template "main" [:div.warning "The form was tampered with."])))
    (n err)))

#_ (-> (mail/send-email
        "test@example.com"
        "test@example.com"
        "Form results."
        :text (str
                "Here is the result of the form:\n\n"
                (js/JSON.stringify data nil 2)))
      (.then #(js/console.log "Email: " (aget % "url"))))

(defn setup-routes [app]
  (web/reset-routes app)
  (web/static-folder app "/css" "node_modules/minimal-stylesheet/")
  (j/call app :use handle-csrf-error)
  (j/call app :get "/" (fn [req res] (serve-form req res view:sign-in)))
  (j/call app :post "/" sign-in)
  (j/call app :get "/sign-up" (fn [req res] (serve-form req res view:sign-up)))
  (j/call app :post "/sign-up" sign-up))

(defn main! []
  (p/let [[app host port] (web/start)]
    (reloader (partial #'setup-routes app))
    (setup-routes app)
    (println "Server main.")))

