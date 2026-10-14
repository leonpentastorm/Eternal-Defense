# 0.0.36: the fire support rework (Ablaze, Spotter Arrows, a curse until death): testing

Branch `feature/drink-station`, after 0.0.35.

## How it was run

* **Unit tests** (`:eternal-defense:test`), JDK 17.
* **Real client** of each edition (Forge 47.4.20 development runtime with TaCZ 1.1.8-hotfix2 and its default gun pack, `xvfb`, 1280 x 720 at GUI scale 2), the new harness `tools/qa/QaFire.java.txt` on a fresh flat NORMAL world at night. It spawns flares of the three types the way the cannon's call does (the thrower's upgrades, landed, no cannon placed: the call fires after a 90-degree turn's wait), puts real mobs in their boxes, records every hit before and after the mod's handlers (Forge's `LivingHurtEvent` at the highest and the lowest priority), every knockback (with its cancelled flag) and every extinguish hiss, fires a real TaCZ AK-47 through TaCZ's own `IGunOperator.shoot`, checks the server and the client, and takes screenshots.

## Results

### Unit tests: 345 of 345 pass

* New `Round0036Test` (7): the three types keep their ids and places; Ablaze 4 a second, the Damage upgrade, half for a raid boss, the stronger burn kept, who can burn, the menu's ninth number; that the napalm marks each second and the carpet is kept, the early exit of the tick handler, the weapon's fire every tick, lit again out of water, the burn as weapon damage, vanilla's burn dropped, cleared on death, the handlers registered; Spotter's 4 and the Damage upgrade, Marked 1.30 and 1.15, the menu's figures; every mob hit and marked with no cap (the cap of ten only for the falling arrows), the arrow hit from the flare for its owner, arrows that hit nothing, no tipped arrows, weapon damage, no knockback, invulnerability-bypassing damage left alone, Glowing for good; the curse list, endless, from the flare, no Glowing, no Damage scaling; the menu, the call and the guide texts and their numbers.
* Changed: `RaidFixesTest` (the menu passes nine arguments), `HandTunedRoundTest` (the tipped-arrow test removed).

### Real client: standalone 61 of 61, pack 61 of 61

What the harness checks (`docs/validation/round-0036/checks-*.txt`):
* **Napalm Carpet, Ablaze.** In the burning carpet two husks are set Ablaze at 4 a second (Damage 0); a blaze (fire immune) and a husk with Fire Resistance are not. After the carpet is gone the husk burns 4 a second by the mod's timer (7 burns in 7 s, every one 4), vanilla's own 1 a second burn never lands, it is on fire on the server and on the client, its weapon-fire mark is refreshed, the blaze takes nothing. In the carpet every carpet hit was its full 5 next to Ablaze, and a hit of 6 right after a burn stays 6 (the burn does not use up the invulnerability window). In a pool the husk is in water with its flames out and still takes 4 a second; out of the water it is lit again. In the rain a husk that moves (in a glass cell open to the sky) keeps taking 4 a second with no extinguish hiss at all (the flames show as particles); after the rain it is lit again. A husk that moves, Ablaze, pressed for 15 seconds against a wall of oak planks and logs on a plank floor, next to wool and leaves, with fire tick on: no block caught fire, all 28 planks still there, it burned the whole time (15 burns). Killing an Ablaze husk clears the mark.
* **Spotter Arrows.** 15 hostile mobs in the box (12 husks, 2 vindicators, a raid boss): the first volley marks all 15 (no cap of ten), each is Glowing as an endless effect (the client sees them glow), each took one arrow hit of 4 (before the mark), all 15 knockbacks were cancelled, no falling arrow hit anything, arrows fell. A husk that arrived late is marked by the next volley. A marked mob's next hit is 4 -> 5.2, the raid boss's 4 -> 4.6; six volleys hit each mob six times and every later hit is 5.2 (marks do not stack); the show arrows are gone soon after they land.
* **Marked, every kind of damage.** On marked mobs: an explosion 10 -> 13, fire 3 -> 3.9, a melee hit 6 -> 7.8, every poison tick 1 -> 1.3, every wither tick 1 -> 1.3; a `/kill` (bypasses invulnerability) is left alone; an unmarked vindicator takes all of them unchanged. **A real TaCZ bullet** (TaCZ's AK-47 from the default gun pack, fired through TaCZ's `IGunOperator.shoot`, result SUCCESS): on the marked vindicator `tacz:bullet` 6.75 -> 8.775 and `tacz:bullet_ignore_armor` 2.25 -> 2.925 (both +30 percent); on the unmarked one 6.75 and 2.25 unchanged.
* **Curse of Debilitation** (Damage level 3): one shell gives three vindicators Wither II, Poison II, Weakness II, Slowness III and Blindness, all endless; nobody glows; an undead husk gets them all except Poison (vanilla). Ten seconds after the last shell the curses are still there, still endless.
* **A raid never learns them as traps.** A raid state (wave 1, its spawning off) with four raiders: a napalm on one (it took the carpet's 5s and Ablaze's 4s), Spotter Arrows on another (4, then 5.2), the curse on a vindicator (poison and wither ticks): the raid learned no trap (`{}`). Control: plain fire on a raider (no weapon) is learned as a fire trap at once.
* **What the player reads.** The Field Guide's support page (screenshots); the cannon menu opened for each of the three shows its card: Spotter Arrows (glows, 30%, no "Arrow Cluster"), Curse of Debilitation (until it dies, no "Glowing"), Napalm Carpet (Ablaze, 4 every second), no placeholder left (screenshots).

### Found by the real-client runs and fixed

* **First standalone run** (`checks-standalone-first-run.txt`, 51 of 57): all six failures were the harness's: the burn count's window was 7 s, not 5; the poison was taken off before its first tick; the raid check then waited on that poison; the cannon menu's card is drawn without the text recorder (now read in the client's language, the screenshots show it). The screenshots also showed the starter Defense Beacon's placement preview in front of the camera (the harness now empties the player's hands and hides the HUD for the world shots).
* **Second standalone run** (`checks-standalone-second-run.txt`, 58 of 59; the one failure the harness's check for a raw "%"): in the raid's napalm the carpet hit right after an Ablaze burn landed as 1 instead of 5, vanilla's half second of invulnerability eating it. **Fixed**: an Ablaze burn ignores that window and leaves it as it was, so it is on top of the carpet and of any later hit; the harness now checks the carpet's full 5s and a hit right after a burn.
* **Final runs**: standalone 61 of 61 (`checks-standalone.txt`), pack 61 of 61 (`checks-pack.txt`).

## Not tested

* A real raid with real raiders walking through the three (the raid part is a raid state set up by the harness: four raiders without AI, a beacon 56 blocks away; the raid's own spawning was off).
* The napalm carpet's fire blocks under a raider with AI (vanilla contact fire, unchanged, see HANDOFF decision 5).
* A dedicated server with a second client; the owner's pack edition with Create, KubeJS and the real gunpacks (only TaCZ's default pack's AK-47 was fired).
* A flare thrown by hand through a placed cannon (the harness spawns the landed flare; the cannon's own path is unchanged since 0.0.34).
* Saving and loading a world with Ablaze or Marked mobs (both live in the mob's persistent data, saved with it; not reloaded).
* A witch that drinks Fire Resistance while Ablaze, an enderman hit by Spotter Arrows, a raid boss set Ablaze (the half is covered by a unit test).
* Sounds were not listened to; the screenshots were looked at, not checked by the harness.
