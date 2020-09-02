(ns cathica.dbus
  (:require
   [cathica.plumb :as plumb]
   [taoensso.timbre :as log]
   [mount.core :as mount :refer [defstate]])
  (:import [org.freedesktop.dbus DBusConnection DBusInterface]))

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

(defstate dbus-signal-handler
  :start (let [path "/org/irq0/cathica/Plumb"]
           (.exportObject
            dbus
            path
            (reify org.irq0.cathica.PlumbInterface
              (^void clipboard [^DBusInterface this]
                (plumb/plumb-current-clipboard :with-picker? true))
              (^void clipboard_default_action [^DBusInterface this]
                (plumb/plumb-current-clipboard :with-picker? false))
              (^void string [^DBusInterface this ^String s]
                (plumb/plumb-string s :with-picker? true))
              (^void string_default_action [^DBusInterface this ^String s]
                (plumb/plumb-string s :with-picker? false))))
           path)
  :stop (.unExportObject dbus dbus-signal-handler))
