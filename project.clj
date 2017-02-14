(defproject sounds-network "0.1.0-SNAPSHOT"
  :description "FIXME: write this!"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :min-lein-version "2.7.1"

  :dependencies [[org.clojure/clojure "1.9.0-alpha14"]
                 [org.clojure/clojurescript "1.9.456"]
                 [org.clojure/test.check "0.9.0"]
                 [clj-soup/clojure-soup "0.1.3"]
                 [datascript "0.15.5"]
                 [devcards "0.2.2"]
                 [sablono "0.7.4"]
                 ;; need to specify this for sablono
                 ;; when not using devcards
                 [cljsjs/react "15.3.1-0"]
                 [cljsjs/react-dom "15.3.1-0"]
                 [org.omcljs/om "1.0.0-alpha46"]
                 #_[reagent "0.6.0"]

                 [yada "1.2.0"]
                 [aleph "0.4.2-alpha12"]
                 [manifold "0.1.6-alpha4"]
                 [metosin/ring-swagger "0.23.0-SNAPSHOT"]
                 [org.clojure/core.match "0.3.0-alpha4"]
                 [org.clojure/core.async "0.2.395"]
                 [cljs-ajax "0.5.8"]
                 [binaryage/devtools "0.9.1"]
                 [binaryage/dirac "1.1.4"]]

  :plugins [[lein-figwheel "0.5.8"]
            [lein-cljsbuild "1.1.4" :exclusions [org.clojure/clojure]]]

  :clean-targets ^{:protect false} ["resources/public/js/compiled"
                                    "target"]

  :source-paths ["src"]

  :cljsbuild {
              :builds [{:id "devcards"
                        :source-paths ["src"]
                        :figwheel { :devcards true
                                    :websocket-url "ws://192.168.2.233:3449/figwheel-ws"
                                   ;; <- note this
                                   ;; :open-urls will pop open your application
                                   ;; in the default browser once Figwheel has
                                   ;; started and complied your application.
                                   ;; Comment this out once it no longer serves you.
                                  }
                        :compiler { :main       "sounds-network.cljs.core"
                                    :asset-path "js/compiled/devcards_out"
                                    :output-to  "resources/public/js/compiled/sounds_network_devcards.js"
                                    :output-dir "resources/public/js/compiled/devcards_out"
                                    :source-map-timestamp true }}
                       {:id "dev"
                        :source-paths ["src"]
                        :figwheel true
                        :compiler {:main       "sounds-network.core"
                                   :asset-path "js/compiled/out"
                                   :output-to  "resources/public/js/compiled/sounds_network.js"
                                   :output-dir "resources/public/js/compiled/out"
                                   :source-map-timestamp true }}
                       {:id "prod"
                        :source-paths ["src"]
                        :compiler {:main       "sounds-network.core"
                                   :asset-path "js/compiled/out"
                                   :output-to  "resources/public/js/compiled/sounds_network.js"
                                   :optimizations :advanced}}]}

  :figwheel {
             :server-ip "192.168.2.233"
             :css-dirs ["resources/public/css"]
               }

  :profiles {:dev {:dependencies [[binaryage/devtools "0.8.2"]
                                  [figwheel-sidecar "0.5.8"]
                                  [com.cemerick/piggieback "0.2.1"]]
                   ;; need to add dev source path here to get user.clj loaded
                   :source-paths ["src" "dev"]
                   ;; for CIDER
                   ;; :plugins [[cider/cider-nrepl "0.12.0"]]
                   :repl-options {; for nREPL dev you really need to limit output
                                  :port 8230
                                  :nrepl-middleware [dirac.nrepl/middleware cemerick.piggieback/wrap-cljs-repl]
                                  :init (set! *print-length* 50)
                                  }}})
