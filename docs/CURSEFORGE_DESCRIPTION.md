# Waypoints Plus

<!-- Upload your banner to CurseForge, then replace this placeholder URL. -->
![Waypoints Plus banner](PASTE_BANNER_IMAGE_URL_HERE)

**Waypoints Plus** is a lightweight client-side Fabric mod for creating colorful, smooth waypoint markers. It provides per-server profiles, cross-dimension coordinate conversion, customizable visuals, and convenient in-game management.

## Main features

- Smooth world-space waypoint labels that always face the camera.
- Visibility through walls and beyond loaded chunks.
- Waypoints separated per multiplayer server or singleplayer world.
- Multiple profiles available on every server.
- Automatic Overworld and Nether conversion at the vanilla `8:1` ratio.
- Custom names, coordinates, vivid colors, and transparency.
- One visual ARGB color picker.
- Optional background, coordinates, distance, and laser.
- Depth-tested lasers that do not draw over blocks.
- Adjustable label scale.
- English and Polish GUI.
- Full in-game creation, editing, repositioning, renaming, and deletion.
- Human-readable JSON files with manual in-game reload support.
- Automatic red `Death` waypoint in a dedicated `Death Waypoints` profile after every death.
- Profile renaming and confirmed profile removal from advanced settings.

<!-- Waypoints visible in the world. -->
![Waypoints in game](PASTE_WAYPOINT_RENDER_IMAGE_URL_HERE)

<!-- Create or edit waypoint screen. -->
![Create waypoint screen](PASTE_WAYPOINT_EDITOR_IMAGE_URL_HERE)

<!-- Settings and profile management. -->
![Settings and profiles](PASTE_SETTINGS_PROFILES_IMAGE_URL_HERE)

## Controls

- `B` — create waypoint.
- `M` — manage waypoints.
- Reload waypoint file — unassigned.
- Previous profile — unassigned.
- Next profile — unassigned.

All controls are configurable from Minecraft's standard Controls menu.

## Screenshot privacy

Both waypoint coordinates and distance can be turned off. Disable them before taking screenshots or recordings when you want to avoid revealing exact location information.

## Requirements

- Fabric Loader.
- Fabric API.
- Minecraft `1.19.2` or `1.20.1` with Java 17.
- Minecraft `1.20.6`, `1.21.1`, `1.21.2–1.21.6`, `1.21.8`, `1.21.10`, or `1.21.11` with Java 21.
- Minecraft `26.1.2` or `26.2` with Java 25.
- Every Minecraft version is distributed as a separately built JAR with exact version metadata.

Waypoints Plus is entirely client-side. A server does not need to install it.

## Local data

Waypoint data is stored in readable per-server files:

```text
config/waypointsplus/profiles.json
config/waypointsplus/waypoints/<server-file-id>.json
```

`profiles.json` maps each server or world to its waypoint file. You can edit the
active server file outside Minecraft and use the reload keybind afterward.

## Project links

- [Modrinth profile](https://modrinth.com/user/Slogerski)
- [Source](https://github.com/Slogerski/Waypoints-Plus)
- [Buy Me a Coffee](https://buymeacoffee.com/slogerski)

<!-- Optional About screen, comparison, or feature collage. -->
![More screenshots](PASTE_ADDITIONAL_IMAGE_URL_HERE)

Created by **Slogerski**.
