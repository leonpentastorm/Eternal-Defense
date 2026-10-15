# 1.1.8: what was tested

The owner's questions: will mobs from other mods or model packs break the elites (Badass most of all); give elites an aura or a health bar that shows at night; then a compatibility pass over the other features. On `dev`. Build: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew --no-daemon --offline build releaseJars` (2026-10-15). Evidence: `docs/validation/release-1.1.8/` (the check lines of the final run and its screenshots).

## What was wrong before

Read from the code and confirmed in the client: Born in Chaos' raid specialists (Door Knight, Dread Hound, Bone Imp, Decrepit Skeleton) are drawn by GeckoLib, whose renderer is not a living-entity renderer, so Forge's `RenderLivingEvent` never fires for them. The Badass size and the raid's red mark hung on that event: a Badass Door Knight had a five-times hitbox around a normal-sized model and a misplaced name, and no GeckoLib raid mob had its red mark. Elite powers themselves (attribute modifiers, goals, events) did not depend on the mob's class.

## Unit tests

`:eternal-defense:test` 480 pass, 0 fail. New `Release118Test` (8 tests): the size mixin is client-only and wraps the renderer's own draw; no `RenderLivingEvent` left in the elite or mark code; the overlay pass; one colour per kind and the kind/level code round trip; the marks packet carries elites and kinds (encode/decode) and the protocol is 43; ring and bar render types, textures and guide lines; the lift of a grown mob's name, bar and mark; another mod's boss is not sent at the beacon; none of the other-mod features throws any more, with their fallbacks (ravager boss, cache without the missing item, TaCZ's own packet, no soldiers); every hook into another mod or the renderer has `require=0`. Six older tests moved from protocol 42 to 43; `Release115Test` follows the new hook. `:eternal-defense-displays:test` 7 pass.

## Real client

`tools/qa/QaRelease118.java.txt`, run with `QA_CREATE=1` and `QA_MODS` holding LesRaisins Tactical Equipments 0.4.3, GeckoLib 4.8.4, Born in Chaos 1.7.5, L_Ender's Cataclysm 3.31, Lionfish API 3.0 and Curios 5.14.1 (the owner's pack versions): Eternal Defense, the displays, TaCZ 1.1.8-hotfix2, TaCZ Attributes 1.4, Create 6.0.8, plus the owner's Apocalypse 1.1.7 and TRIS-dyna 1.1.5 gun packs. A new flat world, survival, Normal, at night; window 1280 x 720. Final run (run 2): **19 of 19 checks pass**. Mixin class export was on for the run.

* **Renderers:** the Door Knight's renderer is `DoorKnightRenderer < GeoEntityRenderer < EntityRenderer` (no living-entity renderer); Cataclysm's Koboleton uses a `MobRenderer`. The raid's specialist pool gave only Born in Chaos and Cataclysm mobs (60 draws).
* **Elites on modded mobs, in a raid at night:** `/eternaldefense elite badass 5 born_in_chaos_v1:door_knight`, `healer 1 cataclysm:koboleton`, `speedster 1 minecraft:zombie`, `badass 3 born_in_chaos_v1:dread_hound`, and a plain Door Knight raider beside them. Server hitboxes: Badass V Door Knight 4.50 x 9.50 against 0.90 x 1.90 (five times). The client has the size 5, the same hitbox, each elite's kind (Badass IV+ red) and all five as raid mobs.
* **Overlay:** looking at the five from 34 blocks for 20 ticks: 4 auras, 4 health bars, 5 raid marks a frame.
* **Size around GeckoLib:** the giant's name tag is drawn inside the size (pose scale 5.0 against 1.0 for the plain Door Knight; the Badass III hound 3.0), then the size is undone for it; mobs that are not grown are drawn as they are.
* **Pictures (night, no night vision):** rings in gold (Badass III), yellow (Speedster), green (Healer) and red (Badass V), health bars in the same colours, every mark, including the plain GeckoLib Door Knight's. Run 1 showed the giant's bar, mark and name hidden inside its model (its helmet and raised axe reach about two blocks over the hitbox at five times); they now float above the hitbox by their usual gap times the size, and run 2's picture shows all three over the model. With hitboxes shown, the five-times model fills its hitbox.
* **Bosses:** the Wither (300), Cataclysm's Netherite Monstrosity (600) and Ender Golem (150) placed near the beacon are not given the beacon attack; a zombie and a Door Knight are.
* **Hard raid bosses:** at levels 3 and 4 `HardRaids.spawn` brings Cataclysm's Ender Golem (awake) and Kobolediator (awake, not asleep), not the ravager fallback.
* **Features that call other mods:** a level-3 hard raid cache holds 9 LesRaisins medical kits, 16 M67, 8 smoke and 3 flash grenades; TaCZ Attributes' reload field, TaCZ's explosive-bullet field and 27 ammunition prices are found; no feature switched itself off, and the log has no "unavailable and skipped" line.
* **Hooks still applied** (javap of the exported classes): the size hooks in `EntityRenderDispatcher`; Brawler and Heavy Hand in `ModernKineticGunItem`; all five reload hooks in `LivingEntityReload`; the catalogue split in TaCZ's `NetworkHandler`; the grenade radius in LesRaisins' `GrenadeEntity`. TaCZ's `ExplodeUtil` was not loaded during the run (no TaCZ explosion), so its Demolition hook was not seen.

## Jars

All four jars (both builds of the main mod, the gun guide, the displays): no test, QA or note files; every PNG holds image chunks only (the two new textures too); no tool, assistant or session names in text, data or class files (the one match is TaCZ's "PEQ6 ILLM" laser in the catalogue, as before).

## Network

Protocol **43** (was 42): the raid marks packet carries each elite's kind and level. A 1.1.8 client cannot join a 1.1.4 to 1.1.7 server, nor the other way; the test-build INSTALL says so.

## Not tested

* A feature actually switching itself off: no installed mod had changed, so the fallbacks (ravager boss, cache without an item, TaCZ's own catalogue packet, no soldiers, reloads on TaCZ's timing, Demolition skipping explosive bullets, JEI without costs) were checked in the source and unit tests only.
* Shaders (Oculus), Entity Model Features / Fresh Animations and other model packs; health-bar mods next to ours.
* Elites rolled by a real raid on Born in Chaos and Cataclysm mobs (made by the command here); a Badass Door Knight walking and smashing; the overlay with many mobs in view (frame time).
* A dedicated server and two players; window sizes other than 1280 x 720.
