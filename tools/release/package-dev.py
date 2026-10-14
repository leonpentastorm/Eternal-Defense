#!/usr/bin/env python3
"""The release bundle: one zip per build of the main mod (voiced, and with --novoice the no-voice build).

  python3 -I tools/release/package-dev.py --base <the owner's exported .mrpack> [--novoice]

Writes dist/Eternal-Defense-<project version>[-novoice].zip and its .sha512: this build's three jars, the repository's KubeJS scripts,
the owner's profile files moved to the 1.0.0 ids (make-testpack.py's migration: renamed configs, quests and settings with the new item
ids), DELETE-FROM-YOUR-PROFILE.txt (the old files they replace), REQUIRED-MODS.txt (every mod the pack needs, with the version the
owner's pack carries, its download link and SHA-512, read from the base pack's index; nothing in the base is executed), INSTALL.txt
and SHA512SUMS.txt. No tests, notes or documents go in. The full test pack is made by make-testpack.py.
"""
import argparse, hashlib, importlib.util, json, re, zipfile
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
OURS = [('eternal-defense', '1.0.0'), ('eternal-defense-gun-guide', '1.0.0'), ('eternal-defense-displays', '1.0.0')]
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
def required_list(base: Path) -> str:
    lines = ['Mods Eternal Defense requires, as the Eternal Defense profile carries them.',
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


INSTALL = '''Eternal Defense VERSION EDITION
Minecraft 1.20.1, Forge 47.4.20, Java 17.

mods/                  the three mods: all three on every client; a dedicated server does not need Gun Guide
kubejs/                the pack's scripts for Eternal Defense
profile/               your profile's own files, moved to the new ids (configs, quests, sound and JEI settings)
REQUIRED-MODS.txt      the mods Eternal Defense needs, with download links and checksums
SHA512SUMS.txt         checksums of the jars

The main mod comes in two builds: eternal-defense-VERSION.jar (radio voice lines) and eternal-defense-VERSION-novoice.jar
(game sounds instead of the voice lines). Install one of them, never both.

Install into your profile
1. Delete every file listed in DELETE-FROM-YOUR-PROFILE.txt (the old jars, scripts and configs).
2. Copy mods/ into the profile's mods folder.
3. Copy kubejs/ over the profile's kubejs folder, and the contents of profile/ over the profile folder.
4. Start a new world: worlds made with earlier builds are not converted.
A dedicated server: Tactical Movement (tacticalmovement-1.0.0.jar) only works on clients; keep it off the server.
'''


def migrated_profile(base: Path) -> tuple:
    """The base's own files after make-testpack.py's migration, and the old paths they replace."""
    spec = importlib.util.spec_from_file_location('make_testpack', REPO / 'tools' / 'release' / 'make-testpack.py')
    testpack = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(testpack)
    with zipfile.ZipFile(base) as pack:
        index = json.loads(pack.read('modrinth.index.json'))
        before = {i.filename: pack.read(i) for i in pack.infolist()
                  if i.filename.startswith('overrides/') and not i.is_dir() and '/mods/' not in i.filename and not i.filename.startswith('overrides/tacz/')}
    after = dict(before)
    testpack.legacy(after, index)
    changed = {name[len('overrides/'):]: data for name, data in after.items() if before.get(name) != data and not name.startswith('overrides/kubejs/')}
    removed = sorted(name[len('overrides/'):] for name in before if name not in after)
    for jar in ('arsenal-beacon-*.jar', 'arsenal-gun-guide-*.jar', 'arsenal-displays-*.jar', 'eternal-defense-*.jar'):
        removed.append('mods/' + jar)
    return changed, removed


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument('--base', type=Path, required=True, help="the owner's exported .mrpack (read only)")
    parser.add_argument('--novoice', action='store_true', help='bundle the no-voice build of the main mod')
    args = parser.parse_args()
    version = (REPO / 'VERSION').read_text().strip()
    edition = '-novoice' if args.novoice else ''
    files = {}
    for mod, jar_version in OURS:
        suffix = edition if mod == 'eternal-defense' else ''
        jar = REPO / 'custom-mods' / mod / 'build' / 'libs' / f'{mod}-{jar_version}{suffix}.jar'
        with zipfile.ZipFile(jar) as check:
            names = check.namelist()
        if any(re.search(r'(?i)(gametest|readme|qa_|/qa/|testing|\.md$|\.txt$)', n) and not n.endswith('LICENSE.txt') for n in names):
            raise SystemExit(f'{jar.name} carries test or note files')
        files[f'mods/{jar.name}'] = jar.read_bytes()
    for path in sorted((REPO / 'pack-integration' / 'kubejs').rglob('*')):
        if path.is_file():
            files['kubejs/' + path.relative_to(REPO / 'pack-integration' / 'kubejs').as_posix()] = path.read_bytes()
    changed, removed = migrated_profile(args.base)
    for name, data in changed.items():
        files['profile/' + name] = data
    files['DELETE-FROM-YOUR-PROFILE.txt'] = ('Files of earlier builds to delete from your profile folder (* matches any version):\n'
                                             + ''.join(f'{name}\n' for name in removed)).encode()
    files['REQUIRED-MODS.txt'] = required_list(args.base).encode()
    label = '(no-voice build)' if args.novoice else ''
    files['INSTALL.txt'] = INSTALL.replace('VERSION', version).replace('EDITION', label).replace(' \n', '\n').encode()
    sums = ''.join(f'{hashlib.sha512(data).hexdigest()}  {name}\n' for name, data in sorted(files.items()) if name.endswith('.jar'))
    files['SHA512SUMS.txt'] = sums.encode()
    out = REPO / 'dist' / f'Eternal-Defense-{version}{edition}.zip'
    out.parent.mkdir(exist_ok=True)
    with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as bundle:
        for name in sorted(files):
            bundle.writestr(name, files[name])
    with zipfile.ZipFile(out) as check:
        assert check.testzip() is None
    out.with_suffix('.zip.sha512').write_text(f'{hashlib.sha512(out.read_bytes()).hexdigest()}  {out.name}\n')
    print(out, f'{out.stat().st_size / 2**20:.1f} MiB', len(files), 'files')
    print('profile files', sorted(changed))
    print('delete', removed)


if __name__ == '__main__':
    main()
