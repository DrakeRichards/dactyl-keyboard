(ns dactyl-keyboard.cad.matrix-test
  (:require [clojure.test :refer [deftest testing is]]
            [dactyl-keyboard.cad.matrix :as matrix]))

(deftest test-walk
  (testing "Walking in place."
    (is (= (matrix/walk [0 0]) [0 0])))
  (testing "Walking one step north."
    (is (= (matrix/walk [0 0] :N) [0 1])))
  (testing "Walking two steps north."
    (is (= (matrix/walk [0 0] :N :N) [0 2]))))

(deftest test-trace-edge
  (testing "Walking the edge of a single tile."
    (is (= (first (matrix/trace-edge
                    (fn [coord] (= coord [0 0]))
                    {:coordinates [0 0], :direction :N}))
           {:coordinates [0 0], :direction :N, :corner ::matrix/outer}))
    (is (= (take 5 (matrix/trace-edge
                     (fn [coord] (= coord [0 0]))
                     {:coordinates [0 0], :direction :N}))
           [{:coordinates [0 0], :direction :N, :corner ::matrix/outer}
            {:coordinates [0 0], :direction :E, :corner ::matrix/outer}
            {:coordinates [0 0], :direction :S, :corner ::matrix/outer}
            {:coordinates [0 0], :direction :W, :corner ::matrix/outer}
            {:coordinates [0 0], :direction :N, :corner ::matrix/outer}])))
  (testing "Walking the edge of a mirror L shape."
    (is (= (take 6 (matrix/trace-edge
                     (fn [coord] (contains? #{[0 0] [1 0] [1 1]} coord))
                     {:coordinates [0 0], :direction :N}))
           [{:coordinates [0 0], :direction :N, :corner ::matrix/outer}
            {:coordinates [0 0], :direction :E, :corner ::matrix/inner}
            {:coordinates [1 1], :direction :N, :corner ::matrix/outer}
            {:coordinates [1 1], :direction :E, :corner ::matrix/outer}
            {:coordinates [1 1], :direction :S, :corner nil}
            {:coordinates [1 0], :direction :S, :corner ::matrix/outer}]))))

(deftest test-trace-between
  (testing "Walking a straight edge with an explicit stopping position."
    (is (= (matrix/trace-between
             (fn [coord] (not-any? neg? coord))
             {:coordinates [0 0], :direction :W}
             {:coordinates [0 4], :direction :N})
           [{:coordinates [0 0], :direction :W, :corner ::matrix/outer}
            {:coordinates [0 0], :direction :N, :corner nil}
            {:coordinates [0 1], :direction :N, :corner nil}
            {:coordinates [0 2], :direction :N, :corner nil}
            {:coordinates [0 3], :direction :N, :corner nil}])))
  (testing "Walking a lap around a single tile, with explicit stop."
    (is (= (take 4 (matrix/trace-between
                     (fn [coord] (= coord [0 0]))
                     {:coordinates [0 0], :direction :N}
                     {:coordinates [0 0], :direction :N}))
           [{:coordinates [0 0], :direction :N, :corner ::matrix/outer}
            {:coordinates [0 0], :direction :E, :corner ::matrix/outer}
            {:coordinates [0 0], :direction :S, :corner ::matrix/outer}
            {:coordinates [0 0], :direction :W, :corner ::matrix/outer}])))
  (testing "Walking a lap around a single tile, with implicit stop."
    (is (= (matrix/trace-between
             (fn [coord] (= coord [0 0])))
           [{:coordinates [0 0], :direction :N, :corner ::matrix/outer}
            {:coordinates [0 0], :direction :E, :corner ::matrix/outer}
            {:coordinates [0 0], :direction :S, :corner ::matrix/outer}
            {:coordinates [0 0], :direction :W, :corner ::matrix/outer}])))
  (testing "Walking a lap around a single tile, shifted 90º."
    (is (= (matrix/trace-between
             (fn [coord] (= coord [0 0]))
             {:coordinates [0 0], :direction :E})
           [{:coordinates [0 0], :direction :E, :corner ::matrix/outer}
            {:coordinates [0 0], :direction :S, :corner ::matrix/outer}
            {:coordinates [0 0], :direction :W, :corner ::matrix/outer}
            {:coordinates [0 0], :direction :N, :corner ::matrix/outer}]))))
