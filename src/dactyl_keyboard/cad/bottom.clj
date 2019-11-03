;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; The Dactyl-ManuForm Keyboard — Opposable Thumb Edition              ;;
;; Bottom Plating                                                      ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(ns dactyl-keyboard.cad.bottom
  (:require [clojure.spec.alpha :as spec]
            [scad-clj.model :as model]
            [scad-tarmi.core :refer [π] :as tarmi-core]
            [scad-tarmi.maybe :as maybe]
            [scad-tarmi.threaded :as threaded]
            [dactyl-keyboard.generics :refer [colours]]
            [dactyl-keyboard.cad.misc :as misc]
            [dactyl-keyboard.cad.matrix :as matrix]
            [dactyl-keyboard.cad.place :as place]
            [dactyl-keyboard.cad.key :as key]
            [dactyl-keyboard.cad.body :as body]
            [dactyl-keyboard.cad.wrist :as wrist]
            [dactyl-keyboard.param.access
             :refer [most-specific get-key-alias tweak-data]]))


;;;;;;;;;;;
;; Basic ;;
;;;;;;;;;;;

(defn anchor-positive
  "A shape that holds a screw, and possibly a heat-set insert.
  Written for use as an OpenSCAD module."
  [getopt]
  (let [prop (partial getopt :case :bottom-plate :installation)
        style (prop :style)
        thickness (* 2 (prop :thickness))
        iso-size (prop :fasteners :diameter)
        head-height (threaded/head-height iso-size :countersunk)
        inserts (= style :inserts)
        base-top-diameter (if inserts (prop :inserts :diameter :top) iso-size)
        walled-top-diameter (+ base-top-diameter thickness)
        z-top-interior (if inserts (max (+ head-height (prop :inserts :length))
                                        (prop :fasteners :length))
                                   (prop :fasteners :length))
        dome (model/translate [0 0 z-top-interior]
               (model/sphere (/ walled-top-diameter 2)))]
    (if inserts
      (let [bottom-diameter (prop :inserts :diameter :bottom)
            top-disc
              (model/translate [0 0 z-top-interior]
                (model/cylinder (/ walled-top-diameter 2) 0.001))
            bottom-disc
              (model/translate [0 0 head-height]
                (model/cylinder (/ (+ bottom-diameter thickness) 2) 0.001))]
        (model/union
          dome
          (model/hull top-disc bottom-disc)
          (misc/bottom-hull bottom-disc)))
      (misc/bottom-hull dome))))

(defn anchor-negative
  "The shape of a screw and optionally a heat-set insert for that screw.
  Written for use as an OpenSCAD module."
  [getopt]
  (let [prop (partial getopt :case :bottom-plate :installation)
        style (prop :style)
        iso-size (prop :fasteners :diameter)]
    (maybe/union
      (model/rotate [π 0 0]
        (threaded/bolt
          :iso-size iso-size,
          :head-type :countersunk,
          :point-type :cone,
          :total-length (prop :fasteners :length),
          :unthreaded-length (when (= style :threads) 0),
          :threaded-length (when (not= style :threads) 0),
          :compensator (getopt :dfm :derived :compensator)
          :negative true))
      (when (= style :inserts)
        (let [d0 (prop :inserts :diameter :bottom)
              d1 (prop :inserts :diameter :top)
              z0 (threaded/head-height iso-size :countersunk)
              z1 (+ z0 (prop :inserts :length))]
          (misc/bottom-hull (model/translate [0 0 z1]
                              (model/cylinder (/ d1 2) 0.001))
                            (model/translate [0 0 z0]
                              (model/cylinder (/ d0 2) 0.001))))))))

(defn fastener-positions
  "Place instances of named module according to user configuration."
  [getopt part module-name]
  (apply maybe/union
    (map (place/wrist-module-placer getopt :bottom module-name)
         (case part
           :case (getopt :case :bottom-plate :installation :fasteners :positions)
           :wrist-rest (getopt :wrist-rest :bottom-plate :fastener-positions)))))

(defn- to-3d
  "Build a 3D bottom plate from a 2D block."
  [getopt block]
  (model/extrude-linear
    {:height (getopt :case :bottom-plate :thickness), :center false}
    block))


;;;;;;;;;;
;; Case ;;
;;;;;;;;;;

(defn- mask
  "A shape akin to the body mask but restricted to bottom-plate height."
  [getopt]
  (let [[x y _] (getopt :mask :size)
        z (getopt :case :bottom-plate :thickness)]
    (maybe/translate [0 0 (/ z 2)]
      (model/cube x y z))))

(defn- wall-base-3d
  "A sliver cut from the case wall."
  [getopt]
  (model/intersection
    (mask getopt)
    (maybe/union
      (key/metacluster body/cluster-wall getopt)
      (body/wall-tweaks getopt))))

(defn- floor-finder
  "Return a sequence of xy-coordinate pairs for exterior wall vertices."
  [getopt cluster edges]
  (map
    (fn [[coord direction turning-fn]]
      (let [key [:wall direction :extent]
            extent (most-specific getopt key cluster coord)]
        (when (= extent :full)
          (take 2
            (place/wall-corner-place getopt cluster coord
              {:directions [direction (turning-fn direction)] :vertex true})))))
    edges))

(defn- cluster-floor-polygon
  "A polygon approximating a floor-level projection of a key clusters’s wall."
  [getopt cluster]
  (maybe/polygon
    (filter some?  ; Get rid of edges with partial walls.
      (mapcat identity  ; Flatten floor-finder output by one level only.
        (reduce
          (fn [coll position]
            (conj coll
              (floor-finder getopt cluster (body/connecting-wall position))))
          []
          (matrix/trace-between
            (getopt :key-clusters :derived :by-cluster cluster :key-requested?)))))))

(defn- housing-floor-polygon
  "A polygon describing the area underneath the rear housing.
  A projection of the 3D shape would work but it would require taking care to
  hull the straight and other parts of the housing separately, because the
  connection between them may be concave. The 2D approach is safer."
  [getopt]
  (model/polygon
    (reduce
      (fn [coll pillar-fn] (conj coll (take 2 (pillar-fn true false))))
      []
      (body/housing-pillar-functions getopt))))

(defn- tweak-floor-vertex
  "A corner vertex on a tweak wall, extending from a key mount."
  [getopt segment-picker bottom
   [alias directions first-segment last-segment]]
  {:post [(spec/valid? ::tarmi-core/point-2d %)]}
  (let [segment (segment-picker (range first-segment (inc last-segment)))]
    (take 2 (place/reckon-from-anchor getopt alias
              {:corner directions, :segment segment, :bottom bottom}))))

(defn- dig-to-seq [node]
  (if (map? node) (dig-to-seq (:hull-around node)) node))

(defn- tweak-floor-pairs
  "Produce coordinate pairs for a polygon. A reducer."
  [getopt [post-picker segment-picker bottom] coll node]
  {:post [(spec/valid? ::tarmi-core/point-coll-2d %)]}
  (let [vertex-fn (partial tweak-floor-vertex getopt segment-picker bottom)]
    (conj coll
      (if (map? node)
        ;; Pick just one post in the subordinate node, on the assumption that
        ;; they’re not all ringing the case.
        (vertex-fn (post-picker (dig-to-seq node)))
        ;; Node is one post at the top level. Always use that.
        (vertex-fn node)))))

(defn- tweak-plate-polygon
  "A single version of the footprint of a tweak."
  [getopt pickers node-list]
  (model/polygon (reduce (partial tweak-floor-pairs getopt pickers) [] node-list)))

(defn- tweak-plate-shadows
  "Versions of a tweak footprint.
  This is a semi-brute-force-approach to the problem that we cannot easily
  identify which vertices shape the outside of the case at z = 0."
  [getopt node-list]
  (apply maybe/union
    (for
      [post [first last], segment [first last], bottom [false true]]
      (tweak-plate-polygon getopt [post segment bottom] node-list))))

(defn- all-tweak-shadows
  "The footprint of all user-requested additional shapes that go to the floor."
  [getopt]
  (apply maybe/union (map #(tweak-plate-shadows getopt (:hull-around %))
                          (filter :at-ground (tweak-data getopt)))))

(defn case-anchors-positive
  "The parts of the case body that receive bottom-plate fasteners."
  [getopt]
  (fastener-positions getopt :case "bottom_plate_anchor_positive"))

(defn- case-positive-2d
  "A union of polygons representing the interior of the case."
  [getopt]
  (maybe/union
    (key/metacluster cluster-floor-polygon getopt)
    (all-tweak-shadows getopt)
    ;; With a rear housing that connects to a regular key cluster wall, there
    ;; is a distinct possibility that two polygons (one for the housing, one
    ;; for the cluster wall) will overlap at one vertex, forming a union where
    ;; that particular intersection has no thickness.
    ;; As of 2019-04-9, OpenSCAD’s development snapshots will render a linear
    ;; extrusion from such a shape perfectly and it will produce a valid STL
    ;; that a slicer can handle.
    ;; However, if you then order some Boolean interaction between the
    ;; extrusion and any other 3D shape, while OpenSCAD will still render it,
    ;; it will not slice correctly: The mesh will be broken at the
    ;; intersection.
    (when (getopt :case :rear-housing :include)
      ;; To work around the problem, the housing-floor-polygon is moved a
      ;; tiny bit toward the origin, preventing the vertex overlap.
      (model/translate [0 -0.000001] (housing-floor-polygon getopt)))
    (when (and (getopt :wrist-rest :include)
               (= (getopt :wrist-rest :style) :threaded))
      (model/cut (wrist/all-case-blocks getopt)))))

(defn case-positive
  "A model of a bottom plate for the entire case but not the wrist rests.
  Screw holes not included."
  [getopt]
  (model/union
    (wall-base-3d getopt)
    (to-3d getopt (case-positive-2d getopt))))

(defn case-negative
  "Just the holes that go into both the case bottom plate and the case body."
  [getopt]
  (fastener-positions getopt :case "bottom_plate_anchor_negative"))

(defn case-complete
  "A printable model of a case bottom plate in one piece."
  [getopt]
  (model/color (:bottom-plate colours)
    (maybe/difference
      (case-positive getopt)
      (maybe/translate [0 0 (getopt :dfm :bottom-plate :fastener-plate-offset)]
        (case-negative getopt)))))


;;;;;;;;;;;;;;;;;
;; Wrist Rests ;;
;;;;;;;;;;;;;;;;;

(defn wrist-anchors-positive
  "The parts of the wrist-rest plinth that receive bottom-plate fasteners."
  [getopt]
  (let [with-plate (getopt :wrist-rest :bottom-plate :include)
        thickness (if with-plate (getopt :case :bottom-plate :thickness) 0)]
    (maybe/translate [0 0 thickness]
      (fastener-positions getopt :wrist-rest "bottom_plate_anchor_positive"))))

(defn- wrist-positive-2d [getopt]
  (model/cut (wrist/unified-preview getopt)))

(defn wrist-positive
  "3D wrist-rest bottom plate without screw holes."
  [getopt]
  (to-3d getopt (wrist-positive-2d getopt)))

(defn wrist-negative
  "Wrist-rest screw holes."
  [getopt]
  (fastener-positions getopt :wrist-rest "bottom_plate_anchor_negative"))

(defn wrist-complete
  "A printable model of a wrist-rest bottom plate in one piece."
  [getopt]
  (model/color (:bottom-plate colours)
    (maybe/difference
      (wrist-positive getopt)
      (maybe/translate [0 0 (getopt :dfm :bottom-plate :fastener-plate-offset)]
        (wrist-negative getopt)))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Combined Case and Wrist-Rest Plates ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn combined-positive
  "A combined bottom plate for case and wrist rest.
  This assumes the use of a threaded-style wrist rest, but will obviously
  prevent the use of threaded fasteners in adjusting the wrist rest.
  This is therefore recommended only where there is no space available between
  case and wrist rest."
  [getopt]
  (model/union
    (wall-base-3d getopt)
    (to-3d getopt
      (model/union
        (case-positive-2d getopt)
        (apply maybe/union
          (reduce
            (fn [coll pair]
              (conj coll (apply model/hull (map model/cut pair))))
            []
            (wrist/block-pairs getopt)))
        (wrist-positive-2d getopt)))))

(defn combined-negative
  [getopt]
  (model/union
    (case-negative getopt)
    (wrist-negative getopt)))

(defn combined-complete
  [getopt]
  (model/color (:bottom-plate colours)
    (maybe/difference
      (combined-positive getopt)
      (combined-negative getopt))))
