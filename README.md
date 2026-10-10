![Waypoints Plus](https://raw.githubusercontent.com/Slogerski/Waypoints-Plus/main/banner.png)

[![Modrinth](https://img.shields.io/badge/MODRINTH-00AF5C?style=for-the-badge&logo=modrinth&logoColor=white)](https://modrinth.com/mod/waypoints-plus)
[![CurseForge](https://img.shields.io/badge/CURSEFORGE-F16436?style=for-the-badge&logo=curseforge&logoColor=white)](https://www.curseforge.com/minecraft/mc-mods/waypoints-plus)
[![Source](https://img.shields.io/badge/SOURCE-181717?style=for-the-badge&logo=github&logoColor=white)](https://github.com/Slogerski/Waypoints-Plus)

![Modrinth Downloads](https://img.shields.io/modrinth/dt/LWEC5GA8?style=flat-square&logo=modrinth&label=Modrinth%20Downloads&color=00AF5C)
![CurseForge Downloads](https://img.shields.io/curseforge/dt/1626803?style=flat-square&logo=curseforge&label=CurseForge%20Downloads&color=F16436)
![GitHub Stars](https://img.shields.io/github/stars/Slogerski/Waypoints-Plus?style=flat-square&logo=github&label=GitHub%20Stars)

**Waypoints Plus** is a client-side Minecraft Fabric mod with custom waypoint icons, editable presets, waypoint grouping and clipboard sharing.

Mark bases, organize locations into profiles and build your own waypoint layouts. Hide coordinate fields with Incognito Mode, or send your team a prepared Fight Alert through Discord or in-game messages.

**No server installation is required.**

## Create waypoints your way

Save your current position or enter coordinates manually. Choose a name, color, dimension and preset, then select an item or block icon to help identify the location.

Use a chest for storage, a spawner for a farm or a different icon for each base. Keep the classic text-only style when you want something simple.

![Waypoint creation with presets, an item icon and Incognito Mode](https://cdn.modrinth.com/data/cached_images/3966ce1c035a58599e05dd03eb6a783269c7bb61.png)

## Icons and presets

Choose from four built-in presets: **Default**, **Default + Icon**, **Single Icon** and **Designed**. Pin favorites to keep them at the top of the preset list.

The **Preset Creator** lets you build your own layout with a live, zoomable preview:

- Move and resize the name, distance, coordinates, profile name and icon.
- Use Minecraft item and block icons, or upload a custom PNG.
- Adjust the background, border, corners and padding.
- Choose which elements expand the background and which can sit outside it.
- Attach elements to other elements or panel edges so they move with changes in text width.
- Import and export presets through the clipboard.

Presets are shared across your servers, worlds and profiles. Individual waypoints can use different item icons without needing a separate preset for each one.

## Keep crowded views readable

**Waypoint Grouping** combines overlapping distant waypoints into a compact marker instead of leaving a pile of labels on screen.

Groups use the Designed layout, show the nearest waypoint's name and include the number of locations in the group. Text-only waypoints can join groups containing icon waypoints.

Enable **Waypoint Grouping** in Advanced Settings and adjust **Grouping Distance**, which defaults to 250 blocks. Nearby waypoints remain separate, and grouping never deletes or merges your saved waypoint data.

## Your locations, organized

Waypoint profiles stay separate for each multiplayer server and singleplayer world.

Create profiles for **Bases**, **Allies**, **Enemies** or **Structures**, or keep an empty profile for a clear view. Switch between them using the menu arrows or keybinds without opening the settings every time.

New waypoints are saved to your active profile.

![Creating and switching waypoint profiles](https://cdn.modrinth.com/data/cached_images/938389823f951be4f36587bcbe79c22213ca6d64.png)

### Optional death waypoints

Death waypoints are **disabled by default**. Enable them in **Advanced Settings** to save future death locations with a date and time.

The dedicated death profile is created when needed. Turning the feature off stops new death waypoints from being created, but keeps existing locations and their profile.

## Manage and share locations

Edit, delete or select multiple waypoints from one screen. Filter the list by dimension, including custom dimensions used by servers.

Export selected waypoints to the clipboard so another player can import them into their active profile. Custom dimensions can be preserved or mapped to another dimension. If an imported waypoint uses a preset that is missing locally, you can choose a replacement.

A **/tp** button is available when the client detects access to the relevant teleport command. Server permissions still apply.

![Waypoint manager with selection, dimension filters, import and export](https://cdn.modrinth.com/data/cached_images/b6f9de709eae6f350144bd33aa11df00c61e456f.png)

## Colors and appearance

Choose colors and transparency with the visual color picker, reuse recent colors and remember a preferred default.

Match the text color to the border, or use a separate default text color for better readability. Select Overworld, Nether, End or a custom dimension when creating or editing a waypoint.

![Waypoint color picker, color history and dimension selection](https://cdn.modrinth.com/data/LWEC5GA8/images/3d4b1615f61bc78a08807640c8458cab041c6960.png)

Waypoint labels face the camera and remain visible through walls and beyond loaded chunks. Optional vertical lasers mark their positions, are hidden by blocks and render only within 250 blocks.

Settings include:

- Background, coordinates, distance and laser visibility.
- Label scale, default text color and background color.
- Background tint based on the border color.
- Cross-dimension visibility with Overworld and Nether 8:1 coordinate conversion.
- Waypoint grouping and its distance threshold.
- Optional death waypoints.
- Menu background effects.
- English and Polish interfaces.

![Waypoint visibility settings and access to Advanced Settings](https://cdn.modrinth.com/data/cached_images/bb4ba45d8538bfcdeafd8899a190d40298340603.png)

## Fast Waypoints

Create a waypoint directly from a short line of text, without opening a menu:

```text
100 64 -250 Meeting point
```

Copy the line and press your **Create Waypoint From Clipboard** keybind. The waypoint is added to your active profile. If the coordinates have no name, it uses **Quick Waypoint**.

Bind **Copy Current Position** to prepare the same format with your own coordinates, ready to paste into a message.

Both shortcuts are unassigned by default and can be configured in Minecraft's **Controls** menu.

[Read the Fast Waypoints guide](https://slogerski.github.io/Waypoints-Plus/?lang=en#fast-waypoints)

## Fight Alerts

Prepare a message once and send it with a keybind when your team needs your location or an update.

Choose a **Discord webhook** or in-game **MSG** to up to 10 trusted recipients. MSG supports an adjustable delay between recipients.

Trigger an alert with a single key, a key combination or repeated presses within a chosen time window. Enable or disable each alert separately, and optionally show a popup with the sending result.

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

Discord messages support multiple lines. In-game MSG converts line breaks to spaces and shortens messages to fit Minecraft's command limit. Server permissions and messaging rules still apply.

**Keep your Discord webhook URL private.**

[Read the Fight Alerts guide](https://slogerski.github.io/Waypoints-Plus/?lang=en#fight-alerts)

## Incognito Mode

Hide coordinate fields in the waypoint creation and editing screens with **Incognito Mode**. Each field displays `****` instead of its value, and your choice is remembered between sessions.

This does not change the saved coordinates or automatically hide information in the world. Coordinates and distance on waypoint labels can be disabled separately in settings.

Before streaming or sharing screenshots, remember that names, landmarks and waypoint directions can still reveal a location.

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

## Installation

Install [Fabric Loader](https://fabricmc.net/) and [Fabric API](https://modrinth.com/mod/fabric-api), then place the Waypoints Plus JAR for your Minecraft version in your `mods` folder.

Check the **Versions** tab for available builds and their requirements. Use the file marked for your Minecraft version.

Waypoints Plus runs on the client and does not need to be installed on the server.

## Local data

Settings, profiles, presets and waypoint files are stored locally in:

```text
config/waypointsplus/
```

Waypoint data uses readable JSON files, with separate files for each server or world. Back up this folder to keep your saved locations and configuration.

After editing waypoint files outside the game, use **Reload Waypoints From File** to load your changes.

---

[Documentation](https://slogerski.github.io/Waypoints-Plus/?lang=en) · [Source](https://github.com/Slogerski/Waypoints-Plus) · [Buy Me a Coffee](https://buymeacoffee.com/slogerski)

Created by **Slogerski**.
