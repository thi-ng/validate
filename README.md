# thi.ng/validate

Leiningen coordinates:
```clj
[thi.ng/validate "0.1.0"]
```
Validates a collection (map or vector) with given validation specs.
Returns a 2-element vector of the (possibly corrected) `coll` and a
map of errors (or nil, if there weren't any).

Specs have the following format:
```clj
key [validation-fn error-message correction-fn]
```
A key can be any datatype. If multiple validations should be applied
to a key, then these must be given as a seq/vector:
```clj
key [[val-fn1 msg1] [val-fn2 msg2 corr-fn] ...]
```
Validation for a key stops with the first failure (so if `val-fn1` fails
(and can't be corrected), `val-fn2` will *not* be checked etc.)

For each spec only the `validation-fn` is required.
Specifying custom error messages allows the library to produce localized
messages. If an `error-message` is omitted, a generic one will be used.
The optional `correction-fn` takes a single arg (the current map value)
and should return a non-`nil` value as correction. If correction
succeeded, no error message will be added for that entry.
```clj
;; just truncate a too long string as correction...
(v/validate {:a "hello world"} :a (v/max-length 5 #(.substring % 0 5)))
; [{:a "hello"} nil]
```
Specs can also be given as nested maps, reflecting the structure
of the collection:
```clj
key {:a {:b [validation-fn error-msg correction-fn]}
     :c [validation-fn error-msg correction-fn]}
```
If a `specs` map contains the wildcard key `:*`, then that key's spec
is applied *first* to all keys in the data map at that parent path.
In the example below the wildcard spec ensures all items of `:b` are
positive numbers, then the last item of `:b` also needs to be >50.
```clj
(v/validate {:a {:b [10 -20 30]}}
  :a {:b {:* [(v/number) (v/pos)]
          2  (v/greater-than 50)}})
; [{:a {:b [10 -20 30]}}
;  {:a {:b {1 ("must be positive"), 2 ("must be greater than 50"}}}]
```
The fail fast behavior also holds true for wildcard validation:
If wildcard validation fails for a key, any additionally given validators
for that key are ignored.

Some examples using various pre-defined validators:
```clj
(v/validate {:a {:name "toxi" :age 38}}
  :a {:name [(v/string) (v/min-length 4)]
      :age  [(v/number) (v/less-than 35)]
      :city [(v/required) (v/string)]})
; [{:a {:age 38, :name "toxi"}}
;  {:a {:city ("is required"), :age ("must be less than 35")}}]

; compare the verbose way used for :min vs. using wildcards for :max keys
(v/validate {:aabb {:min [-100 -200 -300] :max [100 200 300]}}
  :aabb {:min {0 (v/neg) 1 (v/neg) 2 (v/neg)}
  :max {:* (v/pos)}})
; [{:aabb {:max [100 200 300], :min [-100 -200 -300]}} nil]
```
