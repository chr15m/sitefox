(ns sitefox.docs
  (:require [codox.main :refer [generate-docs]]))

(generate-docs
  {:source-paths ["src"]
   :name "Sitefox"
   ;:namespaces [sitefox.web]
   ;:namespaces [sitefox.ui]
   ;:namespaces [sitefox.hello]
   ;:namespaces [sitefox.web sitefox.db sitefox.html sitefox.mail sitefox.logging sitefox.util sitefox.ui sitefox.reloader]
   :source-uri "https://github.com/chr15m/sitefox/blob/master/{filepath}#L{line}"
   :metadata {:doc/format :markdown}
   :output-path "docs"
   :reader :clojurescript
   :language :clojurescript
   :doc-files ["docs/README.md"]})
