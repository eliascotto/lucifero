(defproject lucifero "0.1.1"
  :description "Static website generator"
  :url "https://github.com/elias94/lucifero"
  :license {:name "MIT License"
            :url "https://github.com/elias94/lucifero/blob/master/LICENSE"}

  :dependencies [[org.clojure/clojure "1.10.3"]
                 [org.clojure/tools.cli "1.0.206"]
                 [ring "1.8.1"]
                 [ring/ring-defaults "0.3.2"]
                 [compojure "1.6.2"]
                 [yogthos/config "1.1.7"]
                 [markdown-clj "1.10.6"]
                 [selmer "1.12.44"]
                 [clj-commons/clj-yaml "0.7.107"]
                 [clj-commons/fs "1.6.307"]
                 [org.jsoup/jsoup "1.14.2"]
                 [asset-minifier "0.2.7"]
                 [clj-html-compressor "0.1.1"]
                 [juxt/dirwatch "0.2.5"]
                 [org.clojure/tools.logging "1.1.0"]
                 [ch.qos.logback/logback-classic "1.2.5"]]

  :jvm-opts ["-Xmx1G"]
  
  :plugins [[lein-environ "1.1.0"]]

  :min-lein-version "2.5.0"
  :uberjar-name "core.jar"
  :main lucifero.core

  :clean-targets ^{:protect false}
  [:target-path
   [:builds :app :compiler :output-dir]
   [:builds :app :compiler :output-to]]

  :source-paths ["src"]
  :resource-paths ["resources"]

  :profiles {:dev {:env {:dev true}}

             :uberjar {:env {:production true}
                       :aot :all
                       :omit-source true}})
