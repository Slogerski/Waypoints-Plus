![Waypoints Plus](https://cdn.modrinth.com/data/cached_images/04a473c950b6194611b8c394e21eab240d3198ab_0.webp)

[![Modrinth](https://img.shields.io/badge/MODRINTH-00AF5C?style=for-the-badge&logo=modrinth&logoColor=white)](https://modrinth.com/mod/waypoints-plus)
[![CurseForge](https://img.shields.io/badge/CURSEFORGE-F16436?style=for-the-badge&logo=curseforge&logoColor=white)](https://www.curseforge.com/minecraft/mc-mods/waypoints-plus)
[![Source](https://img.shields.io/badge/SOURCE-181717?style=for-the-badge&logo=github&logoColor=white)](https://github.com/Slogerski/Waypoints-Plus)

![Modrinth Downloads](https://img.shields.io/modrinth/dt/LWEC5GA8?style=flat-square&logo=modrinth&label=Modrinth%20Downloads&color=00AF5C)
![CurseForge Downloads](https://img.shields.io/curseforge/dt/1626803?style=flat-square&logo=curseforge&label=CurseForge%20Downloads&color=F16436)
![GitHub Stars](https://img.shields.io/github/stars/Slogerski/Waypoints-Plus?style=flat-square&logo=github&label=GitHub%20Stars)

**Waypoints Plus** is a client-side Fabric mod for marking locations, sharing coordinates and keeping your team informed.

Save bases, track your last death, organize locations into profiles and customize how your waypoints look. Fight Alerts let you send a prepared message to Discord or trusted players with a keybind.

No server installation is required.

![Waypoints in game](https://cdn.modrinth.com/data/cached_images/93d50c9b45316fef31f048102d40b8c7bc7cf303.png)

## Your locations, organized

Waypoints stay separate for each multiplayer server and singleplayer world. Create profiles for **Bases**, **Allies**, **Enemies**, **Structures**, or an empty profile when you want a clear view.

Switch profiles using the menu arrows or keybinds. New waypoints go into your active profile, while death locations are saved automatically in a dedicated profile with a date and time.

![Waypoint profiles](https://cdn.modrinth.com/data/cached_images/fd2d237376a8c98da11758db7d1e8f4a372a3e60.png)

## Create and manage waypoints

Create a waypoint at your current position or enter coordinates manually. Change its name, color and dimension, including custom dimensions used by servers.

The manager lets you edit, delete, select and export waypoints. Filter the list by dimension to find what you need. A teleport button is available when the client detects access to the relevant command.

![Waypoint management](https://cdn.modrinth.com/data/cached_images/cd94be045a45f9448d6ddca281e58b694eb4a57c.png)

![Waypoint creation and editing](https://cdn.modrinth.com/data/cached_images/ea47ff76d24daa2ee3422219d075c807ec0bf818.png)

## Make them yours

Markers face the camera and remain visible through walls and beyond loaded chunks. Optional lasers mark the waypoint's position and are hidden by blocks.

Choose colors and transparency with the visual color picker, reuse recent colors and save a preferred default. Match text to the border or use a separate default text color.

Settings include:

- Background, coordinates, distance and laser visibility.
- Label scale, text color and background color.
- Adjustable background tint using the border color.
- Cross-dimension visibility with Overworld and Nether `8:1` conversion.
- Menu background effects.
- English and Polish interface.

![Waypoint settings](https://cdn.modrinth.com/data/cached_images/58c7415412a9fd8e24bc08265ddf215bffba4642.png)

## Share coordinates

Export selected waypoints to the clipboard and let another player import them into their current profile. Imports containing custom dimensions can be kept as they are or mapped to another dimension.

For a single location, use **Fast Waypoints**. Copy a line like this:

```text
100 64 -250 Meeting point
```

Press **Create Waypoint From Clipboard** to create it without opening a menu. **Copy Current Position** prepares a line with your own coordinates.

[Read the Fast Waypoints guide](https://slogerski.github.io/Waypoints-Plus/?lang=en#fast-waypoints)

## Fight Alerts

Set up a message once and send it when you need it. Choose a Discord webhook or in-game `MSG` to up to 10 trusted recipients, with an adjustable delay between messages.

Trigger an alert with a single press, a key combination or repeated presses within a chosen time window. Each alert can be enabled or disabled separately, with optional popup notifications.

Message variables can include:

- Your name, coordinates, dimension, server and health.
- Players you attacked during the last minute.
- Golden apples, enchanted golden apples, pearls and totems.
- Armor durability.
- A ready-to-copy Fast Waypoint.

For example:

```text
$PLAYER needs help at $X $Y $Z $DIMENSION on $SERVER
HP: $HP
Opponents: $OPPONENT_COUNT
$OPPONENTS
```

Discord messages support multiple lines. In-game `MSG` converts line breaks to spaces and shortens messages to fit Minecraft's command limit. Server permissions and messaging rules still apply.

Keep your Discord webhook URL private.

[Read the Fight Alerts guide](https://slogerski.github.io/Waypoints-Plus/?lang=en#fight-alerts)

## Default controls

| Action | Default key |
|---|---|
| Create Waypoint | `B` |
| Manage Waypoints | `;` |
| Previous Waypoint Profile | `Left Arrow` |
| Next Waypoint Profile | `Right Arrow` |
| Reload Waypoints From File | Unassigned |
| Create Waypoint From Clipboard | Unassigned |
| Copy Current Position | Unassigned |

Change these in Minecraft's **Controls** menu. Fight Alert triggers are configured separately for each alert.

## Before sharing screenshots

Coordinates and distance can be hidden independently. Turn them off when you do not want them shown in screenshots or recordings. Visible landmarks and waypoint directions can still reveal clues about a location.

## Supported versions

`1.19.2` · `1.20.1` · `1.20.6` · `1.21.1` · `1.21.2–1.21.4` · `1.21.5` · `1.21.6` · `1.21.8` · `1.21.10` · `1.21.11` · `26.1.2` · `26.2`

Install the JAR marked for your Minecraft version, together with [Fabric Loader](https://fabricmc.net/) and [Fabric API](https://modrinth.com/mod/fabric-api).

## Local data

Settings, profiles and waypoint files are stored locally in:

```text
config/waypointsplus/
```

Waypoint data uses readable JSON files, with separate files for each server or world. After editing waypoint files outside the game, use the reload keybind to refresh them.

---

[Documentation](https://slogerski.github.io/Waypoints-Plus/?lang=en) · [Source](https://github.com/Slogerski/Waypoints-Plus) · [Buy Me a Coffee](https://buymeacoffee.com/slogerski)

Created by **Slogerski**.
