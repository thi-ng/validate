# thi.ng/validate

This library can be used to validate and/or conform a nested
associative collection (map or vector) via given validation specs.

## Leiningen coordinates

```clj
[thi.ng/validate "0.1.1"]
```

## Usage

The main entry point for validation is the `validate` function, which
accepts a data structure and a map of similar structure (same
keys/nesting) and returns a 2-element vector of the (possibly
corrected) structure and a map of errors (or nil, if there weren't any).

```clj
(require '[thi.ng.validate.core :as v])

(v/validate
  {:me {:name "toxi" :place :ldn}}
  {:me {:name (v/string) :place (v/member-of #{:sfo :nyc})}})

;; => [{:me {:place :ldn, :name "toxi"}}
       {:me {:place ("must be one of: :sfo, :nyc")}}]
```

### Validation specs

Internally, validation specs have the following format, but the
library provides many predefined validators (many of them parametric),
allowing for the definition of complex validations. E.g. in the
example above we used `v/string` and `v/member-of` validators. Each of
them is implemented as an HOF, which when called expands to a spec
like this:

```clj
{key1 [validation-fn error-message correction-fn]
 key2 ...}
```

Key in a validation spec can be of any datatype. If multiple
validations should be applied to a key, then these must be given as a
seq/vector:

```clj
;; using HOF validators...
{key [(validator1) (validator2) ...]}

;; expanded (low level)
{key [[val-fn1 msg1] [val-fn2 msg2 corr-fn] ...]}
```

For each spec only the `validation-fn` is required.

### Failfast behavior per key & error handling

Validation for a key stops with the first failure (so if `val-fn1` fails
(and can't be corrected), `val-fn2` will *not* be checked etc.)

Specifying custom error messages allows the library to produce
localized messages. If an `error-message` is omitted, a generic one
will be used (Note that most of the supplied validators already
provide an helpful one).

### Auto-correction

The optional `correction-fn` takes two args (the path to the current
key and the map value for that key) and should return a non-`nil`
value as correction. If correction succeeded, no error message will be
added for that entry.

```clj
;; silently truncate a too long string as correction...
(v/validate
  {:a "hello world"}
  {:a (v/max-length 5 (fn [_ v] (.substring v 0 5)))})

;; => [{:a \"hello\"} nil]
```

Specs can also be given
as nested maps, reflecting the structure of the target collection:

```clj key
{:a {:b [validation-fn error-msg correction-fn]}
     :c [validation-fn error-msg correction-fn]}
```

### Wildcards

If a `specs` map contains the wildcard key `:*`, then that key's spec
is applied *first* to all keys in the data map at the wildcard's
parent path. In the example below the wildcard spec ensures all items
of `:b` are positive numbers, then the last item of `:b` also needs to
be >50.

```clj
(v/validate
  {:a {:b [10 -20 30]}}
  {:a {:b {:* [(v/number) (v/pos)], 2 (v/greater-than 50)}}})

;; => [{:a {:b [10 -20 30]}}
;;     {:a {:b {1 ("must be positive"), 2 ("must be greater than 50"}}}}]
```

The fail fast behavior also holds true for wildcard validation: If
wildcard validation fails for a key, any additionally given validators
for that key are ignored.

More examples using various pre-defined validators:

```clj
(v/validate
 {:a {:name "toxi" :age 38}}
 {:a {:name [(v/string) (v/min-length 4)]
      :age  [(v/number) (v/less-than 35)]
      :city [(v/required) (v/string)]}})
      
;; => [{:a {:age 38, :name "toxi"}}
;;     {:a {:city ("is required"), :age ("must be less than 35")}}]

; compare the verbose way used for :min vs. using wildcards for :max keys
(v/validate
  {:aabb {:min [-100 -200 -300] :max [100 200 300]}}
  {:aabb {:min {0 (v/neg) 1 (v/neg) 2 (v/neg)}
          :max {:* (v/pos)}}})

;; => [{:aabb {:max [100 200 300], :min [-100 -200 -300]}} nil]
```

## License

Copyright © 2013 - 2015 Karsten Schmidt

Distributed under the
[Apache Software License 2.0](http://www.apache.org/licenses/LICENSE-2.0).
