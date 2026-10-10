#!/usr/bin/env python3
"""Bundle both built editions with the required, unmodified backend JAR (project 0.0.15: Optimize Path, the same game with less work per tick; it contains everything of 0.0.14 Raider Gates)."""
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
for name in ['OPTIMIZE-PATH.md','OPTIMIZE-PATH-TESTING.md','RAIDER-GATES-TESTING.md','STUCK-RAIDERS-TESTING.md','MESS-HALL-TIERS.md','TIERS-AND-TABLES-TESTING.md','GUIDE-STYLE.md','GAME-DESIGN-DOCUMENT.md','CHANGELOG.md','HANDOFF.md']:
    files['docs/'+name]=(repo/'docs'/name).read_bytes()
for folder in ('optimize-path','raider-gates','stuck-raiders'):
    for evidence in (repo/'docs/validation'/folder).glob('*'):
        if evidence.is_file():files[f'docs/validation/{folder}/'+evidence.name]=evidence.read_bytes()
files['README.txt']=('''Eternal Defense - Optimize Path - project '''+project_version+''' - local test build
Minecraft 1.20.1 / Forge 47.4.20 / Java 17. Network protocol 28 (unchanged since the 0.0.11 test build; no packet was
added or changed). Install this build on the client AND the server.

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
This is the latest build: everything of 0.0.14 (Raider Gates, the stew replace question, the lit Cook Pot board,
3/5/7/9 servings) plus the Optimize Path. The Optimize Path changes NO rule, number, screen or raid behaviour; it makes
the code that runs during play do less work for the same result (see docs/OPTIMIZE-PATH.md):
* the beacon's TaCZ bullet-impact hook listens to TaCZ's own event instead of every event of the game;
* the base score (computed about every 20 seconds between raids and at every raid start) is much cheaper, same score;
* gun-pack resource paths are checked without a regular expression (resource loading);
* the Special Forces corpse sweep is skipped when that optional mod is not installed;
* deaths outside a raid skip an id lookup.

WHAT TO LOOK FOR
Everything should play exactly as 0.0.14. Worth a look: a bullet from a hostile gunner hitting the beacon still damages
it; the Score / building tier shown in the beacon panel is the same as before for the same base; raids start as before.
-Darsenal.optimizeTests=true registers the opt-in /optimize-path-test command (permission level 2). It borrows the
campaign of a world WITHOUT a beacon: use a THROWAWAY world only.

LesRaisins Tactical Equipements 0.4.3 is OPTIONAL and not included.
Apocalypse gunpack and AppleSkin were not used in this round and are not redistributed.

See docs/OPTIMIZE-PATH-TESTING.md for the checks that were actually run, the measured numbers and what was not tested.
JAR versions remain 0.21.0 / 1.1.0; use SHA512SUMS to identify this build.
Feature branch: feature/optimize-path (based on feature/raider-gates). This is not a public release.
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
out=repo/('dist/Arsenal-OptimizePath-'+project_version+'-test.zip');out.parent.mkdir(exist_ok=True)
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
