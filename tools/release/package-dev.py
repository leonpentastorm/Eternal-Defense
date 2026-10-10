#!/usr/bin/env python3
"""Test bundles of the dev branch (project 0.0.19: the owner's music and radio chatter; 0.0.18 hand-tuned sounds and fire support rework; the merged 0.0.13 to 0.0.17), one ZIP per edition
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
for name in ['STANDALONE.md','PACK-EDITION.md','NEW-SOUND-TESTING.md','HAND-TUNED-ROUND-TESTING.md','MUSIC-PLAYER-TESTING.md','BEAUTIFY-PATH-TESTING.md','OPTIMIZE-PATH.md','OPTIMIZE-PATH-TESTING.md','RAIDER-GATES-TESTING.md','STUCK-RAIDERS-TESTING.md','MESS-HALL-TIERS.md','TIERS-AND-TABLES-TESTING.md','GUIDE-STYLE.md','GAME-DESIGN-DOCUMENT.md','CHANGELOG.md','HANDOFF.md']:
    files['docs/'+name]=(repo/'docs'/name).read_bytes()
for folder in ('new-sound','hand-tuned-round','music-player','optimize-path','raider-gates','stuck-raiders'):
    for evidence in (repo/'docs/validation'/folder).glob('*'):
        if evidence.is_file():files[f'docs/validation/{folder}/'+evidence.name]=evidence.read_bytes()
files['README.txt']=('''Eternal Defense - dev branch with every new feature - project '''+project_version+''' - local test build
Minecraft 1.20.1 / Forge 47.4.20 / Java 17. Network protocol 28 (unchanged since the 0.0.11 test build). Install this build on
the client AND the server: new sounds and entities are registered, so a client and a server of different builds refuse each other.

Start a FRESH world. Keep existing worlds separate from this test instance.
This ZIP holds ONE edition (EDITION): install all FOUR JARs of its folder on both client and server, replacing old
versions. Never mix pack and standalone editions or keep duplicate mod IDs.
TaCZ Attributes 1.4 is a required backend and is INCLUDED separately (unchanged since v4).
Also install TaCZ 1.1.8-hotfix2 (existing dependency, not included):
https://www.curseforge.com/minecraft/mc-mods/timeless-and-classics-zero/files/9037989
The pack edition requires the matching full pack's Create/KubeJS and scripts.
This ZIP is an update/test bundle, not a complete modpack.

WHAT IS NEW IN 0.0.19 (since the 0.0.18 bundle)
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
Play a raid through and listen: a different song every wave, the fanfare at the end. Throw a few fire flares in a row and listen
to the radio; throw a supply flare. Watch a barrage come down: the whistle should end on each impact.

LesRaisins Tactical Equipements 0.4.3 is OPTIONAL and not included.
Apocalypse gunpack and AppleSkin were not used in these rounds and are not redistributed.

See docs/NEW-SOUND-TESTING.md, docs/HAND-TUNED-ROUND-TESTING.md, docs/MUSIC-PLAYER-TESTING.md, docs/BEAUTIFY-PATH-TESTING.md, docs/OPTIMIZE-PATH-TESTING.md, docs/RAIDER-GATES-TESTING.md and
docs/STUCK-RAIDERS-TESTING.md for the checks that were actually run and what was not tested (nothing was listened to: the test
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
done, cannon fire, cluster strike, Bunker Buster, shockwave, beacon attacked) and fire support radio lines (0.0.19, six) are treated
the same way. The shell and bomb whistles and the napalm, frost, gravity-hum and starshell sounds are original to this project
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
