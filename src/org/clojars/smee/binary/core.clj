(ns org.clojars.smee.binary.core
  (:use [clojure.java.io :only (input-stream output-stream)])
  (:import [java.io DataInput DataOutput DataInputStream DataOutputStream InputStream ByteArrayInputStream ByteArrayOutputStream])
  (:require [clojure.walk :as walk]))

(defprotocol ^:private BinaryIO
  (read-data  [codec big-in little-in])
  (write-data [codec big-out little-out value]))

(defn codec? [codec]
  (satisfies? BinaryIO codec))

(defmacro ^:private primitive-codec 
  "Create an reification of `BinaryIO` that can read/write a primmitive data type."
  [get-fn write-fn cast-fn & [endianess]]
  (let [big-in     (gensym "big-in")
        little-in  (gensym "little-in")
        big-out    (gensym "big-out")
        little-out (gensym "little-out")
        in (if (= endianess :le) little-in big-in)
        out (if (= endianess :le) little-out big-out)]
    `(reify BinaryIO
       (read-data [codec# ~big-in ~little-in]
          (~cast-fn (~get-fn ~(with-meta in {:tag "UnsignedDataInput"}))))
       (write-data [codec# ~big-out ~little-out value#]
          (~write-fn ~(with-meta out {:tag "UnsignedDataOutput"}) value#))
       Object (toString [_] (str "<BinaryIO " '~get-fn ">")))))

(defn byte->ubyte [b]
  (int (bit-and b 255)))

(defn ubyte->byte [b]
  (if (>= b 128)
    (byte (- b 256))
    (byte b)))

(def primitive-codecs
  {:byte (primitive-codec .readByte .writeByte byte)
   :ubyte (primitive-codec .readUnsignedByte .writeByte byte->ubyte)
   
   ;:char (primitive-codec .readChar .writeChar char :be)
   ;:char-le (primitive-codec .readChar .writeChar char :le)
   ;:char-be (primitive-codec .readChar .writeChar char :be)
   
   :short    (primitive-codec .readShort .writeShort short :be)
   :short-le (primitive-codec .readShort .writeShort short :le)
   :short-be (primitive-codec .readShort .writeShort short :be)

   :int    (primitive-codec .readInt .writeInt int :be)
   :int-le (primitive-codec .readInt .writeInt int :le)
   :int-be (primitive-codec .readInt .writeInt int :be)
   :uint-le (primitive-codec .readUnsignedInt .writeUnsignedInt long :le)
   :uint-be (primitive-codec .readUnsignedInt .writeUnsignedInt long :be)

   :long    (primitive-codec .readLong .writeLong long :be)
   :long-le (primitive-codec .readLong .writeLong long :le)
   :long-be (primitive-codec .readLong .writeLong long :be)

   :float    (primitive-codec .readFloat .writeFloat float :be)
   :float-le (primitive-codec .readFloat .writeFloat float :le)
   :float-be (primitive-codec .readFloat .writeFloat float :be)
   
   :double    (primitive-codec .readDouble .writeDouble double :be)
   :double-le (primitive-codec .readDouble .writeDouble double :le)
   :double-be (primitive-codec .readDouble .writeDouble double :be)
   })

(declare compile-codec)

(defn ordered-map 
  "Parse a binary stream into a map."
  [& kvs]
  {:pre [(even? (count kvs))]} 
  (let [ks (take-nth 2 kvs)
        vs (take-nth 2 (rest kvs))
        key-order (into {} (map-indexed #(vector %2 %) ks))
        internal-map (apply sorted-map-by (comparator #(< (key-order % java.lang.Long/MAX_VALUE) (key-order %2 java.lang.Long/MAX_VALUE))) kvs)]
    (reify 
      BinaryIO
      (read-data  [_ big-in little-in]
        (zipmap ks (map #(read-data % big-in little-in) vs)))
      (write-data [_ big-out little-out value] 
        {:pre [(every? (set ks) (keys value))]}
        (dorun (map #(write-data % big-out little-out %2) 
                    vs
                    (map #(get value %) ks))))
      
      java.lang.Object
       (toString [this] 
         (str internal-map))
      
      clojure.lang.ILookup
      (valAt [_ key]
        (get internal-map key))
      (valAt [_ key not-found]
        (get internal-map key not-found))
      
      clojure.lang.Counted
      (count [_]
         (count internal-map))
      
;       clojure.lang.Associative
;       (containsKey [_ k] 
;         (contains? internal-map k))
;       (entryAt [_ k]
;         (get internal-map k))
;       (assoc [this k v]  
;         (apply ordered-map (apply concat (seq (assoc internal-map k v)))))
       
       clojure.lang.IPersistentMap
       (assoc [this k v]
         (apply ordered-map (apply concat (seq (assoc internal-map k v)))))
       (assocEx [this k v]
         (if (internal-map k) 
           (throw (ex-info "Key already present" {:key k}))
           (apply ordered-map (apply concat (seq (assoc internal-map k v))))))
       (without [this k]
         (apply ordered-map (apply concat (seq (dissoc internal-map k)))))
       
       clojure.lang.IPersistentCollection
       (cons [this [k v]]  
         (assoc this k v))
       (empty [_]
         (ordered-map))
       (equiv [_ other]
         false)

       clojure.lang.Seqable
       (seq [_] 
         (seq internal-map))

       ;; Java interfaces
       java.lang.Iterable
       (iterator [this] 
         (.iterator (seq this))))))

(defn- read-times 
  "Performance optimization for `(repeatedly n #(read-data codec big-in little-in))`"
  [n codec big-in little-in]
  (loop [n (int n), res (transient [])] 
    (if (zero? n) 
      (persistent! res) 
      (recur (dec n) (conj! res (read-data codec big-in little-in))))))

(defn- read-exhausting 
  "Performance optimization for `(take-while (complement nil? )(repeatedly n #(read-data codec big-in little-in)))`"
  [codec big-in little-in]
  (loop [res (transient [])] 
    (if-let [value (try (read-data codec big-in little-in) (catch java.io.EOFException e nil))]
      (recur (conj! res value))
      (persistent! res))))

(defn- read-until-separator 
  "Read until the read value equals `separator`."
  [codec big-in little-in separator] 
  (loop [res (transient []), empty? true] 
    (let [value (try 
                  (read-data codec big-in little-in) 
                  (catch java.io.EOFException e 
                    (if empty? ;there is no value read yet, but the stream is empty 
                      (throw e)
                      ;else: there seems to be no more bytes, so just return what we have
                      separator)))]
      (if 
        (= value separator) 
        (persistent! res)
        (recur (conj! res value) false)))))

(defn repeated 
  "Read a sequence of values. Options are pairs of keys and values with possible keys:
- `:length` fixed length of the sequence
- `:prefix` codec for the length of the sequence to read prior to the sequence itself.
- `:separator` reads until the read value equals the given separator value. EOF of a stream is regarded a separator too.
That means if the last token is the last element in a stream, the final separator may be missing. Caution: When
writing the data there WILL be a final separator. This means, the written data may have more bytes than initially read!

If there is no options, the decoder tries to read continuously until the stream is exhausted.
Example: To read a sequence of integers with a byte prefix for the length use `(repeated :byte :prefix :int)`"
  [codec & options]
  (let [codec (compile-codec codec)
        options (apply hash-map options)
        length (options :length)
        prefix (options :prefix)
        separator (options :separator)]
    (cond length (reify BinaryIO 
                   (read-data  [_ big-in little-in]
                     (read-times length codec big-in little-in))
                   (write-data [_ big-out little-out values]
                     (if (not= length (count values)) 
                       (throw (java.lang.IllegalArgumentException. (str "This sequence should have length " length " but has really length " (count values))))
                       (doseq [value values] 
                         (write-data codec big-out little-out value)))))
          ; use prefix-codec?
          prefix (let [prefix-codec (compile-codec prefix)] 
                   (reify BinaryIO 
                     (read-data  [_ big-in little-in]
                       (let [length (read-data prefix-codec big-in little-in)]
                         (read-times length codec big-in little-in)))
                     (write-data [_ big-out little-out values]
                       (let [length (count values)] 
                         (write-data prefix-codec big-out little-out length)
                         (dorun (map #(write-data codec big-out little-out %) values))))))
          separator (reify BinaryIO
                      (read-data  [_ big-in little-in]
                        (read-until-separator codec big-in little-in separator))
                      (write-data [_ big-out little-out values]
                        (doseq [value values]
                          (write-data codec big-out little-out value))
                        (write-data codec big-out little-out separator)))
          :else (reify BinaryIO
                  (read-data  [_ big-in little-in]
                    (read-exhausting codec big-in little-in))
                  (write-data [_ big-out little-out values]
                    (doseq [value values]
                      (write-data codec big-out little-out value)))))))

(defn constant 
  "Reads a constant value, ignores given value on write. Can be used as a version tag for a composite codec.
Example:
    (encode out (constant :int-le 7) 1234)
    => ;will instead write bytes [7 0 0 0]"
  [codec constant-value]
  (compile-codec codec 
                 (constantly constant-value) 
                 #(do 
                    (assert (= % constant-value) (format "value '%s' should have had the constant value '%s'" (str %) (str constant-value))) 
                    constant-value)))

(defn string [^String encoding & options]
  {:pre [(some #{:length :prefix :separator} (take-nth 2 options))]}
  (compile-codec 
    (apply repeated :byte options)
    (fn string2bytes [^String s] (.getBytes s encoding))
    #(String. (byte-array %) encoding)))



(defn c-string 
  "Zero-terminated string (like in C). String is a sequence of bytes, terminated by a 0 byte."
  [^String encoding]
  (compile-codec 
    (repeated :byte :separator (byte 0))
    (fn string2bytes [^String s] (.getBytes s encoding))
    #(String. (byte-array %) encoding)))


(defn- bit-set? [bytes idx]
  (not (zero? (bit-and (bytes (- (count bytes) 1 (quot idx 8)))
                       (bit-shift-left 1 (mod idx 8))))))
(defn- set-bit [bytes idx]
  (update-in bytes [(- (count bytes) 1 (quot idx 8))]
                   #(bit-or % (bit-shift-left 1 (mod idx 8)))))

(defn bits 
  "`flags` is a sequence of flag names. Each flag's index corresponds to the bit with that index.
Flag names `null` are ignored. Bit count will be padded up to the next multiple of 8."
  [flags]
  (let [byte-count (int (Math/ceil (/ (count flags) 8)))
        idx->flags (into {} (keep-indexed #(when %2 [%1 %2]) flags))
        flags->idx (into {} (keep-indexed #(when %2 [%2 %1]) flags))
        bit-indices (sort (keys idx->flags))]
    (compile-codec (repeated :byte :length byte-count)
                   (fn [flags] (reduce #(set-bit % %2) (into [] (byte-array byte-count)) (vals (select-keys flags->idx flags))))
                   (fn [bytes] (set (map idx->flags (filter #(bit-set? bytes %) bit-indices)))))))

(defn header 
  "Decodes a header using `header-codec`. Passes this datastructure to `header->body` which returns the codec to
use to parse the body. For writing this codec calls `body->header` with the data as parameter and
expects a value to use for writing the header information."
  [header-codec header->body-codec body->header]
  (let [header-codec (compile-codec header-codec)]
    (reify BinaryIO 
      (read-data  [_ big-in little-in]
        (let [header (read-data header-codec big-in little-in)
              body-codec (header->body-codec header)
              body (read-data body-codec big-in little-in)]
          body))
      (write-data [_ big-out little-out value]
        (let [header (body->header value)
              body-codec (header->body-codec header)] 
          (write-data header-codec big-out little-out header)
          (write-data body-codec big-out little-out value))))))

(defn padding 
  "Make sure there is always a minimum byte `length` when writing a value.
Per default the padding are 0-bytes. Optionally a third parameter may specify the
byte value to use for padding"
  [inner-codec length & [byte-value]]
  {:pre [(number? length) (or (nil? byte-value) (number? byte-value))]}
  (let [inner-codec (compile-codec inner-codec)
        padding-value (or byte-value (byte 0))]
    (reify BinaryIO 
      (read-data  [_ big-in _]
        (let [bytes (byte-array length)
              _ (.readFully ^DataInput big-in bytes)
              in (java.io.ByteArrayInputStream. bytes)
              big-in (DataInputStream. in)
              little-in (LittleEndianDataInputStream. in)]
          (read-data inner-codec big-in little-in)))
      (write-data [_ big-out _ value]
        (let [baos (ByteArrayOutputStream. length)
              big-o (DataOutputStream. baos)
              little-o (LittleEndianDataOutputStream. baos)
              _ (write-data inner-codec big-o little-o value)
              arr (.toByteArray baos)
              len (alength arr)
              padding-bytes-left (max 0 (- length len))]
          (if (> 0 (- length len)) 
            (throw (IllegalArgumentException. (str "Data should be max. " length " bytes, but attempting to write " (Math/abs padding-bytes-left) " bytes more!")))
            (do
              (.write ^DataOutputStream big-out arr 0 len)
              (dotimes [_ padding-bytes-left] (.writeByte ^DataOutputStream big-out padding-value)))))))))

;;;;;;; internal compilation of the DSL into instances of `BinaryIO`
;; 
;; let sequences, vectors, maps and primitive's keywords implement BinaryIO
;; that means, compile-codec is optional!
(extend-protocol BinaryIO
  clojure.lang.ISeq
  (read-data [this big-in little-in]
    (map #(read-data % big-in little-in) this))
  (write-data [this big-out little-out values]
    (dorun (map #(write-data % big-out little-out %2) this values)))
  
  clojure.lang.IPersistentVector
  (read-data [this big-in little-in]
    (mapv #(read-data % big-in little-in) this))
  (write-data [this big-out little-out values]
    (dorun (map #(write-data % big-out little-out %2) this values)))
  
  clojure.lang.Keyword
  (read-data [kw big-in little-in]
    (read-data (primitive-codecs kw) big-in little-in))
  (write-data [kw big-out little-out value]
    (write-data (primitive-codecs kw) big-out little-out value))
  
  clojure.lang.IPersistentMap
  (read-data  [m big-in little-in]
    (zipmap (keys m) (map #(read-data % big-in little-in) (vals m))))
  (write-data [m big-out little-out value] 
    (dorun (map (fn [[k v]] (write-data (get m k) big-out little-out v)) value))))

(defn compile-codec 
  ([codec] (if (codec? codec) 
             codec 
             (throw (ex-info (str codec " does not satisfy the protocol BinaryIO!" ) {:codec codec}))))
  ([codec pre-encode post-decode]
    (let [codec (compile-codec codec)]
      (reify BinaryIO
        (read-data  [_ big-in little-in]
          (post-decode (read-data codec big-in little-in)))
        (write-data [_ big-out little-out value]
          (write-data codec big-out little-out (pre-encode value)))))))

;;;;;;;;;;;;;; API for en-/decoding

(defn encode 
  "Serialize a value to the outputstream out according to the codec."
  [codec out value]
  (let [big-out (BigEndianDataOutputStream. out)
        little-out (LittleEndianDataOutputStream. out)]
    (write-data codec big-out little-out value)))

(defn decode 
  "Serialize a value to the outputstream out according to the codec."
  [codec in]
  (let [big-in (BigEndianDataInputStream. in)
        little-in (LittleEndianDataInputStream. in)]
    (read-data codec big-in little-in)))