(ns cathica.dbus
  (:require
   [cathica.core :as core]
   [cathica.logging :refer [with-logging-status]]
   [clojure.java.io :as io]
   [clojure.java.shell :refer [sh]]
   [clojure.string :as string]
   [taoensso.timbre :as log]
   [slingshot.slingshot :refer [throw+ try+]]
   [mount.core :as mount :refer [defstate]]
   [clojure.edn :as edn]
   [clojure.reflect :refer [reflect]]
   )
  (:import [org.freedesktop.dbus DBusConnection DBusSignal DBusSigHandler DBusInterface DBusMatchRule]
           )
  )

(defprotocol Plumb
  (plumb-clipboard [x]))

(extend-type DBusInterface
  Plumb
  (plumb-clipboard [x]
    (log/info "plumb clipboard" x)))



(defstate dbus
  :start (doto (DBusConnection/getConnection DBusConnection/SESSION)
           (.requestBusName "org.irq0.cathica"))
  :stop (.disconnect dbus))


;; (defstate signal-handler
;;   :start (reset! (signal-atom "USR2") [plumb-current-clipboard])
;;   :stop (reinit! "USR2"))

;; (defstate dbus-signal-handler
;;   :start (let [match-rule (make-match-rule "signal" "org.irq0.cathica.Plumber"
;;                             "plumb")
;;                handler (make-dbus-signal-handler plumb-current-clipboard)]
;;            (add-match-signal-handler dbus match-rule handler)
;;            {:match-rule match-rule
;;             :handler handler})
;;   :stop (let [{:keys [match-rule handler]} dbus-signal-handler]
;;           (remove-signal-handler dbus match-rule handler)))


(defstate dbus-signal-handler
  :start (let [path "/org/irq0/cathica/Plumb"]
           (.exportObject
             dbus
             path
             (reify org.irq0.cathica.PlumbInterface
               (^void clipboard [^DBusInterface this]
                (core/plumb-current-clipboard :with-picker? true))
               (^void clipboard_default_action [^DBusInterface this]
                (core/plumb-current-clipboard :with-picker? false))
               (^void string [^DBusInterface this ^String s]
                (core/plumb-string s :with-picker? true))
               (^void string_default_action [^DBusInterface this ^String s]
                (core/plumb-string s :with-picker? false))))
           path)
  :stop (.unExportObject dbus dbus-signal-handler))
