# Mess Hall v5 test build — 0.0.6

Feature branch `feature/messhall-ver-5`, based on v4 `73dbebf`. The owner authorized implementation, testing, packaging and pushing this feature branch. No merge to `dev`/`main`, PR, tag or public release. Network protocol **25** requires matching client/server JARs.

## Installation

Use `dist/Arsenal-MessHall-v5-0.0.6-test.zip` with a **fresh world**. Choose one edition folder and install its four JARs on both client and server, replacing older copies. Never combine both editions. Beacon/Guide stay 0.21.0 and Displays 1.1.0; use the project version and SHA-512 checksums to identify this build.

The required **TaCZ Attributes 1.4** JAR is included separately in each folder, with source, MIT terms and checksums. Keep TaCZ **1.1.8-hotfix2** installed ([author download](https://www.curseforge.com/minecraft/mc-mods/timeless-and-classics-zero/files/9037989)); it is an existing dependency and is not bundled. Pack edition also requires the matching full Create/KubeJS pack and scripts. The Apocalypse gunpack, AppleSkin and optional LesRaisins addon are not bundled. This is an update/test bundle, not a complete modpack.

## Recorded checks

* JDK 17 offline `build releaseJars`: both editions build successfully; 74 beacon unit tests pass with zero failures/errors/skips. The unchanged seven Guide unit results remain Gradle up-to-date, rather than newly rerun.
* Ten packaged standalone Forge server cases pass with TaCZ 1.1.8-hotfix2, TaCZ Attributes 1.4 and author Apocalypse 1.1.4_F loaded. They cover all fifteen legendary recipes, replacing ordinary bonuses, four-food Mk IV doubling, split-stack rejection, server preparation, disabled Stew output, two-tall bowl interaction/filtering/persistence/drop count, independent table upgrades, all ten fabrication purchases and failed-payment atomicity, owned backend modifiers/cleanup, zero-damage Knockback II with Strength III, and actual loaded FUEL gun exclusions/direct request rejection.

* The final fresh-world standalone server passes **all 30 gameplay cases**: five original kitchen, six native gun/lifecycle, ten v5 and nine v3/v4 raid/backend/effect regressions. Trap learning, weapon exclusions from resistance, sunlight-only protection and continued combat fire all pass.
* A real stop/restart of the final delivery JAR retains **9 pot servings and exactly 12,345 remaining field ticks**.

A real Forge client and dedicated server pass all eight UI phases at 1280×720 / GUI scale 3 (240-pixel GUI height), with AppleSkin 2.5.1 installed: Mk I/Mk IV Stew previews, legendary connectors and ingredient/recipe hover text, the one-slot dispenser, Workshop Fabrication, and all four table upgrade views. Actual item tooltips show AppleSkin nutrition plus kitchen information together. Client creative-tab rebuilding confirms exactly one Mess Hall named “Mess Hall”. Material icon tooltips and the larger current-table frame were visually inspected. Screenshots and UI pass markers are retained in `docs/validation/messhall-v5/`. Every delivery gameplay/UI/resource entry matches the passing client JAR; only three opt-in server fixture entries changed afterward. The final server and restart use the exact delivery beacon JAR. Exact artifacts and evidence are identified by `MESS-HALL-VALIDATION-0.0.6.json`.

## Fixture corrections

QA fixtures are distinct from production behavior. Forge EventBus generates wrappers using simple class names: the original new nested Runner collided with v4, so v5 uses a unique listener class. Bowl-drop verification waits for entity visibility in ticking chunks before counting stock. The UI observer closes the previous container before opening the beacon panel; both server and client receive the same Apocalypse gunpack. Minecraft forces AWT headless, so the external X11 runner positions the screenshot cursor. The daylight fixture uses a fixed seed and waits for observed vanilla ignition and actual entity ticks before verifying combat fire, avoiding a probabilistic fixed-time assertion. Older composition assertions now expect the deliberately changed v5 recipes while retaining batch, container-return, concurrent-cook, native gun and lifecycle checks. Failed/interrupted attempts are not counted as passes.

## Reproduce

Use Java 17 and the cached dependencies, from repository root:

```sh
./gradlew --no-daemon --offline build releaseJars
python3 tools/qa/run-v5-server.py --runtime /path/to/prepared-runtime --seed
python3 tools/qa/run-v5-server.py --runtime /path/to/prepared-runtime --instance /path/from/INSTANCE --restart-check --baseline --regressions
python3 tools/qa/run-v5-client.py --runtime /path/to/prepared-runtime --gui-scale 3 --food-tooltip-jar /path/to/appleskin-forge-mc1.20.1-2.5.1.jar
python3 tools/release/package-messhall-v5.py --backend-jar /path/to/tacz-attributes-1.4.jar
```

The runtime must already contain Forge 1.20.1 / 47.4.20 under `runtime/libraries`, pinned dependency JARs plus the author's Apocalypse ZIP under `downloads`, and a `test-runs` directory. Client checks also need the official launcher manifest/assets/natives and Xvfb, following the existing retained-runtime setup. These scripts do not install the runtime. In this workspace source `/workspace/.eternal-defense/activate.sh` first; localhost networking is required even offline.

Server fixtures use `-Darsenal.messHallTests=true`, `/mess-hall-v5-test <lowercase-case>`. V3/v4 regressions use `/mess-hall-v4-test <case>`. The UI observer additionally requires `-Darsenal.v5ClientTests=true` on both ends. These flags are off in normal launches. Use only disposable worlds: fixtures replace arena/campaign state. Every run retains console output, installed-JAR hashes, saves and successful-case JSON outside the repository.

## Remaining owner playtests

The matching complete Create/KubeJS pack, exact Apocalypse/Gun Durability versions and original N-unjam crash, other tooltip mods, two-human co-op, long survival balance and exhaustive third-party weapon behavior remain owner playtests. The reported jam crash was not reproduced; the approved fallback excludes the incompatible fuel guns from crafting. New backend modifiers are verified on real registered attributes, but every third-party animation/spread/shot script is not calibrated. Prior v4 native client reload observations remain historical; v5 client QA exercises menus. The pack edition is compiled and bundled, not claimed as a fully played modpack.
