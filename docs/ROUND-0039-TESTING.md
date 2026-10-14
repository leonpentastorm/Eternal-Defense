# 0.0.39: idle animations: testing

Branch `feature/animation-pass`, made from `feature/drink-station` at 0.0.38.

## How it was run

* **Unit tests** (`:eternal-defense:test`), JDK 17.
* **Real client** of each edition (Forge 47.4.20 development runtime with TaCZ, `xvfb`, 1280 x 720 at GUI scale 2), the new harness `tools/qa/QaAnim.java.txt`. On a fresh flat world with the day cycle, weather, clouds, mob spawning, the beacon's beam and its zone outline off, it builds every animated block in a row (the FOB Beacon, the Satellite Beacon, the Command Table, the Defense Beacon Mk I to IV, the Support Platform Mk I and Mk IV, the Tactical Enchanting Table, the Mess Hall Mk I, the Cook Pot, the Brew Pot, the MRE Station, the Support Cannon). It hides the HUD and makes the camera a spectator, so it stays where it is put. For each block it takes two frames 0.8 s apart with the animations on, and two with the Settings switch off. It also films the flag and the dish, takes the placement ghost, and checks the Settings tab. It then rebuilds an old world's blocks: the four blocks without their block entity, their chunk saved and unloaded, then visited again.

## Results

### Unit tests: 375 of 375 pass

* New `Round0039Test` (11):
  * The renderer's part list is exactly the generator's and the files in `models/block/anim`.
  * For every split model, the static model plus its parts give back the owner's model element for element. The flag's four strips meet edge to edge, and their texture runs on without a gap.
  * The blockstates draw the `_static` models; the item models keep the owner's whole model.
  * The flag's hinges are the strips' own edges, and the other pivots checked sit on their parts: the dish's axle and mast, the spit rod, the MRE rollers, the book's spine, the sparks' centres, the hook's cable and the whips.
  * Over the whole range (turn -110 to +50, tilt -15 to +20, in 10 and 5 degree steps), no point of the dish enters the rest of the block or its whip, and the dish's motion never leaves that range.
  * Every motion stays in its range and never jumps between frames 0.05 s apart. Two blocks side by side do not move in step, and the same block at the same time is in the same pose.
  * The beacon rings sit square with the model most of the time; the press holds up and holds down.
  * The four blocks without data have their block entity and its type; every renderer draws the parts; the platform draws them before its nameplate's early exit; the placement ghost draws the parts at rest.
  * Blocks from before 0.0.39 get their block entity when their chunk loads (server side, the same four blocks, only sections whose palette may hold them, no block changed).
  * At rest, and with the switch off, nothing moves. The switch is on by default, per player, with its Settings label and note.
* Changed: none (`LangKeysTest` and the guide style tests pass with the switch's label and note and the Field Guide's new Settings line).

### Real client

* **First run** (both editions; 29 of 41 standalone, 30 of 41 pack; logs not kept). Every "off" frame pair differed, and the Support Platform Mk I showed no motion. The screenshots showed why: the player starts with the starting kit's Defense Beacon in hand, so its placement ghost and pulsing zone lines were drawn in every frame. Those lines changed between frames, and the ghost stood in front of the platform. The harness now empties the inventory first. No fault in the animations.
* **Second run** (40 of 46 standalone, 43 of 46 pack). Every "off" pair was identical. The remaining failures:
  * Some frame comparisons read a screenshot while it was still being written (the game saves screenshots on another thread): the harness now retries for up to 3 s.
  * The Support Platform Mk I and IV still showed no motion. Their emitter sits on top of the 2-block cabinet, and the camera never reached its planned spot: a creative player without flying fell to the ground after each teleport, so every shot was taken from standing height. The camera is now a spectator, which stays where it is put, and the platforms are filmed from just above their top.
* **Final runs: standalone 46 of 46** (`docs/validation/round-0039/checks-standalone.txt`), **pack 46 of 46** (`checks-pack.txt`).
  * A pack run before that had 45 of 46 (`checks-pack-earlier-run.txt`). Its one failure: the harness read its "saved chunk" value before the server had read the chunk from disk (it still held the harness's "not read yet" marker, -3). The check now waits for that read. The standalone final run used the harness before this wait was added; its read finished in time.
  * In both editions:
    * All 52 part models and the cannon's turret without its hook load, and every animated block's static model loads (no missing-model cube).
    * All 15 blocks have a block entity and a renderer on the client.
    * For each of the 15 blocks (FOB Beacon, Satellite Beacon, Command Table, Defense Beacon Mk I to IV, Support Platform Mk I and IV, Tactical Enchanting Table, Mess Hall Mk I, Cook Pot, Brew Pot, MRE Station, Support Cannon), frames 0.8 s apart differ with the animations on: standalone between 488 and 6,058 pixels, pack between 151 and 6,320. With the Settings switch off they are identical to the pixel.
    * The Settings tab has the "Idle animations" switch, on, with its note. Pressing it turns the animations off and saves that in the client config; pressing it again turns them on.
    * The tab's texts do not overlap each other or a button at 640 x 360, 480 x 270 and 427 x 240 GUI pixels. With the game's music volume at 0, the music switch says "now 0%".
  * **Old world:** in a far chunk, a FOB Beacon, a Satellite Beacon, a Command Table and a Tactical Enchanting Table stand without their block entity. That chunk is saved and unloaded, and the chunk on disk holds no block entity for them. On a visit, all four have theirs again on the server, and on the client too, with its renderer (received with the chunk). They are drawn with their flag and dish (`a09`).
* **Screenshots** (`docs/validation/round-0039/`):
  * `a01`: every block, two frames 0.8 s apart (both editions). The dish has turned, the MRE press is down, the platform emitters have turned, the cannon hook has swung, and the book, rings and flames have moved.
  * `a02`: the FOB flag, six frames 0.4 s apart.
  * `a03`: the satellite dish, six frames 0.4 s apart, turning to a new heading.
  * `a04`: the dish seen from the side, four frames 3 s apart.
  * `a05`: the placement ghost of a Satellite Beacon, with its dish and whip at rest.
  * `a06`: the Settings tab just after the switch was pressed (it reads OFF; the ON state was checked before the press).
  * `a07`: the tab at 1280 x 720, GUI scale 3.
  * `a08`: the music volume note.
  * `a09`: the old-world blocks after the visit.
* Looked at by eye in the films and pairs: no part leaves a gap in its model at rest or in motion; the flag's strips stay joined; the dish never touches its antenna or cabinet. These were seen at the harness's distance (a block about a third of the screen tall), not up close in play.

## Not tested

* A dedicated server with several players watching the same blocks (the motions use only the world's time and each block's position, so every client should show the same pose).
* The frame rate with many animated blocks in view; shader packs (Oculus) and Embeddium/Rubidium; the owner's pack itself.
* A world actually made with an older build. The old-world case was rebuilt in the test client (blocks saved without their block entity, unloaded, loaded again), not taken from a real 0.0.38 save.
* The Mess Hall Mk II to IV, gates, catapult, shops, dispensers and the Drink Station were not changed and not filmed.
* Sounds: none were added.
