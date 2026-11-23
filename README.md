# jolt-cedar — Cedar policy engine for jolt

**jolt-cedar** is a Clojure binding to the [Cedar](https://www.cedarpolicy.com/) policy language, for use by [jolt](https://github.com/jolt-lang/jolt) or any other Clojure program. It evaluates Cedar authorization requests and policy validation by calling the official [`cedar-policy` Rust crate](https://crates.io/crates/cedar-policy) through a thin C ABI, loaded with `jolt.ffi`.

Why a C ABI instead of JNI or a pure-Clojure reimplementation?

- **Correctness for free** — the decisions come from Cedar's own, actively maintained engine, not a port.
- **No JVM/JNI** — jolt has no JVM to lean on, so the native path is a direct C call.
- **Small surface** — the whole binding is three extern-C entry points: authorize, validate, and free a returned string.

The library is **standalone**: it has no dependency on any jolt control-plane code. Policy text is Cedar source; the request, entities, and schema arguments follow the [cedar-policy JSON FFI contract](https://docs.cedarpolicy.com/) and are converted to/from Clojure data inside `jolt.cedar`.

## Requirements

- **Rust** (any recent stable toolchain; the crate has been built with 1.99) plus a C linker.
- **Linux**. The binding builds and loads `libcedar_policy.so`; macOS (`.dylib`) and Windows are not currently supported.
- **Network access** the first time you build — `cargo` fetches `cedar-policy` and its dependencies from crates.io.

## Build

The Rust crate in `native/` builds `libcedar_policy.so` (cedar-policy 4.15.0 wrapped in a small `extern "C"` shim):

```sh
cd native
cargo build --release
# -> native/target/release/libcedar_policy.so
```

`jolt.cedar` finds that shared object automatically, in this order:

1. the `:lib` option passed to `new-authorizer`,
2. the `JOLT_CEDAR_LIB` environment variable,
3. `native/target/release/libcedar_policy.so` next to the checkout,
4. a bare `libcedar_policy.so` on the current working directory / search path.

## Usage

Add the dependency with a pinned SHA (required by jolt's git deps):

```clojure
;; deps.edn
{:deps {racklr-dev/jolt-cedar
        {:git/url "https://github.com/racklr-dev/jolt-cedar"
         :git/sha "<pinned sha>"}}}
```

```clojure
(require '[jolt.cedar :as cedar])

(def authz (cedar/new-authorizer {}))   ; :lib overrides; env var otherwise

(def alice-views
  {:principal {:type "User"   :id "alice"}
   :action    {:type "Action" :id "view"}
   :resource  {:type "Photo"  :id "vacation.jpg"}
   :context   {}})

;; Scoped policy: only the "view" action is permitted.
(cedar/authorized? authz alice-views
  "permit(principal, action == Action::\"view\", resource);"
  [])
;; => {:decision "Allow" :diagnostics {..}}

;; Anything else is denied by Cedar's default deny.
(cedar/authorized? authz (assoc-in alice-views [:action :id] "edit")
  "permit(principal, action == Action::\"view\", resource);"
  [])
;; => {:decision "Deny" :diagnostics {..}}

(cedar/close! authz)
```

Request and entities arguments are plain Clojure maps (or, for entities, a vector of maps); `jolt.cedar` encodes them to the JSON the Cedar FFI expects, and decodes the JSON answer back into keyword-keyed Clojure data.

## API

| function | signature | returns | notes |
| --- | --- | --- | --- |
| `new-authorizer` | `(new-authorizer opts)` | opaque authorizer handle | `opts` may carry `:lib`, the `.so` path; otherwise the default resolution order above applies |
| `authorized?` | `(authorized? authz request policies entities)` | decision map | `request` is `{:principal {:type :id} :action {:type :id} :resource {:type :id} :context {..}}`; `policies` is Cedar source text; `entities` is a Cedar entities vector. On success the map has `:decision` (`"Allow"`/`"Deny"`) and `:diagnostics`; on failure `:errors` (or `:error`) |
| `validate` | `(validate authz policies schema)` | validation map | `schema` is Cedar schema text, a JSON schema string/map, or `nil`/`{}` for no constraints. On success `:success true`; on failure `:success false` with `:validationErrors` |
| `close!` | `(close! authz)` | `nil` | idempotent; any call after close throws `ex-info` |

Argument order is deliberately `(authz, request, policies, entities)` and `(authz, policies, schema)`. Errors — a missing shared library, a malformed policy, or use-after-close — surface as `ex-info` exceptions. The policy set is passed **per call**, not cached on the handle, and the handle is **not** documented as thread-safe: treat one authorizer as single-threaded.

## Status

**Alpha.** The shared object builds, the three extern-C entry points are bound, and the four Clojure functions above work end to end.

The smoke test (`jolt run test/jolt/cedar_test.clj`) covers:

- authorization: a permitting policy, default deny, and entity membership,
- validation: a clean policy and a type error,
- the `close!` contract (idempotent close, use-after-close throws).

Not yet covered: schema-driven validation, non-trivial `:context`, error-path shapes, and concurrency.

## License

- The wrapper (`jolt-cedar`) is EPL-2.0.
- The bundled `cedar-policy` crate is Apache-2.0; if you redistribute the built `libcedar_policy.so`, include the corresponding NOTICE/attribution.
