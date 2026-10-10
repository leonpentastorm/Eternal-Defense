#!/usr/bin/env python3
"""Bundle both built editions with the required, unmodified backend JAR (project 0.0.17: the raid music player; it contains everything of 0.0.16 Beautify Path, 0.0.15 Optimize Path and 0.0.14 Raider Gates)."""
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
for name in ['MUSIC-PLAYER-TESTING.md','BEAUTIFY-PATH-TESTING.md','OPTIMIZE-PATH.md','OPTIMIZE-PATH-TESTING.md','RAIDER-GATES-TESTING.md','STUCK-RAIDERS-TESTING.md','MESS-HALL-TIERS.md','TIERS-AND-TABLES-TESTING.md','GUIDE-STYLE.md','GAME-DESIGN-DOCUMENT.md','CHANGELOG.md','HANDOFF.md']:
    files['docs/'+name]=(repo/'docs'/name).read_bytes()
for folder in ('music-player','optimize-path','raider-gates','stuck-raiders'):
    for evidence in (repo/'docs/validation'/folder).glob('*'):
        if evidence.is_file():files[f'docs/validation/{folder}/'+evidence.name]=evidence.read_bytes()
files['README.txt']=('''Eternal Defense - Raid music player - project '''+project_version+''' - local test build
Minecraft 1.20.1 / Forge 47.4.20 / Java 17. Network protocol 28 (unchanged since the 0.0.11 test build; no packet was
added or changed). Install this build on the client AND the server: new sounds and a new entity are registered, so a
client and a server of different builds refuse each other.

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
This is the latest build: 0.0.14 Raider Gates, 0.0.15 Optimize Path, 0.0.16 Beautify Path and 0.0.17 the music player.
* 0.0.17 Raid music player: your six songs (four ordinary, one boss, one special) plus the three original themes of 0.0.16.
  Every wave starts a song and loops it until the next wave, which fades to another one of the same kind: ordinary raids
  shuffle the ordinary songs, special raids the special songs, and the boss wave of a hard raid plays a boss song. The order
  is random but the same for every player of the base. The song's name, a level meter, the time and a progress bar sit
  under the beacon's status card for the whole raid (the card itself still hides away from the beacon). Beacon panel >
  Settings has two switches that only change things for you: Raid music and Music player. Loudness is the game's Music slider.
* 0.0.16 Beautify Path: new cannon, flare and blast sounds; shells and the Bunker Buster bomb fall from the sky with a
  whistle (every round now takes 1.5 s to land); bigger blasts and screen shake; a second red gate where a gated raider
  comes out; six new fire supports (Cluster Strike, Cryo Shell, Napalm Carpet, Gravity Well, Shockwave, Starshell) in a
  scrolling cannon menu where maxed upgrades can still be read.

WHAT TO LOOK FOR
Listen: is the change between waves smooth (a 3-second fade), do the songs loop cleanly, is the volume right next to the
game's own music? Turn Raid music off in Settings during a raid: the song fades and the game's music comes back.
The new client file config/arsenal-beacon-client.toml stores the two switches.

LesRaisins Tactical Equipements 0.4.3 is OPTIONAL and not included.
Apocalypse gunpack and AppleSkin were not used in this round and are not redistributed.

See docs/MUSIC-PLAYER-TESTING.md and docs/BEAUTIFY-PATH-TESTING.md for the checks that were actually run, the screenshots
and what was not tested (nothing was listened to: the test machine has no sound device).
JAR versions remain 0.21.0 / 1.1.0; use SHA512SUMS to identify this build.
Feature branch: feature/music-player (based on feature/beautify-path). This is not a public release.
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
The six raid songs (Barren Gap, Line Holder, Phase One Assault, Silent Trigger, Boss Battle, Anomaly Protocol) are by
leonpentastorm, made with Suno, supplied by the project owner; All Rights Reserved by their creator. The three raid themes
and the victory fanfare of 0.0.16 and every sound effect are original to this project (tools/audio).
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
out=repo/('dist/Arsenal-MusicPlayer-'+project_version+'-test.zip');out.parent.mkdir(exist_ok=True)
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
