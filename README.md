# binary-dsl

This library is a high performance binary parser combinator. It enables reading and writing arbitrary binary data from Java's io streams. The focus is on enabling parsing of externally defined binary structures. If you have a format specification for any binary structure, this library is for you!

It is inspired by [Gloss](https://github.com/ztellman/gloss) but focuses on java's stream classes. The individual codecs do not require explicit knowledge about the length of data that needs to be read.

[![Build Status](https://secure.travis-ci.org/smee/binary.png)](http://travis-ci.org/smee/binary)

## Artifacts

Binary artifacts are [released to Clojars](https://clojars.org/smee/binary). If you are using Maven, add the following repository
definition to your `pom.xml`:

``` xml
<repository>
  <id>clojars.org</id>
  <url>http://clojars.org/repo</url>
</repository>
```

### The Most Recent Release

With [Leiningen](http://leiningen.org):

``` clojure
[smee/binary "0.5.4"]
```

With Maven:

``` xml
<dependency>
  <groupId>smee</groupId>
  <artifactId>binary</artifactId>
  <version>0.5.4</version>
</dependency>
```

### Note
All functions given in this document refer to the namespace `org.clojars.smee.binary.core` (needs to be `require`d or `use` in your namespace declaration).

## Examples / Demo

Please refer to the [tests](https://github.com/smee/binary/blob/master/test/org/clojars/smee/binary/codectests.clj) for now. There are several demos:

- the start of an [MP3 IDv2 parser](https://github.com/smee/binary/blob/master/src/org/clojars/smee/binary/demo/mp3.clj).
- Another demonstration is the [bitcoin block chain parser](https://github.com/smee/binary/blob/master/src/org/clojars/smee/binary/demo/bitcoin.clj#L168)
- [PNG file format](https://gist.github.com/stathissideris/8801295)
- [MATLAB 5](https://github.com/smee/binary/blob/master/src/org/clojars/smee/binary/demo/matlab5), currently read-only
- [ELF 32/64](https://github.com/smee/binary/blob/master/src/org/clojars/smee/binary/demo/elf.clj)

## Codec
To read binary data we need two things: A `codec` that knows how to read and write it's binary representation and convert it to a clojure data structure and an instance of `java.io.InputStream`.
The codec needs to satisfy the protocol `BinaryIO` (see [here](https://github.com/smee/binary/blob/master/src/org/clojars/smee/binary/core.clj#L6 "here")).

Codecs are composable, you may combine them as you like.

Each codec can have two attached functions:

- `pre-encode` - to convert clojure data to something that can be written to binary
- `post-decode` - to convert something read by a codec to a clojure/java data structure

Example: Let's represent an instance of java.util.Date as a unix epoch and write it as a little-endian long:

``` clojure
(compile-codec :long-le (fn [^java.util.Date date] (.getTime date)) (fn [^long number] (java.util.Date. number))
```

The compiler hints are not necessary. They are just a clarification in this example.
### API
- `encode` takes an instance of `codec`, a java.util.OutputStream and a value and writes the binary representation of this value into the stream.
- `decode` takes a `codec` and a java.util.InputStream and returns a clojure/java value that was read from the stream. Individual read via `decode` are eager!

### Features/Available codecs
#### Primitives
Encodes primitive data types, either **b**ig-**e**ndian or **l**ittle-**e**ndian:

``` clojure
; signed
:byte
:short-le
:short-be
:int-le
:int-be
:uint-le
:uint-be
:long-le
:long-be
:float-le
:float-be
:double-le
:double-be
; unsigned
:ubyte
:ushort-le
:ushort-be
:uint-le
:uint-be
:ulong-le
:ulong-be
```
Please be aware that since Java doesn't support unsigned data types the codecs will consume/produce a bigger data type than for the unsigned case: Unsinged bytes are shorts, unsigned shorts are integers, unsigned integers are longs, unsigned longs are Bigints!

### Sequences
If you want several codecs in a specific order, use a vector:

``` clojure
[:int-le :float-le :float-le]
```

### Maps
To name elements in a binary data source maps are ideal. Unfortunately the order of the keys is unspecified. We need to use a map constructor that respects the order of the keys:

``` clojure
(require '[org.clojars.smee.binary.core :as b])
(b/ordered-map :foo :int-le :bar [:float-le :double-le])
```

As you can see arbitrary nesting of codecs is possible. You can define maps of maps of ...
If you use clojure's map literals, the order of the binary values is unspecified (it is determined by the sequence of keys and values within the map's implementation).

### Repeated
`repeated` uses another `codec` repeatedly until the stream is exhausted. To restrict, how often the `codec` should be used, you can explicitely give one of trhee parameters:

- `:length` gives a fixed length. E.g. `(repeated :int-le :length 5)` will try to read/write exactly five little-endian 32bit integers from/to a stream
- `:prefix` takes another codec that will get read/written first. This `codec` contains the length for the successive read/write of the repeated values. Example: `(repeated :int-le :prefix :short-le)` will first read a short and tries then to read as many integers as specified in this short value.
- `:separator` will read values using the codec until the value read is the same as the given separator value. An example would be `(repeated :byte :separator (byte 0)` for null-tokenized c-strings. If the separator would be the last element in the stream, it is optional (think of comma-separated value where the last column may not have a trailing comma). 

**Caution**: When writing the data there **WILL** be a final separator. This means, the written data may have more bytes than initially read!

- No parameter means: read until exhaustion of the stream (EOF).

### Blob
`blob` is essentially an optimized version of `(repeated :byte ...)` that produces and consumes Java byte arrays. It takes the same options as `repeated`, except for `:separator`.

### String

Reads and writes bytes and converts them from/to strings with a specific string encoding. This codec uses `repeated`, that means it takes either `:length` or `:prefix` as parameter to determine the length of the string.

``` clojure
(string "ISO-8859-1" :length 3) ; read three bytes, interpret them as a string with encoding "ISO-8859-1"
```
### C strings

Similar to `string`, but reads bytes until it finds a null byte:

``` clojure
(c-string "UTF8") ; 
```
    

### Bits
If you have a byte where each bit has a specific meaning you can use a set of keywords as an input.
For example, the following definition says, that the lowest bit in a byte gets the value `:a`, the next one `:b`, then `:c`. The bits 4-7 are ignored, the highest bit has the value `:last`:

```clojure
(decode (bits [:a :b :c nil nil nil nil :last]) instream); let's assume the next byte in instream is 2r11011010
=> #{:b :last}
```
If you now read a byte with the value 2r11011001 using this codec you will get the clojure set `#{:a :b :last}` as a value.

### Header
Decodes a header using `header-codec`. Passes this datastructure to `header->body` which returns the codec to use to parse the body. For writing this codec calls `body->header` with the data as parameter and expects a value to use for writing the header information.

### Padding
Make sure there is always a minimum byte `length` when reading/writing values.
Works by reading `length` bytes into a byte array, then reading from that array using `inner-codec`.
Currently there are three options:

- `:length` is the number of bytes that should be present after writing
- `:padding-byte` is the numeric value of the byte used for padding (default is 0)
- `:truncate?` is a boolean flag that determines the behaviour if `inner-codec` writes more bytes than `padding` can handle: false is the default, meaning throw an exception. True will lead to truncating the output of `inner-codec`.

Example:

``` clojure
(padding (repeated :int-le :length 100) :length 1024 :padding-byte (byte \x))
=> [...] ; sequence of 100 integers, the stream will have 1024 bytes read, though

(encode (padding (repeated (string "UTF8" :separator 0)) :length 11 :truncate? true) outstream ["abc" "def" "ghi"])
=> ; writes bytes [97 98 99 0 100 101 102 0 103 104 105]
   ; observe: the last separator byte was truncated!
```

### Align
This codec is related to `padding` in that it makes sure that the number of bytes
written/read to/from a stream always is aligned to a specified byte boundary.
For example, if a format requires aligning all data to 8 byte boundaries this codec
will pad the written data with `padding-byte` to make sure that the count of bytes written
is divisable by 8.

Parameters:

- `modulo`: byte boundary modulo, should be positive
- `:padding-byte` is the numeric value of the byte used for padding (default is 0)

Example:

``` clojure
(encode (align (repeated :short-be :length 3) :modulo 9 :padding-byte 55) [1 2 3] output-stream)
;==> writes these bytes: [0 1 0 2 0 3 55 55 55]
```

### Constant
If a binary format uses fixed elements (like the three bytes 'ID3' in mp3), you can use this codec. It needs a codec and a fixed value. If the value read using this codec does not match the given fixed value, an exception will be thrown.

``` clojure
(constant (string "ISO-8859-1" :length 3) "ID3")
```
Alternatively, you may treat strings and byte arrays as ```constant``` encoders.


### Union
Union is a C-style union. A fixed number of bytes may represent different values depending on the interpretation of the bytes. The value returned by `read-data` is a map of all valid interpretations according to the specified unioned codecs.
Parameter is the number of bytes needed for the longest codec in this union and a map of value names to codecs.
This codec will read the specified number of bytes from the input streams and then successively try to read from this byte array using each individual codec.

Example: Four bytes may represent an integer, two shorts, four bytes, a list of bytes with prefix or a string.

``` clojure
(union 4 {:integer :int-be 
          :shorts (repeated :short-be :length 2)
          :bytes (repeated :byte :length 4)
          :prefixed (repeated :byte :prefix :byte)
          :str (string \"UTF8\" :prefix :byte)})
```
## License

Copyright © 2014 Steffen Dienst

Distributed under the Eclipse Public License, the same as Clojure.
