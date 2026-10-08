# Mess Hall v4 test build — 0.0.5

Local feature branch: `feature/messhall-ver-4`, based on v3 `2ecd127`.
The owner authorized building, testing, fixes and a local test bundle on 2026-10-08.
The owner authorized committing and pushing to the v4 feature branch. No merge, PR, tag or public release is part of this round. Network protocol stays 24.

## Install the test bundle

Use `dist/Arsenal-MessHall-v4-0.0.5-test.zip` and a fresh world. Choose either `pack/` or `standalone/`; copy its four JARs to both client and server, replacing previous versions. Never install both editions together.

The bundle includes the newly required **TaCZ Attributes 1.4** JAR separately, with attribution, MIT terms and SHA-512 checksums. It is not embedded in the beacon JAR. Keep **TaCZ 1.1.8-hotfix2** installed ([author download](https://www.curseforge.com/minecraft/mc-mods/timeless-and-classics-zero/files/9037989)). The pack edition also needs the matching full Create/KubeJS pack and scripts. Optional LesRaisins 0.4.3 is not bundled.

JAR versions remain beacon/guide 0.21.0 and displays 1.1.0; project version and checksums identify this feature build. The repository's `CLAUDE.md` now requires newly added mandatory mods in future test bundles.

## Fixes found during validation

* The first build rejected cross-mixin accessors to the backend's injected timestamp field. A cached, type-checked reflective bridge now resolves that field after transformation, with a clear error if the pinned backend ABI changes.
* A real damage-event test exposed pointed dripstone matching vanilla fall before the intended spikes class. Spikes now takes precedence, so dripstone shares resistance with other spike traps.
* Test harness corrections are recorded separately from gameplay fixes: exact game-test name selection, valid client simulation-distance settings, a ticking observer for the daylight control, and graceful server shutdown. Forge generates listener wrapper names from package/simple-class/method/event names; the new nested `Client.tick` collided with the older standalone smoke handler. Renaming it to `ReloadSmokeClient.observeReload` gives it a distinct wrapper.

## Recorded results

* JDK 17 `build releaseJars`: both editions pass. 70 beacon unit tests pass; the unchanged 7 guide tests remain Gradle up-to-date and are not counted as newly rerun.
* Production Forge standalone server: all 20 cases pass (5 original kitchen, 6 native gun/lifecycle, 4 v3 food/effect and 5 v4 raid/backend cases). Native AK body damage is 6.0 baseline / 6.48 field / 6.96 home. Empty reload ammo-transfer times were 2669 / 2399 / 2198 ms respectively; these are one fixture run, not a performance benchmark.
* All eleven trap classes pass first-wave/full damage and all four resistance steps, independent later exposure, serialization and reset. Owned flame arrows, weapon burn/poison, marked turret shots and support lightning remain effective after trap immunity. Daylight leaves a marked zombie unlit, while the control burns and combat ignition damages the marked zombie.
* Attribute ownership/player isolation and home/field/neutral changes during native reload pass. The original lifecycle cases cover meal replacement, expiry, death and saved state.
* With optional LesRaisins 0.4.3 installed, radius/owner tests pass. Loading without the optional addon also passes.
* A real stop/restart retains 9 servings and exactly 12,345 remaining field ticks.
* Real client + dedicated server: native reload clips observed at 1.0×, 1.1× and 1.2× across home/field changes, meal removal and restoration (45 active-clip observations). Native ammunition transfer and finished-runner reset pass. The final fixture has a safe field landing pad; the retained screenshot shows the completed reload. Earlier handler-collision attempts are not passing animation checks.

`MESS-HALL-VALIDATION-0.0.5.json` identifies each tested JAR and the delivery artifacts. ZIP-entry comparison confirms that all production gameplay entries in the delivery beacon match the passing server and optional-addon builds; only the opt-in QA fixture classes changed afterward. Short evidence extracts are in `docs/validation/messhall-v4/`.

## Reproduce

Use Java 17 and the pinned Gradle dependencies:

```sh
./gradlew --no-daemon --offline build releaseJars
python tools/qa/run-v4-server.py --runtime /path/to/prepared-runtime --baseline --seed
python tools/qa/run-v4-server.py --runtime /path/to/prepared-runtime --instance /path/from/INSTANCE --restart-check --cases
python tools/qa/run-v4-server.py --runtime /path/to/prepared-runtime --optional-grenades --cases v4optionalgrenadeownerandradius
python tools/qa/run-v4-client.py --runtime /path/to/prepared-runtime
python tools/release/package-messhall-v4.py --backend-jar /path/to/tacz-attributes-1.4.jar
```

The runtime must already contain Forge 1.20.1-47.4.20 under `runtime/libraries`, the exact downloaded dependency JARs under `downloads`, and a `test-runs` directory. Client smoke additionally uses the retained official launcher manifest/assets/natives and Xvfb described in the cloud setup. These runners do not install that runtime. In this workspace, source `/workspace/.eternal-defense/activate.sh` first. Forge/Gradle need localhost networking even for offline runs.

Server fixtures are opt-in with `-Darsenal.messHallTests=true`. Run `/mess-hall-v4-test <lowercase-method-name>` one case at a time, since campaign state is shared. The live reload fixture additionally requires `-Darsenal.v4ClientTests=true` on its isolated server/client. Never enable destructive QA fixtures in a play world. Ordinary launches leave them inactive.

Each run retains console logs, world saves, installed-JAR checksums and `run-results.json` outside the repository. The checked-in validation JSON records successful cases and artifact identity. A failed or interrupted attempt is not a passing result.

## Remaining playtests

Full Create/KubeJS pack runtime, two human defenders/Essential co-op, long survival balance, custom gunpacks with private clocks or animation conventions, exhaustive reload cancel/swap/death/disconnect and shell-stage behavior, and source-erasing third-party turret integrations remain playtests. A standalone dedicated server with fake players is not a complete multiplayer session. One real client observing native clips cannot establish animation quality for every gunpack.
