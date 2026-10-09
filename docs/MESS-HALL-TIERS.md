# Mess Hall levels (project 0.0.11)

Owner request, 2026-10-09. This page is the current rule for what each Mess Hall level does; it replaces the tier rules in `MESS-HALL-V6-IMPLEMENTATION.md` and `MESS-HALL.md`. The recipe matrix, ingredient traits and the 26 effects are unchanged.

| Level | Makes | Meals last | Food slots | Doubling (×2) |
| --- | --- | --- | --- | --- |
| Mk I | sandwiches only | 15 minutes | 3 | no |
| Mk II | sandwiches and **stew** | 20 minutes | 3 | no |
| Mk III | sandwiches and stew | 25 minutes | 3 | **yes** |
| Mk IV | sandwiches and stew | 30 minutes | **6** | yes, and the only level that can double a legendary effect |

Unchanged per level: linked pots 1/2/3/4, stew servings 4/8/12/16, stew batch units 4/7/10/12, upgrade prices (pack 8/16/32 Reinforced Beacon Plating, standalone 4/8/14 Ardent Energy).

## Rules

* **Food slots** are the mixing slots that accept food; the others are closed (they take nothing and are not drawn). A table holds as many kinds of food as its open slots. The sandwich base and output slots sit right after the open slots.
* **Meal length** is set when the meal is made and saved on the sandwich, the pot and the active meal (`MealData.duration`). A Mk I sandwich eaten later still lasts 15 minutes; a Mk IV stew lasts 30. Home still doubles every value and freezes the timer.
* **Stew** needs Mk II. A Mk I hall always stays on sandwiches: the Stew tab shows a padlock, pressing it only explains why, and cooking is refused with the problem `stew_locked`.
* **Doubling** counts an ordinary effect twice when two DIFFERENT foods of one group both feed it. It works from Mk III, in sandwiches and in stew. A legendary effect needs two different foods from EACH of its two groups, which is four food slots, so only Mk IV can double one (the planner and `MealRules.doubles` both enforce it; with three slots the fourth food cannot be placed). Two stacks of one food never count twice.
* A Mk III hall holds three kinds of food, so a doubled effect leaves room for one more food: for example a doubled Vitality (chicken + mutton) plus a single Fortitude (carrot). Three doubled effects need all six slots (Mk IV).
* Everyday and legendary rules otherwise follow `MESS-HALL-V6-IMPLEMENTATION.md`.

## Where it lives in the code

* `MealRules.Tier(mk,pots,servings,ingredients,slots,minutes)` with `stew()`, `doubling()`, `ticks()`; `MealRules.doubles(mk,first,second,legendary)`.
* `MealPlanner.evaluate` reports the problem `slots` for a table with too many kinds and stamps the meal with `tier.ticks()`; `MealPlanner.plan` caps the table at the open slots and only offers pairs from Mk III.
* `MessHallMenu`: slots 0 to 5 are `isActive()` only below `tier.slots()`; `baseX(mk)` / `outX(mk)` place the base and output slots; `stew_locked` in `problem()` and in the mode button.
* `MessHall.HallEntity.setMode` refuses stew below Mk II.
* Protocol raised to **28** (the open slots change what the menu does on both sides).

## What the player sees

* Three chips under the order cards: meal length, food slots and ×2 (with a padlock while locked). Each has a tooltip in plain words.
* Hover the tier badge for the whole ladder of the current hall; hover **Upgrade to Mk N** for what the next level adds and the price.
* Recipe tooltips say whether ×2 is open at this hall; legendary tooltips say it needs four food slots.
* The Field Guide's Mess Hall page lists the four levels and has a *Doubling (×2)* card.
