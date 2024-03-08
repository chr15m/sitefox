(ns NAME.server 
  (:require
    [applied-science.js-interop :as j]
    [promesa.core :as p]
    ["fs" :as fs]
    ["source-map-support" :as sourcemaps]
    [sitefox.html :refer [render-into]]
    [sitefox.web :as web]
    [sitefox.util :refer [env]]
    [sitefox.logging :refer [bind-console-to-file]]
    [sitefox.tracebacks :refer [install-traceback-handler]]))

(bind-console-to-file)
(sourcemaps/install)
(let [admin-email (env "ADMIN_EMAIL")
      build-id (try (fs/readFileSync "build-id.txt") (catch :default _e "dev"))]
  (when admin-email
    (install-traceback-handler admin-email build-id)))

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
