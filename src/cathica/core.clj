(ns cathica.core
  (:require
   [cathica.logging :as logging]
   [cathica.dbus]
   [cathica.repl]
   [cathica.ui :as ui]
   [mount.core :as mount]
   [taoensso.timbre :as log]))

(defn -main [& _]
  (mount/in-clj-mode)
  (logging/with-logging-status)
  (logging/setup)
  (ui/setup)
  (mount/start)
  (log/info "☀"))
