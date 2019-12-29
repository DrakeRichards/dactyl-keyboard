;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; The Dactyl-ManuForm Keyboard — Opposable Thumb Edition              ;;
;; Key Utilities — Switches and Keycaps                                ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(ns dactyl-keyboard.cad.key
  (:require [clojure.java.io :as io]
            [scad-clj.model :as model]
            [scad-tarmi.core :refer [abs π]]
            [scad-tarmi.maybe :as maybe]
            [scad-tarmi.util :refer [loft]]
            [dmote-keycap.data :as capdata]
            [dmote-keycap.measure :as measure]
            [dmote-keycap.models :refer [keycap]]
            [dactyl-keyboard.generics :as generics]
            [dactyl-keyboard.cad.matrix :as matrix]
            [dactyl-keyboard.cad.place :as place]
            [dactyl-keyboard.param.access :refer [most-specific
                                                  key-properties]]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Core Definitions — All Switches ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def switch-properties
  {:alps {:hole {:x 15.5,  :y 12.6}
          :foot {:x 17.25, :y 14.25}}
   :mx   {:hole {:x 14,    :y 14}
          :foot {:x 15.5,  :y 15.5}}})

(defn all-clusters
  "The identifiers of all defined key clusters."
  [getopt]
  (remove #(= :derived %) (keys (getopt :key-clusters))))

(defn- derived
  "A shortcut to look up a cluster-specific derived configuration detail."
  [getopt & keys]
  (apply (partial getopt :key-clusters :derived :by-cluster) keys))

(defn resolve-flex
  "Resolve supported keywords in a coordinate pair to names.
  This allows for integers as well as the keywords :first and :last, meaning
  first and last in the column or row. Columns have priority."
  [getopt cluster [c0 r0]]
  (let [columns (derived getopt cluster :column-range)
        c1 (case c0 :first (first columns) :last (last columns) c0)
        rows (derived getopt cluster :row-indices-by-column c1)]
   [c1 (case r0 :first (first rows) :last (last rows) r0)]))

(defn match-flex
  "Check whether coordinate pairs are the same, with keyword support."
  [getopt cluster & coords]
  (apply = (map (partial resolve-flex getopt cluster) coords)))

(defn chart-cluster
  "Derive some properties about a key cluster from raw configuration info."
  [cluster getopt]
  (let [raws (getopt :key-clusters cluster)
        matrix (getopt :key-clusters cluster :matrix-columns)
        gather (fn [key default] (map #(get % key default) matrix))
        column-range (range 0 (count matrix))
        last-column (last column-range)
        max-rows-above-home (apply max (gather :rows-above-home 0))
        max-rows-below-home (apply max (gather :rows-below-home 0))
        row-range (range (- max-rows-below-home) (+ max-rows-above-home 1))
        key-requested?
          (fn [[column row]]
            "True if specified key is requested."
            (if-let [data (nth matrix column nil)]
              (cond
                (< row 0) (>= (get data :rows-below-home 0) (abs row))
                (> row 0) (>= (get data :rows-above-home 0) row)
                :else true)  ; Home row.
              false))  ; Column not in matrix.
        key-coordinates (matrix/coordinate-pairs
                          column-range row-range key-requested?)
        M (fn [f coll] (into {} (map f coll)))
        row-indices-by-column
          (M (fn [c] [c (filter #(key-requested? [c %]) row-range)])
             column-range)
        coordinates-by-column
          (M (fn [[c rows]] [c (for [r rows] [c r])]) row-indices-by-column)
        column-indices-by-row
          (M
            (fn [r] [r (filter #(key-requested? [% r]) column-range)])
            row-range)
        coordinates-by-row
          (M (fn [[r cols]] [r (for [c cols] [c r])]) column-indices-by-row)]
   {:style (:style raws :standard)
    :last-column last-column
    :column-range column-range
    :row-range row-range
    :key-requested? key-requested?
    :key-coordinates key-coordinates
    :row-indices-by-column row-indices-by-column
    :coordinates-by-column coordinates-by-column
    :column-indices-by-row column-indices-by-row
    :coordinates-by-row coordinates-by-row}))

(defn derive-style-properties
  "Derive properties for each key style.
  These properties include DFM settings from other sections of the
  configuration, used here with their dmote-keycap names, and strings for
  OpenSCAD module names."
  [getopt]
  (reduce
    (fn [coll [style-key explicit]]
      (let [safe-get #(get explicit %1 (%1 capdata/option-defaults))
            switch-type (safe-get :SWitch-type)]
        (assoc coll style-key
          (merge
            capdata/option-defaults
            {:importable-filepath-fn
               #(str (io/file generics/output-directory "scad" %))
             :module-keycap (str "keycap_" (generics/key-to-scadstr style-key))
             :module-switch (str "switch_" (generics/key-to-scadstr switch-type))
             :skirt-length (measure/default-skirt-length switch-type)
             :vertical-offset (measure/plate-to-stem-end switch-type)
             :error-stem-positive (getopt :dfm :keycaps :error-stem-positive)
             :error-stem-negative (getopt :dfm :keycaps :error-stem-negative)
             :error-body-positive (getopt :dfm :error-general)}
            explicit))))
    {}
    (getopt :keys :styles)))

(defn derive-cluster-properties
  "Derive basic properties for each key cluster."
  [getopt]
  (let [by-cluster (fn [coll key] (assoc coll key (chart-cluster key getopt)))]
   {:by-cluster (reduce by-cluster {} (all-clusters getopt))}))

(defn collect-key-aliases
  "Unify cluster-specific key aliases into a single global map that preserves
  their cluster of origin and resolves symbolic coordinates to absolute values."
  [getopt]
  (into {}
    (mapcat
      (fn [cluster]
        (into {}
          (map
            (fn [[alias flex]]
              [alias {:type :key
                      :cluster cluster
                      :coordinates (resolve-flex getopt cluster flex)}]))
          (getopt :key-clusters cluster :aliases)))
      (all-clusters getopt))))

(defn print-matrix
  "Print a schematic picture of a key cluster. For your REPL."
  [cluster getopt]
  (let [prop (partial derived getopt cluster)]
    (doseq [row (reverse (prop :row-range)) column (prop :column-range)]
      (if ((prop :key-requested?) [column row]) (print "□") (print "·"))
      (if (= column (prop :last-column)) (println)))))


;;;;;;;;;;;;;;;;;;;
;; Keycap Models ;;
;;;;;;;;;;;;;;;;;;;

(defn cap-channel-negative
  "The shape of a channel for a keycap to move in."
  [getopt cluster coord {h3 :height wd3 :top-width m :margin}]
  (let [cmp (getopt :dfm :derived :compensator)
        t 1
        step (fn [x y h]
               (model/translate [0 0 h] (model/cube (cmp x) (cmp y) t)))
        prop (key-properties getopt cluster coord)
        {:keys [switch-type skirt-length]} prop
        ;; The size of the switch with overhangs is not to be confused with
        ;; measure/switch-footprint.
        {sx :x, sy :y} (get-in switch-properties [switch-type :foot])
        [wx wy] (measure/skirt-footprint prop)
        h1 (measure/pressed-clearance switch-type skirt-length)
        h2 (measure/resting-clearance switch-type skirt-length)]
    (model/color (:cap-negative generics/colours)
      (model/translate [0 0 (getopt :case :key-mount-thickness)]
        (loft
          [(step (+ sx m) (+ sy m) (/ t 2)) ; A bottom plate for ease of mounting a switch.
           (step (+ sx m) (+ sy m) 1) ; Roughly the height of the foot of the switch.
           (step wx wy h1) ; Space for the keycap’s edges in travel.
           (step wx wy h2)
           (step wd3 wd3 h3)]))))) ; Space for the upper body of a keycap at rest.

(defn single-cap
  "The shape of one keycap at rest on its switch. This is intended for use in
  defining an OpenSCAD module that needs no further input.
  The ‘supported’ argument acts as a fallback in case the user has not
  specified the dmote-keycap parameter by that name. Supports are generally
  appropriate for printable STLs but not for cluster previews."
  [getopt key-style supported]
  (->>
    (merge {:supported supported} (getopt :keys :derived key-style))
    keycap
    (model/translate
      [0 0 (+ (getopt :case :key-mount-thickness)
              (getopt :keys :derived key-style :vertical-offset))])
    (model/color (:cap-body generics/colours))))

(defn cap-positive
  "Recall of the results of single-cap for a particular coordinate."
  [getopt cluster coord]
  (model/call-module (:module-keycap (key-properties getopt cluster coord))))


;;;;;;;;;;;;;;;;;;;;;;
;; Keyswitch Models ;;
;;;;;;;;;;;;;;;;;;;;;;

;; These models are intended solely for use as cutouts and therefore lack
;; features that would not interact with key mounts.

(defn- plate-cutout-height
  [getopt switch-height-to-plate-top]
  (- (* 2 switch-height-to-plate-top) (getopt :case :key-mount-thickness)))

(defn alps-switch
  "One ALPS-compatible cutout model."
  [getopt]
  (let [thickness (getopt :case :key-mount-thickness)
        {hole-x :x, hole-y :y} (get-in switch-properties [:alps :hole])
        height-to-plate-top 4.5
        {foot-x :x, foot-y :y} (get-in switch-properties [:alps :foot])]
    (model/union
      ;; Space for the part of a switch above the mounting hole.
      ;; The actual height of the notches is 1 mm and it’s not a full cuboid.
      (model/translate [0 0 (/ thickness 2)]
        (model/cube foot-x foot-y thickness))
      ;; The hole through the plate.
      (model/translate [0 0 (/ height-to-plate-top -2)]
        (model/cube hole-x hole-y (plate-cutout-height getopt height-to-plate-top)))
      ;; ALPS-specific space for wings to flare out inside the plate.
      (model/translate [0 0 (- (/ height-to-plate-top -2) 0.6)]
        (model/difference
          (model/cube (inc hole-x) hole-y height-to-plate-top)
          (model/cube (inc hole-x) 7.5 height-to-plate-top))))))

(defn mx-switch
  "One MX Cherry-compatible cutout model. Square."
  [getopt]
  (let [thickness (getopt :case :key-mount-thickness)
        hole-xy (get-in switch-properties [:mx :hole :x])
        foot-xy (get-in switch-properties [:mx :foot :x])
        height-to-plate-top 5.004
        nub-radius 1
        nub-depth 4
        nub (->> (model/cylinder nub-radius 2.75)
                 (model/with-fn 20)
                 (model/rotate [(/ π 2) 0 0])
                 (model/translate [(/ hole-xy 2) 0 (- nub-radius nub-depth)])
                 (model/hull
                   (model/translate [(+ 3/4 (/ hole-xy 2)) 0 (/ nub-depth -2)]
                     (model/cube 1.5 2.75 nub-depth))))]
    (model/difference
      (model/union
        ;; Space for the part of a switch above the mounting hole.
        (model/translate [0 0 (/ thickness 2)]
          (model/cube foot-xy foot-xy thickness))
        ;; The hole through the plate.
        (model/translate [0 0 (/ height-to-plate-top -2)]
          (model/cube hole-xy hole-xy
            (plate-cutout-height getopt height-to-plate-top))))
      ;; MX-specific nubs that hold the keyswitch in place.
      (model/union
        nub
        (model/mirror [0 1 0] (model/mirror [1 0 0] nub))))))

(defn single-switch
  "Negative space for the insertion of a key switch through a mounting plate."
  [getopt switch-type]
  (case switch-type
    :alps (alps-switch getopt)
    :mx (mx-switch getopt)))


;;;;;;;;;;;;;;;;;;
;; Other Models ;;
;;;;;;;;;;;;;;;;;;

(defn- single-plate
  "The shape of a key mounting plate."
  [getopt key-style]
  (let [thickness (getopt :case :key-mount-thickness)
        style-data (getopt :keys :derived key-style)
        [x y] (map measure/key-length (get style-data :unit-size [1 1]))]
   (model/translate [0 0 (/ thickness -2)]
     (model/cube x y thickness))))

(defn web-post
  "A shape for attaching things to a corner of a switch mount."
  [getopt]
  (model/cube (getopt :case :key-mount-corner-margin)
              (getopt :case :key-mount-corner-margin)
              (getopt :case :web-thickness)))

(defn mount-corner-post
  "A post shape that comes offset for one corner of a key mount."
  [getopt key-style directions]
  (->> (web-post getopt)
       (model/translate (place/mount-corner-offset getopt key-style directions))))


;;;;;;;;;;;;;;;;;;;;;;;;;
;; Interface Functions ;;
;;;;;;;;;;;;;;;;;;;;;;;;;

(defn cluster-plates [getopt cluster]
  (apply model/union
    (map #(place/cluster-place getopt cluster %
            (single-plate getopt
              (most-specific getopt [:key-style] cluster %)))
         (derived getopt cluster :key-coordinates))))

(defn cluster-cutouts [getopt cluster]
  (apply model/union
     (map #(place/cluster-place getopt cluster %1
             (model/call-module
               (:module-switch (key-properties getopt cluster %1))))
          (derived getopt cluster :key-coordinates))))

(defn cluster-channels [getopt cluster]
  (letfn [(modeller [coord]
            (letfn [(most [path]
                      (most-specific getopt path cluster coord))]
              (cap-channel-negative getopt cluster coord
                {:top-width (most [:channel :top-width])
                 :height (most [:channel :height])
                 :margin (most [:channel :margin])})))]
    (apply model/union
      (map #(place/cluster-place getopt cluster % (modeller %))
           (derived getopt cluster :key-coordinates)))))

(defn cluster-keycaps [getopt cluster]
  (apply model/union
    (map #(place/cluster-place getopt cluster %
            (cap-positive getopt cluster %))
         (derived getopt cluster :key-coordinates))))

(defn metacluster
  "Apply passed modelling function to all key clusters."
  [function getopt]
  (apply maybe/union (map #(function getopt %) (all-clusters getopt))))
