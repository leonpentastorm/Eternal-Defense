#!/usr/bin/env python3
"""Builds the test pack (0.0.41): the owner's Eternal Defense modpack with this build's mods and pack scripts in it.

  python3 -I tools/release/make-testpack.py --base <the owner's exported Eternal-Defense .mrpack>

The base pack is the owner's own Modrinth App profile, exported as a .mrpack: every mod the pack edition needs is in it,
listed by its author's download link (so the launcher fetches each one from its author's host and checks its hash) or
carried in its overrides. This script only reads it (nothing in it is executed) and writes a new .mrpack that
  * drops the old Eternal Defense jars (eternal_defense, eternal_defense_gun_guide, eternal_defense_displays), wherever the base had them;
  * adds this build's three jars (custom-mods/*/build/libs, run `build releaseJars` first) to overrides/mods;
  * copies the repository's pack scripts (pack-integration/kubejs) over the pack's own (a copy that differs only in line endings stays);
  * moves the client-only jars the base carries in overrides/mods to client-overrides/mods (see CLIENT_ONLY), so a dedicated
    server installed from the pack starts;
  * checks that every mod the pack edition requires is listed (see REQUIRED), and fails if one is missing;
  * keeps everything else of the base exactly as it was.
Writes dist/Eternal-Defense-<project version>-test.mrpack and its .sha512, and prints the mod list.
"""
import argparse, hashlib, io, json, re, zipfile
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
OURS = [('eternal-defense', '0.21.0'), ('eternal-defense-gun-guide', '0.21.0'), ('eternal-defense-displays', '1.1.0')]
OUR_IDS = ('eternal_defense', 'eternal_defense_gun_guide', 'eternal_defense_displays')
# What the pack edition needs to run, by a pattern its jar's file name matches (checked in files[] paths and overrides).
REQUIRED = {
    'Create': r'(^|/)create-1\.20\.1-6\.',
    'KubeJS': r'(^|/)kubejs-forge-',
    'Rhino (for KubeJS)': r'(^|/)rhino-forge-',
    'Architectury API (for KubeJS)': r'(^|/)architectury-.*forge',
    'TaCZ': r'(^|/)tacz-1\.20\.1-1\.1\.',
    'TaCZ Attributes': r'(^|/)tacz[-_]attributes',
    'JEI': r'(^|/)jei-1\.20\.1-forge-',
    'KubeJS Create': r'(^|/)kubejs-create-',
    'LesRaisins Tactical Equipments (raid rewards)': r'(^|/)lrtactical',
}
# Jars of the base's overrides that only work on a client: a dedicated server refuses to start with them (by mod id, and why).
CLIENT_ONLY = {
    'tacticalmovement': 'Tactical Movement 1.0.0 loads LocalPlayer when it is built: "invalid dist DEDICATED_SERVER" (0.0.41 server boot); '
                        'it has no network channel (camera lean and keybinds only)',
}


def jar_mod_ids(data: bytes) -> list:
    try:
        with zipfile.ZipFile(io.BytesIO(data)) as jar:
            toml = jar.read('META-INF/mods.toml').decode('utf-8', 'replace')
    except (KeyError, zipfile.BadZipFile):
        return []
    return re.findall(r'^\s*modId\s*=\s*"([^"]+)"', toml.split('[[dependencies', 1)[0], re.M)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument('--base', type=Path, required=True, help="the owner's exported .mrpack")
    parser.add_argument('--out', type=Path)
    args = parser.parse_args()
    version = (REPO / 'VERSION').read_text().strip()
    out = args.out or REPO / 'dist' / f'Eternal-Defense-{version}-test.mrpack'
    entries = {}
    with zipfile.ZipFile(args.base) as base:
        index = json.loads(base.read('modrinth.index.json'))
        for info in base.infolist():
            if info.is_dir() or info.filename == 'modrinth.index.json':
                continue
            if any(part == '..' for part in info.filename.split('/')) or info.filename.startswith('/'):
                raise SystemExit(f'Refusing a path outside the pack: {info.filename}')
            entries[info.filename] = base.read(info)
    dropped = []
    # our old jars, wherever the base carried them
    index['files'] = [f for f in index['files'] if not re.search(r'(^|/)arsenal-(beacon|gun-guide|displays)', f['path']) or dropped.append(f['path'])]
    for name in [n for n in entries if re.match(r'(client-|server-)?overrides/mods/[^/]+\.jar$', n)]:
        if set(jar_mod_ids(entries[name])) & set(OUR_IDS):
            dropped.append(name); del entries[name]
    moved = []
    for name in [n for n in entries if re.match(r'overrides/mods/[^/]+\.jar$', n)]:
        if set(jar_mod_ids(entries[name])) & set(CLIENT_ONLY):
            entries['client-' + name] = entries.pop(name); moved.append(name.rsplit('/', 1)[1])
    added = []
    for mod, jar_version in OURS:
        jar = REPO / 'custom-mods' / mod / 'build' / 'libs' / f'{mod}-{jar_version}.jar'
        entries[f'overrides/mods/{jar.name}'] = jar.read_bytes(); added.append(jar.name)
    scripts = []
    for path in sorted((REPO / 'pack-integration' / 'kubejs').rglob('*')):
        if path.is_file():
            name = 'overrides/kubejs/' + path.relative_to(REPO / 'pack-integration' / 'kubejs').as_posix()
            ours, theirs = path.read_bytes(), entries.get(name)
            if theirs is not None and theirs.replace(b'\r\n', b'\n') == ours.replace(b'\r\n', b'\n'):
                continue  # the same script, only saved with other line endings: the base's copy stays
            scripts.append(name)
            entries[name] = ours
    listed = [f['path'] for f in index['files']] + [n for n in entries if '/mods/' in n]
    missing = [label for label, pattern in REQUIRED.items() if not any(re.search(pattern, p, re.I) for p in listed)]
    if missing:
        raise SystemExit('The base pack lacks required mods: ' + ', '.join(missing))
    index['versionId'] = f"{index.get('versionId', '')}+ed{version}".lstrip('+')
    index['summary'] = f'Eternal Defense test pack with Eternal Defense {version} (pack edition)'
    out.parent.mkdir(exist_ok=True)
    with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as pack:
        pack.writestr('modrinth.index.json', json.dumps(index, indent=1))
        for name in sorted(entries):
            pack.writestr(name, entries[name])
    with zipfile.ZipFile(out) as check:
        assert check.testzip() is None
    digest = hashlib.sha512(out.read_bytes()).hexdigest()
    out.with_suffix(out.suffix + '.sha512').write_text(f'{digest}  {out.name}\n')
    print(f'{out} {out.stat().st_size / 2**20:.1f} MiB')
    print('dropped', dropped)
    print('added', added)
    print('moved to client-overrides (client only)', moved)
    print('pack scripts replaced or added', len(scripts), scripts[:12])
    print('mods listed', len(index['files']), '+ in overrides', sum(1 for n in entries if re.match(r'(client-|server-)?overrides/mods/', n)))


if __name__ == '__main__':
    main()
