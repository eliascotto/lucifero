(ns lucifero.parser
  (:require [clojure.string :as string]
            [selmer.parser :as selmer]
            [selmer.filters :as filters]
            [me.raynes.fs :as fs]
            [clj-yaml.core :as yaml]
            [markdown.core :as markdown]
            [lucifero.utils :as utils]
            [lucifero.defaults :as defaults]
            [lucifero.shared :refer [get-path]]))

;; Regex for find YAML header
;;
;; Uses embedded flags for Multiline and Dotall - (?ms)
(def re-yaml-header #"(?ms)(---\s*\n(.|\n)*?\n?)((---|\.\.\.)\s*$\n?)")

(defn create-render-context
  "Create a context map mixing global and local configuration."
  [global-config local-config]
  {:site global-config :page local-config})

(defn extract-yaml-vars
  "Extract a yaml config from the string."
  [s path]
  (let [yaml-header (re-find re-yaml-header s)]
    (when yaml-header
      (let [yaml-str (string/replace (first yaml-header) #"---" "")]
        (try
          (yaml/parse-string yaml-str)
          (catch Exception ex
            (throw (Exception. (format "Error parsing YAML header on %s\n%s\n"
                                       path
                                       yaml-header) ex))))))))

(defn configure-selmer [config]
  ;; Set default path for layouts file from config
  ;; see https://github.com/yogthos/Selmer#resource-path
  (selmer/set-resource-path! (.getAbsolutePath (get-path config :layouts-dir)))
  (doseq [[k f] defaults/selmer-filters]
    (filters/add-filter! k f)))

(defn- parse-file-with-header
  [path]
  (let [file (slurp path)
        vars (extract-yaml-vars file path)]
    [file vars]))

(defn format-selmer-exception
  "Format the selmer exeption for printing."
  [ex]
  (let [header (format (str "Layout validation error:\n"
                            "%s\n"
                            "\tIn file %s")
                       (:error ex)
                       (:template ex))
        body (map (fn [{line :line, tag :tag}]
                    (format "\tTag %s on line %s" tag line))
                  (:validation-errors ex))]
    (string/join "\n" (cons header body))))

(defn parse-layout
  "Parse a layout file from the layouts directory."
  [layout-name content config page-config]
  (let [layout-file (str (get-path config :layouts-dir)
                         "/" layout-name ".html")]
    (if (fs/exists? layout-file)
      (let [[file yaml-vars]     (parse-file-with-header layout-file)
            merged-config        (merge yaml-vars page-config)
            render-ctx           (create-render-context config merged-config)
            context-with-content (assoc render-ctx :content content)]
        (utils/debug "Parsing layout" layout-name)
        (if (and (map? yaml-vars) (contains? yaml-vars :layout))
          ;; If layout variable is present, recursively parse the parent layouts.
          (let [layout-content (string/replace-first file re-yaml-header "")
                layout-render  (selmer/render layout-content context-with-content)]
            (assert (not= layout-name (yaml-vars :layout))
                    (format "Recursive layout on file %s" layout-file))
            (recur (yaml-vars :layout) layout-render config merged-config))
          ;; Otherwise render the layout file with the context
          (try
            (selmer/render-file (str layout-name ".html") context-with-content)
            (catch clojure.lang.ExceptionInfo ex
              (case (:type (ex-data ex))
                :selmer-validation-error
                (throw (Exception. (format-selmer-exception ex) ex))

                (throw (Exception. (format "Error loading layout %s" layout-name) ex)))))))
      (throw (Exception. (format "Layout file %s.html not found inside directory %s."
                                 layout-name
                                 (:layouts-dir config)))))))

(defn parse-org
  [file-name config] ())

(defn parse-markdown
  "Parse a markdown file with YAML configuration header."
  [file-name config]
  (let [[file yaml-vars] (parse-file-with-header (str file-name))
        md-content       (string/replace-first file re-yaml-header "") ; remove yaml config from content
        render-context   (create-render-context config yaml-vars)
        parsed           (-> md-content
                             (selmer/render render-context) ; render context variables
                             (markdown/md-to-html-string))] ; parse markdown
    {:config yaml-vars :content parsed}))
