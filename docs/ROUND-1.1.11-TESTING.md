# 1.1.11: what was tested

The owner, after asking whether gates counter a beacon on a covered one-block sky island: "Do not do 1 and 4, improve the whole raid detection and pathing system as much as you can, i want a tower defense feel. And we are about to add trap towers and sandbags and such. Apply a tag for these call defenses", with six rules (defenses and doors first; melee rush the beacon, ranged focus players; digging with the attack animation and cracks; bridges, teleport only in extreme cases; melee attack from a one-block ring; the parachute squadron instead of withdrawing). On `dev`. Build: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew --no-daemon --offline build releaseJars` (2026-10-16). Evidence: `docs/validation/release-1.1.11/` (the check lines of the final runs and their screenshots).

## Unit tests

`:eternal-defense:test` 495 pass, 0 fail. New `Release1111Test`: the two `defenses` tags (shipped empty, `replace` false); the in-the-way rule (16 blocks of sight, closer than the beacon, no farther from the beacon than the raider plus 4, 6 up or down, and a defense past the beacon is not in the way); the target order for melee and ranged raiders, Creative and Spectator players never attackable; block damage points (3 + 6 per hardness point, 300 at most, bedrock never), damage a swing, crack stages and breaker ids; the attack ring (beside the target, a step lower at most, or on top) and the dig reach; the bridge planner's cases (water, lava, a chasm, a step down, a wall, a drop toward a lower target, a moat that comes back level); the squadron rules (the beacon 4+ above a stuck raider within the zone + 8, bosses after 3 failed gate searches, landing only on the beacon's level or higher); the archer's lift against a one-block target (the solved height matches the simulated arrow within 0.05); the guide's numbers; protocol 43. `StuckRaiderTest`, `RaiderGateTest`, `LangKeysTest` and `Round0041Test` follow the new API (the squadron instead of withdrawal; the bridge block has no guide card of its own, the raids card explains it). `:eternal-defense-displays:test` 7 pass.

## Real client

`tools/qa/QaRelease1111.java.txt`, run with `QA_CREATE=1` and `QA_MODS` holding LesRaisins Tactical Equipments 0.4.3, GeckoLib 4.8.4 and Born in Chaos 1.7.5 (TaCZ, Create and JEI come with the development client). A new superflat world, Normal; window 1280 x 720. The camera player is in Creative unless a check needs Survival. Run 7: **35 of 35 checks pass**; run 8, unchanged code, to confirm: **35 of 35** again (same numbers within a swing or a block).

* **Tags and doors:** a data pack tagging hay bales as `eternal_defense:defenses` is read (stone is not tagged); closed doors and fence gates count as doors, open ones and trapdoors do not.
* **The ring around a Mk I beacon:** five zombies and husks and a Born in Chaos Door Knight (GeckoLib) spawned around the beacon all end up in the one-block ring and stay there; in 8 seconds the client saw 22 to 23 swings from each; 71 blows on the beacon, all from the ring while swinging, none from outside; the beacon went from 1000 to 952.
* **Players:** a zombie and a skeleton ignore a Creative player standing beside them and go for the beacon; a zombie fights a Survival player who comes within 4 blocks (2.7 blocks seen) and goes back to the beacon once the player is past the leash (30 blocks); a skeleton that sees a Survival player 17 blocks away shoots at them first.
* **Defenses and doors:** a zombie goes for the tagged bale in its way, another for a closed door in its way; the stand-in they aim at is invisible and players cannot hit it. The bale cracks stage by stage the way a player mines it (stages 1, 3, 5, 6, 8), breaks and is saved to be put back; the raider then goes for the beacon, not for another bale on the far side of the beacon. A skeleton goes for a bale in its way before the beacon and wears it down with arrows from 7 blocks (5 shots). The door is hacked down through all ten stages, both halves saved, the wall beside it left standing.
* **Digging:** a zombie walled off digs through two plank blocks one after the other, each through all ten crack stages, swinging 30 times.
* **Bridges:** a zombie lays 5 planks across a water moat and walks over it (no gate used), another 4 across a 3-wide chasm and stays on the level; all 9 planks are recorded with what they replaced. When the raid ends every plank is gone, all 65 water blocks are back, and no crack or stand-in is left.
* **The sky island:** a beacon 12 blocks up on a covered island over open ground. A zombie under it gets stuck, joins the parachute squadron and jumps right above the beacon; chat says so. It comes down on the island, digs into the cover block over the beacon and hits through.
* **No ground at all:** with no spawn spot and no gate spot, the enemies come by parachute over the zone; chat says so.
* **Superflat:** open ground on a superflat world (bedrock over the void) is a safe spawn spot; a raider shut in a bedrock pit far out goes through a gate (not the squadron) and steps out of the exit gate on open ground outside the zone.
* **Mk IV:** around the 3 x 3 Mk IV structure four zombies attack from the 5 x 5 ring, all swinging; 48 blows, none from outside.

Pictures: the ring around the Mk I beacon, a cracked bale, the moat bridge, the squadron falling over the sky island, the parachute spawns, the Mk IV ring.

## Found in game and fixed before the final runs

* **Superflat worlds rejected every spawn spot:** the air-below check looked under the bedrock into the void. It now stops at the bottom of the world. This was a real bug in earlier versions too (raids on superflat worlds fell back to other spawns).
* **Arrows flew over a one-block target:** the vanilla arc aims for a player's height. Arrows loosed at a defense stand-in now get a lift solved from the arrow's own drag and gravity.
* **The squadron drifted:** a falling raider followed its ground path and, aimed at its own height, kept its old heading in the air (the move control "jumps"). Under a parachute it now steers straight for the beacon, aimed a block below, and holds still once over it. Squadron jump spots past the island's edge are skipped.
* **Stale targets:** a raider kept aiming at a defense that had just broken, or turned to one on the far side of the beacon. The stand-in goes the moment its block breaks, and a defense past the beacon is not in the way.
* **The stand-in showed as visible** to the client's check: it now reports itself invisible.

## Jars

All four jars (both builds of the main mod, the gun guide, the displays): no test, QA or note files (the GameTests and the QA scenario stay out); every PNG holds image chunks only, the new bridge texture too; no tool, assistant or session names in text, data or class files (the one match is TaCZ's "PEQ6 ILLM" laser in the catalogue, as before). The two empty `defenses` tags, the bridge block's files and the new classes are in both builds of the main mod.

## Network

Protocol **43**, unchanged (no packet changed). The new block (`raider_bridge`) and entity type (`defense_point`) mean Forge refuses a 1.1.10 / 1.1.11 mix between client and server.

## Not tested

* A real base on real terrain (hills, caves, forests), the owner's pack, a long raid of many waves.
* A dedicated server and two players.
* Fresh Animations / Entity Model Features (they do not load in the development client) and how the swing looks with them.
* TaCZ Turrets as defenses (counted by namespace; the mod was not loaded).
* Creepers blowing walls in a normal raid (the code path ran in no scenario: no creeper was walled off).
* The GameTests in `src/gametest` (updated for the new API, compiled, not run).
* How long walls of each material hold in play, and whether the sight, leash and squadron numbers feel right: owner's call.
