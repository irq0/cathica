(ns cathica.actions
  (:require
   [cathica.ui :as ui]
   [taoensso.timbre :as log]
   [clj-http.client :as http]
   [clojure.java.io :as io]
   [clojure.contrib.humanize :as human]
   [clojure.java.shell :refer [sh]]))

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

(defn desktop-notification
  [title body & {:keys [icon expire-time category]}]
  (apply sh (remove nil? ["notify-send"
                          (when (string? icon)
                            (str "--icon=" icon))
                          (when (number? expire-time)
                            (str "--expire-time=" expire-time))
                          (when (string? category)
                            (str "--category=" category))
                          title
                          body])))

(defn browse [url]
  (sh "xdg-open" url))

(defn emacs-open
  ([file extra-args]
   (apply sh (concat
              ["emacsclient" "--no-wait"]
              extra-args
              [file])))
  ([file]
   (sh "emacsclient" "--no-wait" file)))

(defn emacs-eval
  [code]
  (sh "emacsclient" "--no-wait" "--eval" code))

(defn youtube-dl-video [url]
  (let [{:keys [exit out err]}
        (sh
         "youtube-dl"
         "--format" "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best"
         "--recode-video" "mp4"
         "--embed-subs"
         url
         :dir
         (str (System/getProperty "user.home") "/Desktop/MEDIA"))
        youtube-err (second (re-find #"YouTube said:\s+(.+)$" err))]
    (log/info "Youtube-dl OUT: " out)
    (log/debug "Youtube-dl ERR: " err)
    (if (zero? exit)
      (let [[_ out-filename] (re-find #"Destination:\s(.+\.mp4)" out)]
        out-filename)
      (do
        (log/error "Youtube-dl error: " youtube-err)
        (desktop-notification "Youtube-dl error" youtube-err)))))

(defn download-url [url dst-dir]
  (let [resp (http/get url {:as :stream})
        filename (-> url
                     io/as-url
                     .getPath
                     io/as-file
                     .getName)
        out-file (io/file dst-dir filename)]
    (desktop-notification
     (format "Downloading %s" (human/filesize (:length resp)))
     (format "%s\n➙\n%s" url out-file))
    (io/copy
     (:body resp)
     out-file)
    (desktop-notification "Download finished" out-file)
    out-file))

(defn query-dict-and-show [phrase]
  (ui/text-dialog (:out (sh "dict" "phrase"))))
