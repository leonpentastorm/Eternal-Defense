"""Blockstates, loot tables, recipes and tags for the energy and support blocks. The 3D models and textures come from import_support_gear.py."""
import json

def all_files(assets, mod):
    out = []
    def A(path, data): out.append((assets / path, data))
    ys = {'north': 0, 'east': 90, 'south': 180, 'west': 270}
    A('blockstates/exchange_shop.json', {'variants': {f'facing={f}': {'model': 'arsenal_beacon:block/exchange_shop', 'y': y} for f, y in ys.items()}})
    A('blockstates/support_platform.json', {'variants': {f'facing={f},mk={mk}': {'model': f'arsenal_beacon:block/support_platform_mk{mk}', 'y': y} for f, y in ys.items() for mk in range(1, 5)}})
    A('blockstates/support_cannon.json', {'variants': {'': {'model': 'arsenal_beacon:block/support_cannon'}}})
    def loot(name, keep=None):
        entry = {'type': 'minecraft:item', 'name': f'arsenal_beacon:{name}'}
        if keep: entry['functions'] = [{'function': 'minecraft:copy_state', 'block': f'arsenal_beacon:{name}', 'properties': keep}]
        return {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [entry], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]}
    for name, keep in (('exchange_shop', None), ('support_platform', ['mk']), ('support_cannon', None)):
        out.append((mod / f'main/resources/data/arsenal_beacon/loot_tables/blocks/{name}.json', loot(name, keep)))
    def shaped(result, pattern, key, count=1):
        return {'type': 'minecraft:crafting_shaped', 'pattern': pattern, 'key': {k: {'item': v} for k, v in key.items()}, 'result': {'item': f'arsenal_beacon:{result}', 'count': count}}
    recipes = {
        'exchange_shop': shaped('exchange_shop', ['CCC', 'IGI', 'IRI'], {'C': 'minecraft:copper_ingot', 'I': 'minecraft:iron_ingot', 'G': 'minecraft:glass', 'R': 'minecraft:redstone'}),
        'support_platform': shaped('support_platform', ['CGC', 'IRI', 'III'], {'C': 'minecraft:copper_ingot', 'G': 'minecraft:glass', 'I': 'minecraft:iron_ingot', 'R': 'minecraft:redstone_block'}),
    }
    for folder in ('main', 'standalone'):               # both editions craft these; the standalone jar replaces main's recipe folder
        for name, data in recipes.items(): out.append((mod / f'{folder}/resources/data/arsenal_beacon/recipes/{name}.json', data))
    tag = mod / 'main/resources/data/minecraft/tags/blocks/mineable/pickaxe.json'
    values = json.loads(tag.read_text())['values'] if tag.exists() else []
    for b in ('exchange_shop', 'support_platform', 'support_cannon'):
        if f'arsenal_beacon:{b}' not in values: values.append(f'arsenal_beacon:{b}')
    out.append((tag, {'replace': False, 'values': values}))
    return out
