(ns thi.ng.validate.core
  (:refer-clojure :exclude [map vector not]))

(defn- reduce-specs
  [f state specs path]
  (conj (->> specs (reduce f state) (take 2) (vec)) path))

(defn- remove-failed-specs
  [[_ errors path] specs]
  (filter
   (fn [[k _]] (clojure.core/not (get-in errors (conj path k))))
   specs))

(defn- validate-1
  "Applies a single validation spec to the collection value for
  given path/key. Adds an error message, if validation fails and
  no correction fn is given, or if correction fails."
  [[coll errors :as state] path [f msg correct]]
  (let [value (get-in coll path)]
    (if-not (f value)
      (let [msg (or msg "validation failed")]
        (if correct
          (let [corrected (correct value)]
            (if-not (nil? corrected)
              [(assoc-in coll path corrected) errors]
              [coll (update-in errors path conj msg) true]))
          [coll (update-in errors path conj msg) true]))
      state)))

(defmulti validate-specs
  (fn [_ [_ specs]]
    (cond
     (fn? (first specs)) :single-spec
     (map? specs) (if (:* specs) :nested-spec* :nested-spec)
     (sequential? specs) :multi-spec)))

(defmethod validate-specs :single-spec
  [[_ _ path :as state] [k specs]]
  (conj (->> (validate-1 state (conj path k) specs) (take 2) (vec)) path))

(defmethod validate-specs :multi-spec
  [[_ _ path :as state] [k specs]]
  (let [k-path (conj path k)]
    (reduce-specs
     (fn [[_ _ stop? :as state] spec]
       (if-not stop?
         (validate-1 state k-path spec)
         state))
     state specs path)))

(defmethod validate-specs :nested-spec
  [[m errors path :as state] [k specs]]
  (reduce-specs validate-specs [m errors (conj path k)] specs path))

(defmethod validate-specs :nested-spec*
  [[m errors path :as state] [k specs]]
  (let [spec (:* specs)
        k-path (conj path k)
        value (get-in m k-path)
        ks (if (map? value) (keys value) (range (count value)))
        state [m errors k-path]
        state (reduce-specs #(validate-specs % [%2 spec]) state ks k-path)
        specs (remove-failed-specs state (dissoc specs :*))]
    (reduce-specs validate-specs state specs path)))

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
  succeeded, no error message will be added for that entry.

      (v/validate {:a \"hello world\"}
        :a (v/max-length 5 #(.substring % 0 5)))
      ; [{:a \"hello\"} nil]

  Specs can also be given as nested maps, reflecting the structure
  of the collection:

      key {:a {:b [validation-fn error-msg correction-fn]}
           :c [validation-fn error-msg correction-fn]}

  If a `specs` map contains the wildcard key `:*`, then that key's spec
  is applied *first* to all keys in the data map at that parent path.
  In the example below the wildcard spec ensures all items of `:b` are
  positive numbers, then the last item of `:b` also needs to be > 50.

      (v/validate {:a {:b [10 -20 30]}}
        :a {:b {:* [(v/number) (v/pos)] 2 (v/greater-than 50)}})
      ; [{:a {:b [10 -20 30]}}
      ;  {:a {:b {1 (\"must be positive\")
                  2 (\"must be greater than 50\"}}}]

  The fail fast behavior also holds true for wildcard validation:
  If wildcard validation fails for a key, any additionally given validators
  for that key are ignored.

  If multiple validations should be applied to a key, then these must be
  given as a seq/vector. Validation for a key stops with the first
  failure (so if `val-fn1` fails (and can't be corrected), `val-fn2`
  will *not* be checked etc.):

      key [[val-fn1 msg1] [val-fn2 msg2 corr-fn]]

  Some examples using various pre-defined validators:

      (v/validate {:a {:name \"toxi\" :age 38}}
        :a {:name [(v/string) (v/min-length 4)]
            :age  [(v/number) (v/less-than 35)]
            :city [(v/required) (v/string)]})
      ; [{:a {:age 38, :name \"toxi\"}}
      ;  {:a {:city (\"is required\"),
      ;       :age (\"must be less than 35\")}}]

      (v/validate {:aabb {:min [-100 -200 -300]
                          :max [100 200 300]}}
        :aabb {:min {0 (v/neg) 1 (v/neg) 2 (v/neg)}
               :max {:* (v/pos)}})
      ; [{:aabb {:max [100 200 300],
      ;          :min [-100 -200 -300]}}
      ;   nil]"
  [coll & {:as validators}]
  (->> validators
       (reduce validate-specs [coll nil []])
       (take 2)
       (vec)))

;; ## Validators

;; Commonly used preset validators are supplied below.
;; Apart from the regex validators, all others can be customized
;; with optional error messages and/or correction fns, given
;; as additional arguments...

(defn validator
  "Higher order function to build a validator fn which accepts
  optional an error message and/or correction fn. The constructed
  fn the generates a validation spec. `validator` itself
  takes two args: `f` the actual validation predicate fn and a
  default validation `error` message."
  [f error]
  (fn [& [msg corr]]
    (if (fn? msg) [f error msg] [f (or msg error) corr])))

(defn validator-x
  "Similar to `validator` fn, this is an HOF to build a validator
  fn which takes an extra argument `x` for the validation predicate,
  e.g. to construct a validation of `< x`.

  `validator-x` itself takes 2 fns and a default error message.
  The first fn is the actual validation predicate.
  The second fn is applied to the to-be-verified value before
  passing it to the predicate."
  [pred f err]
  (fn [x & [msg corr]]
    ((validator #(pred (f %) x) (str err " " x)) msg corr)))

(defn not
  [[f _ corr] msg]
  [#(clojure.core/not (f %)) msg corr])

(def required
  "Returns validation spec to ensure the presence of a value.
  For collections, it uses `(seq x)` to only allow
  non-empty collections."
  (validator #(if (coll? %) (seq %) %) "is required"))

(def pos
  "Returns validation spec to ensure value is a positive number."
  (validator #(and (number? %) (pos? %)) "must be a positive number"))

(def neg
  "Returns validation spec to ensure value is a negative number."
  (validator #(and (number? %) (neg? %)) "must be a negative number"))

(def number
  "Returns validation spec to ensure value is a number."
  (validator number? "must be a number"))

(def vector
  "Returns validation spec to ensure value is a vector."
  (validator vector? "must be a vector"))

(def map
  "Returns validation spec to ensure value is a map."
  (validator map? "must be a map"))

(def string
  "Returns validation spec to ensure value is a string."
  (validator string? "must be a string"))

(def min-length
  "Returns validation spec to ensure value has a min length."
  (validator-x >= count "must have min length of"))

(def max-length
  "Returns validation spec to ensure value has a max length."
  (validator-x <= count "must have max length of"))

(def fixed-length
  "Returns validation spec to ensure value has the given number of elements."
  (validator-x = count "must have a length of"))

(def less-than
  "Returns validation spec to ensure value is < x."
  (validator-x < identity "must be less than"))

(def greater-than
  "Returns validation spec to ensure value is > x."
  (validator-x > identity "must be greater than"))

(def equals
  "Returns validation spec to ensure value is x."
  (validator-x = identity "must equal"))

(defn in-range
  "Returns validation spec to ensure value is a number in
  the range `min`..`max` (inclusive)."
  [min max & [msg corr]]
  (let [f #(and (number? %) (>= % min) (<= % max))
        err (str "must be in range " min ".." max)]
    (if (fn? msg)
      [f err msg]
      [f (or msg err) corr])))

(defn member-of
  [set & [msg corr]]
  (let [f #(set %)
        err (str "must be one of " set)]
    (if (fn? msg)
      [f err msg]
      [f (or msg err) corr])))

(defn matches
  "Takes a regex and optional error message, returns a validator spec
  which applies `clojure.core/re-matches` as validation fn."
  ([re] (matches re "must match regexp"))
  ([re msg] [#(re-matches re %) msg]))

(def email
  "Returns validation spec for email addresses."
  (matches #"(?i)^[\w.%+-]+@[a-z0-9.-]+.[a-z]{2,6}$"
           "must be a valid email address"))

(def url
  "Returns validation spec for URLs using comprehensive regex
  by Diego Perini. Also see:

  * <https://gist.github.com/dperini/729294>
  * <http://mathiasbynens.be/demo/url-regex>"
  (matches #"(?i)^(?:(?:https?|ftp):\/\/)(?:\S+(?::\S*)?@)?(?:(?!10(?:\.\d{1,3}){3})(?!127(?:\.\d{1,3}){3})(?!169\.254(?:\.\d{1,3}){2})(?!192\.168(?:\.\d{1,3}){2})(?!172\.(?:1[6-9]|2\d|3[0-1])(?:\.\d{1,3}){2})(?:[1-9]\d?|1\d\d|2[01]\d|22[0-3])(?:\.(?:1?\d{1,2}|2[0-4]\d|25[0-5])){2}(?:\.(?:[1-9]\d?|1\d\d|2[0-4]\d|25[0-4]))|(?:(?:[a-z\u00a1-\uffff0-9]+-?)*[a-z\u00a1-\uffff0-9]+)(?:\.(?:[a-z\u00a1-\uffff0-9]+-?)*[a-z\u00a1-\uffff0-9]+)*(?:\.(?:[a-z\u00a1-\uffff]{2,})))(?::\d{2,5})?(?:\/[^\s]*)?$"
           "must be a valid URL"))
