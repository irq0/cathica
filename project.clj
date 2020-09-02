(defproject cathica "0.1.0-SNAPSHOT"
  :description "Oh, never underestimate plumbing. Plumbing’s very important."
  :source-paths ["src"]
  :java-source-paths ["external/dbus-java/src/main/java" "external/dbus-java/src/main" "java"]
  :jvm-opts ["-Xmx128m"
             "-Dawt.useSystemAAFontSettings=on"
             "-Dswing.aatext=true"
             "-Dsun.java2d.dpiaware=true"
             "-Dswing.defaultlaf=com.sun.java.swing.plaf.gtk.GTKLookAndFeel"
             "-Dswing.crossplatformlaf=com.sun.java.swing.plaf.gtk.GTKLookAndFeel"
             "-XX:-OmitStackTraceInFastThrow"]
  :aot :all
  :main cathica.core

  :plugins [[lein-cljfmt "0.6.8"]]
  :exclusions [org.slf4j/slf4j-nop]
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [com.taoensso/timbre "4.10.0"]
                 [org.slf4j/log4j-over-slf4j "1.7.30"]
                 [org.slf4j/jul-to-slf4j "1.7.30"]
                 [org.slf4j/jcl-over-slf4j "1.7.30"]
                 [com.fzakaria/slf4j-timbre "0.3.19"]
                 [mount "0.1.16"]
                 [clj-time "0.15.2"]
                 [nrepl "0.8.0"]
                 [cider/cider-nrepl "0.25.3"]
                 [org.clojure/core.async "1.3.610"]
                 [slingshot "0.12.2"]
                 [com.novemberain/pantomime "2.11.0"]
                 ;; for dbus-java
                 [com.github.hypfvieh/libmatthew "0.8.3"]
                 [clj-http "3.10.2"]
                 [byte-streams "0.2.4"]
                 [clojure-humanize "0.2.2"]
                 [robert/hooke "1.3.0"]
                 [cheshire "5.10.0"]
                 [seesaw "1.5.0"]
                 [org.jfxtras/jfxtras-all "10.0-r1"]])
