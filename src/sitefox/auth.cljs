(ns sitefox.auth)

(defn login [req res]
  (if (= (aget req.body "password") site-password)
    (do
      (aset req.session "authenticated" true)
      (.json res true))
    (-> res (.status 403) (.json #js {:error "Incorrect password"}))))

(defn logout [req res]
  (aset req.session "authenticated" false)
  (.json res true))
