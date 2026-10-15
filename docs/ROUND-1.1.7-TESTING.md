# 1.1.7: what was tested

The owner's 1.1.7 list: mobs that shoot never become Vampiric, Frostbite or Berserker, melee mobs never a Long Shot; those three kinds get 50% more health. Branch `feature/raid-control`. Build: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew --no-daemon --offline build releaseJars` (2026-10-15). Evidence: `docs/validation/release-1.1.7/`.

## Unit tests

`:eternal-defense:test` 472 pass, 0 fail. New `Release117Test` (2 tests): over 2000 rolls each, an archer (bow or crossbow) gets Long Shot but never Vampiric, Frostbite or Berserker; a fireball shooter gets neither; a melee mob gets the three hit kinds and never a Long Shot; the six other kinds fit every mob; the three hit kinds have +50% health; the guide says so. `Release114Test` follows the new `choose` signature. `:eternal-defense-displays:test` 7 pass.

## Real client

`tools/qa/QaRelease117.java.txt`, the same mods and gun packs as 1.1.6. Final run (run 2): **5 of 5 checks pass**.

* **The raid's own roll** (`Elites.roll`, as a wave spawn calls it), 200 raiders of each mob in a raid at level 5: skeletons, strays and pillagers got Long Shot 23 to 31 times and never Vampiric, Frostbite or Berserker; blazes got neither Long Shot nor the three; zombies, husks and vindicators got Vampiric, Frostbite and Berserker (8 to 22 each) and never a Long Shot.
* **Health** (`/eternaldefense elite vampiric|frostbite|berserker`, zombies): 90 of their 60 (the pack's own factory-tier health scaling makes a zombie 60 at tier 0), 1.5 times; the replies say "(1.5x)".
* **Forced by command** (`elite berserker 1 minecraft:skeleton`): it is made, and the reply says the power works only on its own hits and that raids never make one.

Run 1 passed the same 5 checks; its health check carried a wrong label ("30 of 20"), fixed for run 2.

## Jars

All four jars clean (the one match is TaCZ's "PEQ6 ILLM" laser, as before).

## Network

Protocol **42**, unchanged.

## Not tested

* Mod mobs from other packs (Cataclysm, Born in Chaos) in the roll: a mod mob that shoots without being a skeleton, pillager, blaze, ghast or a vanilla ranged attacker counts as melee.
