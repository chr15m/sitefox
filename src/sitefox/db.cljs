(ns sitefox.db
  (:require
    [sitefox.util :refer [env]]
    ["keyv" :as Keyv]))

(def database-url (env "DATABASE" "sqlite://./database.sqlite"))

(defn kv [kv-ns]
  (Keyv. database-url #js {:namespace kv-ns}))

(defn client []
  (->
    (Keyv. database-url)
    (aget "opts" "store")))
