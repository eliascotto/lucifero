(ns lucifero.defaults
  (:require [clojure.string :as string])
  (:import  (org.jsoup Jsoup)))

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
