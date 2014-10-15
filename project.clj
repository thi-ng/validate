(defproject thi.ng/validate "0.1.0-SNAPSHOT"
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

  :profiles     {:dev {:dependencies [[org.clojure/clojurescript "0.0-2322"]
                                      [criterium "0.4.3"]]
                       :plugins [[org.clojars.cemerick/cljx "0.5.0-SNAPSHOT"]
                                 [lein-cljsbuild "1.0.3"]
                                 [com.cemerick/clojurescript.test "0.3.1"]
                                 [com.cemerick/austin "0.1.4"]]
                       :global-vars {*warn-on-reflection* true}
                       :jvm-opts ^:replace []
                       :auto-clean false
                       :prep-tasks [["cljx" "once"] "javac" "compile"]
                       :aliases {"cleantest" ["do" "clean" ["test" ":all"] ["cljsbuild" "test"]]}}}

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
                           :compiler {:output-to "target/validate-0.1.0-SNAPSHOT.js"
                                      :optimizations :whitespace
                                      :pretty-print true}}]
                 :test-commands {"unit-tests" ["phantomjs" :runner "target/validate-0.1.0-SNAPSHOT.js"]}}


  :pom-addition [:developers [:developer
                              [:name "Karsten Schmidt"]
                              [:url "http://thi.ng"]
                              [:email "k@thi.ng"]
                              [:timezone "0"]]])
