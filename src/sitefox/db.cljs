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

#_ (defn ls [kv-ns & [pre db]]
  (->
    (.query (or db (client)) (str "select * from keyv where key like '" kv-ns ":" (or pre "") "%'"))
    (.then #(.json res (.map % (fn [row]
                                 (let [k (aget row "key")
                                       v (aget (js/JSON.parse (aget row "value")) "value")]
                                   (aset v "kind" k)
                                   v)))))))
