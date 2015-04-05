(ns thi.ng.validate.test.core
  #+cljs
  (:require-macros
   [cemerick.cljs.test :refer [is deftest]])
  (:require
   [thi.ng.validate.core :as v]
   #+clj  [clojure.test :refer :all]
   #+cljs [cemerick.cljs.test]
   #+cljs [cljs.reader :refer [read-string]]))

(def db
  {:people [{:name "toxi" :age "38"}
            {:name "foo" :city "london" :age 23}]})

(deftest wildcard-req-keys
  (let [specs {:people {:* [(v/map) (v/required-keys [:name :age :city])]}}
        [m err] (v/validate db specs)]
    (is (not (nil? (get-in err [:people 0]))))
    (is (nil? (get-in err [:people 1])))))

(deftest wildcard-age-correct
  (let [specs {:people {:* {:age [(v/pos (fn [_ v] (if (string? v) (read-string v)))) (v/less-than 35)]}}}
        [m err] (v/validate db specs)]
    (is (= "must be less than 35" (first (get-in err [:people 0 :age]))))
    (is (nil? (get-in err [:people 1])))
    ))

(deftest required-boolean
  (let [m {:a false :b true :c nil}
        [m err] (v/validate m {:a (v/required) :b (v/required) :c (v/required)})]
    (is (nil? (:a err)))
    (is (nil? (:b err)))
    (is (:c err))))

(deftest valid-email
  (let [email? (partial (first (v/email)) nil)]
    (is (email? "a@foo.com"))
    (is (email? "a.b+sp4m@foo.bar.co"))
    (is (not (email? "a@b.c")))
    (is (not (email? "thi@ng")))))

(deftest valid-url
  (let [uri? (partial (first (v/url)) nil)]
    (is (uri? "http://thi.ng"))
    (is (uri? "https://thi.ng/validate/"))
    (is (uri? "http://thi.ng/validate?foo=bar&baz=42"))
    (is (uri? "http://127.0.0.1/foo?bar=a%20b"))
    (is (uri? "ftp://foo:bar*@127.0.0.1/"))
    (is (not (uri? "http:/thi.ng")))
    (is (not (uri? "htp://thi.ng")))
    (is (not (uri? "thi.ng")))))
