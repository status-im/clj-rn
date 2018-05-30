# CLJ-RN

A utility for building ClojureScript-based React Native apps

## Usage

This small lib provides just some basic functionality that re-natal has: `enable-source-maps` and `rebuild-index`, which is equivalence of re-natal's `enable-source-maps`, `use-*-device`, `use-figwheel`.

```
clj -R:repl -m clj-rn.main help
enable-source-maps  Patches RN packager to server *.map files from filesystem, so that chrome can download them.
rebuild-index       Generate index.*.js for development with figwheel
help                Show this help
```

`rebuild-index` has the following options:
```
clj -R:repl -m clj-rn.main rebuild-index --help
  -p, --platform BUILD-IDS   [:android]  Platform Build IDs <android|ios>
  -a, --android-device TYPE              Android Device Type <avd|genymotion|real>
  -i, --ios-device TYPE                  iOS Device Type <simulator|real>
      --figwheel-port PORT   3449        Figwheel Port
  -h, --help
```

**Example usage**

```
$ clj -R:repl -m clj-rn.main rebuild-index -p android,ios -a genymotion -i real --figwheel-port 3456

```

### Upgrading from an existing project

- If your existing project has a `figwheel-bridge.js` in the root directory, it can be deleted now as `figwheel` task of `clj-rn.main` would create it for you and place it in `target` directory, it is being required by `index.*.js` every time when starting the app with figwheel. Alternatively, you can choose to use your own JS bridge by setting a variable named `:figwheel-bridge` in `clj-rn.conf.edn`, the value should be that of the JS module name. e.g. `./resources/bridge` 
- If you are using the default `figwheel-bridge.js` provided by `clj-rn` (originally from `re-natal`), make sure that `cljsbuild` compiler options for `dev` environment look like this below:
```
{:ios
   {:source-paths ["src"]
    :compiler     {:output-to     "target/ios/index.js"
                   :main          "env.ios.main"
                   :output-dir    "target/ios"
                   :optimizations :none
                   :target        :nodejs}}
   :android
   {:source-paths ["src"]
    :compiler     {:output-to     "target/android/index.js"
                   :main          "env.android.main"
                   :output-dir    "target/android"
                   :optimizations :none
                   :target        :nodejs}}}
```
Note: it also supports `:preloads` and `:closure-defines` options now thanks to the work done by `re-natal`.

- Supported `figwheel-sidecar` version => `0.5.14`.

## Thanks

Special thanks to @psdp for the initial implementation. Awesome job!

## License

Licensed under the [Mozilla Public License v2.0](LICENSE.md)
