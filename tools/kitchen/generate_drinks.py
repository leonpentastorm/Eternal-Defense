#!/usr/bin/env python3
"""0.0.35: blockstates, loot tables, recipes and the pickaxe tag of the drinks corner.

The owner's Drinks Set v2 models (models/block/drink_mixer_mk1..4, drink_pot, drink_pot_liquid, cup_dispenser and the item models
drink_mixer*, drink_pot, cup_dispenser, drink_cup, drink_cup_empty) and textures are installed exactly as supplied and are never written
here. The language file is edited by hand (it keeps its \\u escapes), never by this script.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / 'custom-mods/arsenal-beacon/src/main/resources'
ASSETS = RES / 'assets/arsenal_beacon'
DATA = RES / 'data/arsenal_beacon'
STANDALONE = ROOT / 'custom-mods/arsenal-beacon/src/standalone/resources/data/arsenal_beacon'

def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + '\n')

BLOCKS = [f'drink_mixer_mk{i}' for i in range(1, 5)] + ['drink_pot', 'cup_dispenser']
for name in BLOCKS:
    write(ASSETS / f'blockstates/{name}.json', {'variants': {f'facing={f}': dict(model=f'arsenal_beacon:block/{name}', y=angle) for f, angle in [('north', 0), ('east', 90), ('south', 180), ('west', 270)]}})
    write(DATA / f'loot_tables/blocks/{name}.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': 'arsenal_beacon:' + name}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})

recipes = {
    # the station is built around a vanilla brewing stand, over a cauldron, in a plank and iron frame with glass bottles on top
    'drink_mixer_mk1': {'type': 'minecraft:crafting_shaped', 'pattern': ['PGP', 'IBI', 'PCP'], 'key': {'P': {'tag': 'minecraft:planks'}, 'G': {'item': 'minecraft:glass_bottle'}, 'I': {'item': 'minecraft:iron_ingot'}, 'B': {'item': 'minecraft:brewing_stand'}, 'C': {'item': 'minecraft:cauldron'}}, 'result': {'item': 'arsenal_beacon:drink_mixer_mk1'}},
    # the copper kettle: the Cook Pot's recipe in copper
    'drink_pot': {'type': 'minecraft:crafting_shaped', 'pattern': ['CSC', 'CKC', 'CCC'], 'key': {'C': {'item': 'minecraft:copper_ingot'}, 'S': {'item': 'minecraft:oak_sign'}, 'K': {'item': 'minecraft:cauldron'}}, 'result': {'item': 'arsenal_beacon:drink_pot'}},
    # three paper in a bowl's shape make four Paper Cups (three planks make four bowls)
    'drink_cup_empty': {'type': 'minecraft:crafting_shaped', 'pattern': ['P P', ' P '], 'key': {'P': {'item': 'minecraft:paper'}}, 'result': {'item': 'arsenal_beacon:drink_cup_empty', 'count': 4}},
}
for name, recipe in recipes.items():
    write(DATA / f'recipes/{name}.json', recipe)
    write(STANDALONE / f'recipes/{name}.json', recipe)

path = RES / 'data/minecraft/tags/blocks/mineable/pickaxe.json'
value = json.loads(path.read_text())
for name in BLOCKS:
    if 'arsenal_beacon:' + name not in value['values']:
        value['values'].append('arsenal_beacon:' + name)
write(path, value)
