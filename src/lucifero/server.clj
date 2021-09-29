(ns lucifero.server
  (:require [config.core :refer [env]]
            [ring.middleware.file :refer [wrap-file]]
            [ring.util.response :refer [file-response]]
            [ring.adapter.jetty :refer [run-jetty]]
            [compojure.core :refer [defroutes GET]]
            [compojure.route :as route]))

(defonce server (atom nil))

(defroutes app-routes
  (GET "/" []
    (file-response "index.html"))
  (route/not-found "Not Found"))

(defn app [root]
  (wrap-file app-routes root))

(defn start-server [& {:keys [root]}]
  (let [port (or (env :port) 3000)]
    (try
      (reset! server
              (run-jetty (app root) {:port port
                                     :join? false}))
      (catch Exception ex
        (throw (Exception. "Error starting server" ex))))))

(defn stop-server []
  (try
    (when @server
      (.stop @server)
      (reset! server nil))
    (catch Exception ex
      (throw (Exception. "Error stopping server" ex)))))
