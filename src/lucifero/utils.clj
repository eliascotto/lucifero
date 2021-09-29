(ns lucifero.utils
  (:require [config.core :refer [env]]))

(def ^:dynamic *exit-process?*
  "Bind to false to suppress process termination." (not (:dev env)))

(def ^:dynamic *debug*
  "Debug configuration." (:dev env))

(defn warn
  "Print to stderr."
  [& args]
  (binding [*out* *err*]
    (apply println args)))

(defn debug
  "Print to stdout if in development enviroment."
  [& args]
  (when *debug*
    (apply println (cons "DEBUG:" args))))

(defn log
  "Print log string to stdout, prepending website name."
  [config & s]
  (println (str (:name config) " - " (reduce str s))))

(defn exit
  "Exit the process. Rebind *exit-process?* in order to suppress actual process
  exits for tools which may want to continue operating (REPL)."
  ([exit-code & msg]
   (when (seq? msg)
     (println (first msg)))
   (when *exit-process?*
     (shutdown-agents)
     (System/exit exit-code)))
  ([] (exit 0)))
