(ns sitefox.util
  (:require
    [sitefox.logging :refer [bail]]))

(defn env [k & [default]]
  (or (aget js/process.env k) default))

(defn env-required [k]
  (or (env k) (bail "Required environment variable is missing:" k)))

(defn error-to-json [err]
  (let [e (js/JSON.parse (js/JSON.stringify err))]
    (aset e "message" (str err))
    #js {:error e}))

(defn btoa [s]
  (-> s js/Buffer. (.toString "base64")))
