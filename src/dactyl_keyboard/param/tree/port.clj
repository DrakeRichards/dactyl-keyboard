;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; The Dactyl-ManuForm Keyboard — Opposable Thumb Edition              ;;
;; Parameter Specification – Ports                                     ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(ns dactyl-keyboard.param.tree.port
  (:require [scad-tarmi.core :as tarmi-core]
            [dactyl-keyboard.cots :as cots]
            [dactyl-keyboard.param.schema :as schema]
            [dactyl-keyboard.param.stock :as stock]))

(def raws
  "A flat version of a special part of a user configuration.
  Default values and parsers here are secondary. Validators are used."
  [["# Port configuration options\n\n"
    "Each heading in this document represents a recognized configuration key "
    "in YAML files for a DMOTE variant.\n\n"
    "This specific document describes options for the shape "
    "and position of any individual port. One set of such "
    "options will exist for each entry in `ports`, a section "
    "whose place in the larger hierarchy can be seen [here](options-main.md). "
    "Example uses for ports:\n"
    "\n"
    "* One port for the connection between the two halves of a reflected "
    "keyboard without a central housing. Such ports are usually TRRS or "
    "4P4C (“RJ9”), but you can use practically anything with enough wires.\n"
    "* An external USB port for interfacing with your computer, such as a "
    "full-size USB A port. You might want this when your MCU either has no "
    "such port attached or the attached port is too weak for direct human "
    "use (cf. `shelf`) or difficult to get into a good position.\n"
    "* Additional USB ports, connected via internal hub or to an "
    "integrated microphone clip, phone charger etc.\n"
    "* A speaker for QMK audio.\n"
    "* An LCD screen for QMK video.\n"
    "* An exotic human interface device, such as a large rotary encoder or "
    "trackball, not supported by this application as a type of keyboard "
    "switch.\n"
    "* Assortment drawers built into a large rear or central housing.\n"
    "\n"
    "Notice ports attached directly to microcontroller "
    "boards are treated in the `mcu` section, not here."]
   [:parameter [:include]
    {:default false :parse-fn boolean :validate [::schema/include]}
    "If `true`, include the port. The main use of this option is for "
    "disabling ports defined in other configuration files. "
    "The default value is `false` for consistency with other inclusion "
    "parameters."]
   [:parameter [:body]
    {:default :auto :parse-fn keyword :validate [::schema/body]}
    "A code identifying the [body](configuration.md) in which the port is cut."]
   [:parameter [:type]
    {:default :custom :parse-fn keyword
     :validate [(set (conj (keys cots/port-facts) :custom))]}
    "A code identifying a common type of port. "
    "The following values are recognized.\n\n"
    "* `custom`, meaning that `size` (below) will take effect.\n"
    (cots/support-list cots/port-facts)]
   [:parameter [:size]
    {:default [1 1 1] :parse-fn vec :validate [::tarmi-core/point-3d]}
    "An `[x, y, z]` vector specifying the size of the port in mm. "
    "This is used only with the `custom` port type.\n\n"
    "There are limited facilities for specifying the shape of a port. "
    "Basically, this parameter assumes a cuboid socket. For any different "
    "shape, get as close as possible with `tweaks`, then make your own "
    "adapter and/or widen the socket with a soldering iron or similar "
    "tools to fit a more complex object."]
   [:section [:alignment]
    "How the port lines itself up at its position."]
   [:parameter [:alignment :segment]
    stock/anchor-segment-metadata
    "Which vertical segment of the port itself to place at its anchor. "
    "The default value here is 0, meaning the ceiling of the port."]
   [:parameter [:alignment :side]
    stock/anchor-side-metadata
    "Which wall or corner of the port itself to place at its anchor. "
    "The default value here is `N` (nominal north), which is the open face "
    "of the port."]
   [:parameter [:intrinsic-rotation]
    stock/anchor-3d-vector-metadata
    "An `[x, y, z]` vector of radians, rotating the port around its point "
    "of `alignment` before moving it to `anchor`."]
   [:section [:anchoring]
    stock/anchoring-documentation]
   [:parameter [:anchoring :anchor]
    stock/anchor-metadata stock/anchor-documentation]
   [:parameter [:anchoring :side]
    stock/anchor-side-metadata stock/anchor-side-documentation]
   [:parameter [:anchoring :segment]
    stock/anchor-segment-metadata stock/anchor-segment-documentation]
   [:parameter [:anchoring :offset]
    stock/anchor-3d-vector-metadata stock/anchor-3d-offset-documentation]
   [:section [:holder]
    "A map describing a positive addition to the case on five "
    "sides of the port: Every side but the front."]
   [:parameter [:holder :include]
    {:default false :parse-fn boolean :validate [::schema/include]}
    "If `true`, build a wall around the port."]
   [:parameter [:holder :alias]
    {:default ::placeholder :parse-fn keyword :validate [::schema/alias]}
    "A name for the holder, to allow anchoring other features to it."]
   [:parameter [:holder :thickness]
    {:default 1 :parse-fn num :validate [::schema/thickness]}
    "A number specifying the thickness of the holder’s wall on each side, "
    "in mm."]])
