# Changelog

- Released Waypoints Plus 1.0.0.
- Added colored waypoint borders with lightly rounded corners.
- Redesigned create/edit forms with custom colored fields, editable XYZ coordinates and clipboard paste.
- Fixed waypoint panel layering and translucent border artifacts.
- Restyled general settings with a purple-to-red gradient panel and vertically centered custom inputs.
- Replaced multicolor settings input edges with a uniform magenta-purple accent.
- Added per-server waypoint profiles, profile navigation, creation UI and unbound previous/next profile keys.
- Hid coordinates in the waypoint manager whenever coordinate display is disabled.
- Added reusable live-search item icon picker with ten alphabetical results for create/edit forms.
- Added persistent Icons toggle and a styled About page placeholder.
- Enabled selected item icons on waypoint billboards and localized About as O modzie in Polish.
- Expanded About with a 3:4 avatar slot, author/version details and future platform/support buttons.
- Fixed invisible waypoint item icons by correcting the billboard model Y transform.
- Corrected item icon facing and depth offset for camera-facing waypoint billboards.
- Added optional full-bright colored vertical lasers at waypoint positions.
- Replaced unreliable world-model icons with per-frame projected HUD item icons aligned to waypoint labels.
- Lasers now use depth-tested geometry and are occluded by blocks.

All notable changes to Waypoints Plus will be documented here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and releases use semantic versioning.

## [Unreleased]

### Added

- Initial Fabric 1.21.8 project scaffold.
- Configurable create-waypoint and manage-waypoints key bindings.
- Version-independent waypoint domain model.
- Multi-version module layout and Modrinth artifact assembly task.
- Lightweight through-wall HUD waypoint renderer backed by editable JSON files.
- ARGB marker/background colors, transparency, coordinates, scale, and player-overlap settings.
- Fabric 26.1.2 adapter using official Mojang mappings, Java 25, and the extraction-based HUD API.
- Per-server and per-singleplayer-world waypoint isolation.
- Automatic Overworld/Nether X/Z conversion at an 8:1 ratio.
- Replaced screen-space projection with a smooth, camera-facing world-space billboard renderer.
- Far waypoint billboards are clamped to 24 blocks along the exact target ray to avoid camera far-plane clipping.
- Waypoint labels display real three-dimensional distance in blocks or kilometres.
- English-first GUI with an English/Polish language switch.
- CurseForge-ready artifact assembly and Slogerski author metadata.
- Unbound-by-default key binding for manually reloading externally edited waypoint JSON.
- Removed the ineffective player-overlap setting.
- Per-server waypoint manager with pagination, editing, current-position replacement, and deletion.
- Single color-picker dialog for waypoint creation, including opacity and live preview.
- Removed the experimental waypoint icon system due to unstable world and HUD rendering.
- Added a separate Distance / Odległość toggle to hide waypoint distance labels.
- Added the supplied avatar and Waypoints Plus logo to the About screen assets.
- Moved the logo out of the About GUI and configured it as the Fabric mod icon.
- Connected the About buttons to Modrinth, CurseForge, source code, and Buy Me a Coffee.
- The manual reload key now stores a top-level Player XYZ snapshot in waypoints.json.
- The Player XYZ snapshot is also saved when leaving a server or singleplayer world.
- Waypoint labels now render after translucent terrain and remain visible through glass.
- Added an experimental Fabric 1.21.4 build declaring compatibility with Minecraft 1.21.2–1.21.4.
- Added automatic red Death waypoints in a dedicated Death Waypoints profile.
- Added profile rename and confirmed profile removal controls; Default remains protected.
