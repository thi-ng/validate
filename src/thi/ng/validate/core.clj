(ns thi.ng.validate.core
  (:refer-clojure :exclude [pos? neg? number? string? vector? map?]))

(defn- validate-1
  "Applies a single validation spec to the map value for given path/key.
  If validation fails and no correction fn is given, or if correction
  fails, adds an error message."
  [[m errors :as state] path [f msg correct]]
  (let [value (get-in m path)]
    (if-not (f value)
      (let [msg (or msg "validation failed")]
        (if correct
          (let [corrected (correct value)]
            (if-not (nil? corrected)
              [(assoc-in m path corrected) errors]
              [m (update-in errors path conj msg) true]))
          [m (update-in errors path conj msg) true]))
      state)))

(defn- validate-specs
  "Recursively applies all given validation specs for given key/path."
  [[m errors path :as state] [k specs]]
  (let [k-path (conj path k)]
    (cond
     (fn? (first specs))
     (let [[m errors] (validate-1 state k-path specs)]
       [m errors path])

     (clojure.core/map? specs)
     (let [[m errors] (reduce validate-specs [m errors k-path] specs)]
       [m errors path])

     :default
     (let [[m errors]
           (reduce
            (fn [[m errors stop? :as state] spec]
              (if-not stop?
                (validate-1 state k-path spec)
                state))
            [m errors] specs)]
       [m errors path]))))

(defn validate
  "Validates `coll` (a map or vector) with given validation specs.
  Returns a 2-element vector of the (possibly corrected) `coll` and a
  map of errors (or nil, if there weren't any).

  Specs have the following format:

      key [validation-fn error-message correction-fn]

  For each spec only the `validation-fn` is required.
  If an `error-message` is omitted, a generic one will be used.
  The optional `correction-fn` takes a single arg (the current map value)
  and should return a non-`nil` value as correction. If correction
  succeeded, no error message will be added for that map entry.

  Specs can also be given as nested maps, reflecting the structure
  of the collection:

      key {:a {:b [validation-fn error-msg correction-fn]}
           :c [validation-fn error-msg correction-fn]}

  If multiple validations should be applied to a key, then these must be
  given as a seq/vector. Validation for that key stops with the first
  failure (so if `val-fn1` fails, `val-fn2` will *not* be checked etc.):

      key [[val-fn1 msg1] [val-fn2 msg2 corr-fn]]

  Some examples using various pre-defined validators:

      (require '[thi.ng.validate.core :as v])

      (v/validate {:a {:name \"toxi\" :age 38}}
        :a {:name [v/required v/string? (v/min-length 4)]
            :age  [v/number? (v/less-than 35)]
            :city [v/required v/string?]})
      ; [{:a {:age 38, :name \"toxi\"}}
         {:a {:city (\"is required\"), :age (\"must be less than 35\")}}]

      (v/validate {:aabb {:min [-100 -200 -300] :max [100 200 300]}}
        :aabb {:min {0 v/neg? 1 v/neg? 2 v/neg?}
               :max {0 v/pos? 1 v/pos? 2 v/pos?}})
      ; [{:aabb {:max [100 200 300], :min [-100 -200 -300]}} nil]"
  [coll & {:as validators}]
  (->> validators
       (reduce validate-specs [coll nil []])
       (take 2)
       (vec)))

(def required [#(if (sequential? %) (seq %) %) "is required"])
(def pos? [clojure.core/pos? "must be positive"])
(def neg? [clojure.core/neg? "must be negative"])

(def number? [clojure.core/number? "must be a number"])
(def vector? [clojure.core/vector? "must be a vector"])
(def map? [clojure.core/map? "must be a map"])
(def string? [clojure.core/string? "must be a string"])

(defn min-length [x] [#(>= (count %) x) (str "must have min length of " x)])
(defn max-length [x] [#(<= (count %) x) (str "must have max length of " x)])
(defn fixed-length [x] [#(= (count %) x) (str "must have a length of " x)])

(defn less-than [x] [#(< % x) (str "must be less than " x)])
(defn greater-than [x] [#(> % x) (str "must be greater than " x)])

(defn matches?
  ([re] (matches? re "must match regexp"))
  ([re msg] [#(re-matches re %) msg]))
(def email (matches? #"(?i)^[A-Z0-9._%+-]+@[A-Z0-9.-]+.[A-Z]{2,6}$" "must be a valid email address"))
(def url (matches? #"" "must be a valid URL"))

(comment
  (validate {:a {:name "toxi" :age 38}}
            :a {:name [required string? (min-length 4)]
                :age  [number? (less-than 35)]
                :city required})

  (validate {:aabb {:min [-100 -200 -300] :max [100 200 300]}}
            :aabb {:min {0 neg? 1 neg? 2 neg?}
                   :max {0 pos? 1 pos? 2 pos?}})

  )
