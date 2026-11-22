;; jolt-cedar smoke test: authorize + validate + contract round-trips.
;; Run: cd jolt-cedar && jolt run test/jolt/cedar_test.clj
(ns jolt.test.cedar
  (:require [clojure.string :as str]
            [jolt.cedar :as cedar]))

(defn check
  [label expected got]
  (if (= expected got)
    (println (str "PASS " label))
    (do (println (str "FAIL " label))
        (println (str "  expected " (pr-str expected)))
        (println (str "  got      " (pr-str got)))
        (System/exit 1))))

(def permit-policy
  (str "permit("
       "principal == User::\"alice\", "
       "action == Action::\"view\", "
       "resource == Photo::\"door\");"))

(defn req
  [principal-id]
  {:principal {:type "User" :id principal-id}
   :action {:type "Action" :id "view"}
   :resource {:type "Photo" :id "door"}
   :context {}})

(def authz (cedar/new-authorizer {}))

(check "new-authorizer returns a handle" true (map? authz))
(check "handle carries a loaded lib" true (some? (:lib authz)))
(check "handle carries a closed flag" true (some? (:closed authz)))

(let [ans (cedar/authorized? authz (req "alice") permit-policy [])]
  (check "authorized? answers success" "success" (get ans :type))
  (check "permit policy allows alice" "allow" (get-in ans [:response :decision]))
  (check "diagnostics name the permitting policy"
         true (contains? (set (get-in ans [:response :diagnostics :reason])) "policy0")))

(let [ans (cedar/authorized? authz (req "bob") permit-policy [])]
  (check "default-deny for bob" "deny" (get-in ans [:response :decision])))

;; Entities JSON: a parent/group entity making membership resolvable.
(let [ents [{:uid {:type "Group" :id "staff"}
             :attrs {}
             :parents []}
            {:uid {:type "User" :id "alice"}
             :attrs {}
             :parents [{:type "Group" :id "staff"}]}]
      pol (str "permit(principal in Group::\"staff\", action, resource);")
      ans (cedar/authorized? authz (req "alice") pol ents)]
  (check "entity membership drives an in-principal permit"
         "allow" (get-in ans [:response :decision])))

(let [ans (cedar/validate authz permit-policy {})]
  (check "validate answers success" "success" (get ans :type))
  (check "no validation errors on a clean policy"
         [] (vec (get ans :validation-errors))))

;; A policy that uses an attribute the schema forbids should report an error.
(let [schema {:User {:appliesTo {:principalTypes ["User"]
                                 :context {:attributes {}}}}}
      pol (str "permit(principal, action, resource) when { principal.bogus };" )
      ans (cedar/validate authz pol schema)]
  (check "validation surfaces a type error as a failure or validation error"
         true (boolean (or (= "failure" (get ans :type))
                           (seq (get ans :validation-errors))))))

(cedar/close! authz)
(check "close! is idempotent" nil (cedar/close! authz))
(check "authorized? after close! throws"
       true (try (cedar/authorized? authz (req "alice") permit-policy []) false
                 (catch Exception e (boolean (re-find #"closed" (ex-message e))))))

(println "jolt-cedar smoke test: all green")
