(ns org.clojars.smee.binary.codectests
  (:use clojure.test
        org.clojars.smee.binary.core)
  (:require [org.clojars.smee.binary.demo.protobuf :as pb]
            [org.clojars.smee.binary.demo.bitcoin :as btc]))

(defn- test-roundtrip [codec value expected-bytes] 
  (let [baos (java.io.ByteArrayOutputStream.)
        _ (encode codec baos value)
        arr (.toByteArray baos)
        encoded-bytes (map byte->ubyte (seq arr))
        decoded (decode codec (java.io.ByteArrayInputStream. arr))]
;    (println codec value expected-bytes decoded (java.lang.Long/toBinaryString decoded)) (doseq [b encoded-bytes] (print (java.lang.Integer/toHexString b) " ")) (println)    
    (is (= (class decoded) (class value)))
    (is (= decoded value))  
    (when expected-bytes
      (is (= encoded-bytes expected-bytes)))))

(defn- test-all-roundtrips [test-cases]
  (doseq [[codec value bytes] test-cases]
    (test-roundtrip (compile-codec codec) value bytes)))

(deftest primitive-encodings
  (test-all-roundtrips
    [[:short-be (short 5) [0 5]]
     [:short-le (short 5) [5 0]]
     [:int-be (int 127) [0 0 0 127]]
     [:int-le (int 127) [127 0 0 0]]
     [:uint-le (long 255) [255 0 0 0]]
     [:uint-be (long 255) [0 0 0 255]]
     [:long-be (long 31) [0 0 0 0 0 0 0 31]]
     [:long-le (long 31) [31 0 0 0 0 0 0 0]]
     [:float-le (float 123.45) [0x66 0xe6 0xf6 0x42]]
     [:float-be (float 123.45) [0x42 0xf6 0xe6 0x66]]
     [:double-be (double 123.45) [64 94 220 204 204 204 204 205]]
     [:double-le (double 123.45) [205 204 204 204 204 220 94 64]]
     [(compile-codec :int-be dec inc) 1 [0 0 0 0]] ; test pre-encode and post-decode
     ]))

(deftest string-encodings
  (test-all-roundtrips 
    [[(string "UTF8" :prefix :byte) "ABC" [3 65 66 67]]
     [(string "UTF8" :prefix :int-be) "ABC" [0 0 0 3 65 66 67]]
     [(string "UTF8" :prefix :short-le) "ABC" [3 0 65 66 67]]
     [(string "UTF8" :length 2) "AA" [65 65]]])) 

(deftest map-encodings
  (test-all-roundtrips
    [[(ordered-map 
        :foo :int-be
        :bar :short-le
        :baz :ubyte) {:foo 1 :bar 0, :baz 255} [0 0 0 1 0 0 255]]
     [{:foo :int-be
       :bar :short-le
       :baz :ubyte} {:foo 1 :bar 0, :baz 255}]]))

#_(deftest map-manipulations
  (is (= 0 (count (ordered-map))))
  (is (= [:foo :bar] (keys (ordered-map :foo :byte :bar :int)))))

(deftest repeated-encodings
  (test-all-roundtrips
    [[(repeated :byte :prefix :byte) (vec (range 5)) [5 0 1 2 3 4]]
     [(repeated :byte :length 5) (vec (range 5)) [0 1 2 3 4]]
     [(repeated (string "UTF8" :prefix :byte) :prefix :int-be) ["AAA" "BB" "C"] [0 0 0 3 3 65 65 65 2 66 66 1 67]]
     [(repeated :byte) (vec (range 5)) [0 1 2 3 4]]
     [(repeated :short-le) (vec (range 5)) [0 0 1 0 2 0 3 0 4 0]]]))

(deftest sequence-encodings
  (test-all-roundtrips
    [[[:byte :byte] [1 2] [1 2]]
     [[:short-be :int-be :short-le] [1 2 99] [0 1 0 0 0 2 99 0]]]))

(defn- binary [s]
  (Long/parseLong s 2))

(deftest bitmasks
  (test-all-roundtrips
    [[(bits [:a :b :c nil nil nil nil :last]) #{:c :last} [(binary "10000100")]]
     [(bits [:flag1 :flag2]) #{:flag2} [(binary "00000010")]]
     [(bits [:flag1 :flag2]) #{} [(binary "00000000")]]]))

(deftest mixed-encodings
  (test-all-roundtrips
    [[(ordered-map :foo [:byte :byte]
                   :bar (string "UTF8" :prefix :int-be))
      {:foo [1 2], :bar "test"}
      [1 2 0 0 0 4 116 101 115 116]]]))

(deftest wrong-length
  (are [codec values] (is (thrown? java.lang.RuntimeException (encode codec (NullOutputStream.) values)))
       (string "UTF-8" :length 5) "X"
       (repeated :int :length 3) [1 2]
       (padding :int-le 1) (int 1234)
       (padding :int-le 3) (int 1234)))

(deftest paddings
  (test-all-roundtrips
    [[(padding :int-be 6 (int \x)) (int 55) [0 0 0 55 120 120]]
     [(padding (string "UTF8" :length 6) 6) "abcdef" [97 98 99 100 101 102]]
     [(padding (repeated :int-le) 10 0x99) [1 2] [1 0 0 0 2 0 0 0 0x99 0x99]]]))

(deftest constants
  (test-all-roundtrips
    [[(constant :int-le 7) 7 [7 0 0 0]]
     [(constant (string "UTF8" :length 2) "AB") "AB" [65 66]]]))

(deftest constants-exception-on-wrong-value
  (let [codec (constant (string "UTF8" :length 2) "AB")] 
    (test-roundtrip codec "AB" [65 66])
    (is (thrown? java.lang.AssertionError
                 (decode codec (java.io.ByteArrayInputStream. (byte-array [(byte 0) (byte 0)])))))))

(deftest headers
  (test-all-roundtrips
    [[(header :byte #(string "utf8" :length %) #(.length %)) "ABC" [3 65 66 67]]
     ]))

(deftest bitcoin-block
  (test-all-roundtrips
    [[btc/block-codec
      {:transactions 
       [{:lock-time 0, 
         :outputs [{:script [[4 103 138 253 176 254 85 72 39 25 103 241 166 113 48 183 16 92 214 168 40 224 57 9 166 121 98 224 234 31 97 222 182 73 246 188 63 76 239 56 196 243 85 4 229 30 193 18 222 92 56 77 247 186 11 141 87 138 76 112 43 107 241 29 95] :OP_CHECKSIG], 
                    :amount 5000000000}], 
         :inputs [{:sequence-number -1, 
                   :script [[255 255 0 29] [4] [84 104 101 32 84 105 109 101 115 32 48 51 47 74 97 110 47 50 48 48 57 32 67 104 97 110 99 101 108 108 111 114 32 111 110 32 98 114 105 110 107 32 111 102 32 115 101 99 111 110 100 32 98 97 105 108 111 117 116 32 102 111 114 32 98 97 110 107 115]], 
                   :index -1, 
                   :hash [0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0]}], 
         :transaction-version 1}], 
       :header {:nonce 2083236893, 
                :target 486604799, 
                :timestamp #inst "2009-01-03T18:15:05.000-00:00", 
                :merkle-root [59 163 237 253 122 123 18 178 122 199 44 62 103 118 143 97 127 200 27 195 136 138 81 50 58 159 184 170 75 30 94 74], 
                :previous-hash [0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0], 
                :block-version 1}, 
       :length 285, 
       :separator [249 190 180 217]}
      [249 190 180 217 29 1 0 0 1 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 59 163 237 253 122 123 18 178 122 199 44 62 103 118 143 97 127 200 27 195 136 138 81 50 58 159 184 170 75 30 94 74 41 171 95 73 255 255 0 29 29 172 43 124 1 1 0 0 0 1 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 255 255 255 255 77 4 255 255 0 29 1 4 69 84 104 101 32 84 105 109 101 115 32 48 51 47 74 97 110 47 50 48 48 57 32 67 104 97 110 99 101 108 108 111 114 32 111 110 32 98 114 105 110 107 32 111 102 32 115 101 99 111 110 100 32 98 97 105 108 111 117 116 32 102 111 114 32 98 97 110 107 115 255 255 255 255 1 0 242 5 42 1 0 0 0 67 65 4 103 138 253 176 254 85 72 39 25 103 241 166 113 48 183 16 92 214 168 40 224 57 9 166 121 98 224 234 31 97 222 182 73 246 188 63 76 239 56 196 243 85 4 229 30 193 18 222 92 56 77 247 186 11 141 87 138 76 112 43 107 241 29 95 172 0 0 0 0]]]))

#_(deftest protobuf
  (test-all-roundtrips
    [[pb/proto-key [150 0] [8]]
     [pb/proto-delimited "testing" [0x12 0x07 0x74 0x65 0x73 0x74 0x69 0x6e 0x67]]]))