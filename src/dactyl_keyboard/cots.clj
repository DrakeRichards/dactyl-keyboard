;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; The Dactyl-ManuForm Keyboard — Opposable Thumb Edition              ;;
;; Commercial Off-the-Shelf Parts (COTS) Specifications                ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;; This module collects data about the form factor of third-party designs to
;;; support their use as keyboard parts.

(ns dactyl-keyboard.cots
  (:require [clojure.string :refer [join]]))

(def port-facts
  "Form factors of the main bodies of supported common connectors.

  All described connectors here are receptacles (a.k.a. “female”).
  Connections occur depthwise in the local nomenclature.

  The data is based on common specs. Individual designs may vary, particularly
  for USB B which normally has large housings even for single connectors, and
  most especially for height between versions intended for mounting in
  different orientations.

  Dimensions given here generally describe the main body of a surface- or
  through-hole-mounted connector, excluding all pins, grippers, flared faces
  and other such details, but including plastic “rails” for isolation from a
  PCB, since the height added by such rails tends to be included in the metal
  housing of alternate versions standing on end."
  {:usb-full-a {:width 10 :depth 13.6 :height 6.5
                :description "full-size USB A"}
   :usb-full-2b {:width 12.1 :depth 16.1 :height 11
                 :description "full-size USB 2 B"}
   :usb-full-3b {:width 12 :depth 18.3 :height 12.9
                 :description "full-size USB 3 B"}
   :usb-mini-b {:width 7.56 :depth 9.27 :height 4
                :description "USB mini B"}
   :usb-micro-2b {:width 7.5 :depth 5.9 :height 2.55
                  :description "USB micro 2 B"}
   :usb-c {:width 9.2 :depth 10.5 :height 3.28
           :description "USB C"}
   :modular-4p4c-616e {:width 10 :depth 11 :height 17.7
                       :description (str "modular connector 4P4C, socket "
                                         "616E, minus the vertical stripe")}})

(def mcu-facts
  "Form factors etc. of printed circuit board assemblies (PCBAs) for
  common microcontroller units (MCUs)."
  ;; Starting with global default dimensions.
  {::default {:width 17.78 :thickness 1.57}
   ;; PJRC (Paul J Stoffregen and Robin C Coon) products:
   :teensy-s {:length 30  ; Rough guess.
              :port-type :usb-mini-b
              :port-overshoot 0.5  ; Rough guess.
              :description "Teensy 2.0"}
   :teensy-m {:length 35.56
              :port-type :usb-micro-2b
              :port-overshoot 0.7
              :description "Medium-size Teensy, 3.2 or LC"}
   :teensy-l {:length 53  ; Rough guess.
              :port-type :usb-mini-b
              :port-overshoot 0.5  ; Rough guess.
              :description "Teensy++ 2.0"}
   :teensy-xl {:length 61  ; Rough guess.
               :port-type :usb-micro-2b
               :port-overshoot 1.3  ; Rough guess.
               :description "Extra large Teensy, 3.5 or 3.6"}
   ;; SparkFun products:
   :promicro {:length 33
              :port-type :usb-micro-2b
              :port-overshoot 1.9
              :description "Pro Micro"}
   ;; That-Canadian products:
   :elite-c {:length 33
             :thickness 1
             :port-type :usb-c
             :port-overshoot 1.9  ; Rough guess.
             :description "Elite-C"}
   ;; QMK products:
   :proton-c {:length 52.9  ; About 34.6 mm snapped off.
              :port-type :usb-c
              :port-overshoot 0.75
              :description "Proton C"}})

(def switch-facts
  "Form factors of switches for the purpose of cutting holes into key mounting
  plates. The dmote-keycap library models switches as such in more detail
  for the contrasting purpose of modeling caps."
  {:alps {:hole {:x 15.5,  :y 12.6}
          :foot {:x 17.25, :y 14.25}
          :height {:above-plate 7.6, :into-plate 4.5}
          :description "ALPS-style, including Matias"}
   :mx   {:hole {:x 14,    :y 14}
          :foot {:x 15.5,  :y 15.5}
          :height {:above-plate 5, :into-plate 5}
          :description "Cherry MX style"}})

(defn support-list
  [coll]
  "Return a Markdown string describing passed collection,
  which must be like one of those in this module."
  (join "\n"
    (map (fn [[k {d :description}]] (format "* `%s`: %s." (name k) d))
         (sort (dissoc coll ::default)))))
