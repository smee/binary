# binary-dsl

This library is a high performance binary parser combinator. It enables reading and writing arbitrary binary data from Java's io streams. The focus is on enabling parsing of externally defined binary structures. If you have a format specification for any binary structure, this library is for you!

It is inspired by [Lamina](https://github.com/ztellman/lamina) but focuses on java's stream classes. The individual codecs do not require explicit knowledge about the length of data that needs to be read.

[![Build Status](https://secure.travis-ci.org/smee/binary.png)](http://travis-ci.org/smee/binary)

## Artifacts

Binary artifacts are [released to Clojars](https://clojars.org/org.clojars.smee/binary). If you are using Maven, add the following repository
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
[org.clojars.smee/binary "0.2.5"]
```

With Maven:

``` xml
<dependency>
  <groupId>org.clojars.smee</groupId>
  <artifactId>binary</artifactId>
  <version>0.2.4</version>
</dependency>
```

### Note
All functions given in this document refer to the namespace `org.clojars.smee.binary.core` (needs to be `require`d or `use` in your namespace declaration).

## Examples / Demo

Please refer to the [tests](https://github.com/smee/binary/blob/master/test/org/clojars/smee/binary/codectests.clj) for now or the start of an [MP3 IDv2 parser](https://github.com/smee/binary/blob/master/src/org/clojars/smee/binary/demo/mp3.clj).
Another demonstration is the [bitcoin block chain parser](https://github.com/smee/binary/blob/master/src/org/clojars/smee/binary/demo/bitcoin.clj#L168)

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
```

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
Make sure there is always a minimum byte `length` when writing a value. Reads at least the given number of bytes from the inputstream before parsing it using the inner codec.
Per default the padding bytes are 0. Optionally a third parameter may specify the byte value to use for padding.

``` clojure
(padding (repeated :int-le :length 100) 1024)
=> [...] ; sequence of 100 integers, the stream will have 1024 bytes read, though
```

### Constant
If a binary format uses fixed elements (like the three bytes 'ID3' in mp3), you can use this codec. It needs a codec and a fixed value. If the value read using this codec does not match the given fixed value, an exception will be thrown.

``` clojure
(constant (string "ISO-8859-1" :length 3) "ID3")
```

More documentation is currently in the pipeline...

## License

Copyright © 2014 Steffen Dienst

Distributed under the Eclipse Public License, the same as Clojure.
