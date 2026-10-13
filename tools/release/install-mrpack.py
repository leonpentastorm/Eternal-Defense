#!/usr/bin/env python3
"""Installs a Modrinth modpack (.mrpack) into a folder: a server, a client instance or a test copy (0.0.41).

  python3 -I tools/release/install-mrpack.py dist/Create-Arsenal-0.0.41-test.mrpack /path/to/server --side server

Every file the pack lists is downloaded from its own URL and checked against the pack's SHA-1 and SHA-512 before it is
written; files the pack marks unsupported on that side are skipped; then the overrides (and server- or client-overrides)
are copied over. Nothing in the pack is executed. Paths that would leave the target folder are refused. Downloads are
kept in --cache (by SHA-1), so a second install is offline. Prints the Minecraft and Forge versions the pack needs.
"""
import argparse, hashlib, json, shutil, sys, urllib.request, zipfile
from pathlib import Path, PurePosixPath


def safe(target: Path, relative: str) -> Path:
    p = PurePosixPath(relative)
    if p.is_absolute() or '..' in p.parts or (p.parts and ':' in p.parts[0]):
        raise SystemExit(f'Refusing a path outside the target: {relative}')
    return target.joinpath(*p.parts)


def fetch(url: str, sha1: str, sha512: str, cache: Path) -> Path:
    cached = cache / sha1
    if cached.exists() and hashlib.sha1(cached.read_bytes()).hexdigest() == sha1:
        return cached
    request = urllib.request.Request(url, headers={'User-Agent': 'eternal-defense-testpack/0.0.41'})
    with urllib.request.urlopen(request, timeout=120) as response:
        data = response.read()
    if hashlib.sha1(data).hexdigest() != sha1 or hashlib.sha512(data).hexdigest() != sha512:
        raise SystemExit(f'Checksum mismatch for {url}')
    cache.mkdir(parents=True, exist_ok=True)
    cached.write_bytes(data)
    return cached


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument('pack', type=Path)
    parser.add_argument('target', type=Path)
    parser.add_argument('--side', choices=('server', 'client'), default='client')
    parser.add_argument('--cache', type=Path, default=Path.home() / '.cache' / 'mrpack-files')
    args = parser.parse_args()
    target = args.target.resolve()
    target.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(args.pack) as pack:
        index = json.loads(pack.read('modrinth.index.json'))
        skipped = []
        for entry in index['files']:
            if entry.get('env', {}).get(args.side) == 'unsupported':
                skipped.append(entry['path'])
                continue
            source = None
            for url in entry['downloads']:
                try:
                    source = fetch(url, entry['hashes']['sha1'], entry['hashes']['sha512'], args.cache)
                    break
                except (OSError, ValueError) as error:
                    print(f'  {url}: {error}', file=sys.stderr)
            if source is None:
                raise SystemExit(f'Could not download {entry["path"]}')
            destination = safe(target, entry['path'])
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source, destination)
        copied = 0
        for folder in ('overrides/', f'{args.side}-overrides/'):
            for info in pack.infolist():
                if info.is_dir() or not info.filename.startswith(folder):
                    continue
                destination = safe(target, info.filename[len(folder):])
                destination.parent.mkdir(parents=True, exist_ok=True)
                destination.write_bytes(pack.read(info))
                copied += 1
    print(f'{index.get("name")} {index.get("versionId")}: {len(index["files"]) - len(skipped)} downloaded, '
          f'{len(skipped)} skipped on the {args.side} side, {copied} override files')
    print('needs', json.dumps(index.get('dependencies', {})))


if __name__ == '__main__':
    main()
