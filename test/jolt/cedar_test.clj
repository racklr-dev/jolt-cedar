;; jolt-cedar stub test: pins the public API surface.
;; Run: cd jolt-cedar && jolt run test/jolt/cedar_test.clj
(ns jolt.test.cedar
  (:require [jolt.cedar :as cedar]))

(defn check [label expected got]
  (if (= expected got)
    (println (str "PASS " label))
    (do (println (str "FAIL " label))
        (println (str "  expected " (pr-str expected)))
        (println (str "  got      " (pr-str got)))
        (System/exit 1))))

(defn stub-err [thunk]
  (try (thunk) ::no-throw
       (catch Exception e (ex-message e))))

(defn stub? [msg]
  (boolean (re-find #"^not implemented" (or msg ""))))

(check "new-authorizer defined" true (fn? cedar/new-authorizer))
(check "new-authorizer stub" true (stub? (stub-err #(cedar/new-authorizer {}))))
(check "authorized? stub" true (stub? (stub-err #(cedar/authorized? nil {} "" []))))
(check "validate stub" true (stub? (stub-err #(cedar/validate nil "" "{}"))))
(check "close! stub" true (stub? (stub-err #(cedar/close! nil))))
