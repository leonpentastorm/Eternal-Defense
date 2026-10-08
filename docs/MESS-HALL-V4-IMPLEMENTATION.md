# Mess Hall v4 implementation notes

Project 0.0.5 on `feature/messhall-ver-4`, based on v3 commit `2ecd1276d58e430dda1ef6a012c82beccc9d57f4`. This is a feature-branch test build. Current builds and production runtime results are recorded in [MESS-HALL-V4-TESTING.md](MESS-HALL-V4-TESTING.md) and the validation JSON; earlier results remain historical. No release was published.

## Request and preserved behavior

The owner's current message selects v4, fresh worlds until 1.0, sunlight-only protection and per-raid trap adaptation, and asks for the attached food-buff implementation. The attached implementation prompt supplies the required backend, native/optional integration scope and initial static-only/no-publication constraints. The owner subsequently authorized testing, fixes and a local test bundle on 2026-10-08; the owner later also authorized a commit/push to the v4 feature branch. It referred to continuing v3; the owner's newer request selects the v4 branch. Fresh-world policy removes the need to migrate legacy kitchen footprints in this round.

Recipes, entry crafts, ingredient costs, servings, pot capacities, ingredient trait/pair formulas, artist geometry, vanilla meal effects and all bonus values retain their v3 implementation. `PlayerMeals` still owns exact field ticks, home ×2/freeze, replacement, offline pause, death clearing, dimension travel and save restoration. Display effects carry no gameplay. No food restriction, Brew Cannon, potion cap, ammunition redesign or stat progression was added.

## Dependency and API evidence

| Integration | Selected version / artifact | Source evidence |
| --- | --- | --- |
| TaCZ | Minecraft 1.20.1 **1.1.8-hotfix2**, CurseForge project **1028108**, [file **9037989**](https://www.curseforge.com/minecraft/mc-mods/timeless-and-classics-zero/files/9037989) | Published file metadata identifies `tacz-1.20.1-1.1.8-hotfix2.jar`; retained native classes inspected for reload, bash, explosive projectile and explosion signatures |
| leopoko TaCZ Attributes | **1.4**, `tacz_attributes`, project **1113285**, [file **8470731**](https://www.curseforge.com/minecraft/mc-mods/tacz-attributes/files/8470731) | Upstream commit `91612b5b505a87e2ddcd48b1b1ae1f9c95f482a1`; published artifact metadata/API also inspected statically |
| LesRaisins Tactical Equipements | Optional `lrtactical` **0.4.3**, adapter range **[0.4.3,0.4.4)** | Upstream commit `de97defc2d76c43d9724a458701a44e9ade680d2`: `GrenadeEntity.onDeath/getRadius`, `ThrowableItemEntity` owner and `CustomExplosion` radius consumers |

Both beacon editions require backend version `[1.4]`, deliberately exact because continuity relies on its specific timestamp scale/restore implementation. Existing TaCZ metadata range `[1.1.8,1.2)` remains, but this work's validation targets hotfix2 only. Curse Maven coordinates are `curse.maven:timeless-and-classics-zero-1028108:9037989` and `curse.maven:tacz-attributes-1113285:8470731`. The upstream backend build's older TaCZ file 8141310 is **hotfix**, not hotfix2; it was not copied as our pin.

Upstream references: [leopoko TaCZ Attributes at the inspected commit](https://github.com/leopoko/TaCZ_Attributes/tree/91612b5b505a87e2ddcd48b1b1ae1f9c95f482a1) and [LesRaisins at the inspected commit](https://github.com/LesRaisins-Studios/LesRaisins-Tactical-Equipements/tree/de97defc2d76c43d9724a458701a44e9ade680d2).

The addon metadata declares MIT and its published requirements are Forge, Minecraft and TaCZ. Its upstream development dependencies do not create a new requirement for Apothic/Placebo in this project. LesRaisins code is GPL and artwork is reserved. Our original narrow adapters use API/target signatures; no upstream implementation or artwork is copied into our mod JARs. The local test bundle includes the unmodified MIT backend JAR separately with attribution and license terms. TaCZ and optional LesRaisins are obtained separately from their authors. TAA, GunsmithLib, Apotheosis, Apothic Attributes, TaCZ:Accel and affix systems are not required.

## One application path per effect

| Meal stat | Owner / path | Scope |
| --- | --- | --- |
| Firepower | `PlayerMeals` transient modifier → backend global `gun_damage` → backend native hit handler | Player firearm hits using native TaCZ paths, any namespace; no meal event multiplier beside it |
| Quick Hands | `PlayerMeals` transient modifier → backend global `reload_speed` → backend native reload scaling | Native tactical/empty/staged reload clock; no old elapsed-time accelerator |
| Brawler | One `MealBashMixin` damage argument hook | Native bash and attachment melee; no additional Strength/Might contribution |
| Heavy Hand | Same mixin, knockback argument hook | Bash impulse; does not change bullet knockback |
| Demolition | Native explosion radius argument, or gated grenade detonation radius argument | Actual explosive projectile behavior / shared grenade implementation; no global explosion hook |

Both global gun attributes have neutral value 1. Modifier amount is the meal **bonus `b`**, with `MULTIPLY_TOTAL`, yielding `(existing value) × (1+b)`. Meal-only UUIDs are derived from `arsenal_beacon:meal/<effect>`. Replacement/removal touches only those UUIDs, not other mods' values or category modifiers. Unknown categories retain the global contribution when they use native paths.

## Reload continuity and presentation

Backend 1.4 temporarily maps the native reload timestamp to elapsed time × current global/category rate at method HEAD, then restores its saved timestamp at RETURN. Applying a new rate directly would rescale all prior elapsed progress. `MealReloadContinuityMixin` therefore acts immediately before the native gun's `tickReload` call, after all HEAD callbacks. On a rate transition it maps prior elapsed progress back through the new rate, then lets the backend's single scaling path continue. Unchanged rates receive no extra advancement.

The first production build rejected cross-mixin accessors because Mixin's annotation processor cannot resolve another mod's injected field. The correction now uses a cached, type-checked reflective bridge to the backend's merged `tacz_attributes$originalTimestamp` field. Initialization resolves the transformed class and fails clearly if the pinned ABI changes. A narrow redirect calls the original native gun method once and converts native timestamp deltas back into the backend's original-time domain. This preserves single-round/staged `adjustReloadTime` changes that its restoration would otherwise overwrite. Cancellation/inactivity/completion reset transition state; a newly started reload or changed weapon reference establishes a new rate baseline. Native ammo transfer and reload state remain authoritative.

The client correction is a `Dist.CLIENT` event subscriber using TaCZ Attributes' runner-speed API. It inspects active animation clip names across display tracks/transition targets, applies the backend's full rate to `reload*` clips, and writes 1 when neutral or finished. It does not add another speed multiplier. Previously tracked runners are reset when no longer active, including swaps/disconnects. Private script clocks, copied-stack suppliers that do not preserve native weapon identity, and animations bypassing the native state machine/name convention need separate review. Server/client attribute sync can still introduce the usual network delay; a real-client fixture observed 1.0/1.1/1.2 clip speeds through rate changes and native ammo completion; subjective animation quality and custom clips remain playtests.

## Explosion ownership and optional loading

Native Demolition requires `EntityKineticBullet`, its actual `explosion` boolean and matching server-player ownership. Cached reflection reads only the pinned native behavior field because TaCZ exposes no public getter. The effective radius is passed once to the native utility, which shares it with gameplay and its packet. Power, destruction settings and ownership remain intact. Nonexplosive bullets and unrelated explosives bypass the meal radius hook.

The optional adapter plugin consults Forge's early loading metadata without resolving optional addon classes. Only supported LesRaisins 0.4.3 selects `MealGrenadeMixin`; absent or other versions remain inactive. The mixin redirects the `getRadius` read in `GrenadeEntity.onDeath`, reads the raw radius through a shadow getter, and passes one effective radius into the original `CustomExplosion`. It leaves the grenade field and fuse unchanged. Common initialization references no grenade class or client renderer and logs adapter capability once. Core cooking remains available without LesRaisins.

Protocol remains **24**: no packet registration, fields or direction changed. Backend attributes synchronize through their native attribute system; meals retain vanilla effect display. Pack and standalone still require matching beacon flavors/builds.

## Raid changes

`RaiderSunlightMixin` blocks daylight ignition at `Mob.isSunBurnTick`, with a generated production refmap; it never clears existing fire. `RaidAdaptation` learns positive trap damage by class and applies +25% resistance per subsequent wave, capped at immunity. Learning is raid-local and saved, with announcements at wave boundaries; attributed weapon/turret damage is excluded. Damage/entity tags and narrow weapon attribution boundaries support additional mods. Full examples, supported damage classes and source-attribution limits are in [RAID-ADAPTATION.md](RAID-ADAPTATION.md).

## Verification and remaining scope

The [test-build record](MESS-HALL-V4-TESTING.md) records actual production-JAR checks and the fixes they exposed. Both editions build; standalone loading succeeds with and without LesRaisins 0.4.3. Native body hits, bash, explosive projectile radius, normal reload completion, mid-reload rate continuity, modifier ownership, food/effect lifecycle, meal save/restart, all eleven trap classes and sunlight/combat fire have passing server checks. Production mixins and the generated refmap are exercised by these tests.

Full pack loading, two human clients, arbitrary gun namespaces/categories, every tactical/shell reload and interruption path, addon-specific animation conventions, source-erasing turret attribution and long balance sessions remain unverified. Broad native-path support is an implementation scope, not a claim that all custom addons have been tested. See the linked record for client smoke status and exact artifact hashes.
