(ns thi.ng.validate.test.core
  (:require [thi.ng.validate.core :as v])
  (:use clojure.test))

(def db
  {:people [{:name "toxi" :age "38"}
            {:name "foo" :city "london" :age 23}]})

(deftest wildcard-req-keys
  (let [specs [:people {:* [(v/map) (v/required-keys [:name :age :city])]}]
        [m err] (apply v/validate db specs)]
    (is (not (nil? (get-in err [:people 0]))))
    (is (nil? (get-in err [:people 1])))))

(deftest wildcard-age-correct
  (let [specs [:people {:* {:age [(v/pos (fn [_ v] (if (string? v) (read-string v)))) (v/less-than 35)]}}]
        [m err] (apply v/validate db specs)]
    (is (= "must be less than 35" (first (get-in err [:people 0 :age]))))
    (is (nil? (get-in err [:people 1])))
    ))
