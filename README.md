# jolt-cedar — Cedar policy engine for the jolt control plane

Authorization decisions over the [Cedar](https://github.com/cedar-policy/cedar)
policy language, for the jolt control plane. A Policy Set
([faraday-contracts](https://github.com/racklr-dev/faraday/blob/main/contracts/SPEC.md)
§6) — policies plus optional schema, entities, and context — maps onto the
cedar-policy crate's JSON entry points (`is_authorized_json_str`,
`validate_json_str`), wrapped in a thin C ABI so jolt can bind it with
[jolt.ffi](https://github.com/jolt-lang/jolt) (no JVM/JNI needed).

Standalone: no control-plane dependency. The policy text is Cedar source; all
JSON shapes are the cedar-policy JSON FFI contract.

## Requirements

- [jolt](https://github.com/jolt-lang/jolt) 0.8.28+ to run the tests
- [Rust](https://www.rust-lang.org/) toolchain to build the native shim
- cedar-policy 4.15.0 (pulled by the crate in `native/`)

## Build

```sh
cd native && cargo build --release
# -> native/target/release/libcedar_policy.so
```

`jolt.cedar` resolves the library from `JOLT_CEDAR_LIB`, then the release build
next to the checkout, then the working directory; it throws a readable error
when none is found.

## Usage

```clojure
{:deps {racklr-dev/jolt-cedar
        {:git/url "https://github.com/racklr-dev/jolt-cedar"
         :git/sha "<pinned sha>"}}}
```

```clojure
(require '[jolt.cedar :as cedar])

(def authz (cedar/new-authorizer {:lib "/path/to/libcedar_policy.so"}))

;; scoped allow: only alice may view vacation.jpg
(cedar/authorized? authz
  {:principal {:type "User" :id "alice"}
   :action    {:type "Action" :id "view"}
   :resource  {:type "Photo" :id "vacation.jpg"}
   :context   {}}
  "permit(principal == User::\"alice\", action, resource);"
  [])
;; => {:response {:decision "Allow"} ...}

;; default deny: the same request with no matching permit
(cedar/authorized? authz
  {:principal {:type "User" :id "mallory"}
   :action    {:type "Action" :id "view"}
   :resource  {:type "Photo" :id "vacation.jpg"}
   :context   {}}
  "permit(principal == User::\"alice\", action, resource);"
  [])
;; => {:response {:decision "Deny"} ...}

(cedar/validate authz "permit(principal, action, resource);" nil)
(cedar/close! authz)
```

## API

| Function | Args | Returns | Errors |
|---|---|---|---|
| `new-authorizer` | `opts` | authorizer handle | throws if the lib is not found |
| `authorized?` | `authz request policies entities` | keyword-keyed Cedar answer (`:response {:decision ...}` or `:type "failure"`) | throws on invalid Cedar/JSON |
| `validate` | `authz policies schema` | keyword-keyed validation answer | throws on invalid policy |
| `close!` | `authz` | nil (idempotent) | throws if used after close |

`request` is `{:principal {:type :id} :action {:type :id} :resource {:type :id}
:context {}}`; `policies` is Cedar source; `entities` is a Cedar entities JSON
array; `schema` is Cedar schema text, a JSON schema string, a JSON schema map,
or nil (no constraints). `opts` may carry `:lib` (path; default from
`JOLT_CEDAR_LIB`).

## Status

alpha — the binding, the authorize path (permit, default-deny, entity
membership), validation, and the close! contract are covered by
`test/jolt/cedar_test.clj`. Policy-evaluation error edges are not exhaustively
tested.

## License

EPL-2.0 (wrapper); cedar-policy is Apache-2.0.

## Learn more

- [jolt-lang](https://github.com/jolt-lang/jolt) — the language and toolchain
- [cedar-policy](https://github.com/cedar-policy/cedar) — the engine this wraps
- [jolt.ffi](https://github.com/jolt-lang/jolt) — the stdlib FFI layer used here
- [faraday-contracts](https://github.com/racklr-dev/faraday/blob/main/contracts/SPEC.md) — SPEC.md §6, the Policy Set shape
