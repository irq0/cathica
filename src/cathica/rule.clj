(ns cathica.rule
  (:require
   [taoensso.timbre :as log]))

(defn type-is-text [type]
  (#{"STRING" "UTF8_STRING" "TEXT" "text/plain"} type))

(defn type-is-image [type]
  (re-find #"image/.*" type))

(defn type-is-url [type]
  (= "text/x-moz-url" type))


;; special rule things:
;; $1 is first regex group in data
;; $arg is the result of the arg function
;; $DATA is the raw data from clipboard
;; $TYPE is the clipboard type (e.g STRING or text/plain) matched against type
;; - always called with (arg-function wdir $0..$5)
;; type shortcuts:
;; :text matches test types
;; :image matches image types
;; :url ...


(defmacro rule
  "Define plumbing rule"
  [name {:keys [src type data start arg] :as rule}]
  (let [type-fn (case type
                  :text type-is-text
                  :url type-is-url
                  :image type-is-image
                  :default type-is-text)

        data-re-match (if (nil? data)
                        '(constantly true)
                        `#(re-find ~data %))
        src-match (if (nil? src)
                    '(constantly true)
                    `#(re-find ~src (:src %)))

        arg-match (if (nil? arg)
                    '(constantly "")
                    `#(~(first arg) (:wdir %) ~(second arg)))]
    `(fn [message#]
       (try
         (let [[match-type# match-data#]
               (some (fn [[type# data#]]
                       (when (~type-fn type#)
                         (if (string? data#)
                           [type# (~data-re-match data#)]
                           [type# data#])))
                     (:data message#))

               matches# (if (coll? match-data#) match-data# [match-data#])
               n-matches# (count matches#)
               ~'$DATA match-data#
               ~'$TYPE match-type#
               ~'$0 (if (> n-matches# 0) (nth matches# 0) "")
               ~'$1 (if (> n-matches# 1) (nth matches# 1) "")
               ~'$2 (if (> n-matches# 2) (nth matches# 2) "")
               ~'$3 (if (> n-matches# 3) (nth matches# 3) "")
               ~'$4 (if (> n-matches# 4) (nth matches# 4) "")
               ~'$5 (if (> n-matches# 5) (nth matches# 5) "")
               arg# (~arg-match message#)
               ~'$arg (str arg#)]
           (if (and
                (some? match-data#)
                (~src-match message#)
                arg#)
             (do
               (log/debug "Rule matches: " (quote ~rule))
               {:rule (assoc (quote ~rule) :name ~name)
                :match-context {:matches matches# :n-matches n-matches# :arg arg#}
                :execute (fn [] ~start)})
             nil))
         (catch Exception e#
           (log/error e# "Unexpected error during rule execution: " {:rule (quote ~rule) :message message#})
           nil)))))
