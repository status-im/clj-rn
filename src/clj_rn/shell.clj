(ns clj-rn.shell
  (:require [clojure.string :as str]
            [clojure.java.io :as io])
  (:import  java.lang.Runtime))

(def color-reset "\u001b[0m")
(def color-red "\u001b[31m")
(def color-green "\u001b[32m")
(def color-yellow "\u001b[33m")

(defn exec
  [command]
  (let [process (.exec (Runtime/getRuntime) (into-array (str/split command #" ")))]
    (with-open [reader (io/reader (.getInputStream process))]
      (doseq [line (line-seq reader)]
        (println line)))))
