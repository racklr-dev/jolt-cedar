;; jolt-cedar: Cedar policy engine for the jolt control plane.
;; Scaffold stub: the public API surface is pinned, every body throws :not-implemented.
(ns jolt.cedar
  "Cedar policy engine for the jolt control plane.

  Turns a Policy Set (jolt-contracts SPEC.md §6) into authorization decisions
  over the cedar-policy crate, reached through a thin C ABI via jolt.ffi."
  (:require [jolt.ffi :as ffi]))

(defn new-authorizer
  "Load libcedar_policy.so and return an opaque authorizer handle. opts may
  carry :lib (path to the shared object; default from JOLT_CEDAR_LIB)."
  [opts]
  (throw (ex-info "not implemented: new-authorizer" {:opts opts})))

(defn authorized?
  "Authorize a request against a policy set. request is {:principal {:type :id}
  :action {...} :resource {...} :context {...}}, policies is Cedar source text,
  entities is a JSON array. Returns the cedar decision JSON map."
  [authz request policies entities]
  (throw (ex-info "not implemented: authorized?" {:request request})))

(defn validate
  "Validate policies against an optional schema JSON. Returns the cedar
  validation JSON map."
  [authz policies schema]
  (throw (ex-info "not implemented: validate" {:policies policies})))

(defn close!
  "Release the authorizer. Idempotent."
  [authz]
  (throw (ex-info "not implemented: close!" {})))
