(ns cathica.rules
  (:require
   [cathica.regex :as regex]
   [cathica.actions :as actions]
   [cathica.rule :refer [rule]]
   [cathica.utils :refer [is-file is-dir git-resolve-file format-interval human-duration-short]]
   [clj-time.coerce :as tc]
   [clj-time.core :as time]
   [clj-time.format :as tf]
   [clj-time.local :as tl]
   [clojure.java.shell :refer [sh]]
   [clj-http.client :as http]
   [clojure.string :as string]
   [byte-streams :as bytestreams]
   [pantomime.mime :as pantomime]
   [taoensso.timbre :as log]
   [mount.core :as mount :refer [defstate]]
   [clojure.edn :as edn]))

(def 🖖-bookmark-url "http://10.23.1.23:8023/reader/bookmark/add")
(def 🖖-bookmark-item-base-url "http://10.23.1.23:8023/reader/group/type/bookmark/source/all/item/by-id/")

(def terminal-re #"urxvt256c|Alacritty|main")
(def web-browser-re #"Navigator|google-chrome")
(def bugtracker-base-url "https://mantis/view.php?id=")
(def gerrit-change-base-url "https://gerrit/#/q/")

(defonce selection (.getSystemSelection (java.awt.Toolkit/getDefaultToolkit)))

(def query-string-re #"(?U)([\w\"':-\\+/ ]+)")

(def web-search-engines
  [["Amazon" #(str "http://www.amazon.de/exec/obidos/external-search?keyword=" %)]
   ["Google" #(str "https://www.google.com/complete/search?client=firefox&q=" %)]
   ["Reddit" #(str "https://www.reddit.com/search?q=" %)]
   ["GitHub" #(str "https://github.com/search?ref=opensearch&q=" %)]
   ["Google Maps" #(str "https://www.google.com/maps/search/" % "?hl=en&source=opensearch")]
   ["Wikipedia" #(str "https://en.wikipedia.org/w/index.php?sort=relevance&search=" %
                      "&title=Special:Search&profile=advanced&fulltext=1")]
   ["Cppreference" #(str "https://en.cppreference.com/mwiki/index.php?search=" %)]
   ["Ebay" #(str "https://www.ebay.de/sch/i.html?_nkw=" %)]
   ["DevDocs" #(str "https://devdocs.io/#q=" %)]])

(def +web-search-rules+
  (for [[query-engine-name url-fn] web-search-engines]
    (rule (str "Search " query-engine-name)
          {:type :text
           :data query-string-re
           :start (actions/browse (url-fn (java.net.URLEncoder/encode $1 "UTF-8")))})))

(def logistics-tracing
  [["DHL" #"([0-9]{10,})" #(str "https://www.dhl.de/en/privatkunden/"
                                "pakete-empfangen/verfolgen.html?piececode=" %)]
   ["17track" #"(\w+)" #(str "https://t.17track.net/de#nums=" %)]])

(def +logistics-tracing+
  (for [[query-engine-name regex url-fn] logistics-tracing]
    (rule (str "Track and Trace: " query-engine-name)
          {:type :text
           :data regex
           :start (actions/browse (url-fn (java.net.URLEncoder/encode $1 "UTF-8")))})))

(def +specialized-search-rules+
  [(rule "Manits Issue"
         {:type :text
          :data #"#([0-9]+)"
          :start (actions/browse (str bugtracker-base-url $1))})
   (rule "Gerrit Change"
         {:type :text
          :data #"Change-Id: ([a-zA-Z0-9]+)"
          :start (actions/browse (str gerrit-change-base-url $1))})
   (rule "c++: search cppreference for std::"
         {:type :text
          :data #"std::[a-zA-Z]+"
          :start (actions/browse (str "http://en.cppreference.com/mwiki/index.php?title=Special%3ASearch&search=" $0))})
   (rule "Browse RFC"
         {:type :text
          :data #"(RFC|rfc)[ ]*([0-9]+)"
          :start (actions/browse (str "https://www.rfc-editor.org/rfc/rfc" $2 ".txt"))})
   (rule "Search Web"
         {:type :text
          :data #"(?U).+"
          :start (sh "search_web" (string/trim $0))})
   (rule "Search Dictionary"
         {:type :text
          :data #"(?U).+"
          :start (sh "search_dict" (string/trim $0))})])

(def +rules+
  [(rule "Google Calendar"
         {:type :text
          :data #"(\d{4})-(\d{2})-(\d{2})T\d{2}:\d{2}:\d{2}Z?"
          :start (actions/browse (format "https://calendar.google.com/calendar/r/week/%s/%s/%s" $1 $2 $3))})
   (rule "Search ISBN on Amazon"
         {:type :text
          :data #"([0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9])'"
          :start (actions/browse (str "http://www.amazon.com/s/?field-keywords=" $1))})
   (rule "Browse URL"
         {:type :text
          :data regex/url
          :start (actions/browse $0)})
   (rule "Download URL to Desktop"
         {:type :text
          :data regex/url
          :start (actions/download-url $0 (str (System/getProperty "user.home") "/Desktop"))})
   (rule "Browse local html"
         {:type :text
          :data #"(?U)(file://)?([a-zA-Z0-9_\-\: \/]+\.html?|htm)"
          :arg (is-file $2)
          :start (actions/browse $arg)})
   (rule "Open java file matching stacktrace line"
         {:type :text
          :data #"([a-zA-Z0-9]+\.)+[a-zA-Z0-9]+\([a-zA-Z0-9]+\.java\:[0-9]+\)"
          :start (sh "open_by_stacktrace_line java" $0)})
   (rule "Image viewer"
         {:type :text
          :data #"(?U)(file://)?(\p{Print}+\.(png|PNG|jpe?g|JPE?G|gif|GIF|tiff?|TIFF?))"
          :arg (is-file $2)
          :start (sh "feh" $arg)})
   (rule "Image viewer"
         {:type :image
          :start (let [extension (pantomime/extension-for-name $TYPE)
                       tmp-file (java.io.File/createTempFile "cathica-image-" extension)]
                   (bytestreams/transfer $DATA tmp-file)
                   (sh "feh" (.getAbsolutePath tmp-file))
                   (.deleteOnExit tmp-file))})
   (rule "Document viewer"
         {:type :text
          :data #"(?U)(file://)?(\p{Print}+\.(pdf))"
          :arg (is-file $2)
          :start (sh "mupdf" $arg)})
   (rule "Video Player: Youtube Link"
         {:type :text
          :data #"(?:http(?:s?):\/\/)?(?:www\.)?youtu(?:be\.com\/watch\?v=|\.be\/)([\w\-\_]*)(&(amp;)?‌​[\w\?‌​=]*)?"
          :start (sh "mpv" $0)})
   (rule "Video Player: Download in Background"
         {:type :text
          :data #"(?:http(?:s?):\/\/)?(?:www\.)?youtu(?:be\.com\/watch\?v=|\.be\/)([\w\-\_]*)(&(amp;)?‌​[\w\?‌​=]*)?"
          :start (future
                   (actions/desktop-notification "Starting youtube download" $1)
                   (let [filename (actions/youtube-dl-video
                                   $1)]
                     (actions/desktop-notification "Download finished" filename)))})
   (rule "Gmail - msgid"
         {:type :text
          :data #"rfc822msgid:(.+)|msgid://(.+)"
          :start (actions/browse (format "https://mail.google.com/#search/rfc822msgid:%s" $1))})
   (rule "Gmail - Write Email"
         {:type :text
          :data #"[a-zA-Z0-9\._%+\-]+@[a-zA-Z0-9\.\-]+\.[a-zA-Z]+"
          :start (sh "gmail" $0)})
   (rule "Gmail - Write Email"
         {:type :text
          :data #".*[a-zA-Z0-9 \-_]+ \<([a-zA-Z0-9\._%+\-]+@[a-zA-Z0-9\.\-]+\.[a-zA-Z]+)\>.*"
          :start (sh "gmail" $0)})
   (rule "Open Mangeled Log Filename in Emacs"
         {:type :text
          :data #"…([\w_\/]+\.\w+):(\d+)"
          :src regex/terminal
          :arg (git-resolve-file $1)
          :start (actions/emacs-open $arg [(str "+" $2 ":" 0)])})
   (rule "Open Compile Error in Emacs"
         {:type :text
          :data #"^../([a-zA-Z0-9_\-\:\. \(\)\/]+):(\d+):(\d+):\s+(\w+):\s+(.+)"
          :src regex/terminal
          :arg (is-file $1)
          :start (do
                   (actions/desktop-notification (str "Compiler " $4 " in " $1 " line " $2) $5 :expire-time 0)
                   (actions/emacs-open $arg [(str "+" $2 ":" $3)]))})
   (rule "Show timestamp range"
         {:type :text
          :data #"(?s).+_timestamp_ms: (\d+)\n.+_timestamp_ms: (\d+)"
          :start (let [start (tc/from-long (Long/parseLong $1))
                       end (tc/from-long (Long/parseLong $2))
                       start-local (time/to-time-zone start (time/default-time-zone))
                       end-local (time/to-time-zone end (time/default-time-zone))]
                   (actions/desktop-notification "ts range:"
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
   (rule "Convert Qtimestamps"
    {:type :text
     :data #".*(timestamp|time)_(ms|s): (\d+)"
     :start (let [unit-multiplier (case $2
                                    "ms" 1
                                    "s" 1000)
                  ts (tc/from-long (* unit-multiplier (Long/parseLong $3)))
                  show-ts ["UTC" "Europe/Berlin" "US/Pacific" "US/Central" "US/Eastern"]
                  msg (string/join "\n"
                                   (map #(str % ": "
                                              (tl/format-local-time
                                               (time/to-time-zone
                                                ts
                                                (time/time-zone-for-id %)) :mysql))
                                        show-ts))]
              (actions/desktop-notification (str "ts: " $0) msg :expire-time 0)
              (actions/set-clipboard-string selection msg))})
   (rule "Convert Duration (to clipboard)"
         {:type :text
          :data #"(\d+)(ms|ns|s)"
          :start (let [unit-multiplier (case $2
                                         "ms" 1
                                         "ns" 1/1000
                                         "s" 1000)
                       millis (* (Long/parseLong $1)
                                 unit-multiplier)
                       msg (human-duration-short
                            (org.joda.time.Period. (long millis)))]
                   (actions/set-clipboard-string selection msg)
                   (actions/desktop-notification (str "duration: " $0) msg :expire-time 0))})
   (rule "Convert US Mass Unit"
         {:type :text
          :data #"([\d/ ]+)(ounce|pound|teaspoon|tablespoon|cup|pint)s?"
          :start (let [gram-multiplier (case $2
                                         "ounce" 28.349523125
                                         "pound" 453.59237
                                         "teaspoon" 4.93
                                         "tablespoon" 14.79
                                         "pint" 568.26
                                         "cup" 284.13)
                       grams (* (edn/read-string $1) gram-multiplier)
                       msg (format "%.2fg" grams)]
                   (actions/set-clipboard-string selection msg)
                   (actions/desktop-notification $0 msg :expire-time 0))})
   (rule "Convert Temperature Units (℃, ℉)"
         {:type :text
          :data #"([\d,\.]+)\s*(degree|°|℃|℉|celsius|farenheit)"
          :start (let [value (log/spy (edn/read-string (log/spy $1)))
                       c-to-f (float (+ (* value 9/5) 32))
                       f-to-c (float (* (- value 32) 5/9))
                       msg (format "℃ ➙ ℉ %.2f\n℉ ➙ ℃ %.2f" c-to-f f-to-c)]
                   (actions/desktop-notification $0 msg :expire-time 0))})

   (rule "Browse svg" {:type :text
                       :data #"(file://)?([a-zA-Z0-9_\-\: \/]+\.svg|SVG)"
                       :arg (is-file $2)
                       :start (actions/browse $arg)})
   (rule "Emacs: dired"
         {:type :text
          :data #".*"
          :arg (is-dir $0)
          :start (actions/emacs-open $arg)})
   (rule "Emacs: edit"
         {:type :text
          :data #"(file://)?([a-zA-Z0-9_\-\:\. \(\)\/]+)"
          :arg (is-file $2)
          :start (actions/emacs-open $arg)})
   (rule "Emacs: Commit in Magit"
         {:type :text
          :data #"[0-9a-f]{5,}"
          :start (sh "open_magit_commit" $0)})])

(defn 🖖-bookmark [type url]
  (future
    (let [resp (http/post 🖖-bookmark-url
                          {:content-type :json
                           :form-params {:url url
                                         :type type}})
          item-id (:body resp)
          🖖-url (str 🖖-bookmark-item-base-url item-id)]

      (actions/desktop-notification "bookmark Ready"
                                    🖖-url)
      (actions/browse 🖖-url))))

(def +🖖-rules+
  [(rule
    "🖖 Add Raw Bookmark"
    {:type :text
     :data regex/url
     :start (🖖-bookmark :raw-bookmark $0)})
   (rule
    "🖖 Add Readability Bookmark"
    {:type :text
     :data regex/url
     :start (🖖-bookmark :readability-bookmark $0)})])

(defstate plumbing-rules
  :start (concat
          +rules+
          +specialized-search-rules+
          +web-search-rules+
          +🖖-rules+
          +logistics-tracing+))
