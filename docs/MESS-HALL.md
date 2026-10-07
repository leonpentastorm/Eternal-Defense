# Mess Hall: prepared food

Mess Hall v2 feature preview, project 0.0.3; network protocol **23-pack / 23-standalone**. Branch: `feature/messhall-ver-2`, based on published `dev` 0.0.2; this update is not merged into `dev`. Both editions share meal behavior and vanilla entry recipes. The beacon and other mod JAR build versions remain 0.21.0 / 1.1.0 as required by the project cadence.

## Player loop

Craft a Mess Hall Mk I (planks, furnace, two iron ingots and a barrel) and Cook Pots (cauldron, oak sign and seven iron ingots). Place their entire footprints inside a standing beacon's zone. The hall occupies **2 × 2 × 1**, the pot **2 × 1 × 1**. Face the front toward the serving area. Every occupied cell forwards interaction and breaking to one anchor; structures cannot be pushed by pistons.

Open the hall, insert at least two **different item types** in its six input slots, and choose **Sandwich** or **Stew**. Sandwich uses one item per composition type, normally a staple plus one or two fillings. Stew consumes **4/7/10/12 items** at Mk I/II/III/IV. Each type contributes at least one item; remaining cost is spread over nonempty stacks in slot order, round by round. Mk I supports up to four types; later tiers support all six slots. Duplicate stacks contribute quantity but score their type only once. Stack quantity never increases effect strength. The screen shows available/required items; slot tooltips show each planned deduction. Ingredient containers are returned to the cook. Inventory and output survive closing the screen and world saves. Inputs are shared among cooks.

* **Sandwich:** needs a staple and one effect-producing filling; produces one physical ration, stacks to 16, carries 1–2 effects in item NBT, and is eaten normally even at full hunger. Nutrition: 8, saturation modifier: 0.6. Take it from the output slot. Different compositions do not merge.
* **Stew:** needs two registered ingredients and at least one available effect; stores up to 3 effects in one selected empty connected Cook Pot. Full pots are never overwritten. Selection lists loaded pots and shows coordinates in a tooltip. Nutrition per serving: 10, saturation modifier: 0.8.
* **Serving:** use a normal bowl on the filled pot to eat immediately. The server removes one serving, applies the meal and restores hunger. The same empty bowl is returned in place and can be reused; no portable filled-stew item is introduced. Use without a bowl to read the name, bonuses and servings in chat. The pot's live menu board shows this information too; an empty pot has no stew surface and says Empty Cook Pot.

Casual cooking remains useful: bread plus cooked chicken makes a health ration. Beef, honey and pufferfish make Firepower + Quick Hands + Demolition stew. Farming several families supports better compositions and stocking the team before a raid. No NPC cook, processing timer or mandatory cooking skill is required.

## Tier progression

Upgrade a placed hall through its menu. Upgrades preserve its inventory, prepared output, identity and pot links; the footprint stays the same. Creative upgrades are free.

| Hall | Linked pots | Servings per stew | Food units per batch | Next upgrade: pack | Next upgrade: standalone |
| --- | --- | --- | --- | --- | --- |
| Mk I | 1 | 4 | 4 | 8 Reinforced Plating | 4 Ardent Energy |
| Mk II | 2 | 8 | 7 | 16 Reinforced Plating | 8 Ardent Energy |
| Mk III | 3 | 12 | 10 | 32 Reinforced Plating | 14 Ardent Energy |
| Mk IV | 4 | 16 | 12 | — | — |

All tiers support the same 1–2 sandwich and up-to-3 stew effect limits. Capacity, servings and ingredient costs live in `MealRules.tier`; upgrade prices live in `Economy`. These modest efficiency gains retain meaningful food costs. A read-only consumption plan validates all inputs and destination before preparation changes state; a failed attempt never spends part of a batch. Container remainders are returned once per consumed unit.

## Ingredient traits and strengths

Definitions are datapack resources under `data/<namespace>/meal_ingredients/<name>.json`; `/reload` refreshes them. Vanilla `Ingredient` syntax accepts an item, tag or array of alternatives. Extend the bundled `arsenal_beacon:meal/*` item tags for compatible modded ingredients, or define a new trait:

```json
{
  "ingredient": { "tag": "example:foods/cheese" },
  "staple": false,
  "effects": { "fortitude": 2 }
}
```

Valid effect IDs are `vitality`, `fortitude`, `steadiness`, `mobility`, `firepower`, `quick_hands`, `brawler`, `heavy_hand`, `demolition`; weights are 1–3. A staple-only trait uses an empty `effects` object. Invalid definitions are logged and skipped. Overlapping item/tag definitions use the highest score per effect per distinct item type, then scores sum across different types. Moving an identical food into another slot or adding more items to its stack does not increase its score. The strongest two or three effects win, with enum order breaking ties. An effect's strength is capped at 3: base amount × 1.00 / 1.25 / 1.50. A single meal cannot include every bonus.

| Family | Default examples | Contribution |
| --- | --- | --- |
| Staple | Bread, wheat, baked potato | Sandwich foundation |
| Protein / egg | Cooked chicken/mutton/rabbit, rabbit stew, egg | Vitality (weight 1) |
| Fish | Cooked cod / salmon | Mobility (1) |
| Vegetables | Carrot, potato, beetroot, beetroot soup, dried kelp | Fortitude (1) |
| Dairy | Milk bucket | Fortitude (2), empty bucket returned |
| Mushrooms | Red/brown mushroom, mushroom stew | Steadiness (1) |
| Firepower | Cooked beef | Firepower (1) |
| Quick Hands | Honey bottle | Quick Hands (1), glass bottle returned per item |
| Brawler | Cooked porkchop | Brawler (1) |
| Heavy Hand | Golden carrot | Heavy Hand (1) |
| Demolition | Pufferfish | Demolition (1); prepared meals are safe to eat |
| Sweet | Apple, berries, melon slice, sugar | Mobility (1) |

| Effect | Base field strength | Base home strength |
| --- | --- | --- |
| Vitality | +4 max health | +8 max health |
| Fortitude | +2 armor | +4 armor |
| Steadiness | +5% knockback resistance | +10% knockback resistance |
| Mobility | +4% base movement speed | +8% base movement speed |
| Firepower (`firepower`) | +5% gun damage | +10% gun damage |
| Quick Hands (`quick_hands`) | +6% reload speed | +12% reload speed |
| Brawler (`brawler`) | +15% gun bash damage | +30% gun bash damage |
| Heavy Hand (`heavy_hand`) | +12% gun bash knockback | +24% gun bash knockback |
| Demolition (`demolition`) | +5% grenade/rocket radius | +10% grenade/rocket radius |

The first four effects are owned transient attribute modifiers with stable meal-only UUIDs. They coexist with brewing and other mods' modifiers; no arbitrary vanilla potion effect is refreshed every tick. Eating replaces the old meal rather than stacking meals. Extra max health is capacity, not instant healing. Losing health capacity clamps current health to the new maximum.

## TaCZ integration (1.1.8-hotfix2)

Both editions already require TaCZ `[1.1.8,1.2)`; no new mandatory dependency or build-time TaCZ artifact was added. Compatibility follows the existing reflection / `@Pseudo` mixin architecture. `MealGunCompat` reads the affected server player's UUID-keyed `PlayerMeals` state, never a global weapon setting or another diner's meal. Strength 1/2/3 scales the table by 1/1.25/1.5. Meals replace rather than stack. Impact/detonation uses the owner's current meal and home state; no second gun-only duration exists.

* **Firepower:** supported `EntityHurtByGunEvent.Pre`, using `getAttacker` and `setBaseAmount`. Multiply base damage once by `1 + bonus` before TaCZ applies hit processing/armor splits. Native direct firearm hits are affected; explosion area damage is not multiplied by this effect.
* **Quick Hands:** `MealReloadMixin` at `LivingEntityReload.tickReloadState` advances the shooter's `ShooterDataHolder.reloadTimestamp` by elapsed time × current bonus. This is the operation exposed by TaCZ's script API `adjustReloadTime`. TaCZ's own default/script `getReloadTime`, feed/cooldown states and ammo transfer remain authoritative. Fractional milliseconds carry locally; inactive reloads reset the clock. Rate +6% means duration divided by 1.06, rather than a flat 6% time subtraction. Changing home state changes subsequent progress, without retroactively scaling elapsed reload time. Animation assets retain their own playback rates; the synchronized server reload state finishes earlier. Scripts using unrelated private timers are unsupported.
* **Brawler / Heavy Hand:** `MealBashMixin` modifies only damage / knockback arguments entering `ModernKineticGunItem.doPerLivingHurt`. Default gun bash and stock/muzzle melee attachments using that native path are supported. Ordinary player/mob attacks and vanilla attack/knockback attributes are untouched. Custom melee implementations bypassing this method are unsupported.
* **Demolition:** `MealExplosionMixin` modifies only the radius argument to TaCZ `ExplodeUtil.createExplosion` when the exploder is an `EntityKineticBullet`, the owner is a server player and its native gun index has type `rpg`. This covers the bundled **M320 grenade launcher and RPG-7** and compatible gunpack launchers. Radius is passed consistently to TaCZ block/entity falloff and its explosion packet; damage power is unchanged. Native blast falloff/knockback naturally uses the increased radius. Explosive rifle/pistol rounds, TNT, creepers, unrelated explosives, separate thrown-grenade entities and scripts bypassing this native utility are intentionally unsupported. No global Minecraft explosion hook changes radius.

Beef, honey, pork, golden carrots and pufferfish each supply one distinct gun trait, so a common ingredient does not bundle multiple major combat bonuses. Existing sandwiches/pots/player meals retain their saved compositions after upgrading; new cooking uses the new mappings. The registry IDs and SavedData format remain compatible. Extend the `arsenal_beacon:meal/<effect>` tags or add definitions with the IDs above for other food mods.

## Home territory and player lifecycle

`BaseZone.problem` is the sole territory check, including its standing-beacon, loaded-chunk and Overworld requirements. The server checks every player tick:

* At home: exactly 2× field strength and the **remaining field duration freezes**.
* Outside: normal strength and one field tick expires each server tick.
* Re-entry only changes enhancement; it never writes a fresh duration.

New meals start with **30 minutes / 36,000 field ticks**. The HUD shows the meal name, remaining field time and HOME ENHANCEMENT / FIELD state. Updates go only to the affected player, once per second and immediately on state changes. The client estimates the display between updates; it never controls gameplay duration.

Player state is in Overworld SavedData `arsenal_beacon_meals_v1`, keyed by UUID. It contains composition and exact remaining ticks. It does not use wall-clock expiry. Logout and server downtime pause the timer; login recalculates territory and reinstalls the owned transient modifiers. Dimension travel retains composition and remaining ticks; non-Overworld territory is field territory. Non-death respawn preserves a meal. **Death clears the meal; normal respawn starts without meal buffs.** Expiry removes only meal-owned modifiers and syncs an empty HUD state.

## Linking, breaking and authority

Links reach **8 blocks in three dimensions**, between anchors. Pot placement or interaction tries loaded nearby halls in distance order. Opening a hall discovers nearby unlinked pots. Existing valid links are never stolen by a neighboring hall. The screen can choose a loaded empty pot; default selection is the first empty valid pot.

Hall block entities persist their UUID and reserved pot positions; pots persist their hall position and UUID. Unloaded reservations remain occupied and are not treated as broken. No link traversal force-loads chunks. A loaded missing or mismatched anchor is proof that a link is stale. Removing a loaded hall clears its pots' links; an unloaded pot notices the stale identity when loaded and used again. A replacement hall at the same location has a new identity. Removing a pot frees its reservation. Stored stew remains in an orphaned pot and can be served after reconnecting; it cannot be served while no valid hall is available. Tier upgrades explicitly preserve hall identity.

Ingredients, preparation, output, link assignment, upgrades, serving counts and player buffs all execute on the server thread. Menus validate the current block entity, spectator state, home zone and interaction distance. A failed preparation leaves inputs intact. Two cooks sharing the last batch cannot both produce it; two diners consume separate serialized servings. Clients send ordinary container slot/button intentions, never compositions or serving counts. New packets 15 and 16 are **server → client only**, for player meal HUD and open-menu previews respectively.

Footprint installation retries on scheduled server ticks if a repaired anchor arrives before neighboring cells are available; retries never overwrite an obstructing block or force-load a chunk.

Raids reuse `ArsenalStructures.anchor` and the existing damage journal, so destruction of a footprint cell journals the live anchor block entity, and repair restores current inventories/servings rather than the pre-raid snapshot. No new logistics network or world-wide kitchen registry exists.

## Resources and validation

`tools/kitchen/generate_resources.py` regenerates functional JSON models, entry recipes, tags, traits and English text. Mk I has a wooden spit and rough counter; Mk II copper field equipment; Mk III stone ovens; Mk IV enclosed iron/green canteen equipment. The pot has a hollow interior, a conditional stew surface and an attached live menu board within its one-block height. All use vanilla textures. These are deliberately functional placeholder art, ready for replacement without changing registry IDs, footprint or state.

Run unit/build checks with the project's JDK 17 toolchain and `build releaseJars`. Opt-in real-server tests use `-Darsenal.messHallTests=true` and `/mess-hall-test`; `/mess-hall-fixture seed` followed by stopping/restarting the same disposable server and `/mess-hall-fixture check` exercises disk persistence. V2 native gun/food comparisons use `/mess-hall-v2-test` (six tests). UI smoke uses `-Darsenal.kitchenSmoke=true` alongside the existing standalone smoke flags. These flags are disabled during normal play.

Recorded validation and remaining limitations are kept in `HANDOFF.md`. Final painted art, long co-op balance, full-pack dependencies and exhaustive UI scale/localization testing remain owner playtest work.
