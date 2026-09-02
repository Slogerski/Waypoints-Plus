
![WaypointsPlus](https://cdn.modrinth.com/data/cached_images/04a473c950b6194611b8c394e21eab240d3198ab_0.webp)
<p align="center">

</p>
<p align="center">
   <a href="https://modrinth.com/mod/waypoints-plus">
    <img src="https://img.shields.io/badge/Pobierz-Modrinth-00AF5C?style=for-the-badge&logo=modrinth&logoColor=white" alt="Pobierz z Modrinth">
  </a>
  <a href="https://www.curseforge.com/minecraft/mc-mods/waypoints-plus">
    <img src="https://img.shields.io/badge/Pobierz-CurseForge-F16436?style=for-the-badge&logo=curseforge&logoColor=white" alt="Pobierz z CurseForge">
  </a>
  <a href="https://github.com/Slogerski/Waypoints-Plus">
    <img src="https://img.shields.io/badge/Kod_źródłowy-GitHub-181717?style=for-the-badge&logo=github&logoColor=white" alt="Kod źródłowy">
  </a>

**Waypoints Plus** is a lightweight, client-side Fabric mod for creating smooth and customizable waypoint markers.

Create separate profiles, organize waypoints per server or singleplayer world, and customize their appearance directly in game. No server installation is required.

## Features

- Smooth world-space waypoint markers.
- Visible through walls and beyond loaded chunks.
- Markers always face the camera.
- Separate waypoint storage for every server and singleplayer world.
- Multiple profiles for each world or server.
- Automatic death waypoints stored in a dedicated profile.
- Overworld and Nether coordinate conversion using the vanilla `8:1` ratio.
- Custom waypoint names, positions, colors, and transparency.
- Visual ARGB color picker.
- Customizable marker and background colors.
- Optional coordinates, distance, background, and laser.
- Adjustable waypoint scale.
- Depth-tested lasers that do not render through blocks.
- English and Polish interface.
- Full in-game waypoint creation, editing, repositioning, and deletion.
- Human-readable JSON storage with manual reload support.

## Profiles

Keep different groups of waypoints separate without mixing everything into one list. Each server or world can contain multiple independent profiles.

![Replace this with a description](https://cdn.modrinth.com/data/cached_images/fd2d237376a8c98da11758db7d1e8f4a372a3e60.png)

## Waypoint management

Create waypoints at your current position or enter custom coordinates. Existing waypoints can be renamed, moved, recolored, or removed directly from the management screen.

![Replace this with a description](https://cdn.modrinth.com/data/cached_images/cd94be045a45f9448d6ddca281e58b694eb4a57c.png)

---
![Replace this with a description](https://cdn.modrinth.com/data/cached_images/ea47ff76d24daa2ee3422219d075c807ec0bf818.png)
---

## Customization

Control the information displayed on waypoint markers and adjust their appearance without editing configuration files manually.

Available options include:

- Background visibility.
- Coordinates visibility.
- Distance visibility.
- Laser visibility.
- Marker scale.
- Default marker color.
- Default background color.
- Menu background and blur where supported.
- English and Polish language.
![Replace this with a description](https://cdn.modrinth.com/data/cached_images/58c7415412a9fd8e24bc08265ddf215bffba4642.png)

## Default controls

| Action | Default key |
|---|---:|
| Create Waypoint | `b` |
| Manage Waypoints | `;` |
| Previous Waypoint Profile | `Left Arrow` |
| Next Waypoint Profile | `Right Arrow` |
| Reload Waypoints From File | Unassigned |
| Quick Waypoint | Unassigned |
| Copy Current Position | Unassigned |

All controls can be changed in Minecraft's standard Controls menu.

## Screenshot privacy

Waypoint coordinates and distance can be disabled independently. Turn them off before taking screenshots or recording when you do not want to reveal an exact location.

## Supported versions

Waypoints Plus provides a separate, optimized JAR for each supported Minecraft version:

- `1.19.2`
- `1.20.1`
- `1.20.6`
- `1.21.1`
- `1.21.4`
- `1.21.5`
- `1.21.6`
- `1.21.8`
- `1.21.10`
- `1.21.11`
- `26.1.2`
- `26.2`

Always install the file marked for your exact Minecraft version.

## Requirements

- [Fabric Loader](https://fabricmc.net/)
- [Fabric API](https://modrinth.com/mod/fabric-api)

Waypoints Plus is entirely client-side. It does not need to be installed on the server.

## Local data

Configuration and waypoint data are stored in:

```text
config/waypointsplus/
