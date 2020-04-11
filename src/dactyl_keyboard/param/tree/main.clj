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
            [dactyl-keyboard.param.schema :as schema]
            [dactyl-keyboard.param.stock :as stock]
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
(spec/def ::rows (spec/map-of ::schema/flexcoord ::individual-row))
(spec/def ::individual-column (spec/keys :opt-un [::rows ::parameters]))
(spec/def ::columns (spec/map-of ::schema/flexcoord ::individual-column))
(spec/def ::overrides (spec/keys :opt-un [::columns ::parameters]))

(def raws
  "A flat version of the specification for a complete user configuration.
  This absorbs major subsections from elsewhere."
  [["# General configuration options\n\n"
    "Each heading in this document represents a recognized configuration key "
    "in the main body of a YAML file for a DMOTE variant. Other documents "
    "cover special sections of this one in more detail."]
   [:parameter [:reflect]
    {:default false :parse-fn boolean}
    "If `true`, mirror the case, producing one version for the right hand "
    "and another for the left. The two halves will be almost identical: "
    "Only chiral parts, such as threaded holes, are exempt from mirroring "
    "with `reflect`.\n\n"
    "You can use this option to make a ‘split’ keyboard, though the two "
    "halves are typically connected by a signalling cable, by a rigid "
    "`central-housing`, or by one or more rods anchored to "
    "some feature such as `rear-housing` or `back-plate`."]
   [:section [:keys]
    "Keys, that is keycaps and electrical switches, are not the main focus of "
    "this application, but they influence the shape of the case."]
   [:parameter [:keys :preview]
    {:default false :parse-fn boolean}
    "If `true`, include models of the keycaps in place on the keyboard. This "
    "is intended for illustration as you work on a design, not for printing."]
   [:parameter [:keys :styles]
    {:default {:default {}} :parse-fn schema/keycap-map
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
     :parse-fn (schema/map-of keyword
                 (base/parser-with-defaults cluster/raws))
     :validate [(spec/map-of
                  ::schema/key-cluster
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
       :parse-fn (schema/map-of
                   keyword
                   (schema/map-like
                     {:parameters rep
                      :columns
                       (schema/map-of
                         schema/keyword-or-integer
                         (schema/map-like
                           {:parameters rep
                            :rows
                              (schema/map-of
                                schema/keyword-or-integer
                                (schema/map-like {:parameters rep}))}))}))
       :validate [(spec/map-of ::schema/key-cluster ::overrides)]})
    "Starting here, you gradually descend from the global level "
    "toward the key level."]
   [:parameter [:secondaries]
    {:default {}
     :parse-fn schema/named-secondary-positions,
     :validate [::schema/named-secondary-positions]}
    "A map where each item provides a name for a position in space. "
    "Such positions exist in relation to other named features of the keyboard "
    "and can themselves be used as named features: Typically as supplementary "
    "targets for `tweaks`, which are defined below.\n"
    "\n"
    "An example:\n\n"
    "```secondaries:\n"
    "  s0:\n"
    "    anchor: f0\n"
    "    side: NNE\n"
    "    segment: 3\n"
    "    offset: [0, 0, 10]\n```"
    "\n"
    "This example gives the name `s0` to a point 10 mm above a key or "
    "some other feature named `f0`, which must be defined elsewhere.\n"
    "\n"
    "A `side` and `segment` are useful mainly with key aliases. "
    "An `offset` is applied late, i.e. in the overall coordinate system, "
    "following any transformations inherent to the anchor."]
   [:section [:case]
    "Much of the keyboard case is generated from the `wall` parameters "
    "described [here](options-nested.md). "
    "This section deals with lesser features of the case."]
   [:parameter [:case :key-mount-thickness]
    {:default 1 :parse-fn num}
    "The thickness in mm of each switch key mounting plate."]
   [:parameter [:case :key-mount-corner-margin]
    {:default 1 :parse-fn num}
    "The thickness in mm of an imaginary “post” at each corner of each key "
    "mount. Copies of such posts project from the key mounts to form the main "
    "walls of the case.\n"
    "\n"
    "`key-mount-thickness` is similarly the height of each post."]
   [:parameter [:case :web-thickness]
    {:default 1 :parse-fn num}
    "The thickness in mm of the webbing between switch key "
    "mounting plates, and of the rear housing’s walls and roof."]
   [:section [:case :central-housing]
    "A central housing can occupy the space between the key clusters, "
    "providing a rigid mechanical connection and space for an MCU.\n\n"
    "When present, a central housing naturally determines the position of each "
    "other part of the keyboard: Key clusters on each side should be anchored "
    "to points on the central housing, instead of being anchored to the "
    "origin."]
   [:parameter [:case :central-housing :include]
    {:default false :parse-fn boolean}
    "If this and `reflect` are both true, add a central housing."]
   [:parameter [:case :central-housing :preview]
    {:default false :parse-fn boolean}
    "If `true`, include the central housing when rendering each half of the "
    "main body of the keyboard."]
   [:section [:case :central-housing :shape]
    "The shape of the central housing determines, in part, how it connects "
    "to the rest of the keyboard, including the general shape of an optional "
    "adapter. "
    "The adapter is also influenced, in part, by settings devoted to it, "
    "in the next section.\n"
    "\n"
    "Assuming that adapters are included, you can think of a keyboard with "
    "a central housing as a row of buildings: Terraced housing, also known "
    "as row houses. "
    "In this metaphor, the series of vertices placed with the `interface` "
    "parameter in this section, and the `width`, determine the shape of each "
    "house. "
    "The adapters are the gables on either side of a particular house, "
    "up against which two others house are built: The two mirrored halves of "
    "the main body."]
   [:parameter [:case :central-housing :shape :width]
    {:default 1 :parse-fn num}
    "The approximate extent of the housing itself, on the x axis, in mm."]
   [:parameter [:case :central-housing :shape :interface]
    {:default [] :parse-fn schema/central-housing-interface
     :validate [::schema/central-housing-interface]}
    "The `interface` setting is essentially a list of points in space. "
    "Each of these points can influence the shape of the central housing "
    "itself, its adapter, and/or the shape of its bottom plate.\n"
    "\n"
    "The question of which bodies will include each item in the list is "
    "solved by the properties of each item in the list, including "
    "the position of each point. Specifically, the `base` z-acis `offset` "
    "coordinate (see below) controls the default values of the following "
    "two optional properties if you omit them:\n"
    "\n"
    "* `at-ground`: If `true`, or if omitted and the `base` z coordinate is "
    "not positive, the item will shape the bottom plate for the central "
    "housing, if any.\n"
    "* `above-ground`: If `true`, or if the `base` z coordinate is "
    "not negative, the item will shape the main body of the central "
    "housing itself. If an adapter is included, the item will shape that "
    "too.\n"
    "\n"
    "Those items which are “above ground” determine the shape of each outer "
    "edge of the housing, at the interface between the housing itself and "
    "the rest of the case. They do this as an ordered list of "
    "vertices, each defined primarily by an offset in a little section under "
    "the `base` key. The following keys are allowed in the value of `base`:\n"
    "\n"
    "* `offset` (required): A three-dimensional position in mm. This offset "
    "is from a point that is already displaced from the origin of the "
    "coordinate system by exactly one half of the `width` set above. "
    "The last coordinate (z coordinate) in this value determines the default "
    "settings for both `at-ground` and `above-ground`, above.\n"
    "* `left-hand-alias` (optional): A symbolic name for this point on the "
    "interface. Specifically, `left-hand-alias` will identify the point on "
    "the left-hand edge of the housing in its standard orientation.\n"
    "* `right-hand-alias` (optional): A symbolic name for the point on the "
    "right-hand-side edge. Because the right-hand side of the main body of "
    "the keyboard is the source of the left-hand side (with `reflect`, hence "
    "with a central housing), a `right-hand-alias` has more general utility.\n"
    "\n"
    "In addition to all properties named thus far, each item in the "
    "`interface` list may also include an `adapter` section. "
    "This section, and everything in it, is optional and relates to the "
    "central housing adapter feature. "
    "Briefly, the adapter fits precisely onto each interface of the housing. "
    "Here’s what the `adapter` section can contain:"
    "\n"
    "* `offset`: Not to be confused with the base offset, this one is also "
    "three-dimensional and in mm. It’s added to the width of the adapter "
    "and the base position.\n"
    "* `alias`: A symbolic name for this point on the side of the adapter "
    "facing away from the central housing. Notice that the side facing toward "
    "the central housing is coterminous with the interface itself, so the "
    "corresponding point on it is named by `right-hand-alias`.\n"
    "\n"
    "The following example covers only one vertex on the housing and one "
    "corresponding vertex on its adapter, and is therefore not a complete "
    "interface. That said, it does show all of the properties one item in "
    "the `interface` list can have, with realistic values.\n"
    "\n"
    "```\n"
    "  interface:\n"
    "  - at-ground: true\n"
    "    above-ground: true\n"
    "    base:\n"
    "      offset: [0, 20, 40]\n"
    "      left-hand-alias: housing-side-1L\n"
    "      right-hand-alias: housing-side-1R\n"
    "    adapter:\n"
    "      offset: [10, 0, 0]\n"
    "      alias: adapter-side-1\n```"
    "\n"
    "In this example, the vertex named `adapter-side-1` will be placed 10 mm "
    "plus the overall width of the adapter away from `housing-side-1R`, with "
    "the body of the adapter covering the intervening distance, so that the "
    "shell of the adapter touches both vertices, and the housing only touches "
    "one. Given that the `base` z cooridinate is zero, both `at-ground` and "
    "`above-ground` have their default values and are therefore redundant.\n"
    "\n"
    "Aliases for vertices on the interface itself, as opposed to the adapter, "
    "are useful mainly when you anchor features like an MCU holder to the "
    "central housing. Doing so, you need to be aware that the central housing "
    "has both symmetric and asymmetric properties. Its basic shape, including "
    "everything you can determine with `interface`, will be bilaterally "
    "symmetric. Adapters and their fasteners, and bottom plates, are also "
    "symmetric, except for threaded holes. By contrast, "
    "central-housing-specific `tweaks` and MCU holders will only appear on "
    "the specific side of the housing that you indicate, breaking symmetry.\n"
    "\n"
    "Here’s an example of a valid, minimal, triangular profile, like a little "
    "ridge tent:\n"
    "\n"
    "```\n"
    "  interface:\n"
    "  - base:\n"
    "      offset: [0, -10, 0]\n"
    "  - base:\n"
    "      offset: [4, 0, 10]\n"
    "  - base:\n"
    "      offset: [0, 10, 0]\n```"
    "\n"
    "In this example, the high point in the middle is offset on the x axis, "
    "giving the edge of the central housing a slant as seen from infinite y. "
    "Notice also that the lowest z coordinate is 0, "
    "which places this central housing on the floor, inside the `mask`. "
    "This is not required. A lower z coordinate will cause the `mask` to open "
    "the bottom of the central housing, which is usually more practical for "
    "running wires through the keyboard. "
    "Notice that if you do use a lower z coordinate and you still want "
    "the two lower points to be included in the body of the central housing, "
    "you will need to set `above-ground` to `true`, as an explicit addition "
    "to the two items at the extremes.\n"
    "\n"
    "It may not be obvious why `at-ground` and `above-ground` are mutually "
    "independent of one another and of the `base` z coordinate that gives them "
    "their default values. The main reason for this design is to allow points "
    "that are “ethereal”, appearing in neither body. Ethereal points are "
    "similar to `secondaries`, but unlike `secondaries`, they are influenced "
    "by central-housing width settings and not tied to other points on the "
    "interface. They are intended as anchors for key clusters and tweaks, "
    "where their responsiveness to width alone comes in handy as you work out "
    "the precise shape of the housing.\n"
    "\n"
    "The DMOTE application uses the `interface` to construct OpenSCAD "
    "polyhedrons. OpenSCAD and CGAL have many requirements upon polyhedrons "
    "and a carelessly constructed interface will violate them, resulting in "
    "a central housing that cannot be rendered or printed. "
    "As a rule of thumb, define your interface moving clockwise from the "
    "point of view of positive infinite x, like the tent example. "
    "Be especially careful with `adapter` offsets on the y and z axes, "
    "and try to keep the housing itself more than twice as broad as its wall "
    "thickness.\n"]
   [:section [:case :central-housing :adapter]
    "The central housing can connect to key clusters through an adapter: "
    "A part that is shaped like the central housing and extends the "
    "rest of the case to meet the central housing at an interface.\n\n"
    "Using `case` → `tweaks`, points on the adapter should be connected to key "
    "cluster walls to close the case around the adapter but leave the adapter "
    "itself open."]
   [:parameter [:case :central-housing :adapter :include]
    {:default false :parse-fn boolean}
    "If this is `true`, add an adapter for the central housing."]
   [:parameter [:case :central-housing :adapter :width]
    {:default 1 :parse-fn num}
    "The approximate width of the adapter on each side of the central housing, "
    "along its axis (the x axis). Individual points on the adapter can be "
    "offset from this width using the `interface` list."]
   [:section [:case :central-housing :adapter :lip]
    "To stabilize the connection between the central housing and the adapter, "
    "the interface between them can include an interior lip."]
   [:parameter [:case :central-housing :adapter :lip :include]
    {:default false :parse-fn boolean}
    "If `true`, attach a lip to the central housing."]
   [:parameter [:case :central-housing :adapter :lip :thickness]
    {:default 1 :parse-fn num}
    "The thickness of the lip at each point along it, in mm."]
   [:section [:case :central-housing :adapter :lip :width]
    "The lip extends in both directions from the edge of the central housing: "
    "Both into the adapter (the “outer” part) and into the housing itself "
    "(the “inner” part), where it grows out of the inner wall with an optional "
    "taper. The total width of the lip is the sum of all these sections."]
   [:parameter [:case :central-housing :adapter :lip :width :outer]
    {:default 1 :parse-fn num}
    "The distance the lip protrudes outside the central housing and thence "
    "into the adapter, in mm."]
   [:parameter [:case :central-housing :adapter :lip :width :inner]
    {:default 1 :parse-fn num}
    "The width of the lip inside the central housing, before it starts to "
    "taper, in mm."]
   [:parameter [:case :central-housing :adapter :lip :width :taper]
    {:default 0 :parse-fn num}
    "The width of a taper from the inner portion of the lip to the inner "
    "wall of the central housing, in mm.\n\n"
    "The default value, zero, produces a right-angled transition. The higher "
    "the value, the more gentle the transition becomes."]
   [:section [:case :central-housing :adapter :fasteners]
    "To connect the central housing and the adapter, threaded fasteners "
    "can be driven through the wall of either, into receivers extending "
    "from the other."]
   [:parameter [:case :central-housing :adapter :fasteners :bolt-properties]
    stock/implicit-threaded-bolt-metadata
    stock/threaded-bolt-documentation]
   [:parameter [:case :central-housing :adapter :fasteners :positions]
    {:default []
     :parse-fn schema/central-housing-normal-positions
     :validate [::schema/central-housing-normal-positions]}
    "A list of places where threaded fasteners will go through the wall.\n\n"
    "Each item in this list is a map with three mandatory keys:\n"
    "\n"
    "* `starting-point`: The name of a vertex on the interface. This must be "
    "a `base` point, not a point on the far side of the adapter.\n"
    "* `radial-offset`: A distance in mm from the starting point, along the "
    "interface.\n"
    "* `lateral-offset`: A distance in mm from the starting point, along the "
    "axis of the central housing, which is the x axis.\n"
    "\n"
    "Both of the two offsets are numbers: Simple scalars. Each of them can "
    "be either positive or negative, but not zero. Zero is not allowed "
    "because these numbers have side effects:\n"
    "\n"
    "* The radial offset follows the interface in the original order of its "
    "definition. A positive radial offset moves along the interface "
    "in the direction of that original order and a negative number moves "
    "the other way around, “against” the interface. "
    "A non-zero offset is needed here to identify that direction of travel "
    "itself, which in turn is used to identify the next vertex on the "
    "interface. Take care not to enter a number larger than the distance to "
    "the next vertex.\n"
    "* The lateral offset determines which model the fastener will be a part "
    "of, as negative space. A positive lateral offset puts the hole through "
    "the wall of the adapter, and a negative offset puts the hole through "
    "the wall of the central housing. A non-zero offset is needed to pick a "
    "side, and it has to be large enough to secure the fastener on that side, "
    "so it won’t intersect the oppsite side.\n"
    "\n"
    "The following example map will start from a vertex named `apex` "
    "and proceed from there, 5 mm forward toward the next point after `apex` "
    "and 4 mm to the side, where a hole for a fastener will be left in the "
    "wall of each end of the central housing.\n"
    "\n"
    "```positions:\n"
    "  - starting-point: apex\n"
    "    radial-offset: 5\n"
    "    lateral-offset: -4\n```"
    "\n"
    "In addition to these mandatory properties, each fastener position can "
    "include a more advanced property: `direction-point`. This allows you to "
    "name an arbitrary anchor point anywhere on the keyboard, overriding the "
    "side effect of `radial-offset` in choosing a neighbouring point on the "
    "interface. "
    "This feature may help resolve problems with sloping adapters or other "
    "obstacles, but consider it experimental. More detailed overrides for "
    "placement may be introduced in a future version, if they are needed."]
   [:section [:case :central-housing :adapter :receivers]
    "One receiver is created for each of the `fasteners`. Each of these "
    "has a threaded hole to keep the fastener in place. Like the adapter lip, "
    "receivers extend from the inside wall, but receivers are anchored across "
    "the interface from their respective fasteners: A positive "
    "`lateral-offset`, above, extends a receiver from the central housing "
    "into the adapter."]
   [:section [:case :central-housing :adapter :receivers :thickness]
    "The thickness of material in various parts of each receiver."]
   [:parameter [:case :central-housing :adapter :receivers :thickness :rim]
    {:default 1 :parse-fn num}
    "The maximum thickness of the loop of each receiver where it grabs the "
    "fastener, in mm."]
   [:parameter [:case :central-housing :adapter :receivers :thickness :bridge]
    {:default 1 :parse-fn num}
    "The thickness of the main body of each receiver where it extends across "
    "the interface, in the plane of the housing wall, in mm."]
   [:section [:case :central-housing :adapter :receivers :width]
    "This section is analogous to lip `width`. The “outer” width of "
    "each receiver is a function of its fastener’s lateral offset and cannot "
    "be configured here."]
   [:parameter [:case :central-housing :adapter :receivers :width :inner]
    {:default 1 :parse-fn num}
    "The width of the receiver at its base, before it starts to taper, in mm."]
   [:parameter [:case :central-housing :adapter :receivers :width :taper]
    {:default 0 :parse-fn num}
    "The width of a taper, as with the lip."]
   [:section [:case :central-housing :bottom-plate]
    "Any bottom plating for the case will extend to the midpoint of the "
    "central housing, on the assumption that bottom-plating anchors will "
    "be used to attach it there."]
   [:section [:case :central-housing :bottom-plate :projections]
    "To facilitate printing a central housing standing on its edge, or to add "
    "strength, you can extend bottom-plating anchors onto the nearest wall, "
    "via a convex hull of each anchor and its projection. The result is an "
    "internal chamfer resembling a primitive fillet."]
   [:parameter [:case :central-housing :bottom-plate :projections :include]
    {:default false :parse-fn boolean}
    "If `true`, extend each bottom-plating anchor."]
   [:parameter [:case :central-housing :bottom-plate :projections :scale]
    {:default [1 1] :parse-fn vec :validate [::tarmi-core/point-2d]}
    "The scale of each projection, as a 2-tuple of horizontal and vertical "
    "factors. The horizontal factor controls the width of the projection and "
    "the vertical factor its height. The length of the projection is fixed "
    "at the distance between the center of the anchor and the outermost "
    "part of its shell."]
   [:parameter [:case :central-housing :bottom-plate :fastener-positions]
    {:default [] :parse-fn schema/projecting-2d-positions
     :validate [::schema/projecting-2d-list]}
    "The positions of threaded fasteners used to attach the bottom plate to "
    "the body of the central housing. In addition to the properties permitted "
    "in similar lists of such anchors, the central housing permits a "
    "`direction`, formulated as a point on the compass or an angle in "
    "radians. This property controls the facing of a projection. Typically, "
    "you want it facing the central housing’s nearest wall."]
   [:parameter [:case :central-housing :tweaks]
    {:default [] :parse-fn schema/case-tweak-map
     :validate [::schema/tweak-name-map]}
    "Precisely like `case` → `tweaks` but just for the central housing."]
   [:section [:case :rear-housing]
    "The furthest row of a key cluster can be extended into a rear housing "
    "for the MCU and various other features."]
   [:parameter [:case :rear-housing :include]
    {:default false :parse-fn boolean}
    "If `true`, add a rear housing. Please arrange case walls so as not to "
    "interfere, by removing them along the far side of the last row of key "
    "mounts in the indicated cluster."]
   [:parameter [:case :rear-housing :wall-thickness]
    {:default 1 :parse-fn num}
    "The horizontal thickness in mm of the walls."]
   [:parameter [:case :rear-housing :roof-thickness]
    {:default 1 :parse-fn num}
    "The vertical thickness in mm of the flat top."]
   [:section [:case :rear-housing :position]
    "Where to put the rear housing. Unlike a central housing, a rear housing "
    "is placed in relation to a key cluster. By default, it sits all along "
    "the far (north) side of the `main` cluster but has no depth."]
   [:parameter [:case :rear-housing :position :cluster]
    {:default :main :parse-fn keyword :validate [::schema/key-cluster]}
    "The key cluster at which to anchor the housing."]
   [:section [:case :rear-housing :position :offsets]
    "Modifiers for where to put the four sides of the roof. All are in mm."]
   [:parameter [:case :rear-housing :position :offsets :north]
    {:default 0 :parse-fn num}
    "The extent of the roof on the y axis; its horizontal depth."]
   [:parameter [:case :rear-housing :position :offsets :west]
    {:default 0 :parse-fn num}
    "The extent on the x axis past the first key in the row."]
   [:parameter [:case :rear-housing :position :offsets :east]
    {:default 0 :parse-fn num}
    "The extent on the x axis past the last key in the row."]
   [:parameter [:case :rear-housing :position :offsets :south]
    {:default 0 :parse-fn num}
    "The horizontal distance in mm, on the y axis, between the furthest key "
    "in the row and the roof of the rear housing."]
   [:parameter [:case :rear-housing :height]
    {:default 0 :parse-fn num}
    "The height in mm of the roof, over the floor."]
   [:section [:case :rear-housing :fasteners]
    "Threaded bolts can run through the roof of the rear housing, making it a "
    "hardpoint for attachments like a stabilizer to connect the two halves of "
    "a split keyboard."]
   [:parameter [:case :rear-housing :fasteners :bolt-properties]
    stock/implicit-threaded-bolt-metadata
    stock/threaded-bolt-documentation]
   [:parameter [:case :rear-housing :fasteners :bosses]
    {:default false :parse-fn boolean}
    "If `true`, add nut bosses to the ceiling of the rear housing for each "
    "fastener. Space permitting, these bosses will have some play on the "
    "north-south axis, to permit adjustment of the angle of the keyboard "
    "halves under a stabilizer."]
   [:section [:case :rear-housing :fasteners :west]
    "A fastener on the inward-facing end of the rear housing."]
   [:parameter [:case :rear-housing :fasteners :west :include]
    {:default false :parse-fn boolean}
    "If `true`, include this fastener."]
   [:parameter [:case :rear-housing :fasteners :west :offset]
    {:default 0 :parse-fn num}
    "A one-dimensional offset in mm from the inward edge of the rear "
    "housing to the fastener. You probably want a negative number if any."]
   [:section [:case :rear-housing :fasteners :east]
    "A fastener on the outward-facing end of the rear housing. All parameters "
    "are analogous to those for `west`."]
   [:parameter [:case :rear-housing :fasteners :east :include]
    {:default false :parse-fn boolean}]
   [:parameter [:case :rear-housing :fasteners :east :offset]
    {:default 0 :parse-fn num}]
   [:section [:case :back-plate]
    "Given that independent movement of each half of a split keyboard is not "
    "useful, each half can include a mounting plate for a stabilizing rod. "
    "That is a straight piece of wood, aluminium, rigid plastic etc. to "
    "connect the two halves mechanically and possibly carry the wire that "
    "connects them electrically.\n\n"
    "This option is similar to rear housing, but the back plate block "
    "provides no interior space for an MCU etc. It is solid, with holes "
    "for threaded fasteners including the option of nut bosses. "
    "Its footprint is not part of a `bottom-plate`."]
   [:parameter [:case :back-plate :include]
    {:default false :parse-fn boolean}
    "If `true`, include a back plate block. This is not contingent upon "
    "`reflect`."]
   [:parameter [:case :back-plate :beam-height]
    {:default 1 :parse-fn num}
    "The nominal vertical extent of the back plate in mm. "
    "Because the plate is bottom-hulled to the floor, the effect "
    "of this setting is on the area of the plate above its holes."]
   [:section [:case :back-plate :fasteners]
    "Two threaded bolts run through the back plate."]
   [:parameter [:case :back-plate :fasteners :bolt-properties]
    stock/implicit-threaded-bolt-metadata
    stock/threaded-bolt-documentation]
   [:parameter [:case :back-plate :fasteners :distance]
    {:default 1 :parse-fn num}
    "The horizontal distance between the bolts."]
   [:parameter [:case :back-plate :fasteners :bosses]
    {:default false :parse-fn boolean}
    "If `true`, cut nut bosses into the inside wall of the block."]
   [:section [:case :back-plate :position]
    "The block is positioned in relation to a named feature."]
   [:parameter [:case :back-plate :position :anchor]
    {:default :origin :parse-fn keyword :validate [::schema/anchor]}
    "The name of a feature where the block will attach."]
   [:parameter [:case :back-plate :position :offset]
    stock/anchor-3d-vector-metadata
    stock/anchor-3d-offset-documentation]
   [:section [:case :bottom-plate]
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
    "that file to fill the interior gap.\n"]
   [:parameter [:case :bottom-plate :include]
    {:default false :parse-fn boolean}
    "If `true`, include a bottom plate for the case."]
   [:parameter [:case :bottom-plate :preview]
    {:default false :parse-fn boolean}
    "Preview mode. If `true`, put a model of the plate in the same file as "
    "the case it closes. Not for printing."]
   [:parameter [:case :bottom-plate :combine]
    {:default false :parse-fn boolean}
    "If `true`, combine wrist rests for the case and the bottom plate into a "
    "single model, when both are enabled. This is typically used with the "
    "`solid` style of wrest rest."]
   [:parameter [:case :bottom-plate :thickness]
    {:default 1 :parse-fn num}
    "The thickness (i.e. height) in mm of all bottom plates you choose to "
    "include. This covers plates for the case and for the wrist rest.\n"
    "\n"
    "The case will not be raised to compensate for this. Instead, the height "
    "of the bottom plate will be removed from the bottom of the main model so "
    "that it does not extend to z = 0."]
   [:section [:case :bottom-plate :installation]
    "How your bottom plate is attached to the rest of your case."]
   [:parameter [:case :bottom-plate :installation :style]
    {:default :threads :parse-fn keyword
     :validate [::schema/plate-installation-style]}
    "The general means of installation. This parameter has been reduced to a "
    "placeholder: The only available style is `threads`, signifying the use "
    "of threaded fasteners connecting the bottom plate to anchors in "
    "the body of the keyboard."]
   [:parameter [:case :bottom-plate :installation :dome-caps]
    {:default false :parse-fn boolean}
    "If `true`, terminate each anchor with a hemispherical tip. This is "
    "an aesthetic feature, primarily intended for externally visible anchors "
    "and printed threading. "
    "If all of your anchors are completely internal to the case, and/or you "
    "intend to tap the screw holes after printing, dome caps are wasteful at "
    "best and counterproductive at worst."]
   [:parameter [:case :bottom-plate :installation :thickness]
    {:default 1 :parse-fn num}
    "The thickness in mm of each wall of the anchor points for threaded "
    "fasteners."]
   [:section [:case :bottom-plate :installation :inserts]
    "You can use heat-set inserts in the anchor points.\n\n"
    "It is assumed that, as in Tom Short’s Dactyl-ManuForm, the inserts are "
    "largely cylindrical."]
   [:parameter [:case :bottom-plate :installation :inserts :include]
    {:default false :parse-fn boolean}
    "If `true`, make space for inserts."]
   [:parameter [:case :bottom-plate :installation :inserts :length]
    {:default 1 :parse-fn num}
    "The length in mm of each insert."]
   [:section [:case :bottom-plate :installation :inserts :diameter]
    "Inserts may vary in diameter across their length."]
   [:parameter [:case :bottom-plate :installation :inserts :diameter :top]
    {:default 1 :parse-fn num}
    "Top diameter in mm."]
   [:parameter [:case :bottom-plate :installation :inserts :diameter :bottom]
    {:default 1 :parse-fn num}
    "Bottom diameter in mm. This needs to be at least as large as the top "
    "diameter since the mounts for the inserts only open from the bottom."]
   [:section [:case :bottom-plate :installation :fasteners]
    "The type and positions of the threaded fasteners used to secure each "
    "bottom plate."]
   [:parameter [:case :bottom-plate :installation :fasteners :bolt-properties]
    stock/implicit-threaded-bolt-metadata
    stock/threaded-bolt-documentation]
   [:parameter [:case :bottom-plate :installation :fasteners :positions]
    {:default []
     :parse-fn schema/anchored-2d-positions
     :validate [::schema/anchored-2d-list]}
    "A list of places where threaded fasteners will connect the bottom plate "
    "to the rest of the case."]
   [:section [:case :leds]
    "Support for light-emitting diodes in the case walls."]
   [:parameter [:case :leds :include]
    {:default false :parse-fn boolean}
    "If `true`, cut slots for LEDs out of the case wall, facing "
    "the space between the two halves."]
   [:section [:case :leds :position]
    "Where to attach the LED strip."]
   [:parameter [:case :leds :position :cluster]
    {:default :main :parse-fn keyword :validate [::schema/key-cluster]}
    "The key cluster at which to anchor the strip."]
   [:parameter [:case :leds :amount]
    {:default 1 :parse-fn int} "The number of LEDs."]
   [:parameter [:case :leds :housing-size]
    {:default 1 :parse-fn num}
    "The length of the side on a square profile used to create negative space "
    "for the housings on a LED strip. This assumes the housings are squarish, "
    "as on a WS2818.\n"
    "\n"
    "The negative space is not supposed to penetrate the wall, just make it "
    "easier to hold the LED strip in place with tape, and direct its light. "
    "With that in mind, feel free to exaggerate by 10%."]
   [:parameter [:case :leds :emitter-diameter]
    {:default 1 :parse-fn num}
    "The diameter of a round hole for the light of an LED."]
   [:parameter [:case :leds :interval]
    {:default 1 :parse-fn num}
    "The distance between LEDs on the strip. You may want to apply a setting "
    "slightly shorter than the real distance, since the algorithm carving the "
    "holes does not account for wall curvature."]
   [:parameter [:case :tweaks]
    {:default [] :parse-fn schema/case-tweak-map
     :validate [::schema/tweak-name-map]}
    "Additional shapes. This is usually needed to bridge gaps between the "
    "walls of the key clusters. The expected value here is an arbitrarily "
    "nested structure starting with a map of names to lists.\n"
    "\n"
    "The names at the top level are arbitrary but should be distinct and "
    "descriptive. Their only technical significance lies in the fact that "
    "when you combine multiple configuration files, a later tweak will "
    "override a previous tweak if and only if they share the same name.\n"
    "\n"
    "Below the names, each item in each list can follow one of the following "
    "patterns:\n"
    "\n"
    "- A leaf node. This is a tuple of 1 to 4 elements specified below.\n"
    "- A map, representing an instruction to combine nested items in a "
    "specific way.\n"
    "- A list of any combination of the other two types. This type exists at "
    "the second level from the top and as the immediate child of each map "
    "node.\n"
    "\n"
    "Each leaf node identifies a particular named feature of the keyboard. "
    "It’s usually a set of corner posts on a named (aliased) key mount. "
    "These are identical to the posts used to build the walls, "
    "but this section gives you greater freedom in how to combine them. "
    "The elements of a leaf are, in order:\n"
    "\n"
    "1. Mandatory: The name of a feature, such as a key by its `alias`.\n"
    "2. Optional: A compass point, such as `SW` for south-west or `NNE` for "
    "north by north-east. "
    "If this is omitted, i.e. if only the mandatory element is given, the "
    "tweak will use the middle of the named feature.\n"
    "3. Optional: A starting wall segment ID, which is an integer from 0 to "
    "at most 4 inclusive (2 is the maximum for an MCU lock plate, 4 for a "
    "key mount). "
    "If this is omitted, but a side is named, the default value is 0.\n"
    "4. Optional: A second wall segment ID. If this is provided, the leaf "
    "will represent the convex hull of the two indicated segments plus all "
    "segments between them. If this is omitted, only one wall post will be "
    "placed.\n"
    "\n"
    "By default, a map node will create a convex hull around its child "
    "nodes. However, this behaviour can be modified. The following keys are "
    "recognized:\n"
    "\n"
    "- `at-ground`: If `true`, child nodes will be extended vertically down "
    "to the ground plane, as with a `full` wall. The default value for this "
    "key is `false`. See also: `bottom-plate`.\n"
    "- `above-ground`: If `true`, child nodes will be visible as part of the "
    "case. The default value for this key is `true`.\n"
    "- `chunk-size`: Any integer greater than 1. If this is set, child nodes "
    "will not share a single convex hull. Instead, there will be a "
    "sequence of smaller hulls, each encompassing this many items.\n"
    "- `highlight`: If `true`, render the node in OpenSCAD’s "
    "highlighting style. This is convenient while you work.\n"
    "- `hull-around`: The list of child nodes. Required.\n"
    "\n"
    "In the following example, `A` and `B` are key aliases that would be "
    "defined elsewhere. "
    "The example is interpreted to mean that a plate should be "
    "created stretching from the south-by-southeast corner of `A` to the "
    "north-by-northeast corner of `B`. Due to `chunk-size` 2, that first "
    "plate will be joined, not hulled, with a second plate from `B` back to a "
    "different corner of `A`, with a longer stretch of (all) wall "
    "segments down the corner of `A`.\n"
    "\n"
    "```case:\n"
    "  tweaks:\n"
    "    bridge-between-A-and-B:\n"
    "      - chunk-size: 2\n"
    "        hull-around:\n"
    "        - [A, SSE]\n"
    "        - [B, NNE]\n"
    "        - [A, SSW, 0, 4]\n```\n"
    "\n"
    "Note that tweaks listed here will be considered part of the main body "
    "of the keyboard. A separate parameter is available for tweaking the "
    "central housing."]
   [:section [:case :foot-plates]
    "Optional flat surfaces at ground level for adding silicone rubber feet "
    "or cork strips etc. to the bottom of the keyboard to increase friction "
    "and/or improve feel, sound and ground clearance."]
   [:parameter [:case :foot-plates :include]
    {:default false :parse-fn boolean} "If `true`, include foot plates."]
   [:parameter [:case :foot-plates :height]
    {:default 4 :parse-fn num} "The height in mm of each mounting plate."]
   [:parameter [:case :foot-plates :polygons]
    {:default []
     :parse-fn schema/anchored-polygons
     :validate [::schema/foot-plate-polygons]}
    "A list describing the horizontal shape, size and "
    "position of each mounting plate as a polygon."]
   [:section [:mcu]
    "MCU is short for ”micro-controller unit”. You need at least one of "
    "these, it’s assumed to be mounted on a PCB, and you typically want some "
    "support for it inside the case.\n\n"
    "The total number of MCUs is governed by more than one setting, roughly "
    "in the following order:\n\n"
    "* If `mcu` → `include` is `false`, there is no MCU.\n"
    "* If `mcu` → `include` is `true` but `reflect` is `false`, there is one "
    "MCU.\n"
    "* If `mcu` → `include` and `reflect` and `mcu` → `position` → `central` "
    "are all `true`, there is (again) one MCU.\n"
    "* Otherwise, there are two MCUs: One in each half of the case, because "
    "of reflection."]
   [:parameter [:mcu :include]
    {:default false :parse-fn boolean}
    "If `true`, make space for at least one MCU PCBA."]
   [:parameter [:mcu :preview]
    {:default false :parse-fn boolean}
    "If `true`, render a visualization of the MCU PCBA. "
    "For use in development."]
   [:parameter [:mcu :type]
    {:default :promicro :parse-fn keyword
     :validate [(partial contains? cots/mcu-facts)]}
    "A code name for a form factor. "
    "The following values are supported, representing a selection of "
    "designs for commercial products from PJRC, SparkFun, the QMK team "
    "and others:\n\n"
    (cots/support-list cots/mcu-facts)]
   [:parameter [:mcu :intrinsic-rotation]
    stock/anchor-3d-vector-metadata
    "A vector of 3 angles in radians. This parameter governs the rotation of "
    "the PCBA around its anchor point in the front.\n"
    "By default, the PCBA appears lying flat, with the MCU side up and the "
    "connector end facing nominal north, away from the user.\n\n"
    "As an example, to have the PCBA standing on its long edge instead of "
    "lying flat, you would set this parameter like `[0, 1.5708, 0]`, the "
    "middle number being roughly π/2."]
   [:section [:mcu :position]
    "Where to place the MCU PCBA after intrinsic rotation."]
   [:parameter [:mcu :position :central]
    {:default false :parse-fn boolean}
    "If `true`, treat the MCU as central even on a reflected keyboard. "
    "When this setting and `reflect` are both `true` and a central housing "
    "is set to be included, MCU support will go in the central housing file, "
    "not the main case files.\n\n"
    "This setting is related to `central-housing` but, for flexibility, their "
    "relationship does not take `anchor` into account."]
   [:parameter [:mcu :position :anchor]
    {:default :origin :parse-fn keyword :validate [::schema/anchor]}
    "The name of a feature at which to place the PCBA. "
    "Typically a key alias, central housing point or `rear-housing`.\n\n"
    "To ensure harmony, when you enable `central` (above), you would normally "
    "enable both `reflect` and `central-housing` *and* set this `anchor` to "
    "`origin` or to a point on the central housing, in such a way that the "
    "MCU support will be physically attached to and supported by the central "
    "housing wall."]
   [:parameter [:mcu :position :side]
    stock/anchor-side-metadata
    stock/anchor-side-documentation]
   [:parameter [:mcu :position :segment]
    stock/anchor-segment-metadata
    stock/anchor-segment-documentation]
   [:parameter [:mcu :position :offset]
    stock/anchor-3d-vector-metadata
    stock/anchor-3d-offset-documentation]
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
    {:default {} :parse-fn schema/compass-angle-map
     :validate [::schema/compass-angle-map]}
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
    {:default ::placeholder :parse-fn keyword :validate [::schema/alias]}
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
    {:default [] :parse-fn schema/mcu-grip-anchors
     :validate [::schema/mcu-grip-anchors]}
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
    "    offset: [-1, -1]```\n\n"
    "Grip anchor points are all empty by default. "
    "They can be occupied, and connected, using `tweaks`."]
   [:parameter [:ports]
    {:heading-template "Special section %s"
     :default {}
     :parse-fn (schema/map-of keyword
                 (base/parser-with-defaults port/raws))
     :validate [(spec/map-of
                  ::schema/alias
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
    {:default :threaded :parse-fn keyword :validate [::schema/wrist-rest-style]}
    "The style of the wrist rest. Available styles are:\n\n"
    "- `threaded`: threaded fasteners connect the case and wrist rest.\n"
    "- `solid`: the case and wrist rest are joined together by `tweaks` "
    "as a single piece of plastic."]
   [:parameter [:wrist-rest :preview]
    {:default false :parse-fn boolean}
    "Preview mode. If `true`, this puts a model of the wrist rest in the same "
    "OpenSCAD file as the case. That model is simplified, intended for gauging "
    "distance, not for printing."]
   [:section [:wrist-rest :position]
    "The wrist rest is positioned in relation to a named feature."]
   [:parameter [:wrist-rest :position :anchor]
    {:default :origin :parse-fn keyword :validate [::schema/anchor]}
    "The name of a feature where the wrist rest will attach. "
    "The vertical component of its position will be ignored."]
   [:parameter [:wrist-rest :position :side]
    stock/anchor-side-metadata stock/anchor-side-documentation]
   [:parameter [:wrist-rest :position :segment]
    stock/anchor-segment-metadata stock/anchor-segment-documentation]
   [:parameter [:wrist-rest :position :offset]
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
     :parse-fn schema/nameable-spline
     :validate [::schema/nameable-spline]}
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
    {:default 0 :parse-fn num}
    "Tait-Bryan pitch."]
   [:parameter [:wrist-rest :rotation :roll]
    {:default 0 :parse-fn num}
    "Tait-Bryan roll."]
   [:parameter [:wrist-rest :mounts]
    {:heading-template "Special section %s"
     :default []
     :parse-fn (schema/tuple-of (base/parser-with-defaults restmnt/raws))
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
    {:default [] :parse-fn schema/anchored-2d-positions
     :validate [::schema/anchored-2d-list]}
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
    {:default [] :parse-fn schema/anchored-2d-positions
     :validate [::schema/anchored-2d-list]}
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
