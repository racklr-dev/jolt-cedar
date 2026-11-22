# jolt-cedar — Cedar policy engine for the jolt control plane

One concrete mechanism: authorization decisions over the Cedar policy
language, for the jolt control plane. A Policy Set (jolt-contracts SPEC.md
§6) — policies + optional schema + entities + context — maps onto the
cedar-policy crate's JSON entry points (is_authorized_json_str,
validate_json_str), wrapped in a thin C ABI so jolt can bind it with
jolt.ffi (no JVM/JNI needed).

Standalone: no control-plane dependency. The policy text is Cedar source;
all JSON shapes are the cedar-policy JSON FFI contract.

## Native library

The Rust crate in native/ builds libcedar_policy.so (cedar-policy 4.15.0
with a small extern-C shim):

    cd native && cargo build --release
    # -> native/target/release/libcedar_policy.so

Set JOLT_CEDAR_LIB to its path (defaults to the release build next to the
checkout) and require jolt.cedar.

## Use

```clojure
;; deps.edn
{:deps {racklr-dev/jolt-cedar
        {:git/url "https://github.com/racklr-dev/jolt-cedar"
         :git/sha "<pinned sha>"}}}
```

```clojure
(require '[jolt.cedar :as cedar])

(def authz (cedar/new-authorizer {:lib "/path/to/libcedar_policy.so"}))
(cedar/authorized? authz
  {:principal {:type "User" :id "alice"} :action {:type "Action" :id "view"}
   :resource {:type "Photo" :id "vacation.jpg"} :context {}}
  "permit(principal, action, resource);"
  [])
(cedar/close! authz)
```

## Status

Working. The .so builds (cargo build --release in native/), jolt.ffi binds the
three extern-C entry points, and jolt.cedar exposes new-authorizer / authorized?
/ validate / close!. The smoke test covers authorize (permit, default-deny,
entity membership), validation, and the close! contract:

    jolt run test/jolt/cedar_test.clj

Board: nc-4867 (scaffold), nc-41BD (binding), nc-7675 (smoke test) all done.

## License

EPL-2.0 (wrapper); cedar-policy is Apache-2.0.
