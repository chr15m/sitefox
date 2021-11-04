(ns sitefox.util
  (:require
    ["json-stringify-safe" :as json-stringify-safe]
    [sitefox.logging :refer [bail]]))

(defn env [k & [default]]
  (or (aget js/process.env k) default))

(defn env-required [k & [msg]]
  (or (env k) (bail (or msg (str "Required environment variable is missing:" k)))))

(defn error-to-json [err]
  (let [e (js/JSON.parse (json-stringify-safe err))]
    (aset e "message" (str err))
    #js {:error e}))

(defn btoa [s]
  (-> s js/Buffer. (.toString "base64")))
