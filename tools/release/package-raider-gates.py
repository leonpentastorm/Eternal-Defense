#!/usr/bin/env python3
"""Bundle both built editions with the required, unmodified backend JAR (project 0.0.14: Raider Gates, which replace the silent stuck-raider rescue of 0.0.13; stew replace prompt, lit Cook Pot board, 3/5/7/9 servings)."""
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
for name in ['RAIDER-GATES-TESTING.md','STUCK-RAIDERS-TESTING.md','MESS-HALL-TIERS.md','TIERS-AND-TABLES-TESTING.md','GUIDE-STYLE.md','GAME-DESIGN-DOCUMENT.md','CHANGELOG.md','HANDOFF.md']:
    files['docs/'+name]=(repo/'docs'/name).read_bytes()
for folder in ('raider-gates','stuck-raiders'):
    for evidence in (repo/'docs/validation'/folder).glob('*'):
        if evidence.is_file():files[f'docs/validation/{folder}/'+evidence.name]=evidence.read_bytes()
files['README.txt']=('''Eternal Defense - Raider Gates - project '''+project_version+''' - local test build
Minecraft 1.20.1 / Forge 47.4.20 / Java 17. Network protocol 28 (unchanged from the 0.0.11 test build; no packet was
added). Install this build on the client AND the server: it adds one entity type (the gate).

Start a FRESH world for this feature. Keep existing worlds separate from this test instance.
Choose ONE folder: pack for the matching Create/KubeJS pack, or standalone.
Install all FOUR JARs from that folder on both client and server, replacing old
versions. Never mix pack and standalone editions or keep duplicate mod IDs.
TaCZ Attributes 1.4 is a required backend and is INCLUDED separately (unchanged since v4).
Also install TaCZ 1.1.8-hotfix2 (existing dependency, not included):
https://www.curseforge.com/minecraft/mc-mods/timeless-and-classics-zero/files/9037989
The pack folder requires the matching full pack's Create/KubeJS and scripts.
This ZIP is an update/test bundle, not a complete modpack.

WHAT IS IN IT
This is the latest build. Raider Gates (0.0.14) are the upgraded version of the stuck-raider rescue of 0.0.13, so
both are in here: the 0.0.13 progress timer, busy rules, ladder and spawn checks, with the silent teleport replaced
by a visible gate. See docs/GAME-DESIGN-DOCUMENT.md section 5.2 and docs/CHANGELOG.md.

* Raider Gates. A ground raider that walks up to lava or magma, a drop of 6 or more blocks or 8 or more blocks of
  water in a row (a short pond is waded), or that makes no progress for 25 seconds (and is not fighting, shooting or
  digging), stops and channels for 8 seconds beside a red gate (the Return portal sprite, tinted red). You can shoot the
  channeler: every second it is hurt adds 3 seconds, 10 at most; killing it cancels the gate. About 3 seconds before it
  reappears the exit shows portal particles and a sound. It comes out at the edge of the staging ring, at least 24 blocks
  from every player, and marches again. Raiders stuck in one place share one gate (8 channel, the rest wait). After
  three gates a raider is withdrawn (one chat line per wave); a raid boss is gated again and never withdrawn.
  Raiders still march straight: their pathfinding is unchanged. Flyers, gliders, paratroopers under a parachute and
  Special Forces soldiers are not covered.
* Mess Hall: cooking a stew over the stew still in a pot now asks first (Replace stew / Keep it / Escape). Stew
  servings per pot are now 3 / 5 / 7 / 9 for Mk I to Mk IV (the Mk IV value, 9, was asked for; the rest of the ladder
  was scaled to match and is one line in MealRules.tier). The Cook Pot's lamp bar gives light and its board text is
  drawn bright, so you can read the menu in the dark (from the front side).
* Field Guide: the Raids card has one line about the red gate; the Mess Hall and Cook Pot cards quote the new servings
  and the replace question.

HOW TO LOOK AT IT
* Start a raid near lava, a cliff edge or a wide lake and watch the raiders that stop at it (they will not walk in).
  Add -Darsenal.stuckLog=true to the game's JVM arguments and the log gets one "[stuck] event=..." line for every
  trigger, channel, teleport, cancel and withdrawal, with the terrain around the raider.
* -Darsenal.stuckTests=true registers the opt-in GameTests and the /stuck-raider-test command (permission level 2).
  They force-load and build a large stone platform far from spawn (chunk x 750): use a THROWAWAY world only.

LesRaisins Tactical Equipements 0.4.3 is OPTIONAL and not included.
Apocalypse gunpack and AppleSkin were not used in this round and are not redistributed.

See docs/RAIDER-GATES-TESTING.md for the checks that were actually run and what was not. Not tested, among others: a
real raid on real terrain, the red particle ring and the sounds (never seen or heard), a dedicated server, two players
near a destination, the standalone kitchen after this change, the legacy v2 to v6 GameTests.
JAR versions remain 0.21.0 / 1.1.0; use SHA512SUMS to identify this build.
Feature branch: feature/raider-gates (based on feature/stuck-raiders). This is not a public release.
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
files['SHA512SUMS']=(''.join(f'{hashlib.sha512(data).hexdigest()}  {name}\n' for name,data in sorted(files.items()))).encode()
out=repo/('dist/Arsenal-RaiderGates-'+project_version+'-test.zip');out.parent.mkdir(exist_ok=True)
with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED) as archive:
    for name,data in sorted(files.items()):archive.writestr(name,data)
with zipfile.ZipFile(out) as archive:
    assert archive.testzip() is None
    assert sum(name.endswith('.jar') for name in archive.namelist())==8
    for name,data in files.items():assert archive.read(name)==data
print(out)
digest=hashlib.sha512(out.read_bytes()).hexdigest()
out.with_suffix(out.suffix+'.sha512').write_text(digest+'  '+out.name+'\n')
print('SHA512 '+digest)
