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
      ["express-session$default" :as r-session :refer [Store]]
      :cljs
      ["express-session" :as r-session :refer [Store]])
   #?(:org.babashka/nbb
      ["morgan$default" :as r-morgan]
      :cljs
      ["morgan" :as r-morgan])
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
     (def session-store Store)
     (def morgan r-morgan)
     (def Keyv r-Keyv)))
