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

;;;;;;;;;; common ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
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

;;;;;;;;;;; transaction scripts ;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private opcodes 
    {:OP_0	0
     :OP_PUSHDATA1	76
     :OP_PUSHDATA2	77
     :OP_PUSHDATA4	78
     :OP_1NEGATE	79
     :OP_1 81
     :OP_2 82
     :OP_3 83
     :OP_4 84
     :OP_5 85
     :OP_6 86
     :OP_7 87
     :OP_8 88
     :OP_9 89
     :OP_10	90
     :OP_11	91
     :OP_12	92
     :OP_13	93
     :OP_14	94
     :OP_15	95
     :OP_16	96
     :OP_NOP	97
     :OP_IF	99
     :OP_NOTIF	100
     :OP_ELSE	103
     :OP_ENDIF	104
     :OP_VERIFY	105
     :OP_RETURN	106
     :OP_TOALTSTACK	107
     :OP_FROMALTSTACK	108
     :OP_IFDUP	115
     :OP_DEPTH	116
     :OP_DROP	117
     :OP_DUP	118
     :OP_NIP	119
     :OP_OVER	120
     :OP_PICK	121
     :OP_ROLL	122
     :OP_ROT	123
     :OP_SWAP	124
     :OP_TUCK	125
     :OP_2DROP	109
     :OP_2DUP	110
     :OP_3DUP	111
     :OP_2OVER	112
     :OP_2ROT	113
     :OP_2SWAP	114
     :OP_CAT	126
     :OP_SUBSTR	127
     :OP_LEFT	128
     :OP_RIGHT	129
     :OP_SIZE	130
     :OP_INVERT	131
     :OP_AND	132
     :OP_OR	133
     :OP_XOR	134
     :OP_EQUAL	135
     :OP_EQUALVERIFY	136
     :OP_1ADD	139
     :OP_1SUB	140
     :OP_2MUL	141
     :OP_2DIV	142
     :OP_NEGATE	143
     :OP_ABS	144
     :OP_NOT	145
     :OP_0NOTEQUAL	146
     :OP_ADD	147
     :OP_SUB	148
     :OP_MUL	149
     :OP_DIV	150
     :OP_MOD	151
     :OP_LSHIFT	152
     :OP_RSHIFT	153
     :OP_BOOLAND	154
     :OP_BOOLOR	155
     :OP_NUMEQUAL	156
     :OP_NUMEQUALVERIFY	157
     :OP_NUMNOTEQUAL	158
     :OP_LESSTHAN	159
     :OP_GREATERTHAN	160
     :OP_LESSTHANOREQUAL	161
     :OP_GREATERTHANOREQUAL	162
     :OP_MIN	163
     :OP_MAX	164
     :OP_WITHIN	165
     :OP_RIPEMD160	166
     :OP_SHA1	167
     :OP_SHA256	168
     :OP_HASH160	169
     :OP_HASH256	170
     :OP_CODESEPARATOR	171
     :OP_CHECKSIG	172
     :OP_CHECKSIGVERIFY	173
     :OP_CHECKMULTISIG	174
     :OP_CHECKMULTISIGVERIFY	175
     :OP_PUBKEYHASH	253
     :OP_PUBKEY	254
     :OP_INVALIDOPCODE	255
     :OP_RESERVED	80
     :OP_VER	98
     :OP_VERIF	101
     :OP_VERNOTIF	102
     :OP_RESERVED1	137
     :OP_RESERVED2	138})

(def ^:private opcodes-rev (into {} (for [[k v] opcodes] [v k])))
(def ^:private push-codec-opcodes (set (range 1 76)))
(def ^:private push-codecs (zipmap push-codec-opcodes (map #(repeated :ubyte :length %) push-codec-opcodes)))

(def script-codec
  (let [] 
    (reify BinaryIO
      (read-data  [_ big-in little-in]
        (let [overall (read-data var-int-le big-in little-in)] 
          (loop [n 0, res []]
            (if 
              (= n overall) res
              (let [b (byte->ubyte (.readByte ^DataInput big-in))]
                (if (contains? push-codec-opcodes b) 
                  (recur (+ n b 1) (conj res (read-data (push-codecs b) big-in little-in)))
                  (recur (inc n) (conj res (opcodes-rev b)))))))))
      (write-data [_ big-out little-out script]
        (let [len (reduce #(if (keyword? %2) (inc %1) (+ %1 1 (count %2))) 0 script)]
          (write-data var-int-le big-out little-out len)
          (doseq [token script]
            (if (keyword? token)
              (.writeByte ^DataOutput big-out (opcodes token))
              (let [len (count token)]
                (.writeByte ^DataOutput big-out len)
                (write-data (push-codecs len) big-out little-out token)))))))))


;;;;;;;;;; blocks ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def block-magic (constant (repeated :ubyte :length 4) [0xf9 0xbe 0xb4 0xd9]))

(def hash (repeated :ubyte :length 32))

(defn var-len [codec]
  (repeated codec :prefix var-int-le))

(def transaction-input
  (ordered-map
    :hash hash
    :index :int-le
    :script script-codec 
    :sequence-number :int-le))

(def transaction-output
  (ordered-map
    :amount :long-le
    :script script-codec))

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