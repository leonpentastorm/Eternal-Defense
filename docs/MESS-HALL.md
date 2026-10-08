# Mess Hall: current prepared-food rules

Current feature: **v5**, project **0.0.6**, branch `feature/messhall-ver-5`, protocol **25**. Fresh world per feature until 1.0. JAR versions remain 0.21.0 / 1.1.0.

The current specification, ingredient groups, all fifteen legendary recipes, exact tier/economy numbers, server behavior and UI flow are in [MESS-HALL-V5-IMPLEMENTATION.md](MESS-HALL-V5-IMPLEMENTATION.md). Tested scope and remaining playtests are in [MESS-HALL-V5-TESTING.md](MESS-HALL-V5-TESTING.md).

* Every vanilla ingredient gives ordinary Minecraft bonuses. Two specific groups make one legendary TaCZ bonus, replacing those groups’ normal bonuses.
* Six slots; at most two effects in a sandwich, three in stew. Newly composed bonuses have one base strength; food weights choose effects rather than increasing strength.
* **Only Mk IV stew doubles categories:** two distinct foods for an ordinary bonus, or two distinct foods from EACH paired group (four slots) for a legendary bonus. No split-stack shortcut or triple stacking. Levels I–III show no strength bars; Mk IV stew shows two.
* Home doubles meal values and freezes remaining field time; it does not refresh it. Thirty minutes in the field; logout pauses, dimension travel retains, death clears, new meals replace.
* Fabricate starter installations in the beacon’s Workshop Fabrication tab. Each equipment table upgrades itself. Use the Bowl Dispenser for stored bowls; crouch-use opens its bowl-only slot.
* Fuel/Field Radio weapons are unavailable through Universal crafting. The recovery shovel deals zero damage and repels with Knockback II.

Required runtime: TaCZ 1.1.8-hotfix2 plus separate **TaCZ Attributes 1.4** JAR on client/server, included in both edition folders of the v5 test bundle. Optional LesRaisins Tactical 0.4.3 retains the v4 owned-grenade adapter. No additional attribute framework is required.

Historical validation remains in the v2/v3 records and [MESS-HALL-V4-IMPLEMENTATION.md](MESS-HALL-V4-IMPLEMENTATION.md), [MESS-HALL-V4-TESTING.md](MESS-HALL-V4-TESTING.md). Those historical food recipes and all-tier pairing rules are superseded by v5. [RAID-ADAPTATION.md](RAID-ADAPTATION.md) remains current for sunlight and traps.
