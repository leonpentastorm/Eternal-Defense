# 1.1.17: what was tested

The owner sent a picture of the Support Platform's Shop tab and asked: "Make this UI scrollable". In the picture the six products were squeezed into rows 18 pixels tall, so each product's second line (its note) was cut by the next row. On `dev`. Build: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew --no-daemon --offline build releaseJars` (2026-10-17). Evidence: `docs/validation/release-1.1.17/`.

## What changed

* **Full rows.** The Shop tab shows four products at a time. Each row is 24 pixels tall again, with room for the name and the note.
* **Scrolling.** Three ways, like the Exchange:
  * the mouse wheel over the screen;
  * dragging the scroll bar's thumb;
  * clicking the bar's track.
* **The scroll line.** A line under the list says which products are shown: "Showing 1 to 4 of 6. Scroll for more."
* **Unchanged.**
  * The pick follows the product, not the row, so it stays when the list scrolls.
  * The Buy button buys the picked product.
  * Hovering a row still shows the product's full text.
  * The Supply grid and Upgrade tabs do not scroll.
* **Code.** `SupportScreen`: four row buttons that show the product at `scroll + row`; `visibleRows`, `maxScroll`, `scrollBy`, the bar (`barX`, `trackH`, `thumbH`, `scrollTo`) and the wheel, drag and release handlers. The bar and the scroll line are drawn only on the Shop tab. New text: `support.scroll`.

## Unit tests

`:eternal-defense:test` **514 pass, 0 fail**. The new `Release1117Test` has 2 tests:

* **Rows and the scroll:** rows are 26 apart (24 tall), four are shown, and the scroll line fits above the Buy button. The list scrolls by 2, so every one of the 6 products can be reached.
* **Wiring:** rows show and pick the product at the scroll position; the wheel, the thumb and the track; no shrinking rows; the scroll text has its three numbers.

`Release111Test` now looks for the tooltip of the product at the scroll position.

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
* **Run 5: 10 of 10.** The results below are from this run.

### The checks

* **The top:** four full rows (Support Cannon, Support Flare, Return Flare, Fire Support Flare), each 24 pixels tall. Picture `r1117_shop_top.png`: both lines of every row, the scroll bar at the top, and "Showing 1 to 4 of 6. Scroll for more."
* **The wheel:** three notches down scroll to the end and stop there (Return Flare to Handheld Radio).
* **Picking:** a click on the last row picks the Handheld Radio. Picture `r1117_shop_scrolled.png`: the radio picked, the thumb at the bottom, "Showing 3 to 6 of 6".
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
