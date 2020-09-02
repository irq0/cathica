(ns cathica.repl
  (:require
   [nrepl.server :as nrepl]
   [mount.core :refer [defstate]]))

(defn nrepl-handler []
  (require 'cider.nrepl)
  (ns-resolve 'cider.nrepl 'cider-nrepl-handler))

(defstate nrepl-server
  :start (nrepl/start-server :port 42001 :handler (nrepl-handler))
  :stop (nrepl/stop-server #'nrepl-server))
