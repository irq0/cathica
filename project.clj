(defproject cathica "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url ""
  :source-paths ["src"]
  :java-source-paths ["external/dbus-java/src/main/java" "external/dbus-java/src/main" "java"]
  :jvm-opts ["-Xmx1g"
             "-XX:-OmitStackTraceInFastThrow"
             ]
  :min-lein-version  "2.0.0"

  :main cathica.core

  :dependencies [
                 [org.clojure/clojure "1.10.1"]
                 [com.taoensso/timbre "4.10.0"]
                 [org.slf4j/log4j-over-slf4j "1.7.30"]
                 [org.slf4j/jul-to-slf4j "1.7.30"]
                 [org.slf4j/jcl-over-slf4j "1.7.30"]
                 [com.fzakaria/slf4j-timbre "0.3.17"]
                 [mount "0.1.16"]
                 [clj-time "0.15.2"]
                 [nrepl "0.6.0"]
                 [cider/cider-nrepl "0.22.4"]
                 [org.clojure/core.async "0.6.532"]
                 [slingshot "0.12.2"]
                 [com.novemberain/pantomime "2.11.0"]
                 ;; for dbus-java
                 [com.github.hypfvieh/libmatthew "0.8.3"]
                 [clj-http "3.10.0"]
                 [byte-streams "0.2.4"]
                 [robert/hooke "1.3.0"]
                 [cheshire "5.9.0"]
                 [seesaw "1.5.0"]
                 [org.jfxtras/jfxtras-all "10.0-r1"]])
