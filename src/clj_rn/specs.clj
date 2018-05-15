(ns clj-rn.specs
  (:require [clojure.spec.alpha :as s]))

(def not-empty-string?
  (and string? #(not (empty? %))))

;;; config

(s/def :config/main (s/merge (s/keys :req-un [:config/name]
                                     :opt-un [:config/js-modules :config/resource-dirs])
                             (s/map-of #{:name :js-modules :resource-dirs} any?)))

(s/def :config/name (and not-empty-string? #(re-matches #"^[A-Z][A-Za-z0-9]+$" %)))
(s/def :config/js-modules (s/coll-of not-empty-string?))
(s/def :config/resource-dirs (s/coll-of not-empty-string?))
