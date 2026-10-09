# Writing the Field Guide and other help text

The reader is somebody who has just spawned into the world. They know Minecraft, not this mod. Everything that teaches them something (the Field Guide, item tooltips, confirmation screens, hints, empty-state lines) follows these rules. This is **not** the design document: reasons, pillars and tuning belong in `GAME-DESIGN-DOCUMENT.md`.

## What a feature card says

Every feature gets a card, in this order:

```
# Defense Beacon
- What it is: one plain sentence.
- You get: the benefit.
- How it works: the steps, using the names of the buttons as they appear on screen.
- How to unlock: where it comes from and what it costs, or "You start with one".
- Careful: what can go wrong (optional, repeat as needed).
- Good to know: anything else (optional).
```

* **Careful** is for catches the player cannot undo or would not guess: it resets something, it affects the whole team, there is only one, it spends something, it makes the next raid harder. If a system has a trap, the card names it, in red.
* "Explain it like I'm five": short sentences, everyday words, one idea per bullet. A sentence over 42 words or a line over 330 characters fails `LangKeysTest.guideSentencesStayShortAndPlain`.
* Say what to press, not what the code does. Name the tab or button exactly (`Buy five active days`, `Upgrade to Mk N`).
* Lists that are not a feature (the fifteen legendary mixes, the fire support types) use `- Label: text` bullets under a plain heading.
* Do not put design intent, balance reasoning, config tuning or formulas in a page. Exact numbers a player may want to look up go in the page's `detail` key, which feeds the **Reference** tab.

## The side menu

Every page has an item icon in the Field Guide's side menu (`BeaconClient.GuideScreen.icon`). Pick the item that stands for the page's subject, ideally one of the mod's own. A page added to `GuideText.IDS` without a `case` there fails `LangKeysTest.everyGuidePageHasItsOwnMenuIcon`. Keep page titles short: the menu rail shows them only as a tooltip, but the list cuts them at about 17 characters.

## Keys and editions

* A page `x` has `guide.x.title`, `guide.x.body` and optionally `guide.x.detail`. The page list is `GuideText.IDS`.
* Text that differs by edition: put `[pack] ` or `[standalone] ` at the start of the line. The other edition never sees it, and the card stays in one piece. The older suffix keys (`key.pack`, `key.standalone`) still work and are appended after the base text, which suits whole sections such as the co-op notes.
* In language files a percent sign is written `%%`. Key tokens such as `{interact}` and `{reload}` are replaced with the player's own bindings; never write a key name into the text.

## What the tests enforce

* `LangKeysTest.everyGuideCardFollowsTheSameFormat`: a card that starts with `What it is` has `You get`, `How it works` and `How to unlock`, in that order, in both editions.
* `LangKeysTest.everyRegisteredFeatureHasAGuideCard`: every block and item registered in `ArsenalBeacon` has a card (or is listed as an ingredient). Add a block or an item and this test fails until the guide explains it.
* `LangKeysTest.guideSentencesStayShortAndPlain`, the edition-leak checks, and `SupportTest.theFieldGuideQuotesTheRealNumbers` (numbers in the guide must equal the code).
* `GuideTextTest`: the `[pack]` / `[standalone]` line rules.

## Other places that teach

The same rules apply to the plant and reset confirmation (`confirm.place.body`, `confirm.remove.body`), the item tooltips for the Defense Beacon, Recovery Shovel and Field Guide (`BeaconItems`), upgrade and price hints, and the kitchen coach lines. A message that asks the player to commit to something hard to undo must say what cannot be undone.
