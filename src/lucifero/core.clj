(ns lucifero.core
  (:refer-clojure :exclude [read])
  (:require [clojure.set :as set]
            [clojure.walk :as walk]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.tools.cli :refer [parse-opts]]
            [me.raynes.fs :as fs]
            [lucifero.utils :as utils]
            [lucifero.defaults :as defaults]
            [lucifero.parser :as parser]
            [lucifero.shared :refer [get-path]]
            [lucifero.server :as server]
            [asset-minifier.core :refer [minify-css
                                         minify-js]]
            [clj-html-compressor.core :as compressor]
            [juxt.dirwatch :refer [watch-dir]])
  (:import (java.io Reader))
  (:gen-class))

(def ^:dynamic *cwd*
  "Current working directory." (System/getProperty "user.dir"))

(defn file-with-extension?
  "Returns true if the java.io.File represents a file whose name ends
  with one of the Strings in extensions."
  [^java.io.File file extensions]
  (and (.isFile file)
       (let [name (.getName file)]
         (some #(.endsWith name %) extensions))))

(defn page-file?
  "Returns true if the java.io.File represents a file with supported
  extension."
  [^java.io.File file]
  (file-with-extension? file (vals defaults/pages-extensions)))

(defn is-markdown?
  [^java.io.File file]
  (file-with-extension? file (list (:markdown defaults/pages-extensions))))

(defn create-page-url
  "Create an absolute url for the page."
  [page config]
  (str (:baseurl config) "/" (string/replace (str page) #"index.html" "")))

(defn copy-public-dir
  "Copy the public directory into the build, if exists."
  [config]
  (let [public (get-path config :public-dir)
        dist   (:dest-dir config)]
    (fs/copy-dir-into public dist)
    (utils/log config (format "Cloned %s directory to %s" public dist))))

(defn write-page
  "Write page inside the distributable directory."
  [path content]
  (with-open [file (io/writer path)]
    (.write file content)))

(defn create-new-dist
  "Create a new distributable directory."
  [config]
  (let [dist-dir (:dest-dir config)]
    (fs/delete-dir dist-dir)
    (fs/mkdir dist-dir)
    (utils/log config (format "Created new directory %s" dist-dir))))

(defn minify-resources
  [config]
  (let [public  (get-path config :public-dir)
        dest    (:dest-dir config)
        css-dir (io/file (str public) "css")
        js-dir  (io/file (str public) "js")
        has-dir? #(and (fs/exists? %) (fs/directory? %))]
    (when (has-dir? css-dir)
      (minify-css css-dir (str (io/file dest "css" "styles.min.css"))))
    (when (has-dir? js-dir)
      (minify-js js-dir (str (io/file dest "js" "scripts.min.js"))))))

(defn build-website
  "Build the website using the configuration."
  [config]
  (utils/debug "Inside build website")
  (let [pages-dir (get-path config :pages-dir)
        pages     (rest (file-seq pages-dir))]
    (when-not (and (fs/exists? (str pages-dir))
                   (fs/directory? (str pages-dir)))
      (throw (Exception. "Configured :pages-dir is not a valid directory.")))
    ;; Initial build actions
    (parser/configure-selmer config)
    (create-new-dist config)
    (copy-public-dir config)
    (minify-resources config)
    ;; Page parsing content-layout
    (doseq [page pages]
      (when (page-file? page)
        (utils/log config "Building page " (str page))
        (let [config (-> config ; merge :variables into site configuration
                         (merge (:variables config))
                         (dissoc :variables))
              {page-config :config
               content :content} (if (is-markdown? page)
                                   (parser/parse-markdown page config)
                                   (parser/parse-org page config))
              page-layout     (if (contains? page-config :layout)
                                (:layout page-config)
                                "default")
              page-config-ext (assoc page-config
                                     :url
                                     (create-page-url page config))
              page-html       (parser/parse-layout page-layout
                                                   content
                                                   config
                                                   page-config-ext)
              html-compressed (compressor/compress page-html {:remove-intertag-spaces true})]
          (write-page (str (io/file (:dest-dir config)
                                    (str (fs/name page) ".html")))
                      html-compressed)
          (utils/log config "Built page " (str page)))))))

(defn read-raw
  "Read project file without loading certificates, plugins,
  middleware, etc."
  [source]
  (binding [*ns* (find-ns 'lucifero.core)]
    (try
      (if (instance? Reader source)
        (load-reader source)
        (load-file source))
      (println "Reading configuration file...")
      (catch Exception e
        (throw (Exception. (format "Error loading %s" source) e))))
    ;; Removes website from the namespace, returning is value
    (let [website (resolve 'lucifero.core/website)]
      (when-not website
        (throw (Exception. (format "%s must define a website map" source))))
      (ns-unmap 'lucifero.core 'website)
      @website)))

(defn read
  "Read website map out of file, which defaults to config.clj
  and run the website"
  ([]
   (read "config.clj"))
  ([file]
   (let [config (read-raw file)]
     (build-website config)
     config)))

(defn- unquote-website
  "Inside defwebsite forms, unquoting (~) allows for arbitrary
  evaluation."
  [args]
  (walk/walk (fn [item]
               (cond
                 (and (seq? item) (= `unquote (first item))) (second item)
                 ;; needed if we want fn literals preserved
                 (or (seq? item) (symbol? item)) (list 'quote item)
                 :else (let [result (unquote-website item)]
                         ;; clojure.walk strips metadata
                         (if-let [m (meta item)]
                           (with-meta result m)
                           result))))
             identity
             args))

(defn- argument-list->argument-map
  "Transform the list of arguments to the defwebsite macro
in a map."
  [args]
  (let [keys        (map first (partition 2 args))
        unique-keys (set keys)]
    (if (= (count keys) (count unique-keys))
      (apply hash-map args)
      (let [duplicates (->> (frequencies keys)
                            (remove #(> 2 (val %)))
                            (map first))]
        (throw
         (IllegalArgumentException.
          (format "Duplicate keys: %s"
                  (string/join ", " duplicates))))))))

(defn meta-merge
  "Recursively merge values based on the information in their
  metadata."
  [left right]
  (cond
    (-> left meta :reduce)
    (-> left meta :reduce
        (reduce left right)
        (with-meta (meta left)))

    (and (map? left) (map? right))
    (merge-with meta-merge left right)

    (and (set? left) (set? right))
    (set/union right left)

    (and (coll? left) (coll? right))
    (if (or (-> left meta :prepend)
            (-> right meta :prepend))
      (-> (concat right left)
          (with-meta (merge (meta right) (meta left))))
      (-> (concat left right)
          (with-meta (merge (meta left) (meta right)))))

    (= (class left) (class right)) right

    :else
    (do (utils/warn left "and" right "have a type mismatch merging profiles.")
        right)))

(defn make
  ([website website-name version root]
   (make (with-meta (assoc website
                           :name (name website-name)
                           :group (name website-name)
                           :version version
                           :root root)
           (meta website))))
  ([website]
   (-> (meta-merge defaults/config-defaults website)
       (with-meta (meta website)))))

(defmacro defwebsite
  "Define a website symbol in the namespace with the attached
   configuration map.
  config.clj file must call this macro to define a website."
  [website-name version & args]
  (let [f (io/file *file*)]
    `(let [args# ~(unquote-website (argument-list->argument-map args))
           root# ~(when f (.getParent f))]
       (def ~'website
         (make args# '~website-name ~version root#)))))

(defn start-server
  "Start the server with attached watched for rebuild."
  [conf-file path]
  (let [config (read (str conf-file))]
    (server/start-server :root (:dest-dir config))
    (watch-dir (fn [& _]
                 (println "Files changed, reloading...")
                 (read (str conf-file)))
               (io/file path))))

(defn run
  "Build website starting from the configuration file. Optionally (with :serve)
  start a web server with hot reloading once the build as finished."
  [& {:keys [serve path] :or {serve false}}]
  (try
    (let [conf-file (io/file path "config.clj")]
      (if (.exists conf-file)
        (if serve
          (start-server conf-file path)
          (read (str conf-file)))
        (utils/warn "ERROR"
                    "[conf.clj] - Website configuration file not found.")))
    (catch Exception e
      (println (.getMessage e))
      (flush)
      (utils/exit (:exit-code (ex-data e) 1))))
  (when-not serve
    (utils/exit 0)))

(defn usage [opts]
  (->> ["Lucifero - Simple static website generator"
        ""
        "Available tasks:"
        "  watch\tRun a webserver with hot reloading"
        "  build\tBuild a distributable version"
        ""
        "Options:"
        opts]
       (string/join \newline)))

(def cli-options
  [["-p" "--path PATH" "Path of the project"
    :validate [#(and (fs/exists? %) (fs/directory? %))]]
   ["-h" "--help"]])

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (string/join \newline errors)))

(defn validate-args
  "Validate the command line arguments and return an error in case are not valid."
  [args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    (cond
      (:help options)
      {:exit-message (usage summary) :ok? true}

      errors
      {:exit-message (error-msg errors)}

      (and (= 1 (count arguments))
           (#{"watch", "build", "publish"} (first arguments)))
      {:action (first arguments) :options options}

      :else
      {:exit-message (usage summary)})))

(defn -main
  "Command line entry point."
  [& args]
  (let [{:keys [action options exit-message ok?]} (validate-args args)
        dir-path (if (contains? options :path)
                   (.getAbsolutePath (io/file (:path options)))
                   *cwd*)]
    (if exit-message
      (utils/exit (if ok? 0 1) exit-message)
      (case action
        "watch" (run :serve true
                     :path  dir-path)
        "build" (run)))))

