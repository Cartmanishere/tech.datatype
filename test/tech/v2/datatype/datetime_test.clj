(ns tech.v2.datatype.datetime-test
  (:require [tech.v2.datatype :as dtype]
            [tech.v2.datatype.datetime :as dtype-dt]
            [tech.v2.datatype.datetime.operations :as dtype-dt-ops]
            [tech.v2.datatype.functional :as dfn]
            [tech.v2.tensor :as dtt]
            [clojure.test :refer [deftest is]])
  (:import [java.time.temporal ChronoUnit]))


(deftest instant
  (let [base-instant (dtype-dt/instant)
        scalar-res (dtype-dt-ops/plus-days base-instant 1)
        iterable-res (dtype-dt-ops/plus-days base-instant
                                            (apply list (range 5)))
        reader-res (dtype-dt-ops/plus-days base-instant
                                           (range 5))]
    (is (= [:instant :instant :instant :instant]
           (mapv dtype/get-datatype [base-instant
                                     scalar-res
                                     iterable-res
                                     reader-res])))
    (is (dfn/reduce-and (dtype-dt-ops/== iterable-res reader-res)))
    (is (every? #(< (long %) 100)
                (dtype-dt-ops/difference-milliseconds
                 iterable-res
                 (->> (dtype-dt/pack iterable-res)
                      (dtype-dt/unpack)))))
    (is (= :packed-instant (dtype/get-datatype (dtype-dt/pack iterable-res))))
    (is (every? #(< (long %) 100)
                (dtype-dt-ops/difference-milliseconds
                 iterable-res
                 ;;test math on packed types
                 (->> (repeat 5 base-instant)
                      (dtype-dt/pack)
                      (dtype-dt-ops/plus-days (range 5))
                      (dtype-dt/unpack))))
        (format "expected:%s\n     got:%s"
                (mapv #(.toString ^Object %)
                      iterable-res)
                (mapv #(.toString ^Object %)
                      (->> (repeat 5 base-instant)
                           (dtype-dt/pack)
                           (dtype-dt-ops/plus-days (range 5))
                           (dtype-dt/unpack)))))))


(deftest local-date-time-add-day
  (let [base-elem (dtype-dt/local-date-time)
        scalar-res (dtype-dt-ops/plus-days base-elem 1)
        iterable-res (dtype-dt-ops/plus-days base-elem
                                            (apply list (range 5)))
        reader-res (dtype-dt-ops/plus-days base-elem
                                           (range 5))]
    (is (= [:local-date-time :local-date-time :local-date-time :local-date-time]
           (mapv dtype/get-datatype [base-elem
                                     scalar-res
                                     iterable-res
                                     reader-res])))
    (is (dfn/reduce-and (dtype-dt-ops/== iterable-res reader-res)))
    (is (every? #(< (long %) 100)
                (dtype-dt-ops/difference-milliseconds iterable-res
                                                      (->> (dtype-dt/pack iterable-res)
                                                           (dtype-dt/unpack)))))
    (is (= :packed-local-date-time (dtype/get-datatype (dtype-dt/pack iterable-res))))
    (is (every? #(< (long %) 100)
                (dtype-dt-ops/difference-milliseconds
                 iterable-res
                 (->> (repeat 5 base-elem)
                      (dtype-dt/pack)
                      (dtype-dt-ops/plus-days (range 5))
                      (dtype-dt/unpack))))
        (format "expected:%s\n     got:%s"
                (mapv #(.toString ^Object %)
                      iterable-res)
                (mapv #(.toString ^Object %)
                      (->> (repeat 5 base-elem)
                           (dtype-dt/pack)
                           (dtype-dt-ops/plus-days (range 5))
                           (dtype-dt/unpack)))))))


(deftest epoch-seconds-and-millis-have-correct-datatype
  (let [item-seq (repeat 5 (dtype-dt/instant))]
    (is (= :epoch-milliseconds
           (-> (dtype-dt-ops/get-epoch-milliseconds item-seq)
               (dtype/get-datatype))))
    (is (= :epoch-seconds
           (-> (dtype-dt-ops/get-epoch-seconds item-seq)
               (dtype/get-datatype))))))


(deftest epoch-times
  (let [start-zdt (dtype-dt/milliseconds-since-epoch->zoned-date-time 0)
        now-zdt (dtype-dt/zoned-date-time)]
    (is (= (.between (ChronoUnit/MINUTES) start-zdt now-zdt)
           (dtype-dt-ops/get-epoch-minutes now-zdt)))
    (is (= (.between (ChronoUnit/HOURS) start-zdt now-zdt)
           (dtype-dt-ops/get-epoch-hours now-zdt)))
    (is (= (.between (ChronoUnit/DAYS) start-zdt now-zdt)
           (dtype-dt-ops/get-epoch-days now-zdt)))
    (is (= (.between (ChronoUnit/WEEKS) start-zdt now-zdt)
           (dtype-dt-ops/get-epoch-weeks now-zdt)))))



;;I really don't expect tensors to be used with date time objects but regardless
;;the basic expectations of these things (like functions that take a tensor return
;;a tensor) all need to be respected.


(deftest simple-tensor-ops
  (let [src-data (dtype/make-container
                  :typed-buffer
                  :instant
                  9)
        test-tens (dtt/reshape src-data [3 3])]
    (is (= [3 3] (dtype/shape test-tens)))
    ;;Also, it has to print without exception
    (is (string? (.toString test-tens)))))