#!/usr/bin/env python3
"""The update bundle of a dev round (0.0.41 on: the pack edition only, the standalone edition is discontinued).

  python3 -I tools/release/package-dev.py --base <the owner's exported .mrpack> [--backend-jar tacz-attributes-1.4.jar]

Writes dist/Eternal-Defense-Dev-<project version>-pack-update.zip and its .sha512: this build's three jars, the repository's KubeJS scripts,
the round's docs and text evidence, a README with what changed and how to install it, and REQUIRED-MODS.txt: every mod the pack
edition requires, with the version the owner's pack carries, its author's download link (Modrinth) and SHA-512, read from the base
pack's index (nothing in the base is executed). The mods that became required in 0.0.41 (NEW_REQUIRED: JEI, KubeJS Create,
LesRaisins Tactical Equipments) are included unchanged in required-mods/ with their license notices and source links, taken from
--cache (install-mrpack.py's download cache, files named by SHA-1) and checked against the pack's SHA-512; with --backend-jar TaCZ
Attributes 1.4 is included unchanged too. The others are listed only (unchanged since earlier rounds). The full test pack is made by
make-testpack.py (too large for the repository).
"""
import argparse, hashlib, io, json, re, zipfile
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
OURS = [('eternal-defense', '0.21.0'), ('eternal-defense-gun-guide', '0.21.0'), ('eternal-defense-displays', '1.1.0')]
REQUIRED = [  # label, mod id, pattern of the jar's file name in the pack
    ('TaCZ', 'tacz', r'(^|/)tacz-1\.20\.1-1\.1\.'),
    ('TaCZ Attributes', 'tacz_attributes', r'(^|/)tacz[-_]attributes'),
    ('Create', 'create', r'(^|/)create-1\.20\.1-6\.'),
    ('KubeJS', 'kubejs', r'(^|/)kubejs-forge-'),
    ('Rhino (for KubeJS)', 'rhino', r'(^|/)rhino-forge-'),
    ('Architectury API (for KubeJS)', 'architectury', r'(^|/)architectury-.*forge'),
    ('KubeJS Create', 'kubejs_create', r'(^|/)kubejs-create-'),
    ('LesRaisins Tactical Equipments', 'lrtactical', r'(^|/)lrtactical'),
    ('JEI (client)', 'jei', r'(^|/)jei-1\.20\.1-forge-'),
]
# Required since 0.0.41: bundled unchanged (all three licenses allow it), with the notice and the source the license asks for.
NEW_REQUIRED = {
    'jei': ('MIT', 'https://github.com/mezz/JustEnoughItems', 'https://modrinth.com/mod/jei'),
    'kubejs_create': ('LGPL-3.0-only', 'https://github.com/KubeJS-Mods/KubeJS-Create', 'https://modrinth.com/mod/kubejs-create'),
    'lrtactical': ('GPL-3.0-only', 'https://github.com/LesRaisins-Studios/LesRaisins-Tactical-Equipements', 'https://modrinth.com/mod/lr-tactical'),
}


def new_required_jars(base: Path, cache: Path) -> dict:
    out = {}
    with zipfile.ZipFile(base) as pack:
        index = json.loads(pack.read('modrinth.index.json'))
    for label, mod_id, pattern in REQUIRED:
        if mod_id not in NEW_REQUIRED:
            continue
        hit = next(f for f in index['files'] if re.search(pattern, f['path'], re.I))
        data = (cache / hit['hashes']['sha1']).read_bytes()
        if hashlib.sha512(data).hexdigest() != hit['hashes']['sha512']:
            raise SystemExit(f'Checksum mismatch for {label}')
        name = hit['path'].rsplit('/', 1)[1]
        license_id, source, page = NEW_REQUIRED[mod_id]
        out[f'required-mods/{name}'] = data
        out[f'required-mods/{name}.LICENSE-NOTICE.txt'] = (
            f'{label} ({mod_id}), file {name}, included UNCHANGED, as downloaded from {hit["downloads"][0]}\n'
            f'SHA-512 {hit["hashes"]["sha512"]}\nLicense: {license_id}, by its authors (see the jar and the source).\n'
            f'Source code: {source}\nProject page: {page}\n').encode()
    return out


def required_list(base: Path) -> str:
    lines = ['Mods the pack edition requires (0.0.41), as the owner\'s Eternal Defense profile carries them.',
             'Download each from its author\'s page (the link below); the SHA-512 lets you check the file.', '']
    with zipfile.ZipFile(base) as pack:
        index = json.loads(pack.read('modrinth.index.json'))
        for label, mod_id, pattern in REQUIRED:
            hit = next((f for f in index['files'] if re.search(pattern, f['path'], re.I)), None)
            if hit:
                lines += [f'{label} ({mod_id}): {hit["path"].rsplit("/", 1)[1]}', f'  {hit["downloads"][0]}', f'  sha512 {hit["hashes"]["sha512"]}', '']
                continue
            override = next((n for n in pack.namelist() if re.search(pattern, n, re.I) and n.endswith('.jar')), None)
            if override is None:
                raise SystemExit(f'The base pack lacks {label}')
            digest = hashlib.sha512(pack.read(override)).hexdigest()
            lines += [f'{label} ({mod_id}): {override.rsplit("/", 1)[1]} (in the pack\'s own files, not on Modrinth)',
                      '  https://www.curseforge.com/minecraft/mc-mods/tacz-attributes/files/8470731' if mod_id == 'tacz_attributes' else '  (see the pack)',
                      f'  sha512 {digest}', '']
    lines.append('Licenses: each mod keeps its own author\'s license; none of these jars is changed or re-hosted here.')
    return '\n'.join(lines) + '\n'


README = '''Eternal Defense - project VERSION - update bundle for the Eternal Defense pack (local test build)
Minecraft 1.20.1 / Forge 47.4.20 / Java 17. NETWORK PROTOCOL 41: install this build on the client AND the server.
Start a FRESH world for this round (until 1.0 every feature round wants one).

THE STANDALONE EDITION IS DISCONTINUED (0.0.41). There is one edition, the pack, and Forge now refuses to start without the mods it
needs: TaCZ, TaCZ Attributes 1.4, Create 6, KubeJS and KubeJS Create, LesRaisins Tactical Equipments 0.4.3+, JEI (client). Your
Eternal Defense profile has all of them; REQUIRED-MODS.txt lists them with their download links and checksums. The three that became
required in 0.0.41 (JEI, KubeJS Create, LesRaisins Tactical Equipments) are also in required-mods/, unchanged, each with its license
notice and source link (MIT, LGPL-3.0, GPL-3.0). SHA512SUMS.txt covers every jar in this bundle.

HOW TO INSTALL INTO YOUR PROFILE (the full test pack, Eternal-Defense-VERSION-test.mrpack, does this for you)
1. In the profile's mods folder delete the old eternal-defense, eternal-defense-gun-guide and eternal-defense-displays jars; copy the three jars of
   mods/ in their place.
2. Copy kubejs/ over the profile's kubejs folder (it replaces our scripts; nothing else of yours is touched).
3. A dedicated server: Tactical Movement 1.0.0 (tacticalmovement-1.0.0.jar) is client-only and stops a server from starting; keep it
   on the clients only (the test pack puts it in client-overrides).

WHAT IS NEW IN 0.0.43 (no packet changed; protocol still 41)
* Exchange: 16 Iron Ore for 2 Ardent Energy (instead of 16 iron ingots for 1: smelt it, or crush it with Create for more), 8 iron
  blocks for 8 (instead of 16 for 8), 16 andesite alloy for 1 (instead of 2). If your world's config/eternal-defense-exchange.txt
  was never edited, it gets the new prices by itself the next time the shop opens; an edited one keeps yours.
* The FOB's mine can send clay, and stones for building: calcite, dripstone, red sandstone, blackstone, and Create's asurine,
  crimsite, ochrum, veridium, limestone, scoria and scorchia. A stone block counts half, so twice as many come as clay.

PLAYTEST SCRIPT FOR 0.0.43 (about 5 minutes)
1. Open the Exchange Shop's Buy tab: Iron Ore 16 for 2, Iron Block 8 for 8, Andesite Alloy 16 for 1. Buy one of each.
2. At a FOB's Receiving Gate, press Choose for the mine: 18 items, clay and the stones among them. Pick clay, then a stone, and
   watch the next deliveries arrive in the gate's storage.

(0.0.42 fixed the corpse crash, made refused placements keep the item with a red preview, kept the return zone clear of falling
blocks and listed magazines with their rounds at the ammo table: docs/CHANGELOG.md.)

(0.0.41 brought the one edition, the Hub crash fix, the MRE rod, the later-Age line, the radio, the plating recipe, blaze rods at 6,
cooking from the Hub, the raid skull, the Hub zone and the installations in the base score: docs/CHANGELOG.md.)

Every round's changes: docs/CHANGELOG.md. What was tested and what was not: docs/ROUND-VERSIONCOMPACT-TESTING.md.
'''


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument('--base', type=Path, required=True, help="the owner's exported .mrpack (read only)")
    parser.add_argument('--backend-jar', type=Path, help='TaCZ Attributes 1.4, included unchanged')
    parser.add_argument('--cache', type=Path, required=True, help="install-mrpack.py's download cache (files named by SHA-1)")
    args = parser.parse_args()
    version = (REPO / 'VERSION').read_text().strip()
    compact = version.replace('.', '')[-4:].rjust(4, '0')
    files = {}
    for mod, jar_version in OURS:
        jar = REPO / 'custom-mods' / mod / 'build' / 'libs' / f'{mod}-{jar_version}.jar'
        files[f'mods/{jar.name}'] = jar.read_bytes()
    if args.backend_jar:
        data = args.backend_jar.read_bytes()
        with zipfile.ZipFile(io.BytesIO(data)) as jar:
            meta = jar.read('META-INF/mods.toml').decode()
        if 'tacz_attributes' not in meta or 'version="1.4"' not in meta.replace(' ', ''):
            raise SystemExit('Expected the published TaCZ Attributes 1.4 jar')
        files['mods/tacz-attributes-1.4.jar'] = data
    for path in sorted((REPO / 'pack-integration' / 'kubejs').rglob('*')):
        if path.is_file():
            files['kubejs/' + path.relative_to(REPO / 'pack-integration' / 'kubejs').as_posix()] = path.read_bytes()
    for name in ['CHANGELOG.md', 'PACK-EDITION.md', f'ROUND-{compact}-TESTING.md']:
        files['docs/' + name] = (REPO / 'docs' / name).read_bytes()
    for evidence in sorted((REPO / 'docs' / 'validation' / f'round-{compact}').glob('*')):
        if evidence.is_file() and evidence.suffix in ('.txt', '.png'):
            files[f'docs/validation/round-{compact}/{evidence.name}'] = evidence.read_bytes()
    files.update(new_required_jars(args.base, args.cache))
    files['REQUIRED-MODS.txt'] = required_list(args.base).encode()
    files['README.txt'] = README.replace('VERSIONCOMPACT', compact).replace('VERSION', version).encode()
    sums = ''.join(f'{hashlib.sha512(data).hexdigest()}  {name}\n' for name, data in sorted(files.items()) if name.endswith('.jar'))
    files['SHA512SUMS.txt'] = sums.encode()
    out = REPO / 'dist' / f'Eternal-Defense-Dev-{version}-pack-update.zip'
    with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as bundle:
        for name in sorted(files):
            bundle.writestr(name, files[name])
    with zipfile.ZipFile(out) as check:
        assert check.testzip() is None
    out.with_suffix('.zip.sha512').write_text(f'{hashlib.sha512(out.read_bytes()).hexdigest()}  {out.name}\n')
    print(out, f'{out.stat().st_size / 2**20:.1f} MiB', len(files), 'files')


if __name__ == '__main__':
    main()
