# Waypoints Plus

<!-- Replace the URL below with your uploaded banner. -->
![Waypoints Plus banner](PASTE_BANNER_IMAGE_URL_HERE)

**Waypoints Plus** is a lightweight, client-side waypoint manager for Minecraft. Create colorful markers, organize them into per-server profiles, and keep them visible smoothly through walls and across long distances.

## Features

- Smooth, camera-facing waypoint labels rendered in the game world.
- Waypoints remain visible through walls and outside loaded terrain.
- Separate waypoint storage for every multiplayer server and singleplayer world.
- Multiple profiles per server, with quick profile switching.
- Automatic Overworld ↔ Nether coordinate conversion using the vanilla `8:1` ratio.
- Custom waypoint name, XYZ position, color, and transparency.
- Built-in color picker with precise ARGB control.
- Optional translucent background, coordinates, distance, and vertical laser.
- Lasers are hidden behind blocks.
- Adjustable waypoint scale.
- English and Polish interface.
- Editable, unencrypted JSON storage and a manual reload keybind.
- Create, edit, move, rename, and delete waypoints from in-game menus.
- Automatic red `Death` waypoint in a dedicated `Death Waypoints` profile after every death.
- Profile renaming and confirmed profile removal from advanced settings.

<!-- Replace the URL below with a waypoint screenshot. -->
![Waypoint rendering](PASTE_WAYPOINT_RENDER_IMAGE_URL_HERE)

<!-- Replace the URL below with a GUI screenshot. -->
![Waypoint editor](PASTE_WAYPOINT_EDITOR_IMAGE_URL_HERE)

<!-- Replace the URL below with a profiles screenshot. -->
![Waypoint profiles](PASTE_PROFILES_IMAGE_URL_HERE)

## Default controls

- `B` — create a waypoint.
- `M` — manage waypoints.
- Reload waypoint file — unassigned by default.
- Previous profile — unassigned by default.
- Next profile — unassigned by default.

Every keybind can be changed in Minecraft's Controls menu.

## Privacy controls

Coordinates and distance can be disabled independently. This is useful when sharing screenshots or recordings where you do not want to expose exact location information.

## Supported versions

- Experimental compatibility build for Fabric `1.21.2–1.21.4` — Java 21. Test each game version before relying on it.
- Fabric for Minecraft `1.21.8` — Java 21.
- Fabric for Minecraft `26.1.2` — Java 25.
- Fabric API is required.

## Configuration

Waypoint data is stored locally in:

```text
config/waypointsplus/waypoints.json
```

The file is intentionally readable and can be edited outside the game. Use the unassigned reload keybind to refresh externally modified waypoints.

## Links

- [Author on Modrinth](https://modrinth.com/user/Slogerski)
- [CurseForge projects](https://www.curseforge.com/members/slogerski/projects)
- [Source code](https://github.com/Slogerski/Waypoints-Plus)
- [Buy Me a Coffee](https://buymeacoffee.com/slogerski)

<!-- Optional final screenshot or feature collage. -->
![Additional preview](PASTE_ADDITIONAL_IMAGE_URL_HERE)

Made by **Slogerski**.
