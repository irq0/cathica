(ns cathica.utils
  (:require
   [cathica.logging :refer [with-logging-status]]
   [clojure.java.io :as io]
   [clojure.java.shell :refer [sh]]
   [clojure.string :as string]
   [taoensso.timbre :as log]
   [clojure.edn :as edn]
   [slingshot.slingshot :refer [throw+ try+]]
   ))

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


(defn current-window-info []
  (-> (sh "current-window") :out edn/read-string))
