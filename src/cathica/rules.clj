(ns cathica.rules
  (:require
   [cathica.plumb :refer [rule]]
   [cathica.logging :refer [with-logging-status]]
   [cathica.utils :refer [is-file is-dir]]
   [clojure.java.io :as io]
   [clojure.java.shell :refer [sh]]
   [clojure.string :as string]
   [taoensso.timbre :as log]
   [slingshot.slingshot :refer [throw+ try+]]
   [mount.core :as mount :refer [defstate]]
   [clojure.edn :as edn]))

(defn browse [url]
  (sh "chromium-browser" url))

(defn emacs-open [file]
  (sh "emacsclient" "--no-wait" file))

(def +default-rules+
  [(rule
     {:type :text
      :data #"(\d{4})-(\d{2})-(\d{2})T\d{2}:\d{2}:\d{2}Z?"
      :start (browse (format "https://calendar.google.com/calendar/r/week/%s/%s/%s" $1 $2 $3))})
   (rule
     {:type :text
      :data #"rfc822msgid:(.+)|msgid://(.+)"
      :start (browse (format "https://mail.google.com/#search/rfc822msgid:%s" $1))})
   (rule
     {:type :text
      :data #"([0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9])'"
      :start (browse (str "http://www.amazon.com/s/?field-keywords=" $1))})
   (rule
     {:type :text
      :data #"Build[ ]*#([0-9]+)"
      :start (browse (str "https://jenkins/job/gerrit_review_build/" $1))})
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
      :data #"(file://)?([a-zA-Z0-9_\-\:\. \(\)\/]+)"
      :arg (is-file $2)
      :start (emacs-open $arg)})
   (rule
     {:type :text
      :src #"google-chrome|chromium-browser|brave|Navigator"
      :data #"([a-zA-Z]+)"
      :start (sh "search_dict" $1)})
   (rule
     {:type :text
      :src #"google-chrome|chromium-browser|brave|Navigator"
      :data #"([a-zA-Z ]+)"
      :start (sh "search_web" $1)})
   (rule
     {:type :text
      :src #"google-chrome|mail\.google\.com__.*|emacs|Alacritty|urxvt256c"
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
