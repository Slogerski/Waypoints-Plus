# Architecture

Waypoints Plus separates stable application logic from Minecraft APIs.

## Modules

### `common`

Contains waypoint models, use cases, and interfaces. It must not import Minecraft, Fabric, rendering, input, or screen classes. This keeps most code reusable across Minecraft versions and loaders.

### `platforms/fabric/<minecraft-version>`

Contains the smallest possible adapter for one exact Minecraft version:

- loader entrypoint and key bindings;
- Minecraft-backed repository implementations;
- screens and rendering integrations;
- version-specific resources and metadata.

Code moves into `common` whenever it can be expressed without platform types. Version-specific code never leaks into another platform module.

Versions through 1.21.11 use Yarn adapters. Minecraft 26.x uses Mojang's official
names and the extraction-based GUI API. Rendering has explicit compatibility
boundaries for legacy 1.19.2, the classic 1.20–1.21 pipeline, the command-queue
pipeline in 1.21.10–1.21.11, and the 26.x extraction pipeline.

## UI boundaries

Input calls `WaypointScreenRouter`. The planned screens are:

1. `CreateWaypointScreen`, opened by the create binding.
2. `WaypointManagerScreen`, opened by the manage binding.
3. `AdvancedSettingsScreen`, opened from the manager's gear button.

The router allows screen implementations to change between Minecraft versions without changing key-binding or domain logic.

## Adding another version

1. Add a `planned` entry to `versions/supported-versions.json`.
2. Create `platforms/fabric/<version>/` from the nearest API-compatible adapter.
3. Build and test the module independently.
4. Change its manifest status to `active`.
5. Run `buildAll validateReleaseArtifacts` and update public documentation.

`settings.gradle`, aggregate tasks, artifact collection, and CI discover active
versions from the manifest. A new active version therefore cannot be omitted from
the complete build accidentally.
