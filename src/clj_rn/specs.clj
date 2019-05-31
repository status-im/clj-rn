(ns clj-rn.specs
  (:require [clojure.spec.alpha :as s]))

(def not-empty-string?
  (and string? #(not (empty? %))))

;;; config

(s/def :config/main (s/merge (s/keys :req-un [:config/name
                                              :config/builds]
                                     :opt-un [:config/js-modules
                                              :config/desktop-modules
                                              :config/resource-dirs
                                              :config/figwheel-bridge
                                              :config/figwheel-options
                                              :config/run-options])
                             (s/map-of #{:name
                                         :js-modules
                                         :desktop-modules
                                         :resource-dirs
                                         :figwheel-bridge
                                         :figwheel-options
                                         :builds
                                         :run-options} any?)))

(s/def :run-options/ios map?)
(s/def :run-options/android map?)
(s/def :run-options/desktop map?)
(s/def :run-options/default map?)
(s/def :config/run-options (s/merge (s/keys :opt-un [:run-options/ios
                                                     :run-options/android
                                                     :run-options/desktop
                                                     :run-options/default])
                                    (s/map-of #{:ios :android :desktop :default} any?)))

(s/def :config/name (and not-empty-string? #(re-matches #"^[A-Z][A-Za-z0-9]+$" %)))
(s/def :config/js-modules (s/coll-of not-empty-string?))
(s/def :config/desktop-modules (s/coll-of not-empty-string?))
(s/def :config/resource-dirs (s/coll-of (s/or not-empty-string? map?)))
(s/def :config/figwheel-bridge not-empty-string?)
(s/def :config/figwheel-options map?)
(s/def :config/builds (s/coll-of map?))
