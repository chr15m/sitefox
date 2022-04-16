(ns update-readme
  (:require
    ["fs" :as fs]
    ["child_process" :refer [execSync]]))

(defn replace-version [tag sha line]
  (let [updated-line (str " {io.github.chr15m/sitefox {:git/tag \"" tag "\" :git/sha \"" sha "\"}}}")]
    (if (> (.indexOf line "git/sha") -1)
      updated-line
      line)))

(let [package (-> (fs/readFileSync "package.json") js/JSON.parse)
      readme (-> (fs/readFileSync "README.md") .toString)
      lines (.split readme "\n")
      sha (-> (execSync "git rev-parse HEAD") .toString .trim)
      version (aget package "version")
      tag (str "v" version)
      updated (.map lines (partial replace-version tag sha))
      readme-new (.join updated "\n")]
  (fs/writeFileSync "README.md" readme-new))
