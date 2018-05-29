(ns clj-rn.core
  (:require [clj-rn.utils :as utils]
            [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [clojure.string :as str]))

(def debug-host-rx #"host]\s+\?:\s+@\".*\";")

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

(defn write-env-dev [hosts-map & [figwheel-port]]
  (-> "(ns env.config)\n\n(def figwheel-urls %s)"
      (format (-> (into {}
                        (for [[platform host] hosts-map]
                          {platform (str "ws://" host ":" (or figwheel-port 3449) "/figwheel-ws")}))
                  pprint/pprint
                  with-out-str))
      ((partial spit "env/dev/env/config.cljs"))))

(defn rebuild-index-js [platform {:keys [app-name host-ip js-modules resource-dirs figwheel-bridge]}]
  (let [modules     (->> (for [dir resource-dirs]
                           (->> (file-seq (io/file dir))
                                (filter #(and (not (re-find #"DS_Store" (str %)))
                                              (.isFile %)))
                                (map (fn [file] (when-let [unix-path (->> file .toPath .iterator iterator-seq (str/join "/"))]
                                                  (-> (str "./" unix-path)
                                                      (str/replace "\\" "/")
                                                      (str/replace "@2x" "")
                                                      (str/replace "@3x" "")))))))
                         flatten
                         (concat js-modules ["react" "react-native" "create-react-class"])
                         distinct)
        modules-map (zipmap modules modules)
        target-file (str "index." (if (= :ios platform) "ios" "android")  ".js")]
    (try
      (-> "var modules={};%s;\nrequire('%s').withModules(modules).start('%s','%s','%s');"
          (format
           (->> modules-map
                (map (fn [[module path]]
                       (str "modules['" module "']=require('" path "')")))
                (str/join ";"))
           (or figwheel-bridge "./target/figwheel-bridge.js")
           app-name
           (name platform)
           host-ip)
          ((partial spit target-file)))
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
