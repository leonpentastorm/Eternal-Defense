# 1.1.17: what was tested

The owner sent a picture of the Support Platform's Shop tab and asked: "Make this UI scrollable". In the picture the six products were squeezed into rows 18 pixels tall, so each product's second line (its note) was cut by the next row. On `dev`. Build: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew --no-daemon --offline build releaseJars` (2026-10-17). Evidence: `docs/validation/release-1.1.17/`.

## What changed

* **Full rows.** The Shop tab shows four products at a time. Each row is 24 pixels tall again, with room for the name and the note.
* **Scrolling.** Three ways, like the Exchange:
  * the mouse wheel over the list;
  * dragging the scroll bar's thumb;
  * clicking the bar's track.
* **The scroll line.** A line under the list says which products are shown: "Products 1 to 4 of 6. Scroll or drag the bar for more."
* **Unchanged.**
  * The pick follows the product, not the row, so it stays when the list scrolls.
  * The Buy button buys the picked product.
  * Hovering a row still shows the product's full text.
  * The Supply grid and Upgrade tabs do not scroll.
* **Code.** The screen code is commit `af2a233` on `dev`, made in the owner's other session, which could not compile or run it. While this round was under way it reached `dev` first. A version written here in parallel (the same rows, bar and wheel) was dropped in its favour, and `af2a233` was merged, built, unit-tested and played here.
  * `SupportScreen`: `ROW` 26, `VISIBLE` 4. One row button per visible slot shows and picks `scroll + slot`.
  * The wheel works over the list, the bar has drag and track clicks, and hover text goes through `rowOf`.
  * Rows narrow for the bar only when the list scrolls.
  * New text: `support.scroll`.

## Unit tests

`:eternal-defense:test` **514 pass, 0 fail**. The new `Release1117Test` has 2 tests:

* **Rows and the scroll:** rows are 26 apart (24 tall), four are shown, the scroll line fits above the Buy button, and there are more products than rows.
* **Wiring:** rows show and pick the product at the scroll position; the list scrolls by products minus rows, so every product can be reached; the wheel, the thumb and the track; no shrinking rows; the scroll text has its three numbers.

`:eternal-defense-gun-guide:test` 8 pass. `:eternal-defense-displays:test` 7 pass.

## Real client

`tools/qa/QaRelease1117.java.txt` ran with `QA_CREATE=1` (Create loaded) in a new flat world at 1280 x 720. Setup:

* A Defense Beacon was planted.
* A Support Platform owned by the player was placed inside its zone.
* The player was in Survival with 9 Ardent Energy.
* The platform was opened with a real use of the block, then its Shop tab.

### Run history

* **Run 1: 0 of 9.** The screen never opened. The platform only works inside a beacon's zone, and the harness had placed no beacon. A beacon was added.
* **Run 2: 10 of 10.** In the pictures a hover tooltip covered half the list, because the test's pointer rested on a row.
* **Run 3: 10 of 10.** Moving the pointer through the window system did not take under the virtual display. The tooltip was still there.
* **Run 4: 9 of 10.** The game's own pointer position was set instead, and the pictures were clean. The first check ran before the Shop tab had been drawn once: rows are shown on the first frame. A short wait was added before it (a harness timing fix).
* **Run 5: 10 of 10** on the version written here.
* **Run 6: 10 of 10** on the merged code from `af2a233`. The harness was unchanged; the test still reads the same fields (`scroll`, `selected`, `rows`) and the wheel lands over the list. The results and pictures below are from this run.

### The checks

* **The top:** four full rows (Support Cannon, Support Flare, Return Flare, Fire Support Flare), each 24 pixels tall. Picture `r1117_shop_top.png`: both lines of every row, the scroll bar at the top, and "Products 1 to 4 of 6. Scroll or drag the bar for more." whole and clear of the Ardent balance.
* **The wheel:** three notches down scroll to the end and stop there (Return Flare to Handheld Radio).
* **Picking:** a click on the last row picks the Handheld Radio. Picture `r1117_shop_scrolled.png`: the radio picked, the thumb at the bottom, "Products 3 to 6 of 6".
* **The bar:**
  * A click at the top of the bar goes back to the top, and the radio stays picked.
  * Dragging the thumb down goes to the end.
* **Other tabs:** the wheel on the Supply grid tab leaves the shop's scroll alone.
* **Buying:** after scrolling, the Fire Support Flare was picked and Buy pressed. One flare was bought for 6 of the 9 Ardent. Picture `r1117_shop_bought.png`: "Purchased." and 3 Ardent left.

## Jars

* All four jars (both builds of the main mod, the gun guide, the displays) hold no test, QA or note files.
* No tool, assistant or session names appear in text, data or class files. The one match is TaCZ's "PEQ6 ILLM" laser in the catalogue, as before.

## Network

* Protocol **43**, unchanged: no packet and no registry entry changed.
* The change is in the client screen only.

## Not tested

* Other GUI scales and window sizes than 1280 x 720 at the default scale.
* The owner's full pack, its resource packs and the owner's GUI scale.
* A dedicated server, a second player.
