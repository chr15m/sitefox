#!/usr/bin/env node

const args = process.argv.slice(2);
const execSync = require('child_process').execSync;
const fs = require('fs-extra');
const replace = require('replace-in-file');

const name = args[0];
const dir = name && args[0].replace(/-/g, '_');

if (name) {
  console.log("Creating", name);
  fs.copySync(__dirname + "/template", name);
  fs.moveSync(name + "/gitignore", name + "/.gitignore");
  replace.sync({
    "files": [
      args[0] + "/**/**",
    ],
    "from": "NAME",
    "to": name,
    "countMatches": true,
  });
  console.log("Running npm install");
  execSync("npm install", {"cwd": name, "stdio": ["inherit", "inherit", "inherit"]});
  console.log("\nOk, you are ready to roll:\n$ cd " + name + "\n$ npm run serve\n\nThen edit server.cljs\n");
} else {
  console.log("Usage: " + process.argv[1] + " APP-NAME");
}
