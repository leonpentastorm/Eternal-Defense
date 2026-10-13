# Eternal Defense / Create Arsenal

Editable Java, assets, recipes and Gradle projects for the three Create Arsenal mods. This is the **0.21.0 source handoff** for the UI redesign, rather than a decompiled release archive.

| Project | Version | Purpose |
| --- | --- | --- |
| `custom-mods/arsenal-beacon` | 0.21.0 | Shared defense beacon, raid campaign, progression and Universal Weapon Platform |
| `custom-mods/arsenal-gun-guide` | 0.21.0 | Client weapon controls guide and supported gun-jam warning |
| `custom-mods/arsenal-displays` | 1.1.0 | Eleven weapon stands, racks and glass cases |

Read [ARTIST-BRIEF.md](ARTIST-BRIEF.md) for the player-facing pitch and visual direction, then [the UI development handoff](docs/UI-DEVELOPMENT.md) for code locations and constraints. The redesign is described in [docs/UI-REDESIGN-NOTES.md](docs/UI-REDESIGN-NOTES.md) and the Ardent Energy / base support systems in [docs/ENERGY-AND-SUPPORT.md](docs/ENERGY-AND-SUPPORT.md). Since 0.0.41 only the curated modpack (pack) edition is built.

**Project 0.0.41** (file `VERSION`): the standalone edition is discontinued; the pack edition is the only one, and the mods it cannot run without are required in `mods.toml` (see [PACK-EDITION.md](docs/PACK-EDITION.md)). Where the project stands, how to build and test, and what is unverified: [docs/HANDOFF.md](docs/HANDOFF.md); every round: [docs/CHANGELOG.md](docs/CHANGELOG.md); the rules: [docs/GAME-DESIGN-DOCUMENT.md](docs/GAME-DESIGN-DOCUMENT.md). Network protocol **41**. Project versions (`VERSION`) are separate from the JAR versions above.

The test pack for a round is the owner's exported Modrinth profile with this build's jars and scripts (`tools/release/make-testpack.py`); it is too large for the repository and is handed over directly. Until 1.0, start a fresh world for each feature.

## Build

Requires **JDK 17**. Gradle 8.8, ForgeGradle 6.0.54, Minecraft 1.20.1 and Forge 47.4.20 are pinned. The wrapper downloads Gradle; a separate Gradle installation is unnecessary.

Linux/macOS:

```sh
./gradlew --no-daemon build releaseJars
```

Windows PowerShell:

```powershell
.\gradlew.bat --no-daemon build releaseJars
```

After building, each project's `build/libs` contains its JAR (the pack edition; since 0.0.41 there is no `-standalone.jar`). The displays project also produces a sources JAR, which is for developers rather than installation. `build` runs the Java unit tests. The beacon now compiles against pinned public TaCZ and TaCZ Attributes artifacts from Curse Maven and JEI APIs. First dependency resolution needs network access; offline builds require those artifacts and MixinGradle already cached. No launcher-instance dependency paths or embedded addon JARs are used. **These commands passed for v6; see its validation record for runtime coverage.**

For a cloud environment's first build, see [NETWORK-ALLOWLIST.txt](docs/NETWORK-ALLOWLIST.txt) and [network setup](docs/CLOUD-SETUP.md).

## Playtest

Use the test pack of the round (the owner's Create Arsenal profile with this build's jars), or install the three jars into a copy of that profile. The required mods are listed in [PACK-EDITION.md](docs/PACK-EDITION.md); Forge names any that is missing. A headless build does not confirm that a screen looks correct in Minecraft.

The build pins TaCZ [file 9037989](https://www.curseforge.com/minecraft/mc-mods/timeless-and-classics-zero/files/9037989) and TaCZ Attributes project 1113285 / file 8470731. Download third-party mods from their authors; they are not vendored into the source projects.

## Contents and baseline

- `src/main/java`: shared behavior and screens.
- `src/main/resources`: pack release metadata, authored assets, language files and data.
- `src/test/java`: Java unit tests where present.
- `pack-integration`: authored KubeJS recipes and FTB Quests for reference.
- `docs/RELEASE-VALIDATION-0.21.0.json`: prior release validation, with its scope and limits. It is a historical baseline, not a claim that future UI changes have been tested.

The original `Arsenal-Standalone-Mods-0.21.0.zip` remains as the initial release artifact. Edit the source projects for the redesign. Do not unpack release JARs over them.

Licensing remains as declared by each mod. Gun Displays code is MIT; its supplied artwork is reserved, as detailed in its `LICENSE.txt`. Including art in this repository for the owner's UI collaboration does not change its license.

Previous v2/v3 distributions and validation records are historical artifacts. They do not contain the v4 changes or validate this backend.
