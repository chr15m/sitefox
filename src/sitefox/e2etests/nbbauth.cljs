(ns sitefox.e2etests.nbbauth
  (:require
    ;[clojure.test :as t :refer [deftest is async]]
    #_ [clojure.string :as str]
    [applied-science.js-interop :as j]
    [promesa.core :as p]
    ["child_process" :refer [spawn spawnSync]]
    ["tree-kill$default" :as kill]
    ;["playwright$default" :as pw]
    ["wait-port$default" :as wait-for-port]
    #_ [sitefox.db :refer [client]]))

;(def browser-type pw/chromium)

(def rig (atom {}))

(def log (j/call-in js/console [:log :bind] js/console " ---> "))

(defn run-server [path server-command port]
  ; first run npm init in the folder
  (log "Installing server deps.")
  (spawnSync "npm i" #js {:cwd path
                          :stdio "inherit"
                          :shell true})
  ; now run the server
  (log "Spawning server.")
  (p/let [server (spawn server-command #js {:cwd path
                                            :stdio "inherit"
                                            :shell true
                                            :detach true})
          port-info (wait-for-port #js {:host "127.0.0.1" :port port})
          pid (j/get server :pid)]
    (log "Port found, server running with PID" pid)
    (j/assoc! port-info :process server
          :kill (fn [] (kill pid)))))

(p/let [server (run-server "examples/nbb" "npm i; npm run serve" 8000)]
  (p/delay 3000)
  (print "Waited 3 seconds, aborting")
  ;(j/call-in server [:process :kill] "SIGINT")
  ;(j/call-in server [:controller :abort])
  (j/call server :kill)
  )

#_ (t/use-fixtures
  :once
  {:before
   #(async
      done
      (p/let [;db (client)
              ;[cwd server] (if (not use-dev-server) (run-server) ["." nil])
              port 8000
              browser (.launch pw/chromium #js {:headless false})
              context (.newContext browser)
              page (.newPage context)]
        #_ (when (not server)
          (print "Using existing server - make sure you're running the test db.\n"
                 "DATABASE_URL=sqlite://./tests.sqlite make watch"))
        ; reset the test db
        #_  (when (str/includes? (aget js/process.env "DATABASE_URL") "/tests.sqlite")
          (print "Deleting the test database.")
          (.query db "delete from keyv;"))
        (reset! rig {:page page :browser browser
                     ;:server server :cwd cwd
                     :base-url (str "http://localhost:" port)})
        (wait-for-port #js {:host "localhost" :port port})
        (done)))
   :after
   #(async
      done
      (p/let [{:keys [browser server]} @rig]
        (.close browser)
        (when server
          (.kill server))
        (done)))})

#_ (deftest nbbauth
  (t/testing "Auth against Sitefox on nbb tests."
    (async done
           (p/let [{:keys [page base-url]} @rig]
             (.goto page base-url)
             (is true)
             (p/delay 10000)
             (done)))))

#_ (t/run-tests 'sitefox.e2etests.nbbauth)
