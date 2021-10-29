Set up a ClojureScript web server in one command using
[sitefox](https://github.com/chr15m/sitefox)
and [nbb](https://github.com/borkdude/nbb).

```shell
npm create sitefox-nbb mywebsite
cd mywebsite
npm run serve
```

Then open `server.cljs` in your editor and start hacking.
When `server.cljs` is modified the server will automatically reload routes. 👍

To serve the live version without file watcher reloading:

```
npm run serve-live
```

See the [sitefox documentation](https://github.com/chr15m/sitefox#batteries-included) for details on what you can do next.
