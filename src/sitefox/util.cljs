(ns sitefox.util
  "Various utilities for server side ClojureScript."
  (:require
    ["json-stringify-safe" :as json-stringify-safe]
    [sitefox.logging :refer [bail]]))

(defn env
  "Returns the environment variable named in k with optional default value."
  [k & [default]]
  (or (aget js/process.env k) default))

(defn env-required
  "Returns the environment variable named in k or exits the process if missing.
  The message printed on exit can be customised in msg."
  [k & [msg]]
  (or (env k) (bail (or msg (str "Required environment variable is missing:" k)))))

(defn error-to-json
  "Convert a JavaScript error into a form that can be returned as JSON data."
  [err]
  (let [e (js/JSON.parse (json-stringify-safe err))]
    (aset e "message" (str err))
    #js {:error e}))

(defn btoa
  "Server side version of browser JavaScript's btoa base64 encoding."
  [s]
  (-> s js/Buffer. (.toString "base64")))
