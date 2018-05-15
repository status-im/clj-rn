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

## Thanks

Special thanks to @psdp for the initial implementation. Awesome job!

## License

Licensed under the [Mozilla Public License v2.0](LICENSE.md)
