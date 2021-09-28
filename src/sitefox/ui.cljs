(ns sitefox.ui)

(defn log [& args] (apply js/console.log (clj->js args)) (first args))

(defn check-time-interval [seconds [divisor interval-name]]
  (let [interval (js/Math.floor (/ seconds divisor))]
    (when (> interval 1)
      (str interval " " interval-name))))

(defn time-since [date-string]
  (let [since-epoch (-> date-string (js/Date.))
        seconds (js/Math.floor (/ (- (js/Date.) since-epoch) 1000))]
    (first (remove nil? (map (partial check-time-interval seconds)
                             [[31536000 "years"]
                              [2592000 "months"]
                              [86400 "days"]
                              [3600 "hrs"]
                              [60 "mins"]
                              [1 "secs"]])))))

(defn simple-date-time [dt]
  (-> dt (.split "T") (.join " ") (.split ".") first))

(def slug-regex (js/RegExp. "\\W+" "g"))

(defn slug [text]
  (-> text
      .toString
      .toLowerCase
      (.replace slug-regex "-")))
