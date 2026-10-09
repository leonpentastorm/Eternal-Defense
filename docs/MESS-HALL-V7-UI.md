# Mess Hall v6 UI pass (project 0.0.8)

Branch **feature/messhall-ver-6** (owner override in `CLAUDE.md`), on top of v6 `cfba0ab`. Protocol **27-pack / 27-standalone**. Fresh world per feature until 1.0 is unchanged; saved hall blocks gain two optional fields (`StewMode`, `Order`) and load without them.

The owner's three requests:

1. The mixing system took too many clicks (Mix guide button, two tabs, pick, confirm, back, per effect). **Rewritten.**
2. The Bowl and Milk Dispenser gave no feedback; the Milk Dispenser even spent milk when there was nothing to clear. **Fixed.**
3. Comb through the UI the original author did not make and bring it to the project style. **Done for the screens listed below.**

## The new kitchen flow

One screen, no sub-screens. `MealRecipeScreen` and the Mix guide button are gone.

| Step | What the player does | Clicks |
| --- | --- | --- |
| Choose | Click the recipe icons they want: 11 everyday effects (top row) and 15 legendary gun effects (second row). Click again to remove. | 1 per effect |
| Gather | Press **Gather ingredients**. The hall takes the right foods from the table and the player's pack and lays them out, including the bread base. Nothing is spent. | 1 |
| Cook | Press **Make sandwich** or **Cook stew**. | 1 |

A two-recipe sandwich is four clicks; v6 needed about twenty. Cooking by hand still works: with no recipes chosen the hall ranks effects from the foods on the table exactly as before (the *Auto effects* chip).

### Screen layout (316 × 238, fits Minecraft's 320 × 240 minimum GUI)

* Header: title, **Upgrade to Mk N** (when affordable), tier badge. Hover the badge for pots, servings and food per batch.
* Row of **Sandwich / Stew** tabs; to the right the order counter, or for four seconds the server's last message (toast).
* **Recipe palette**: the 18 px effect tiles themselves, no frames. Cyan ring and check = in your order; dimmed = your pack has no food for it; orange `!` = it cannot be cooked together with the order. Hover shows FIELD and HOME values, the foods that feed it (icons, the ones you carry first), the Mk IV ×2 rule and why it is or is not available.
* Slot row: six mixing slots with the effect each food feeds, **Take back** (arrow) button, then the bread **Base** and **Out** slots (sandwich) or the linked **pots** (stew; click to choose, the first empty pot is chosen for you).
* Bottom right: **Your order** with a card per recipe (✓ doubled `×2`, value, or what is missing), **Clear**, food-batch bar and the primary button.
* Bottom left: the player's inventory, headed by a one-line coach (`Press Gather`, `Ready to cook`, `Need more food (7/12)`, …).

### The primary button

| State | Label | Condition |
| --- | --- | --- |
| Gather | Gather ingredients | A recipe order exists, the table does not make it yet, and the pack can supply it |
| Missing | Missing foods (disabled) | The pack cannot supply it; the tooltip names the recipes lacking food |
| Cook | Make sandwich / Cook stew | The table is valid; otherwise disabled with the exact reason on hover |

### Server rules (all in `MessHallMenu`, `MealPlanner`, `IngredientTraits`)

* Button ids: 0/1 mode, 2 prepare, 3 upgrade, 4 clear order, **5 gather**, **6 take back**, 40+ toggle recipe (26 effects, signed-byte safe), 100+ choose pot.
* **The order lives with the hall** (`HallEntity.stewMode`, `HallEntity.order`, saved as effect ids) so it survives closing the screen, restarts, hall upgrades and a second cook opening the same hall.
* Toggling a recipe is accepted when the order stays within the limit (2 sandwich / 3 stew) and can be cooked at all with this hall's tier (no shared food group between legendary recipes, enough food kinds for the tier). It is refused with a notice otherwise. Pack contents are not required, so a player can plan a recipe before owning the food.
* Switching mode drops an order that cannot work in the new mode (for example two legendaries in a three-kind sandwich) and says so.
* **Gather**: pool = the six table slots plus the main inventory (never armor or offhand). `MealPlanner.plan` picks foods: it prefers doubling (Mk IV stew), then fewer food kinds, foods already on the table and deeper stock, pads to two kinds, and for a stew adds the best fillers until the batch is covered. Table contents are lifted out, the plan placed, leftovers returned to the pack (dropped if full). If the pack cannot cover the batch it gathers everything it can and reports the shortfall.
* **Take back** returns every mixing slot and the base slot to the pack.
* Every action sets a notice (`NoticeSeq`/`Notice`/`NoticeArgs`) that the screen shows as a toast. Effect names travel as ids (`fx:vitality,mobility`) and are translated on the client.
* `compose` (used by Prepare) and the planner share one function, `MealPlanner.evaluate`, so a plan can never promise a meal that preparation refuses. Chosen effects the table cannot make are reported as `unmet` (problem `missing`) instead of rejecting the whole order.
* The food catalogue (id, group, ordinary effects of every food any trait accepts) is built once per datapack reload and sent once per opened menu for the tooltips; it replaces the per-effect source lists that were rebuilt into every preview.

## Dispenser feedback

| Action | Feedback |
| --- | --- |
| Take a bowl | Action bar `Took a bowl — N left` (gold when 8 or fewer, `Took the last bowl…` at 0, plus `Inventory full: it dropped at your feet.` when it did), a pop of sound and a bowl flying out of the front. |
| Empty Bowl Dispenser | Red action bar line, dispenser-fail sound. |
| Milk Dispenser, something to clear | Action bar `Milk cleared Strength, Poison, your Firepower Stew — 5,500 mB left`, drinking sound, white puff. A lost saved meal also gets one chat line. |
| Milk Dispenser, **nothing to clear** | `You have no effects to clear — no milk spent.` No dose is spent. |
| Milk Dispenser empty | Red line with the remaining mB. |
| Milk Bottle with nothing to clear | The bottle is kept (use is refused client and server side) with the same kind of message. |
| Refilling the tank in the screen | Bucket sound. |

## Screens brought to the project style

* **Bowl Dispenser, Milk Dispenser**: header with subtitle, brass/cyan/orange/red status card, ghost item in each empty slot, live gauge (bowl bar; graduated tank with surface line and an eighth-mark every 1,000 mB), key hints that show the player's own bindings, the shared recessed slot and inventory drawing.
* **Workshop Fabrication** (beacon tab): three groups (Tables, Kitchen, Support & trade) with their own accent and rule, short item names, one-line descriptions in the hover tooltip, costs as `icon count` (orange when short), an orange line when the beacon is not near or not planted, no more truncated names or clipped costs.
* **Weapon-table Upgrade view**: tab label no longer truncated (`Upgrade`), and the search box, Find, pager and scrollbar of a one-row list are hidden there, replaced by a short explanation.
* **Field Guide**: the Kitchen and Legendary mixes pages and the Stations page rewritten from walls of text into the guide's heading-and-bullet markup; they no longer mention the removed Mix guide.
* Shared kit additions in `Ui`: `slot`, `inventory`, `keyHint`, `ghost`, `tank`, pixel marks (`check`, `alert`, `cross`, `deposit`) so colour is never the only cue, and `InfoTip`, a tooltip that mixes text, status marks and rows of item icons.

## Files

New: `MealPlanner.java` (pure logic), `MealPlannerTest.java`, `tools/qa/QaKitchen.java.txt` (real-client scenario). Removed: `MealRecipeScreen.java`. Rewritten: `MessHallScreen`, `MessHallMenu`, `BowlDispenserScreen`, `MilkDispenserScreen`. Changed: `IngredientTraits` (compose delegates to the planner, food catalogue), `MessHall` (persisted mode and order), `MealClient` (catalogue), `BowlDispenser`, `MilkDispenser`, `BeaconClient` (fabrication tab), `PlatformScreen`, `Ui`, `BeaconNetwork` (protocol), `en_us.json`, `MealV6ClientSmoke` (cookbook phases removed), `MessHallV6GameTests` (new selection and milk rules), `tools/qa/setup-qa.sh`.

Validation: `docs/MESS-HALL-V7-TESTING.md`.
