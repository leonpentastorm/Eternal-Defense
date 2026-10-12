# 0.0.35: dearer meals, three new meal effects, the Drink Station: testing

Branch `feature/drink-station`.

## How it was run

* **Unit tests** (`:arsenal-beacon:test`), JDK 17.
* **Real client** of each edition (Forge 47.4.20 development runtime, `xvfb`, 1280 x 720 at GUI scale 2; the size checks at 640 x 360, 480 x 270 and 427 x 240), the new harness `tools/qa/QaDrinks.java.txt` on a fresh NORMAL world (creative, one step in survival to pay for an upgrade). It drives the screens with real clicks (the screen's own `mouseClicked`, vanilla slot clicks through the client's game mode, the mod's menu-button packet), checks the server's state and the client's view, records each frame's texts for the size checks and takes screenshots.

## Results

### Unit tests: RESULT_UNIT

* New `Round0035Test` (14): effects and Brew Pots per tier, the Latte's 2-effect limit; 10 minutes, the sweetener steps (1,000 and 2,000 mB, never a third), 250 and 750 mB, the 4,000 mB tank and its 4 marks; the storage (54 x 64) and the break guard (256 breaks, 257 does not); the refusal order of a mixing slot (thrown, plain, multi, regeneration, harmful, instant, duplicate) and that every refusal has its text; the tracker's ownership test, when it may put its effect on, the layers it keeps when it takes its own off, the covered potions it remembers; milk and death; the owner's models installed and used (and the generator never writing them); stations hall-sized and kept through an upgrade, Forge's brewing registry, the break guard; prices, the Workshop's Kitchen group, the recipes in both editions; protocol 40; the Field Guide's numbers; the mixing slots' own index (the bug found on the client).
* From the meal part of this round: `MealRulesTest` (batch costs 12/21/30/36, the sandwich's 2 of each filling and 2 bread; the three replaced effects in place, their ids, numbers and caps, Hardy's refund, their icons, texts and foods), `MealPlannerTest` (2 units a filling), the protocol checks in `FobTest`, `RaidRoundTest`, `Round0034Test` (40).

### Real client: RESULT_CLIENT

What the harness checks (`docs/validation/round-0035/checks-*.txt`):
* The corner: the station is 2 wide and 2 tall (three structure parts). Mk I: the mixing menu is locked, no tabs or tiles, the storage empty.
* The brewing stand: water, nether wart, blaze powder: the awkward potions stay in the stand; sugar: the three Speed potions move into the storage by themselves, the stand's bottle slots empty; one blaze powder gave 20 brews (2 used).
* The storage: shift-clicking a Strength, a Speed II, a splash poison, Turtle Master and Healing stores all five (any potion is stored); they leave the pack and potions stay unstackable in it. A click on a stored potion puts one bottle on the cursor; clicking again with it puts it back; shift takes as many as the pack holds and says how many; shift-clicking them back stores them again; a stored potion's tooltip (screenshot).
* The break guard: a station with 300 bottles is not broken by hand (its upper cell), and the action bar says it holds 300 bottles; at 256 it breaks and drops all 256 bottles.
* Upgrade to Mk II with the screen's button (creative: free, as the Mess Hall's); the storage kept; one Brew Pot linked; the effect tiles (Speed, Strength, Night Vision; no Regeneration).
* Mixing: Speed chosen; Gather took the strongest Speed (Speed II) from the storage, nothing spent; the client's mixing slot shows it; the preview "Speed II · 10 min"; ready to brew. The sweetener: Regeneration II adds 750 mB and the bottle comes back; +10 min needs 1,000 mB (refused with 750); Regeneration I adds 250 mB; the preview says 20 minutes; Brew: pot 1 holds one Speed II drink of 20 minutes, the tank spent exactly one step, the potion's bottle came back, the toast says "Brewed Speed into pot 1".
* Brewing over the full pot asks "Replace the drink?" (screenshot); Replace drink: the pot holds the new drink.
* Refusals in the mixing slot, each with its notice on screen: a splash potion, Regeneration ("Regeneration only goes in the sweetener"), Healing (instant), Poison (harmful).
* The Latte: the tab opens the cup slot; Gather also takes a Paper Cup from the pack; Make Latte makes a "Speed Latte" of 10 minutes and uses the cup; it stacks to 16.
* The station's screen at 640 x 360, 480 x 270 and 427 x 240, both tabs: no text over another or across a button, every text and button in the panel, no slot under a button, no text cut short.
* Drinking: right-clicking the pot with a Paper Cup makes the drink the active one, empties the pot, keeps the cup, says so; at home Speed I shows as an endless vanilla effect. 400 blocks out in the field the timer runs and the effect counts down with it. A vanilla Speed II drunk then is left alone; when it runs out the drink's Speed I comes back with the drink's time. A milk bucket does not end the drink. Back home it is endless again and the timer stops.
* A weaker vanilla Speed I (5 minutes) under a Speed II drink: the drink is on top; when a new drink replaces it, the vanilla Speed I is back with its own time (not removed, not extended). A Latte drunk replaces that drink. A Milk Bottle ends the drink and its effect.
* The Cup Dispenser hands out a cup and says "9 left"; its stock screen (screenshot).
* Mk III in survival: the upgrade takes 8 Ardent Energy (standalone) or 16 plating (pack); two Brew Pots linked; a second potion of the same effect is refused. Mk IV: three pots; Speed, Strength and Night Vision gathered into one drink; brewed into pot 2; pot 1 gets Night Vision; screenshots of the taps' icons and the pots' boards and liquid.
* The meal effects: Hardy, Blast Ward and Projectile Ward show as their own effects with their names and icons; at home Blast Ward takes 40 percent off an explosion, Projectile Ward 30 percent off an arrow, fire is no longer reduced. Hardy's recipe tooltip in the Mess Hall (screenshot).
* The Field Guide's drinks page and Workshop Fabrication's Kitchen group (screenshots).
* Death ends the drink.

### Found by the real-client runs and fixed

* **First standalone run** (90 of 106; its log was overwritten by the next start, the failures are listed here):
  * **every mixing slot was inactive**: the anonymous slot class read `Slot.index` (its place in the menu, 5 to 7) instead of the mixing slot's own number, so the client never drew a gathered potion and the server refused every potion put in by hand before it could say why (the refusal and duplicate checks). Gather, Brew and the Latte worked, because they fill the slots directly. Fixed; a unit test guards it;
  * **coming home did not make the drink endless**: at home only an endless effect counted as the drink's own, so its timed effect was taken for a vanilla potion and left counting down. Fixed: the drink's timed effect is its own at home too, and is made endless;
  * two texts cut short (the stand's "done bottles" line, the Take back button): the line is gone (the stand's tooltip says it), the button is wider;
  * harness: the upgrade check expected a payment in creative (creative upgrades are free, as the Mess Hall's; a survival upgrade is now checked at Mk III); the confirmation's title is not a recorded text (the check reads its body); the Mess Hall was opened out of reach.
* **Second standalone run** (`checks-standalone-second-run.txt`, 100 of 107): the stand's line was still cut at every size (removed), and the awkward-potion check read the stand before the brew finished (the harness now waits for it).
RUNS_MORE

## Not tested

* A dedicated server with a second client (single player only): two players at one station, the storage clicks from two players at once.
* The owner's pack edition with Create, KubeJS and the gunpacks (the development runtime has neither); other mods' brewing recipes (Forge's registry is used, only vanilla recipes were brewed).
* Logging out and in with an active drink (the timer pauses: covered only by the code, the tracker's state is saved data).
* An ACTIVE FOB zone as "home" for the drink (the same test as the meal's, `MealZone`; only the Home Zone was used).
* Hopper or pipe access: not in this version, by design.
* A potion drunk at home over the drink's endless effect (vanilla drops it; the tracker remembers it through Forge's effect-added event): only the weaker-underneath case set up by the tracker itself was run.
* The tap icons, the pots' boards and the liquid colour were looked at in screenshots, not checked by the harness.
* GUI scale 1, and a world from 0.0.34 (old meals with Mobility, Hearth or Springy Step expire by design; not loaded).
* Hardy's slower hunger in play over time (the refund is covered by a unit test; not measured on the client).
* The Mess Hall GameTests changed this round (`MessHallV3GameTests`: Blast Ward, Projectile Ward and Hardy; the renamed effects in `MessHallGameTests` and `MessHallV6GameTests`) compile but were not run, and the kitchen harness (`QaKitchen`) was not run: the new stew and sandwich costs are covered by unit tests only (`MealRulesTest`, `MealPlannerTest`). On the client only Hardy's tooltip and the three effects' names, icons and damage cuts were checked.
