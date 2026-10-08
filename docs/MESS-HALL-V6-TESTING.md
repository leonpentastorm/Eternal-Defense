# Mess Hall v6: actual validation

Project **0.0.7**, feature **`feature/messhall-ver-6`**, protocol **26**. Both pack and standalone editions build with Java 17. The final build passes **74 beacon unit tests**; the **7 Guide tests** remain Gradle up-to-date. Standalone runtime uses Forge 47.4.20, TaCZ 1.1.8-hotfix2, TaCZ Attributes 1.4 and author Apocalypse 1.1.4_F. The final live client and restart use the exact delivery JAR. All server gameplay classes/data match the 36-case passing JAR; only the Mess Hall renderer, language text and new gray bread sprite changed afterward. Entry comparison and SHA-512 evidence are in `MESS-HALL-VALIDATION-0.0.7.json`.

## Gameplay and persistence

**All 36 server gameplay cases pass:** five original kitchen, six native gun/lifecycle, six v6, ten v5, nine v3/v4. Every combination of three out of eleven ordinary effects (**165 triples**) is checked at all four tiers (**660 compositions**): no automatic legendary, exact selected effects, ×2 only at Mk IV. All fifteen legendary recipes retain explicit selection, replacement, distinct-item doubling and connector contributors. Native menu packet round trips cover every recipe button; forged client meal previews cannot alter preparation.

V6 checks exercise separate bread filtering/payment/persistence, blocked-output atomicity, bread intentionally contributing Grain in mixing slots, disabled Stew base/output, full milk buckets and four bottle doses, returned empty containers, full/blocked tank refusal, water rejection, Forge milk enabling, milk recipe output, complete potion/meal cleanup and surviving unrelated attribute ownership. Cleared buffs remain absent after tick/refresh and saved-state reload. Both dispenser stock menus reject held main/offhand items and open with empty hands. Prior trap learning, sunlight/combat-fire, weapon exclusion, reload, shovel and workshop regressions remain passing.

A real stop/restart retains **2,750 mB milk**, **three pending Milk Bottles**, the returned bucket, **three base bread**, **nine stew servings** and exactly **12,345 field ticks**. No old-world migration is needed under the owner’s fresh-world policy.

## Real client and artwork

A real Forge client/dedicated server pass **13 phases** at 1280×720 / GUI scale 3 (**240-pixel GUI height**), with AppleSkin 2.5.1: separate base/ordinary sandwich, three doubled ordinary stew effects, explicitly selected Firepower plus ordinary Swim, both cookbook tabs, milk fill bar, bowl stock, all eleven compact fabrication cards, placed owner artwork/shovel and all four equipment upgrade screens. Ingredient tooltips show kitchen information within the same AppleSkin/vanilla tooltip. Creative rebuilding confirms one Mess Hall and the new milk dispenser. Screenshots were visually inspected; evidence lives in `validation/messhall-v6/`.

All **ten supplied model/texture files match archive bytes exactly**. Resource regeneration preserves these files, controller inheritance and current Field Guide text. The empty base uses a separate original gray bread sprite, avoiding a hue-preserving tint of vanilla bread. Owner source ZIP/provenance is in `art/kitchen-extras-v6/`.

## Corrections found during QA

The first live client exposed oversized selection IDs: native Minecraft menu packets use signed bytes. V6 uses IDs **40–65**, with a packet-codec regression check and successful live selection. Returning from the cookbook rebuilds pot widgets without retaining old entries. Cookbook pagination uses three recipe rows at small GUI sizes, with four at larger sizes; ×1/×2 rules are visible and full values have tooltips. Screenshots identified overlapping linked-pot/quantity labels and excessive sandwich hint height; linked counts now live in the upper status card and hints stop above the quantity bar. The old bowl-drop fixture now waits for real chunk entity visibility rather than a fixed ten helper ticks. QA startup waits tolerate slower concurrent boot, and client startup recognizes a complete pass-marker line rather than matching that text inside a traceback. Interrupted/failed attempts are excluded from pass totals.

## Reproduce

With a prepared runtime and cached dependencies, from repository root:

```sh
./gradlew --no-daemon --offline build releaseJars
python3 tools/qa/run-v6-server.py --runtime /path/to/runtime --baseline --regressions --seed
python3 tools/qa/run-v6-server.py --runtime /path/to/runtime --instance /path/from/INSTANCE --restart-check --cases
python3 tools/qa/run-v6-client.py --runtime /path/to/runtime --gui-scale 3 --food-tooltip-jar /path/to/appleskin-forge-mc1.20.1-2.5.1.jar
python3 tools/release/package-messhall-v6.py --backend-jar /path/to/tacz-attributes-1.4.jar
```

Runtime prerequisites follow v5’s retained official Forge/launcher/assets/natives/Xvfb setup. In this workspace source `/workspace/.eternal-defense/activate.sh` first; offline Gradle still needs localhost access. V6 server commands require `-Darsenal.messHallTests=true`; live UI also requires `-Darsenal.v6ClientTests=true` on both ends. These opt-in flags are off in normal gameplay. Use disposable worlds. Historical v5 UI scripts are not the current v6 UI target.

The test ZIP contains **eight JARs**: all three Arsenal mods plus separate unmodified **TaCZ Attributes 1.4** in each edition. Choose one edition; install its four JARs on both client/server, alongside existing TaCZ. SHA-512 manifests, required backend author/license details and actual QA evidence are included. Apocalypse and AppleSkin are QA-only and are not redistributed.

## Remaining playtests

The matching complete Create/KubeJS modpack is compiled but not played here. Two-human co-op, long survival balance, actual third-party pipes/milk-container addons, other UI/gunpack combinations and the owner’s exact jam addon/original N-unjam crash remain playtests. Milk capability validation is checked on Forge fluid objects; that is not a claim that every pipe or container addon works. Existing fuel-gun crafting exclusion remains the approved fallback for the unreproduced jam crash.
