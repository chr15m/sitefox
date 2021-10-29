(ns NAME.ui
  (:require [reagent.core :as r]
            [reagent.dom :as rdom]))

(defonce state (r/atom {}))

(defn button-clicked [ev]
  (swap! state update-in [:number] inc))

(defn component-main [state]
  [:div
   [:h1 "NAME"]
   [:p "Welcome to the app!"]
   [:button {:on-click button-clicked} "click me"]
   [:pre (pr-str @state)]  
   [:p [:a {:href "/mypage"} "Static server rendered page."]]
   [:p [:a {:href "/api/example.json"} "JSON API example."]]])

(defn start {:dev/after-load true} []
  (rdom/render [component-main state]
               (js/document.getElementById "app")))

(defn main! []
  (start))
