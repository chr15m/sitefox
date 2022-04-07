(ns sitefox.auth
  (:require
    [cljs.test :refer-macros [is async]]
    [promesa.core :as p]
    ["crypto" :refer [createHash createHmac randomBytes pbkdf2Sync scryptSync createCipheriv createDecipheriv]]
    [sitefox.util :refer [env]]))

(defn make-hmac-token
  "Create an HMAC token to be used for verifying data was generated by the server and is unmodified."
  [secret size & materials]
  (let [s (-> (createHash "sha512") (.update secret) .digest)
        h (createHmac "sha512" s)]
    (doseq [v materials]
      (.update h (str v)))
    (-> h
        (.digest "hex")
        (.slice 0 size))))

(defn hash-password
  "Hash a password for storage in a database.
  If `salt` (hex string) is not supplied it will be generated (it should be passed when comparing but not when generating)."
  {:test (fn []
           (let [[e s] (hash-password "goober")
                 [e2 s2] (hash-password "goober" s)
                 [e3] (hash-password "something" s)
                 [e4] (hash-password "goober")
                 [e5] (hash-password "goober" "deadc0de")]
             (is (= e e2))
             (is (= s s2))
             (is (not= e e3))
             (is (not= e e4))
             (is (not= e e5))))}
  [pw & [salt]]
  (let [salt (if salt
               (js/Buffer.from salt "hex")
               (randomBytes 16))]
    [(.toString (pbkdf2Sync pw salt 310000 32 "sha512") "hex")
     (.toString salt "hex")]))

(defn encrypt-for-transit
  "Encrypts a piece of data for transit using symmetric key cryptography and the server's own secret."
  [materials]
  (js/Promise.
    (fn [res _err]
      (let [secret (env "SECRET" "DEVMODE")
            k (scryptSync secret "encrypt-for-transit" 32)
            iv (randomBytes 16)
            encoded (-> materials clj->js js/JSON.stringify)]
        (when (= secret "DEVMODE") (js/console.error "Warning: env var SECRET is not set."))
        (let [cipher (createCipheriv "aes-256-gcm" k iv #js {:authTagLength 16})
              encrypted-buffer (js/Buffer.concat
                                 #js [(.update cipher encoded) (.final cipher)])
              auth-tag (.getAuthTag cipher)
              assembled (js/Buffer.concat
                          #js [iv encrypted-buffer auth-tag])]
          (-> assembled
              (.toString "base64")
              (.replaceAll "/" "_")
              (.replaceAll "+" "-")
              res))))))

(defn decrypt-for-transit
  "Decrypts a piece of data using symmetric key cryptography and the server's own secret."
  {:test (fn []
           (async done
                  (p/let [vi "some string of data"
                          vx (encrypt-for-transit vi)
                          vo (decrypt-for-transit vx)
                          vi2 (clj->js {:something-else 42 :h [1 2 4]})
                          vx2 (encrypt-for-transit vi2)
                          vo2 (decrypt-for-transit vx2)]
                    (is (= vi vo))
                    (is (= (js->clj vi2) (js->clj vo2)))
                    ; test modified iv
                    (p/let [vi "something"
                            vx (encrypt-for-transit vi)
                            l (.slice vx 0 1)
                            r (.slice vx 2)
                            decrypted (decrypt-for-transit (str l "X" r))]
                      (is (= decrypted nil)))
                    ; test modified encrypted packet
                    (p/let [vi "another thing"
                            vx (encrypt-for-transit vi)
                            l (.slice vx 0 18)
                            r (.slice vx 19)
                            decrypted (decrypt-for-transit (str l "Z" r))]
                      (is (= decrypted nil)))
                    ; test truncated packet / iv
                    (p/let [vi "something else"
                            vx (encrypt-for-transit vi)
                            decrypted (decrypt-for-transit (.slice vx 1))]
                      (is (= decrypted nil))))))}
  [encrypted]
  (js/Promise.
    (fn [res _err]
      (let [secret (env "SECRET" "DEVMODE")
            k (scryptSync secret "encrypt-for-transit" 32)
            data (js/Buffer.from encrypted "base64")
            auth-tag (.slice data -16)
            iv (.slice data 0 16)
            msg (.slice data 16 -16)
            cipher (-> (createDecipheriv "aes-256-gcm" k iv #js {:authTagLength 16})
                       (.setAuthTag auth-tag))
            raw (try (str
                       (.update cipher msg "utf8")
                       (.final cipher "utf8"))
                     (catch :default _e nil))
            decoded (-> raw js/JSON.parse)]
        (res decoded)))))

(defn timestamp-expired?
  "Check if a timestamp (ms) has expired."
  {:test (fn []
           (let [now (-> (js/Date.) (.getTime))]
             (is (timestamp-expired? nil 1))
             (is (timestamp-expired? "BLAH" 1))
             (is (timestamp-expired? (- now 3000) 2000))
             (is (timestamp-expired? now -1))
             (is (not (timestamp-expired? now 500)))
             (is (not (timestamp-expired? now 2000)))
             (is (not (timestamp-expired? (- now 1500) 2000)))
             (is (not (timestamp-expired? (+ now 1000) 2000)))))}
  [time-stamp milliseconds]
  (let [time-stamp (js/parseInt time-stamp)]
    (or
      (js/isNaN time-stamp)
      (< time-stamp (-> (js/Date.) (.getTime) (- milliseconds))))))
