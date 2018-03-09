(ns cathica.core
  (:require
   [cathica.CathicaDBusConnection]
   [cathica.logging :refer [with-logging-status]]
   [clojure.java.io :as io]
   [clojure.java.shell :refer [sh]]
   [taoensso.timbre :as log]
   [slingshot.slingshot :refer [throw+ try+]]
   [mount.core :as mount :refer [defstate]]
   [clojure.edn :as edn]
   [clojure.reflect :refer [reflect]]
   )
  (:import [org.freedesktop.dbus DBusConnection DBusSignal DBusSigHandler DBusInterface DBusMatchRule]
           [cathica.CathicaDBusConnection]
           )
  )

;; (:import [org.jnativehook.keyboard NativeKeyListener NativeKeyEvent]
;;            [org.jnativehook NativeHookException GlobalScreen])


(defprotocol Plumb
  (plumb-clipboard [x]))


(extend-type DBusInterface
  Plumb
  (plumb-clipboard [x]
    (log/info "plumb clipboard" x)))

(mount/in-clj-mode)

(defstate dbus
  :start (doto (DBusConnection/getConnection DBusConnection/SESSION)
           (.requestBusName "org.irq0.cathica"))
  :stop (.disconnect dbus))




(defn desktop-notification [title body & args]
  (.callWithCallback dbus notif-obj "Notify"
    (reify org.freedesktop.dbus.CallbackHandler
      (^void handle [_ x] (log/info "notif fin" x))
      (^void handleError
       [_ ^org.freedesktop.dbus.exceptions.DBusExecutionException e]
       (log/info "notif err")))
    (to-array ["org.irq0.cathica"
               (org.freedesktop.dbus.UInt32. 0)
               ""
               "title - summary"
               "body"
               (to-array [])
               {}
               5])))

;;   :start (reset! (signal-atom "USR2") [plumb-current-clipboard])
;;   :stop (reinit! "USR2"))

;; dbus-send --session --dest=or
;; dbus-send --session --dest=org.naquadah.awesome.awful /com/console/sleep com.console.sleep.sleepMessage string:$(python ~/HomeDev/temp/sleep.py)


;; (def info (. bus (getRemoteObject "org.apertium.info" "/" (class org.apertium.Info))))

;; (doseq mode (. info (modes))
;;        (prn mode))

;; (defstate signal-handler
;;   :start (reset! (signal-atom "USR2") [plumb-current-clipboard])
;;   :stop (reinit! "USR2"))

(defn start []
  (with-logging-status)
  (mount/start))

(defn stop []
  (mount/stop))

;; global keyboard listener - listens to all - passwords and shit !

(defonce clipboard (.getSystemClipboard (java.awt.Toolkit/getDefaultToolkit)))
(defonce selection (.getSystemSelection (java.awt.Toolkit/getDefaultToolkit)))

;; alternative - sigusr1 handler

(defn get-current-clipboard [])

(defn prefered-representation-class [mime-type]
  (cond
    (re-find #"text/x-moz-url" mime-type) java.nio.ByteBuffer
    (re-find #"text/.+" mime-type) java.lang.String
    (re-find #"(image|audio|video)/.+" mime-type) java.io.InputStream))


(defn get-clipboard [cb]
  (let [want-mime-types #"(text/(plain|x-moz-url|html))|(image/(png|jpeg|gif|bmp|webp))|(audio/(midi|mpeg|webm|ogg|wav))|(video/(webm|ogg))"]
    (into {} (keep (fn [flavor]
                     (let [mime-type (.getHumanPresentableName flavor)
                           repr-class (.getRepresentationClass flavor)]
                       (log/spy [mime-type repr-class])
                       (when (and (re-find want-mime-types mime-type)
                               (= (prefered-representation-class mime-type) repr-class))
                         [(str mime-type) (.getData cb flavor)])))
               (.getAvailableDataFlavors cb)))))

(defn current-window-info []
  (-> (sh "current-window") :out edn/read-string))


(defn expand-file [wdir s]
  (if (.isAbsolute (io/as-file s))
    (.getAbsoluteFile (io/as-file s))
    (.getAbsoluteFile (java.io.File. wdir s))))

(defn is-file [wdir s]
  (let [file (expand-file wdir s)]
    (when (.isFile file) file)))

(defn is-dir [wdir s]
  (let [file (expand-file wdir s)]
    (when (.isDirectory file) file)))

(defn browse [url]
  (sh "google-chrome" url))

(defn emacs-open [file]
  (sh ["emacsclient" "--no-wait" file]))

(defn type-is-text [type]
  (#{"STRING" "UTF8_STRING" "TEXT" "text/plain"} type))

(defn type-is-image [type]
  (re-find #"image/.*" type))

(defn type-is-url [type]
  (= "text/x-moz-url" type))

(def str-msg {:src "google-chrome"
              :dst nil
              :type "UTF8_STRING"
              :wdir nil
              :data "http://irq0.org"})

(def url-msg {:src "google-chrome"
              :dst nil
              :type "text/x-moz-url"
              :wdir nil
              :data "http://irq0.org"})

(def file-msg {:src "rxvt"
              :dst nil
              :type "UTF8_STRING"
              :wdir "/home/seri"
              :data "bin/seri_plumb"})

(def file-url-msg {:src "emacs"
              :dst nil
              :type "UTF8_STRING"
              :wdir "/home/seri/tmp"
              :data "file:///home/seri/Pictures/wallpaper/suedamerika_2016-11-11_13:37:16_machupicchu_41.JPG"})

(def image-msg {:src "emacs"
                :dst nil
                :type "image/jpeg"
                :wdir "/home/seri/tmp"
                :data (slurp "file:///home/seri/Pictures/wallpaper/IMG_3083.JPG")})

(defn plumb [rules message])

(defmacro rule
  "Define plumbing rule"
  [{:keys [src type data start arg] :as rule}]
  (let [type-fn (case type
                  :text type-is-text
                  :url type-is-url
                  :image type-is-image
                  :default type-is-text)

        data-match (if (nil? data)
                     '(constantly true)
                     `#(re-matches ~data (:data %)))
        src-match (if (nil? src)
                    '(constantly true)
                    `#(re-find ~src (:src %)))

        arg-match (if (nil? arg)
                    '(constantly "")
                    `#(~(first arg) (:wdir %) ~(second arg)))]
    `(fn [message#]
       (try
         (let [matches# (~data-match message#)
               n-matches# (count matches#)
               ~'$0 (if (> n-matches# 0) (nth matches# 0) "")
               ~'$1 (if (> n-matches# 1) (nth matches# 1) "")
               ~'$2 (if (> n-matches# 2) (nth matches# 2) "")
               ~'$3 (if (> n-matches# 3) (nth matches# 3) "")
               ~'$4 (if (> n-matches# 4) (nth matches# 4) "")
               ~'$5 (if (> n-matches# 5) (nth matches# 5) "")
               arg# (~arg-match message#)
               ~'$arg (str arg#)]
           (if (and
                 (~type-fn (:type message#))
                 (some? matches#)
                 (~src-match message#)
                 arg#)
             ~start
             false))
         (catch Exception e#
           (log/error e# "Unexpected error during rule execution: " (quote ~rule) message#)
           false)))))

(defn plumb [rules message]
  (->> rules
    (map #(% message))
    (filter some?)
    first))

;; special rule things:
;; $1 is first regex group in data
;; $arg is the result of the arg function
;; - always called with (arg-function wdir $0..$5)
;; type shortcuts:
;; :text matches test types
;; :image matches image types
;; :url ...

(def rules
  [(rule
     {:type :text
      :data #"([0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9])'"
      :start (browse (str "http://www.amazon.com/s/?field-keywords=" $1))})
   (rule
     {:type :text
      :data #"#([0-9]+)"
      :start (browse (str "https://mantis/view.php?id=" $1))})
   (rule
     {:type :text
      :data #"(https?|ftp)://[a-zA-Z0-9_@\-]+([.:][a-zA-Z0-9_@\-]+)*/?[a-zA-Z0-9_?,%#~&/\-+=]+([:.][a-zA-Z0-9_?,%#~&/\-+=]+)*"
      :start (browse $0)})
   (rule
     {:type :text
      :data #"Change-Id: ([a-zA-Z0-9]+)"
      :start (browse (str "https://gerrit/#/q/" $1))})
   (rule
     {:type :text
      :data #"(file://)?([a-zA-Z0-9_\-\: \/]+\.html?|htm)"
      :arg (is-file $2)
      :start (browse $arg)})
   (rule
     {:type :text
      :data #"([a-zA-Z0-9]+\.)+[a-zA-Z0-9]+\([a-zA-Z0-9]+\.java\:[0-9]+\)"
      :start (sh "open_by_stacktrace_line java" $0)})
   (rule
     {:type :text
      :data #"(file://)?(\p{Print}+\.(png|PNG|jpe?g|JPE?G|gif|GIF|tiff?|TIFF?))"
      :arg (is-file $2)
      :start (sh "feh" $arg)})
   (rule
     {:type :text
      :data #"[a-zA-Z0-9\._%+\-]+@[a-zA-Z0-9\.\-]+\.[a-zA-Z]+"
      :start (sh "gmail" $0)})
   (rule
     {:type :text
      :data #".*[a-zA-Z0-9 \-_]+ \<([a-zA-Z0-9\._%+\-]+@[a-zA-Z0-9\.\-]+\.[a-zA-Z]+)\>.*"
      :start (sh "gmail" $0)})
   (rule
     {:type :text
      :data #"(file://)?([a-zA-Z0-9_\-\: \/]+\.svg|SVG)"
      :arg (is-file $2)
      :start (browse $arg)})
   (rule
     {:type :text
      :data #".*"
      :arg (is-dir $0)
      :start (emacs-open $arg)})
   (rule
     {:type :text
      :data #"(file://)?([a-zA-Z0-9_\-\:\. \(\)]+)"
      :start (emacs-open $arg)})
   (rule
     {:type :text
      :src #"google-chrome"
      :data #"([a-zA-Z]+)"
      :start (sh "search_dict" $1)})
   (rule
     {:type :text
      :src #"google-chrome"
      :data #"([a-zA-Z ]+)"
      :start (sh "search_web" $1)})
   (rule
     {:type :text
      :data #"Build[ ]*#([0-9]+)"
      :start (browse (str "https://jenkins/job/gerrit_review_build/" $1))})
   (rule
     {:type :text
      :src #"google-chrome|mail\.google\.com__.*|emacs"
      :data #"[0-9a-f]{5,}"
      :start (sh "open_magit_commit" $0)})
   (rule
     {:type :text
      :data #"std::[a-zA-Z]+"
      :start (browse (str "http://en.cppreference.com/mwiki/index.php?title=Special%3ASearch&search=" $0))})
   (rule
     {:type :text
      :data #"(RFC|rfc)[ ]*([0-9]+)"
      :start (browse (str "https://www.rfc-editor.org/rfc/rfc" $2 ".txt"))})
   ])


(defn plumb-current-clipboard []
  (log/info "Signal recvd. Plumbing current clipboard and active window")
  (let [win (current-window-info)
        sel (get "text/plain" (get-clipboard selection))

        msg {:src (:window-class win)
             :type "text/plain"
             :wdir (:wdir win)
             :data sel}]
    (plumb rules msg)))

(defn plumb-string [s]
  (log/info "Signal recvd. Plumbing string: " s)
  (let [win (current-window-info)
        msg {:src (:window-class win)
             :type "text/plain"
             :wdir (:wdir win)
             :data s}]
    (plumb rules msg)))

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
                (plumb-current-clipboard))
               (^void string [^DBusInterface this ^String s]
                (plumb-string s))))
           path)
  :stop (.unExportObject dbus dbus-signal-handler))
