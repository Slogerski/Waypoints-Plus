# Development

## Requirements

- JDK 21 for Minecraft 1.21.4 and 1.21.8
- JDK 25 for Minecraft 26.1.2 and aggregate builds
- Git
- No global Gradle installation is required; use the included wrapper.

## Useful commands

```powershell
.\gradlew.bat buildAll
.\gradlew.bat :fabric-1.21.4:runClient
.\gradlew.bat :fabric-1.21.8:runClient
.\gradlew.bat assembleModrinthArtifacts
```

## Conventions

- Keep package names under `pl.slogerski.waypointsplus`.
- Keep platform-neutral code in `common`.
- Never reference one Minecraft-version module from another.
- Add translations for every user-visible string.
- Do not commit `.gradle`, `build`, IDE, or game-run directories.
- A pull request should compile with `buildAll` before review.
