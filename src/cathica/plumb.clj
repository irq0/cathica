(ns cathica.plumb
  (:require
   [cathica.actions :as actions]
   [cathica.ui :as ui]
   [cathica.rules :as rules]
   [cathica.utils :as utils]
   [cathica.clipboard :as clipboard]
   [taoensso.timbre :as log]))

(defn execute-rule [message rule]
  (let [{:keys [rule execute match-context]} rule]
    (try
      (execute)
      (catch Throwable th
        (log/error th "Rule failed"
                   {:message message
                    :rule rule})))))

(defn plumb-first
  "Find first matching rule - execute that"
  [rules message]
  (log/info "Plumbing message: " message)
  (let [matched-rules (->> rules
                           (map #(% message))
                           (filter map?))]
    (log/infof "%s/%s rules match" (count matched-rules) (count rules))
    (log/debug "Matched rules:" (pr-str (map :rule matched-rules)))

    (if (= 0 (count matched-rules))
      (actions/desktop-notification "cathica:plumb ERROR"
                                    (format "No mathing rule for %s" (pr-str message)))
      (do
        (log/info "Executing first match: " (first matched-rules))
        (execute-rule message (first matched-rules))))))

(defn plumb-with-picker
  "Match all rules, show picker gui, execute selection"
  [rules message]
  (log/info "Plumbing message: " message)
  (let [matched-rules (->> rules
                           (map #(% message))
                           (filter map?))
        picked (ui/picker matched-rules message)]
    (log/infof "%s/%s rules matched" (count matched-rules) (count rules))

    (if (nil? picked)
      (actions/desktop-notification "cathica:plumb ERROR"
                                    (format "Nothing selected %s" (pr-str message)))
      (do
        (log/info "Executing: " picked)
        (execute-rule message picked)))))

(defn plumb-message [{:keys [message with-picker?]}]
  (if with-picker?
    (plumb-with-picker rules/plumbing-rules message)
    (plumb-first rules/plumbing-rules message)))

(defn plumb-current-clipboard [& {:keys [with-picker?]
                                  :or {with-picker? true}}]
  (let [win (utils/current-window-info)]
    (plumb-message {:message {:src (:window-class win)
                              :wdir (:wdir win)
                              :data (clipboard/get-clipboard clipboard/selection)}
                    :with-picker? with-picker?})))

(defn plumb-string [s & {:keys [with-picker?]
                         :or {with-picker? true}}]
  (let [win (utils/current-window-info)]
    (plumb-message {:message {:src (:window-class win)
                              :wdir (:wdir win)
                              :data {"text/plain" s}}
                    :with-picker? with-picker?})))
