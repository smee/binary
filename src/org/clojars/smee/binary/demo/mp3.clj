(ns ^{:doc "MP3 IDv2 tags, according to the current specification at http://www.id3.org/id3v2.4.0-structure"
      :author "Steffen Dienst"} 
  org.clojars.smee.binary.demo.mp3
  (:use clojure.test
        org.clojars.smee.binary.core))


(defn int->synchsafe [x]
  {:pre [(< x (bit-shift-left 1 29))]}
  (let [m0 (bit-and x 127)
        m1 (bit-and (bit-shift-right x 7) 127)
        m2 (bit-and (bit-shift-right x 14) 127)
        m3 (bit-and (bit-shift-right x 21) 127)]
    (reduce bit-or (int 0) [m0 
                            (bit-shift-left m1 8)
                            (bit-shift-left m2 16)
                            (bit-shift-left m3 24)])))
(defn synchsafe->int [x]
  (let [m0 (bit-and x 255)
        m1 (bit-and (bit-shift-right x 8) 255)
        m2 (bit-and (bit-shift-right x 16) 255)
        m3 (bit-and (bit-shift-right x 24) 255)]
    (reduce bit-or (int 0) [m0 
                            (bit-shift-left m1 7)
                            (bit-shift-left m2 14)
                            (bit-shift-left m3 21)])))

(defn synchsafe-int []
  (compile-codec :int-be int->synchsafe synchsafe->int))

(def header
  (ordered-map
    :magic-number (string "ISO-8859-1" :length 3) ;; "ID3"
    :version (ordered-map :major :byte
                          :minor :byte)
    :flags (bits [nil nil nil nil :footer? :experimental? :extended-header? :unsynchronized?]) 
    :tag-size (synchsafe-int)))

(def extended-header
  (ordered-map 
    :header-size (synchsafe-int)
    :flags-num :byte
    :extended-flags (bits [nil nil nil nil :tag-restrictions? :crc? :update?])))

(def idv2-frame 
  (ordered-map
    :id (string "ISO-8859-1" :length 4)
    :size (synchsafe-int)
    ;;  section 4.1.
    :flags (ordered-map :status (bits [nil nil nil nil :read-only? :frame-discarded-file-alteration? :frame-discarded-tag-alteration?])
                        :format (bits [:data-length-added? :unsynchronized? :encrypted? :compressed? nil nil :group-information?])) 
    ))

(def mp3-id3v2-codec
  (compile-codec [header]))

(comment 
  (use '[clojure.java.io :only [input-stream]])
  (let [in (input-stream "d:\\test.mp3")]
    (println (decode mp3-id3v2-codec in))
    (println (decode idv2-frame in)))
  
  )
