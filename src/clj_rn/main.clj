(ns clj-rn.main
  (:require [clj-rn.core :as core]
            [clj-rn.shell :as shell]
            clj-rn.specs
            [clj-rn.utils :as utils]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as spec]
            [clojure.string :as str]
            [clojure.tools.cli :as cli]))

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

(def cli-tasks-info
  {:enable-source-maps {:desc "Patches RN packager to server *.map files from filesystem, so that chrome can download them."}
   :rebuild-index      {:desc "Generate index.*.js for development with figwheel"}
   :help               {:desc "Show this help"}})

(defn- show-help []
  (doseq [[task {:keys [desc usage]}] cli-tasks-info]
    (println (format (str shell/color-yellow "%-20s" shell/color-reset
                          shell/color-green "%s" shell/color-reset)
                     (name task) desc))
    (when usage
      (println)
      (->> usage
           (map #(str "  " %))
           (str/join "\n")
           println)
      (println))))

(defn parse-cli-opts [args opts]
  (let [{:keys [options errors summary]} (cli/parse-opts args opts)]
    (cond
      (:help options)     (do (println summary)
                              (System/exit 1))
      (not (nil? errors)) (utils/log-err errors)
      :else               options)))

;;; Task dispatching

(defmulti task (comp keyword str/lower-case #(or % "") first))

(defmethod task :default [args]
  (println (format "Unknown or missing task. Choose one of: %s\n"
                   (->> cli-tasks-info
                        keys
                        (map name)
                        (interpose ", ")
                        (apply str))))
  (show-help)
  (System/exit 1))

;;; :enable-source-maps task

(defmethod task :enable-source-maps [_]
  (core/enable-source-maps))

(def rebuild-index-task-opts
  [["-p" "--platform BUILD-IDS" "Platform Build IDs <android|ios>"
    :id       :build-ids
    :default  [:android]
    :parse-fn #(->> (.split % ",")
                    (map (comp keyword str/lower-case str/trim))
                    vec)
    :validate [(fn [build-ids] (every? #(some? (#{:android :ios} %)) build-ids)) "Must be \"android\", and/or \"ios\""]]
   ["-a" "--android-device TYPE" "Android Device Type <avd|genymotion|real>"
    :id       :android-device
    :parse-fn #(keyword (str/lower-case %))
    :validate [#(some? (#{:avd :genymotion :real} %)) "Must be \"avd\", \"genymotion\", or \"real\""]]
   ["-i" "--ios-device TYPE" "iOS Device Type <simulator|real>"
    :id       :ios-device
    :parse-fn #(keyword (str/lower-case %))
    :validate [#(some? (#{:simulator :real} %)) "Must be \"simulator\", or \"real\""]]
   [nil "--figwheel-port PORT" "Figwheel Port"
    :id       :figwheel-port
    :default  3449
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]
   ["-h" "--help"]])

(defmethod task :rebuild-index [[_ & args]]
  (let [{:keys [build-ids
                android-device
                ios-device
                figwheel-port]}   (parse-cli-opts args rebuild-index-task-opts)
        hosts-map                 {:android (core/resolve-dev-host :android android-device)
                                   :ios     (core/resolve-dev-host :ios ios-device)}
        {:keys [name
                js-modules
                resource-dirs
                figwheel-bridge]} (get-main-config)]
    (when-not figwheel-bridge
     (core/copy-figwheel-bridge))
    (core/write-env-dev hosts-map figwheel-port)
    (doseq [build-id build-ids
            :let     [host-ip       (get hosts-map build-id)
                      platform-name (if (= build-id :ios) "iOS" "Android")]]
      (core/rebuild-index-js build-id {:app-name        name
                                       :host-ip         host-ip
                                       :js-modules      js-modules
                                       :resource-dirs   resource-dirs
                                       :figwheel-bridge figwheel-bridge})
      (when (= build-id :ios)
        (core/update-ios-rct-web-socket-executor host-ip)
        (utils/log "Host in RCTWebSocketExecutor.m was updated"))
      (utils/log (format "Dev server host for %s: %s" platform-name host-ip)))))

;;; Help

(defmethod task :help [_]
  (show-help)
  (System/exit 1))

(defn -main [& args]
  (task args))
