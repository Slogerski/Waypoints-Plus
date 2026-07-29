# Development

## Requirements

- JDK 17 for Minecraft 1.19.2 and 1.20.1
- JDK 21 for Minecraft 1.20.6 through 1.21.11
- JDK 25 for Minecraft 26.x and aggregate builds
- Git
- No global Gradle installation is required; use the included wrapper.

## Useful commands

```powershell
.\gradlew.bat buildAll
.\gradlew.bat :fabric-1.21.11:runClient
.\gradlew.bat validateReleaseArtifacts
.\gradlew.bat listSupportedVersions
```

## Conventions

- Keep package names under `pl.slogerski.waypointsplus`.
- Keep platform-neutral code in `common`.
- Never reference one Minecraft-version module from another.
- Add translations for every user-visible string.
- Do not commit `.gradle`, `build`, IDE, or game-run directories.
- A pull request should pass `buildAll validateReleaseArtifacts` before review.
