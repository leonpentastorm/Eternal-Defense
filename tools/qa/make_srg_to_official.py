#!/usr/bin/env python3
"""Builds an SRG file (srg names -> official Mojang names) from the ForgeGradle caches, so that mixins of production-built mods (TaCZ)
can be applied in a development run: -Dmixin.env.remapRefMap=true -Dmixin.env.refMapRemappingFile=<this file>.
Usage: make_srg_to_official.py <versions/1.20.1 dir> <output.srg>"""
import re, sys
from pathlib import Path

base = Path(sys.argv[1])
out = Path(sys.argv[2])

# --- official <-> obf (Mojang mappings: "official.Name -> obf:", members indented)
cls_off2obf, cls_obf2off = {}, {}
methods = {}   # official class -> list of (obf name, official name, return, [args])
fields = {}
current = None
for line in (base / 'client_mappings.txt').read_text().splitlines():
    if not line or line.startswith('#'):
        continue
    if not line.startswith(' '):
        m = re.match(r'(\S+) -> (\S+):', line)
        current = m.group(1).replace('.', '/')
        cls_off2obf[current] = m.group(2).replace('.', '/')
        cls_obf2off[m.group(2).replace('.', '/')] = current
        methods[current] = []
        fields[current] = []
        continue
    body = line.strip()
    m = re.match(r'(?:\d+:\d+:)?(\S+) (\S+)\((.*)\)(?::\d+:\d+)? -> (\S+)', body)
    if m:
        ret, name, args, obf = m.groups()
        methods[current].append((obf, name, ret, [a for a in args.split(',') if a]))
        continue
    m = re.match(r'(\S+) (\S+) -> (\S+)', body)
    if m:
        fields[current].append((m.group(3), m.group(2)))

PRIM = {'int': 'I', 'void': 'V', 'boolean': 'Z', 'float': 'F', 'double': 'D', 'long': 'J', 'byte': 'B', 'short': 'S', 'char': 'C'}
def desc(t, to_obf):
    arrays = 0
    while t.endswith('[]'):
        arrays += 1
        t = t[:-2]
    if t in PRIM:
        d = PRIM[t]
    else:
        n = t.replace('.', '/')
        d = 'L' + (cls_off2obf.get(n, n) if to_obf else n) + ';'
    return '[' * arrays + d
def mdesc(ret, args, to_obf):
    return '(' + ''.join(desc(a, to_obf) for a in args) + ')' + desc(ret, to_obf)

# --- obf -> srg (tsrg2: "obf srg id" classes, tab members)
lines = []
srg_class = None
obf_class = None
member = {}   # (official class, obf name, obf desc) -> srg name
fmember = {}
for line in (base / 'mcp_mappings.tsrg').read_text().splitlines():
    if line.startswith('tsrg2') or not line.strip():
        continue
    if not line.startswith('\t'):
        parts = line.split()
        obf_class, srg_class = parts[0], parts[1]
        continue
    if line.startswith('\t\t'):
        continue
    parts = line.strip().split()
    if obf_class not in cls_obf2off:
        continue
    off = cls_obf2off[obf_class]
    if len(parts) >= 3 and parts[1].startswith('('):
        member[(off, parts[0], parts[1])] = parts[2]
    elif len(parts) >= 2:
        fmember[(off, parts[0])] = parts[1]

count = 0
with out.open('w') as f:
    for off, ms in methods.items():
        for obf, name, ret, args in ms:
            srg = member.get((off, obf, mdesc(ret, args, True)))
            if srg and srg != name:
                d = mdesc(ret, args, False)
                f.write(f'MD: {off}/{srg} {d} {off}/{name} {d}\n')
                count += 1
    for off, fs in fields.items():
        for obf, name in fs:
            srg = fmember.get((off, obf))
            if srg and srg != name:
                f.write(f'FD: {off}/{srg} {off}/{name}\n')
                count += 1
print('wrote', count, 'mappings to', out)
