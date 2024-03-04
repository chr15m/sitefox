(ns sitefox.ui
  "Utility functions that can be used in client-side code."
  (:require
    [clojure.test :refer [is]]))

(defn log [& args] (apply js/console.log (clj->js args)) (first args))

(defn check-time-interval [seconds [divisor interval-name]]
  (let [interval (js/Math.floor (/ seconds divisor))]
    (when (> interval 1)
      (str interval " " interval-name))))

(defn time-since
  "Returns a string describing how long ago the date described in `date-string` was."
  {:test (fn []
      (is (= (time-since (- (js/Date.) 18000)) "18 secs"))
      (is (= (time-since (- (js/Date.) (* 1000 60 60 25))) "25 hrs"))
      (is (= (time-since "2013-02-01T00:00:00.000Z" "2014-02-01T00:00:00.000Z") "12 months"))
      (is (= (time-since "2013-02-01T00:00:00.000Z" "2013-02-05T23:15:23.000Z") "4 days"))
      (is (nil? (time-since (+ (js/Date.) (* 1000 60 60 25))))) ; future or same dates return nil
      (is (nil? (time-since "2013-02-01T00:00:00.000Z" "2013-02-01T00:00:00.000Z"))))}
  [date-string & [from-date-string]]
  (let [from-date (if from-date-string (js/Date. from-date-string) (js/Date.))
        since-epoch (-> date-string js/Date.)
        seconds (js/Math.floor (/ (- from-date since-epoch) 1000))]
    (first (remove nil? (map (partial check-time-interval seconds)
                             [[31536000 "years"]
                              [2592000 "months"]
                              [86400 "days"]
                              [3600 "hrs"]
                              [60 "mins"]
                              [1 "secs"]])))))

(defn simple-date-time
  "Returns a simple string representation of the date and time in YYYY-MM-DD HH:MM:SS format."
  {:test (fn []
           (is (= (simple-date-time "2018-01-01T13:17:00.000Z") "2018-01-01 13:17:00"))
           (is (= (simple-date-time "2021-07-15T01:12:33.000Z") "2021-07-15 01:12:33")))}
  [dt]
  (-> dt (.split "T") (.join " ") (.split ".") first))

(def slug-regex (js/RegExp. "[^A-Za-z0-9\\u00C0-\\u1FFF\\u2800-\\uFFFD]+" "g"))

(defn slug
  "Converts `text` to a url-friendly slug."
  {:test (fn []
           (is (= (slug "A calm visit, to the kitchen.") "a-calm-visit-to-the-kitchen"))  
           (is (= (slug "The 99th surprise.") "the-99th-surprise"))
           (is (= (slug "$ goober. it's true.") "goober-it-s-true"))  
           (is (= (slug "* 我爱官话 something") "我爱官话-something")))}
  [text]
  (-> text
      .toString
      .toLowerCase
      (.replace slug-regex " ")
      .trim
      (.replace (js/RegExp. " " "g") "-")))

(defn get-cookie
  "Returns the value of the named cookie from js/document.cookies (or optionally the `cookies` argument)."
  {:test (fn []
           (is (= (get-cookie "one" "one=two") "two"))
           (is (= (get-cookie "three" "three=four; five=six") "four")))}
  [cookie-name & [cookies]]
  (second (re-find (js/RegExp. (str cookie-name "=([^;]+)")) (or cookies (aget js/document "cookie")))))

(defn csrf-token
  "Returns the CSRF token passed by Sitefox in the `XSRF-TOKEN` cookie.
   Pass this value when doing POST requests via ajax:

   ```clojure
   (js/fetch \"/api/endpoint\"
             #js {:method \"POST\"
                  :headers #js {:Content-Type \"application/json\"
                                :XSRF-Token (csrf-token)}
                                :body (js/JSON.stringify (clj->js data))})
   ```

   **Note**: passing the token via client side cookie is now deprecated.
   If you want to re-enable it set the client side environment variable `SEND-CSRF-COOKIE`,
   and then this function will work again.

   See the README for more details: <https://github.com/chr15m/sitefox/#csrf-protection>
  "
  []
  (get-cookie "XSRF-TOKEN"))

(defn fetch-csrf-token
  "Returns a promise which resolves to a string containing the CSRF token fetched from the Sitefox backend server.
  Pass this value as the X-XSRF-TOKEN when doing POST requests via ajax fetch:
  
  ```clojure
    (-> (fetch-csrf-token)
        (.then (fn [token]
                 (js/fetch \"/api/endpoint\"
                           #js {:method \"POST\"
                                :headers #js {:Content-Type \"application/json\"
                                              :X-XSRF-TOKEN token} ; <- use token here
                                :body (js/JSON.stringify (clj->js some-data))}))))
  ```
  "
  []
  (->
    (js/fetch "/_csrf-token")
    (.then #(.json %))))
