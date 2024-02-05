(ns sitefoxtest.e2etests
  (:require
    [clojure.test :as t :refer [deftest is async]]
    [clojure.string :refer [includes?]]
    [applied-science.js-interop :as j]
    [promesa.core :as p]
    ["child_process" :refer [spawn spawnSync]]
    ["process" :refer [env]]
    ["tree-kill$default" :as kill]
    ["playwright$default" :as pw]
    ["wait-port$default" :as wait-for-port]
    #_ [sitefox.db :refer [client]]))

;(def browser-type pw/chromium)

(def base-url "http://localhost:8000")

(def log (j/call-in js/console [:log :bind] js/console " ---> "))
(def log-listeners (atom #{}))

(defn run-server [path server-command port]
  ; first run npm init in the folder
  (log "Installing server deps.")
  (spawnSync "npm i --no-save" #js {:cwd path
                                    :stdio "inherit"
                                    :shell true})
  ; now run the server
  (log "Spawning server.")
  (p/let [server (spawn server-command #js {:cwd path
                                            ;:stdio "inherit"
                                            :shell true
                                            :detach true})
          port-info (wait-for-port #js {:host "127.0.0.1" :port port})
          pid (j/get server :pid)]
    (j/call-in server [:stdout :on] "data"
               (fn [data]
                 (doseq [[re-string listener-fn] @log-listeners]
                   (let [matches (.match (.toString data) (js/RegExp. re-string "s"))]
                     (when matches
                       (listener-fn matches)
                       (swap! log-listeners disj [re-string listener-fn]))))
                 #_ (log "Server:" (.toString data))))
    (log "Port found, server running with PID" pid)
    (j/assoc! port-info
              :process server
              :kill (p/promisify #(kill pid "SIGTERM" %)))))

(defn listen-to-log [re-string]
  (js/Promise.
    (fn [res]
      (swap! log-listeners conj [re-string res]))))

(defn get-browser []
  (p/let [browser (.launch pw/chromium #js {:headless (nil? (j/get env "CI")) :timeout 3000})
          context (.newContext browser)
          page (.newPage context)]
    (.setDefaultTimeout page 3000)
    {:browser browser :context context :page page}))

(defn catch-fail [err done server & [browser]]
  (is (nil? err) (str "Error in test: " (.toString err)))
  (j/call server :kill)
  (when browser
    (.close browser))
  (done))

(deftest basic-site-test
  (t/testing "Basic test of Sitefox on nbb."
    (async done
           (p/let [_ (log "Test: basic-site-test")
                   server (run-server "examples/nbb" "npm i --no-save; npm run serve" 8000)
                   res (js/fetch "http://localhost:8000/")
                   text (.text res)]
             (p/catch
               (p/do!
                 (is (j/get-in server [:process :pid]) "Server is running?")
                 (is (j/get server :open) "Server port is open?")
                 (is (j/get res :ok) "Was server response ok?")
                 (is (includes? text "Hello") "Server response includes 'Hello' text?")
                 (log "Test done. Killing server.")
                 (j/call server :kill)
                 (log "After server.")
                 (done))
               #(catch-fail % done server))))))

(defn check-for-text [page text message]
  (p/let [_ (-> page (.waitForSelector (str ":has-text('" text "')")))
          content (.content page)]
    (is (includes? content text) message)))

(deftest nbb-auth
  (t/testing "Auth against Sitefox on nbb tests."
    (async done
           (p/let [_ (log "Test: nbb-auth")
                   server (run-server "examples/nbb-auth" "npm i --no-save; npm run serve" 8000)
                   {:keys [page browser]} (get-browser)]
             (p/catch
               (p/do!
                 (.goto page base-url)
                 ; click "Sign in"
                 (-> page (.locator "a[href='/auth/sign-in']") .click)
                 ; click "Sign up"
                 (-> page (.locator "a[href='/auth/sign-up']") .click)
                 ; fill out details and sign up
                 (-> page (.locator "input[name='email']") (.fill "goober@example.com"))
                 (-> page (.locator "input[name='email2']") (.fill "goober@example.com"))
                 (-> page (.locator "input[name='password']") (.fill "tester"))
                 (-> page (.locator "input[name='password2']") (.fill "tester"))

                 (p/let [[log-items] (p/all [(listen-to-log "verify-url (?<url>http.*?)[\n$]")
                                             (-> page (.locator "button:has-text('Sign up')") .click)])
                         url (j/get-in log-items [:groups :url])]
                   ; click on the verification link
                   (.goto page url)
                   (check-for-text page "Signed in" "User is correctly signed in after verification.")
                   (.goto page base-url)
                   (check-for-text page "Signed in" "User is correctly signed in on homepage."))

                 (log "Closing resources.")
                 (j/call server :kill)
                 (.close browser)
                 (log "Resources closed.")
                 (done))
               #(catch-fail % done server browser))))))

#_ (defmethod cljs.test/report [:cljs.test/default :end-run-tests] [m]
     (print "report")
     (if (cljs.test/successful? m)
       (println "Success!")
       (println "FAIL")))

(t/run-tests *ns*)