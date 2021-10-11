(ns update-deps
  (:require
    ["fs" :as fs]
    [clojure.edn :as edn]
    [clojure.pprint :refer [pprint]]))

(let [package (js/require "../package.json")
      js-deps (js->clj (aget package "dependencies"))
      deps (edn/read-string (fs/readFileSync "src/deps.cljs" "utf8"))
      deps-updated (assoc deps :npm-deps js-deps)]
  (binding [*print-fn* (fn [s]
                         (fs/writeFileSync "src/deps.cljs" s))]
    (pprint deps-updated)))
