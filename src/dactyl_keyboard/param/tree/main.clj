;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; The Dactyl-ManuForm Keyboard — Opposable Thumb Edition              ;;
;; Parameter Specification – Main                                      ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(ns dactyl-keyboard.param.tree.main
  (:require [clojure.spec.alpha :as spec]
            [clojure.java.io :refer [file]]
            [scad-tarmi.core :as tarmi-core]
            [scad-app.core :as appdata]
            [dmote-keycap.data :as capdata]
            [dmote-keycap.schema :as capschema]
            [dactyl-keyboard.param.base :as base]
            [dactyl-keyboard.param.schema.valid :as valid]
            [dactyl-keyboard.param.schema.parse :as parse]
            [dactyl-keyboard.param.stock :as stock]
            [dactyl-keyboard.param.tree.central :as central]
            [dactyl-keyboard.param.tree.cluster :as cluster]
            [dactyl-keyboard.param.tree.port :as port]
            [dactyl-keyboard.param.tree.nested :as nested]
            [dactyl-keyboard.param.tree.restmnt :as restmnt]
            [dactyl-keyboard.cots :as cots]))


;; Though this module describes the main body of parameters, it contains
;; within it certain sections specified elsewhere. Validators for these
;; sections are created from the detailed specifications by delegation.

(spec/def ::parameters (base/delegated-validation nested/raws))
(spec/def ::individual-row (spec/keys :opt-un [::parameters]))
(spec/def ::rows (spec/map-of ::valid/flexcoord ::individual-row))
(spec/def ::individual-column (spec/keys :opt-un [::rows ::parameters]))
(spec/def ::columns (spec/map-of ::valid/flexcoord ::individual-column))
(spec/def ::overrides (spec/keys :opt-un [::columns ::parameters]))

(def raws
  "A flat version of the specification for a complete user configuration.
  This absorbs major subsections from elsewhere."
  [["# General configuration options\n\n"
    "Each heading in this document represents a recognized configuration key "
    "in the main body of a YAML file for a DMOTE variant. Other documents "
    "cover special sections of this one in more detail."]
   [:section [:keys]
    "Keys, that is keycaps and electrical switches, are not the main focus of "
    "this application, but they influence the shape of the case."]
   [:parameter [:keys :preview]
    {:default false :parse-fn boolean}
    "If `true`, include models of the keycaps in place on the keyboard. This "
    "is intended for illustration as you work on a design, not for printing."]
   [:parameter [:keys :styles]
    {:default {:default {}} :parse-fn parse/keycap-map
     :validate [(spec/map-of keyword? ::capschema/keycap-parameters)]}
    "Here you name all the styles of keys on the keyboard and describe each "
    "style using parameters to the `keycap` function of the "
    "[`dmote-keycap`](https://github.com/veikman/dmote-keycap) library. "
    "Switch type is one aspect of key style.\n"
    "\n"
    "These key styles determine the size of key mounting plates on the "
    "keyboard and what kind of holes are cut into those plates for the "
    "switches to fit inside. "
    "Negative space is also reserved above the plate for the movement "
    "of the keycap: A function of switch height, switch travel, and keycap "
    "shape. In addition, if the keyboard is curved, key styles help determine "
    "the spacing between key mounts.\n"
    "\n"
    "In options by key, documented [here](options-nested.md), you specify "
    "which style of key is used for each position on the keyboard."]
   [:parameter [:key-clusters]
    {:heading-template "Special section %s"
     :default {:main {:matrix-columns [{:rows-below-home 0}]
                      :aliases {}}}
     :parse-fn (parse/map-of keyword
                 (base/parser-with-defaults cluster/raws))
     :validate [(spec/map-of
                  ::valid/key-cluster
                  (base/delegated-validation cluster/raws))]}
    "This section describes the general size, shape and position of "
    "the clusters of keys on the keyboard, each in its own subsection. "
    "It is documented in detail [here](options-clusters.md)."]
   [:section [:by-key]
    "This section repeats. Each level of settings inside it "
    "is more specific to a smaller part of the keyboard, eventually reaching "
    "the level of individual keys. It’s all documented "
    "[here](options-nested.md)."]
   [:parameter [:by-key :parameters]
    {:heading-template "Special recurring section %s"
     :default (base/extract-defaults nested/raws)
     :parse-fn (base/parser-with-defaults nested/raws)
     :validate [(base/delegated-validation nested/raws)]}
    "Default values at the global level."]
   [:parameter [:by-key :clusters]
    (let [rep (base/parser-wo-defaults nested/raws)]
      {:heading-template "Special section %s ← overrides go in here"
       :default {}
       :parse-fn (parse/map-of
                   keyword
                   (parse/map-like
                     {:parameters rep
                      :columns
                       (parse/map-of
                         parse/keyword-or-integer
                         (parse/map-like
                           {:parameters rep
                            :rows
                              (parse/map-of
                                parse/keyword-or-integer
                                (parse/map-like {:parameters rep}))}))}))
       :validate [(spec/map-of ::valid/key-cluster ::overrides)]})
    "Starting here, you gradually descend from the global level "
    "toward the key level."]
   [:parameter [:secondaries]
    {:default {}
     :parse-fn parse/named-secondary-positions
     :validate [::valid/named-secondary-positions]}
    "A map where each item provides a name for a position in space. "
    "Such positions exist in relation to other named features of the keyboard "
    "and can themselves be used as named features: Typically as supplementary "
    "targets for `tweaks`, which are defined below.\n"
    "\n"
    "An example:\n\n"
    "```secondaries:\n"
    "  s0:\n"
    "    anchoring:\n"
    "      anchor: f0\n"
    "      side: SE\n"
    "      segment: 2\n"
    "      offset: [1, 0, 0]\n"
    "    override [null, null, 2]\n"
    "    translation: [0, 3, 0]\n```\n"
    "\n"
    "This example gives the name `s0` to a point near some feature named "
    "`f0`, which must be defined elsewhere. All parameters in the `anchoring` "
    "map work like their equivalent for primary features like `mcu`, so that "
    "`offset` is applied in the vector space of the anchor.\n"
    "\n"
    "Populated coordinates in `override` replace corresponding coordinates "
    "given by the anchor, and `translation` finally shifts the position "
    "of the secondary feature in the global vector space.\n"
    "\n"
    "In the example, `s0` is a position 1 mm to the local right of the "
    "south-east corner of vertical segment 2 of `f0`, projected onto the "
    "global x-y plane at z = 2 (i.e. 2 mm above the floor), and then shifted "
    "3 mm away from the user on that plane."]
   [:section [:main-body]
    "The main body of the keyboard is the main output of this application. "
    "It may be the only body. "
    "Much of this part of the case is generated from the `wall` parameters "
    "described [here](options-nested.md). "
    "This section deals with lesser features of the main body."]
   [:parameter [:main-body :reflect]
    {:default false :parse-fn boolean}
    "If `true`, mirror the main body, producing one version for the right hand "
    "and another for the left. The two halves will be almost identical: "
    "Only chiral parts, such as threaded holes, are exempt from mirroring "
    "with `main-body` → `reflect`.\n\n"
    "You can use this option to make a ‘split’ keyboard, though the two "
    "halves are typically connected by a signalling cable, by a rigid "
    "`central-housing`, or by one or more rods anchored to "
    "some feature such as `rear-housing` or `back-plate`."]
   [:parameter [:main-body :key-mount-thickness]
    {:default 1 :parse-fn num}
    "The thickness in mm of each switch key mounting plate."]
   [:parameter [:main-body :key-mount-corner-margin]
    {:default 1 :parse-fn num}
    "The thickness in mm of an imaginary “post” at each corner of each key "
    "mount. Copies of such posts project from the key mounts to form the main "
    "walls of the case.\n"
    "\n"
    "`key-mount-thickness` is similarly the height of each post."]
   [:parameter [:main-body :web-thickness]
    {:default 1 :parse-fn num}
    "The thickness in mm of the webbing between switch key "
    "mounting plates, and of the rear housing’s walls and roof."]
   [:section [:main-body :rear-housing]
    "The furthest row of a key cluster can be extended into a rear housing "
    "for the MCU and various other features."]
   [:parameter [:main-body :rear-housing :include]
    {:default false :parse-fn boolean}
    "If `true`, add a rear housing. Please arrange case walls so as not to "
    "interfere, by removing them along the far side of the last row of key "
    "mounts in the indicated cluster."]
   [:parameter [:main-body :rear-housing :wall-thickness]
    {:default 1 :parse-fn num}
    "The horizontal thickness in mm of the walls."]
   [:parameter [:main-body :rear-housing :roof-thickness]
    {:default 1 :parse-fn num}
    "The vertical thickness in mm of the flat top."]
   [:section [:main-body :rear-housing :position]
    "Where to put the rear housing. Unlike a central housing, a rear housing "
    "is placed in relation to a key cluster. By default, it sits all along "
    "the far (north) side of the `main` cluster but has no depth."]
   [:parameter [:main-body :rear-housing :position :cluster]
    {:default :main :parse-fn keyword :validate [::valid/key-cluster]}
    "The key cluster at which to anchor the housing."]
   [:section [:main-body :rear-housing :position :offsets]
    "Modifiers for where to put the four sides of the roof. All are in mm."]
   [:parameter [:main-body :rear-housing :position :offsets :north]
    {:default 0 :parse-fn num}
    "The extent of the roof on the y axis; its horizontal depth."]
   [:parameter [:main-body :rear-housing :position :offsets :west]
    {:default 0 :parse-fn num}
    "The extent on the x axis past the first key in the row."]
   [:parameter [:main-body :rear-housing :position :offsets :east]
    {:default 0 :parse-fn num}
    "The extent on the x axis past the last key in the row."]
   [:parameter [:main-body :rear-housing :position :offsets :south]
    {:default 0 :parse-fn num}
    "The horizontal distance in mm, on the y axis, between the furthest key "
    "in the row and the roof of the rear housing."]
   [:parameter [:main-body :rear-housing :height]
    {:default 0 :parse-fn num}
    "The height in mm of the roof, over the floor."]
   [:section [:main-body :rear-housing :fasteners]
    "Threaded bolts can run through the roof of the rear housing, making it a "
    "hardpoint for attachments like a stabilizer to connect the two halves of "
    "a split keyboard."]
   [:parameter [:main-body :rear-housing :fasteners :bolt-properties]
    stock/implicit-threaded-bolt-metadata
    stock/threaded-bolt-documentation]
   [:parameter [:main-body :rear-housing :fasteners :bosses]
    {:default false :parse-fn boolean}
    "If `true`, add nut bosses to the ceiling of the rear housing for each "
    "fastener. Space permitting, these bosses will have some play on the "
    "north-south axis, to permit adjustment of the angle of the keyboard "
    "halves under a stabilizer."]
   [:section [:main-body :rear-housing :fasteners :west]
    "A fastener on the inward-facing end of the rear housing."]
   [:parameter [:main-body :rear-housing :fasteners :west :include]
    {:default false :parse-fn boolean}
    "If `true`, include this fastener."]
   [:parameter [:main-body :rear-housing :fasteners :west :offset]
    {:default 0 :parse-fn num}
    "A one-dimensional offset in mm from the inward edge of the rear "
    "housing to the fastener. You probably want a negative number if any."]
   [:section [:main-body :rear-housing :fasteners :east]
    "A fastener on the outward-facing end of the rear housing. All parameters "
    "are analogous to those for `west`."]
   [:parameter [:main-body :rear-housing :fasteners :east :include]
    {:default false :parse-fn boolean}]
   [:parameter [:main-body :rear-housing :fasteners :east :offset]
    {:default 0 :parse-fn num}]
   [:section [:main-body :back-plate]
    "Given that independent movement of each half of a split keyboard is not "
    "useful, each half can include a mounting plate for a stabilizing rod. "
    "That is a straight piece of wood, aluminium, rigid plastic etc. to "
    "connect the two halves mechanically and possibly carry the wire that "
    "connects them electrically.\n\n"
    "This option is similar to rear housing, but the back plate block "
    "provides no interior space for an MCU etc. It is solid, with holes "
    "for threaded fasteners including the option of nut bosses. "
    "Its footprint is not part of a `bottom-plate`."]
   [:parameter [:main-body :back-plate :include]
    {:default false :parse-fn boolean}
    "If `true`, include a back plate block. This is not contingent upon "
    "`reflect`."]
   [:parameter [:main-body :back-plate :beam-height]
    {:default 1 :parse-fn num}
    "The nominal vertical extent of the back plate in mm. "
    "Because the plate is bottom-hulled to the floor, the effect "
    "of this setting is on the area of the plate above its holes."]
   [:section [:main-body :back-plate :fasteners]
    "Two threaded bolts run through the back plate."]
   [:parameter [:main-body :back-plate :fasteners :bolt-properties]
    stock/implicit-threaded-bolt-metadata
    stock/threaded-bolt-documentation]
   [:parameter [:main-body :back-plate :fasteners :distance]
    {:default 1 :parse-fn num}
    "The horizontal distance between the bolts."]
   [:parameter [:main-body :back-plate :fasteners :bosses]
    {:default false :parse-fn boolean}
    "If `true`, cut nut bosses into the inside wall of the block."]
   [:section [:main-body :back-plate :anchoring]
    stock/anchoring-documentation]
   [:parameter [:main-body :back-plate :anchoring :anchor]
    stock/anchor-metadata stock/anchor-documentation]
   [:parameter [:main-body :back-plate :anchoring :offset]
    stock/anchor-3d-vector-metadata stock/anchor-3d-offset-documentation]
   [:section [:main-body :bottom-plate]
    "A bottom plate can be added to close the case. This is useful mainly to "
    "simplify transportation.\n"
    "\n"
    "#### Overview\n"
    "\n"
    "The bottom plate is largely two-dimensional. The application builds most "
    "of it from a set of polygons, trying to match the perimeter of the case "
    "at the ground level (i.e. z = 0).\n"
    "\n"
    "Specifically, there is one polygon per key cluster, limited to `full` "
    "wall edges, one polygon for the rear housing, and one set of polygons "
    "for each of the first-level case `tweaks` that use `at-ground`, ignoring "
    "chunk size and almost ignoring tweaks nested within lists of tweaks.\n"
    "\n"
    "This methodology is mentioned here because its results are not perfect. "
    "Pending future features in OpenSCAD, a future version may be based on a "
    "more exact projection of the case, but as of 2018, such a projection is "
    "hollow and cannot be convex-hulled without escaping the case, unless "
    "your case is convex to start with.\n"
    "\n"
    "For this reason, while the polygons fill the interior, the perimeter of "
    "the bottom plate is extended by key walls and case `tweaks` as they "
    "would appear at the height of the bottom plate. Even this brutality may "
    "be inadequate. If you require a more exact match, do a projection of the "
    "case without a bottom plate, save it as DXF/SVG etc. and post-process "
    "that file to fill the interior gap."]
   [:parameter [:main-body :bottom-plate :include]
    {:default false :parse-fn boolean}
    "If `true`, include a bottom plate for the case."]
   [:parameter [:main-body :bottom-plate :preview]
    {:default false :parse-fn boolean}
    "Preview mode. If `true`, put a model of the plate in the same file as "
    "the case it closes. Not for printing."]
   [:parameter [:main-body :bottom-plate :combine]
    {:default false :parse-fn boolean}
    "If `true`, combine wrist rests for the case and the bottom plate into a "
    "single model, when both are enabled. This is typically used with the "
    "`solid` style of wrest rest."]
   [:parameter [:main-body :bottom-plate :thickness]
    {:default 1 :parse-fn num}
    "The thickness (i.e. height) in mm of all bottom plates you choose to "
    "include. This covers plates for the case and for the wrist rest.\n"
    "\n"
    "The case will not be raised to compensate for this. Instead, the height "
    "of the bottom plate will be removed from the bottom of the main model so "
    "that it does not extend to z = 0."]
   [:section [:main-body :bottom-plate :installation]
    "How your bottom plate is attached to the rest of your case."]
   [:parameter [:main-body :bottom-plate :installation :style]
    {:default :threads :parse-fn keyword
     :validate [::valid/plate-installation-style]}
    "The general means of installation. This parameter has been reduced to a "
    "placeholder: The only available style is `threads`, signifying the use "
    "of threaded fasteners connecting the bottom plate to anchors in "
    "the body of the keyboard."]
   [:parameter [:main-body :bottom-plate :installation :dome-caps]
    {:default false :parse-fn boolean}
    "If `true`, terminate each anchor with a hemispherical tip. This is "
    "an aesthetic feature, primarily intended for externally visible anchors "
    "and printed threading. "
    "If all of your anchors are completely internal to the case, and/or you "
    "intend to tap the screw holes after printing, dome caps are wasteful at "
    "best and counterproductive at worst."]
   [:parameter [:main-body :bottom-plate :installation :thickness]
    {:default 1 :parse-fn num}
    "The thickness in mm of each wall of the anchor points for threaded "
    "fasteners."]
   [:section [:main-body :bottom-plate :installation :inserts]
    "You can use heat-set inserts in the anchor points.\n\n"
    "It is assumed that, as in Tom Short’s Dactyl-ManuForm, the inserts are "
    "largely cylindrical."]
   [:parameter [:main-body :bottom-plate :installation :inserts :include]
    {:default false :parse-fn boolean}
    "If `true`, make space for inserts."]
   [:parameter [:main-body :bottom-plate :installation :inserts :length]
    {:default 1 :parse-fn num}
    "The length in mm of each insert."]
   [:section [:main-body :bottom-plate :installation :inserts :diameter]
    "Inserts may vary in diameter across their length."]
   [:parameter [:main-body :bottom-plate :installation :inserts :diameter :top]
    {:default 1 :parse-fn num}
    "Top diameter in mm."]
   [:parameter [:main-body :bottom-plate :installation :inserts :diameter :bottom]
    {:default 1 :parse-fn num}
    "Bottom diameter in mm. This needs to be at least as large as the top "
    "diameter since the mounts for the inserts only open from the bottom."]
   [:section [:main-body :bottom-plate :installation :fasteners]
    "The type and positions of the threaded fasteners used to secure each "
    "bottom plate."]
   [:parameter [:main-body :bottom-plate :installation :fasteners :bolt-properties]
    stock/implicit-threaded-bolt-metadata
    stock/threaded-bolt-documentation]
   [:parameter [:main-body :bottom-plate :installation :fasteners :positions]
    {:default []
     :parse-fn parse/anchored-2d-positions
     :validate [::valid/anchored-2d-list]}
    "A list of places where threaded fasteners will connect the bottom plate "
    "to the rest of the case."]
   [:section [:main-body :leds]
    "Support for light-emitting diodes in the case walls."]
   [:parameter [:main-body :leds :include]
    {:default false :parse-fn boolean}
    "If `true`, cut slots for LEDs out of the case wall, facing "
    "the space between the two halves."]
   [:section [:main-body :leds :position]
    "Where to attach the LED strip."]
   [:parameter [:main-body :leds :position :cluster]
    {:default :main :parse-fn keyword :validate [::valid/key-cluster]}
    "The key cluster at which to anchor the strip."]
   [:parameter [:main-body :leds :amount]
    {:default 1 :parse-fn int} "The number of LEDs."]
   [:parameter [:main-body :leds :housing-size]
    {:default 1 :parse-fn num}
    "The length of the side on a square profile used to create negative space "
    "for the housings on a LED strip. This assumes the housings are squarish, "
    "as on a WS2818.\n"
    "\n"
    "The negative space is not supposed to penetrate the wall, just make it "
    "easier to hold the LED strip in place with tape, and direct its light. "
    "With that in mind, feel free to exaggerate by 10%."]
   [:parameter [:main-body :leds :emitter-diameter]
    {:default 1 :parse-fn num}
    "The diameter of a round hole for the light of an LED."]
   [:parameter [:main-body :leds :interval]
    {:default 1 :parse-fn num}
    "The distance between LEDs on the strip. You may want to apply a setting "
    "slightly shorter than the real distance, since the algorithm carving the "
    "holes does not account for wall curvature."]
   [:section [:main-body :foot-plates]
    "Optional flat surfaces at ground level for adding silicone rubber feet "
    "or cork strips etc. to the bottom of the keyboard to increase friction "
    "and/or improve feel, sound and ground clearance."]
   [:parameter [:main-body :foot-plates :include]
    {:default false :parse-fn boolean} "If `true`, include foot plates."]
   [:parameter [:main-body :foot-plates :height]
    {:default 4 :parse-fn num} "The height in mm of each mounting plate."]
   [:parameter [:main-body :foot-plates :polygons]
    {:default []
     :parse-fn parse/anchored-polygons
     :validate [::valid/foot-plate-polygons]}
    "A list describing the horizontal shape, size and "
    "position of each mounting plate as a polygon."]
   [:parameter [:central-housing]
    {:heading-template "Section %s"
     :default (base/extract-defaults central/raws)
     :parse-fn (base/parser-with-defaults central/raws)
     :validate [(base/delegated-validation central/raws)]}
    "A major body separate from the main body, located in between and "
    "connecting the two halves of a reflected main body. "
    "The central housing is documented in detail [here](options-central.md)."]
   [:parameter [:tweaks]
    {:default {} :parse-fn parse/tweak-grove
     :validate [::valid/tweak-name-map]}
    "Additional shapes. This parameter is usually needed to bridge gaps "
    "between the walls of key clusters. The expected value here is an "
    "arbitrarily nested structure starting with a map of names to lists.\n"
    "\n"
    "The names at the entry level are arbitrary but should be distinct and "
    "descriptive. They cannot serve as anchors. "
    "Their only technical significance lies in the fact that when you combine "
    "multiple configuration files, a later tweak will override a previous "
    "tweak if and only if they share the same name.\n"
    "\n"
    "In the list below each name, each item can follow one of the following "
    "patterns:\n"
    "\n"
    "- A leaf node, representing a simple shape in a specific place.\n"
    "- A branch node, containing a list like the one below each name and "
    "representing some combination of the nodes in it.\n"
    "\n"
    "These terms are metaphorical. In the metaphor, the list itself is not "
    "one tree but the soil of a grove of trees.\n"
    "\n"
    "Each **leaf node** places something near a named part of the keyboard. "
    "This is ordinary [anchoring](configuration.md) of simple shapes. "
    "In the final form of a leaf, it is a map with the following keys:\n"
    "\n"
    "- `anchoring` (required): A nested map. See the general documentation "
    "[here](configuration.md).\n"
    "- `sweep` (optional): An integer. If you supply a sweep, you must also "
    "supply a `segment` in `anchoring`. `sweep` identifies another segment "
    "and must be the larger of the two numbers. "
    "With both, the leaf will represent the convex hull of the two segments "
    "plus all segments between them, off the same anchor. "
    "This is most commonly used to finish the outer walls of a case.\n"
    "- `size` (optional): An `[x, y, z]` vector describing a cuboid, in mm. "
    "If you supply this, for certain types of anchors, it overrides the "
    "default model. However, some types of anchors will ignore a custom "
    "size. The default size depends both on the type of anchor and on which "
    "anchoring parameters you use.\n"
    "\n"
    "All those keys in a leaf map take a up a lot of space. If you wish, "
    "you can instead define each leaf in the form of a list of 1 to 5 "
    "elements:\n"
    "\n"
    "1. The `anchor`.\n"
    "2. The `side`.\n"
    "3. The starting vertical segment ID.\n"
    "4. The sweep, which is the stopping vertical segment ID.\n"
    "\n"
    "As a fifth element, and/or in place of any of the last three, "
    "the list may contain a map of additional leaf settings that is merged "
    "into the final representation specified above.\n"
    "\n"
    "Here’s the fine print on the two different ways to specify a leaf:\n"
    "\n"
    "- You cannot use the list format alone to specify a size or offset.\n"
    "- When you use the list format, the first element must be the name of "
    "an anchor. You cannot have a map as the first element.\n"
    "- In the list format, you can specify `null` in place of elements "
    "you don’t want to specify, but this is only meaningful for `side`.\n"
    "\n"
    "By default, a **branch node** will create a convex hull around the "
    "nodes it contains. However, this behaviour can be modified. "
    "The following keys are recognized in any branch node:\n"
    "\n"
    "- `hull-around` (required): The list of child nodes.\n"
    "- `chunk-size` (optional): Any integer greater than 1. "
    "If this is set, child nodes will not share a single convex hull. "
    "Instead, there will be a sequence of smaller hulls, each encompassing "
    "this many items.\n"
    "- `highlight` (optional): If `true`, render the node in OpenSCAD’s "
    "highlighting style. This is convenient while you work.\n"
    "\n"
    "Leaf and branch nodes at the entry level, in the list following the "
    "name of a tweak, are special. "
    "They grow in the soil and represent individual plants with "
    "their own roots. When nodes are selected for a particular purpose, "
    "that selection happens at the entry level. "
    "Nodes at the entry level may therefore contain the following extra "
    "keys, which determine how each node affects the keyboard:\n"
    "\n"
    "- `positive` (optional): If `true`, add material to the case. "
    "If `false`, subtract material. The default value is `true`.\n"
    "- `at-ground` (optional): This setting has two effects. "
    "If `true`, extend vertically down to the ground plane, "
    "as with a `full` wall, *and* influence the shape of a `bottom-plate`. "
    "The default value is `false`.\n"
    "- `above-ground` (optional): If `true`, appear as part of the case. "
    "The default value is `true`. When this is `false` and `at-ground` is "
    "`true`, the node affects the bottom plate only, which is the only use "
    "for this option.\n"
    "- `body` (optional): Refer to general documentation "
    "[here](configuration.md). As usual, the default value is `auto`. "
    "When the node is a branch, `auto` uses the first leaf subordinate to "
    "the branch for the usual heuristics.\n"
    "\n"
    "Nodes subordinate to branches may not contain these extra keys.\n"
    "\n"
    "In the following example, `A` and `B` are key aliases that would be "
    "defined elsewhere.\n"
    "\n"
    "```tweaks:\n"
    "  bridge-between-A-and-B:\n"
    "    - chunk-size: 2\n"
    "      hull-around:\n"
    "      - [A, SSE, 0]\n"
    "      - [B, NNE, 0]\n"
    "      - [A, SSW, 0, 4]\n```\n"
    "\n"
    "The example is interpreted to mean that a plate should be "
    "created stretching from the south-by-southeast corner of `A` to the "
    "north-by-northeast corner of `B`. Due to `chunk-size` 2, that first "
    "plate will be joined to, but not fully hulled with, a second plate "
    "from `B` back to a different corner of `A`, with a longer stretch "
    "of (all) wall segments running down the corner of `A`."]
   [:section [:mcu]
    "MCU is short for ”micro-controller unit”. You need at least one of "
    "these, it’s assumed to be mounted on a PCB, and you typically want some "
    "support for it inside the case.\n\n"
    "The total number of MCUs is governed by more than one setting, roughly "
    "in the following order:\n\n"
    "* If `mcu` → `include` is `false`, there is no MCU.\n"
    "* If `mcu` → `include` is `true` but `main-body` → `reflect` is `false`, "
    "there is one MCU.\n"
    "* If `mcu` → `include` and `main-body` → `reflect` and `mcu` → `position` "
    " → `central` are all `true`, there is (again) one MCU.\n"
    "* Otherwise, there are two MCUs: One in each half of the case, because "
    "of reflection."]
   [:parameter [:mcu :include]
    {:default false :parse-fn boolean}
    "If `true`, make space for at least one MCU PCBA."]
   [:parameter [:mcu :preview]
    {:default false :parse-fn boolean}
    "If `true`, render a visualization of the MCU PCBA. "
    "For use in development."]
   [:parameter [:mcu :body]
    {:default :auto :parse-fn keyword :validate [::valid/body]}
    "A code identifying the [body](configuration.md) that houses the MCU."]
   [:parameter [:mcu :type]
    {:default :promicro :parse-fn keyword
     :validate [(partial contains? cots/mcu-facts)]}
    "A code name for a form factor. "
    "The following values are supported, representing a selection of "
    "designs for commercial products from PJRC, SparkFun, the QMK team "
    "and others:\n\n"
    (cots/support-list cots/mcu-facts)]
   [:parameter [:mcu :intrinsic-rotation]
    stock/compass-incompatible-3d-angle-metadata
    "A vector of 3 angles in radians. This parameter governs the rotation of "
    "the PCBA around its anchor point in the front. "
    "By default, the PCBA appears lying flat, with the MCU side up and the "
    "connector end facing nominal north, away from the user."]
   [:section [:mcu :anchoring]
    "Where to place the MCU PCBA after intrinsic rotation. "
    stock/anchoring-documentation]
   [:parameter [:mcu :anchoring :anchor]
    stock/anchor-metadata stock/anchor-documentation]
   [:parameter [:mcu :anchoring :side]
    stock/anchor-side-metadata stock/anchor-side-documentation]
   [:parameter [:mcu :anchoring :segment]
    stock/anchor-segment-metadata stock/anchor-segment-documentation]
   [:parameter [:mcu :anchoring :offset]
    stock/anchor-3d-vector-metadata stock/anchor-3d-offset-documentation]
   [:section [:mcu :support]
    "This section offers a couple of different, mutually compatible ways to "
    "hold an MCU PCBA in place. Without such support, the MCU will be "
    "rattling around inside the case.\n\n"
    "Support is especially important if connector(s) on the PCBA will be "
    "exposed to animals, such as people. Take care that the user can plug in "
    "a USB cable, which requires a receptable to be both reachable "
    "through the case *and* held there firmly enough that the force of the "
    "user’s interaction will neither damage nor displace the board.\n\n"
    "Despite the importance of support in most use cases, no MCU support is "
    "included by default."]
   [:parameter [:mcu :support :preview]
    {:default false :parse-fn boolean}
    "If `true`, render a visualization of the support in place. This applies "
    "only to those parts of the support that are not part of the case model."]
   [:section [:mcu :support :shelf]
    "The case can include a shelf for the MCU.\n\n"
    "A shelf is the simplest type of MCU support, found on the original "
    "Dactyl-ManuForm. It provides very little mechanical support to hold the "
    "MCU itself in place, so it is not suitable for exposing a connector on "
    "the MCU PCBA through the case. Instead, it’s suitable for use together "
    "with a pigtail cable between the MCU and a secondary USB connector "
    "embedded in the case wall (see `ports`). "
    "It’s especially good with stiff single-strand wiring that will help keep "
    "the MCU in place without a lock or firm grip."]
   [:parameter [:mcu :support :shelf :include]
    {:default false :parse-fn boolean}
    "If `true`, include a shelf."]
   [:parameter [:mcu :support :shelf :extra-space]
    {:default [0 0 0] :parse-fn vec :validate [::tarmi-core/point-3d]}
    "Modifiers for the size of the PCB, on all three axes, in mm, for the "
    "purpose of determining the size of the shelf.\n\n"
    "For example, the last term, for z, adds extra space between the "
    "component side of the PCBA up to the overhang on each side of the shelf, "
    "if any. The MCU will appear centered inside the available space, so this "
    "parameter can move the plane of the shelf itself."]
   [:parameter [:mcu :support :shelf :thickness]
    {:default 1 :parse-fn num :validate [pos?]}
    "The thickness of material in the shelf, below or behind the PCBA, in mm."]
   [:parameter [:mcu :support :shelf :bevel]
    {:default {} :parse-fn parse/compass-angle-map
     :validate [::valid/compass-angle-map]}
    "A map of angles, in radians, indexed by cardinal compass points, whereby "
    "any and all sides of the shelf are turned away from the MCU PCBA. "
    "This feature is intended mainly for manufacturability, to reduce the "
    "need for supports in printing, but it can also add strength or help "
    "connect to other features."]
   [:section [:mcu :support :shelf :sides]
    "By default, a shelf includes raised sides to hold on to the "
    "PCBA. This is most useful when the shelf is rotated, following the "
    "MCU (cf. `intrinsic-rotation`), out of the x-y plane."]
   [:parameter [:mcu :support :shelf :sides :lateral-thickness]
    {:default 1 :parse-fn num :validate [pos?]}
    "The thickness of material to each side of the MCU, in mm."]
   [:parameter [:mcu :support :shelf :sides :overhang-thickness]
    {:default 1 :parse-fn num :validate [pos?]}
    "The thickness of material in the outermost part on each side, in mm."]
   [:parameter [:mcu :support :shelf :sides :overhang-width]
    {:default 0 :parse-fn num :validate [#(not (neg? %))]}
    "The extent to which each grip extends out across the PCBA, in mm."]
   [:parameter [:mcu :support :shelf :sides :offsets]
    {:default [0 0]
     :parse-fn (fn [candidate]
                 (if (number? candidate) [candidate candidate] (vec candidate)))
     :validate [::tarmi-core/point-2d]}
    "One or two lengthwise offsets in mm. When these are left at zero, the "
    "sides of the shelf will appear in full. A negative or positive offset "
    "shortens the corresponding side, towards or away from the connector side "
    "of the PCBA."]
   [:section [:mcu :support :lock]
    "An MCU lock is a support feature made up of three parts:\n\n"
    "* A fixture printed as part of the case. This fixture includes a plate for "
    "the PCB and a socket. The socket holds a USB connector on the PCB in "
    "place.\n"
    "* The bolt of the lock, printed separately.\n"
    "* A threaded fastener, not printed.\n"
    "The fastener connects the bolt to the fixture as the lock closes over "
    "the PCB. Confusingly, the fastener constitutes a bolt, in a different "
    "sense of that word.\n\n"
    "A lock is most appropriate when the PCB aligns with a long, flat wall; "
    "typically the wall of a rear housing. It has the advantage that it can "
    "hug the connector on the PCB tightly from four sides, thus preventing "
    "a fragile surface-mounted connector from snapping off."]
   [:parameter [:mcu :support :lock :include]
    {:default false :parse-fn boolean}
    "If `true`, include a lock."]
   [:parameter [:mcu :support :lock :width-factor]
    {:default 1 :parse-fn num}
    "A multiplier for the width of the PCB. This determines the width of the "
    "parts touching the PCB in a lock: The plate and the base of the bolt."]
   [:parameter [:mcu :support :lock :fastener-properties]
    ;; This parameter is named “fastener-properties” instead of the normal
    ;; “bolt-properties” to help distinguish it both from the lock bolt and
    ;; from parameters that add an implicit default length. The nomenclature
    ;; is not ideal.
    stock/explicit-threaded-bolt-metadata
    "Like the various `bolt-properties` parameters elsewhere, this parameter "
    "describes a threaded fastener using the `bolt` function in the "
    "[`scad-klupe.iso`](https://github.com/veikman/scad-klupe) library.\n\n"
    "This particular set of fastener propertes should not include a "
    "`total-length` because the application will interpolate default values "
    "for both `unthreaded-length` and `threaded-length` based on other "
    "properties of the lock. A contradictory `total-length` is an error."]
   [:section [:mcu :support :lock :plate]
    "In the lock, the MCU PCBA sits on a plate, as part of the fixture. "
    "This plate is named by analogy with a roughly corresponding part in a "
    "door lock. The plate actually looks like a bed for the PCB.\n\n"
    "The plate is typically more narrow than the PCB, its width being "
    "determined by `width-factor`. Its total height is the sum of this "
    "section’s `base-thickness` and `clearance`."]
   [:parameter [:mcu :support :lock :plate :alias]
    {:default ::placeholder :parse-fn keyword :validate [::valid/alias]}
    "A name you can use to target the base of the plate for `tweaks`. "
    "This is useful mainly when there isn’t a flat wall behind the lock."]
   [:parameter [:mcu :support :lock :plate :base-thickness]
    {:default 1 :parse-fn num}
    "The thickness of the base of the plate, in mm."]
   [:parameter [:mcu :support :lock :plate :clearance]
    {:default 1 :parse-fn num}
    "The distance between the MCU PCB and the base of the plate, in mm.\n\n"
    "Unlike the base of the plate, its clearance displaces the PCB and cannot "
    "be targeted by `tweaks`, but both parts of the plate have the same "
    "length and width.\n\n"
    "The main use for `clearance` is to leave room between a wall supporting "
    "the lock and the PCB’s through-holes, so its height should be roughly "
    "matched to the length of wire overshoot through the PCB, with a safety "
    "margin for air."]
   [:section [:mcu :support :lock :socket]
    "A housing around the USB connector on the MCU PCBA."]
   [:parameter [:mcu :support :lock :socket :thickness]
    {:default 1 :parse-fn num}
    "The wall thickness of the socket."]
   [:section [:mcu :support :lock :bolt]
    "The bolt of the MCU lock, named by analogy with a regular door lock, is "
    "not to be confused with the threaded fastener holding it in place. "
    "The properties of the threaded fastener are set using "
    "`fastener-properties` above while the properties of the lock bolt are set "
    "here."]
   [:parameter [:mcu :support :lock :bolt :clearance]
    {:default 1 :parse-fn num}
    "The distance of the bolt from the populated side of the PCB. "
    "This distance should be slightly greater than the height of the tallest "
    "component on the PCB."]
   [:parameter [:mcu :support :lock :bolt :overshoot]
    {:default 1 :parse-fn num}
    "The distance across which the bolt will touch the PCB at the mount end. "
    "Take care that this distance is free of components on the PCB."]
   [:parameter [:mcu :support :lock :bolt :mount-length]
    {:default 1 :parse-fn num}
    "The length of the base containing a threaded channel used to secure the "
    "bolt over the MCU. This is in addition to `overshoot` and "
    "goes in the opposite direction, away from the PCB."]
   [:parameter [:mcu :support :lock :bolt :mount-thickness]
    {:default 1 :parse-fn num}
    "The thickness of the mount. This should have some rough correspondence "
    "to the threaded portion of your fastener, which should not have a shank."]
   [:section [:mcu :support :grip]
    "The case can extend to hold the MCU firmly in place.\n\n"
    "Space is reserved for the MCU PCB. This space will cut into each grip "
    "that intersects the PCB, as determined by the center of each post (set "
    "with `anchors` in this section) and its `size`. These intersections "
    "create notches in the grips, which is how they hold onto the PCB. The "
    "deeper the notch, the more flexible the case has to be to allow assembly."]
   [:parameter [:mcu :support :grip :size]
    {:default [1 1 1] :parse-fn vec :validate [::tarmi-core/point-3d]}
    "The three dimensions of a grip post, in mm.\n\n"
    "This parameter determines the size of the object that will occupy an "
    "anchor point for a grip when that point is targeted by a tweak. It "
    "corresponds to `key-mount-corner-margin` and `web-thickness` but "
    "provides more control and is specific to MCU grips."]
   [:parameter [:mcu :support :grip :anchors]
    {:default [] :parse-fn parse/mcu-grip-anchors
     :validate [::valid/mcu-grip-anchors]}
    "A list of points in space positioned relative to the PCB’s corners.\n\n"
    "Each point must have an `alias`, which is a name you can use "
    "elsewhere to refer to that point, and a `side`, identifying one "
    "side of the PCB, e.g. `SE` for the south-east corner.\n\n"
    "Each point may also have an `offset` from the stated side. These "
    "offsets must be given in mm, either as a 2-tuple like `[1, 2]` for a "
    "two-dimensional offset in the plane of the PCB, or as a 3-tuple "
    "like `[1, 2, 3]` for a three-dimensional offset that can put the point "
    "above or below the PCB.\n\n"
    "An example with two-dimensional offsets hugging one corner:\n\n"
    "```anchors\n"
    "  - alias: corner-side\n"
    "    side: SE\n"
    "    offset: [1, 1]\n"
    "  - alias: corner-back\n"
    "    side: SE\n"
    "    offset: [-1, -1]\n```"
    "\n"
    "Grip anchor points are all empty by default. "
    "They can be occupied, and connected, using `tweaks`."]
   [:parameter [:ports]
    {:heading-template "Special section %s"
     :default {}
     :parse-fn (parse/map-of keyword
                 (base/parser-with-defaults port/raws))
     :validate [(spec/map-of
                  ::valid/alias
                  (base/delegated-validation port/raws))]}
    "This section describes ports, including sockets in the case walls to "
    "contain electronic receptacles for signalling connections and other "
    "interfaces. Each port gets its own subsection. "
    "Ports are documented in detail [here](options-ports.md)."]
   [:section [:wrist-rest]
    "An optional extension to support the user’s wrist."]
   [:parameter [:wrist-rest :include]
    {:default false :parse-fn boolean}
    "If `true`, include a wrist rest with the keyboard."]
   [:parameter [:wrist-rest :style]
    {:default :threaded :parse-fn keyword :validate [::valid/wrist-rest-style]}
    "The style of the wrist rest. Available styles are:\n\n"
    "- `threaded`: threaded fasteners connect the case and wrist rest.\n"
    "- `solid`: the case and wrist rest are joined together by `tweaks` "
    "as a single piece of plastic."]
   [:parameter [:wrist-rest :preview]
    {:default false :parse-fn boolean}
    "Preview mode. If `true`, this puts a model of the wrist rest in the same "
    "OpenSCAD file as the case. That model is simplified, intended for gauging "
    "distance, not for printing."]
   [:section [:wrist-rest :anchoring]
    stock/anchoring-documentation " "
    "For wrist rests, the vertical component of the anchor’s position is "
    "ignored."]
   [:parameter [:wrist-rest :anchoring :anchor]
    stock/anchor-metadata stock/anchor-documentation]
   [:parameter [:wrist-rest :anchoring :side]
    stock/anchor-side-metadata stock/anchor-side-documentation]
   [:parameter [:wrist-rest :anchoring :segment]
    stock/anchor-segment-metadata stock/anchor-segment-documentation]
   [:parameter [:wrist-rest :anchoring :offset]
    stock/anchor-2d-vector-metadata stock/anchor-2d-offset-documentation]
   [:parameter [:wrist-rest :plinth-height]
    {:default 1 :parse-fn num}
    "The average height of the plastic plinth in mm, at its upper lip."]
   [:section [:wrist-rest :shape]
    "The wrist rest needs to fit the user’s hand."]
   [:section [:wrist-rest :shape :spline]
    "The horizontal outline of the wrist rest is a closed spline."]
   [:parameter [:wrist-rest :shape :spline :main-points]
    {:default [{:position [0 0]} {:position [1 0]} {:position [1 1]}]
     :parse-fn parse/nameable-spline
     :validate [::valid/nameable-spline]}
    "A list of nameable points, in clockwise order. The spline will pass "
    "through all of these and then return to the first one. Each point can "
    "have two properties:\n\n"
    "- Mandatory: `position`. A pair of coordinates, in mm, relative to other "
    "points in the list.\n"
    "- Optional: `alias`. A name given to the specific point, for the purpose "
    "of placing yet more things in relation to it."]
   [:parameter [:wrist-rest :shape :spline :resolution]
    {:default 1 :parse-fn num}
    "The amount of vertices per main point. The default is 1. If 1, only the "
    "main points themselves will be used, giving you full control. A higher "
    "number gives you smoother curves.\n\n"
    "If you want the closing part of the curve to look smooth in high "
    "resolution, position your main points carefully.\n\n"
    "Resolution parameters, including this one, can be disabled in the main "
    "`resolution` section."]
   [:section [:wrist-rest :shape :lip]
    "The lip is the uppermost part of the plinth, lining and supporting the "
    "edge of the pad. Its dimensions are described here in mm away from the "
    "pad."]
   [:parameter [:wrist-rest :shape :lip :height]
    {:default 1 :parse-fn num} "The vertical extent of the lip."]
   [:parameter [:wrist-rest :shape :lip :width]
    {:default 1 :parse-fn num} "The horizontal width of the lip at its top."]
   [:parameter [:wrist-rest :shape :lip :inset]
    {:default 0 :parse-fn num}
    "The difference in width between the top and bottom of the lip. "
    "A small negative value will make the lip thicker at the bottom. This is "
    "recommended for fitting a silicone mould."]
   [:section [:wrist-rest :shape :pad]
    "The top of the wrist rest should be printed or cast in a soft material, "
    "such as silicone rubber."]
   [:section [:wrist-rest :shape :pad :surface]
    "The upper surface of the pad, which will be in direct contact with "
    "the user’s palm or wrist."]
   [:section [:wrist-rest :shape :pad :height]
    "The piece of rubber extends a certain distance up into the air and down "
    "into the plinth. All measurements in mm."]
   [:parameter [:wrist-rest :shape :pad :height :surface-range]
    {:default 1 :parse-fn num}
    "The vertical range of the upper surface. Whatever values are in "
    "a heightmap will be normalized to this scale."]
   [:parameter [:wrist-rest :shape :pad :height :lip-to-surface]
    {:default 1 :parse-fn num}
    "The part of the rubber pad between the top of the lip and the point "
    "where the heightmap comes into effect. This is useful if your heightmap "
    "itself has very low values at the edges, such that moulding and casting "
    "it without a base would be difficult."]
   [:parameter [:wrist-rest :shape :pad :height :below-lip]
    {:default 1 :parse-fn num}
    "The depth of the rubber wrist support, measured from the top of the lip, "
    "going down into the plinth. This part of the pad just keeps it in place."]
   [:section [:wrist-rest :shape :pad :surface :edge]
    "The edge of the pad can be rounded."]
   [:parameter [:wrist-rest :shape :pad :surface :edge :inset]
    {:default 0 :parse-fn num}
    "The horizontal extent of softening. This cannot be more than half the "
    "width of the outline, as determined by `main-points`, at its narrowest "
    "part."]
   [:parameter [:wrist-rest :shape :pad :surface :edge :resolution]
    {:default 1 :parse-fn num}
    "The number of faces on the edge between horizontal points.\n\n"
    "Resolution parameters, including this one, can be disabled in the main "
    "`resolution` section."]
   [:section [:wrist-rest :shape :pad :surface :heightmap]
    "The surface can optionally be modified by the [`surface()` function]"
    "(https://en.wikibooks.org/wiki/OpenSCAD_User_Manual/"
    "Other_Language_Features#Surface), which requires a heightmap file."]
   [:parameter [:wrist-rest :shape :pad :surface :heightmap :include]
    {:default false :parse-fn boolean}
    "If `true`, use a heightmap. The map will intersect the basic pad "
    "polyhedron."]
   [:parameter [:wrist-rest :shape :pad :surface :heightmap :filepath]
    {:default (file ".." ".." "resources" "heightmap" "default.dat")}
    "The file identified here should contain a heightmap in a format OpenSCAD "
    "can understand. The path should also be resolvable by OpenSCAD."]
   [:section [:wrist-rest :rotation]
    "The wrist rest can be rotated to align its pad with the user’s palm."]
   [:parameter [:wrist-rest :rotation :pitch]
    {:default 0 :parse-fn parse/compass-incompatible-angle}
    "Tait-Bryan pitch."]
   [:parameter [:wrist-rest :rotation :roll]
    {:default 0 :parse-fn parse/compass-incompatible-angle}
    "Tait-Bryan roll."]
   [:parameter [:wrist-rest :mounts]
    {:heading-template "Special section %s"
     :default []
     :parse-fn (parse/tuple-of (base/parser-with-defaults restmnt/raws))
     :validate [(spec/coll-of (base/delegated-validation restmnt/raws))]}
    "A list of mounts for threaded fasteners. Each such mount will include at "
    "least one cuboid block for at least one screw that connects the wrist "
    "rest to the case. "
    "This section is used only with the `threaded` style of wrist rest."]
   [:section [:wrist-rest :sprues]
    "Holes in the bottom of the plinth. You pour liquid rubber through these "
    "holes when you make the rubber pad. Sprues are optional, but the general "
    "recommendation is to have two of them if you’re going to be casting your "
    "own pads. That way, air can escape even if you accidentally block one "
    "sprue with a low-viscosity silicone."]
   [:parameter [:wrist-rest :sprues :include]
    {:default false :parse-fn boolean}
    "If `true`, include sprues."]
   [:parameter [:wrist-rest :sprues :inset]
    {:default 0 :parse-fn num}
    "The horizontal distance between the perimeter of the wrist rest and the "
    "default position of each sprue."]
   [:parameter [:wrist-rest :sprues :diameter]
    {:default 1 :parse-fn num}
    "The diameter of each sprue."]
   [:parameter [:wrist-rest :sprues :positions]
    {:default [] :parse-fn parse/anchored-2d-positions
     :validate [::valid/anchored-2d-list]}
    "The positions of all sprues. This is a list where each item needs an "
    "`anchor` naming a main point in the spline. You can add an optional "
    "two-dimensional `offset`."]
   [:section [:wrist-rest :bottom-plate]
    "The equivalent of the case `bottom-plate` parameter. If included, "
    "a bottom plate for a wrist rest uses the `thickness` configured for "
    "the bottom of the case.\n"
    "\n"
    "Bottom plates for the wrist rests have no ESDS electronics to "
    "protect but serve other purposes: Covering nut pockets, silicone "
    "mould-pour cavities, and plaster or other dense material poured into "
    "plinths printed without a bottom shell."]
   [:parameter [:wrist-rest :bottom-plate :include]
    {:default false :parse-fn boolean}
    "Whether to include a bottom plate for each wrist rest."]
   [:parameter [:wrist-rest :bottom-plate :inset]
    {:default 0 :parse-fn num}
    "The horizontal distance between the perimeter of the wrist rest and the "
    "default position of each threaded fastener connecting it to its "
    "bottom plate."]
   [:parameter [:wrist-rest :bottom-plate :fastener-positions]
    {:default [] :parse-fn parse/anchored-2d-positions
     :validate [::valid/anchored-2d-list]}
    "The positions of threaded fasteners used to attach the bottom plate to "
    "its wrist rest. The syntax of this parameter is precisely the same as "
    "for the case’s bottom-plate fasteners. Corners are ignored and the "
    "starting position is inset from the perimeter of the wrist rest by the "
    "`inset` parameter above, before any offset stated here is applied.\n\n"
    "Other properties of these fasteners are determined by settings for the "
    "case."]
   [:parameter [:wrist-rest :mould-thickness]
    {:default 1 :parse-fn num}
    "The thickness in mm of the walls and floor of the mould to be used for "
    "casting the rubber pad."]
   [:section [:resolution]
    "Settings for the amount of detail on curved surfaces. More specific "
    "resolution parameters are available in other sections."]
   [:parameter [:resolution :include]
    {:default false :parse-fn boolean}
    "If `true`, apply resolution parameters found throughout the "
    "configuration. Otherwise, use defaults built into this application, "
    "its libraries and OpenSCAD. The defaults are generally conservative, "
    "providing quick renders for previews."]
   [:parameter [:resolution :minimum-face-size]
    {:default 1, :parse-fn num, :validate [::appdata/minimum-face-size]}
    "File-wide OpenSCAD minimum face size in mm."]
   [:section [:dfm]
    "Settings for design for manufacturability (DFM)."]
   [:parameter [:dfm :error-general]
    {:default 0 :parse-fn num}
    "A measurement in mm of errors introduced to negative space in the xy "
    "plane by slicer software and the printer you will use.\n"
    "\n"
    "The default value is zero. An appropriate value for a typical slicer "
    "and FDM printer with a 0.5 mm nozzle would be about -0.5 mm.\n"
    "\n"
    "This application will try to compensate for the error, though only for "
    "certain sensitive inserts, not for the case as a whole."]
   [:section [:dfm :keycaps]
    "Measurements of error, in mm, for parts of keycap models. "
    "This is separate from `error-general` because it’s especially important "
    "to have a tight fit between switch sliders and cap stems, and the "
    "size of these details is usually comparable to an FDM printer nozzle.\n"
    "\n"
    "If you will not be printing caps, ignore this section."]
   [:parameter [:dfm :keycaps :error-stem-positive]
    {:default (:error-stem-positive capdata/option-defaults) :parse-fn num}
    "Error on the positive components of stems on keycaps, such as the "
    "entire stem on an ALPS-compatible cap."]
   [:parameter [:dfm :keycaps :error-stem-negative]
    {:default (:error-stem-negative capdata/option-defaults) :parse-fn num}
    "Error on the negative components of stems on keycaps, such as the "
    "cross on an MX-compatible cap."]
   [:section [:dfm :central-housing]
    "DFM for the central housing."]
   [:parameter [:dfm :central-housing :sections]
    {:default [] :parse-fn vec :validate [(spec/coll-of number?)]}
    "A series of coordinates in mm on the x axis. If a central housing is "
    "included and this parameter is populated, the program will produce "
    "additional model outputs where the central housing is "
    "sectioned off at the specified coordinates. Each piece is rendered "
    "standing on its plain crossection.\n"
    "\n"
    "The purpose of this parameter is to simplify the printing of a central "
    "housing piece by piece, with each piece standing on end to minimize the "
    "need for support. This is particularly useful when the central housing "
    "interface is irregular or the total length of the housing is greater "
    "than the vertical build volume of your printer.\n"
    "\n"
    "This feature provides no mechanism for joining the different sections "
    "after printing. In a thermoplastic, you should be able to join them "
    "by running a soldering iron along the interior seams between sections.\n"
    "\n"
    "Coordinates entered here are not reflected. When picking them out, "
    "consider that any bottom plates for the central housing, which are "
    "reflected, meet in the middle at x = 0. "
    "If you use `[0]` for this parameter, both plates and sections will meet "
    "on the same line, weakening the structure."]
   [:section [:dfm :bottom-plate]
    "DFM for bottom plates."]
   [:parameter [:dfm :bottom-plate :fastener-plate-offset]
    {:default 0 :parse-fn num}
    "A vertical offset in mm for the placement of screw holes in bottom "
    "plates. Without a slight negative offset, slicers will tend to make the "
    "holes too wide for screw heads to grip the plate securely.\n"
    "\n"
    "Notice this will not affect how screw holes are cut into the case."]
   [:section [:mask]
    "A box limits the entire shape, cutting off any projecting by-products of "
    "the algorithms. By resizing and moving this box, you can select a "
    "subsection for printing. You might want this while you are printing "
    "prototypes for a new style of switch, MCU support etc."]
   [:parameter [:mask :size]
    {:default [1000 1000 1000] :parse-fn vec :validate [::tarmi-core/point-3d]}
    "The size of the mask in mm. By default, `[1000, 1000, 1000]`."]
   [:parameter [:mask :center]
    {:default [0 0 500] :parse-fn vec :validate [::tarmi-core/point-3d]}
    "The position of the center point of the mask. By default, `[0, 0, 500]`, "
    "which is supposed to mask out everything below ground level. If you "
    "include bottom plates, their thickness will automatically affect the "
    "placement of the mask beyond what you specify here."]])
