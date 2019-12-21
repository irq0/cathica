(ns cathica.ui
  (:require
   [seesaw.core :refer :all]
   [seesaw.mig :refer [mig-panel]]
   [seesaw.font :refer [font]]
   [seesaw.keymap :refer [map-key]]
   [taoensso.timbre :as log]
   [byte-streams :as bs]
   [clojure.pprint :refer [pprint]]))


(defn mouse-location []
  (-> (java.awt.MouseInfo/getPointerInfo)
                .getLocation))

(defn -init []
  ;; on linux this enables gtk -> hidpi
  (System/setProperty "awt.useSystemAAFontSettings" "on")
  (System/setProperty "swing.aatext" "true")
  (javax.swing.UIManager/setLookAndFeel
   (javax.swing.UIManager/getSystemLookAndFeelClassName))
  (native!))

(-init)

(reify javax.swing.ListCellRenderer
  (^java.awt.Component getListCellRendererComponent
   [^javax.swing.ListCellRenderer this
    ^javax.swing.JList lst
    ^Object value
    ^int index
    ^boolean isSelected
    ^boolean cellHasFocus]
   (.setText this (str value))))


               ;; (^void clipboard [^DBusInterface this]
               ;;  (core/plumb-current-clipboard))
               ;; (^void string [^DBusInterface this ^String s]
               ;;  (core/plumb-string s))))


(defn- make-cell-renderer []
  (reify javax.swing.ListCellRenderer
    (^java.awt.Component getListCellRendererComponent
      [^javax.swing.ListCellRenderer this
       ^javax.swing.JList lst
       ^Object value
       ^int index
       ^boolean selected?
       ^boolean focus?]
      (let [background (if selected?
                         :orange
                         :white)
            text (if (map? value)
                   (get-in value [:rule :name])
                   value)]
        (label
         :background background
         :text text)))))

(defn- make-picker-frame [choices message]
  (doto
   (frame
    :id :picker
    :title "cathica-picker"
    :content (border-panel
              :hgap 10
              :west (scrollable
                     (doto (listbox
                            :id :choices
                            :preferred-size [400 :by 200]
                            :model choices)
                       (.setCellRenderer (make-cell-renderer))))
              :center (vertical-panel
                       :preferred-size [600 :by 800]
                       :items [(scrollable (text :text (with-out-str (pprint message))
                                     :id :message
                                     :multi-line? true))
                               (grid-panel

                                :items [
                                (label :id :image
                                      :icon
                                      (when-let [image-data (get-in message [:data "image/png"])]
                                        (javax.swing.ImageIcon.
                                         (bs/to-byte-array image-data))
                                        (.reset image-data)
                                        ))])
                               (text :text ""
                                     :id :rule-preview
                                     :multi-line? true)])))
    (.setAlwaysOnTop true)
    (.setLocation (mouse-location))))


(defn- add-behavior [frame promise]
  (let [{:keys [choices picker rule-preview]} (group-by-id frame)
        deliver-choice (fn [&_] (deliver promise (selection choices))
                         (dispose! picker))
        show-rule (fn []
                    (when-let [rule (:rule (selection choices))]
                      (value! rule-preview
                              (with-out-str (pprint rule)))))]

    (listen choices :mouse-clicked deliver-choice)
    (map-key choices "ENTER" deliver-choice)
    (map-key picker "ctrl N" (fn [_] (.setSelectedIndex
                                      choices (inc (.getSelectedIndex choices)))
                               (show-rule)))

    (map-key picker "ctrl P" (fn [_] (.setSelectedIndex
                                      choices (dec (.getSelectedIndex choices)))
                               (show-rule)))
    (doseq [k ["Q" "ESCAPE"]]
      (map-key picker k (fn [_] (deliver promise nil)
                            (dispose! picker))))
    frame))


(defn picker [choices message]
  (let [p (promise)]
    (try
      (invoke-now
       (-> (make-picker-frame choices message)
           (add-behavior p)
           pack!
           show!))
      (catch Exception e
        (log/warn e "Picker failed" choices message)
        (deliver p nil)))
      @p))
