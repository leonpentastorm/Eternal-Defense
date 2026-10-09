# Create Arsenal UI design spec

How the redesigned screens are built, so an artist or developer can extend them without guessing.
Everything below is implemented in `custom-mods/arsenal-beacon/src/main/java/dev/createarsenal/beacon/Ui.java`
(shared kit), `RichText.java` (guide and confirmation copy) and the Gun Guide's `GuideClient.java`.

## Principles

1. **Hierarchy first.** Current Age, the selected item, its requirements and the primary action are the four things
   that must be readable at a glance. Detail sits behind a tab, the *Show details* toggle in the guide, or a tooltip.
2. **Code-drawn, not texture-drawn.** Panels, buttons, bars and key caps are rectangles, so they scale to every GUI
   scale and window shape with no stretched textures and no nine-slice seams. There is nothing to reskin in a PNG; the
   palette constants in `Ui.java` are the single source of truth. If an artist later wants textured panels, replace
   `Ui.panel`, `Ui.card` and `UiButton.renderWidget` and keep the same 1 px bevel / 4 px rivet proportions, so the
   nine-slice margins are **4 px** (corner rivets), centre stretch.
3. **Colour is never the only cue.** Ages carry a written name and pips, locked rows carry a padlock, conflicts carry
   a `!` mark, selected rows carry a lit border and fill, and warnings carry words.
4. **Live data only.** No key, item name, count or timer is painted into an image. Key labels come from the player's
   current `KeyMapping`s (or the `{interact}`, `{reload}`... tokens in language files).

## Palette

| Role | Hex | Used for |
| --- | --- | --- |
| Navy (panel) | `#111E28` | Panel body |
| Slate (card) | `#1C303D` | Cards, buttons, tab bar |
| Slate light | `#2A4555` | Top/left bevel, hover fill |
| Deep | `#0A1218` | Insets, tracks, pressed fill |
| Edge | `#3D5C6D` | Button borders, rules |
| Cyan | `#5AE2DF` | Primary accent, progress, selected, instrument light |
| Cyan fill | `#17424A` | Selected row / toggle fill |
| Brass | `#C9A24B` | Trim, rivets, "notable" tags (hard raid, completed branch, current tier) |
| Ink | `#EDF5F8` | Primary text |
| Muted | `#9FB3BC` | Secondary text |
| Orange | `#FFA36C` | **Requirements only**: missing materials, "how to unlock", capped payouts |
| Red | `#ED7369` | **Danger only**: low HP, jam, removal, attack warning |

Age accents (always printed beside the written Age name):
Frontier `#E8C17B` · World Wars `#A7CA8B` · Modern `#6CD4EF` · Advanced `#C29AFF` · Exotic `#FF86BD`;
categories: Create Armory `#E8B85C` · Turrets `#FFA36C` · Supplies `#B7E3B2`.

### Contrast (WCAG ratio)

| Text colour | on Navy | on Slate | on Deep | on selected fill | on primary button |
| --- | --- | --- | --- | --- | --- |
| Ink | 15.3 | 12.4 | 17.1 | 9.9 | 6.7 |
| Muted | 7.8 | 6.3 | 8.7 | 5.0 | 3.4 (not used there) |
| Cyan | 10.8 | 8.7 | 12.0 | 7.0 | 4.7 |
| Brass | 7.1 | 5.7 | 7.9 | 4.6 | 3.1 (not used there) |
| Orange | 8.6 | 7.0 | 9.6 | 5.6 | 3.7 (border only) |
| Red | 5.9 | 4.7 | 6.5 | 3.8 | 2.5 (not used there) |

All age accents are 6.1:1 or better on Slate and 7.5:1 on Navy. Disabled text (`#74888F`, 4.6:1 on Navy) is exempt but
stays legible. Primary buttons only ever carry Ink text. Rule of thumb: never put Muted, Brass, Orange or Red text on a
coloured button fill.

## Button and widget states (`Ui.UiButton`)

| State | Fill | Border | Text |
| --- | --- | --- | --- |
| Normal | Slate | Edge | Ink |
| Hover | Slate light | Edge | Ink |
| Pressed | Deep | Edge | Ink |
| Selected (toggle, category, row) | Cyan fill | Cyan or the item's Age accent | Ink |
| Disabled | Navy | Slate | `#74888F` |
| Locked | as Disabled + padlock glyph | | |
| Warning (short on materials) | any | **Orange** | Ink + words ("Need 3 more") |
| Primary | `#1D5F60` | Cyan | Ink |
| Danger | `#4A1F21` | Red | `#FFD9D5` |
| Tab (selected) | Slate + 2 px cyan top rule | Edge | Ink (unselected: Muted) |
| Keyboard focus | any | extra 1 px cyan ring outside the border | |

Toggles show a 30 x 12 switch plus the words ON / OFF.

## Layout and spacing

* Unit: 1 GUI pixel. Outer gutter 14 px, card padding 9-10 px, gap between cards 4-6 px, button height 20 px
  (18 px in tab rows and headers, 16 px for upgrade Buy buttons, 22 px for settings toggles).
* Header 28 px (title + optional subtitle, 2 px cyan rule at the bottom). Tab row starts at y + 31.
* Panels never exceed the window: width `min(max, window - 16)`, height `min(max, window - 16)`.
  Maximums: Control 520 x 340, Confirm 460 x 300, Rewards 600 x 350, Platform 1120 x 680.
* Feedback strip: one 13 px inset at the bottom (`y + height - 19`), shown for 7 s and then removed. Platform uses a
  plain line at `y + height - 12` because its page controls occupy the strip.
* Compact mode kicks in under 280 px panel height: fewer wrapped lines, breakdown grid hidden, long explanations
  moved to tooltips. Verified at GUI scale 2, 3 and 4 on a 1280 x 720 window (see `docs/ui/screens/`).
* HUD: left edge, vertically centred (clear of chat and hotbar at the bottom, minimap and TaCZ HUD at the right,
  block-information overlays at the top). Attack banner: top centre, between 8 and 58 px from the top.
  Gun Guide: right edge, vertically centred.

## Typography

Vanilla Minecraft font only: it is the only font guaranteed to carry every localized glyph, and it keeps pixel
alignment at every GUI scale. Hierarchy comes from colour, position and the 1.5x raid-level numeral, not from fonts.
Long names are measured with the real font and end in an ellipsis only when they cannot fit; the full name is always
available in the detail panel or tooltip.

## Icons and models

Existing item art is a detailed painted pixel style in `textures/item/industrial_atlas.png` (leather book, riveted plates, circuit
board, coil, crystal cube...) referenced by UV from item models. New items follow that style: 64 x 64 sprites with a dark
outline, a 3-4 tone ramp per material (steel, brass, copper, cyan or ember glow), a bright specular pixel, and a transparent
background. Sources are generated by `tools/ui-assets/make_sprites.py`. Placeable blocks use JSON element models over a tile kit painted in
the beacon's own palette (charcoal `#2f343a` brushed metal, gold `#c3963f` plates, cyan `#3fe3ff` light, hazard stripes), see ENERGY-AND-SUPPORT.md. The display artwork remains All Rights Reserved by its creator.

## Container screens (Mess Hall, dispensers, platforms)

Rules the kitchen and dispenser screens follow (0.0.8). New container screens should too.

* **Size.** Never wider than 316 or taller than 238 GUI pixels: Minecraft's automatic GUI scale guarantees a 320 x 240 window, so a larger panel is cut off at the smallest supported size. Check at GUI scale 3 on a 1280 x 720 window.
* **Slots.** Draw every slot with `Ui.slot` (a coloured 18 px frame and a recessed 16 px well) and the player's inventory with `Ui.inventory`; never plain bordered rectangles. The frame takes the accent of what the slot is for (effect colour, tier colour); an empty slot that wants something specific shows `Ui.ghost` of that item.
* **Status cards.** One `Ui.card` carries a block's state; its accent says how it is doing (brass or cyan normal, orange running low, red empty). The numbers and a `Ui.bar` or `Ui.tank` sit inside it.
* **Key hints** come from the live key bindings through `Ui.keyHint`, never from fixed text such as "right-click".
* **One primary action.** The bottom-right `Look.PRIMARY` button is the single next step and its label changes with the state (Gather ingredients, Make sandwich, Cook stew). A disabled primary button explains itself in its tooltip.
* **Coach line.** Under the main controls, one short line says what to do next (orange with an alert mark when something must be fixed, cyan with a check when ready).
* **Messages.** A server message is a toast of at most two lines that disappears after about four seconds; it never hides a control the player may need next. Effect names inside messages travel as ids and are translated on the client.
* **Tooltips.** One tooltip at a time, drawn after the widgets. `Ui.InfoTip` mixes text, status marks and rows of item icons; use `Ui.materialTooltip` for costs.
* **Shape as well as colour.** Status marks are drawn pixel glyphs (`Ui.check`, `Ui.alert`, `Ui.cross`, `Ui.deposit`); an icon that is unavailable is also dimmed, not only recoloured.
* **Recipe tiles.** The 18 px effect icons are complete tiles, drawn at their native size with a 2 px gap and a cyan ring for the chosen state. Do not scale them in the palette.
