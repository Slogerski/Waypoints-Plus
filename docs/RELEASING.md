# Releasing to Modrinth

Publishing is intentionally separate from normal CI so pull requests can never upload a release.

1. Replace the snapshot in `mod_version` inside `gradle.properties`.
2. Move entries from `Unreleased` into a dated version in `CHANGELOG.md`.
3. Run `./gradlew buildAll validateReleaseArtifacts`.
4. Test every JAR from `build/modrinth/` with every Minecraft version declared by that artifact.
5. Create a Git tag matching the mod version.
6. Upload the exact tested JAR to Modrinth as an alpha, beta, or release according to `release_channel`.

Automatic Modrinth publishing can be added after a Modrinth project ID exists. Its token must be stored only as a GitHub Actions secret and never committed.
