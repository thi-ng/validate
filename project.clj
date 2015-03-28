(defproject thi.ng/validate "0.1.2"
  :description  "Composable data validation & correction for structured data."
  :url          "https://github.com/thi-ng/validate"
  :license      {:name "Apache Software License, Version 2.0"
                 :url "https://www.apache.org/licenses/LICENSE-2.0"
                 :distribution :repo}
  :scm          {:name "git"
                 :url "git@github.com:thi-ng/validate.git"}

  :min-lein-vesion "2.4.0"

  :dependencies [[org.clojure/clojure "1.6.0"]]

  :source-paths ["src/cljx"]
  :test-paths   ["target/test-classes"]

  :profiles     {:dev {:dependencies [[org.clojure/clojurescript "0.0-3117"]
                                      [criterium "0.4.3"]]
                       :plugins [[com.keminglabs/cljx "0.6.0"]
                                 [lein-cljsbuild "1.0.5"]
                                 [com.cemerick/clojurescript.test "0.3.3"]]
                       :global-vars {*warn-on-reflection* true}
                       :jvm-opts ^:replace []
                       :auto-clean false
                       :prep-tasks [["cljx" "once"] "javac" "compile"]
                       :aliases {"cleantest" ["do" "clean," "cljx" "once," "test," "cljsbuild" "test"]}}}

  :cljx         {:builds [{:source-paths ["src/cljx"]
                           :output-path "target/classes"
                           :rules :clj}
                          {:source-paths ["src/cljx"]
                           :output-path "target/classes"
                           :rules :cljs}
                          {:source-paths ["test/cljx"]
                           :output-path "target/test-classes"
                           :rules :clj}
                          {:source-paths ["test/cljx"]
                           :output-path "target/test-classes"
                           :rules :cljs}]}

  :cljsbuild    {:builds [{:source-paths ["target/classes" "target/test-classes"]
                           :id "simple"
                           :compiler {:output-to "target/validate-0.1.2.js"
                                      :optimizations :whitespace
                                      :pretty-print true}}]
                 :test-commands {"unit-tests" ["phantomjs" :runner "target/validate-0.1.2.js"]}}


  :pom-addition [:developers [:developer
                              [:name "Karsten Schmidt"]
                              [:url "http://thi.ng"]
                              [:email "k@thi.ng"]
                              [:timezone "0"]]])
