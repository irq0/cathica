(ns cathica.rules
  (:require
   [cathica.plumb :refer [rule desktop-notification]]
   [cathica.logging :refer [with-logging-status]]
   [cathica.utils :refer [is-file is-dir git-resolve-file format-interval human-duration-short]]
   [clj-time.coerce :as tc]
   [clj-time.core :as time]
   [clj-time.format :as tf]
   [clj-time.local :as tl]
   [clojure.java.io :as io]
   [clojure.java.shell :refer [sh]]
   [clojure.string :as string]
   [taoensso.timbre :as log]
   [slingshot.slingshot :refer [throw+ try+]]
   [mount.core :as mount :refer [defstate]]
   [clojure.edn :as edn]))

(def terminal-re #"urxvt256c|Alacritty|main")
(def web-browser-re #"Navigator|google-chrome")
(def bugtracker-base-url "https://mantis/view.php?id=")
(def gerrit-change-base-url "https://gerrit/#/q/")

(defonce selection (.getSystemSelection (java.awt.Toolkit/getDefaultToolkit)))

(defn set-clipboard-string [cb s]
  (.setContents
    cb
    (reify java.awt.datatransfer.Transferable
      (^Object getTransferData [this ^java.awt.datatransfer.DataFlavor flavor]
       s)
      (getTransferDataFlavors [this]
        (into-array java.awt.datatransfer.DataFlavor [(java.awt.datatransfer.DataFlavor/stringFlavor)]))
      (^boolean isDataFlavorSupported [this ^java.awt.datatransfer.DataFlavor flavor]
       (= flavor (java.awt.datatransfer.DataFlavor/stringFlavor))))
    nil))

(defn browse [url]
  (sh "firefox" url))

(defn emacs-open
  ([file extra-args]
   (apply sh (concat
        ["emacsclient" "--no-wait"]
        extra-args
        [file])))
  ([file]
   (sh "emacsclient" "--no-wait" file)))


(def +default-rules+
  [(rule
     {:name "Google Calendar"
      :type :text
      :data #"(\d{4})-(\d{2})-(\d{2})T\d{2}:\d{2}:\d{2}Z?"
      :start (browse (format "https://calendar.google.com/calendar/r/week/%s/%s/%s" $1 $2 $3))})
   (rule
     {:name "Search ISBN on Amazon"
      :type :text
      :data #"([0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9])'"
      :start (browse (str "http://www.amazon.com/s/?field-keywords=" $1))})
   (rule
     {:name "Jenkins Build"
      :type :text
      :data #"Build[ ]*#([0-9]+)"
      :start (browse (str "https://jenkins/job/gerrit_review_build/" $1))})
   (rule
     {:name "Manits Issue"
      :type :text
      :data #"#([0-9]+)"
      :start (browse (str "https://mantis/view.php?id=" $1))})
   (rule
     {:name "Browse URL"
      :type :text
      :data #"(https?|ftp)://[a-zA-Z0-9_@\-]+([.:][a-zA-Z0-9_@\-]+)*/?[a-zA-Z0-9_?,%#~&/\-+=]+([:.][a-zA-Z0-9_?,%#~&/\-+=]+)*"
      :start (browse $0)})
   (rule
     {:name "Gerrit Change"
      :type :text
      :data #"Change-Id: ([a-zA-Z0-9]+)"
      :start (browse (str "https://gerrit/#/q/" $1))})
   (rule
     {:name "Browse local html"
      :type :text
      :data #"(file://)?([a-zA-Z0-9_\-\: \/]+\.html?|htm)"
      :arg (is-file $2)
      :start (browse $arg)})
   (rule
     {:name "Open java file matching stacktrace line"
      :type :text
      :data #"([a-zA-Z0-9]+\.)+[a-zA-Z0-9]+\([a-zA-Z0-9]+\.java\:[0-9]+\)"
      :start (sh "open_by_stacktrace_line java" $0)})
   (rule
     {:name "Image viewer"
      :type :text
      :data #"(file://)?(\p{Print}+\.(png|PNG|jpe?g|JPE?G|gif|GIF|tiff?|TIFF?))"
      :arg (is-file $2)
      :start (sh "feh" $arg)})
   (rule
     {:name "Gmail - msgid"
      :type :text
      :data #"rfc822msgid:(.+)|msgid://(.+)"
      :start (browse (format "https://mail.google.com/#search/rfc822msgid:%s" $1))})
   (rule
     {:name "Gmail - Write Email"
      :type :text
      :data #"[a-zA-Z0-9\._%+\-]+@[a-zA-Z0-9\.\-]+\.[a-zA-Z]+"
      :start (sh "gmail" $0)})
   (rule
     {:name "Gmail - Write Email"
      :type :text
      :data #".*[a-zA-Z0-9 \-_]+ \<([a-zA-Z0-9\._%+\-]+@[a-zA-Z0-9\.\-]+\.[a-zA-Z]+)\>.*"
      :start (sh "gmail" $0)})
   (rule
    {:name "Open Log Filename in Emacs"
     :type :text
     :data #"…([\w_\/]+\.\w+):(\d+)"
     :src terminal-re
     :arg (git-resolve-file $1)
     :start (emacs-open $arg [(str "+" $2 ":" 0)])})

   (rule
    {:name "Open Compile Error in Emacs"
     :type :text
     :data #"^../([a-zA-Z0-9_\-\:\. \(\)\/]+):(\d+):(\d+):\s+(\w+):\s+(.+)"
     :src terminal-re
     :arg (is-file $1)
     :start (do
              (desktop-notification (str "Compiler " $4 " in " $1 " line " $2) $5 :expire-time 0)
              (emacs-open $arg [(str "+" $2 ":" $3)]))})
   (rule
    {:name "Show timestamp range"
     :type :text
     :data #"(?s).+_timestamp_ms: (\d+)\n.+_timestamp_ms: (\d+)"
     :start (let [start (tc/from-long (Long/parseLong $1))
                  end (tc/from-long (Long/parseLong $2))
                  start-local (time/to-time-zone start (time/default-time-zone))
                  end-local (time/to-time-zone end (time/default-time-zone))]
              (desktop-notification "ts range:"
                                    (str
                                     "UTC:      "
                                     (tf/unparse (:mysql tf/formatters) start)
                                     " - "
                                     (tf/unparse (:mysql tf/formatters) end)
                                     "\n"
                                     "Local:    "
                                     (tl/format-local-time start-local :mysql)
                                     " - "
                                     (tl/format-local-time end-local :mysql)
                                     "\n"
                                     "Duration: "
                                     (format-interval (time/interval start end)))
                                    :expire-time 0))})

   (rule
    {:name "Convert Qtimestamps"
     :type :text
     :data #".*(timestamp|time)_(ms|s): (\d+)"
     :start (let [unit-multiplier (case $2
                                    "ms" 1
                                    "s" 1000)
                  ts (tc/from-long (* unit-multiplier (Long/parseLong $3)))
                  show-ts ["UTC" "Europe/Berlin" "US/Pacific" "US/Central" "US/Eastern"]
                  msg (string/join "\n"
                                   (map #(str % ": "
                                              (tl/format-local-time (time/to-time-zone ts (time/time-zone-for-id %)) :mysql))
                                        show-ts))]
              (desktop-notification (str "ts: " $0) msg :expire-time 0)
              (set-clipboard-string selection msg))})

   (rule
    {:name "Convert Duration (to clipboard)"
     :type :text
     :data #"(\d+)(ms|ns|s)"
     :start (let [unit-multiplier (case $2
                                    "ms" 1
                                    "ns" 1/1000
                                    "s" 1000)
                  millis (* (Long/parseLong $1)
                            unit-multiplier)
                  msg (human-duration-short
                       (org.joda.time.Period. (long millis)))]
              (set-clipboard-string selection msg)
              (desktop-notification (str "duration: " msg)  :expire-time 0))})

   (rule
    {:name "Browse svg"
     :type :text
     :data #"(file://)?([a-zA-Z0-9_\-\: \/]+\.svg|SVG)"
     :arg (is-file $2)
     :start (browse $arg)})
   (rule
    {:name "dired"
     :type :text
     :data #".*"
     :arg (is-dir $0)
     :start (emacs-open $arg)})
   (rule
    {:name "emacs edit"
     :type :text
     :data #"(file://)?([a-zA-Z0-9_\-\:\. \(\)\/]+)"
     :arg (is-file $2)
     :start (emacs-open $arg)})
   (rule
    {:name "commit in magit"
     :type :text
     :data #"[0-9a-f]{5,}"
     :start (sh "open_magit_commit" $0)})
   (rule
    {:name "c++: search cppreference for std::"
     :type :text
     :data #"std::[a-zA-Z]+"
     :start (browse (str "http://en.cppreference.com/mwiki/index.php?title=Special%3ASearch&search=" $0))})
   (rule
    {:name "Browse RFC"
     :type :text
     :data #"(RFC|rfc)[ ]*([0-9]+)"
     :start (browse (str "https://www.rfc-editor.org/rfc/rfc" $2 ".txt"))})
   (rule
    {:name "Search dict"
     :type :text
     :data #"([a-zA-Z]+)"
     :start (sh "search_dict" $1)})
   (rule
    {:name "Search web"
     :type :text
     :data #"([a-zA-Z ]+)"
     :start (sh "search_web" $1)})])
