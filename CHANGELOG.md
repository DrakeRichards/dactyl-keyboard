# Change log
This log follows the conventions of
[keepachangelog.com](http://keepachangelog.com/). It picks up from DMOTE
version 0.2.0, thus covering only a fraction of the project’s history.

## [Unreleased]
Nothing yet.

## [Version 0.3.0]
### Changed
- Moved and replaced some options:
    - Dimensions of `keycaps` have moved into nestable `parameters` under
      `by-key`.
    - `key-alias` settings have been merged into `anchor`. `anchor` can now
      refer to a variety of features either by alias or by a built-in and
      reserved name like `rear-housing` or `origin`. In some cases, it is now
      possible to anchor features more freely as a result.
    - Moved `case` → `rear-housing` → `offsets` into
      `case` → `rear-housing` → `position`.
    - Moved `case` → `rear-housing` → `distance` into
      `case` → `rear-housing` → `position` → `offsets` as `south`.
    - Renamed the `key-corner` input of `case` → `foot-plates` → `polygons`
      to `corner`.
    - Removed the option `case` → `rear-housing` → `west-foot` in favour of
      more general `foot-plates` functionality.
    - Removed `wrist-rest` → `shape` → `plinth-base-size` in favour of settings
      (in a new `spline` section) that do not restrict you to a cuboid.
    - Removed `wrist-rest` → `shape` → `chamfer`. You can achieve the old
      chamfered, boxy look by setting spline resolution to 1 and manually
      positioning the corners of the wrist rest for it.
    - Moved `wrist-rest` → `shape` → `lip-height` to
      `wrist-rest` → `shape` → `lip` → `height`.
    - Moved `wrist-rest` → `shape` → `pad` → `surface-heightmap`
      to `wrist-rest` → `shape` → `pad` → `surface` → `heightmap` → `filepath`.
    - Substantial changes to `wrist-rest` → `fasteners`, which has been castled
      to `wrist-rest` → `mounts` and is now a list.
- Removed the `solid` style of wrist rest attachment.
- Removed the option `wrist-rest` → `fasteners` → `mounts` → `plinth-side` →
  `pocket-scale`, obviated by a new generic `dfm` feature.
- Renamed the ‘finger’ key cluster to ‘main‘.
- As a side effect of outsourcing the design of threaded fasteners to
  `scad-tarmi`, the `flat` style of bolt head has been renamed to
  the more specific `countersunk`.
- Removed `create-models.sh`, adding equivalent functionality to the Clojure
  application itself (new flags: `--render`, `--renderer`).
- Added intermediate `scad` and `stl` folders under `things`.
- Split generated documentation (`options.md`) into four separate documents
  (`options-*.md`).

### Added
- This log.
- Support for generating a bottom plate that closes the case.
    - This includes support for a separate plate for the wrist rest, and a
      combined plate that joins the two models.
- Improvements to wrist rests.
    - Arbitrary polygonal outlines and vertically rounded edges, without a
      height map.
    - Tilting.
    - Support for placing wrist rests in relation to their point
      of attachment to the case using a new `anchoring` parameter.
    - Support for multiple mount points.
    - Support for naming the individual blocks that anchor a wrist rest.
    - Support for placing wrist rests in relation to a specific corner of a key.
      In the previous version, the attachment would be to the middle of the key.
    - Parametrization of mould wall thickness.
    - Parametrization of sprues.
- Support for naming your key clusters much more freely, and/or adding
  additional clusters. Even the new ‘main’ cluster is optional.
    - Support for a `cluster` parameter to `case` → `rear-housing` →
      `position`. The rear housing would previously be attached to the finger
      cluster.
    - Support for a `cluster` parameter to `case` → `leds` → `position`.
      LEDs would previously be attached to the finger cluster.
    - Support for anchoring any cluster to any other, within logical limits.
- Parametrization of keycap sizes, adding support for sizes other than 1u in
  both horizontal dimensions, as well as diversity in keycap height and
  clearance.
- Support for a filename whitelist in the CLI.
- Support for placing `foot-plates` in relation to objects other than keys.
- Support for generic compensation for slicer and printer inaccuracies in the
  xy plane through a new option, `dfm` → `error`.

### Fixed
- Improved support for Windows by using `clojure.java.io/file` instead of
  string literals with Unix-style file-path separators.
- Strengthened parameter validation for nested sections.

### Developer
- Significant restructuring of the code base for separation of concerns.
    - Switched to docstring-first function definitions.
    - Shifted more heavily toward explicit namespacing and took the opportunity
      to shorten some function names in the matrix module and elsewhere.
- Added a dependency on `scad-tarmi` for shorter OpenSCAD code and more
  capable models of threaded fasteners.
- Rearranged derived parameter structure somewhat to support arbitrary key
  clusters and the use of aliases for more types of objects (other than keys).
- Removed the `new-scad` function without replacement.
- Removed a dependency on `unicode-math`. The requisite version of the library
  had not been deployed to Clojars and its use was cosmetic.

[Unreleased]: https://github.com/veikman/dactyl-keyboard/compare/dmote-v0.3.0...HEAD
[Version 0.3.0]: https://github.com/veikman/dactyl-keyboard/compare/dmote-v0.2.0...v0.3.0
