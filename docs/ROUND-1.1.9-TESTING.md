# 1.1.9: what was tested

The owner's list after 1.1.8: the elite health bar red instead of white; a Badass V zombie's head hitbox was in its legs (shooting its head gave no headshot mark, its legs did); the Badass slowdown halved, creepers too; then one guaranteed elite in the introduction raid, its Badass locked to level I. On `dev`. Build: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew --no-daemon --offline build releaseJars` (2026-10-15). Evidence: `docs/validation/release-1.1.9/` (the check lines of the final run and its screenshots).

## The white bar, reproduced first

The owner's exported profile carries Oculus 1.8.0 with Embeddium 0.3.31 and the MakeUp UltraFast 9.5g shader pack (shaders on, bloom off), plus Entity Model Features, Entity Texture Features and Fresh Animations. The 1.1.8 client was run with Oculus, Embeddium, MakeUp and the owner's shader settings: every elite ring and health bar was drawn white, while the raid's red mark (its colour is in its texture) stayed red. The shader pack drops the vertex tint the rings and bars were coloured with. (That picture was not kept; the run's check lines were 19 of 19, as in 1.1.8.) Entity Model Features could not be loaded in the development client (its mixins name game methods by their production names without a remapping table), so Fresh Animations was not part of any run.

## Unit tests

`:eternal-defense:test` 486 pass, 0 fail. New `Release119Test` (6 tests): every kind and level has a ring texture whose own pixels are its colour (and the spark strip too), with Badass IV and V red; the texture tool's colours equal the code's; the bar is a red texture over a dark one, drawn like the raid mark, and no kind colour is passed as a tint; the slowdown halved (2.5% a level, a creeper 3.75%); the eyes grow with the body, the head band and its mixin; the introduction raid's one elite and Badass I. `Release116Test` follows the halved numbers; `Release118Test` follows the new ring code and guide lines. `:eternal-defense-displays:test` 7 pass.

## Real client

`tools/qa/QaRelease119.java.txt`, run with `QA_CREATE=1` and `QA_MODS` holding LesRaisins 0.4.3, GeckoLib 4.8.4, Born in Chaos 1.7.5, Cataclysm 3.31, Lionfish API 3.0, Curios 5.14.1, **Oculus 1.8.0 and Embeddium 0.3.31**, the **MakeUp UltraFast 9.5g** pack with the owner's `oculus.properties` and pack settings, plus TaCZ 1.1.8-hotfix2, TaCZ Attributes 1.4, Create 6.0.8 and the owner's two gun packs. A new flat world, survival, Normal; window 1280 x 720. **12 of 12 checks pass.** Mixin class export was on.

* **Shaders:** Oculus reports the MakeUp pack in use.
* **Slowdown:** `elite badass 5 minecraft:zombie` has a speed modifier of -0.125, `elite badass 5 minecraft:creeper` -0.1875; the replies say "12.5% slower" and "18.75% slower" (a Badass III Dread Hound "7.5% slower").
* **Eyes:** the Badass V zombie's eye height is 8.70 on the server and the client (its box 9.75 tall); a plain zombie's 1.74.
* **Headshots, by TaCZ's own hit test** (`EntityUtil.getHitResult`, a ray straight through the mob): the giant's head at 7.6, 8.7 and 9.6 blocks up is a headshot; 1.0, 1.74 (the old head height, at its shins), 4.0 and 7.0 are body hits. A plain zombie: 1.74 and 1.95 head, 1.0 body (TaCZ's own band). The transformed `EntityUtil` (exported) calls the hook at its three returns.
* **Colours:** six elites at night (Badass V zombie and creeper, Healer, Speedster, Vampiric zombies, a Badass III Dread Hound), 6 rings and 6 bars drawn. With the shader pack: red bars over dark tracks on all six, and the rings in their colours (red for the two Badass V, gold for the Badass III hound, crimson, yellow, green); the same scene with shaders switched off through Oculus' own setting: the same colours. Pictures of both and of the giant zombie up close.
* **The first raid:** 300 elite rolls made as in the introduction raid at raid level 9: 93 Badass, all level I. Then a real introduction raid (a new campaign's first raid, `EternalDefense.begin`), each raider killed two seconds after it appeared: 18 raiders, exactly one elite (a Flak Armor Husk, announced in chat in wave 1), the raid won and the first raid marked done.

## Jars

All four jars (both builds of the main mod, the gun guide, the displays): no test, QA or note files; every PNG holds image chunks only (the thirteen new elite textures too); no tool, assistant or session names in text, data or class files (the one match is TaCZ's "PEQ6 ILLM" laser in the catalogue, as before).

## Network

Protocol **43**, unchanged from 1.1.8 (no packet changed): 1.1.8 and 1.1.9 can join each other.

## Not tested

* Fresh Animations / Entity Model Features with the elites (they would not load in the development client); the owner's full pack.
* A real gun fired by a player at a giant's head (the hit test TaCZ's bullets call was driven directly); the headshot hit mark on screen.
* Shader packs other than MakeUp UltraFast.
* A dedicated server and two players; window sizes other than 1280 x 720.
