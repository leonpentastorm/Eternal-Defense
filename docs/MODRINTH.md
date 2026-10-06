# Create Arsenal

### Build a home worth defending. Build a factory that keeps it alive. Fill an armory with weapons worth displaying.

**Minecraft 1.20.1 · Forge · built around [Timeless and Classics Zero](https://modrinth.com/mod/timeless-and-classics-zero) guns**

Night falls. Somewhere past the edge of your base, something is gathering. Your turrets are loaded, your walls are patched, and the beacon in the middle of it all is humming. You have one day left to get ready.

**Create Arsenal turns Minecraft into a shared campaign of building, industry and base defense.** Plant a Defense Beacon, survive your first raid, and grow a ramshackle camp into a fortified home, a humming factory and a fully equipped armory.

![Screenshot: a fortified base under a raid](REPLACE_ME)

---

## Your base is the objective

Plant the **Defense Beacon** and a glowing grid marks your protected zone. From then on, the beacon is what you fight for. Lose it and the campaign is over. Hold it and everything gets better.

- **Raids that scale with you.** Enemy numbers adapt to how many players are defending. Every third raid is a harder encounter with a **boss**.
- **They breach, they dig, they shoot.** Attackers come from beyond the zone, go for your doors first, and punch through walls if they have to. Ranged enemies hang back and fire from a distance.
- **Win and the damage is repaired.** Victory restores everything the raid broke. Defeat leaves the ruins as they are.
- **A beacon that grows with you.** Three Core upgrades take it from **Mk-1 to a hulking Mk-4**, with separate upgrades for logistics, damage reduction, healing, attacker detection and vertical protection.
- **A beautiful base pays off.** Your base is scored on structure, palette, details, lighting, furnishings, layout, machinery and weapon stations. Build something worth defending and the rewards get bigger. So do the raids.
- **Take a break, or don't.** Pick a lower raid level, call the next raid early, or pay to push the schedule back.

![Screenshot: the beacon at Mk-4](REPLACE_ME)

---

## The Universal Weapon Platform

Every serious defender needs a workshop. Four connected stations keep a huge weapon collection organised instead of overwhelming:

| Station | What it does |
| --- | --- |
| **Weapons** | Search, filter by type and era, craft guns, turrets and supplies |
| **Ammo** | Shows exactly the ammunition and magazines your equipped gun can use. Pay with materials or **Universal Ammo Coins** |
| **Attachments** | Compatible upgrades for the gun in your hands, with a count of what is still locked |
| **Armor** | Helmets, vests, plates, pouches, boots and vision gear |

Five eras of progression take you from **frontier rifles to futuristic hardware**. A modern pistol stays a modern unlock, so the weapon you want is always something to work towards.

![Screenshot: the weapon browser](REPLACE_ME)

---

## NEW: Ardent Energy, and the support gun that answers your call

Hostile mobs now drop **Ardent Energy**, a glowing crystal currency. Spend it at the **Exchange Shop**, a proper vending machine that trades energy for items (you edit the offers in a plain text file), and on **your own personal support base**.

### Every player gets their own Support Platform and Support Cannon

Buy a **Support Platform** (and an **Exchange Shop**) at the Weapon Platform's workshop, place them **anywhere inside your beacon zone**, buy a hulking **3 × 3 Support Cannon** and build your base around it. Every piece carries a brass plate with **your name** on its back. Fill the platform's supply grid with whatever you want delivered. Then throw a flare:

- **Support Flare.** The cannon grinds around to face your flare, fires, and a **supply chest parachutes in** with everything you loaded. A countdown ticks in chat after the shot. Underground, it appears at your feet.
- **Return Flare.** Throw it and a **purple portal opens where it lands**. Step through and you are standing at your platform. Upgrade it and the portal stays open for five minutes and **takes you back again**.
- **Fire Support Flare.** A glowing box marks the target. Your cannon shells it, exactly on the flare, every time. Throw a second one mid-barrage and the cannon tells you *Barrage in process*: it is next in line.

The cannon is **heavy**: it turns with a grinding of gears and only fires once it has settled onto the flare. Far from your base? Chat keeps you posted: *Cannon preparing…* and *You heard cannon fire roaring*.

### Six kinds of fire support. Pick yours.

Click your cannon to choose; each type has its own icon, and the one you have chosen is shown in big letters above your hotbar while you hold a Fire Support Flare. **Your choice, your character**: even if a friend hands you their flare, you call down *your* support, and the whole server sees it.

> **Dev just called in NARUKAMI'S FAVOR**

| | |
| --- | --- |
| **Explosion Barrage** | A classic. Shells land exactly on the flare and flatten everything hostile in the box |
| **Arrow Cluster Bomb** | A shower of arrows over the whole area, with two extra volleys |
| **Narukami's Favor** | Lightning on every hostile mob in the area |
| **Bunker Buster** | One enormous bomb that **digs out every block in its box** like butter. The only support that can hurt a player (half their health, armor or not) and it refuses to go off inside your own base |
| **Healing Barrage** | Explosions that heal your team |
| **Curse of Debilitation** | The worst debuffs in the game on every enemy in the zone |

### Upgrade the cannon

Faster traverse · faster fire rate · more volleys · more damage · **Quantum Tunneling** (fire support works underground) · a **slowness field** over the whole target area · a **lasting, two-way return portal** · a **healing aura** around your supply flares · **Area of Effect** (the red box grows with every level) · **Dimensional Link** (call fire support in the Nether, the End and beyond).

![Screenshot: the support cannon firing](REPLACE_ME)

---

## Show it off: Gun Displays

Eleven handcrafted **stands, wall racks and glass cases** for your collection: standing, wide, pistol, heavy and wall-mounted versions. Your trophy room counts towards your base score, so showing off is also good strategy.

---

## Never fumble in a firefight: Gun Guide

A small client-side HUD that always shows **the real controls for the gun in your hands**, using *your* key bindings, with warnings when two actions share a key. If a supported gun **jams** mid-fight, a clear alert tells you how to clear it. It is meant for the moment you have forgotten which key reloads.

---

## Easy to learn

You do not need to read a wiki. New players get a starter weapon with matching ammunition, a **field guide** and the beacon tools. Every screen uses clear icons, plain-language requirements and a "How to unlock" line on anything locked. Raid warnings reach every player (**BEACON IS BEING ATTACKED**), and chat reminds you a day before a raid starts, then every six in-game hours. When you win, you will know about it.

---

## Editions

| | Standalone | Modpack edition |
| --- | --- | --- |
| Mods | Defense Beacon, Gun Guide, Gun Displays | The same three, with the curated Create progression |
| Needs | Forge 1.20.1 and TaCZ | Create, KubeJS and the pack's recipe set |
| Best for | Dropping into any TaCZ world | The full Create factory experience |

---

## FAQ

**Is this multiplayer?** Yes. The campaign is shared. One beacon per world, one base for everyone, and each player owns their own support gear.

**Do I need Create?** Not for the standalone edition. The modpack edition builds its progression on Create.

**Can I change the exchange offers?** Yes. They live in `config/arsenal-beacon-exchange.txt`, one `item = price` per line. Drop chances live in `arsenal-beacon-common.toml`.

**Can the support cannon hurt me?** Not unless you stand in the box of a Bunker Buster. Flares, shells, lightning and arrows never hurt players; the Bunker Buster takes half your health whatever armor you wear, so keep clear of its red cube.

**Does my support gear move with me?** Yes. Pick it up for free with the Beacon Recovery Shovel and it keeps its upgrades and contents.

---

*Your base is the objective. Your workshop is the progression system. Light the beacon.*
