# 1.1.10: what was tested

The owner: "extend horizontal zone maximum to match the max vertical. zone level 0 stay the same, scale up harder". On `dev`. Build: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew --no-daemon --offline build releaseJars` (2026-10-16). Evidence: `docs/validation/release-1.1.10/`.

## The change

Core zone radius 8, 12, 20, 32 (was 8, 12, 18, 24): Mk-1 unchanged, the steps grow (+4, +8, +12), and Mk-4 is 65 blocks across, the height of the full Vertical zone (16 below, the beacon's layer, 48 above). Upgrade costs and beacon health are unchanged. Nothing else is tied to the old maximum: raid spawns, gates, hunts, the beacon attack range and the outline all measure from the current radius.

## Unit tests

`:eternal-defense:test` 487 pass, 0 fail. New `Release1110Test`: the four radii, Mk-1 unchanged, each step bigger than the last, Mk-4 as wide as the full Vertical zone is tall, the guide line. `RulesTest` follows the new top radius (32). `:eternal-defense-displays:test` 7 pass.

## Real client

`tools/qa/QaRelease1110.java.txt` (`QA_CREATE=1`, LesRaisins 0.4.3 in `QA_MODS`), a new flat world, window 1280 x 720: **4 of 4 checks pass**.

* A Mk-4 beacon with the full Vertical zone: radius 32, 16 down, 48 up, 65 tall; the corners (32, 48, 32) and (-32, -16, -32) are inside, one block further out, up or down is not. The client gets the same size (32, 16, 48) and draws the box (picture).
* A raid's base snapshot of the 65 x 65 x 65 zone: 67 ticks (274,625 positions, 4,096 a tick), the server's average tick at most 5.0 ms while it scanned. In the flat world it kept 16,918 blocks; a base on deep stone keeps more.

## Network

Protocol **43**, unchanged (no packet changed). A 1.1.9 client on a 1.1.10 server would show the old radii in the upgrade text; the drawn zone follows the server.

## Not tested

* A real base at the full size: snapshot memory and save size with tens of thousands of blocks, repairs after a raid across the whole zone.
* Raid pathing from spawns farther out (the spawn ring moves out with the radius); FOBs and other gear placed near a much wider zone.
* A dedicated server and two players.
