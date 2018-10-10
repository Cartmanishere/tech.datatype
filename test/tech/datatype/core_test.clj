(ns tech.datatype.core-test
  (:require [clojure.test :refer :all]
            [tech.datatype.core :as dtype]
            [tech.datatype.base :as base]
            [clojure.core.matrix :as m]
            [clojure.core.matrix.macros :refer [c-for]]
            [tech.datatype.java-primitive :as primitive])
  (:import [java.nio FloatBuffer]))


(deftest raw-copy-with-mutable-lazy-sequence
  ;;It is important that raw copy can work with a lazy sequence of double buffers where
  ;;the same buffer is being filled for ever member of the lazy sequence.  This is an easy
  ;;optimization to make that cuts down the memory usage when reading from datasets by
  ;;a fairly large amount.
  (let [input-seq (partition 10 (range 100))
        input-ary (double-array 10)
        ;;Note the input ary is being reused.  Now imagine the input ary is a large
        ;;and we want a batch size of 50.  This is precisely the use case
        ;;where we don't want to allocate dynamically an entire batch worth of data
        ;;every time but copy one image at a time into the packed buffer for upload
        ;;to the gpu.
        double-array-seq (map (fn [data]
                                (dtype/copy-raw->item! data input-ary 0)
                                input-ary)
                              input-seq)
        output-doubles (double-array 100)]
    (dtype/copy-raw->item! double-array-seq output-doubles 0)
    (is (= (vec output-doubles) (mapv double (flatten input-seq))))))

(defn basic-copy
  [src-fn dest-fn src-dtype dst-dtype]
  (let [ary (src-fn src-dtype (range 10))
        buf (dest-fn dst-dtype 10)
        retval (dtype/make-array-of-type :float64 10)]
    ;;copy starting at position 2 of ary into position 4 of buf 4 elements
    (dtype/copy! ary 2 buf 4 4)
    (dtype/copy! buf 0 retval 0 10)
    (is (m/equals [0 0 0 0 2 3 4 5 0 0]
                  (vec retval)))))


(def create-functions [dtype/make-array-of-type
                       dtype/make-buffer-of-type])



(deftest generalized-copy-test
  (->> (for [src-fn create-functions
             dst-fn create-functions
             src-dtype primitive/datatypes
             dst-dtype primitive/datatypes]
         (basic-copy src-fn dst-fn src-dtype dst-dtype))
       dorun))


(deftest array-of-array-support
  (let [^"[[D" src-data (make-array (Class/forName "[D") 5)
        _ (doseq [idx (range 5)]
            (aset src-data idx (double-array (repeat 10 idx))))
        dst-data (float-array (* 5 10))]
    ;;This should not hit any slow paths.
    (with-bindings {#'base/*error-on-slow-path* true}
      (dtype/copy-raw->item! src-data dst-data 0))
    (is (= (vec (float-array (flatten (map #(repeat 10 %) (range 5)))))
           (vec dst-data)))))


(deftest out-of-range-data-causes-exception
  (is (thrown? Throwable (dtype/copy! (int-array [1000 2000 3000 4000]) (byte-array 4)))))


(deftest out-of-range-data-does-not-cause-exception-if-unchecked
  (let [byte-data
        (dtype/copy! (int-array [1000 2000 3000 4000]) 0 (byte-array 4) 0 4 {:unchecked? true})]
    (is (= [-24 -48 -72 -96]
           (vec byte-data)))))


(set! *warn-on-reflection* true)


(deftest copy-time-test
  (testing "Run perf regression of times spent to copy data"
    (let [num-items (long 100000)
          src-data (float-array (range num-items))
          dst-data (float-array num-items)
          array-copy (fn []
                       (c-for [idx (int 0) (< idx num-items) (inc idx)]
                              (aset dst-data idx (aget src-data idx))))
          src-buf (FloatBuffer/wrap src-data)
          dst-buf (FloatBuffer/wrap dst-data)
          buffer-copy (fn []
                        (let [
                              ;; Curiously, uncommenting this gets a far faster result.
                              ;; But it isn't at all practical.
                              ;; src-buf (FloatBuffer/wrap src-data)
                              ;; dst-buf (FloatBuffer/wrap dst-data)
                              ]
                          (c-for [idx (int 0) (< idx num-items) (inc idx)]
                                 (.put dst-buf idx (.get src-buf idx)))))

          dtype-copy (fn []
                       (dtype/copy! src-buf 0 dst-buf 0 num-items))

          unchecked-dtype-copy (fn []
                                 (dtype/copy! src-buf 0 dst-buf 0 num-items {:unchecked? true}))

          raw-copy (get @base/*copy-table* [:nio-buffer :nio-buffer :float32 :float32 true])
          raw-dtype-copy (fn []
                           (raw-copy src-buf 0 dst-buf 0 num-items {:unchecked? true}))
          generic-copy (fn []
                         (base/generic-copy! src-buf 0 dst-buf 0 num-items {:unchecked? true}))
          fns {:array-copy array-copy
               :buffer-copy buffer-copy
               :dtype-copy dtype-copy
               :unchecked-dtype-copy unchecked-dtype-copy
               :raw-copy raw-dtype-copy
               :generic-copy generic-copy}
          run-timed-fns (fn []
                          (->> fns
                               (map (fn [[fn-name time-fn]]
                                      [fn-name (with-out-str
                                                 (time
                                                  (dotimes [iter 400]
                                                    (time-fn))))]))
                               (into {})))
          warmup (run-timed-fns)
          times (run-timed-fns)]
      (println times))))