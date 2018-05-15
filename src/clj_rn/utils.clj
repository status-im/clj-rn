(ns clj-rn.utils
  (:require [clj-rn.shell :as shell]))

(defn colorizer [c]
  (fn [& args]
    (str c (apply str args) shell/color-reset)))

(defn println-colorized [message color]
  (println ((colorizer color) message)))

(defn log
  ([s]
   (log s shell/color-green))
  ([s color]
   (println-colorized s color)))

(defn log-err [s]
  (println-colorized s shell/color-red)
  (System/exit 1))
