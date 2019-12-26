;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; The Dactyl-ManuForm Keyboard — Opposable Thumb Edition              ;;
;; Placement Utilities                                                 ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;; This module consolidates functions on the basis that some minor features,
;;; including foot plates and bottom-plate anchors, can be positioned in
;;; relation to multiple other types of features, creating the need for a
;;; a high-level, delegating placement utility that builds on the rest.

(ns dactyl-keyboard.cad.place
  (:require [clojure.spec.alpha :as spec]
            [scad-clj.model :as model]
            [scad-tarmi.core :refer [abs π] :as tarmi-core]
            [scad-tarmi.maybe :as maybe]
            [scad-tarmi.flex :as flex]
            [dmote-keycap.data :as capdata]
            [dmote-keycap.measure :as measure]
            [dactyl-keyboard.generics :refer [directions-to-unordered-corner]]
            [dactyl-keyboard.cad.matrix :as matrix]
            [dactyl-keyboard.cad.misc :as misc]
            [dactyl-keyboard.param.access
             :refer [most-specific resolve-anchor key-properties]]))


;;;;;;;;;;;;;;;
;; Functions ;;
;;;;;;;;;;;;;;;

;; Primitives.

(defn lateral-offset
  "Produce a 3D vector for moving something laterally."
  [getopt direction offset]
  (let [{:keys [dx dy]} (matrix/compass-to-grid direction)]
    [(* dx offset) (* dy offset) 0]))


;; Key mounts.

(defn mount-corner-offset
  "Produce a mm coordinate offset for a corner of a switch mount."
  [getopt key-style directions]
  (let [style-data (getopt :keys :derived key-style)
        [subject-x subject-y] (map measure/key-length
                                (get style-data :unit-size [1 1]))
        m (getopt :case :key-mount-corner-margin)]
    [(* (apply matrix/compass-dx directions) (- (/ subject-x 2) (/ m 2)))
     (* (apply matrix/compass-dy directions) (- (/ subject-y 2) (/ m 2)))
     (/ (getopt :case :web-thickness) -2)]))

(defn- curver
  "Given an angle for progressive curvature, apply it. Else lay keys out flat."
  [subject dimension-n rotate-type delta-fn orthographic
   rot-ax-fn getopt cluster coord obj]
  (let [index (nth coord dimension-n)
        most #(most-specific getopt % cluster coord)
        angle-factor (most [:layout rotate-type :progressive])
        neutral (most [:layout :matrix :neutral subject])
        separation (most [:layout :matrix :separation subject])
        delta-f (delta-fn index neutral)
        delta-r (delta-fn neutral index)
        angle-product (* angle-factor delta-f)
        flat-distance (* capdata/mount-1u (- index neutral))
        key-prop (key-properties getopt cluster coord)
        {:keys [switch-type skirt-length]} key-prop
        radius (+ (measure/resting-clearance switch-type skirt-length)
                  (/ (/ (+ capdata/mount-1u separation) 2)
                     (Math/sin (/ angle-factor 2))))
        ortho-x (- (* delta-r (+ -1 (- (* radius (Math/sin angle-factor))))))
        ortho-z (* radius (- 1 (Math/cos angle-product)))]
   (if (zero? angle-factor)
     (flex/translate (assoc [0 0 0] dimension-n flat-distance) obj)
     (if orthographic
       (->> obj
            (rot-ax-fn angle-product)
            (flex/translate [ortho-x 0 ortho-z]))
       (misc/swing-callables flex/translate radius
                             (partial rot-ax-fn angle-product) obj)))))

(defn- put-in-column
  "Place a key in relation to its column."
  [rot-ax-fn getopt cluster coord obj]
  (curver :row 1 :pitch #(- %1 %2) false
          rot-ax-fn getopt cluster coord obj))

(defn- put-in-row
  "Place a key in relation to its row."
  [rot-ax-fn getopt cluster coord obj]
  (let [style (getopt :key-clusters :derived :by-cluster cluster :style)]
   (curver :column 0 :roll #(- %2 %1) (= style :orthographic)
           rot-ax-fn getopt cluster coord obj)))

(declare reckon-feature)

(defn- cluster-origin-finder
  "Compute 3D coordinates for the middle of a key cluster.
  Return a unary function: A partial translator."
  [getopt subject-cluster]
  (let [settings (getopt :key-clusters subject-cluster)
        {:keys [anchor offset] :or {offset [0 0 0]}} (:position settings)
        feature (reckon-feature getopt (resolve-anchor getopt anchor))]
   (partial flex/translate (mapv + feature offset))))

(defn cluster-place
  "Place and tilt passed ‘subject’ as if into a key cluster.
  This uses flex, so the ‘subject’ argument can be a
  single point in 3-dimensional space, typically an offset in mm from the
  middle of the indicated key, or a scad-clj object."
  [getopt cluster coord subject]
  (let [most #(most-specific getopt (concat [:layout] %) cluster coord)
        center (most [:matrix :neutral :row])
        bridge (cluster-origin-finder getopt cluster)]
    (->> subject
         (flex/translate (most [:translation :early]))
         (flex/rotate [(most [:pitch :intrinsic])
                       (most [:roll :intrinsic])
                       (most [:yaw :intrinsic])])
         (put-in-column #(flex/rotate [%1 0 0] %2) getopt cluster coord)
         (put-in-row #(flex/rotate [0 %1 0] %2) getopt cluster coord)
         (flex/translate (most [:translation :mid]))
         (flex/rotate [(most [:pitch :base])
                       (most [:roll :base])
                       (most [:yaw :base])])
         (flex/translate [0 (* capdata/mount-1u center) 0])
         (flex/translate (most [:translation :late]))
         (bridge))))


;; Case walls extending from key mounts.

(defn- wall-segment-offset
  "Compute a 3D offset from one corner of a switch mount to a part of its wall."
  [getopt cluster coord cardinal-direction segment]
  (let [most #(most-specific getopt (concat [:wall] %) cluster coord)
        thickness (most [:thickness])
        bevel-factor (most [:bevel])
        parallel (most [cardinal-direction :parallel])
        perpendicular (most [cardinal-direction :perpendicular])
        {dx :dx dy :dy} (cardinal-direction matrix/compass-to-grid)
        bevel
          (if (zero? perpendicular)
            bevel-factor
            (* bevel-factor
               (/ perpendicular (abs perpendicular))))]
   (case segment
     0 [0 0 0]
     1 [(* dx thickness) (* dy thickness) bevel]
     2 [(* dx parallel) (* dy parallel) perpendicular]
     3 [(* dx (+ parallel thickness))
        (* dy (+ parallel thickness))
        perpendicular]
     4 [(* dx (+ parallel))
        (* dy (+ parallel))
        (+ perpendicular bevel)])))

(defn- wall-vertex-offset
  "Compute a 3D offset from the center of a web post to a vertex on it."
  [getopt directions keyopts]
  (let [xy (/ (getopt :case :key-mount-corner-margin) 2)
        z (/ (getopt :case :key-mount-thickness) 2)]
    (matrix/cube-vertex-offset directions [xy xy z] keyopts)))

(defn wall-corner-offset
  "Combined [x y z] offset from the center of a switch mount.
  By default, this goes to one corner of the hem of the mount’s skirt of
  walling and therefore finds the base of full walls."
  [getopt cluster coordinates
   {:keys [directions segment vertex]
    :or {segment 3, vertex false} :as keyopts}]
  (mapv +
    (if directions
      (mount-corner-offset getopt
        (most-specific getopt [:key-style] cluster coordinates)
        directions)
      [0 0 0])
    (if directions
      (wall-segment-offset
        getopt cluster coordinates (first directions) segment)
      [0 0 0])
    (if (and directions vertex)
      (wall-vertex-offset getopt directions keyopts)
      [0 0 0])))

(defn wall-corner-place
  "Absolute position of the lower wall around a key mount."
  ([getopt cluster coordinates]
   (wall-corner-place getopt cluster coordinates {}))
  ([getopt cluster coordinates keyopts]
   (wall-corner-place getopt cluster coordinates keyopts [0 0 0]))
  ([getopt cluster coordinates keyopts subject]
   (cluster-place getopt cluster coordinates
     (flex/translate
       (wall-corner-offset getopt cluster coordinates keyopts)
       subject))))

(defn wall-slab-center-offset
  "Combined [x y z] offset to the center of a vertical wall.
  Computed as the arithmetic average of its two corners."
  [getopt cluster coordinates direction]
  (letfn [(c [turning-fn]
            (wall-corner-offset getopt cluster coordinates
              {:directions [direction (turning-fn direction)]}))]
    (vec (map / (vec (map + (c matrix/left) (c matrix/right))) [2 2 2]))))

(defn wall-edge-sequence
  "Corner posts for the upper or lower part of the edge of one case wall slab.
  Return a sequence of transformations on the subject, or nil.
  Return nil when no wall is requested (extent zero) and when the lower portion of the wall is requested _and_ the wall in question is not full (i.e. should
  not reach the floor) _and_ the subject is not a coordinate."
  [getopt cluster upper [coord direction turning-fn] subject]
  (let [extent (most-specific getopt [:wall direction :extent] cluster coord)
        last-upper-segment (case extent :full 4, extent)
        place-segment
          (fn [segment]
            (->>
              subject
              (flex/translate
                (wall-corner-offset getopt cluster coord
                  {:directions [direction (turning-fn direction)],
                   :vertex (spec/valid? ::tarmi-core/point-2-3d subject)
                   :segment segment}))
              (cluster-place getopt cluster coord)))]
    (if-not (zero? last-upper-segment)
      (if upper
        ;; The part of the wall above the vertical drop to z = 0.
        (map place-segment (range (inc last-upper-segment)))
        ;; The image from which the vertical drop to z = 0 should be made.
        (cond
          ;; Use all the lower (post-perpendicular) segments for a full wall.
          (= extent :full) (map place-segment [2 3 4])
          ;; Make an exception for coordinate reckoning. This exception is
          ;; useful for drawing the bottom plate beneath the cluster.
          (spec/valid? ::tarmi-core/point-2-3d subject) (map place-segment [last-upper-segment]))))))


;; Central housing.

(defn- chousing-place
  "Place passed shape in relation to a vertex of the central housing."
  [getopt index part side depth subject]
  {:pre [(nat-int? index), (keyword? part), (keyword? depth)]}
  (let [prop (partial getopt :case :central-housing :derived :points part)
        base (case part
               :gabel (prop side depth index)
               :adapter (prop depth index))]
    (flex/translate base subject)))


;; Rear housing.

(defn- rhousing-segment-offset
  "Compute the [x y z] coordinate offset from a rear housing roof corner."
  [getopt cardinal-direction segment]
  (let [cluster (getopt :case :rear-housing :position :cluster)
        key (partial getopt :case :rear-housing :derived)
        wall (partial wall-segment-offset getopt cluster)]
   (case cardinal-direction
     :west (wall (key :west-end-coord) cardinal-direction segment)
     :east (wall (key :east-end-coord) cardinal-direction segment)
     :north (if (= segment 0) [0 0 0] [0 1 -1]))))

(defn rhousing-vertex-offset [getopt directions]
  (let [t (/ (getopt :case :web-thickness) 2)]
    (matrix/cube-vertex-offset directions [t t t] {})))

(defn rhousing-place
  "Place passed shape in relation to a corner of the rear housing’s roof."
  [getopt corner segment subject]
  (let [offset0 (getopt :case :rear-housing :derived
                  (directions-to-unordered-corner corner))
        offset1 (rhousing-segment-offset getopt (first corner) segment)]
    (flex/translate (mapv + offset0 offset1) subject)))


;; Microcontroller.

(declare into-nook)

(defn mcu-place
  "Transform passed shape into the reference frame for an MCU PCB
  This is mostly special treatment of the rear housing, which could be
  obviated by improving its construction along the lines of the central
  housing."
  [getopt subject]
  (let [use-housing (= (getopt :mcu :position :anchor) :rear-housing)
        corner (getopt :mcu :position :corner)
        z (getopt :mcu :derived :pcb :width)
        lateral-shim
          (lateral-offset getopt (second corner)
            (apply +
              (remove nil?
                [(when use-housing
                   1)  ; Compensate for housing wall segment 1 displacement.
                 (when use-housing
                   (/ (getopt :case :rear-housing :wall-thickness) -2))
                 (when use-housing
                   (/ (getopt :mcu :derived :pcb :thickness) -2))
                 (- (getopt :mcu :support :lateral-spacing))])))]
   (->>
     subject
     (flex/translate
       (lateral-offset getopt (second corner)
         (- (getopt :mcu :derived :pcb :connector-overshoot))))
     ;; Face the corner’s main direction, plus arbitrary rotation.
     (flex/rotate
       (mapv +
         (getopt :mcu :position :rotation)
         [0 0 (- (matrix/compass-radians (first corner)))]))
     (flex/translate
       (mapv +
         ;; Move into the requested corner.
         (into-nook getopt :mcu)
         ;; Move away from a supporting wall, if any.
         lateral-shim
         ;; Raise above the floor.
         [0 0 (/ z 2)])))))


;; Wrist rests.

(defn wrist-place
  "Place passed object like the plinth of the wrist rest."
  [getopt obj]
  (->>
    obj
    (flex/rotate [(getopt :wrist-rest :rotation :pitch)
                  (getopt :wrist-rest :rotation :roll)
                  0])
    (flex/translate (conj (getopt :wrist-rest :derived :center-2d)
                          (getopt :wrist-rest :plinth-height)))))

(defn wrist-undo
  "Reverse the rotation aspect of wrist-placement by repeating it in the negative.
  This is intended solely as a convenience to avoid having to rebalance models
  in the slicer."
  [getopt obj]
  (maybe/rotate [(- (getopt :wrist-rest :rotation :pitch))
                 (- (getopt :wrist-rest :rotation :roll))
                 0]
    obj))

(defn- remap-outline
  [getopt base-xy outline-key]
  (let [index (.indexOf (getopt :wrist-rest :derived :outline :base) base-xy)]
    (nth (getopt :wrist-rest :derived :outline outline-key) index)))

(defn- wrist-lip-coord
  [getopt xy outline-key]
  {:post [(spec/valid? ::tarmi-core/point-3d %)]}
  (let [nxy (remap-outline getopt xy outline-key)]
    (wrist-place getopt (conj nxy (getopt :wrist-rest :derived :z1)))))

(defn wrist-segment-coord
  "Take an xy coordinate pair as in the 2D wrist-rest spline outline and a
  segment ID number as for a case wall.
  Return vertex coordinates for the corresponding point on the plastic plinth
  of a wrist rest, in its final position.
  Segments extend outward and downward. Specifically, segment 0 is at
  the top of the lip, segment 1 is at the base of the lip, segment 2 is at
  global floor level, and all other segments are well below floor level to
  ensure that they fall below segment 1 even on a low and tilted rest."
  [getopt xy segment]
  {:pre [(vector? xy), (integer? segment)]
   :post [(spec/valid? ::tarmi-core/point-3d %)]}
  (case segment
    0 (wrist-place getopt (conj xy (getopt :wrist-rest :derived :z2)))
    1 (wrist-lip-coord getopt xy :lip)
    ; By default, recurse and override the z coordinate of segment 1.
    (assoc (wrist-segment-coord getopt xy 1) 2 (if (= segment 2) 0.0 -100.0))))

(defn wrist-segment-naive
  "Use wrist-segment-coord with a layer of translation from the naïve/relative
  coordinates initially supplied by the user to the derived base.
  Also support outline keys as an alternative to segment IDs, for bottom-plate
  fasteners."
  [getopt naive-xy outline-key segment]
  (let [translator (getopt :wrist-rest :derived :relative-to-base-fn)
        aware-xy (translator naive-xy)]
    (if (some? outline-key)
      (wrist-lip-coord getopt aware-xy outline-key)
      (wrist-segment-coord getopt aware-xy segment))))

(defn wrist-block-place
  "Place a block for a wrist-rest mount."
  ;; TODO: Rework the block model to provide meaningful support for corner and
  ;; segment. Those parameters are currently ignored.
  [getopt mount-index side-key corner segment obj]
  {:pre [(integer? mount-index) (keyword? side-key)]}
  (let [prop (partial getopt :wrist-rest :mounts mount-index :derived)]
    (->>
      obj
      (flex/rotate [0 0 (prop :angle)])
      (flex/translate (prop side-key)))))


;; Polymorphic treatment of the properties of aliases.
;; The by-type multimethod dispatches placement of features in relation to
;; other features, on the basis of properties associated with each alias,
;; starting with its :type.

(defmulti by-type (fn [_ {:keys [type]}] type))

(defmethod by-type :origin
  [_ {:keys [initial]}]
  initial)

(defmethod by-type :central-housing
  [getopt {:keys [index initial part side depth] :or {depth :outer}}]
  (chousing-place getopt index part side depth initial))

(defmethod by-type :rear-housing
  [getopt {:keys [corner segment initial] :or {segment 3}}]
  (rhousing-place getopt corner segment initial))

(defmethod by-type :wr-perimeter
  [getopt {:keys [coordinates outline-key segment initial] :or {segment 3}}]
  (flex/translate
    (wrist-segment-naive getopt coordinates outline-key segment)
    initial))

(defmethod by-type :wr-block
  [getopt {:keys [mount-index side-key corner segment initial]
           :or {segment 3}}]
  (wrist-block-place getopt mount-index side-key corner segment initial))

(defmethod by-type :key
  [getopt {:keys [cluster coordinates corner segment initial]
           :or {segment 3} :as opts}]
  (cluster-place getopt cluster coordinates
    (if (some? corner)
      ;; Corner named. By default, the target feature is the outermost wall.
      (flex/translate
        (wall-corner-offset getopt cluster coordinates
          (merge opts {:directions corner :segment segment}))
        initial)
      ;; Else no corner named.
      ;; The target feature is the middle of the key mounting plate.
      initial)))

(defmethod by-type :mcu-grip
  [getopt {:keys [corner offset initial]}]
  (mcu-place getopt
    (flex/translate (mapv + (getopt :mcu :derived :pcb corner) offset)
      initial)))

(defmethod by-type :secondary
  [getopt {:keys [anchor offset] :or {offset [0 0 0]} :as opts}]
  (let [primary (resolve-anchor getopt anchor)
        clean (dissoc opts :type :anchor :alias :offset)]
    (flex/translate
      (mapv + (get primary :offset [0 0 0]) offset)
      (reckon-feature getopt (merge clean (dissoc primary :offset))))))

;; Generalizations.

(defn- reckon-feature
  "A convenience for placing stuff in relation to other features.
  Differents parts of a feature can be targeted with keyword parameters.
  Return a scad-clj node or, by default, a vector of three numbers.
  Generally, the vector refers to what would be the middle of the outer wall
  of a feature. For keys, rear housing and wrist-rest mount blocks, this
  is the middle of a wall post. For central housing and the perimeter of the
  wrist rest, it’s a vertex on the surface.
  Any offset passed to this function will be interpreted in the native context
  of each feature placement function, with varying results."
  [getopt {:keys [offset subject] :or {offset [0 0 0], subject [0 0 0]}
           :as opts}]
  (by-type getopt (assoc opts :initial (flex/translate offset subject))))

(defn reckon-from-anchor
  "Find a position corresponding to a named point."
  [getopt anchor extras]
  {:pre [(keyword? anchor) (map? extras)]}
  (reckon-feature getopt (merge extras (resolve-anchor getopt anchor))))

(defn reckon-with-anchor
  "Produce coordinates for a specific feature using a single map that names
  an anchor."
  [getopt {:keys [anchor] :as opts}]
  {:pre [(keyword? anchor)]}
  (reckon-from-anchor getopt anchor opts))

(defn offset-from-anchor
  "Apply an offset from a user configuration to the output of
  reckon-with-anchor, instead of passing it as an input.
  The results are typically more predictable than passing the offset to
  reckon-with-anchor, being simple addition at a late stage.
  This function also supports explicit 2-dimensional inputs and outputs."
  [getopt opts dimensions]
  {:pre [(integer? dimensions)]}
  (let [base-3d (reckon-with-anchor getopt (dissoc opts :offset))
        base-nd (subvec base-3d 0 dimensions)
        offset-nd (get opts :offset (take dimensions (repeat 0)))]
    (mapv + base-nd offset-nd)))

(defn into-nook
  "Produce coordinates for alignment with an anchor.
  This has strict expectations and provides some special treatment for the
  rear housing."
  [getopt field]
  (let [anchor (getopt field :position :anchor)
        corner (getopt field :position :corner)
        general (reckon-from-anchor getopt anchor {:corner corner})
        to-nook
          (if (= anchor :rear-housing)
            ;; Pull the subject from the middle of the rear-housing wall
            ;; to perfect alignment with the outside of that wall.
            (lateral-offset getopt (first corner)
              (/ (getopt :case :rear-housing :wall-thickness) 2))
            ;; Else don’t bother.
            [0 0 0])
        offset (getopt field :position :offset)]
    (mapv + (misc/z0 general) to-nook offset)))

(defn wrist-module-placer
  "Produce a function that places a named module in relation to a named inset
  from the outline of the wrist rest."
  [getopt outline-key module-name]
  (fn [configuration]
    (model/translate
      (misc/z0 (offset-from-anchor getopt
                 (assoc configuration :outline-key outline-key)
                 2))
      (model/call-module module-name))))
