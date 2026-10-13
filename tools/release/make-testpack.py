#!/usr/bin/env python3
"""Builds the test pack (0.0.41): the owner's Create Arsenal modpack with this build's mods and pack scripts in it.

  python3 -I tools/release/make-testpack.py --base <the owner's exported Create-Arsenal .mrpack>

The base pack is the owner's own Modrinth App profile, exported as a .mrpack: every mod the pack edition needs is in it,
listed by its author's download link (so the launcher fetches each one from its author's host and checks its hash) or
carried in its overrides. This script only reads it (nothing in it is executed) and writes a new .mrpack that
  * drops the old Create Arsenal jars (arsenal_beacon, arsenal_gun_guide, arsenal_displays), wherever the base had them;
  * adds this build's three jars (custom-mods/*/build/libs, run `build releaseJars` first) to overrides/mods;
  * copies the repository's pack scripts (pack-integration/kubejs) over the pack's own;
  * checks that every mod the pack edition requires is listed (see REQUIRED), and fails if one is missing;
  * keeps everything else of the base exactly as it was.
Writes dist/Create-Arsenal-<project version>-test.mrpack and its .sha512, and prints the mod list.
"""
import argparse, hashlib, io, json, re, zipfile
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
OURS = [('arsenal-beacon', '0.21.0'), ('arsenal-gun-guide', '0.21.0'), ('arsenal-displays', '1.1.0')]
OUR_IDS = ('arsenal_beacon', 'arsenal_gun_guide', 'arsenal_displays')
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
    out = args.out or REPO / 'dist' / f'Create-Arsenal-{version}-test.mrpack'
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
    added = []
    for mod, jar_version in OURS:
        jar = REPO / 'custom-mods' / mod / 'build' / 'libs' / f'{mod}-{jar_version}.jar'
        entries[f'overrides/mods/{jar.name}'] = jar.read_bytes(); added.append(jar.name)
    scripts = []
    for path in sorted((REPO / 'pack-integration' / 'kubejs').rglob('*')):
        if path.is_file():
            name = 'overrides/kubejs/' + path.relative_to(REPO / 'pack-integration' / 'kubejs').as_posix()
            if entries.get(name) != path.read_bytes():
                scripts.append(name)
            entries[name] = path.read_bytes()
    listed = [f['path'] for f in index['files']] + [n for n in entries if '/mods/' in n]
    missing = [label for label, pattern in REQUIRED.items() if not any(re.search(pattern, p, re.I) for p in listed)]
    if missing:
        raise SystemExit('The base pack lacks required mods: ' + ', '.join(missing))
    index['versionId'] = f"{index.get('versionId', '')}+ed{version}".lstrip('+')
    index['summary'] = f'Create Arsenal test pack with Eternal Defense {version} (pack edition)'
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
    print('pack scripts replaced or added', len(scripts), scripts[:12])
    print('mods listed', len(index['files']), '+ in overrides', sum(1 for n in entries if re.match(r'(client-|server-)?overrides/mods/', n)))


if __name__ == '__main__':
    main()
