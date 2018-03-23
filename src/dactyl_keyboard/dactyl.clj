;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; The Dactyl-ManuForm Keyboard — Opposable Thumb Edition              ;;
;; Main Module — Final Composition and Outputs                         ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(ns dactyl-keyboard.dactyl
  (:require [scad-clj.scad :refer [write-scad]]
            [scad-clj.model :exclude [use import] :refer :all]
            [dactyl-keyboard.params :refer :all]
            [dactyl-keyboard.tweaks :refer [key-cluster-bridge
                                            key-cluster-bridge-cutouts
                                            finger-case-tweaks]]
            [dactyl-keyboard.cad.key :refer :all]
            [dactyl-keyboard.cad.case :refer :all]
            [dactyl-keyboard.cad.aux :refer :all]))

(defn new-scad []
  "Reload this namespace with any changed dependencies. Redraw .scad files."
  (clojure.core/use 'dactyl-keyboard.dactyl :reload-all))

(defn print-finger-matrix []
  "Print a picture of the finger layout. No thumb keys. For your REPL."
  (for [row (reverse all-finger-rows)
        column all-finger-columns]
    (do (if (finger? [column row]) (print "□") (print "·"))
        (if (= column last-finger-column) (println)))))

(def wrist-rest-dual-view
  (union
    (wrist-rest-right false)
    (translate [-100 0 0] (wrist-rest-right true))))

(def keyboard-right
  "Right-hand-side keyboard model."
  (union
    (difference
      (union
        (difference case-walls-for-the-fingers rj9-space)
        (if include-wrist-rest
          (case wrist-rest-style
            :solid case-wrist-hook
            :threaded case-wrist-plate))
        case-walls-for-the-thumbs
        key-cluster-bridge
        finger-case-tweaks
        mcu-support
        finger-plates
        finger-web
        thumb-plates
        thumb-web
        rj9-holder
        (if include-feet foot-plates)
        (if include-backplate-block backplate-block))
      key-cluster-bridge-cutouts
      mcu-negative
      finger-cutouts
      finger-key-channels
      thumb-cutouts
      thumb-key-channels
      (if include-led-housings led-holes)
      (if include-backplate-block backplate-fastener-holes)
      (if include-wrist-rest
        (if (= wrist-rest-style :threaded) wrist-threaded-rod))
      (translate [0 0 -500] (cube 1000 1000 1000)))
    ;; The remaining elements are visualizations for use in development.
    ;; Do not render these to STL. Use the ‘#_’ macro or ‘;’ to hide them.
    #_(wrist-rest-right false)
    #_mcu-visualization
    #_finger-keycaps
    #_thumb-keycaps))

(spit "things/right-hand.scad"
      (write-scad keyboard-right))

(spit "things/left-hand.scad"
      (write-scad (mirror [-1 0 0] keyboard-right)))

(if include-wrist-rest
  (do
    (spit "things/right-wrist.scad"
          (write-scad wrist-rest-dual-view))
    (spit "things/left-wrist.scad"
          (write-scad (mirror [-1 0 0] wrist-rest-dual-view)))))

(defn -main [dum] 1)  ; Dummy to make it easier to batch.
