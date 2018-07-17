(ns clj-rn.specs
  (:require [clojure.spec.alpha :as s]))

(def not-empty-string?
  (and string? #(not (empty? %))))

;;; config

(s/def :config/main (s/merge (s/keys :req-un [:config/name
                                              :config/builds]
                                     :opt-un [:config/js-modules
                                              :config/resource-dirs
                                              :config/figwheel-bridge
                                              :config/figwheel-options])
                             (s/map-of #{:name
                                         :js-modules
                                         :resource-dirs
                                         :figwheel-bridge
                                         :figwheel-options
                                         :builds} any?)))

(s/def :config/name (and not-empty-string? #(re-matches #"^[A-Z][A-Za-z0-9]+$" %)))
(s/def :config/js-modules (s/coll-of not-empty-string?))
(s/def :config/resource-dirs (s/coll-of not-empty-string?))
(s/def :config/figwheel-bridge not-empty-string?)
(s/def :config/figwheel-options map?)
(s/def :config/builds (s/coll-of map?))
