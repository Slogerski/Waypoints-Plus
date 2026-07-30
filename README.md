![aa](https://cdn.modrinth.com/data/cached_images/6c02ea6a406ee040def164f51a8835d175682fc5_0.webp)
# Waypoints Plus

**Waypoints Plus** is a lightweight client-side Fabric mod for creating colorful, smooth waypoint markers. It provides per-server profiles, cross-dimension coordinate conversion, customizable visuals, and convenient in-game management.

## Main features

- Smooth world-space waypoint labels that always face the camera.
- Visibility through walls and beyond loaded chunks.

  ![Waypoint visible through walls](https://cdn.modrinth.com/data/cached_images/508c974789f762e07739a94b9ad77deb36211a8a.png)

- Waypoints separated per multiplayer server or singleplayer world.
- Multiple profiles available on every server.

  ![Waypoint profiles](https://cdn.modrinth.com/data/cached_images/2fd36136971c1513fa275b3c8bba72c1fac908aa.png)

- Automatic Overworld and Nether conversion at the vanilla `8:1` ratio.
- Custom names, coordinates, vivid colors, and transparency.
- One visual ARGB color picker.
- Optional background, coordinates, distance, and laser.
- Depth-tested lasers that do not draw over blocks.
- Adjustable label scale.
- English and Polish GUI.
- Full in-game creation, editing, repositioning, renaming, and deletion.
- Human-readable JSON files with manual in-game reload support.

![Waypoint editor](https://cdn.modrinth.com/data/cached_images/a7a7d25f2cc3738de3a55edfe372b9c790c8c0df.png)

![Waypoint settings](https://cdn.modrinth.com/data/cached_images/c2f885458fa6e35ad00b63700a11140ab6507556.png)

## Controls

- `B` - create waypoint.
- `M` - manage waypoints.
- Reload waypoint file - unassigned.
- Previous profile - unassigned.
- Next profile - unassigned.

All controls are configurable from Minecraft's standard Controls menu.

## Screenshot privacy

Both waypoint coordinates and distance can be turned off. Disable them before taking screenshots or recordings when you want to avoid revealing exact location information.

## Requirements

- Fabric Loader.
- Fabric API.
- Minecraft `1.19.2` or `1.20.1` with Java 17.
- Minecraft `1.20.6`, `1.21.1`, `1.21.2`–`1.21.6`, `1.21.8`, `1.21.10`, or `1.21.11` with Java 21.
- Minecraft `26.1.2` or `26.2` with Java 25.

Each supported Minecraft version receives a separately built JAR with exact loader metadata.
Minecraft `26.3` will be added only after official Minecraft and Fabric artifacts are available.

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
- [CurseForge projects](https://www.curseforge.com/members/slogerski/projects)
- [Source](https://github.com/Slogerski/Waypoints-Plus)
- [Buy Me a Coffee](https://buymeacoffee.com/slogerski)

![More screenshots](https://cdn.discordapp.com/attachments/1473421832282439680/1530993566741168148/ImageToStl.com_model.gltf_1.gif?ex=6a67985e&is=6a6646de&hm=641fc80f26690a5489e0fea2323b61c667ea39c537d47648ac9f1f7a73bfd66b&)

## License

Copyright (c) 2026 Slogerski. All rights reserved. See [LICENSE](LICENSE).

Created by **Slogerski**.
