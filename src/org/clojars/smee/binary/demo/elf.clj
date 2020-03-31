(ns org.clojars.smee.binary.demo.elf
  (:require [org.clojars.smee.binary.core :refer :all]
            [clojure.java.io :only [input-stream]]))

(def primitives 
  {:elf64 {:addr (align :ulong-le :modulo 8)
           :off  (align :ulong-le :modulo 8)
           :half  (align :ushort-le :modulo 2)
           :word  (align :uint-le :modulo 4)
           :xword (align :ulong-le :modulo 8)
           :sword (align :long-le :modulo 8)}
   :elf32 {:addr (align :uint-le :modulo 4)
           :off  (align :uint-le :modulo 4)
           :half  (align :ushort-le :modulo 2)
           :word  (align :uint-le :modulo 4)
           :sword  (align :int-le :modulo 4)}})

(def e-ident
  (padding
    (ordered-map
      :ei-magic "ELF" 
      :ei-class (enum :byte {:elf32 1 :elf64 2})
      :ei-data (enum :byte {:le 1 :be 2})
      :ei-version :byte
      :ei-osabi :byte)
    :length 16))

(defn phdr [endianess] 
  (let [{:keys [word addr off xword]} (primitives endianess)] 
    (if (= endianess :elf64) 
      (ordered-map
        :p-type (enum word {:null 0
                            :load 1
                            :dynamic 2
                            :interp 3
                            :note 4
                            :shlib 5
                            :phdr 6
                            :loos 0x60000000
                            :gnu-eh-frame 0x6474e550
                            :gnu-stack 0x6474e551
                            :gnu-relro 0x6474e552
                            :hios 0x6fffffff
                            :loproc 0x70000000
                            :hiproc 0x7fffffff
                            } :lenient? true)  ; type of segment, ignores vendor specifics
        :p-flags (padding (bits [:x :w :r]) :length 2) ; segment attributes
        :p-offset off ; offset in file
        :p-vaddr addr ; virtual address in memory
        :p-paddr addr ; reserved
        :p-filesz xword ; size of segment in file
        :p-memsz xword ; size of segment in memory
        :p-align xword ; alignment of segment
        )
      (ordered-map
        :p-type (enum word {:null 0
                            :load 1
                            :dynamic 2
                            :interp 3
                            :note 4
                            :shlib 5
                            :phdr 6
                            :loos 0x60000000
                            :gnu-eh-frame 0x6474e550
                            :gnu-stack 0x6474e551
                            :gnu-relro 0x6474e552
                            :hios 0x6fffffff
                            :loproc 0x70000000
                            :hiproc 0x7fffffff
                            } :lenient? true)  ; type of segment, ignores vendor specifics
        :p-offset off ; offset in file
        :p-vaddr addr ; virtual address in memory
        :p-paddr addr ; reserved
        :p-filesz word ; size of segment in file
        :p-memsz word ; size of segment in memory
        :p-flags (padding (bits [:x :w :r]) :length 2) ; segment attributes
        :p-align word ; alignment of segment
        ))))

(def ehdr
  (-> e-ident
    (header 
      (fn [ident]
        (let [{:keys [word half addr off]} (-> ident :ei-class primitives)]
          (ordered-map                  
            :e-type (enum half {:none 0
                                :rel 1
                                :exec 2
                                :dyn 3
                                :core 4
                                :loos 0xfe00
                                :hios 0xfeff
                                :loproc 0xff00
                                :hiproc 0xffff})      ; object file type
            :e-machine (enum half {:none 0 
                                   :at&t-we-32100 1 
                                   :sparc 2
                                   :intel-80386 3
                                   :motorola-68000 4
                                   :motorola-88000 5
                                   :intel-80860 7
                                   :mips-rs3000 8
                                   :amd64 62} :lenient? true)    ; machine type
            :e-version word   ; object file version
            :e-entry addr     ; entry point address
            :e-phoff off      ; program header offset
            :e-shoff off      ; section header offset
            :e-flags word     ; processor-specific flags
            :e-ehsize half    ; elf header size
            :e-phentsize half ; size of program header in entry
            :e-phnum half     ; number of program header entries
            :e-shentsize half ; size of section header entry
            :e-shnum half     ; number of section header entry 
            :e-shstmdx half   ; section name string table index
            )))
      nil
      :keep-header? true)
    (compile-codec
      #(hash-map :header (:e-ident %) :body (dissoc % :e-ident))
      #(assoc (:body %) :e-ident (:header %)))))

(def elf-codec
  (-> ehdr
    (header 
      #(repeated (phdr (-> % :e-ident :ei-class)) :length (:e-phnum %))
      nil
      :keep-header? true)
    (compile-codec    
      #(hash-map :header (:e-header %) :body (:p-headers %))
      #(hash-map :e-header (:header %) :p-headers (:body %)))))




(comment
  (require 'clojure.pprint)
  (set! *print-length* nil)
  (with-open [is (CountingInputStream. (input-stream "echo32"))
              os (clojure.java.io/output-stream "echo32.out")] 
    (let [elf (decode elf-codec is)] 
      (clojure.pprint/pprint elf)      
      (encode elf-codec os elf))
    (println (format "bytes read: %x" (.size is))))  
  )
