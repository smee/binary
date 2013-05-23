(ns org.clojars.smee.binary.demo.protobuf
  (:use org.clojars.smee.binary.core
        [clojure.java.io :only [input-stream]])
  (:import org.clojars.smee.binary.core.BinaryIO))

(defn- reconstruct-varint [bytes]
  (reduce bit-or 
          (long 0) 
          (map-indexed #(bit-shift-left (bit-and 2r1111111 %2) (* 7 %)) bytes)))

(defn- construct-varint [number]
  (loop [left number, bytes (transient [])]
    (if (<= left 127)
      (persistent! (conj! bytes left))
      (recur (bit-shift-right left 7) (conj! bytes (bit-or 128 (bit-and 127 left)))))))

(def var-int 
  "Refer to https://developers.google.com/protocol-buffers/docs/encoding#varints"
  (reify BinaryIO 
    (read-data  [_ big-in little-in]
      (loop [bytes (transient [])]
        (let [b (.readByte little-in)]
          (if (bit-test b 8)
            (recur (conj! bytes b))
            (reconstruct-varint (persistent! (conj! bytes b)))))))
    
    (write-data [_ big-out little-out value]
      (doseq [b (construct-varint value)]
        (.writeByte little-out b)))))

(def proto-key (let [types {:varint 0
                            :64bit 1
                            :delimited 2
                            :start-group 3
                            :end-group 4
                            :32bit 5}
                     rev-types (into {} (for [[k v] types] [v k]))] 
                 (compile-codec var-int 
                                (fn [[number type]] (bit-or (bit-shift-left number 3) (get types type)))
                                #(vector (bit-shift-right % 3) (get rev-types (bit-and 2r111 %))))))

(def proto-string )
(comment
  (defn byte2bits [byte]
    (Integer/toString byte 2))
  
  (reconstruct-varint [2r10101100 2r00000010])
  (map byte2bits (construct-varint 300))
  (reconstruct-varint (construct-varint 300))
  
  (decode protobuf (input-stream "dev-resources/google_message1.dat"))
  
  (defn t [codec value] 
     (let [baos (java.io.ByteArrayOutputStream.)
           _ (encode codec baos value)
           arr (.toByteArray baos)
           encoded-bytes (map byte->ubyte (seq arr))
           decoded (decode codec (java.io.ByteArrayInputStream. arr))]    
       (println value (mapv byte2bits encoded-bytes) decoded)))
  (t proto-key [1 :varint])
  )