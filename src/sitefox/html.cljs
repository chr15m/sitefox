(ns sitefox.html
  (:require
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
  [html-string selector reagent-forms]
  (let [t (parse-html html-string)
        el ($ t selector)
        rendered (r reagent-forms)]
    (when (not el) (throw (js/Error. (str "HTML element not found: \"" selector "\""))))
    (j/call el :set_content rendered)
    (.toString t)))
