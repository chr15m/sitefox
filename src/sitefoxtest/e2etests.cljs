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

(def host "localhost")
(def base-url (str "http://" host ":8000"))
(def browser-timeout 60000)

(def log (j/call-in js/console [:log :bind] js/console " ---> "))
(def log-listeners (atom #{}))

(j/assoc! env "BIND_ADDRESS" host)

(defn console-listener [print-fn debug data]
  (when debug
    (print-fn (.toString data)))
  (doseq [[re-string listener-fn] @log-listeners]
    (let [matches (.match (.toString data)
                          (js/RegExp. re-string "s"))]
      (when matches
        (listener-fn matches)
        (swap! log-listeners disj [re-string listener-fn])))))

(defn run-server [path server-command port]
  ; delete any old database hanging around
  (spawnSync "rm -f database.sqlite"
             #js {:cwd path
                  :stdio "inherit"
                  :shell true})
  ; now run the server
  (log "Spawning server.")
  (p/let [server (spawn server-command #js {:cwd path
                                            ;:stdio "inherit"
                                            :shell true
                                            :detach true})]
    (log "Setting up stdout/err listeners.")
    (j/call-in server [:stdout :on] "data" #(console-listener js/console.log (aget env "DEBUG") %))
    (j/call-in server [:stderr :on] "data" #(console-listener js/console.error true %))
    (j/call-in server [:on] "exit" (fn [code]
                                     (when (> code 0)
                                       (js/console.log "Server exited with code: " code)
                                       (js/console.log "Set DEBUG=1 to see stderr.")
                                       (j/call js/process :exit code))))
    (p/let [port-info (wait-for-port #js {:host host :port port})
            pid (j/get server :pid)]
      (log "Port found, server running with PID" pid)
      (j/assoc! port-info
                :process server
                :kill (p/promisify #(kill pid "SIGTERM" %))))))

(defn listen-to-log [re-string]
  (js/Promise.
    (fn [res]
      (swap! log-listeners conj [re-string res]))))

(defn get-browser []
  (p/let [browser (.launch pw/chromium
                           #js {:headless (not (nil? (j/get env "CI")))
                                :timeout browser-timeout})
          context (.newContext browser)
          page (.newPage context)]
    (.setDefaultTimeout page 3000)
    {:browser browser :context context :page page}))

(defn catch-fail [err done server & [browser]]
  (log "Caught test error.")
  (when err
    (.error js/console err))
  (is (nil? err)
      (str "Error in test: " (.toString err)))
  (when (and server (j/get server :kill))
    (j/call server :kill))
  (when browser
    (.close browser))
  (done))

(deftest basic-site-test
  (t/testing "Basic test of Sitefox on nbb."
    (async done
           (p/let [_ (log "Test: basic-site-test")
                   server (run-server "examples/nbb"
                                      "npm i --no-save; npm run serve"
                                      8000)]
             (p/catch
               (p/let [res (js/fetch base-url)
                       text (.text res)]
                 (log "Starting test checks.")
                 (is (j/get-in server [:process :pid])
                     "Server is running?")
                 (is (j/get server :open)
                     "Server port is open?")
                 (is (j/get res :ok)
                     "Was server response ok?")
                 (is (includes? text "Hello")
                     "Server response includes 'Hello' text?")
                 (log "Test done. Killing server.")
                 (j/call server :kill)
                 (log "After server.")
                 (print)
                 (done))
               #(catch-fail % done server))))))

(defn check-for-text [page text message]
  (p/let [_ (-> page (.waitForSelector (str ":has-text('" text "')")))
          content (.content page)]
    (is (includes? content text) message)))

(defn check-for-no-text [page text selector message]
  (p/let [_ (-> page (.waitForSelector selector))
          content (.content page)]
    (is (not (includes? content text)) message)))

(defn check-failed-sign-in
  [page password]
  (p/do!
    ; sign in again
    (.goto page base-url)
    ; click "Sign in"
    (-> page (.locator "a[href='/auth/sign-in']") .click)

    ; do a failed sign in
    (-> page (.locator "input[name='email']")
        (.fill "goober@example.com"))
    (-> page (.locator "input[name='password']")
        (.fill password))
    (-> page
        (.locator "button:has-text('Sign in')")
        .click)
    (check-for-text page "Invalid email or password"
                    "Incorrect password shows a message.")))

(defn sign-out [page]
  ; click "Sign out"
  (-> page (.locator "a[href='/auth/sign-out']") .click)
  (check-for-no-text page "Signed in" "a[href='/auth/sign-in']"
                     "User is correctly signed out on homepage."))

(defn sign-in [page password]
  (p/do!
    ; successful sign in
    (-> page (.locator "input[name='password']")
        (.fill password))
    (-> page
        (.locator "button:has-text('Sign in')")
        .click)
    (check-for-text
      page "Signed in"
      "User is correctly signed in again.")))

(deftest nbb-auth
  (t/testing "Auth against Sitefox on nbb tests."
    (async done
           (p/let [_ (log "Test: nbb-auth")
                   server (run-server "examples/nbb-auth"
                                      "npm i --no-save; npm run serve"
                                      8000)
                   {:keys [page browser]} (get-browser)]
             (p/catch
               (p/do!
                 (.goto page base-url)
                 ; click "Sign in"
                 (-> page (.locator "a[href='/auth/sign-in']") .click)
                 ; click "Sign up"
                 (-> page (.locator "a[href='/auth/sign-up']") .click)
                 ; fill out details and sign up
                 (-> page (.locator "input[name='email']")
                     (.fill "goober@example.com"))
                 (-> page (.locator "input[name='email2']")
                     (.fill "goober@example.com"))
                 (-> page (.locator "input[name='password']")
                     (.fill "tester"))
                 (-> page (.locator "input[name='password2']")
                     (.fill "tester"))

                 (p/let [[log-items]
                         (p/all [(listen-to-log
                                   "verify-url (?<url>http.*?)[\n$]")
                                 (-> page
                                     (.locator "button:has-text('Sign up')")
                                     .click)])
                         url (j/get-in log-items [:groups :url])]
                   ; click on the verification link
                   (.goto page url)
                   (check-for-text
                     page "Signed in"
                     "User is correctly signed in after verification.")
                   (.goto page base-url)
                   (check-for-text
                     page "Signed in"
                     "User is correctly signed in on homepage."))

                 (print "sign out")
                 (sign-out page)

                 (print "check failed sign in")
                 (check-failed-sign-in page "testerwrong")

                 (print "sign in again")
                 (sign-in page "tester")

                 (print "forgot password")
                 ; test forgot password flow
                 (-> page (.locator "a[href='/auth/sign-out']") .click)
                 ; click "Sign in"
                 (-> page (.locator "a[href='/auth/sign-in']") .click)
                 ; click "Forgot password link"
                 (-> page (.locator "a[href='/auth/reset-password']") .click)
                 ; fill out the forgot password form
                 (-> page (.locator "input[name='email']")
                     (.fill "goober@example.com"))

                 (p/let [[log-items]
                         (p/all [(listen-to-log
                                   "verify-url (?<url>http.*?)[\n$]")
                                 (-> page
                                     (.locator
                                       "button:has-text('Reset password')")
                                     .click)])
                         url (j/get-in log-items [:groups :url])]
                   (check-for-text page "Reset password link sent"
                                   "User has been notified of reset email.")
                   (.goto page url))

                 ; enter updated passwords
                 (-> page (.locator "input[name='password']")
                     (.fill "testagain"))
                 (-> page (.locator "input[name='password2']")
                     (.fill "testagain"))
                 (-> page
                     (.locator "button:has-text('Update password')")
                     .click)
                 (check-for-text
                   page "Signed in"
                   "User is correctly signed in again.")

                 ; check sign in fails with old password
                 (sign-out page)
                 (check-failed-sign-in page "tester")
                 ; check successful sign in with new password
                 (sign-in page "testagain")

                 (log "Closing resources.")
                 (j/call server :kill)
                 (.close browser)
                 (log "Resources closed.")
                 (done))
               #(catch-fail % done server browser))))))

(defn check-form-submit [page]
  (p/do!
    ; fill out bad form details
    (-> page (.locator "input[name='name']")
        (.fill "Bilbo"))
    (-> page (.locator "input[name='date']")
        (.fill "2023-06-01"))
    (-> page (.locator "input[name='count']")
        (.fill "7"))
    (-> page (.locator "button[type='submit']") .click)
    (check-for-text
      page "Form complete."
      "Form submits sucessfully.")))

(deftest nbb-forms
  (t/testing "Sitefox forms and CSRF on nbb tests."
    (async done
           (p/let [_ (log "Test: nbb-forms")
                   server (run-server "examples/form-validation"
                                      "npm i --no-save; npm run serve"
                                      8000)
                   {:keys [page context browser]} (get-browser)]
             (p/catch
               (p/do!
                 (.goto page base-url)

                 ; fill out bad form details
                 (-> page (.locator "input[name='name']")
                     (.fill ""))
                 (-> page (.locator "input[name='date']")
                     (.fill "SEPTEMBER THE NOTHING"))
                 (-> page (.locator "input[name='count']")
                     (.fill "XYZ"))

                 (-> page (.locator "button[type='submit']") .click)

                 (check-for-text
                   page "You must enter a name between 5 and 20 characters."
                   "Name validation failed successfully.")

                 (check-for-text
                   page "You must enter a valid date in YYYY-MM-DD format."
                   "Date validation failed successfully.")

                 (check-for-text
                   page "You must enter a quantity between 5 and 10."
                   "Count validation failed successfully.")

                 ; fill out form correctly

                 (.goto page base-url)

                 (check-form-submit page)

                 ; fill out form correctly but fail csrf
                 (.goto page base-url)

                 ; fill out bad form details
                 (-> page (.locator "input[name='name']")
                     (.fill "Bilbo"))
                 (-> page (.locator "input[name='date']")
                     (.fill "2023-06-01"))
                 (-> page (.locator "input[name='count']")
                     (.fill "7"))
                 ; modify csrf field
                 (-> page
                     (.evaluate "document.querySelector('input[name=\"_csrf\"]').value='BOGUS'"))

                 (-> page (.locator "button[type='submit']") .click)

                 (check-for-text
                   page "The form was tampered with."
                   "CSRF error caught sucessfully.")

                 ; sanity check by running multiple CSRF checks in parallel forms

                 ; open another form to get a new csrf token
                 (p/let [page2 (.newPage context)]
                   (.goto page2 base-url)
                   ; then reload the first page to get a new token
                   (.goto page (str base-url "?hello=1"))
                   ; check the second tab can still successfully submit
                   (check-form-submit page2)
                   ; close the page2 tab
                   (.close page2))

                 ; Check that fetch requests still work with CSRF protection in place
                 (p/all [; click the ajax POST submit button
                         (-> page (.locator "button#ajax") .click)
                         ; wait for waitForResponse fetch request loading to complete
                         (.waitForResponse page #(.includes (.url %) "/ajax"))])
                 (check-for-text
                   page "received!"
                   "The POST fetch request failed.")

                 (log "Closing resources.")
                 (j/call server :kill)
                 (.close browser)
                 (log "Resources closed.")
                 (done))
               #(catch-fail % done server browser))))))

(deftest basic-compiled-shadow-test
  (t/testing "Basic test of Sitefox on compiled shadow-cljs."
    (async done
           (p/let [_ (log "Test: basic-compiled-shadow-test")
                   server (run-server "examples/shadow-cljs"
                                      "npm i --no-save; npm run serve-live"
                                      8000)]
             (p/catch
               (p/let [res (js/fetch base-url)
                       text (.text res)]
                 (log "Starting test checks.")
                 (is (j/get-in server [:process :pid])
                     "Server is running?")
                 (is (j/get server :open)
                     "Server port is open?")
                 (is (j/get res :ok)
                     "Was server response ok?")
                 (is (includes? text "Hello")
                     "Server response includes 'Hello' text?")
                 (log "Test done. Killing server.")
                 (j/call server :kill)
                 (log "After server.")
                 (print)
                 (done))
               #(catch-fail % done server))))))

(deftest basic-shadow-dev-test
  (t/testing "Basic test of Sitefox on shadow-cljs."
    (async done
           (p/let [_ (log "Test: basic-shadow-dev-test")
                   server (run-server "examples/shadow-cljs"
                                      "npm i --no-save; npm run serve"
                                      8000)]
             (p/catch
               (p/let [res (js/fetch base-url)
                       text (.text res)]
                 (log "Starting test checks.")
                 (is (j/get-in server [:process :pid])
                     "Server is running?")
                 (is (j/get server :open)
                     "Server port is open?")
                 (is (j/get res :ok)
                     "Was server response ok?")
                 (is (includes? text "Hello")
                     "Server response includes 'Hello' text?")
                 (log "Test done. Killing server.")
                 (j/call server :kill)
                 (log "After server.")
                 (print)
                 (done))
               #(catch-fail % done server))))))

;(t/run-test sitefoxtest.e2etests/nbb-forms)
(t/run-tests *ns*)
