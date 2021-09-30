(ns lucifero.defaults
  (:require [clojure.java.io :as io]
            [clojure.string :as string])
  (:import  (org.jsoup Jsoup)))

(def template-dir
  "Default directory for template."
  (io/resource "template"))

(def cli-tasks
  "Tasks available at command line."
  [["watch" "Run a webserver with hot reloading"]
   ["build" "Build a distributable version"]
   ["new"   "Create a new website template"]])

(def config-defaults
  "Default website configuration."
  {:title       "Lucifero"
   :description "Static website generator"
   :baseurl     "https://www.example.com"
   :pages-dir   "pages"
   :public-dir  "public"
   :layouts-dir "layouts"
   :dest-dir    "dist"})

(def pages-extensions
  "Extension supported for pages."
  {:markdown ".md"
   :org      ".org"})

(def selmer-filters
  "Additionally selmer filters"
  {;; Strip html tags from the string
   :strip-html           #(.text (Jsoup/parse %))
   ;; Replace multiple whitespace with one, trimming the string
   :normalize-whitespace #(string/replace (string/trim %) #"\s{2,}" " ")})
