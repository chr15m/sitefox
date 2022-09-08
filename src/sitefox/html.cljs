(ns sitefox.html
  "Functions for wrangling HTML and rendering Reagent components into selectors."
  (:require
   [clojure.test :refer [is]]
   [applied-science.js-interop :as j]
   [reagent.dom.server :refer [render-to-static-markup] :rename {render-to-static-markup r}]
   [sitefox.deps :refer [parse-html]]))

(defn parse "Shorthand for [`node-html-parser`'s `parse` function](https://www.npmjs.com/package/node-html-parser#usage).
            Returns a dom-like document object that can be manipulated as in the browser."
  [html-string] (parse-html html-string))

(defn $ "Shorthand for CSS style `querySelector` on parsed HTML `element`
        such as the `document` returned by the `parse` function or a sub-element."
  [element selector] (.querySelector element selector))

(defn $$ "Shorthand for CSS style `querySelectorAll` on parsed HTML `element`
         such as the `document` returned by the `parse` function or a sub-element."
  [element selector] (.querySelectorAll element selector))

(defn render "Shorthand for Reagent's `render-to-static-markup`." [form] (r form))

(defn select-apply
  "Parse `template` if it is a string and then run each of selector-applications on it.
  If it is already a `document`-like object it won't be parsed first.
  The `selector-applications` should each be an array like: `[selector document-method-name ...arguments]`.
  For each one the selector will be run and then the method run on the result, with arguments passed to the method.
  The special 'method' `setHTML` expects a Reagent form which will be rendered and `innerHTML` will be set to the result."
  {:test (fn []
           (let [html-string "<html><body><div id='app'></div><span id=one></span><span id=two></span></body></html>"]
             (is (= (select-apply html-string ["#app" :remove])
                    "<html><body><span id=one></span><span id=two></span></body></html>"))
             (is (= (select-apply html-string ["#app" :setHTML [:p "My message."]])
                    "<html><body><div id='app'><p>My message.</p></div><span id=one></span><span id=two></span></body></html>"))
             (is (= (select-apply html-string ["span" :setHTML "In span."] ["#app" :remove])
                    "<html><body><span id=one>In span.</span><span id=two>In span.</span></body></html>"))
             (is (= (select-apply html-string ["span" :setAttribute "data-thing" 42] ["#app" :remove])
                    "<html><body><span id=\"one\" data-thing=\"42\"></span><span id=\"two\" data-thing=\"42\"></span></body></html>"))
             (is html-string)))}
  [template & selector-application-pairs]
  (let [string-template (= (type template) js/String)
        document (if string-template (parse-html template) template)]
    (doseq [[selector method-name & args] selector-application-pairs]
      (doseq [el ($$ document selector)]
        (if (= (keyword method-name) :setHTML)
          (j/assoc! el :innerHTML (render (first args)))
          (j/apply el method-name (clj->js args)))))
    (if string-template
      (j/call document :toString)
      document)))

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

