(defproject smee/binary "0.5.1-SNAPSHOT"
  :description "DSL for binary I/O using java's stream apis."
  :url "http://github.com/smee/binary"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]]
  :java-source-paths ["java-src"]
  :profiles {:dev 
             {:dependencies [[org.clojure/clojure "1.6.0"]
                             [io.aviso/pretty "0.1.9"]]}})
