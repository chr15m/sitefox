STATIC=public/*.html public/images public/css

all: server.js build

server.js: src/**/*.cljs shadow-cljs.edn
	npx shadow-cljs release server --debug

build: src/**/* $(STATIC)
	mkdir -p build
	cp -LR --preserve=all $(STATIC) build
	npx shadow-cljs release app
	touch build

.PHONY: watch watcher server repl clean

server:
	@echo "waiting for devserver.js to appear."
	@rm -f devserver.js; until [ -f devserver.js -a -d .shadow-cljs ]; do sleep 1; done; echo "devserver.js appeared. starting."
	@sleep 1 && while [ 1 ]; do DEV=1 node devserver.js; sleep 3; echo "restarting devserver.js"; done

watcher:
	npx shadow-cljs watch server app

watch:
	make -j2 watcher server

repl:
	npx shadow-cljs cljs-repl app

clean:
	rm -rf .shadow-cljs devserver.js server.js build node_modules package-lock.json
