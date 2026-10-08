# Eternal Defense / Create Arsenal

Editable Java, assets, recipes and Gradle projects for the three Create Arsenal mods. This is the **0.21.0 source handoff** for the UI redesign, rather than a decompiled release archive.

| Project | Version | Purpose |
| --- | --- | --- |
| `custom-mods/arsenal-beacon` | 0.21.0 | Shared defense beacon, raid campaign, progression and Universal Weapon Platform |
| `custom-mods/arsenal-gun-guide` | 0.21.0 | Client weapon controls guide and supported gun-jam warning |
| `custom-mods/arsenal-displays` | 1.1.0 | Eleven weapon stands, racks and glass cases |

Read [ARTIST-BRIEF.md](ARTIST-BRIEF.md) for the player-facing pitch and visual direction, then [the UI development handoff](docs/UI-DEVELOPMENT.md) for code locations and constraints. The redesign is described in [docs/UI-REDESIGN-NOTES.md](docs/UI-REDESIGN-NOTES.md) and the Ardent Energy / base support systems in [docs/ENERGY-AND-SUPPORT.md](docs/ENERGY-AND-SUPPORT.md). Both the curated modpack and standalone editions are included. They share Java and registry IDs, with different release metadata, recipes and guide content.

Project preview **0.0.5 / Mess Hall v4** is local work on `feature/messhall-ver-4`, based on v3 commit `2ecd1276d58e430dda1ef6a012c82beccc9d57f4`. [Prepared food](docs/MESS-HALL.md) now uses TaCZ Attributes 1.4 for gun damage/reload, preserves reload progress when home bonuses change, and supports native explosive rounds plus an optional LesRaisins grenade adapter. [Raid trap adaptation](docs/RAID-ADAPTATION.md) raises resistance in later waves; raiders avoid sunlight ignition while remaining vulnerable to weapon fire. The painted v3 kitchens and vanilla meal display remain in place. Protocol stays **24** because packets did not change. Project versions (`VERSION`) remain separate from the JAR build versions above.

V4 has a **local test build**: [installation and actual validation](docs/MESS-HALL-V4-TESTING.md). Both editions build and the standalone server checks pass with the required backend. The owner authorized delivery to the v4 feature branch; no PR or public release is part of this round. Until 1.0 the owner starts a fresh world for each feature; old kitchen footprint migration is outside this round. Existing meal save formats are retained.

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

After building, each project's `build/libs` contains its pack JAR and `-standalone.jar`. The displays project also produces a sources JAR, which is for developers rather than installation. `build` runs the Java unit tests; `releaseJars` additionally builds all three standalone releases. The beacon now compiles against pinned public TaCZ and TaCZ Attributes artifacts from Curse Maven and JEI APIs. First dependency resolution needs network access; offline builds require those artifacts and MixinGradle already cached. No launcher-instance dependency paths or embedded addon JARs are used. **These commands passed for v4; see the validation record for runtime coverage.**

For a cloud environment's first build, see [NETWORK-ALLOWLIST.txt](docs/NETWORK-ALLOWLIST.txt) and [network setup](docs/CLOUD-SETUP.md).

## Playtest the correct edition

Install exactly one edition of each mod, with its runtime dependencies, into a separate Forge test instance. Pack and standalone JARs use the same mod IDs and cannot be installed together. A headless build does not confirm that a screen looks correct in Minecraft.

- **Pack beacon v4:** requires TaCZ **1.1.8-hotfix2**, [leopoko's TaCZ Attributes **1.4**](https://www.curseforge.com/minecraft/mc-mods/tacz-attributes/files/8470731), Create and KubeJS, plus the curated recipes/catalogue from the complete Create Arsenal pack. The small `pack-integration` folder provides authored recipe and quest context; it is not the complete modpack.
- **Standalone beacon v4:** requires the same TaCZ and TaCZ Attributes versions on clients and server. Create, KubeJS, JEI and Gun Displays are optional for the beacon. Gun Guide is client-only. See [standalone installation and balance](docs/STANDALONE.md).

The build pins TaCZ [file 9037989](https://www.curseforge.com/minecraft/mc-mods/timeless-and-classics-zero/files/9037989) and TaCZ Attributes project 1113285 / file 8470731. LesRaisins Tactical Equipements (`lrtactical`) is optional; its Demolition adapter is gated to 0.4.3 (`[0.4.3,0.4.4)`). TAA, GunsmithLib, Apotheosis and Apothic Attributes are not required. Download third-party mods from their authors; they are not vendored into the source projects.

## Contents and baseline

- `src/main/java`: shared behavior and screens.
- `src/main/resources`: pack release metadata, authored assets, language files and data.
- `src/standalone/resources`: standalone metadata and replacement recipes.
- `src/test/java`: Java unit tests where present.
- `pack-integration`: authored KubeJS recipes and FTB Quests for reference.
- `docs/RELEASE-VALIDATION-0.21.0.json`: prior release validation, with its scope and limits. It is a historical baseline, not a claim that future UI changes have been tested.

The original `Arsenal-Standalone-Mods-0.21.0.zip` remains as the initial release artifact. Edit the source projects for the redesign. Do not unpack release JARs over them.

Licensing remains as declared by each mod. Gun Displays code is MIT; its supplied artwork is reserved, as detailed in its `LICENSE.txt`. Including art in this repository for the owner's UI collaboration does not change its license.

Previous v2/v3 distributions and validation records are historical artifacts. They do not contain the v4 changes or validate this backend.
