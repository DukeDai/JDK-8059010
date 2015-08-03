(defproject opencv-crash-repro "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [opencv/opencv "3.0.0"] 
                 [opencv/opencv-native "3.0.0"]
                 [net.mikera/core.matrix "0.36.1"]
                 [net.mikera/vectorz-clj "0.28.0"]                 
                 [net.mikera/imagez "0.5.0-thinktopic"]
                 [thinktopic/image "0.2.0-SNAPSHOT"]]
  
  :plugins [[s3-wagon-private "1.1.2"]]
  :repositories  {"snapshots"  {:url "s3p://thinktopic.jars/snapshots/"
                                :passphrase :env
                                :username :env}
                  "releases"  {:url "s3p://thinktopic.jars/releases/"
                               :passphrase :env
                               :username :env}}
  
  :profiles { :uberjar { :aot :all } }
  :main opencv-crash-repro.main
  )
