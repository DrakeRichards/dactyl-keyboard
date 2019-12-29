;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; The Dactyl-ManuForm Keyboard — Opposable Thumb Edition              ;;
;; Microcontrollers                                                    ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(ns dactyl-keyboard.cad.mcu
  (:require [scad-clj.model :as model]
            [scad-tarmi.core :refer [π]]
            [scad-tarmi.maybe :as maybe]
            [scad-tarmi.threaded :as threaded]
            [scad-tarmi.util :refer [loft]]
            [dactyl-keyboard.compass :as compass]
            [dactyl-keyboard.misc :refer [colours]]
            [dactyl-keyboard.cad.misc :as misc]
            [dactyl-keyboard.cad.place :as place]))


;;;;;;;;;;;;
;; Models ;;
;;;;;;;;;;;;


(def usb-a-female-dimensions
  "This assumes the flat orientation common in laptops.
  In a DMOTE, USB connector width would typically go on the z axis, etc."
  {:full {:width 10.0 :length 13.6 :height 6.5}
   :micro {:width 7.5 :length 5.9 :height 2.55}})

(defn- descriptor-vec
  [{:keys [width length thickness height]}]
  [(or width 1) (or length 1) (or thickness height 1)])

(defn derive-properties
  "Derive secondary properties of the MCU."
  [getopt]
  (let [mcu-type (getopt :mcu :type)
        pcb-base {:thickness 1.57 :connector-overshoot 1.9}
        pcb-model (case mcu-type
                    :promicro {:width 18 :length 33}
                    :teensy {:width 17.78 :length 35.56}
                    :teensy++ {:width 17.78 :length 53})
        [x y z] (descriptor-vec (merge pcb-base pcb-model))
        sw [(/ x -2) (- y) 0]
        pcb-corners {:NW (mapv + sw [0 y 0])
                     :NE (mapv + sw [x y 0])
                     :SE (mapv + sw [x 0 0])
                     :SW sw}
        plate-transition (- (+ (getopt :mcu :support :lock :plate :clearance)
                               (/ z 2)))]
   {:include-centrally (and (getopt :mcu :include)
                            (getopt :mcu :position :central))
    :include-laterally (and (getopt :mcu :include)
                            (not (and (getopt :reflect)
                                      (getopt :case :central-housing :include)
                                      (getopt :mcu :position :central))))
    ;; Add [x y z] coordinates of the four corners of the PCB. No DFM.
    :pcb (merge pcb-base pcb-model pcb-corners)
    :connector (:micro usb-a-female-dimensions)
    :lock-width (* (getopt :mcu :support :lock :width-factor) x)}))

(defn collect-grip-aliases
  "Collect the names of MCU grip anchors. Expand 2D offsets to 3D."
  [getopt]
  (reduce
    (fn [coll {:keys [corner offset alias] :or {offset [0 0]}}]
      (assoc coll alias
        {:type :mcu-grip,
         :corner (compass/directions-to-unordered-corner corner),
         :offset (subvec (conj offset 0) 0 3)}))
    {}
    (getopt :mcu :support :grip :anchors)))

(defn pcba-model
  "A model of an MCU: PCB and integrated USB connector (if any). The
  orientation of the model is flat with the connector on top, facing “north”.
  The middle of that short edge of the PCB centers at the origin of the local
  cordinate system."
  [getopt include-margin connector-elongation]
  (let [prop (partial getopt :mcu :derived)
        overshoot (prop :pcb :connector-overshoot)
        [pcb-x pcb-y pcb-z] (descriptor-vec (prop :pcb))
        [usb-x usb-y-base usb-z] (descriptor-vec (prop :connector))
        usb-y (+ usb-y-base connector-elongation)
        margin (if include-margin (getopt :dfm :error-general) 0)
        mcube (fn [& dimensions] (apply model/cube (map #(- % margin) dimensions)))]
    (model/union
      (model/translate [0 (/ pcb-y -2) 0]
        (model/color (:pcb colours)
          (mcube pcb-x pcb-y pcb-z)))
      (model/translate [0
                        (+ (/ usb-y -2) (/ connector-elongation 2) overshoot)
                        (+ (/ pcb-z 2) (/ usb-z 2))]
        (model/color (:metal colours)
          (mcube usb-x usb-y usb-z))))))

(defn pcba-visualization [getopt]
  (place/mcu-place getopt (pcba-model getopt false 0)))

(defn pcba-negative [getopt]
  (place/mcu-place getopt (pcba-model getopt true 10)))

(defn alcove
  "A blocky shape at the connector end of the MCU.
  For use as a complement to pcba-negative.
  This is provided because a negative of the MCU model itself digging into the
  inside of a wall would create only a narrow notch, which would require
  high printing accuracy or difficult cleanup."
  [getopt]
  (let [prop (partial getopt :mcu :derived)
        [pcb-x _ pcb-z] (descriptor-vec (prop :pcb))
        usb-z (prop :connector :height)
        error (getopt :dfm :error-general)
        x (- pcb-x error)]
    (place/mcu-place getopt
      (model/hull
        (model/translate [0 (/ x -2) 0]
          (model/cube x x (- pcb-z error)))
        (model/translate [0 (/ x -2) (/ (+ pcb-z usb-z) 2)]
          (model/cube (dec x) x (- usb-z error)))))))

(defn lock-plate-base
  "The model of the plate upon which an MCU PCBA rests in a lock.
  This is intended for use in the lock model itself (complete)
  and in tweaks (base only, not complete)."
  [getopt complete]
  (let [[_ pcb-y pcb-z] (descriptor-vec (getopt :mcu :derived :pcb))
        plate-y (+ pcb-y (getopt :mcu :support :lock :bolt :mount-length))
        clearance (getopt :mcu :support :lock :plate :clearance)
        base-thickness (getopt :mcu :support :lock :plate :base-thickness)
        full-z (+ (/ pcb-z 2) clearance base-thickness)]
    (model/translate [0
                      (/ plate-y -2)
                      (+ (- full-z) (/ (if complete full-z base-thickness) 2))]
      (model/cube (getopt :mcu :derived :lock-width)
                  plate-y
                  (if complete full-z base-thickness)))))

(defn lock-fixture-positive
  "Parts of the lock-style MCU support that integrate with the case.
  These comprise a plate for the bare side of the PCB to lay against and a socket
  that encloses the USB connector on the MCU to stabilize it, since integrated
  USB connectors are usually surface-mounted and therefore fragile."
  [getopt]
  (let [prop (partial getopt :mcu :derived)
        [_ pcb-y pcb-z] (descriptor-vec (prop :pcb))
        [usb-x usb-y usb-z] (descriptor-vec (prop :connector))
        plate-z (getopt :mcu :support :lock :plate :clearance)
        plate-y (+ pcb-y (getopt :mcu :support :lock :bolt :mount-length))
        thickness (getopt :mcu :support :lock :socket :thickness)
        socket-z-thickness (+ (/ usb-z 2) thickness)
        socket-z-offset (+ (/ pcb-z 2) (* 3/4 usb-z) (/ thickness 2))
        socket-x (+ usb-x (* 2 thickness))]
   (place/mcu-place getopt
     (model/union
       (lock-plate-base getopt true)
       ;; The socket:
       (model/hull
         ;; Purposely ignore connector overshoot in placing the socket.
         ;; This has the advantages that the lock itself can also be stabilized
         ;; by the socket, while the socket does not protrude outside the case.
         (model/translate [0 (/ usb-y -2) socket-z-offset]
           (model/cube socket-x usb-y socket-z-thickness))
         ;; Stabilizers for the socket:
         (model/translate [0 0 10]
           (model/cube socket-x 1 1))
         (model/translate [0 0 socket-z-offset]
           (model/cube (+ socket-x 6) 1 1)))))))

(defn lock-fasteners-model
  "Negative space for a bolt threading into an MCU lock."
  [getopt]
  (let [head-type (getopt :mcu :support :lock :fastener :style)
        d (getopt :mcu :support :lock :fastener :diameter)
        l0 (threaded/head-height d head-type)
        l1 (if (= (getopt :mcu :position :anchor) :rear-housing)
             (getopt :case :rear-housing :wall-thickness)
             (getopt :case :web-thickness))
        [_ pcb-y pcb-z] (descriptor-vec (getopt :mcu :derived :pcb))
        l2 (getopt :mcu :support :lock :plate :clearance)
        y1 (getopt :mcu :support :lock :bolt :mount-length)]
    (->>
      (threaded/bolt
          :iso-size d
          :head-type head-type
          :unthreaded-length (max 0 (- (+ l1 l2) l0))
          :threaded-length (getopt :mcu :support :lock :bolt :mount-thickness)
          :negative true)
      (model/rotate [π 0 0])
      (model/translate [0 (- (+ pcb-y (/ y1 2))) (- (+ (/ pcb-z 2) l1 l2))]))))

(defn lock-sink [getopt]
  (place/mcu-place getopt
    (lock-fasteners-model getopt)))

(defn lock-bolt-model
  "Parts of the lock-style MCU support that don’t integrate with the case.
  The bolt as such is supposed to clear PCB components and enter the socket to
  butt up against the USB connector. There are some margins here, intended for
  the user to file down the tip and finalize the fit."
  [getopt]
  (let [prop (partial getopt :mcu :derived)
        usb-overshoot (prop :pcb :connector-overshoot)
        [_ pcb-y pcb-z] (descriptor-vec (prop :pcb))
        [usb-x usb-y usb-z] (descriptor-vec (prop :connector))
        mount-z (getopt :mcu :support :lock :bolt :mount-thickness)
        mount-overshoot (getopt :mcu :support :lock :bolt :overshoot)
        mount-y-base (getopt :mcu :support :lock :bolt :mount-length)
        clearance (getopt :mcu :support :lock :bolt :clearance)
        shave (/ clearance 2)
        contact-z (- usb-z shave)
        bolt-z-mount (- mount-z clearance pcb-z)
        mount-x (getopt :mcu :derived :lock-width)
        bolt-z0 (+ (/ pcb-z 2) clearance (/ bolt-z-mount 2))
        bolt-z1 (+ (/ pcb-z 2) shave (/ contact-z 2))]
   (model/difference
     (model/union
       (model/translate [0
                         (- (/ mount-overshoot 2) pcb-y (/ mount-y-base 2))
                         (+ (/ pcb-z -2) (/ mount-z 2))]
         (model/cube mount-x (+ mount-overshoot mount-y-base) mount-z))
       (loft
         [(model/translate [0 (- pcb-y) bolt-z0]
            (model/cube usb-x 10 bolt-z-mount))
          (model/translate [0 (/ pcb-y -4) bolt-z0]
            (model/cube usb-x 1 bolt-z-mount))
          (model/translate [0 (- usb-overshoot usb-y) bolt-z1]
            (model/cube usb-x misc/wafer contact-z))]))
     (pcba-model getopt true 0)  ; Notch the mount.
     (lock-fasteners-model getopt))))

(defn lock-bolt-locked [getopt]
  (place/mcu-place getopt (lock-bolt-model getopt)))

(defn negative-composite [getopt]
  (model/union
    (pcba-negative getopt)
    (alcove getopt)
    (when (getopt :mcu :support :lock :include)
      (lock-sink getopt))))

(defn lock-fixture-composite
  "MCU support features outside the alcove."
  [getopt]
  (model/difference
    (lock-fixture-positive getopt)
    (lock-bolt-locked getopt)
    (pcba-negative getopt)
    (lock-sink getopt)))

(defn preview-composite
  [getopt]
  (maybe/union
    (pcba-visualization getopt)
    (when (getopt :mcu :support :lock :include)
      (lock-bolt-locked getopt))))

