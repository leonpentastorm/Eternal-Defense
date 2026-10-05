# Create Arsenal — artist and UI brief

## The pitch

**Build a home worth defending. Build a factory that keeps it alive. Fill an armory with weapons worth displaying.**

Create Arsenal turns Minecraft into a shared campaign of building, industry and base defense. Players settle around a defense beacon, automate their supplies, collect weapons and face increasingly ambitious attacks. The three custom mods below form its identity. Each also has a standalone release.

The audience includes someone opening the pack for the first time. The interface should teach that player through clear choices, readable item icons and short explanations. Assume no knowledge of this project's development history.

## 1. Create Arsenal: Defense Beacon

### Player-facing description

**Your base is the objective. Your workshop is the progression system.** Plant a beacon and survive an introductory raid to earn your first weapon workbench. From there, turn a small camp into a fortified home, a Create factory and a fully equipped arsenal.

Better construction and beacon upgrades improve rewards. Richer rewards bring more frequent attacks, eventually reaching one raid every two active Minecraft days. Every third raid is a harder encounter with a boss. Enemy numbers scale for the participating players. Players can choose a lower raid level, start a raid immediately or spend resources to buy an increasingly expensive break from scheduled raids.

Victory restores tracked raid damage inside the protected zone. Spent ammunition, fuel and durability stay spent. Defeat leaves that damage behind. Rewards are collected through a large reward chest and include resources, useful components, supplies and Universal Ammo Coins. Coins offer an alternative way to buy ammunition.

Raid enemies approach from beyond the protected zone and can breach blocks, preferring doors when available. Melee attackers strike the objective; ranged attackers fire from a distance. Nearby ambient hostiles can also threaten an exposed beacon, but solid cover prevents them from detecting it through walls. Repeated defender deaths hurt the shared beacon. Ammunition-consuming turrets help hold the line. Attacker highlights appear after a minute, with an upgrade for immediate detection.

The beacon grows from **Mk-1 to Mk-4** through three Core upgrades. Its largest model occupies a 3 × 3 footprint. Separate upgrades improve logistics, damage reduction, healing, attacker detection and vertical protection. A visible animated grid marks the protected volume, including its top and bottom. The beam and red beacon hurtbox outline can be switched on or off.

A beautiful, inhabited base matters: scoring considers structure, palette, details, lighting, furnishings, layout, machinery and platforms. The UI previews this breakdown and progress toward the next reward tier. Five building tiers and five Logistics levels contribute to a maximum reward tier of ten; the selected raid cap limits the actual payout.

### The Universal Weapon Platform

Four complementary stations organize a large collection without forcing players to browse every recipe at once:

- **Weapons:** search by name, filter by weapon type and Age, craft weapons and manage the workshop. Special categories contain Create Armory weapons, turrets and Supplies.
- **Ammo:** shows ammunition and supported magazines relevant to the equipped gun. Repeated recipes for an identical cartridge become one entry; different magazine families, capacities and meaningful item data remain distinct. Players can pay crafting materials or raid-earned Ammo Coins where supported.
- **Attachments:** shows compatible upgrades for the equipped gun, with a count of options still locked behind later Ages.
- **Armor:** contains worn armor and protective equipment: helmets, vests, leg protection, boots, tactical pouches, plates and vision gear. Food, healing items, grenades and other consumables belong in **Supplies**.

Each station displays its own Age. Five eras move from frontier weapons through industrial and modern equipment to advanced and futuristic technology. Historical identity matters alongside power: a modern pistol remains a modern unlock. Ammo, attachment and armor stations are purchased and upgraded through the weapon station's Workshop. Holding a gun and using the live interaction key opens relevant ammo or attachment options.

The stations use two-block-wide, two-block-tall models. Upgraded stations inside the protected zone contribute to base score, with duplicate-station bonuses limited. Creative mode makes purchases and upgrades free for testing. New players receive a starter weapon and matching ammunition, a field guide and the beacon tools. Placing the beacon and packing it up with the shovel both require an explicit confirmation.

### Screens to redesign

| Surface | The player's immediate question | Information and actions to preserve |
| --- | --- | --- |
| Beacon Overview | “How is my base doing?” | Health, raid state, next raid, reward tier, score bar and breakdown, repair and reward collection |
| Beacon Upgrades | “What should I improve next?” | Branch levels, exact next effect, material icons and counts, purchase button, missing-material feedback |
| Beacon Settings | “What is protected?” | Zone grid, beam and hurtbox toggles; removal controls |
| Raid delay / control | “Can I take a break or fight now?” | Pause time, escalating price, next-raid bonus, selected raid cap, start-now action |
| Rewards & tiers | “Is the next tier worth it?” | Tier comparison, concrete reward quantities and harder-raid bonus |
| Weapon / Ammo / Attachment / Armor browser | “What can I make for my loadout?” | Prominent separate station Ages, search and filters, item names and icons, materials, compatibility and locked counts |
| Workshop | “How do I unlock more equipment?” | Buy stations, upgrade nearby stations, exact costs and requirements |
| Field guide | “How does this work?” | Brief pages explaining placement, raids, scoring, upgrades, factories and stations |
| Placement / removal confirmation | “What am I committing to?” | Strong confirmation; removal disables raids and resets beacon upgrades |
| Raid HUD | “What needs attention right now?” | Wave / attackers / boss health when relevant; a clear red **BEACON IS BEING ATTACKED** warning for every player |

Special-category buttons should visibly select the current category. The large filter becomes **Back to normal category** while browsing a special category. Locked recipes explain **How to unlock:** in plain language. Long weapon names and large material counts must remain readable.

## 2. Arsenal Gun Guide

### Player-facing description

**Know your weapon before the next firefight.** A compact guide shows the actions available while holding a TaCZ gun, using the player's actual key bindings. Reload, inspect, melee, interaction and other supported controls stay understandable even after remapping.

The guide expands for five seconds when a gun is first drawn, then collapses to a small icon showing its configurable shortcut. That shortcut reopens or hides the guide. With supported durability integration, a **JAM** alert appears beside the crosshair and the guide identifies the action used to clear it. Conflicting controls should be easy to recognize.

### Artist's focus

Design an expanded control card, a collapsed shortcut badge, clear open/closed states and a highly legible jam alert. The expanded card should feel like a polished game's contextual controls. The collapsed badge should remain useful without distracting from aiming. Every key label comes from the current bindings; never paint a fixed “H,” “R,” “U” or guide shortcut into an image.

## 3. Arsenal Gun Displays

### Player-facing description

**Give your collection a proper armory.** Replace statue displays with industrial stands, wall racks and glass cases designed around the weapons themselves. Eleven display designs include small-gun and pistol options, wide racks, protective cases and a heavy-weapon stand for large weapons.

Place a weapon on a display, swap it or retrieve it with its saved item data intact. Directional placement and larger displays support deliberate room layouts. Glass-case weapon scaling is conservative to reduce clipping around oversized magazines and attachments.

Standalone players craft the displays with vanilla materials. When Defense Beacon is installed, those recipes are disabled and displays are acquired through the Universal Weapon Platform's Supplies category.

### Artist's focus

The models are an established part of the visual identity. Improve item icons, naming consistency and any concise interaction cues without hiding the actual weapon. Keep floor stands, wall racks, glass cases and the heavy stand easy to distinguish at inventory scale.

## Visual direction

**A welcoming workshop with a serious defense system.** Combine dark steel, warm wood, brass and cyan instrument lighting. Use crisp pixel art inspired by the readability and character of 16-bit games such as Castlevania, Mega Man X and Stardew Valley, while creating original assets. The protected-zone grid provides the brighter TRON-like element.

Current UI colors are a useful starting point, not a requirement: navy `#111E28`, slate `#1C303D`, cyan `#5AE2DF`, muted text `#9FB3BC` and light text `#EDF5F8`. Reserve orange for requirements and red for urgent danger or destructive actions. Give Ages distinct accents, always paired with their written name.

Prioritize hierarchy: current Age, selected item, requirements and primary action should be clear at a glance. Keep explanations short and put detail behind a guide, tooltip or secondary panel. Missing-material feedback should be stable, attached to the relevant action and disappear cleanly. Avoid tooltip trails and overlapping panels.

The pack also has a minimap, block-information overlay, chat and TaCZ weapon HUD. Place urgent warnings and control cards so these elements can coexist. Design for different GUI scales and narrower screens, long localized text, mouse and keyboard navigation, and color-vision differences. Item and upgrade icons need transparent backgrounds without stray edge pixels.

## Requested handoff

- Editable mockups for the main beacon tabs, reward comparison, weapon browser, Workshop, confirmations and expanded/collapsed Gun Guide.
- Button and tab states: normal, hover, pressed, selected, disabled and locked; warning and error states.
- Original transparent pixel icons for stations, beacon upgrades, components, Ammo Coin and display families. Supply native-size pixels and clean larger previews; 16, 32 and 64 pixel exports are useful.
- A layout and spacing sheet, font recommendations and a palette with contrast guidance. If panels use textures, provide stretch or nine-slice guidance.
- At least one beginner mockup and one busy late-game mockup with real item names, quantities, locked options and all separate station Ages.

Deliver designs rather than baked screenshots of text. Key labels, item names, counts, timers and progression remain live game data. New designs must preserve the gameplay decisions and warnings described here.

## Release flavors

**Pack edition:** uses Create Arsenal's curated factory progression, materials, quests and integration recipes.

**Standalone edition:** Defense Beacon requires TaCZ and Forge, while Create, KubeJS, JEI and Gun Displays are optional. It supplies vanilla-based recipes and imports loaded TaCZ weapons into a generic progression catalogue. Automatic Age and price estimates can be overridden in its configuration; they are a starting balance, not a promise that every third-party pack has identical power. Gun Guide and Gun Displays can also run independently with TaCZ.

The editions share mod IDs. Install one flavor of each mod. Artist layouts should support both editions: write “progression” where a standalone player may not have Create installed, and avoid directing that player to an unavailable mod.
