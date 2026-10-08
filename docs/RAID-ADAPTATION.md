# Raid trap adaptation — v4

Project 0.0.5, `feature/messhall-ver-4`. Production standalone damage-event and sunlight tests pass; see [the validation record](MESS-HALL-V4-TESTING.md). Until final version 1.0, each feature is played in a fresh world.

## Sunlight and combat fire

Marked raid mobs return false from vanilla `Mob.isSunBurnTick`: daylight does not ignite zombies, skeletons or phantoms. The old per-tick `clearFire` is removed. Flame arrows, fire traps and lava can ignite and damage raiders normally, subject to their native species immunities and the trap resistance below. This change does not remove a blaze's native fire immunity. Sunlight protection never grants general fire resistance.

## Learning across waves

Learning belongs to the current raid and damage class, shared by its registered attackers, including bosses. A class is learned only after a positive final damage event, after armor and damage reductions. The whole first damaging wave takes normal damage from that class; later hits in that same wave do not gain resistance.

| Wave relative to first damaging wave | Resistance | Damage received |
| --- | --- | --- |
| First exposure | 0% | 100% |
| Next wave | 25% | 75% |
| Two waves later | 50% | 50% |
| Three waves later | 75% | 25% |
| Four or more waves later | 100% | 0% |

At each later wave start, chat announces **“The Raid just adapted to fire damage (25% resistance).”** The 50% and 75% steps announce the new percentage; the fourth step announces **“The Raid is now immune to fire trap damage.”** Each class has its own first exposure wave. Later waves advance resistance even if that trap is not used again; short raids may finish before immunity.

Example: a lava moat first damages raiders in wave 1. Waves 2/3/4/5 take 75/50/25/0 percent lava/fire trap damage. A fall trap first used in wave 3 stays at full damage in wave 3 and starts adapting in wave 4. Switching lava to campfires does not create a new first exposure: both are fire damage.

`CampaignData.TrapFirstWave` saves first exposure by class; restarting during a raid retains learning. Starting the next raid or resetting the campaign clears it. Resistance is read from the current wave for every hit, so raiders already alive at a wave boundary follow that wave too. Ambient mobs and players never receive this resistance.

## Covered damage classes

| Class | Examples |
| --- | --- |
| Fire | Lava moat/lake, campfire, magma/hot floor, fire blocks and subsequent trap burn ticks |
| Fall | Drop pits and forced falls |
| Drowning | Water chambers |
| Suffocation | Piston/block compression and in-wall traps |
| Spikes | Cactus, sweet berries, pointed dripstone landing |
| Crushing | Falling sand/gravel, anvils, falling stalactites, entity cramming |
| Freezing | Powder snow |
| Explosion | Ownerless redstone/dispenser TNT and other tagged trap blasts |
| Projectile | Ownerless dispenser arrows and other tagged trap projectiles |
| Magic | Ownerless poison/magic/wither damage, including potion traps and wither roses |
| Lightning | Unattributed lightning damage |

Classification follows the actual damage event rather than scanning builds, blocks or chunks. A natural hazard of the same class also teaches the raid if a registered attacker takes damage from it. Non-damaging confinement, slowing, navigation traps and block destruction are not changed by damage immunity. Unclassified custom damage, void, commands and starvation do not teach resistance.

## Defensive weapons stay effective

Damage attributed to a living attacker, an owned projectile, owned area-effect cloud or owned primed TNT is excluded. This covers player/mob combat, ordinary TaCZ bullets, grenades, turret shots carrying their attacker/projectile attribution, and directly ignited TNT used as a weapon. Ownerless redstone TNT remains a trap. The support cannon's flare, arrow barrage and Narukami damage are explicitly excluded even if its caller is offline.

Weapon ignition and poison/wither applications retain a target-side expiry marker, so subsequent ownerless burn/effect ticks remain weapon damage. Direct lava/fire contact still counts as a trap. A flame arrow therefore continues working after the raid has learned fire traps. A weapon addon that burns a target without an attributed hit needs the integration boundary below.

Custom turrets sometimes discard all attacker information and use an ownerless vanilla projectile or generic environmental damage. Such events cannot be distinguished automatically from dispenser traps. Integrate those sources through tags or the narrow marker/API; the system does not guess turret identity from block proximity.

## Datapack and addon integration

Extend damage tags at `data/arsenal_beacon/tags/damage_type/trap/<class>.json` with a mod's actual damage IDs. Existing vanilla entries are included in the source. If tags overlap, classification order is fire, spikes, fall, drowning, suffocation, crushing, freezing, explosion, projectile, magic, lightning. Spikes precedes vanilla fall because pointed dripstone belongs to both tags; prefer one class per custom damage ID.

Exempt a defensive weapon's entity type using `data/arsenal_beacon/tags/entity_types/adaptation_exempt_sources.json`, or its exclusive damage type using `data/arsenal_beacon/tags/damage_type/adaptation_exempt.json`. Entity tags already include the support flare plus optional `tacz:bullet` and `lrtactical:grenade_entity`. Do not globally exempt a shared vanilla arrow/fire/explosion damage type; that would also exempt traps.

Java integrations may set persistent boolean `arsenalDefensiveWeapon` on an ownerless weapon projectile/source, use `RaidAdaptation.weaponDamage(target, source, amount)` around a direct ownerless hit, and call `RaidAdaptation.weaponFire(target, ticks)` before applying weapon ignition. These boundaries affect attribution only, not the original weapon damage amount. Effect attribution uses Forge's effect source where available.

## Verification scope

The standalone production-JAR tests pass all eleven classes through 25/50/75/100% resistance, repeated first-wave hits, independent later exposure, serialization, new-raid reset and ambient-mob exclusion. Actual flame-arrow impacts, subsequent weapon burn ticks, marked ownerless weapon shots, weapon poison and the support-lightning boundary remain effective after trap immunity. A real-tick daylight test confirms that an unmarked zombie burns, a raid-marked zombie does not, and combat ignition still damages the raid-marked zombie.

These fixtures do not exercise every physical trap construction, natural fire-immune species, full pack runtime or source-erasing turret addon. Such integrations need individual attribution review. See [test details](MESS-HALL-V4-TESTING.md).
