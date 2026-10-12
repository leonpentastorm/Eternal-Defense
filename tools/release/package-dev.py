#!/usr/bin/env python3
"""Test bundles of the dev branch (project 0.0.34, branch feature/supply-catapult: the live catapult, the box as a supply drop, fishing and husbandry as one, the specialty cap; 0.0.33: the Supply Catapult and the specialty seller, placement previews; 0.0.32: village research, the monster hunter guild, a 30-minute S-tier floor, icons; 0.0.31: tribute you choose, the Receiving Gate's storage, FOB management at the Command Table; 0.0.30: the owner's 4-wide two-lane gates, where a FOB counts, a bigger enchanting screen, a scrolling Workshop; 0.0.29: enchanting prices doubled; 0.0.28: the owner's list after the FOB slices; 0.0.27: FOB slice 4, the Tactical Enchanting Table; 0.0.26: the Return Flare needs the cannon, ammo parts on the platform; 0.0.25 FOB slice 3, SOS and repairs; 0.0.24 FOB slice 2, the supply rail; 0.0.23 FOB slice 1; 0.0.22 the supply and recall radio lines, ammo parts, a hunt's minimum pay; 0.0.21 armed raid skeletons, night raids, the one-block arrival, iron at the Exchange; 0.0.20 Tactical Operations and the owner's raid list; 0.0.19 the owner's music and radio chatter; 0.0.18 hand-tuned sounds and fire support rework; the merged 0.0.13 to 0.0.17), one ZIP per edition
(each holds that edition's four JARs, the required unmodified backend JAR, the docs and the evidence; split so each stays under 30 MB)."""
import argparse, hashlib, zipfile
from pathlib import Path

parser=argparse.ArgumentParser()
parser.add_argument('--backend-jar',type=Path,required=True)
args=parser.parse_args()
repo=Path(__file__).resolve().parents[2]
project_version=(repo/'VERSION').read_text().strip()
backend=args.backend_jar.read_bytes()
with zipfile.ZipFile(args.backend_jar) as jar:
    metadata=jar.read('META-INF/mods.toml').decode()
    if 'tacz_attributes' not in metadata or 'version="1.4"' not in metadata.replace(' ', ''):
        raise SystemExit('Expected published TaCZ Attributes 1.4 JAR')
files={}
for edition in ('pack','standalone'):
    suffix='-standalone' if edition=='standalone' else ''
    for mod,version in [('arsenal-beacon','0.21.0'),('arsenal-gun-guide','0.21.0'),('arsenal-displays','1.1.0')]:
        name=f'{mod}-{version}{suffix}.jar'
        files[f'{edition}/{name}']=(repo/'custom-mods'/mod/'build/libs'/name).read_bytes()
    files[f'{edition}/tacz-attributes-1.4.jar']=backend
# 0.0.26: the pack's ammo recipe script (the cases and propellant per batch); copy it over the pack's own
files['pack/kubejs/server_scripts/20_guns_and_ammo.js']=(repo/'pack-integration/kubejs/server_scripts/20_guns_and_ammo.js').read_bytes()
# 0.0.28: the restoration matrix recipe (8 per craft, a Brass Receiver); copy it over the pack's own
files['pack/kubejs/server_scripts/15_beacon_recipes.js']=(repo/'pack-integration/kubejs/server_scripts/15_beacon_recipes.js').read_bytes()
for name in ['STANDALONE.md','PACK-EDITION.md','ROUND-0034-TESTING.md','ROUND-0033-TESTING.md','ROUND-0032-TESTING.md','ROUND-0031-TESTING.md','ROUND-0030-TESTING.md','ROUND-0029-TESTING.md','ROUND-0028-TESTING.md','FOB-SLICE-4-TESTING.md','ROUND-0026-TESTING.md','FOB-SLICE-3-TESTING.md','FOB-SLICE-2-TESTING.md','FOB-SLICE-1-TESTING.md','ROUND-0022-TESTING.md','RAID-ROUND-0021-TESTING.md','TACTICAL-OPERATIONS-TESTING.md','NEW-SOUND-TESTING.md','HAND-TUNED-ROUND-TESTING.md','MUSIC-PLAYER-TESTING.md','BEAUTIFY-PATH-TESTING.md','OPTIMIZE-PATH.md','OPTIMIZE-PATH-TESTING.md','RAIDER-GATES-TESTING.md','STUCK-RAIDERS-TESTING.md','MESS-HALL-TIERS.md','TIERS-AND-TABLES-TESTING.md','GUIDE-STYLE.md','GAME-DESIGN-DOCUMENT.md','CHANGELOG.md','HANDOFF.md']:
    files['docs/'+name]=(repo/'docs'/name).read_bytes()
# six screenshots of the latest round (the rest are in the repository): the ZIP must stay under 30 MB
BUNDLED_SHOTS={'standalone-f29_catapult_preview.png','standalone-f29b_catapult_model.png','standalone-f30b_seller.png','standalone-f31_catapult_throw.png','standalone-f31d_parcel_at_beacon.png','standalone-f32_beacon_preview.png',
    'standalone-f30f_seller_live_throw.png','standalone-f30h_seller_box_in_air.png','standalone-f31e_parcel_parachute.png','standalone-f29d_fob_catapult_online.png','standalone-f23d_fob_research_top.png','standalone-f17m_gate_fishing_husbandry.png'}
# screenshots of the latest round only (each ZIP must stay under 30 MB, the beacon JAR alone is 24 MB); the older rounds' text evidence, their screenshots are in the repository
for folder in ('round-0034','round-0033','round-0032','round-0031','round-0030','round-0029','round-0028','fob-slice-4','round-0026','fob-slice-3','fob-slice-2','fob-slice-1','round-0022','raid-round-0021','tactical-operations','new-sound','hand-tuned-round','music-player','optimize-path','raider-gates','stuck-raiders'):
    for evidence in (repo/'docs/validation'/folder).glob('*'):
        if evidence.is_file() and (evidence.name in BUNDLED_SHOTS or evidence.suffix=='.txt'):files[f'docs/validation/{folder}/'+evidence.name]=evidence.read_bytes()
files['README.txt']=('''Eternal Defense - dev branch with every new feature - project '''+project_version+''' - local test build
Minecraft 1.20.1 / Forge 47.4.20 / Java 17. NETWORK PROTOCOL 39 since 0.0.34 (38 in 0.0.33): 0.0.34 adds the catapults' live state for
the Command Table, so an older client cannot join this server or the other way round. Install this build on the client AND the server.

Start a FRESH world. A Supply Gate placed before 0.0.30 is not converted: take it back with the Recovery Shovel BEFORE you update
(or right after) and place it again. A world from 0.0.30 keeps its FOBs: each gets a specialty from its number and every slot on,
and what its old stockpile held moves into the Receiving Gate's storage. A world from 0.0.31 keeps its FOBs at research level 0
(a potion makers' choice may point at another ingredient: the list now runs D to S; choose again). A world from 0.0.31 to 0.0.33 keeps its
FOBs' specialties: fishing and animal husbandry both become Fishing & Animal Husbandry, with the same item chosen. Keep existing worlds separate from this test instance.
This ZIP holds ONE edition (EDITION): install all FOUR JARs of its folder on both client and server, replacing old
versions. Never mix pack and standalone editions or keep duplicate mod IDs.
TaCZ Attributes 1.4 is a required backend and is INCLUDED separately (unchanged since v4).
Also install TaCZ 1.1.8-hotfix2 (existing dependency, not included):
https://www.curseforge.com/minecraft/mc-mods/timeless-and-classics-zero/files/9037989
The pack edition requires the matching full pack's Create/KubeJS and scripts.
This ZIP is an update/test bundle, not a complete modpack.

THIS BUILD IS FROM THE FEATURE BRANCH feature/supply-catapult (0.0.32 is on dev).

WHAT IS NEW IN 0.0.34: THE LIVE CATAPULT, THE SUPPLY DROP, FISHING AND HUSBANDRY AS ONE, A CAP ON THE SPECIALTY
* The specialty seller shows the FOB's Supply Catapult live, however far away it stands: it turns toward your base, throws, the
  box flies out, and a line under it says what it does (ready, turning, throwing, the box in the air and when it lands). The
  catapult now turns and throws the same whether anybody is near it or not, and turns back to rest when it has nothing to throw.
* The box comes home as a supply drop: your chat counts down 12 seconds like the Supply Flare's, then the parcel appears high
  over a spot beside your Defense Beacon and comes down by parachute.
* Contact Specialty Seller has a handheld radio and plays your radio static; every purchase plays one of your four order lines.
* Fishing & Animal Husbandry is one specialty, both names, both icons, both lists (12 items).
* The FOB screen says whether the Supply Catapult is ONLINE or OFFLINE, and why. The Research tab's level 5 reads "Supply
  Catapult + Specialty Shop", and reaching it is announced.
* Nerf: a specialty sends at most 4 S-tier items' worth an hour (64 value; 8 A-tier, 16 B-tier ...), however big the FOB, its
  research or the slots switched off. The 30-minute promise (the slowest FOB, research 4, every slot on) is under it.
* The monster hunter guild no longer sends the totem of undying (too strong). A FOB that chose it sends rotten flesh until you
  choose again.

PLAYTEST SCRIPT FOR 0.0.34 (about 10 minutes)
1. FOB Beacon: the FOB tab's Supply Catapult line (OFFLINE, why). Research tab: level 5 reads Supply Catapult + Specialty Shop.
2. With research 5 and a catapult placed: the line reads ONLINE. At home, open the Command Table's FOBs tab, press Contact
   Specialty Seller (the radio static), Buy 1 lot (an order line): watch the catapult turn and throw in the seller.
3. Close the table and look up beside your Defense Beacon: the chat counts 12 seconds and the parcel parachutes down.
4. A potion makers' FOB at research 5 with every other slot off: the gate screen shows at most 4 ghast tears an hour.

WHAT WAS NEW IN 0.0.33: THE SUPPLY CATAPULT AND THE SPECIALTY SELLER, PLACEMENT PREVIEWS
* Supply Catapult (your model): buy it at Workshop Fabrication (16 logs, 8 iron, 16 string, plus 2 logistics modules in the
  pack or 8 Ardent Energy standalone). Place it in a FOB's village, one per FOB. Mine it with an axe to move it.
* Command Table, FOBs tab: every FOB row has Contact Specialty Seller. It opens when the FOB is ACTIVE, its research is at
  level 5 and its catapult stands (the button's tooltip says what is missing). The seller lists every item of the FOB's
  specialty: pick one, Buy 1 lot or Buy 4 lots. A lot is worth one S-tier item (1 ghast tear, 2 ender pearls, 16 bones ...)
  and costs 4 Ardent Energy standalone, 2 in the pack. Up to 4 boxes wait at a catapult.
* After you buy, the catapult turns toward your Defense Beacon, throws the box, and the box parachutes down beside the beacon
  (0.0.34: 12 seconds later, counted down in chat). You read when it is thrown and where it landed.
* FOB zone outline: the three lines that followed you are gone; the FOB Beacon's ground level is marked with a tick every block.
* Placement preview for every block of the mod: hold it and look at the ground for a see-through copy and its outline. The
  Defense Beacon also shows the base zone it would get.

PLAYTEST SCRIPT FOR 0.0.33
1. Take a FOB to research level 5 (FOB Beacon, Research tab). Buy a Supply Catapult; hold it in the village: the preview. Place it.
2. At the Command Table's FOBs tab, press Contact Specialty Seller. Pick an item, Buy 1 lot. Go to the village: the catapult
   turns and throws (or stay home: it throws anyway). Come back home: the box parachutes down beside your beacon.
3. Hold other blocks (a Command Table, a Defense Beacon) and look at the ground: the previews.

WHAT WAS NEW IN 0.0.32: VILLAGE RESEARCH, THE MONSTER HUNTER GUILD, 30 MINUTES FOR AN S-TIER ITEM, ICONS
* Dragon's breath is S tier now (it was B).
* 30 minutes for an S-tier item, even at the slowest: a FOB with 3 villagers, gates 128 blocks apart and all four slots on makes
  one S-tier item every 30 minutes of play in its specialty slot once its research is at level 4. Everything else makes more:
  20 villagers twice as much, a 1,000-block line twice again, up to 20 percent more at 5,000 blocks (the best FOB 4.8 times
  the slowest), one slot alone 4 times, riding back to back 2.5 times. Worths: D 1, C 2, B 4, A 8, S 16, an emerald 4, iron 2.
* Village research: the FOB Beacon has a Research tab. Give emeralds (1 point each), diamonds (4) or Ardent Energy (2), one or
  ten at a time, while the FOB is ACTIVE. 5 levels (16, 48, 96, 160 and 240 points). Level 2 opens the specialty's A-tier items,
  level 4 its S-tier items (the potion makers' best, the guild's ender pearls and better, the trader's endgame goods). Every
  level: the specialty +25 percent, emeralds, the mine and the farm +5 percent. Locked items have a padlock in the gate's grid.
  Taking the FOB back with the shovel loses its research (as it loses its specialty).
* The monster hunter guild: a fifth specialty with every monster drop but the bosses' (ender pearls are A).
* Icons: every loot slot, specialty and research has a round icon with a villager at the job, in the style of the paintings
  (the first flat icons are kept in the JAR, unused). The gate's grid shows each item's tier letter.
* Command Table map: a Names switch under the map hides the FOBs' names and distances (the flags, zones and arrows stay).

PLAYTEST SCRIPT FOR 0.0.32
1. Set up a FOB with both gates as before. Open the Receiving Gate: the slot cards have their icons. Press Choose on the
   specialty: the A and S items have a padlock; point at one to read the level that opens it.
2. Open the FOB Beacon's screen, Research tab. Give a few emeralds or diamonds (+1, +10); watch the bar and the chat line at a
   new level. Reach level 4 (160 points) and choose an S item at the gate.
3. On a monster hunter guild (one FOB in five; otherwise take a FOB back with the shovel and plant a new one): choose ender
   pearls after level 2.
4. Over 30 minutes of play on a small, slow FOB at level 4 with every slot on, the specialty should bring one S item.
5. Command Table, a map: press Names; the names go, the flags and arrows stay.

WHAT WAS NEW IN 0.0.31: TRIBUTE YOU CHOOSE, THE RECEIVING GATE'S STORAGE, FOB MANAGEMENT
* Every FOB has four loot slots instead of the random goods: Emeralds (every village, at least 1 every 3 deliveries), The mine
  (redstone, iron, andesite, flint, gunpowder or charcoal), The farm (one plant food) and its Specialty, rolled when you plant
  it: Fishing, Animal husbandry, Potion makers (your tier list: S takes 16 times as many deliveries as D) or Wandering trader hub
  (an item costing more emeralds takes longer). Choose each slot's item on either gate's screen (Choose). To get another
  specialty, take the FOB Beacon back with the shovel and win a new FOB.
* Each slot has an ON/OFF switch: the slots still on share the switched-off slot's part. Switch three off and the last one
  gets the whole delivery.
* Far bonus: nothing up to 1,000 blocks between the gates, then up to 20 percent more at 5,000 blocks.
* The Receiving Gate has its own storage, a double chest (take only). Every delivery lands there, never in the reward chest.
  When it is full, deliveries wait (amber). Taking the FOB back puts what it holds in your inventory. Both gates now cost 2
  chests more.
* The gate screen takes the whole window: the storage, the light, a live countdown to the next delivery, each slot's item, how
  many per delivery (or one every N deliveries) and about how many an hour. On a small window (for example 1920 x 1080 at GUI
  scale 4) the storage has its own tab. The FOB screen sums up its tribute.
* Command Table: a new FOBs tab lists every FOB (name, distance and direction from the base, villagers, state, route light);
  during an SOS it shows the time left to answer and reprints the SOS map if you lost it. Both maps show the FOBs; a FOB beyond
  the map's edge is an arrow at the edge with its name and distance.
* Balance: a delivery by itself comes at most every 5 minutes of play; a round trip you ride brings up to two deliveries, at
  most 2.5 times what the route would have sent by itself since the last one (so riding a tiny loop over and over pays nothing
  extra).
* Bug fix: on a dedicated server the enchanting table's buy buttons did nothing (since 0.0.27). Fixed for every screen.

PLAYTEST SCRIPT FOR 0.0.31
1. Set up a FOB with both gates and a loop, as in 0.0.30 (the gates now need 2 chests each). Ride the loop once.
2. Open the Receiving Gate (click its beam): the storage on the left holds the ride's delivery. Take something out; try to put
   something in (it refuses).
3. On the Tribute tab: switch the mine OFF and watch the other slots' numbers grow; switch it back ON. Choose iron for the mine
   and something for the farm and the specialty (point at an item for its rate). Watch the countdown.
4. Make the window small (or GUI scale 4 at 1920 x 1080): the storage gets its own tab; nothing should overlap anywhere.
5. Open the FOB Beacon's screen: the middle column sums up the tribute.
6. Command Table: the FOBs tab. As an operator, /arsenal fob sos: the tab shows the time left; drop the SOS map and press
   Reprint SOS map. Look at both maps for your FOB's flag or the arrow at the edge.
7. On a dedicated server: buy an enchant at the Tactical Enchanting Table (the buttons work now).

WHAT WAS NEW IN 0.0.30: YOUR 4-WIDE GATES, WHERE A FOB COUNTS, TWO SCREENS
* Supply Delivery and Receiving Gates: your new 4 wide x 2 tall x 1 deep models, across two rails side by side. Use the gate on
  one of the two rails: it stands across both (the second rail decides the side). On a single rail it still works; click the
  half of the rail on the side where the empty lane should go. Best build: a loop, two rails through both gates joined by a
  curve at each end, so the cart goes out on one rail and comes home on the other. A seated rider passes under the beam.
  Both columns show the signal. The FOB's name is on the nameplate.
* Both gates are sold at Workshop Fabrication (the Delivery Gate still also at the FOB shop; still one per FOB).
* Holding a FOB Beacon and looking at the ground: a see-through FOB where it would stand, an outline of the village part it
  would take (green: you can plant it; orange: not), and a line under the crosshair with its villagers, beds and workstations,
  or why not.
* A planted FOB's zone (meal bonus, FOB shop) shows as an olive outline when you are near it. The FOB screen has a Zone outline
  switch to turn it off (for everyone).
* Tactical Enchanting Table: a bigger screen (440 x 260), every line in its own place; nothing overlaps with the slot empty.
* Workshop Fabrication scrolls (mouse wheel or the bar on the right) instead of squeezing its rows.

PLAYTEST SCRIPT FOR 0.0.30
1. Plant your Defense Beacon. Buy a FOB Beacon at Workshop Fabrication (scroll the list). Walk into a village holding it and look
   at the ground: the ghost, the outline and the line under the crosshair. Find a green spot and plant it.
2. Win or complete its raid (/arsenal fob complete). Walk around: the olive outline is where meals double. Open the FOB screen
   and switch Zone outline off and on.
3. Lay two rails side by side through the village and use the Supply Delivery Gate on one of them. Lay two rails at home and
   place the Supply Receiving Gate the same way; pick the FOB on its screen. Join the two lines into a loop (a curve at each end,
   at least 128 blocks between the gates).
4. Sit in a minecart and ride the loop once round: the route opens on the way out, the round trip delivers on the way home.
5. Open the Tactical Enchanting Table with its slot empty: nothing overlaps.

WHAT WAS NEW IN 0.0.29: ENCHANTING PRICES DOUBLED
* Tactical Enchanting Table, both editions doubled: standalone 4, 6, 10, 16 Ardent Energy per level by rarity, the pack
  8, 12, 20, 32 (treasure enchants twice that). Mk I, II, III still take 17, 33, 50 percent off, so a Mk III table halves every
  price (Sharpness V: standalone 20, or 10 at Mk III; pack 40, or 20 at Mk III).

WHAT WAS NEW IN 0.0.28: YOUR LIST AFTER THE FOUR FOB SLICES
* Tactical Enchanting Table: the pack edition pays twice the standalone price (4, 6, 10, 16 Ardent Energy per level by rarity),
  the standalone price stays (2, 3, 5, 8). Mk I, II and III take 17, 33 and 50 percent off: a pack table at Mk III charges the
  standalone price.
* Warlord Seal: sell it at the Exchange Shop for 4 Ardent Energy (pack) or 8 (standalone). If you never edited your world's
  config/arsenal-beacon-exchange.txt it updates by itself; if you did, add the line "arsenal_beacon:warlord_seal = 4" (pack) or
  "= 8" (standalone) under [sell].
* Platform costs that take any log now say "Wood (any log)" instead of "Dark Oak Log".
* Patrols pay twice what they did, at every tier.
* Pack: the Beacon Restoration Matrix recipe makes 8 and takes a Brass Receiver instead of a Hardened Receiver; everything paid in
  matrices costs half (Restoration branch, the Vertical zone's share, Quantum tunneling 6, Healing aura 4, Dimensional link 10, the
  platform's last era 8). Copy pack/kubejs/server_scripts/15_beacon_recipes.js from this ZIP over your pack's file of that name.
* Pack: every won raid gives one Pressed Receiver.
* Bunker Buster: five barrage shells' damage (was two). Cluster Strike: six shells over its 12 seconds (was 80 percent of six).
  Gravity Well: 6 seconds + 2 per More volley level, 12 at the top. Starshell: 30 seconds + 10 per More volley level, a full
  minute at the top, and Speed II instead of Haste II.
* FOB tribute brings emeralds too (a quarter per villager per hour of play).
* Why some bases only see Patrols: the Satellite Beacon's Mk (not the Defense Beacon's) sets what it finds. Mk I finds Patrols
  only; Mk II adds Garrisons, which need a pillager outpost, desert or jungle temple, swamp hut, igloo or mansion in range (else a
  Patrol is offered); Mk III adds Warlords.

PLAYTEST SCRIPT FOR 0.0.28 (about 15 minutes)
1. Enchanting table: read a level's price in both editions if you can; upgrade to Mk III and check a pack price equals the
   standalone one.
2. Sell a Warlord Seal at the Exchange Shop (Sell tab). (As an operator: /give @s arsenal_beacon:warlord_seal.)
3. Open a platform recipe that needs logs: it should say "Wood (any log)" and take any log.
4. Clear a Patrol: its pay in the reward chest is twice the old one (the offer card shows it).
5. Pack: craft restoration matrices (8 per craft, Brass Receiver); check the Restoration upgrade and the cannon's Quantum,
   Aura and Dimension prices are half. Win a raid: one Pressed Receiver in the prizes.
6. Fire a Bunker Buster, a Cluster Strike, a Gravity Well and a Starshell (with More volley 3 if you have it): compare with
   the cannon menu's numbers; the Starshell should give Speed.
7. Ride or wait for FOB tribute: emeralds arrive with it; the gate screen lists nine goods.

WHAT WAS NEW IN 0.0.27: FORWARD OPERATING BASES, SLICE 4 OF 4, THE TACTICAL ENCHANTING TABLE
* Your enchanting bench model: the Tactical Enchanting Table, bought at Workshop Fabrication (12 iron, 4 obsidian, 3 books, plus
  2 resonance coils in the pack edition or 8 Ardent Energy in the standalone one). One per base, in your zone; the Recovery
  Shovel moves it.
* Put a weapon, tool or armor in its slot: every vanilla enchant that fits is listed, a button per level with its price in
  Ardent Energy. No experience, no lapis, no books. Curses are never sold. Vanilla tables, librarians and anvils still work.
* Level I is always open. Higher levels need ACTIVE FOBs: Sharpness II needs 2, III 3, IV 4, V all five. A DOWN FOB does not
  count. A locked level's tooltip says why.
* Price per level: 2 (common), 3 (uncommon), 5 (rare), 8 (very rare) Ardent Energy times the level in the standalone edition
  (0.0.28: twice that in the pack). Upgrade the table to Mk I, II, III (pack 8, 16, 32 resonance coils; standalone 6, 12, 24
  Ardent Energy) for a discount, for everyone (0.0.28: 17, 33, 50 percent).
* Warlord Seal (new item): a cleared Warlord hunt pays one into the reward chest. Spend it at the table to unlock a treasure
  enchant for the whole team: Mending, Frost Walker, Soul Speed or Swift Sneak (the last two take one more seal for each of
  levels II and III).

WHAT WAS NEW IN 0.0.26: YOUR TWO FIXES
* The Return Flare needs your Support Cannon again, like every flare. Only the return gate an operation opens when there is no
  ground stays free.
* Pack edition: the Universal Ammo Platform now charges the 0.0.22 ammo parts itself (2 x the propellant, 20 percent more cases):
  your pack still ran the old KubeJS script, which is why you saw the old amounts. Weak special rounds (flares, bean bags,
  pepperballs, water, medkits) keep 1 case and 1 propellant for a small batch.
  Copy pack/kubejs/server_scripts/20_guns_and_ammo.js from this ZIP over your pack's kubejs/server_scripts/20_guns_and_ammo.js
  so JEI's gun smith recipes show the same amounts (the platform does not need it).

WHAT WAS NEW IN 0.0.25: FORWARD OPERATING BASES, SLICE 3 OF 4, SOS AND REPAIRS
* Every 10 to 12 hours of play (counted only while someone is online, never during your beacon's raid or the day before one) one
  ACTIVE FOB calls for help: a chat line and a bell, a red SOS line on your status card, and a map with a red X on the FOB.
* Go there within 7 days of play: the village raid starts as soon as someone stands in the village (Bad Omen I). Win it: nothing
  changes. Lose it, or stay away: the FOB is DOWN (no shop, no tribute, the supply route stops; its gates turn red).
* Villagers killed in a FOB's raid are remembered. Repair a DOWN FOB at its FOB Beacon: each lost villager is listed with its
  price; they come back with their trades. Standalone 6 Ardent Energy plus 1 to 5 per villager (by level), pack 3 plus 1 to 3.
  A FOB that never won its first raid starts that raid again after the repair.
* Operators: /arsenal fob sos (an SOS now at the nearest ACTIVE FOB), /arsenal fob sos expire (let it run out).

WHAT WAS NEW IN 0.0.24: FORWARD OPERATING BASES, SLICE 2 OF 4, THE SUPPLY RAIL
* Your two gate models: the Supply Delivery Gate (bought at an ACTIVE FOB's shop, one per FOB) and the Supply Receiving Gate
  (Workshop Fabrication). Each costs 16 iron, 8 copper, 8 redstone, plus 1 logistics module (pack) or 4 Ardent Energy
  (standalone). Use one on a straight rail: the arch stands across it. The Delivery Gate goes in the FOB's village and links
  itself; the Receiving Gate goes in your base zone and asks which FOB it serves. Both signs show the FOB's name.
* An ACTIVE FOB's villagers make goods (redstone, gunpowder, flint, bread, carrots, potatoes, leather, paper) into a stockpile.
* Sit in a plain minecart and ride from the Receiving Gate through the Delivery Gate once (they must be 128+ blocks apart): the
  route opens and that ride's time is measured. From then on the FOB sends goods to your beacon's reward chest by itself, with
  no cart and no rail needed, as long as its inspection lasts (8 hours of play). Ride a full round trip (home, FOB, home) to
  bring twice as much at once and renew the inspection.
* The light on each gate: GREEN all good; AMBER a ride under way, the inspection running out, or the reward chest backed up
  (200 stacks waiting); RED something is missing. Click a gate: what it needs, with a tick or a cross and the fix.
* Your gate models and textures are used unchanged. The lamps are lit by a small overlay we added (a new texture
  supply_gate_signal.png and three overlay models), switched by a "signal" block state; docs/HANDOFF.md lists it.

WHAT WAS NEW IN 0.0.23: FORWARD OPERATING BASES, SLICE 1 OF 4
* FOB Beacon (your FOB station model): buy it at Workshop Fabrication (16 iron, 16 copper, 8 redstone, 4 glass, plus 2 logistics
  modules and 2 reinforced plating in the pack edition, or 8 Ardent Energy in the standalone one) and plant it in a vanilla village.
  It needs your Defense Beacon planted, a village with at least 3 villagers and no FOB yet; up to 5 FOBs, Overworld only.
* Planting starts a village raid at once (Bad Omen I: 3, 5 or 7 waves by difficulty). Win it: the FOB is ACTIVE. Lose it (the
  village falls, or fewer than half of the villagers living there at the start survive): DOWN. Take a DOWN FOB back with the
  Recovery Shovel and plant it again to retry (repairs come in slice 3).
* Outside its own raid a FOB's villagers cannot be hurt (only /kill and the void get through).
* ACTIVE FOB: a shop with Support (supply drop), Return and Fire Support Flares at the Support Platform's prices (3, 2, 6 Ardent
  Energy); the flares work as at home (they need your own Support Platform and your cannon). Meals do not run down in its
  village and give the home bonus (the new FOB Zone effect).
* Raiders of village raids drop no Ardent Energy; your beacon's raiders still do.
* Right-click the FOB Beacon: its screen (state, villagers, the raid, the shop, Call the raid). A Name Tag renamed at an anvil
  renames the FOB (names are unique). The status card shows the FOB you stand in, and with the shovel in hand how many you have.
  The Recovery Shovel takes a FOB Beacon back after a confirmation. Operators: /arsenal fob list|status|complete|reset
  (0.0.24 adds deliver and overdue).

PLAYTEST SCRIPT FOR SLICE 1 (a fresh world; about 20 minutes)
1. Plant your Defense Beacon. At Workshop Fabrication buy a FOB Beacon (FOBs page of the Field Guide: price and rules).
2. Find a village. Try to plant the FOB Beacon outside it, then in it: read the refusals and check they make sense.
3. Plant it in the village (Normal difficulty). A raid starts: the boss bar, the chat line naming the FOB and the number of
   villagers to keep alive. Right-click the FOB Beacon during the raid: wave, raiders left, villagers alive.
4. Fight the raid. Win it: the FOB turns ACTIVE (chat line). Or let villagers die: below half it goes DOWN at once.
5. ACTIVE: open the FOB Beacon, buy a flare of each kind, throw them in the village (do they work as at home?).
6. Eat a meal, walk into the village: the FOB Zone effect appears and the meal stops running down; walk out: it counts down.
7. Hit a villager: nothing happens. Rename the FOB with a Name Tag; try the name of another FOB.
8. Hold the Recovery Shovel: the status card line. Use the shovel on the FOB Beacon: the confirmation, then it is back in your
   inventory and the villagers can be hurt again.
9. Tell us: did the raid feel right for a first FOB? Was half of the villagers a fair line? Anything confusing in the texts?

PLAYTEST SCRIPT FOR SLICE 2 (after slice 1, with an ACTIVE FOB; about 30 minutes)
1. Open the FOB Beacon and buy the Supply Delivery Gate. Lay a straight rail in the village and use the gate on it: the arch
   stands across the rail and its sign shows the FOB's name. Try to buy a second one: refused.
2. At home buy a Supply Receiving Gate (Workshop Fabrication), lay a rail in your zone (at least 128 blocks from the FOB's gate)
   and use the gate on it: pick the FOB. Both signs show its name; both lights are red. Click a gate: read the checklist.
3. Lay a rail between the gates (or part of the way and ride the rest: only the passes count). Sit in a plain minecart, roll
   through the Receiving Gate (the action bar says the ride started), ride to the FOB through the Delivery Gate: "Route open".
   The lights: amber while you ride, green after.
4. Pick up the rail and the cart. Wait (or play elsewhere): tribute arrives in the beacon reward chest with a chat line.
5. Ride a full round trip (home, FOB, home): "Round trip complete: N items delivered; inspection renewed for 8 h". Compare
   with what came by itself.
6. Fill the reward chest queue past 200 stacks (or leave it full): the light turns amber and nothing arrives; empty it.
7. Let the inspection run out (8 hours of play, or as an operator /arsenal fob overdue near the FOB): the light turns red,
   deliveries stop, the stockpile keeps filling. A round trip brings it back. (/arsenal fob deliver brings the next delivery by
   itself forward to now.)
8. Take a gate back with the Recovery Shovel and place it again: it remembers its FOB; the route needs a new ride.
9. Tell us: is the rail worth building? Too much or too little tribute? Is the gate screen clear?

PLAYTEST SCRIPT FOR SLICE 3 (an ACTIVE FOB with its gates; about 20 minutes)
1. As an operator, at home: /arsenal fob sos. Read the chat line; look at the status card (the SOS line) and the map you got.
2. Ride or walk to the village: the raid starts when you arrive. Fight it. Let a villager or two die if you want to see the repair.
   Win: the SOS ends, the map burns, the FOB stays ACTIVE. Open the FOB Beacon: lost villagers can be brought back.
3. Go home and call another: /arsenal fob sos, then ignore it (/arsenal fob sos expire to skip the 7 days). The FOB is DOWN: the
   gates turn red and say why; the FOB shop is closed; nothing is delivered.
4. Go to the FOB Beacon: the repair list (each lost villager, its price, the total). Pay: the villagers come back with their
   trades and the FOB is ACTIVE again.
5. Try calling an SOS while one is open (refused), and take a FOB back with the shovel during an SOS (it is called off).
6. Tell us: are 10 to 12 hours between calls and 7 days to answer right? Are the repair prices fair?

PLAYTEST SCRIPT FOR SLICE 4 (at home, with some Ardent Energy; about 15 minutes)
1. At Workshop Fabrication buy the Tactical Enchanting Table (FOBs page of the Field Guide: price and rules). Place it in your
   zone; try a second one (refused) and one outside the zone (refused).
2. Click it, put a sword in the slot. Read the list: level I buttons open, higher ones locked; hover a locked one.
3. Buy Sharpness I. With 2 ACTIVE FOBs buy Sharpness II (as an operator, /arsenal fob complete near a planted FOB makes it ACTIVE).
   Try Smite on the same sword (refused: it conflicts).
4. Upgrade the table to Mk I: the prices drop by 17 percent (0.0.28).
5. Clear a Warlord hunt (Satellite Beacon Mk III), take the Warlord Seal from the reward chest, and unlock Mending at the table.
   Buy it. (As an operator: /give @s arsenal_beacon:warlord_seal.)
6. Close the screen: the sword is back in your inventory. Move the table with the Recovery Shovel: the Mk and the unlocks stay.
7. Tell us: are the prices fair against your Ardent Energy income? Is "more FOBs, higher levels" the right pull?

WHAT WAS NEW IN 0.0.22
* Your radio lines: a supply flare answers with one of your two supply lines, a return flare with one of your three recall lines
  (it had none). Fire support flares keep their six. No line plays twice in a row.
* Pack edition: every ammo recipe takes twice the propellant and 20 percent more cartridge cases, rounded up (a batch of 8 rounds
  or fewer: 2 cases and 2 propellant; 32 rounds: 5 cases, 4 propellant; 64 rounds: 10 cases, 8 propellant). Standalone unchanged.
* Bug fix (your report): operations paid at the tier of the last raid you WON, so until you won a raid above tier 0 every one
  paid like tier 0 (a Patrol: 0 Ardent Energy and 1 coin). They now pay, and fight, at the "Next payout" tier your beacon shows.
  The satellite's Mk does not raise a class's pay: it unlocks Garrison (Mk II) and Warlord (Mk III), which pay more.
* Tactical Operations: a mission always pays at least 1 Ardent Energy and 2 ammo coins.

WHAT WAS NEW IN 0.0.21
* Raid skeletons have their bows again and pillagers their crossbows: they shoot from range instead of walking up like zombies,
  and no longer open gate after gate. A skeleton without a bow fights in melee.
* Raids only come at night (all but the first). Start raid by day books the raid for nightfall ("Raid at nightfall").
* A raid comes every 10 active days at reward tier 0, one day sooner per tier, down to every 3 days.
* During a raid cover no longer hides the beacon: monsters within 48 blocks of your zone come for it.
* The alarm: your double beep (cut to 0.6 s), a 0.5 s pause, again, for as long as the red warning shows.
* Tactical Operations: the mission line shows only with the mission map or the Recovery Shovel in hand; the map burns when the
  mission is done; the chosen X on the table is red, Patrol blue, Garrison purple, Warlord gold; no objective on oceans or rivers.
* Arrival: one block is enough. A small glowing zone forms around it, the enemies come out of red gates 8 to 12 blocks out and
  rush in, one per tick (the old spot search is gone). No block at all: you get a return gate home for 5 minutes. Warband enemies
  carry the red "!".
* Starshell Haste lasts 30 s, so you keep it after leaving the light.
* Exchange: the pack edition also sells gold, blaze rods, ender pearls, diamonds and iron at the standalone prices; both editions
  sell 16 iron blocks for 8 Ardent Energy. An untouched old shop file gets the new list; an edited one is kept.

WHAT WAS NEW IN 0.0.20
* Tactical Operations: a Command Table and a Satellite Beacon (Workshop Fabrication, inside the zone) offer hunting missions:
  Patrol, Garrison (around a real structure, Mk II), Warlord (a named, glowing captain, Mk III). Accept one at the table; everyone
  gets a map with a red X and a Return Flare; the warband comes out of red gates when you get within 64 blocks; the last kill pays
  Ardent Energy and ammo coins into the beacon's reward chest. SCAN MAP draws the land around the base on the table.
* No more repeating "Reinforcements are waiting" line: with no open ground far out, enemies come out of red gates closer in, and
  chat says so once a wave. Every enemy the mod places comes out of a red gate, which stands just behind it (bigger than a
  stuck raider's gate) and stays 5 seconds. /arsenal test-gate shows one (operators).
* Raid enemies start with Speed II for 10 seconds.
* Your 0.0.18 incoming sound is back, played where the round lands: heard whichever way you look.
* Cannon menu: Effect, Damage and Area for each fire support, with your own cannon's numbers; the card scrolls.
* Cryo Shell 3 blocks wider every way. Starshell: in this build a player in its light gets Haste II and no Regeneration (checked
  in the game); if you still see Regeneration, tell us which cannon type and which parcel were near (the Healing Barrage and the
  parcel's Healing aura give Regeneration).
* The mission-cleared jingle is a placeholder (your Victory Fanfare) until you send a song for it.

WHAT WAS NEW IN 0.0.19
* Raid music is all yours: Acid Redeemer, Assault Loop, Breach Core and Rolling Wave join the ordinary songs (eight now, as many
  as the longest raid has waves); the themes made for 0.0.16 are gone; winning plays your Victory Fanfare.
* No song plays twice in one raid (a shuffled order). A special raid opens with Anomaly Protocol, a hard raid's boss wave plays
  Boss Battle.
* A falling round whistles again (the earlier whistle), ending as it lands; it still takes 5.15 s from the shot to the impact.
* No more flare siren: a landing flare pops, and when the call is taken your radio answers with one of your six fire support lines
  (random, never the same twice in a row; supply flares get "payload incoming" or "stay out of the drop zone", return flares and
  refused calls none). It follows you, on the Voice/Speech volume slider.

WHAT WAS NEW IN 0.0.18
* Your eight hand-tuned sounds (cannon turning loop, "done" clunk with the shot right after, cannon fire, Cluster Strike crackle
  played 6 dB below your file, Bunker Buster, Shockwave, beacon attack alarm); every other explosion is the game's own again.
* Quantum tunneling only under 3 or more solid blocks; the cannon turns 1.5 times slower; Defense paid in plating, Vertical zone
  in 2 of every part (pack), Reconnaissance 5 / 3 / 1 minutes / at once; Cluster Strike, Napalm Carpet, Cryo Shell and Gravity
  Well are one lingering round; Shockwave pushes to the edge; Starshell lights the area and gives Haste; Arrow Cluster fires
  tipped arrows; the Bunker Buster shakes the camera.

EVERYTHING ELSE ON DEV SINCE 0.0.12
0.0.13 stuck raiders, 0.0.14 Raider Gates, 0.0.15 Optimize Path, 0.0.16 Beautify Path, 0.0.17 raid music player.

WHAT TO LOOK FOR
Run the FOB playtest script above. Throw a supply flare and a return flare and listen to your new lines. In the pack edition, craft ammo and check the cases and
propellant it takes. Walk to a mission with the map in hand and watch the arrival (does it still lag on your machine?). Let a raid come at night and
watch the skeletons shoot. Stand in a Starshell and walk out. Build a Command Table and a Satellite Beacon, press SCAN MAP,
accept a mission and hunt it down with a friend. Play a raid near
forest, water or mountains and watch for the red gates. Throw a fire flare and look away: the incoming scream should still play.
Open the cannon menu and scroll the card.

LesRaisins Tactical Equipements 0.4.3 is OPTIONAL and not included.
Apocalypse gunpack and AppleSkin were not used in these rounds and are not redistributed.

See docs/ROUND-0034-TESTING.md, docs/ROUND-0033-TESTING.md, docs/ROUND-0032-TESTING.md, docs/ROUND-0031-TESTING.md, docs/ROUND-0030-TESTING.md, docs/FOB-SLICE-1-TESTING.md, docs/ROUND-0022-TESTING.md, docs/RAID-ROUND-0021-TESTING.md, docs/TACTICAL-OPERATIONS-TESTING.md, docs/NEW-SOUND-TESTING.md, docs/HAND-TUNED-ROUND-TESTING.md, docs/MUSIC-PLAYER-TESTING.md, docs/BEAUTIFY-PATH-TESTING.md, docs/OPTIMIZE-PATH-TESTING.md, docs/RAIDER-GATES-TESTING.md and
docs/STUCK-RAIDERS-TESTING.md for the checks that were actually run (screenshots of rounds before 0.0.22 are in the repository's
docs/validation, not in this ZIP) and what was not tested (nothing was listened to: the test
machine has no sound device; the game's audio output was recorded and analysed instead).
JAR versions remain 0.21.0 / 1.1.0; use SHA512SUMS to identify this build.
Branch: dev. This is not a public release.
''').encode()
files['THIRD-PARTY.md']=b'''# Included required addon

TaCZ Attributes 1.4 by leopoko, mod ID `tacz_attributes`, MIT.
The unmodified published JAR is supplied separately in each edition folder;
it is not shaded into any Arsenal JAR. Install only one copy.

Author download: https://www.curseforge.com/minecraft/mc-mods/tacz-attributes/files/8470731
Source: https://github.com/leopoko/TaCZ_Attributes/tree/91612b5b505a87e2ddcd48b1b1ae1f9c95f482a1
License evidence: source README and published META-INF/mods.toml declare MIT.
The published JAR contains no standalone LICENSE/NOTICE file; its metadata is
preserved unchanged. Standard MIT terms accompany this attribution in licenses/.

TaCZ and optional LesRaisins are not redistributed in this archive.
Arsenal display artwork remains All Rights Reserved by its creator.
All the music is by leonpentastorm, made with Suno, supplied by the project owner: the ten raid songs (Barren Gap, Line Holder,
Phase One Assault, Silent Trigger, Acid Redeemer, Assault Loop, Breach Core, Rolling Wave, Boss Battle, Anomaly Protocol) and the
Victory Fanfare; All Rights Reserved by their creator. The project owner's sound effects (0.0.18: cannon turning, cannon turning
done, cannon fire, cluster strike, Bunker Buster, shockwave, beacon attacked; 0.0.21 cuts the alarm to its first 0.6 s) and flare radio lines (0.0.19, six fire support; 0.0.22, two supply and three recall), the Command Table and Satellite Beacon models and textures (0.0.20), and the FOB station model and texture (0.0.23) are treated the same way.
The napalm, frost, gravity-hum and starshell sounds and the five Command Table sounds (0.0.20) are original to this project
(tools/audio).
'''
files['licenses/tacz-attributes-MIT.txt']=b'''TaCZ Attributes by leopoko - MIT License
Attribution supplied from the upstream project; no copyright year is asserted.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
'''
shared={n:d for n,d in files.items() if not n.startswith(('pack/','standalone/'))}
for edition in ('pack','standalone'):
    part={n:d for n,d in files.items() if n.startswith(edition+'/')}
    part.update(shared)
    part['README.txt']=part['README.txt'].replace(b'EDITION',edition.encode())
    part['SHA512SUMS']=(''.join(f'{hashlib.sha512(data).hexdigest()}  {name}\n' for name,data in sorted(part.items()))).encode()
    out=repo/('dist/Arsenal-Dev-'+project_version+'-'+edition+'-test.zip');out.parent.mkdir(exist_ok=True)
    with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED) as archive:
        for name,data in sorted(part.items()):archive.writestr(name,data)
    with zipfile.ZipFile(out) as archive:
        assert archive.testzip() is None
        assert sum(name.endswith('.jar') for name in archive.namelist())==4
        for name,data in part.items():assert archive.read(name)==data
    size=out.stat().st_size;assert size<30*1024*1024,f'{out.name} is {size} bytes'
    digest=hashlib.sha512(out.read_bytes()).hexdigest()
    out.with_suffix(out.suffix+'.sha512').write_text(digest+'  '+out.name+'\n')
    print(out,round(size/2**20,1),'MiB','SHA512',digest)
