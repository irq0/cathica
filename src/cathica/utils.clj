(ns cathica.utils
  (:require
   [cathica.logging :refer [with-logging-status]]
   [clojure.java.io :as io]
   [clojure.java.shell :refer [sh]]
   [clojure.string :as string]
   [taoensso.timbre :as log]
   [clj-time.core :as time]
   [clojure.edn :as edn]
   [slingshot.slingshot :refer [throw+ try+]]
   ))

(defn expand-file [wdir s]
  (when-not (or (nil? wdir) (nil? s))
    (if (.isAbsolute (io/as-file s))
      (.getAbsoluteFile (io/as-file s))
      (.getAbsoluteFile (java.io.File. wdir s)))))

(defn is-file [wdir s]
  (when-let [file (expand-file wdir s)]
    (when (.isFile file) file)))

(defn is-dir [wdir s]
  (when-let [file (expand-file wdir s)]
    (when (.isDirectory file) file)))

(defn git-git-dir [some-dir]
  (let [{:keys [out exit]}
        (sh "git" "rev-parse" "--git-dir"
            :dir some-dir)]
    (when (= 0 exit)
      (string/trim out))))

(defn git-repo-dir [some-dir]
  (let [{:keys [out exit]}
        (sh "git" "rev-parse" "--show-toplevel"
            :dir some-dir)]
    (when (= 0 exit)
      (string/trim out))))

(defn git-resolve-file [wdir s]
  (when-not (empty? wdir)
    (let [git-dir (git-git-dir wdir)
          repo-dir (git-repo-dir wdir)
          {:keys [out exit]}
          (sh "git" "--git-dir" git-dir "ls-files"
              :dir wdir)]
      (when (= 0 exit)
        (->> (string/split out #"\n")
             (some #(when (re-find (re-pattern s) %1) %1))
             (is-file repo-dir))))))

(defn current-window-info []
  (-> (sh "current-window") :out edn/read-string))


(defn format-period [period]
  (let [formatter (some-> (org.joda.time.format.PeriodFormatterBuilder.)
                    .printZeroNever
                    .appendDays
                    (.appendSuffix "d")
                    .appendHours
                    (.appendSuffix "h")
                    .appendMinutes
                    (.appendSuffix "m")
                    .printZeroAlways
                    .appendSeconds
                    (.appendSuffix "s")
                    .toFormatter)]
    (.print formatter period)))

(defn format-interval [interval]
  (format-period (.toPeriod interval)))

(defn period-since-now [ts]
  (.toPeriod (time/interval ts (time/now))))

(defn since-now-str [ts]
  (some-> ts
    period-since-now
    format-period
    (str " ago")))

(defn human-duration-short [period]
  (let [formatter (some->
                   (org.joda.time.format.PeriodFormatterBuilder.)
                 .printZeroNever
                     .appendDays
                     (.appendSuffix "d")
                     .appendHours
                     (.appendSuffix "h")
                     .appendMinutes
                     (.appendSuffix "m")
                     .printZeroAlways
                     .appendSeconds
                     (.appendSuffix "s")
                     .printZeroNever
                     .appendMillis
                     (.appendSuffix "ms")
                     .toFormatter)]
     (.print formatter period)))
