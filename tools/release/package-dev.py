#!/usr/bin/env python3
"""Test bundles of the dev branch (project 0.0.26: the Return Flare needs the cannon, ammo parts on the platform; 0.0.25 FOB slice 3, SOS and repairs; 0.0.24 FOB slice 2, the supply rail; 0.0.23 FOB slice 1; 0.0.22 the supply and recall radio lines, ammo parts, a hunt's minimum pay; 0.0.21 armed raid skeletons, night raids, the one-block arrival, iron at the Exchange; 0.0.20 Tactical Operations and the owner's raid list; 0.0.19 the owner's music and radio chatter; 0.0.18 hand-tuned sounds and fire support rework; the merged 0.0.13 to 0.0.17), one ZIP per edition
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
for name in ['STANDALONE.md','PACK-EDITION.md','ROUND-0026-TESTING.md','FOB-SLICE-3-TESTING.md','FOB-SLICE-2-TESTING.md','FOB-SLICE-1-TESTING.md','ROUND-0022-TESTING.md','RAID-ROUND-0021-TESTING.md','TACTICAL-OPERATIONS-TESTING.md','NEW-SOUND-TESTING.md','HAND-TUNED-ROUND-TESTING.md','MUSIC-PLAYER-TESTING.md','BEAUTIFY-PATH-TESTING.md','OPTIMIZE-PATH.md','OPTIMIZE-PATH-TESTING.md','RAIDER-GATES-TESTING.md','STUCK-RAIDERS-TESTING.md','MESS-HALL-TIERS.md','TIERS-AND-TABLES-TESTING.md','GUIDE-STYLE.md','GAME-DESIGN-DOCUMENT.md','CHANGELOG.md','HANDOFF.md']:
    files['docs/'+name]=(repo/'docs'/name).read_bytes()
# screenshots of the two latest rounds only (each ZIP must stay under 30 MB); the older rounds' text evidence, their screenshots are in the repository
for folder in ('fob-slice-3','fob-slice-2','fob-slice-1','round-0022','raid-round-0021','tactical-operations','new-sound','hand-tuned-round','music-player','optimize-path','raider-gates','stuck-raiders'):
    for evidence in (repo/'docs/validation'/folder).glob('*'):
        if evidence.is_file() and (folder in ('fob-slice-3','fob-slice-2','fob-slice-1') or evidence.suffix=='.txt'):files[f'docs/validation/{folder}/'+evidence.name]=evidence.read_bytes()
files['README.txt']=('''Eternal Defense - dev branch with every new feature - project '''+project_version+''' - local test build
Minecraft 1.20.1 / Forge 47.4.20 / Java 17. NETWORK PROTOCOL 33 (32 in 0.0.24, 31 in 0.0.23, 30 in 0.0.21 and 0.0.22): the FOB
screens, the supply gate screen and the status card ride on existing packets that now carry more, so an older client cannot join
this server or the other way round. Install this build on the client AND the server.

Start a FRESH world. Keep existing worlds separate from this test instance.
This ZIP holds ONE edition (EDITION): install all FOUR JARs of its folder on both client and server, replacing old
versions. Never mix pack and standalone editions or keep duplicate mod IDs.
TaCZ Attributes 1.4 is a required backend and is INCLUDED separately (unchanged since v4).
Also install TaCZ 1.1.8-hotfix2 (existing dependency, not included):
https://www.curseforge.com/minecraft/mc-mods/timeless-and-classics-zero/files/9037989
The pack edition requires the matching full pack's Create/KubeJS and scripts.
This ZIP is an update/test bundle, not a complete modpack.

WHAT IS NEW IN 0.0.26: YOUR TWO FIXES
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

See docs/FOB-SLICE-1-TESTING.md, docs/ROUND-0022-TESTING.md, docs/RAID-ROUND-0021-TESTING.md, docs/TACTICAL-OPERATIONS-TESTING.md, docs/NEW-SOUND-TESTING.md, docs/HAND-TUNED-ROUND-TESTING.md, docs/MUSIC-PLAYER-TESTING.md, docs/BEAUTIFY-PATH-TESTING.md, docs/OPTIMIZE-PATH-TESTING.md, docs/RAIDER-GATES-TESTING.md and
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
