(ns sitefox.deps
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
      ["keyv" :as r-Keyv])))

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
     (def Keyv r-Keyv)))
