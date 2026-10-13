# 0.0.37: bowls and cups used up; endless home effects that stop blinking: testing

Branch `feature/drink-station`, after 0.0.36.

## How it was run

* **Unit tests** (`:arsenal-beacon:test`), JDK 17.
* **Real client** of each edition (Forge 47.4.20 development runtime with TaCZ, `xvfb`), the new harness `tools/qa/QaDrinkLoop.java.txt` on a fresh flat world with a Defense Beacon (the player stands in its Home Zone). It counts every time Haste is added to the player, taken off or runs out (Forge's `MobEffectEvent.Added`, `Remove`, `Expired`), with the code that did it, and plays a rogue mod that takes every endless effect off the player every 15 ticks.

## Results

### Unit tests: 350 of 350 pass

* New `Round0037Test` (5): the Cook Pot and the Brew Pot use up one bowl or cup after a serving, only then, not in creative; the bowl and cup texts and the Field Guide say "used up" and no longer "reusable", "kept" or "come back"; two losses within 30 s trip the guard, one does not, two far apart do not; the long timer's window and top-up; the drink knows its long timer as its own at home and just out of it, never at another level; the trackers' and the Milk Dispenser's own removals are not counted, the meal's long timer is topped up in place, the handlers are registered.
* Changed: `MessHallGameTests` (two players eat with stacks of bowls and each uses up one; compiled, not run).

### Real client: standalone 26 of 26, pack 26 of 26

What the harness checks (`docs/validation/round-0037/checks-*.txt`):
* **The reported flicker, looked for.** A Haste drink at home for 20 seconds: Haste is never taken off or run out (no change at all), it stays endless. The same with a meal on top; next to a vanilla beacon giving Haste I (the beacon's Haste comes every 4 seconds and is covered, nothing is taken off); with the beacon at four levels; after the beacon is gone; with a Haste II drink; coming home from the field (one change: made endless, then nothing). **The flicker did not happen in the development runtime.**
* **The guard, with a rogue mod** taking every endless effect away every 15 ticks: the drink's Haste is taken away twice, then has the 20-minute timer at home; so do the drink's Speed, the meal's effect and the Home Zone effect; the player gets one chat line; the server log names `QaDrinkLoop$RogueMod.takeEndless`. With the rogue mod still on for 65 seconds, Haste is never taken off again and its timer is topped up in place (an "added", no "removed"). Out in the field the drink counts down its own field time again.
* **Bowls and cups.** A Mess Hall Mk IV with a Cook Pot, a Drink Station Mk IV with a Brew Pot, both linked and filled. In survival: eating with 3 bowls leaves 2 and the serving is eaten; drinking with 3 Paper Cups leaves 2 and the drink is the active one; eating with the last bowl leaves an empty hand. In creative: eating with 3 bowls leaves 3.

### Runs

* **First standalone run** (`checks-standalone-first-run.txt`, 26 of 26): it showed the player four chat lines at once, one per effect the guard switched (the drink's Haste and Speed, the meal's, the Home Zone's). Now one line per player; each effect is still in the server log.
* **Final runs**: standalone 26 of 26 (`checks-standalone.txt`), pack 26 of 26 (`checks-pack.txt`).

## Not tested

* **The owner's pack itself**, so the mod that takes the endless effects away is not known: the guard is tested with a rogue mod played by the harness. The owner's `latest.log` line `[Eternal Defense] ... last by ...` will name it.
* A dedicated server, several players; a totem of undying at home (one loss by design, not two); the guard after a logout (it resets, by design).
* An ACTIVE FOB zone as home (the same `MealZone` test as the Home Zone's; only the Home Zone was used).
* `QaDrinks` and `QaKitchen` were not run again: their only pot steps run in creative, where bowls and cups are kept as before (`QaDrinks`'s check now says so).
