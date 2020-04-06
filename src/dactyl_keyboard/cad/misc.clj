;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; The Dactyl-ManuForm Keyboard — Opposable Thumb Edition              ;;
;; Miscellaneous CAD Utilities                                         ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;; Functions useful in more than one scad-clj project.

(ns dactyl-keyboard.cad.misc
  (:require [clojure.spec.alpha :as spec]
            [scad-clj.model :as model]
            [scad-tarmi.core :as tarmi]
            [scad-tarmi.maybe :as maybe]
            [scad-klupe.iso :refer [bolt]]))

(def wafer 0.001)  ; Generic insignificant feature size.
(def nodule (apply model/cube (repeat 3 wafer)))

(defn map-to-3d-vec
  "Turn a descriptive hash map into an x-y-z vector."
  {:post [#(spec/valid? ::tarmi/point-3d %)]}
  [{:keys [width depth length thickness height]}]
  [(or width 1) (or depth length 1) (or thickness height 1)])

(defn shallow-wrap
  "Return a permissible index to a list-like collection.
  This function does not wrap around completely so that e.g. -2 indexes
  the second-to-last-element, but it does pick the opposite end of the
  collection when out of bounds."
  [array index]
  (let [n (count array)]
    (cond (< index 0) (dec n), (>= index n) 0, :default index)))

(defn pad-to-3d
  "Pad a coordinate vector to three dimensions."
  ([coordinates] (pad-to-3d coordinates 0))
  ([coordinates padding]
   {:pre [(vector? coordinates)
          (<= (count coordinates) 3)]}
   (if (= (count coordinates) 3)
     coordinates
     (pad-to-3d (conj coordinates padding) padding))))

(defn z0
  "No relation to Cicero."
  [coordinates]
  {:pre [(vector? coordinates)]}
  (pad-to-3d (subvec coordinates 0 2) 0))

(defn bottom-extrusion [height p]
  (model/extrude-linear {:height height, :twist 0, :convexity 0, :center false}
    (model/project p)))

(defn bottom-hull [& p]
  (model/hull p (bottom-extrusion wafer p)))

(defn swing-callables
  "Rotate passed object with passed radius, not around its own axes.
  The ‘translator’ function receives a vector based on the radius, in the z
  axis only, and an object to translate.
  If ‘rotator’ is a 3-vector of angles or a 2-vector of an angle and an axial
  filter, a rotation function will be created based on that."
  [translator radius rotator obj]
  (if (vector? rotator)
    (if (= (count rotator) 3)
      (swing-callables translator radius (partial maybe/rotate rotator) obj)
      (swing-callables translator radius
        (partial maybe/rotate (first rotator) (second rotator))
        obj))
    ;; Else assume the rotator is usable as a function and apply it.
    (->> obj
      (translator [0 0 (- radius)])
      rotator
      (translator [0 0 radius]))))

(defn merge-bolt
  "Wrap scad-klupe.iso/bolt for multiple sources of parameters."
  [& option-maps]
  (bolt (apply merge option-maps)))
