(ns NAME.server 
  (:require
    ["fs" :as fs]
    [applied-science.js-interop :as j]
    [promesa.core :as p]
    [sitefox.util :refer [env]]
    [sitefox.html :refer [render-into]]
    [sitefox.web :as web]
    [sitefox.logging :refer [bind-console-to-file]]))

(bind-console-to-file)

(defonce server (atom nil))

(def template (fs/readFileSync "public/index.html"))

(defn my-page []
  [:main
   [:h1 "My page"]
   [:p "This is a server rendered page."]
   [:p [:a {:href "/"} "Return to the app"]]])

(defn api-example [_req res]
  (.json res (clj->js {:question 42})))

(defn setup-routes [app]
  (web/reset-routes app)
  (j/call app :get "/mypage" #(.send %2 (render-into template "body" [my-page])))
  (j/call app :get "/api/example.json" api-example)
  (web/static-folder app "/" "public"))

(defn main! []
  (p/let [[app host port] (web/start)]
    (reset! server app)
    (setup-routes app)
    (println "Serving on" (str "http://" host ":" port))))

(defn ^:dev/after-load reload []
  (js/console.log "Reloading.")
  (setup-routes @server))
