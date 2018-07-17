(ns clj-rn.main
  (:require [clj-rn.core :as core]
            [clj-rn.shell :as shell]
            [clj-rn.utils :as utils]
            [clojure.string :as str]
            [clojure.tools.cli :as cli]))

;;; Helper functions.

(def cli-tasks-info
  {:enable-source-maps {:desc "Patches RN packager to server *.map files from filesystem, so that chrome can download them."}
   :rebuild-index      {:desc "Generate index.*.js for development with figwheel"}
   :watch              {:desc "Start development"}
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

(defn print-and-exit [msg]
  (println msg)
  (System/exit 1))

(defn parse-cli-options [args options]
  (let [{:keys [options errors summary]} (cli/parse-opts args options)]
    (cond
      (:help options)     (print-and-exit summary)
      (not (nil? errors)) (print-and-exit errors)
      :else               options)))

(def common-options
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
    :validate [#(some? (#{:simulator :real} %)) "Must be \"simulator\", or \"real\""]]])

(defn with-common-options
  [options]
  (concat common-options options [["-h" "--help"]]))

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

;;; :rebuild-index task

(def rebuild-index-task-options
  (with-common-options
    [[nil "--figwheel-port PORT" "Figwheel Port"
      :id       :figwheel-port
      :default  3449
      :parse-fn #(Integer/parseInt %)
      :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]]))

(defmethod task :rebuild-index [[_ & args]]
  (let [{:keys [build-ids
                android-device
                ios-device
                figwheel-port]}   (parse-cli-options args rebuild-index-task-options)
        hosts-map                 {:android (core/resolve-dev-host :android android-device)
                                   :ios     (core/resolve-dev-host :ios ios-device)}]
    (core/write-env-dev hosts-map figwheel-port)
    (core/rebuild-index-files build-ids hosts-map)))

;;; :watch task

(def watch-task-options
  (with-common-options
    [[nil "--[no-]start-app" "Start `react-native run-*` or not" :default true]
     [nil "--[no-]start-figwheel" "Start Figwheel or not" :default true]
     [nil "--[no-]start-cljs-repl" "Start cljs repl or not" :default true]]))

(defmethod task :watch [[_ & args]]
  (let [options (parse-cli-options args watch-task-options)]
    (core/watch options)))

;;; :help task

(defmethod task :help [_]
  (show-help)
  (System/exit 1))

(defn -main [& args]
  (task args))
