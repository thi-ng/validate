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
  "Recursively applies all given validation specs for given key/path.
  If `specs` is a map, applies itself recursively for each key in the spec.
  If `specs` is a vector of specs, applies each in succession and
  bails at first validation failure for that key."
  [[m errors path :as state] [k specs]]
  (let [k-path (conj path k)]
    (cond
     (fn? (first specs))
     (let [[m errors] (validate-1 state k-path specs)]
       [m errors path])

     (clojure.core/map? specs)
     (if-let [spec (:* specs)]
       (let [value (get-in m k-path)
             ks (if (clojure.core/map? value) (keys value) (range (count value)))
             [m errors] (reduce (fn [state k] (validate-specs state [k spec])) [m errors k-path] ks)]
         [m errors path])
       (let [[m errors] (reduce validate-specs [m errors k-path] specs)]
         [m errors path]))

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

      (v/validate {:a \"hello world\"}
        :a (v/max-length 5 #(.substring % 0 5)))
      ; [{:a \"hello\"} nil]

  Specs can also be given as nested maps, reflecting the structure
  of the collection:

      key {:a {:b [validation-fn error-msg correction-fn]}
           :c [validation-fn error-msg correction-fn]}

  If a `specs` map contains the wildcard key `:*` then that key's spec
  is applied to all keys in the data map at that parent path, e.g.:

      (v/validate {:a {:b [10 -20 30]}}
        :a {:b {:* [(v/number?) (v/pos?)]}})
      ; [{:a {:b [10 -20 30]}}
      ;  {:a {:b {1 (\"must be positive\")}}}]

  If multiple validations should be applied to a key, then these must be
  given as a seq/vector. Validation for that key stops with the first
  failure (so if `val-fn1` fails, `val-fn2` will *not* be checked etc.):

      key [[val-fn1 msg1] [val-fn2 msg2 corr-fn]]

  Some examples using various pre-defined validators:

      (v/validate {:a {:name \"toxi\" :age 38}}
        :a {:name [(v/string?) (v/min-length? 4)]
            :age  [(v/number?) (v/less-than? 35)]
            :city [(v/required) (v/string?)]})
      ; [{:a {:age 38, :name \"toxi\"}}
      ;  {:a {:city (\"is required\"),
      ;       :age (\"must be less than 35\")}}]

      (v/validate {:aabb {:min [-100 -200 -300]
                          :max [100 200 300]}}
        :aabb {:min {0 (v/neg?) 1 (v/neg?) 2 (v/neg?)}
               :max {:* (v/pos?)}})
      ; [{:aabb {:max [100 200 300],
      ;          :min [-100 -200 -300]}}
      ;   nil]"
  [coll & {:as validators}]
  (->> validators
       (reduce validate-specs [coll nil []])
       (take 2)
       (vec)))

;; ## Validators

(defn validator
  [f err]
  (fn [& [msg corr]]
    (if (fn? msg) [f err msg] [f (or msg err) corr])))

(defn validator-x [pred f err]
  (fn [x & [msg corr]]
    ((validator #(pred (f %) x) (str err " " x)) msg corr)))

(def required
  (validator #(if (sequential? %) (seq %) %) "is required"))

(def pos?
  (validator clojure.core/pos? "must be positive"))

(def neg?
  (validator clojure.core/neg? "must be negative"))

(def number?
  (validator clojure.core/number? "must be a number"))

(def vector?
  (validator clojure.core/vector? "must be a vector"))

(def map?
  (validator clojure.core/map? "must be a map"))

(def string?
  (validator clojure.core/string? "must be a string"))

(def min-length? (validator-x >= count "must have min length of"))
(def max-length? (validator-x <= count "must have max length of"))
(def fixed-length? (validator-x = count "must have a length of"))
(def less-than? (validator-x < identity "must be less than"))
(def greater-than? (validator-x > identity "must be greater than"))

(defn matches?
  "Takes a regex and optional error message, returns a validator spec
  which applies `clojure.core/re-matches` as validation fn."
  ([re] (matches? re "must match regexp"))
  ([re msg] [#(re-matches re %) msg]))

(def email?
  "Validation spec for email addresses."
  (matches? #"(?i)^[\w.%+-]+@[a-z0-9.-]+.[a-z]{2,6}$"
            "must be a valid email address"))

(def url?
  "Validation spec for URLs using comprehensive regex by Diego Perini.

  Also see:

  * <https://gist.github.com/dperini/729294>
  * <http://mathiasbynens.be/demo/url-regex>"
  (matches? #"(?i)^(?:(?:https?|ftp):\/\/)(?:\S+(?::\S*)?@)?(?:(?!10(?:\.\d{1,3}){3})(?!127(?:\.\d{1,3}){3})(?!169\.254(?:\.\d{1,3}){2})(?!192\.168(?:\.\d{1,3}){2})(?!172\.(?:1[6-9]|2\d|3[0-1])(?:\.\d{1,3}){2})(?:[1-9]\d?|1\d\d|2[01]\d|22[0-3])(?:\.(?:1?\d{1,2}|2[0-4]\d|25[0-5])){2}(?:\.(?:[1-9]\d?|1\d\d|2[0-4]\d|25[0-4]))|(?:(?:[a-z\u00a1-\uffff0-9]+-?)*[a-z\u00a1-\uffff0-9]+)(?:\.(?:[a-z\u00a1-\uffff0-9]+-?)*[a-z\u00a1-\uffff0-9]+)*(?:\.(?:[a-z\u00a1-\uffff]{2,})))(?::\d{2,5})?(?:\/[^\s]*)?$"
            "must be a valid URL"))
