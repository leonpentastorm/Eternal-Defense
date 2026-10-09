#!/usr/bin/env python3
"""Bundle both built editions with the newly required, unmodified backend JAR (project 0.0.9: Field Guide pass)."""
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
for name in ['FIELD-GUIDE-TESTING.md','GUIDE-STYLE.md','MESS-HALL-V7-UI.md','MESS-HALL-V7-TESTING.md','MESS-HALL-V6-IMPLEMENTATION.md','RAID-ADAPTATION.md']:
    files['docs/'+name]=(repo/'docs'/name).read_bytes()
for folder in ('field-guide','messhall-v7'):
    for evidence in (repo/'docs/validation'/folder).glob('*'):
        if evidence.is_file():files[f'docs/validation/{folder}/'+evidence.name]=evidence.read_bytes()
files['README.txt']=('''Eternal Defense - Field Guide for new players - project '''+project_version+''' - local test build
Minecraft 1.20.1 / Forge 47.4.20 / Java 17. Network protocol 27 (unchanged).

Start a FRESH world for this feature. Keep existing worlds separate from this test instance.
Choose ONE folder: pack for the matching Create/KubeJS pack, or standalone.
Install all FOUR JARs from that folder on both client and server, replacing old
versions. Never mix pack and standalone editions or keep duplicate mod IDs.
TaCZ Attributes 1.4 is a required backend and is INCLUDED separately (unchanged since v4).
Also install TaCZ 1.1.8-hotfix2 (existing dependency, not included):
https://www.curseforge.com/minecraft/mc-mods/timeless-and-classics-zero/files/9037989
The pack folder requires the matching full pack's Create/KubeJS and scripts.
This ZIP is an update/test bundle, not a complete modpack.

WHAT CHANGED (see docs/FIELD-GUIDE-TESTING.md and docs/GUIDE-STYLE.md)
* The Field Guide is rewritten for somebody who has just spawned in. Every feature is a card:
  what it is, what you get, how it works, how to unlock it, and red Careful notes for the catches
  (for example: the beacon only comes off with the Recovery Shovel and that resets the whole campaign).
* The Field Guide's side menu is back in every window size, with an item icon for each page
  (a list with titles when there is room, a two-column icon rail with hover names when there is not).
* New page "Extra gun mods" for Gun Guide and Gun Displays; many features that had no explanation now do.
* The plant confirmation and the Defense Beacon tooltip warn about the reset. The standalone upgrade
  hint now says Ardent Energy (it used to say "craft it").
* Exact numbers stay in the Reference page. Rules, numbers, recipes and the economy are unchanged.
Everything else is the Mess Hall v6 UI pass (0.0.8).

LesRaisins Tactical Equipements 0.4.3 is OPTIONAL and not included.
Apocalypse gunpack and AppleSkin were not used in this round and are not redistributed.

See docs/FIELD-GUIDE-TESTING.md for the checks that were actually run and what was not.
JAR versions remain 0.21.0 / 1.1.0; use SHA512SUMS to identify this build.
Feature branch: feature/messhall-ver-6. This is not a public release.
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
out=repo/('dist/Arsenal-FieldGuide-'+project_version+'-test.zip');out.parent.mkdir(exist_ok=True)
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
