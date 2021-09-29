(ns lucifero.shared
  (:require [clojure.java.io :as io]))

(defn get-path
  "Return the full path from a directory keyword in the config."
  [config path-k]
  (io/file (:directory config) (get config path-k)))
