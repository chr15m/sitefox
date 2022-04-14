(ns generate-docs
  (:require
    ["fs" :as fs]
    ["child_process" :refer [execSync]]))

(let [readme (-> (fs/readFileSync "README.md")
                 .toString
                 (.replace "docs/" ""))]
  (fs/writeFileSync "docs/README.md" (str "# Readme\n\n" readme))
  (fs/renameSync "src/sitefox/deps.cljc" "x")
  (fs/writeFileSync "src/sitefox/deps.cljc" "(ns sitefox.deps)\n")
  ; TODO: print stderr
  (execSync "clojure -X:codox")
  (fs/rmSync "docs/README.md")
  (fs/rmSync "src/sitefox/deps.cljc")
  (fs/renameSync "x" "src/sitefox/deps.cljc"))
