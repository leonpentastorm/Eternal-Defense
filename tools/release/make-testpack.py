#!/usr/bin/env python3
"""Builds the test pack: the owner's modpack with this build's mods and pack scripts in it.

  python3 -I tools/release/make-testpack.py --base <the owner's exported .mrpack> [--novoice]

The base pack is the owner's own Modrinth App profile, exported as a .mrpack: every mod the pack edition needs is in it,
listed by its author's download link (so the launcher fetches each one from its author's host and checks its hash) or
carried in its overrides. This script only reads it (nothing in it is executed) and writes a new .mrpack that
  * drops earlier jars of our three mods (by mod id, before and after 1.0.0), wherever the base had them;
  * adds this build's three jars (custom-mods/*/build/libs, run `build releaseJars` first) to overrides/mods; with --novoice the
    no-voice edition of the main mod;
  * copies the repository's pack scripts (pack-integration/kubejs) over the pack's own (a copy that differs only in line endings stays);
  * moves the base's own files to the 1.0.0 ids (see LEGACY): pack scripts and configs with the old names are dropped or renamed,
    item ids, class names and titles in its text files are rewritten, and the exchange file is dropped so the mod writes its default;
  * moves the client-only jars the base carries in overrides/mods to client-overrides/mods (see CLIENT_ONLY), so a dedicated
    server installed from the pack starts;
  * checks that every mod the pack edition requires is listed (see REQUIRED), and fails if one is missing;
  * keeps everything else of the base exactly as it was.
Writes dist/Eternal-Defense-<project version>[-novoice]-test.mrpack and its .sha512, and prints the mod list.
"""
import argparse, hashlib, io, json, re, zipfile
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
OURS = [('eternal-defense', '1.0.0'), ('eternal-defense-gun-guide', '1.0.0'), ('eternal-defense-displays', '1.0.0')]
OUR_IDS = ('eternal_defense', 'eternal_defense_gun_guide', 'eternal_defense_displays')
# Mod ids, packages and names used before 1.0.0, for packs exported from earlier builds.
LEGACY_IDS = ('arsenal_beacon', 'arsenal_gun_guide', 'arsenal_displays')
LEGACY = [
    ('dev.createarsenal.', 'dev.eternaldefense.'),
    ('arsenal_beacon', 'eternal_defense'),
    ('arsenal_gun_guide', 'eternal_defense_gun_guide'),
    ('arsenal_displays', 'eternal_defense_displays'),
    ('Create Arsenal: Defense Beacon', 'Eternal Defense'),
    ('Create Arsenal: Gun Guide', 'Eternal Defense: Gun Guide'),
    ('Arsenal Displays', 'Eternal Defense: Displays'),
    ('Create Arsenal', 'Eternal Defense'),
    ('Bosses and the Exotic Arsenal', 'Bosses and Exotic Weapons'),
    ('Brass arsenal', 'Brass armory'),
    ('starts your arsenal.', 'starts your armory.'),
]
LEGACY_FILES = [
    ('overrides/config/arsenal-controls-playtest-1.applied', 'overrides/config/eternal-defense-controls-1.applied'),
    ('overrides/config/arsenal-beacon-', 'overrides/config/eternal-defense-'),
    ('overrides/config/arsenal-controls', 'overrides/config/eternal-defense-controls'),
    ('overrides/config/arsenal-gun-guide-', 'overrides/config/eternal-defense-gun-guide-'),
    ('overrides/config/arsenal_gun_guide-', 'overrides/config/eternal_defense_gun_guide-'),
]
# Third-party names that contain the old word and stay as they are.
THIRD_PARTY = re.compile(r"daffas_?arsenal|marbledsarsenal|Marbled's Arsenal", re.I)
BINARY = ('.jar', '.zip', '.png', '.ogg', '.nbt', '.dat', '.mca', '.mrpack')
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


def legacy(entries: dict, index: dict) -> list:
    """Moves the base's own files to the 1.0.0 ids; returns what changed."""
    changed = []
    repo_kubejs = REPO / 'pack-integration' / 'kubejs'
    for name in list(entries):
        if name.startswith('overrides/kubejs/') and 'arsenal' in name.rsplit('/', 1)[1]:
            renamed = name.replace('arsenal', 'eternal_defense')
            if (repo_kubejs / renamed[len('overrides/kubejs/'):]).exists():
                del entries[name]; changed.append(f'dropped {name}')
        elif name == 'overrides/config/arsenal-beacon-exchange.txt':
            del entries[name]; changed.append(f'dropped {name} (the mod writes the 1.0.0 offers)')
    for name in list(entries):
        for old, new in LEGACY_FILES:
            if name.startswith(old):
                entries[name.replace(old, new, 1)] = entries.pop(name); changed.append(f'renamed {name}')
                break
    for name in list(entries):
        if not name.startswith(('overrides/', 'client-overrides/', 'server-overrides/')) or name.endswith(BINARY):
            continue
        if '/tacz/' in name:
            continue
        try:
            text = entries[name].decode('utf-8')
        except UnicodeDecodeError:
            continue
        new = text
        for old, replacement in LEGACY:
            new = new.replace(old, replacement)
        if new != text:
            entries[name] = new.encode('utf-8'); changed.append(f'rewrote {name}')
    if index.get('name') == 'Create Arsenal':
        index['name'] = 'Eternal Defense'
    return changed


def leftovers(entries: dict, index: dict) -> list:
    found = []
    for name, data in [('modrinth.index.json', json.dumps(index).encode())] + sorted(entries.items()):
        if '/tacz/' in name or name.endswith(BINARY):
            continue
        try:
            text = THIRD_PARTY.sub('', data.decode('utf-8'))
        except UnicodeDecodeError:
            continue
        if re.search(r'arsenal', text, re.I) or re.search(r'arsenal', THIRD_PARTY.sub('', name), re.I):
            found.append(name)
    return found


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument('--base', type=Path, required=True, help="the owner's exported .mrpack")
    parser.add_argument('--novoice', action='store_true', help='use the no-voice edition of the main mod')
    parser.add_argument('--out', type=Path)
    args = parser.parse_args()
    version = (REPO / 'VERSION').read_text().strip()
    edition = '-novoice' if args.novoice else ''
    out = args.out or REPO / 'dist' / f'Eternal-Defense-{version}{edition}-test.mrpack'
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
    # earlier jars of our mods, wherever the base carried them
    index['files'] = [f for f in index['files'] if not re.search(r'(^|/)(arsenal-(beacon|gun-guide|displays)|eternal-defense)', f['path']) or dropped.append(f['path'])]
    for name in [n for n in entries if re.match(r'(client-|server-)?overrides/mods/[^/]+\.jar$', n)]:
        if set(jar_mod_ids(entries[name])) & set(OUR_IDS + LEGACY_IDS):
            dropped.append(name); del entries[name]
    migrated = legacy(entries, index)
    moved = []
    for name in [n for n in entries if re.match(r'overrides/mods/[^/]+\.jar$', n)]:
        if set(jar_mod_ids(entries[name])) & set(CLIENT_ONLY):
            entries['client-' + name] = entries.pop(name); moved.append(name.rsplit('/', 1)[1])
    added = []
    for mod, jar_version in OURS:
        suffix = edition if mod == 'eternal-defense' else ''
        jar = REPO / 'custom-mods' / mod / 'build' / 'libs' / f'{mod}-{jar_version}{suffix}.jar'
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
    index['summary'] = f'Eternal Defense {version} test pack' + (' (no-voice edition)' if args.novoice else '')
    left = leftovers(entries, index)
    if left:
        raise SystemExit('Old names left in: ' + ', '.join(left))
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
    print('moved to the 1.0.0 ids', len(migrated)); [print('  ' + line) for line in migrated]
    print('mods listed', len(index['files']), '+ in overrides', sum(1 for n in entries if re.match(r'(client-|server-)?overrides/mods/', n)))


if __name__ == '__main__':
    main()
