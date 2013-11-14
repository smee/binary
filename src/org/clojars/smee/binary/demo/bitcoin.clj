(ns org.clojars.smee.binary.demo.bitcoin
  "Implementation of the raw binary format of the bitcoin block chain.
Specification from https://en.bitcoin.it/wiki/Protocol_specification and
http://james.lab6.com/2012/01/12/bitcoin-285-bytes-that-changed-the-world"
  (:refer-clojure :exclude [hash])
  (:use org.clojars.smee.binary.core
        [clojure.java.io :only [input-stream]])
  (:import org.clojars.smee.binary.core.BinaryIO
           java.io.DataOutput
           java.io.DataInput))

(def block-magic (constant (repeated :ubyte :length 4) [0xf9 0xbe 0xb4 0xd9]))

(def var-int-le
  (let [s-le (compile-codec :short-le)
        i-le (compile-codec :int-le)
        l-le (compile-codec :long-le)] 
    (reify BinaryIO
      (read-data  [_ big-in little-in]
        (let [b (.readByte ^DataInput little-in)]
          (condp = b
            -3 #_0xfd (read-data s-le big-in little-in) 
            -2 #_0xfe (read-data i-le big-in little-in)
            -1 #_0xff (read-data l-le big-in little-in)
            (byte->ubyte b))))
      (write-data [_ big-out little-out value]
        (cond
          (< value 0xfd) (.writeByte ^DataOutput little-out value)
          (< value 0xffff) (do (.writeByte ^DataOutput little-out 0xfd) (write-data s-le big-out little-out value))
          (< value 0xffffffff) (do (.writeByte ^DataOutput little-out 0xfe) (write-data i-le big-out little-out value))
          :else (do (.writeByte ^DataOutput little-out 0xff) (write-data l-le big-out little-out value)))))))

(def hash (repeated :ubyte :length 32))

(defn var-len [codec]
  (repeated codec :prefix var-int-le))

(def transaction-input
  (ordered-map
    :hash hash
    :index :int-le
    :script (var-len :ubyte) 
    :sequence-number :int-le))

(def transaction-output
  (ordered-map
    :amount :long-le
    :script (var-len :ubyte)))

(def transaction
  (ordered-map
    :transaction-version :int-le
    :inputs (var-len transaction-input)
    :outputs (var-len transaction-output)
    :lock-time :int-le))

(def block-codec
  (ordered-map
    :separator block-magic
    :length :int-le
    :header (ordered-map 
              :block-version :int-le
              :previous-hash hash
              :merkle-root hash
              :timestamp (compile-codec :int-le #(int (/ (.getTime ^java.util.Date %) 1000)) #(java.util.Date. (long (* % 1000))))
              :target :int-le
              :nonce :int-le)
    :transactions (var-len transaction)
    ))