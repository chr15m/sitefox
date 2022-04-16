(ns webserver
  (:require
    ["fs" :as fs]
    [promesa.core :as p]
    [applied-science.js-interop :as j]
    [nbb.core :refer [*file*]]
    [sitefox.reloader :refer [nbb-reloader]]
    [sitefox.web :as web]
    [sitefox.html :as html]
    [sitefox.auth :as auth]))

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
    (auth/setup-auth app)
    (auth/setup-email-based-auth app template "main")
    (auth/setup-reset-password app template "main")  
    (.get app "/" (fn [req res] (homepage req res template)))))

(defonce init
  (p/let [self *file*
          [app host port] (web/start)]
    (setup-routes app)
    (nbb-reloader self #(setup-routes app))
    (println "Serving on" (str "http://" host ":" port))))
