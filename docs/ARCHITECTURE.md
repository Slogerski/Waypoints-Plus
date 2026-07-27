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

Minecraft 1.21.4 and 1.21.8 use separate Yarn adapters, while 26.1.2 uses Mojang's official mappings and the extraction-based GUI API. They therefore share data and domain code, not platform source files.

## UI boundaries

Input calls `WaypointScreenRouter`. The planned screens are:

1. `CreateWaypointScreen`, opened by the create binding.
2. `WaypointManagerScreen`, opened by the manage binding.
3. `AdvancedSettingsScreen`, opened from the manager's gear button.

The router allows screen implementations to change between Minecraft versions without changing key-binding or domain logic.

## Adding another version

1. Create `platforms/fabric/<version>/` based on the nearest supported version.
2. Add version-specific dependency properties to `gradle.properties`.
3. Include the module in `settings.gradle`.
4. Add its build and remap tasks to the root aggregation tasks.
5. Update the support table in `README.md` and verify every module independently.
