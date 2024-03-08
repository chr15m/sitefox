Set up a full-stack ClojureScript web server in one command using
[sitefox](https://github.com/chr15m/sitefox)
and [shadow-cljs](https://shadow-cljs.github.io/docs/UsersGuide.html).

```shell
npm create sitefox-shadow-fullstack mywebapp
cd mywebapp
npm install
make watch
```

Then open `src/mywebapp/server.cljs` to edit the back end code.
Open `src/mywebapp/ui.cljs` to edit the front end code.
Code will be automatically reloaded. 👍

# Production

To make a production build into the `build` folder:

```
make
```

To run the production build:

```
cd build && node server.js
```

See the [sitefox documentation](https://github.com/chr15m/sitefox#batteries-included) for details on what you can do next.
