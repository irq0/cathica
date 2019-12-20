(ns cathica.repl
  (:require
   [nrepl.server :refer [start-server stop-server]]
   [mount.core :refer [defstate]]))

(defn nrepl-handler []
  (require 'cider.nrepl)
  (ns-resolve 'cider.nrepl 'cider-nrepl-handler))

(defstate nrepl-server
  :start (start-server :port 42001 :handler (nrepl-handler))
  :stop (stop-server nrepl-server))
