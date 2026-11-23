;; jolt-cedar: Cedar policy engine for the jolt control plane.
(ns jolt.cedar
  "Cedar policy engine for the jolt control plane.

  Turns a Policy Set (jolt-contracts SPEC.md section 6) into authorization
  decisions over the cedar-policy crate, reached through a thin C ABI via
  jolt.ffi. The shared object is libcedar_policy.so, built by the Rust crate
  in native/; set JOLT_CEDAR_LIB to override its location."
  (:require [clojure.data.json :as json]
            [clojure.string :as str]
            [jolt.ffi :as ffi]
            [jolt.fs :as fs]))

;; ---- native library -------------------------------------------------------

(def ^:private lib-path-rel "native/target/release/libcedar_policy.so")

(defn- project-root
  "The repo root when *file* is src/jolt/cedar.clj, else nil."
  []
  (when (bound? #'*file*)
    (some-> *file* fs/path fs/parent fs/parent fs/parent str)))

(defn- existing
  [p]
  (when (and p (fs/exists? p)) (str p)))

(defn- resolve-lib-path
  "Find libcedar_policy.so: JOLT_CEDAR_LIB, then a system install
  (/usr/local/lib, /usr/lib), then relative to the source checkout, then
  the working directory."
  []
  (or (existing (System/getenv "JOLT_CEDAR_LIB"))
      (some existing ["/usr/local/lib/libcedar_policy.so"
                      "/usr/lib/libcedar_policy.so"])
      (existing (when-let [r (project-root)] (str (fs/path r lib-path-rel))))
      (existing lib-path-rel)
      (existing "libcedar_policy.so")))

(defonce ^:private *lib*
  (if-let [p (resolve-lib-path)]
    (ffi/load-library p)
    (throw (ex-info (str "jolt.cedar: libcedar_policy.so not found; "
                         "build native/ or set JOLT_CEDAR_LIB")
                    {}))))

(ffi/defcfn cedar-authorized-raw
  "is_authorized_json_str" [:string :string :string] :pointer)
(ffi/defcfn cedar-validate-raw
  "validate_json_str" [:string :string] :pointer)
(ffi/defcfn cedar-free-string-raw
  "cedar_free_string" [:pointer] :void)

(defn- read-ptr
  "Read and free a NUL-terminated C string; nil for NULL."
  [p]
  (when-not (ffi/null? p)
    (try (ffi/ptr->string p)
         (finally (cedar-free-string-raw p)))))

(defn- ->json
  "Encode non-string Clojure data as JSON; strings pass through."
  [x]
  (if (string? x) x (json/write-str x)))

(defn- parse-answer
  "Parse a Cedar JSON answer string into keyword-keyed Clojure data."
  [s]
  (when-not (str/blank? s)
    (json/read-str s :key-fn keyword)))

;; ---- public API -----------------------------------------------------------

(defn new-authorizer
  "Load libcedar_policy.so and return an opaque authorizer handle. opts may
  carry :lib (path to the shared object; default from JOLT_CEDAR_LIB)."
  [opts]
  {:lib (or (:lib opts) *lib*)
   :closed (atom false)})

(defn- check-open!
  [authz who]
  (when (true? (some-> authz :closed deref))
    (throw (ex-info (str "jolt.cedar/" who ": authorizer is closed") {})))
  authz)

(defn authorized?
  "Authorize a request against a policy set. request is {:principal {:type :id}
  :action {:type :id} :resource {:type :id} :context {..}}, policies is Cedar
  source text, entities is a Cedar entities JSON array. Returns the decision
  answer as keyword-keyed data."
  [authz request policies entities]
  (check-open! authz "authorized?")
  (-> (cedar-authorized-raw (->json request) policies (->json entities))
      read-ptr
      parse-answer))

(defn validate
  "Validate policies against an optional schema. schema is Cedar schema text,
  a JSON schema string, or a JSON schema map; nil or {} means no constraints.
  Returns the validation answer as keyword-keyed data."
  [authz policies schema]
  (check-open! authz "validate")
  (-> (cedar-validate-raw policies (->json (or schema {})))
      read-ptr
      parse-answer))

(defn close!
  "Release the authorizer. Idempotent."
  [authz]
  (when (map? authz)
    (reset! (:closed authz) true))
  nil)
