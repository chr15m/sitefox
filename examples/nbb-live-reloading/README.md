Create a sitefox server using [nbb](https://github.com/borkdude/nbb) with live-reloading via [browser-sync](https://github.com/BrowserSync/browser-sync).

To test this out:

 1. Clone this repo.
 2. Go into `examples/nbb`.
 3. `npm i`
 4. `npm run serve`

This will run `server.cljs` using nbb. Browser sync will automatically open a tab with your app loaded. If you edit `server.cljs` the page will be refreshed. If you edit `css/style.css` the styles will be hot-loaded without refreshing the page.
