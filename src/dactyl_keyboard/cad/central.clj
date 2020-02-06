;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; The Dactyl-ManuForm Keyboard — Opposable Thumb Edition              ;;
;; Central Housing                                                     ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(ns dactyl-keyboard.cad.central
  (:require [thi.ng.math.core :as math]
            [scad-clj.model :as model]
            [scad-tarmi.core :refer [abs]]
            [scad-tarmi.maybe :as maybe]
            [scad-tarmi.threaded :as threaded]
            [scad-tarmi.util :refer [loft]]
            [dactyl-keyboard.cad.misc :refer [wafer]]
            [dactyl-keyboard.cad.poly :as poly]
            [dactyl-keyboard.cad.place :as place]))


;;;;;;;;;;;;;;;
;; Internals ;;
;;;;;;;;;;;;;;;

;; Geometry.
(defn- outline-back-to-3d
  [base-3d outline]
  (map (fn [[x _ _] [y1 z1]] [x y1 z1]) base-3d outline))
(defn- horizontal-shifter [x-fn] (fn [[x y z]] [(x-fn x) y z]))
(defn- mirror-shift [points] (map (horizontal-shifter -) points))
(defn- shift-points
  "Manipulate a series of 3D points forming a perimeter.
  Inset (contract) the points in the yz plane (in 2D) and/or shift each point
  on the x axis (back in 3D). Return a vector for indexability."
  ([base]  ; Presumably called for vector conversion.
   (shift-points base 0))
  ([base inset]
   (shift-points base inset 0))
  ([base inset delta-x]
   (shift-points base inset + delta-x))
  ([base inset x-operator delta-x]
   (as-> base subject
     (mapv rest subject)
     (poly/from-outline subject inset)
     (outline-back-to-3d base subject)
     (mapv (horizontal-shifter #(x-operator % delta-x)) subject))))

;; Predicates for sorting fasteners by the object they penetrate.
(defn- adapter-side [{:keys [lateral-offset]}] (neg? lateral-offset))
(defn- housing-side [{:keys [lateral-offset]}] (pos? lateral-offset))
(defn- any-side [_] true)

(defn- fastener-feature
  "The union of all features produced by a given model function at the sites of
  all adapter fasteners matching a predicate function, on the right-hand side."
  [getopt pred model-fn]
  (let [positions (getopt :case :central-housing :adapter :fasteners :positions)
        subject-fn #(place/chousing-fastener getopt % (model-fn getopt %))]
    (apply maybe/union (map subject-fn (filter pred positions)))))

(defn- single-right-side-fastener
  "A fastener for attaching the central housing to the rest of the case.
  Because threaded fasteners are chiral, the model is generated elsewhere
  and invoked here as a module, so scad-app can mirror it."
  [_ _]
  (model/call-module "housing_adapter_fastener"))

(defn- single-left-side-fastener
  "The same model, pre-emptively mirrored to get the right threading."
  [_ _]
  (model/mirror [-1 0 0] (model/call-module "housing_adapter_fastener")))

(defn- single-receiver
  "An extension through the central-housing interface array to receive a single
  fastener. This design is a bit rough; more parameters would be needed to
  account for the possibility of wall surfaces angled on the x or y axes."
  [getopt {:keys [lateral-offset]}]
  (let [rprop (partial getopt :case :central-housing :adapter :receivers)
        fprop (partial getopt :case :central-housing :adapter :fasteners)
        z-wall (getopt :case :web-thickness)
        diameter (fprop :diameter)
        width (+ diameter (* 2 (rprop :thickness :rim)))
        z-hole (- (fprop :length) z-wall)
        z-bridge (min z-hole (rprop :thickness :bridge))
        x-gabel (abs lateral-offset)
        x-inner (+ x-gabel (rprop :width :inner))
        x-taper (+ x-inner (rprop :width :taper))
        signed (fn [x] (* (- x) (math/sign lateral-offset)))]
    (loft
      ;; The furthermost taper sinks into a straight wall.
      [(model/translate [(signed x-taper) 0 (/ z-wall -2)]
         (model/cube wafer (dec diameter) wafer))
       ;; The thicker base of the anchor.
       (model/translate [(signed x-inner) 0 (- (+ z-wall (/ z-bridge 2)))]
         (model/union
           (model/cube wafer (inc diameter) z-bridge)
           (model/cube wafer diameter (inc z-bridge))))
       (model/translate [(signed x-gabel) 0 (- (+ z-wall (/ z-bridge 2)))]
         (model/union
           (model/cube wafer (inc diameter) z-bridge)
           (model/translate [0 0 -1]
             (model/cube wafer diameter z-bridge))))
       ;; Finally the bridge extending past the base.
       (model/translate [0 0 (- (+ z-wall (/ z-hole 2)))]
         (model/hull  ; Soft edges, more material in the middle.
           (model/cylinder (/ (inc diameter) 2) z-hole)
           (model/translate [0 0 (/ z-hole 8)]
             (model/cylinder (/ width 2) (/ z-hole 3)))))])))

(defn- collect-point-pair
  "Collect any aliases noted in the user configuration for one item in the
  interface array."
  [idx {:keys [base adapter]}]
  (let [props {:type :central-housing, :index idx}
        pluck (fn [alias part extra]
                (when alias [alias (merge props {:part part} extra)]))]
    [(pluck (:right-hand-alias base) :gabel {:side :right})
     (pluck (:left-hand-alias base) :gabel {:side :left})
     (pluck (:alias adapter) :adapter {})]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Configuration Interface ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn collect-point-aliases
  "A map of aliases to corresponding indices in the interface array."
  [getopt]
  (->> (getopt :case :central-housing :shape :interface)
    (map-indexed collect-point-pair)
    (apply concat)
    (into {})))

(defn derive-properties
  "Derive certain properties from the base configuration."
  [getopt]
  (let [thickness (getopt :case :web-thickness)
        half-width (/ (getopt :case :central-housing :shape :width) 2)
        adapter-width (getopt :case :central-housing :adapter :width)
        interface (getopt :case :central-housing :shape :interface)
        base-points (map #(get-in % [:base :offset]) interface)
        left-points (mirror-shift base-points)
        gabel-out (shift-points base-points 0 half-width)
        gabel-in (shift-points base-points thickness half-width)
        adapter-points-3d (map #(get-in % [:adapter :offset] [0 0 0]) interface)
        adapter-intrinsic (shift-points adapter-points-3d)
        adapter-outer (mapv (partial map + [adapter-width 0 0])
                            gabel-out adapter-intrinsic)
        lip-t (getopt :case :central-housing :adapter :lip :thickness)
        lip-w (partial getopt :case :central-housing :adapter :lip :width)
        include-main (and (getopt :reflect)
                          (getopt :case :central-housing :include))
        include-adapter (and include-main
                             (getopt :case :central-housing :adapter :include))]
    {:include-main include-main
     :include-adapter include-adapter
     :include-lip (and include-adapter
                       (getopt :case :central-housing :adapter :lip :include))
     :points
      {:gabel {:right {:outer gabel-out
                       :inner gabel-in}
               :left {:outer (shift-points left-points 0 - half-width)
                      :inner (shift-points left-points thickness - half-width)}}
       :adapter {:outer adapter-outer
                 :inner (shift-points adapter-outer thickness)}
       :lip {:outside {:outer (shift-points gabel-in 0 (lip-w :outer))
                       :inner (shift-points gabel-in lip-t (lip-w :outer))}
             :inside {:outer (shift-points gabel-in 0 - (+ (lip-w :taper)
                                                           (lip-w :inner)))
                      :inner (shift-points gabel-in lip-t - (lip-w :inner))}}}}))


;;;;;;;;;;;;;;;;;;;
;; Model Interop ;;
;;;;;;;;;;;;;;;;;;;

(defn build-fastener
  "A threaded fastener for attaching a central housing to its adapter.
  This needs to be mirrored for the left-hand-side adapter, being chiral.
  Hence it is written for use as an OpenSCAD module."
  [getopt]
  (let [prop (partial getopt :case :central-housing :adapter :fasteners)]
    (threaded/bolt
      :iso-size (prop :diameter),
      :head-type :countersunk,
      :point-type :cone,
      :total-length (prop :length),
      :compensator (getopt :dfm :derived :compensator)
      :negative true)))

(defn adapter-right-fasteners
  "All of the screws (negative space) for one side of the housing and adapter."
  [getopt]
  (fastener-feature getopt any-side single-right-side-fastener))

(defn adapter-left-fasteners
  "All of the screws for the other side. Due to a curiosity of the way bilateral
  symmetry is currently implemented for the central housing, this function does
  not mirror the positions of adapter-right-fasteners."
  [getopt]
  (fastener-feature getopt any-side single-left-side-fastener))

(defn adapter-fastener-receivers
  "Receivers for screws, extending from the central housing into the adapter."
  [getopt]
  (fastener-feature getopt housing-side single-receiver))

(defn tweak-post
  "Place an adapter between the housing polyhedron and a case wall."
  [getopt alias]
  {:pre [(keyword? alias)]}
  (let [shape (model/cube wafer wafer wafer)]
    (model/hull
      (place/reckon-from-anchor getopt alias {:depth :outer, :subject shape})
      (place/reckon-from-anchor getopt alias {:depth :inner, :subject shape}))))

(defn lip-body-right
  "A lip for an adapter."
  [getopt]
  (let [vertices (partial getopt :case :central-housing :derived :points :lip)]
    (poly/tuboid
      (vertices :outside :outer)
      (vertices :outside :inner)
      (vertices :inside :outer)
      (vertices :inside :inner))))

(defn adapter-shell
  "An OpenSCAD polyhedron describing an adapter for the central housing.
  This is just the positive shape, excluding secondary features like fasteners,
  because those may affect other parts of the adapted case."
  [getopt]
  (maybe/union
    (let [vertices (partial getopt :case :central-housing :derived :points)]
      (poly/tuboid
        (vertices :gabel :right :outer)
        (vertices :gabel :right :inner)
        (vertices :adapter :outer)
        (vertices :adapter :inner)))
    (fastener-feature getopt adapter-side single-receiver)))

(defn main-shell
  "An OpenSCAD polyhedron describing the body of the central housing.
  For use in building both the central housing itself as a program output
  and a bottom plate at floor level."
  [getopt]
  (let [vertices (partial getopt :case :central-housing :derived :points)]
    (poly/tuboid
      (vertices :gabel :left :outer)
      (vertices :gabel :left :inner)
      (vertices :gabel :right :outer)
      (vertices :gabel :right :inner))))

(defn negatives
  "Collected negative space for the keyboard case model beyond the adapter."
  [getopt]
  (maybe/union
    (adapter-fastener-receivers getopt)
    (adapter-right-fasteners getopt)))

