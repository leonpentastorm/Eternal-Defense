# Mess Hall: prepared food

Mess Hall **v4** test build, project **0.0.5**; network protocol remains **24-pack / 24-standalone**. Branch: `feature/messhall-ver-4`, based on v3 `2ecd1276d58e430dda1ef6a012c82beccc9d57f4`. The owner authorized builds, tests, a bundle with the new required backend JAR, and delivery to the v4 feature branch. See the validation record for actual coverage. Both editions share meal behavior and vanilla entry recipes. JAR build versions remain 0.21.0 / 1.1.0. Until final 1.0 the owner starts fresh worlds per feature; legacy footprint migration is outside this round. Existing compositions and exact remaining field ticks keep their save format.

## Player loop

Craft a Mess Hall Mk I (planks, furnace, two iron ingots and a barrel) and Cook Pots (cauldron, oak sign and seven iron ingots). Place their entire footprints inside a standing beacon's zone. The hall occupies **2 wide × 1 deep × 2 tall** (the anchor, the cell to its right, and both cells above), the pot **1 × 1 × 2** (the anchor and the cell above, where its chalkboard hangs). Face the front toward the serving area. Every occupied cell forwards interaction and breaking to one anchor; structures cannot be pushed by pistons.

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

Valid effect IDs are `vitality`, `fortitude`, `steadiness`, `mobility`, `firepower`, `quick_hands`, `brawler`, `heavy_hand`, `demolition`, `might`, `agility`, `fortune`, `recovery`, `hearth`, `springy`, `swim`; weights are 1–3. An optional `"family"` string (default: the file name) groups foods for the pair bonus below. A staple-only trait uses an empty `effects` object. Invalid definitions are logged and skipped. Overlapping item/tag definitions use the highest score per effect per distinct item type, then scores sum across different types. Moving an identical food into another slot or adding more items to its stack does not increase its score. The strongest two or three effects win, with enum order breaking ties. An effect's strength is capped at 3: base amount × 1.00 / 1.25 / 1.50. A single meal cannot include every bonus.

| Family | Default examples | Contribution |
| --- | --- | --- |
| Staple | Bread, wheat, baked potato | Sandwich foundation |
| Protein / egg | Cooked chicken/mutton/rabbit, rabbit stew, egg | Vitality (1) |
| Fish | Cooked cod / salmon | Mobility (1) |
| Vegetables | Carrot, potato, beetroot, beetroot soup, dried kelp | Fortitude (1) |
| Dairy | Milk bucket | Fortitude (2), empty bucket returned |
| Mushrooms | Red/brown mushroom, mushroom stew | Steadiness (1) |
| Sweet | Apple, berries, melon slice, sugar | Mobility (1) |
| Firepower | Cooked beef | Firepower (1) |
| Quick Hands | Honey bottle | Quick Hands (1), glass bottle returned per item |
| Brawler | Cooked porkchop | Brawler (1) |
| Heavy Hand | Golden carrot | Heavy Hand (1) |
| Demolition | Pufferfish | Demolition (1); prepared meals are safe to eat |
| Raw meat *(v3)* | Raw beef, chicken, mutton, porkchop, rabbit | Might (1) |
| Raw fish *(v3)* | Raw cod, salmon, tropical fish | Strong Swimmer (1) |
| Treats *(v3)* | Cookie, pumpkin pie | Agility (1) |
| Lucky *(v3)* | Glow berries; suspicious stew (2) | Fortune |
| Golden *(v3)* | Golden apple (2); enchanted golden apple (3, and Fortune 1) | Recovery |
| Grim *(v3)* | Rotten flesh, spider eye, poisonous potato | Hearth (1); prepared meals are safe to eat |
| Chorus *(v3)* | Chorus fruit | Springy Step (2) |

**Pair bonus (stews only).** When two *different* foods of the **same family** both feed an effect, that effect's value is **doubled** (e.g. cooked chicken + cooked mutton: Vitality ×2; cooked cod + cooked salmon: Mobility ×2). The doubling is stored with the meal and shown as `×2` in the kitchen screen, the pot board and the effect list. Two identical foods, a stack, or foods of different families do not pair. Protections (Hearth, Springy Step) are capped below full immunity.

| Effect | Base field strength | Base home strength |
| --- | --- | --- |
| Vitality | +4 max health | +8 max health |
| Fortitude | +2 armor | +4 armor |
| Steadiness | +5% knockback resistance | +10% knockback resistance |
| Mobility | +4% base movement speed | +8% base movement speed |
| Firepower (`firepower`) | +8% gun damage | +16% gun damage |
| Quick Hands (`quick_hands`) | +10% reload speed | +20% reload speed |
| Brawler (`brawler`) | +20% gun bash damage | +40% gun bash damage |
| Heavy Hand (`heavy_hand`) | +15% gun bash knockback | +30% gun bash knockback |
| Demolition (`demolition`) | +10% grenade/rocket radius | +20% grenade/rocket radius |
| Might *(v3)* | +8% melee damage | +16% melee damage |
| Agility *(v3)* | +8% attack speed | +16% attack speed |
| Fortune *(v3)* | +1 luck | +2 luck |
| Recovery *(v3)* | 1 health every 5 s | 2 health every 5 s |
| Hearth *(v3)* | 10% less fire and explosion damage | 20% less |
| Springy Step *(v3)* | 12% less fall damage | 24% less |
| Strong Swimmer *(v3)* | +10% swim speed | +20% swim speed |

Vitality, Fortitude, Steadiness, Mobility, Might, Agility, Fortune, Strong Swimmer, Firepower and Quick Hands are owned transient attribute modifiers with stable meal-only UUIDs; Recovery, Hearth and Springy Step are server-side pulse and damage hooks that read the same meal. They coexist with brewing and other mods' modifiers. The vanilla potion effects that display a meal are only a display, synchronized once a second, and carry no attribute modifiers of their own. Eating replaces the old meal rather than stacking meals. Extra max health is capacity, not instant healing. Losing health capacity clamps current health to the new maximum.

## TaCZ integration (1.1.8-hotfix2)

Both beacon editions require **leopoko's TaCZ Attributes 1.4** (`tacz_attributes`, exact metadata range `[1.4]`) on clients and server alongside TaCZ `[1.1.8,1.2)`. The selected TaCZ artifact is **1.1.8-hotfix2**, CurseForge project 1028108 / [file 9037989](https://www.curseforge.com/minecraft/mc-mods/timeless-and-classics-zero/files/9037989); the backend is project 1113285 / [file 8470731](https://www.curseforge.com/minecraft/mc-mods/tacz-attributes/files/8470731). Gradle resolves both through Curse Maven for development/runtime; neither JAR is embedded. TAA, GunsmithLib, Apotheosis, Apothic Attributes, TaCZ:Accel and affix progression are not meal requirements.

`PlayerMeals` remains the single server-authoritative UUID-keyed state. Strength 1/2/3 scales the table by 1/1.25/1.5, pairs double their contribution, and home doubles it again. Impact/detonation reads the owner's current meal/home state, not a shot-time snapshot. No gun-specific food list, gun-ID compatibility list or second duration exists.

* **Firepower:** one transient `MULTIPLY_TOTAL` modifier on global `tacz_attributes:gun_damage`, amount `b` (e.g. 0.08 field / 0.16 home), yielding `1+b` times the existing attribute value. Base values, category attributes and other mods' modifiers are preserved. TaCZ Attributes owns the native gun-hit multiplication; our former event listener is removed. Unknown categories still receive the global contribution when using the native hit path. Firepower does not increase explosive area damage.
* **Quick Hands:** the same owned modifier operation on global `tacz_attributes:reload_speed`, amount 0.10 field / 0.20 home before strength/pair scaling. Effective rate is the backend's global × category value. Native elapsed-time scaling, reload scripts, ammo transfer and finishing stay in the backend/TaCZ path. Our former `MealReloadMixin` and `ReloadClock` accelerator are removed. `MealReloadContinuityMixin` rebases the original timestamp only when the rate changes: preserve elapsed progress at the previous rate, then continue at the new rate. Native staged/single-round script timestamp adjustments survive the backend's temporary scale/restore pair. Inactive/completed reloads and a new gun/reload clear transition state. A +10% rate gives duration divided by 1.10, not a flat 10% time subtraction.
* **Reload presentation:** client-only `MealReloadClient` walks the active display's animation metadata across all tracks and transition targets. Clip names beginning with the backend's `reload` convention receive the current full rate, including an explicit reset to 1. It sets the backend runner multiplier rather than multiplying it a second time. Finishing, cancellation, swapping and disconnect reset tracked runners; non-reload clips are not accelerated by the meal. Gunpacks with private timers or unrelated clip names/state machines that bypass these native paths need separate review. No fixed track number is assumed.
* **Brawler / Heavy Hand:** one `MealBashMixin` changes only damage / knockback arguments entering native `ModernKineticGunItem.doPerLivingHurt`. Default gun bash and stock/muzzle attachment melee using this method are eligible. Bullet knockback and ordinary vanilla melee are outside this hook. TaCZ already includes vanilla Strength/Might in native bash damage; this adapter does not add those contributions again. Custom melee paths bypassing the method need separate review.
* **Demolition / native projectiles:** `MealExplosionMixin` scales the radius argument to `ExplodeUtil.createExplosion` once when the exploder is an actually explosive `EntityKineticBullet` and the supplied owner matches its actual server-player owner. The verified native `explosion` behavior field is read; gun namespace/category is irrelevant. RPG/M320-like launchers and explosive rifle/pistol rounds using this path are eligible. The shared radius reaches block/entity falloff, blast knockback and TaCZ's presentation packet; damage power, ownership and block-destruction settings remain native. Nonexplosive bullets, TNT, creepers, unrelated explosives and scripts/projectiles bypassing this utility receive no Demolition contribution.
* **Demolition / optional thrown grenades:** LesRaisins Tactical Equipements (`lrtactical`) **0.4.3**, gated as `[0.4.3,0.4.4)`, supplies a shared `GrenadeEntity.onDeath` → `CustomExplosion` path. `MealGrenadeMixin` scales only the detonation's radius argument using its real projectile owner, without changing the stored radius, fuse, power, destruction policy or item-specific behavior. Gameplay and radius-dependent presentation use that same `CustomExplosion` radius. This supports grenade implementations using the shared path rather than M67/RGN IDs. Missing or other addon versions leave the adapter inactive without making the addon mandatory. No optional addon class or client renderer is referenced by common initialization.

The backend intentionally pins 1.4: reload continuity cooperates with its specific scale/restore implementation via a cached, type-checked field bridge. Dependency/API source evidence and validation scope are in [the v4 implementation notes](MESS-HALL-V4-IMPLEMENTATION.md). Optional combat addons retain their own settings; integrations that independently remultiply the same stat require review.

Beef, honey, pork, golden carrots and pufferfish each supply one distinct gun trait, so a common ingredient does not bundle multiple major combat bonuses. Existing sandwiches/pots/player meals retain their saved compositions after upgrading; new cooking uses the new mappings. The registry IDs and SavedData format remain compatible. Extend the `arsenal_beacon:meal/<effect>` tags or add definitions with the IDs above for other food mods.

## Home territory and player lifecycle

`BaseZone.problem` is the sole territory check, including its standing-beacon, loaded-chunk and Overworld requirements. The server checks every player tick:

* At home: exactly 2× field strength and the **remaining field duration freezes**.
* Outside: normal strength and one field tick expires each server tick.
* Re-entry only changes enhancement; it never writes a fresh duration.

New meals start with **30 minutes / 36,000 field ticks**. **There is no custom meal HUD any more: a meal is shown with ordinary vanilla potion effects** (top-right icons and the inventory effect list): one effect per bonus (`meal_<id>`, amplifier = strength − 1, plus 4 for a pair bonus), with a custom inventory text showing the current value and the time. In the field each effect counts down together with the server timer. At home the effects are **endless** (shown as ∞/"Frozen") and a **Home Zone** effect is added ("Home zone: your food buffs double and don't decay."). The server keeps the effects in step once a second and on every change (the meal itself is server state, so clearing effects with a command or a milk bucket only hides them until the next sync). The effects have no curative items and carry no gameplay of their own.

Player state is in Overworld SavedData `arsenal_beacon_meals_v1`, keyed by UUID. It contains composition and exact remaining ticks. It does not use wall-clock expiry. Logout and server downtime pause the timer; login recalculates territory and reinstalls the owned transient modifiers. Dimension travel retains composition and remaining ticks; non-Overworld territory is field territory. Non-death respawn preserves a meal. **Death clears the meal; normal respawn starts without meal buffs.** Expiry removes only meal-owned modifiers and clears the vanilla meal display.

## Linking, breaking and authority

Links reach **8 blocks in three dimensions**, between anchors. Pot placement or interaction tries loaded nearby halls in distance order. Opening a hall discovers nearby unlinked pots. Existing valid links are never stolen by a neighboring hall. The screen can choose a loaded empty pot; default selection is the first empty valid pot.

Hall block entities persist their UUID and reserved pot positions; pots persist their hall position and UUID. Unloaded reservations remain occupied and are not treated as broken. No link traversal force-loads chunks. A loaded missing or mismatched anchor is proof that a link is stale. Removing a loaded hall clears its pots' links; an unloaded pot notices the stale identity when loaded and used again. A replacement hall at the same location has a new identity. Removing a pot frees its reservation. Stored stew remains in an orphaned pot and can be served after reconnecting; it cannot be served while no valid hall is available. Tier upgrades explicitly preserve hall identity.

Ingredients, preparation, output, link assignment, upgrades, serving counts and player buffs all execute on the server thread. Menus validate the current block entity, spectator state, home zone and interaction distance. A failed preparation leaves inputs intact. Two cooks sharing the last batch cannot both produce it; two diners consume separate serialized servings. Clients send ordinary container slot/button intentions, never compositions or serving counts. Packet 16 is **server → client only**, for open-menu previews (the v2 meal HUD packet 15 was removed in v3; potion effects are synchronized by vanilla).

Footprint installation retries on scheduled server ticks if a repaired anchor arrives before neighboring cells are available; retries never overwrite an obstructing block or force-load a chunk.

Raids reuse `ArsenalStructures.anchor` and the existing damage journal, so destruction of a footprint cell journals the live anchor block entity, and repair restores current inventories/servings rather than the pre-raid snapshot. No new logistics network or world-wide kitchen registry exists.

## Resources and verification

`tools/kitchen/generate_resources.py` regenerates blockstates, item models, loot tables, entry recipes, tags, traits and English text. **The block geometry is no longer generated**: the painted models of the artist package (`Create-Arsenal-Mess-Hall.zip`: Mk I wooden spit, Mk II copper field station, Mk III stone ovens, Mk IV enclosed canteen, and the Cook Pot with a hollow interior, a separate stew surface model and a chalkboard) are installed under `models/block/` with their own textures. Halls are 2 wide x 1 deep x 2 tall, the pot 1 x 1 x 2; front is north; the stew surface is drawn by `KitchenRenderer` when the pot holds stew, and the chalkboard text (x 2.5 to 13.5, y 18.5 to 26.5 of the pot's upper block) is drawn live. Effect icons for the vanilla potion display come from `tools/kitchen/make_effect_icons.py`.

**V4 build and runtime results:** see [the test-build record](MESS-HALL-V4-TESTING.md) and `MESS-HALL-VALIDATION-0.0.5.json`. Both editions build, and the standalone production-JAR server suite covers original kitchen tests, native gun effects, v3 food/effects, trap progression, sunlight/combat fire, backend modifier ownership and mid-reload rate changes. The optional LesRaisins 0.4.3 radius/owner check and real meal save/restart also pass. These results do not certify every custom gunpack or full pack/co-op playthrough.
