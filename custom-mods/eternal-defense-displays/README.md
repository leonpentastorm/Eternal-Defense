# Eternal Defense Displays

Build a gun room worth showing off. Eternal Defense Displays adds eleven stands, wall
racks and glass cases for **Timeless and Classics Zero (TaCZ)**, including a
wide heavy-weapon stand for miniguns and other oversized weapons.

## Displays

- Standard and wide floor stands
- Standard and wide wall racks
- Compact pistol stand and compact pistol wall rack
- Small and wide floor glass cases
- Small and wide wall glass cases
- A two-block heavy-weapon stand

The glass cases use smaller weapon rendering with extra space below the receiver
for magazines. Wide cases suit rifles; small cases suit pistols and compact guns.
Unusually large addon weapons or attachments may extend beyond a display.

## How to use

Hold a TaCZ gun, look at a display and press TaCZ's **interact** key once (TaCZ's
default is O; the Eternal Defense pack sets it to H) to put the gun on display. Press it with another gun to swap them.
Right-click with an empty main hand to take the stored gun back. Right-clicking with
a gun aims, as TaCZ always does, and leaves the display alone.
Your weapon keeps its attachments, ammunition, name and other item data.
Both halves of a wide display interact with the same stored weapon.
Wall displays need a solid wall behind every occupied cell.

### Weapon Display Tuner

A screwdriver for your gun room. Right-click a display that holds a gun with the
tuner to open its panel, then change the gun's **size** (25% to 300%), **move** it
left/right, up/down and back/front (up to 16 pixels, a full block, each way) and
**rotate** it: tilt, turn and roll (up to 180 degrees each way). The display shows
every change as you make it. **Copy** and **Paste** carry the settings to another
display, **Reset** puts the gun back the way the display shows it on its own.
The settings belong to the display, so they stay when you swap guns.

## Crafting

Every display and the tuner have their own crafting table recipe with vanilla items
(JEI shows them; the recipe book unlocks them once you carry an iron ingot):

| Item | Recipe (top row first) | Key |
| --- | --- | --- |
| Gun Stand | ` H ` / ` A ` / `ISI` | H tripwire hook, A armor stand, I iron ingot, S smooth stone slab |
| Wide Gun Stand | `H H` / `A A` / `ISI` | as above |
| Pistol Stand | ` H ` / `NSN` | N iron nugget |
| Heavy Weapon Stand | `HCH` / `IBI` / `SSS` | C chain, B iron block |
| Wall Gun Rack | `H H` / `IFI` | F item frame |
| Wide Wall Gun Rack | `H H` / `FIF` / `LLL` | L any planks |
| Pistol Wall Rack | `NHN` / ` F ` | |
| Glass Gun Case | `PPP` / `PHP` / `IWI` | P glass pane, W any carpet |
| Wide Glass Gun Case | `GGG` / `HWH` / `III` | G glass |
| Wall Glass Gun Case | `PHP` / `PWP` / `IFI` | |
| Wide Wall Glass Gun Case | `GGG` / `HWH` / `FIF` | |
| Weapon Display Tuner | `  N` / ` I ` / `T  ` | T stick |

## Requirements

**Minecraft Java 1.20.1 · Forge 47.4+ · TaCZ 1.1.8+**

Install Eternal Defense Displays and TaCZ on both the client and server; both sides
must run the same version (the tuner talks to the server). No Create, JEI, KubeJS or
Eternal Defense is required. This release targets Forge; Fabric and NeoForge builds
are not included.

## Eternal Defense integration

With **Eternal Defense** installed, the eleven displays can also be bought in the
**Weapon Platform → Supplies** category; the crafting recipes stay available.

The furniture is rendered as static block geometry. Only the displayed gun uses
TaCZ's native item rendering; the displays have no block-entity tick loop.

## License

Code is MIT licensed. Models, textures and previews remain All Rights Reserved
by their creator. Unmodified mod JARs may be included in modpacks. TaCZ is a
separate required dependency, not bundled or copied into this mod.
