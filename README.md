# CLJ-RN

A utility for building ClojureScript-based React Native apps

## Usage

This small lib provides ability to start development with just one command and some basic functionality that re-natal has: `enable-source-maps` and `rebuild-index`, which is equivalence of re-natal's `enable-source-maps`, `use-*-device`, `use-figwheel`.

```
clj -R:dev -m clj-rn.main help

enable-source-maps  Patches RN packager to server *.map files from filesystem, so that chrome can download them.
rebuild-index      Generate index.*.js for development with figwheel
watch             Start figwheel and cljs repl
help              Show this help
```

`watch` has the following options:
```
clj -R:dev -m clj-rn.main watch -h

-p, --platform BUILD-IDS [:android]  Platform Build IDs <android|ios>
-a, --android-device TYPE           Android Device Type <avd|genymotion|real>
-i, --ios-device TYPE              iOS Device Type <simulator|real>
    --[no-]start-app               Start `react-native run-*` or not
    --[no-]start-figwheel           Start Figwheel or not
    --[no-]start-cljs-repl           Start cljs repl or not
    --[no-]start-bundler           Start React Native Metro bundler or not
-h, --help
```

## Setup re-natal project

1. Create `deps.edn` file and add `clj-rn` as dependency. More about how to use git libraries here https://clojure.org/guides/deps_and_cli#_using_git_libraries
2. Create `clj-rn.conf.edn`. See example [clj-rn.conf.example.edn](clj-rn.conf.example.edn)
3. Run `clj -R:dev -m clj-rn.main watch -p ios -i simulator` to start Figwheel and cljs repl

**Example usage**

Run `watch` task which also starts Figwheel and cljs repl:

```
clj -R:dev -m clj-rn.main watch -p android,ios -a genymotion -i real

```

Start React Native bundler `react-native start`

Now you can run app `react-native run-android` or `react-native run-ios`


## Possible config options

- `:name` Name of your project. The same as in `package.json`
- `:builds` Dev builds. You can copy it from `project.clj`
- `:js-modules` List of all used libraries what is required like this `(js/require "...")` for ios and android platforms
- `:desktop-modules` List of all used libraries what is required like this `(js/require "...")` for desktop platform
- `:resource-dirs` Folders with images and other resources
- `:figwheel-bridge.js"` re-natal adds this file when you init project. If you don't have it, you can skip this options
- `:figwheel-options` options for fegwheel server. List of all options https://github.com/bhauman/lein-figwheel/blob/0f62d6d043abb6156393fd167f6c1496c5439689/sidecar/resources/conf-fig-docs/FigwheelOptions.txt
- `:run-options` Those options will be passed to `react-native run-*`

## Thanks

Special thanks to @psdp for the initial implementation. Awesome job!

## License

Licensed under the [Mozilla Public License v2.0](LICENSE.md)
