(ns clj-rn.core
  (:require [clj-rn.utils :as utils]
            [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [clojure.string :as str]
            [clj-rn.shell :as shell]
            [figwheel-sidecar.repl-api :as ra]
            [clojure.java.io :as io]
            [clj-rn.utils :as utils]
            [clojure.edn :as edn]
            [clojure.spec.alpha :as spec]
            clj-rn.specs))

(def debug-host-rx #"host]\s+\?:\s+@\".*\";")
(def react-native-cli "node_modules/react-native/local-cli/cli.js")

(defn get-main-config []
  (try
    (let [usr-config-file "clj-rn.conf.edn"]
      (when-not (.isFile (io/as-file usr-config-file))
        (throw (Exception. "Please create a clj-rn.conf.edn config file")))
      (let [config (edn/read-string (slurp usr-config-file))]
        (when-not (spec/valid? :config/main config)
          (throw (Exception. "Invalid config.")))
        config))
    (catch Exception e
      (utils/println-colorized (.getMessage e) shell/color-red)
      (System/exit 1))))

(defn copy-resource-file! [resource-path target-path]
  (let [resource-file (io/resource resource-path)
        target-file   (io/file target-path)]
    (with-open [in (io/input-stream resource-file)]
      (io/copy in target-file))))

(defn edit-file-contents! [path replacements-map]
  (as-> (slurp path) $
    (reduce (fn [contents [match replacement]]
              (str/replace contents match replacement))
            $ replacements-map)
    (spit path $)))

(defn enable-source-maps []
  (doseq [path ["node_modules/metro/src/Server/index.js"
                "node_modules/metro-bundler/src/Server/index.js"
                "node_modules/metro-bundler/build/Server/index.js"
                "node_modules/react-native/packager/src/Server/index.js"]]
    (when (.exists (io/as-file path))
      (utils/log (str "Patching file " path  " to serve *.map files."))
      (spit path
            (str/replace (slurp path) "/\\.map$/" "/index\\..*\\.map$/"))))
  (utils/log "Source maps enabled."))

(defn get-lan-ip
  "If .lan-ip file exists, it fetches the ip from the file."
  []
  (if-let [ip (try (slurp ".lan-ip") (catch Exception e nil))]
    (str/trim-newline ip)
    (cond
      (some #{(System/getProperty "os.name")} ["Mac OS X" "Windows 10"])
      (.getHostAddress (java.net.InetAddress/getLocalHost))
      :else
      (->> (java.net.NetworkInterface/getNetworkInterfaces)
           (enumeration-seq)
           (filter #(not (or (str/starts-with? (.getName %) "docker")
                             (str/starts-with? (.getName %) "br-"))))
           (map #(.getInterfaceAddresses %))
           (map
             (fn [ip]
               (seq (filter #(instance?
                               java.net.Inet4Address
                               (.getAddress %))
                            ip))))
           (remove nil?)
           (first)
           (filter #(instance?
                      java.net.Inet4Address
                      (.getAddress %)))
           (first)
           (.getAddress)
           (.getHostAddress)))))

(defmulti resolve-dev-host (fn [platform _] platform))

(defmethod resolve-dev-host :android [_ device-type]
  (case device-type
    :real       "localhost"
    :avd        "10.0.2.2"
    :genymotion "10.0.3.2"
    (get-lan-ip)))

(defmethod resolve-dev-host :ios [_ device-type]
  (if (= device-type :simulator)
    "localhost"
    (get-lan-ip)))

(defmethod resolve-dev-host :desktop [_ _]
  "localhost")

(defn write-env-dev [hosts-map & [figwheel-port]]
  (-> "(ns env.config)\n\n(def figwheel-urls %s)"
      (format (-> (into {}
                        (for [[platform host] hosts-map]
                          {platform (str "ws://" host ":" (or figwheel-port 3449) "/figwheel-ws")}))
                  pprint/pprint
                  with-out-str))
      ((partial spit "env/dev/env/config.cljs"))))

(defn get-modules
  [resource-dirs js-modules]
  (->> (for [dir resource-dirs]
         (let [{:keys [path prefix]} (if (map? dir)
                                       dir
                                       {:path   dir
                                        :prefix "./"})]
          (->> (file-seq (io/file path))
               (filter #(and (not (re-find #"DS_Store" (str %)))
                             (.isFile %)))
               (map (fn [file] (when-let [unix-path (->> file .toPath .iterator iterator-seq (str/join "/"))]
                                 (-> (str prefix unix-path)
                                     (str/replace "\\" "/")
                                     (str/replace "@2x" "")
                                     (str/replace "@3x" ""))))))))
       flatten
       (concat js-modules ["react" "react-native" "create-react-class"])
       distinct))

(defn generate-modules-map
  [modules]
  (str/join ";" (map #(str "modules['" % "']=require('" % "')") modules)))

(defn rebuild-index-js
  [platform {:keys [app-name host-ip js-modules desktop-modules resource-dirs figwheel-bridge]}]
  (let [target-file (str "index." (name platform)  ".js")
        modules (get-modules resource-dirs (if (= platform :desktop) desktop-modules js-modules))]
    (try
      (spit
       target-file
       (format
        "var modules={};%s;\nrequire('%s').withModules(modules).start('%s','%s','%s');"
        (generate-modules-map modules)
        (or (str/replace figwheel-bridge #"\.js" "") "./target/figwheel-bridge")
        app-name
        (name platform)
        host-ip))
      (utils/log (str target-file " was regenerated"))
      (catch Exception e
        (utils/log-err (.getMessage e))))))

(defn update-ios-rct-web-socket-executor [host]
  (edit-file-contents! "node_modules/react-native/Libraries/WebSocket/RCTWebSocketExecutor.m"
                       {debug-host-rx (str "host] ?: @\"" host "\";")}))

(defn copy-figwheel-bridge []
  (io/make-parents "target/.")
  (copy-resource-file! "figwheel-bridge.js" "target/figwheel-bridge.js")
  (utils/log "Copied figwheel-bridge.js"))

(defn rebuild-index-files
  [build-ids hosts-map]
  (let [{:keys [js-modules desktop-modules name resource-dirs figwheel-bridge]} (get-main-config)]
    (when-not figwheel-bridge (copy-figwheel-bridge))
    (doseq [build-id build-ids
            :let [host-ip (get hosts-map build-id)
                  platform-name (case build-id
                                  :ios "iOS"
                                  :android "Android"
                                  :desktop "Desktop"
                                  "Unknown")]]
      (rebuild-index-js build-id {:app-name        name
                                  :host-ip         host-ip
                                  :js-modules      js-modules
                                  :desktop-modules desktop-modules
                                  :resource-dirs   resource-dirs
                                  :figwheel-bridge figwheel-bridge})
      (when (= build-id :ios)
        (update-ios-rct-web-socket-executor host-ip)
        (utils/println-colorized
         "Host in RCTWebSocketExecutor.m was updated"
         shell/color-green))
      (utils/println-colorized
       (format "Dev server host for %s: %s" platform-name host-ip)
       shell/color-green))))

(defn- execute-react-native-cli
  ([command] (execute-react-native-cli command false))
  ([command async?]
   (let [full-command (concat ["node" react-native-cli] command)]
     (if async?
       (future (shell/exec full-command))
       (shell/exec full-command)))))

(defn- get-run-options
  [config platform]
  (reduce
   #(conj %1 (str "--" (name (first %2))) (second %2))
   []
   (merge (get-in config [:run-options :default]) (get-in config [:run-options platform]))))

(defn- run-builds
  [build-ids config]
  (doseq [build-id build-ids]
    (let [run-options (get-run-options config build-id)
          command (cons (str "run-" (name build-id)) run-options)]
      (execute-react-native-cli command))))

(defn- check-react-native-instalation []
  (when-not (.exists (io/file react-native-cli))
    (utils/println-colorized "react-native is not installed locally. If you don't have it in 'package.json' run 'npm install react-native --save'. Note what globally installed react-native won't be used." shell/color-red)
    (System/exit 1)))

(defn watch
  [{:keys [build-ids android-device ios-device start-figwheel start-app start-cljs-repl start-bundler]}]
  (let [{:keys [figwheel-options builds] :as config} (get-main-config)
        hosts-map {:android (resolve-dev-host :android android-device)
                   :ios     (resolve-dev-host :ios ios-device)
                   :desktop (resolve-dev-host :desktop nil)}]
    (check-react-native-instalation)
    (enable-source-maps)
    (write-env-dev hosts-map)
    (rebuild-index-files build-ids hosts-map)
    (when start-bundler
      (execute-react-native-cli ["start"] true))
    (when start-figwheel
      (ra/start-figwheel!
       {:build-ids build-ids
        :all-builds builds
        :figwheel-options figwheel-options}))
    (when start-app (run-builds build-ids config))
    (when (and start-figwheel start-cljs-repl)
      (ra/cljs-repl)
      (when (:nrepl-port figwheel-options)
        (spit ".nrepl-port" (:nrepl-port figwheel-options))))))
