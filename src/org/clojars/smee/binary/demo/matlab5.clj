(ns org.clojars.smee.binary.demo.matlab5
  "Implementation for MATLAB 5 binary files. Currently read only, because creating headers requires
knowing the number of bytes written. Since we do not know these size beforehand, we currently can't write
a consistent header.
Implementation according to http://www.mathworks.com/help/pdf_doc/matlab/matfile_format.pdf

CAUTION: This implementation is incomplete! Still missing:
- write structures (need to know binary length of nested structures for the header)
- structures
- cells
- complex values
- do not assume little-endian data, respect endianess indicator header field"
  (:use org.clojars.smee.binary.core
        [clojure.java.io :only [input-stream]])
  (:import org.clojars.smee.binary.core.BinaryIO
           java.io.DataOutput
           java.io.DataInput))

(defn- aligned 
  "All tags and data need to be 64bit-aligned."
  [codec]
  (align codec :modulo 8))

(defn- map-invert [m]
  {:post [(= (count (keys %)) (count (keys m)))]}
  (into {} (for [[k v] m] [v k])))

(defn- break [hdr]
  (throw (ex-info "not implemented, can't create headers for this element!" {:header hdr})))

;;;;;;;;;;;;; constants ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def ^:private element-types 
  {:long-format 0
   :byte 1
   :ubyte 2
   :short-le 3
   :ushort-le 4
   :int-le 5
   :uint-le 6
   :float-le 7
   :double-le 9
   :long-le 12
   :ulong-le 13
   :miMATRIX 14
   :miCOMPRESSED 15
   :miUTF8 16
   :miUTF16 17
   :miUTF32 18})
(def ^:private element-types-rev (map-invert element-types))

(def element-type-sizes "length in bytes per individual value of a data type" 
  {:byte 1
   :ubyte 1
   :short-le 2
   :ushort-le 2
   :int-le 4
   :uint-le 4
   :float-le 4
   :double-le 8
   :long-le 8
   :ulong-le 8})

;;;;;;;;;; header for each subelement ;;;;;;;;;;;;;;;;;;;;;;;;

(def data-element-header-part1 (union 4 {:short (ordered-map :length :short-le
                                                             :type (enum :short-le element-types))
                                         :type :int-le}))
(def element-type (enum :int-le element-types))

(def data-element-header (reify BinaryIO
                           (read-data [_ b l]
                             (let [{hdr :short t :type :as m} (read-data data-element-header-part1 b l)]
                               (if (= :long-format (:type hdr))
                                 {:type (element-types-rev t)
                                  :length (read-data :int-le b l)}
                                 ; should be hdr, but in my tests the type always says 'ubyte' although the value is an integer
                                 {:length 4
                                  :type :uint-le})))
                           (write-data [this b l value]
                             (throw (ex-info "not implemented" {:codec this :value value})))))

(def array-type (enum :byte {:cell 1
                             :structure 2
                             :object 3
                             :chars 4
                             :sparse 5
                             :doubles 6
                             :floats 7
                             :bytes 8
                             :ubytes 9
                             :shorts 10
                             :ushorts 11
                             :ints 12
                             :uints 13
                             :longs 14
                             :ulongs 15}))

(declare subelement)
;;;;;;;;;;;;;; matrices ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def flag-codec (header data-element-header
                        #(align (ordered-map
                                  :array-type array-type
                                  :flags (bits [nil nil nil nil nil :complex :global :logical]))
                                :modulo (:length %))
                        #(hash-map :type :uint-le :length 8)))

(def dimensions-codec (header data-element-header
                              (fn [{t :type l :length}] 
                                (aligned (repeated t :length (/ l (get element-type-sizes t)))))
                              break))


(defn data-matrix [length] 
  (padding (ordered-map             
             :flags flag-codec
             :dimensions dimensions-codec 
             :name (header data-element-header 
                           #(aligned (string "UTF8" :length (:length %)))
                           #(hash-map :type 1 :length (count %)))
             :real subelement) 
           :length length))

;;;;;;;;;;;; codecs for different data types / elements ;;;;;;;;;;;;;;;;;;;

(defmulti data-element :type)
(defmethod data-element :miMATRIX [{l :length}] 
  (data-matrix l))

(defmethod data-element :miCOMPRESSED [{l :length}] 
  (blob :length l))

(defmethod data-element :miUTF8 [{l :length}] 
  (string "UTF8" :length l))

(defmethod data-element :miUTF16 [{l :length}] 
  (string "UTF16" :length l))

(defmethod data-element :miUTF32 [{l :length}] 
  (string "UTF32" :length l))

(defmethod data-element :default [{t :type l :length :as hdr}]
;  (println "default handler, unknown subelement with header=" hdr)
  (aligned (repeated t :length (/ l (element-type-sizes t)))))

;;;;;;;;;;; overall structure ;;;;;;;;;;;;;;;;;;;;;;;;

(def matlab-header (ordered-map :text (compile-codec (string "UTF8" :length 124) 
                                                     #(apply str % (repeat (max 0 (- 124 (count %))) \space))
                                                     identity) 
                                :version :short-le
                                ;todo use endianess to switch codecs for data later on
                                :endianess (string "UTF8" :length 2)))

(def subelement "Each individual entry of a MATLAB file has this structure" 
  (header data-element-header ;#(blob :length (:length %)) 
          data-element 
          break))

(def matlab-5-codec (ordered-map 
                      :header matlab-header 
                      :elements (repeated subelement)))

(comment
  (require 'clojure.pprint)
  (set! *print-length* 5)
  (->> "e:\\datasets\\Volumes\\Seagate\\seizure_detection\\competition_data\\clips\\Patient_2\\Patient_2_ictal_segment_0018.mat"
    input-stream
    (decode matlab-5-codec)
    clojure.pprint/pprint)  
  )