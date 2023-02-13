(ns sitefox.deps
  "This module exists so that shadow-cljs can be used without :target :esm.
  Nbb uses esm and so the $default syntax works there.
  With shadow-cljs in :target :node-script mode the imports can't have $default."
  #?(:cljs
     (:require
       #?(:org.babashka/nbb
          ["express$default" :as r-express]
          :cljs
          ["express" :as r-express])
       #?(:org.babashka/nbb
          ["cookie-parser$default" :as r-cookies]
          :cljs
          ["cookie-parser" :as r-cookies])
       #?(:org.babashka/nbb
          ["body-parser$default" :as r-body-parser]
          :cljs
          ["body-parser" :as r-body-parser])
       #?(:org.babashka/nbb
          ["serve-static$default" :as r-serve-static]
          :cljs
          ["serve-static" :as r-serve-static])
       #?(:org.babashka/nbb
          ["express-session$default" :as r-session]
          :cljs
          ["express-session" :as r-session])
       #?(:org.babashka/nbb
          ["morgan$default" :as r-morgan]
          :cljs
          ["morgan" :as r-morgan])
       #?(:org.babashka/nbb
          ["node-html-parser$default" :refer [parse]]
          :cljs
          ["node-html-parser" :refer [parse]])
       #?(:org.babashka/nbb
          ["csurf$default" :as r-csrf]
          :cljs
          ["csurf" :as r-csrf])
       #?(:org.babashka/nbb
          ["keyv$default" :as r-Keyv]
          :cljs
          ["keyv" :as r-Keyv])
       #?(:org.babashka/nbb
          ["passport$default" :as r-passport]
          :cljs
          ["passport" :as r-passport])
       #?(:org.babashka/nbb
          ["passport-local$default" :as r-LocalStrategy]
          :cljs
          ["passport-local" :as r-LocalStrategy])
       #?(:org.babashka/nbb
          [nbb.core :refer [load-file]])
       #?(:org.babashka/nbb
          ["fs" :refer [readFileSync]])))
       #?(:clj
          (:refer-clojure :exclude [slurp])))

#?(:cljs
   (do
     (def express r-express)
     (def cookies r-cookies)
     (def body-parser r-body-parser)
     (def session r-session)
     (def csrf r-csrf)
     (def serve-static r-serve-static)
     (def morgan r-morgan)
     (def parse-html parse)
     (def Keyv r-Keyv)
     (def passport r-passport)
     (def LocalStrategy r-LocalStrategy)
     (def cljs-loader load-file)))

#?(:clj
   (defmacro inline [file]
     (clojure.core/slurp file))
   :org.babashka/nbb
   (defmacro inline [file]
     (.toString (readFileSync file))))
