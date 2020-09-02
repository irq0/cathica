(ns cathica.clipboard)

(defonce clipboard (.getSystemClipboard (java.awt.Toolkit/getDefaultToolkit)))
(defonce selection (.getSystemSelection (java.awt.Toolkit/getDefaultToolkit)))

(defn get-current-clipboard [])

(defn prefered-representation-class [mime-type]
  (cond
    (re-find #"text/x-moz-url" mime-type) java.nio.ByteBuffer
    (re-find #"text/.+" mime-type) java.lang.String
    (re-find #"(image|audio|video)/.+" mime-type) java.io.InputStream))

(defn get-clipboard [cb]
  (let [want-mime-types #"(text/(plain|x-moz-url|html))|(image/(png|jpeg|gif|bmp|webp))|(audio/(midi|mpeg|webm|ogg|wav))|(video/(webm|ogg))"]
    (into {} (keep (fn [flavor]
                     (let [mime-type (.getHumanPresentableName flavor)
                           repr-class (.getRepresentationClass flavor)]
                       (when (and (re-find want-mime-types mime-type)
                                  (= (prefered-representation-class mime-type) repr-class))
                         [(str mime-type) (.getData cb flavor)])))
                   (.getAvailableDataFlavors cb)))))
