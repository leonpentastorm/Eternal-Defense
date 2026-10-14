#!/usr/bin/env python3
"""0.0.38: blockstate, loot table, pickaxe tag and recipes of the MRE Station corner.

The owner's MRE models (models/block/mre_maker, models/item/mre_maker, models/item/mre) and textures (block/mre_maker, item/mre) are
installed exactly as supplied and are never written here. The Marines Fuel icon is drawn by tools/art/marines_fuel_crayon.py. The
language file is edited by hand (it keeps its \\u escapes), never by this script.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / 'custom-mods/eternal-defense/src/main/resources'
ASSETS = RES / 'assets/eternal_defense'
DATA = RES / 'data/eternal_defense'
STANDALONE = ROOT / 'custom-mods/eternal-defense/src/standalone/resources/data/eternal_defense'

def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + '\n')

# the MRE Station is registered as mre_maker, the owner's model name; it faces the player like the other kitchen blocks
name = 'mre_maker'
write(ASSETS / f'blockstates/{name}.json', {'variants': {f'facing={f}': dict(model=f'eternal_defense:block/{name}', y=angle) for f, angle in [('north', 0), ('east', 90), ('south', 180), ('west', 270)]}})
write(DATA / f'loot_tables/blocks/{name}.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': 'eternal_defense:' + name}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
write(ASSETS / 'models/item/marines_fuel.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'eternal_defense:item/marines_fuel'}})

recipes = {
    # a clay ball and any dye make two crayons (the forge:dyes tag holds every dye, modded ones too)
    'marines_fuel': {'type': 'minecraft:crafting_shapeless', 'ingredients': [{'item': 'minecraft:clay_ball'}, {'tag': 'forge:dyes'}], 'result': {'item': 'eternal_defense:marines_fuel', 'count': 2}},
}
for rid, recipe in recipes.items():
    write(DATA / f'recipes/{rid}.json', recipe)
    write(STANDALONE / f'recipes/{rid}.json', recipe)

path = RES / 'data/minecraft/tags/blocks/mineable/pickaxe.json'
value = json.loads(path.read_text())
if 'eternal_defense:' + name not in value['values']:
    value['values'].append('eternal_defense:' + name)
write(path, value)
