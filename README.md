# Eternal Defense / Create Arsenal

Editable Java, assets, recipes and Gradle projects for the three Create Arsenal mods. This is the **0.21.0 source handoff** for the UI redesign, rather than a decompiled release archive.

| Project | Version | Purpose |
| --- | --- | --- |
| `custom-mods/arsenal-beacon` | 0.21.0 | Shared defense beacon, raid campaign, progression and Universal Weapon Platform |
| `custom-mods/arsenal-gun-guide` | 0.21.0 | Client weapon controls guide and supported gun-jam warning |
| `custom-mods/arsenal-displays` | 1.1.0 | Eleven weapon stands, racks and glass cases |

Read [ARTIST-BRIEF.md](ARTIST-BRIEF.md) for the player-facing pitch and visual direction, then [the UI development handoff](docs/UI-DEVELOPMENT.md) for code locations and constraints. The redesign is described in [docs/UI-REDESIGN-NOTES.md](docs/UI-REDESIGN-NOTES.md) and the Ardent Energy / base support systems in [docs/ENERGY-AND-SUPPORT.md](docs/ENERGY-AND-SUPPORT.md). Both the curated modpack and standalone editions are included. They share Java and registry IDs, with different release metadata, recipes and guide content.

Project update **0.0.2** adds [Mess Hall prepared food](docs/MESS-HALL.md): tiered kitchens, portable sandwiches, communal Cook Pots, and persistent meal bonuses that double and freeze their field timer at home. The new network protocol is **22**; clients and servers need matching editions and builds. Functional kitchen art uses vanilla textures pending painted replacements. Project versions (`VERSION`) remain separate from the JAR build versions above.

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

Each project's `build/libs` contains its pack JAR and `-standalone.jar`. The displays project also produces a sources JAR, which is for developers rather than installation. `build` runs the Java unit tests; `releaseJars` additionally builds all three standalone releases. TaCZ is accessed through runtime integration and does **not** require a local TaCZ JAR to compile these projects. The beacon compiles against pinned public JEI APIs; there are no launcher-instance dependency paths.

For a cloud environment's first build, see [NETWORK-ALLOWLIST.txt](docs/NETWORK-ALLOWLIST.txt) and [network setup](docs/CLOUD-SETUP.md).

## Playtest the correct edition

Install exactly one edition of each mod, with its runtime dependencies, into a separate Forge test instance. Pack and standalone JARs use the same mod IDs and cannot be installed together. A headless build does not confirm that a screen looks correct in Minecraft.

- **Pack:** requires TaCZ, Create and KubeJS, plus the curated recipes/catalogue from the complete Create Arsenal pack. Use the pack's existing instance for UI testing. The small `pack-integration` folder provides authored recipe and quest context; it is not the complete modpack.
- **Standalone:** requires TaCZ. Create, KubeJS, JEI and Gun Displays are optional for the beacon. Gun Guide is client-only. See [standalone installation and balance](docs/STANDALONE.md).

The recorded baseline used TaCZ 1.1.8 hotfix 2. Download third-party mods from their authors; they are not vendored into the source projects.

## Contents and baseline

- `src/main/java`: shared behavior and screens.
- `src/main/resources`: pack release metadata, authored assets, language files and data.
- `src/standalone/resources`: standalone metadata and replacement recipes.
- `src/test/java`: Java unit tests where present.
- `pack-integration`: authored KubeJS recipes and FTB Quests for reference.
- `docs/RELEASE-VALIDATION-0.21.0.json`: prior release validation, with its scope and limits. It is a historical baseline, not a claim that future UI changes have been tested.

The original `Arsenal-Standalone-Mods-0.21.0.zip` remains as the initial release artifact. Edit the source projects for the redesign. Do not unpack release JARs over them.

Licensing remains as declared by each mod. Gun Displays code is MIT; its supplied artwork is reserved, as detailed in its `LICENSE.txt`. Including art in this repository for the owner's UI collaboration does not change its license.

Mess Hall v2 feature preview (0.0.3, protocol 23): `feature/messhall-ver-2`. Food costs, gun buffs and compatibility: [Mess Hall notes](docs/MESS-HALL.md); test build: `dist/Arsenal-MessHall-v2-0.0.3-test.zip`.
