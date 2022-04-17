(ns sitefox.html
  "Functions for wrangling HTML and rendering Reagent components into selectors."
  (:require
   [clojure.test :refer [is]]
   [applied-science.js-interop :as j]
   [reagent.dom.server :refer [render-to-static-markup] :rename {render-to-static-markup r}]
   [sitefox.deps :refer [parse-html]]))

(defn parse "Shorthand for parse-html." [html-string] (parse-html html-string))

(defn $ "Shorthand for CSS style `querySelector` on parsed HTML `template`." [template selector] (.querySelector template selector))

(defn $$ "Shorthand for CSS style `querySelectorAll` on parsed HTML `template`." [template selector] (.querySelectorAll template selector))

(defn render "Shorthand for Reagent's `render-to-static-markup`." [form] (r form))

(defn render-into
  "Render a Reagent component into the chosen element of an HTML document.
  
  * `html-string` is the HTML document to be modified.
  * `selector` is a CSS-style selector such as `#app` or `main`.
  * `reagent-forms` is a valid Reagent component."
  {:test (fn []
           (let [html-string "<html><body><div id='app'></div></body></html>"]
             (is (render-into html-string "body" [:div "Hello, world!"]))
             (is (= (render-into html-string "#app" [:div "Hello, world!"])
                    "<html><body><div id='app'><div>Hello, world!</div></div></body></html>"))
             (is (= (render-into html-string "body" [:main "Hello, world!"])
                    "<html><body><main>Hello, world!</main></body></html>"))
             (is (thrown-with-msg?
                  js/Error #"HTML element not found"
                  (render-into html-string "#bad" [:div "Hello, world!"])))))}
  [html-string selector reagent-forms]
  (let [t (parse-html html-string)
        el ($ t selector)
        rendered (r reagent-forms)]
    (when (not el) (throw (js/Error. (str "HTML element not found: \"" selector "\""))))
    (j/call el :set_content rendered)
    (.toString t)))

(defn direct-to-template
  "Render `selector` `component` Reagent pairs into the HTML `template` string and use the express `res` to send the resulting HTML to the client."
  [res template & selector-component-pairs]
  (.send res
         (reduce
           (fn [html [selector component]]
             (render-into html selector component))
           template
           (partition 2 selector-component-pairs))))

