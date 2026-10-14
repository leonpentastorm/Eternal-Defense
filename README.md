# Eternal Defense

Source of the Eternal Defense mods for Minecraft 1.20.1 (Forge 47): defend a beacon base against night raids, build a Create factory, arm it with TaCZ guns, and run forward bases, supply lines and kitchens.

**Version 1.0.0** (file `VERSION`, tag `v1.0.0`).

| Project | Jar | Purpose |
| --- | --- | --- |
| `custom-mods/eternal-defense` | `eternal-defense-1.0.0.jar`, or `eternal-defense-1.0.0-novoice.jar` | The defense beacon, raids, support gear, Weapon Platform, Exchange, forward bases, kitchens, Logistics Hub |
| `custom-mods/eternal-defense-gun-guide` | `eternal-defense-gun-guide-1.0.0.jar` | Client HUD with the held gun's controls and a jam alert |
| `custom-mods/eternal-defense-displays` | `eternal-defense-displays-1.0.0.jar` | Weapon stands, racks and glass cases |

The main mod comes in two builds: with the radio voice lines, and **no-voice**, where each spoken radio line plays a game sound instead. Install one of them. The required mods (TaCZ, TaCZ Attributes, Create, KubeJS, KubeJS Create, LesRaisins Tactical Equipments, JEI) and the test pack are described in [PACK-EDITION.md](docs/PACK-EDITION.md). 1.0.0 renamed every id: start a fresh world.

## Build

Requires **JDK 17**. Gradle 8.8, ForgeGradle 6.0.54, Minecraft 1.20.1 and Forge 47.4.20 are pinned; the wrapper downloads Gradle.

```sh
./gradlew --no-daemon build releaseJars
```

On Windows: `.\gradlew.bat --no-daemon build releaseJars`.

Each project's `build/libs` then holds its jar; the main project also holds the no-voice jar. The displays project also makes a sources jar for developers. `build` runs the unit tests. The first dependency resolution needs network access (TaCZ and TaCZ Attributes from Curse Maven, JEI, Create for the compat code); offline builds need those artifacts cached. For a cloud environment see [NETWORK-ALLOWLIST.txt](docs/NETWORK-ALLOWLIST.txt) and [CLOUD-SETUP.md](docs/CLOUD-SETUP.md).

GameTests and their test structures are in the `gametest` source set: development runs load them, the release jars leave them out.

## Documentation

* [docs/HANDOFF.md](docs/HANDOFF.md): where the project stands, how to build and test, what is unverified.
* [docs/GAME-DESIGN-DOCUMENT.md](docs/GAME-DESIGN-DOCUMENT.md): the rules and numbers.
* [docs/CHANGELOG.md](docs/CHANGELOG.md): every release.
* [docs/GUIDE-STYLE.md](docs/GUIDE-STYLE.md): how the in-game guide text is written.
* `pack-integration`: the pack's KubeJS recipes and FTB Quests.

## Licensing

Each mod keeps the license declared in its `mods.toml` (MIT for the code). Gun Displays code is MIT; its supplied artwork is All Rights Reserved by its creator, as its `LICENSE.txt` details. Third-party mods are downloaded from their authors and are not part of this repository.
