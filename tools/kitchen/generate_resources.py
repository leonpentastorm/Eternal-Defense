#!/usr/bin/env python3
"""Rebuild functional kitchen models, recipes, food tags and ingredient definitions."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / 'custom-mods/arsenal-beacon/src/main/resources'
ASSETS = RES / 'assets/arsenal_beacon'
DATA = RES / 'data/arsenal_beacon'

def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + '\n')

def cube(start, end, texture):
    return {'from': start, 'to': end, 'faces': {face: {'uv': [0, 0, 16, 16], 'texture': '#' + texture} for face in ['north', 'south', 'east', 'west', 'up', 'down']}}

def model(name, textures, elements):
    write(ASSETS / f'models/block/{name}.json', {'textures': dict(particle=textures['frame'], **textures), 'elements': elements})

def block(name):
    write(ASSETS / f'blockstates/{name}.json', {'variants': {f'facing={f}': dict(model=f'arsenal_beacon:block/{name}', y=angle) for f, angle in [('north', 0), ('east', 90), ('south', 180), ('west', 270)]}})
    write(ASSETS / f'models/item/{name}.json', {'parent': f'arsenal_beacon:block/{name}', 'display': {'gui': {'rotation': [30, 225, 0], 'translation': [-4, -3, 0], 'scale': [.32, .32, .32]}, 'fixed': {'scale': [.35, .35, .35]}, 'ground': {'scale': [.3, .3, .3]}, 'thirdperson_righthand': {'scale': [.25, .25, .25]}}})
    write(DATA / f'loot_tables/blocks/{name}.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': 'arsenal_beacon:' + name}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})

for mk in range(1, 5):
    name = f'mess_hall_mk{mk}'
    textures = {'frame': ['minecraft:block/spruce_planks', 'minecraft:block/copper_block', 'minecraft:block/polished_andesite', 'minecraft:block/iron_block'][mk - 1], 'top': 'minecraft:block/smooth_stone', 'fire': 'minecraft:block/magma', 'metal': 'minecraft:block/cauldron_side', 'trim': ['minecraft:block/oak_log', 'minecraft:block/stripped_oak_log', 'minecraft:block/stone_bricks', 'minecraft:block/green_concrete'][mk - 1]}
    elements = [cube([1, 1, 1], [31, 3, 31], 'frame')]
    for x in (2, 27):
        for z in (2, 27):
            elements.append(cube([x, 3, z], [x + 3, 12, z + 3], 'frame'))
    if mk == 1:
        elements += [cube([1, 11, 17], [31, 13, 31], 'frame'), cube([5, 3, 4], [24, 5, 12], 'fire'), cube([4, 5, 7], [5, 16, 9], 'trim'), cube([24, 5, 7], [25, 16, 9], 'trim'), cube([4, 13, 7], [25, 14, 9], 'metal'), cube([9, 11, 6], [19, 14, 10], 'trim')]
    elif mk == 2:
        elements += [cube([1, 11, 1], [31, 13, 31], 'top'), cube([3, 13, 3], [13, 15, 12], 'metal'), cube([17, 13, 18], [29, 14, 29], 'trim'), cube([2, 13, 29], [30, 16, 31], 'frame')]
    elif mk == 3:
        elements += [cube([1, 3, 1], [31, 11, 15], 'trim'), cube([1, 11, 1], [31, 13, 31], 'top'), cube([4, 13, 3], [14, 16, 14], 'metal'), cube([19, 13, 3], [29, 16, 14], 'metal'), cube([3, 5, .9], [12, 9, 2], 'fire')]
    else:
        elements += [cube([1, 3, 1], [31, 11, 31], 'trim'), cube([0, 11, 0], [32, 13, 32], 'top'), cube([2, 13, 2], [14, 15, 14], 'metal'), cube([18, 13, 2], [30, 15, 14], 'metal'), cube([2, 13, 21], [30, 16, 30], 'frame'), cube([3, 5, .8], [29, 9, 1.8], 'frame')]
    model(name, textures, elements)
    block(name)

model('cook_pot', {'frame': 'minecraft:block/cauldron_side', 'trim': 'minecraft:block/spruce_planks', 'inside': 'minecraft:block/cauldron_inner'}, [cube([2, 2, 2], [30, 4, 14], 'frame'), cube([2, 4, 2], [4, 12, 14], 'frame'), cube([28, 4, 2], [30, 12, 14], 'frame'), cube([4, 4, 2], [28, 12, 4], 'frame'), cube([4, 4, 12], [28, 12, 14], 'frame'), cube([4, 4, 4], [28, 5, 12], 'inside'), cube([0, 4, 6], [2, 9, 10], 'frame'), cube([30, 4, 6], [32, 9, 10], 'frame'), cube([15, 1, 14], [17, 16, 16], 'trim'), cube([3, 8, 0], [29, 16, 1], 'trim')])
block('cook_pot')
model('cook_pot_stew', {'frame': 'minecraft:block/brown_mushroom_block'}, [cube([4, 10, 4], [28, 10.5, 12], 'frame')])
# A small bread-and-filling ration model uses vanilla textures until painted item art arrives.
write(ASSETS / 'models/item/prepared_sandwich.json', {'textures': {'particle': 'minecraft:block/white_terracotta', 'bread': 'minecraft:block/white_terracotta', 'filling': 'minecraft:block/green_terracotta'}, 'elements': [cube([2, 4, 3], [14, 7, 13], 'bread'), cube([2, 7, 3], [14, 9, 13], 'filling'), cube([2, 9, 3], [14, 12, 13], 'bread')], 'display': {'gui': {'rotation': [30, 225, 0], 'scale': [.85, .85, .85]}, 'thirdperson_righthand': {'scale': [.5, .5, .5]}, 'firstperson_righthand': {'rotation': [0, 45, 0], 'scale': [.6, .6, .6]}}})

families = {
    'staples': (['bread', 'baked_potato', 'wheat'], True, {}),
    'protein': (['cooked_chicken', 'cooked_mutton', 'cooked_rabbit', 'rabbit_stew'], False, {'vitality': 1}),
    'fish': (['cooked_cod', 'cooked_salmon'], False, {'mobility': 1}),
    'vegetables': (['carrot', 'potato', 'beetroot', 'beetroot_soup', 'dried_kelp'], False, {'fortitude': 1}),
    'dairy': (['milk_bucket'], False, {'fortitude': 2}),
    'eggs': (['egg'], False, {'vitality': 1}),
    'mushrooms': (['brown_mushroom', 'red_mushroom', 'mushroom_stew'], False, {'steadiness': 1}),
    'sweet': (['apple', 'sweet_berries', 'melon_slice', 'sugar'], False, {'mobility': 1}),
    'firepower': (['cooked_beef'], False, {'firepower': 1}),
    'quick_hands': (['honey_bottle'], False, {'quick_hands': 1}),
    'brawler': (['cooked_porkchop'], False, {'brawler': 1}),
    'heavy_hand': (['golden_carrot'], False, {'heavy_hand': 1}),
    'demolition': (['pufferfish'], False, {'demolition': 1}),
}
for name, (items, staple, effects) in families.items():
    write(DATA / f'tags/items/meal/{name}.json', {'replace': False, 'values': ['minecraft:' + i for i in items]})
    write(DATA / f'meal_ingredients/{name}.json', {'ingredient': {'tag': 'arsenal_beacon:meal/' + name}, 'staple': staple, 'effects': effects})

recipes = {
    'mess_hall_mk1': {'type': 'minecraft:crafting_shaped', 'pattern': ['PFP', 'IBI', 'PPP'], 'key': {'P': {'tag': 'minecraft:planks'}, 'F': {'item': 'minecraft:furnace'}, 'I': {'item': 'minecraft:iron_ingot'}, 'B': {'item': 'minecraft:barrel'}}, 'result': {'item': 'arsenal_beacon:mess_hall_mk1'}},
    'cook_pot': {'type': 'minecraft:crafting_shaped', 'pattern': ['ISI', 'ICI', 'III'], 'key': {'I': {'item': 'minecraft:iron_ingot'}, 'S': {'item': 'minecraft:oak_sign'}, 'C': {'item': 'minecraft:cauldron'}}, 'result': {'item': 'arsenal_beacon:cook_pot'}},
}
for name, recipe in recipes.items():
    write(DATA / f'recipes/{name}.json', recipe)
    write(ROOT / f'custom-mods/arsenal-beacon/src/standalone/resources/data/arsenal_beacon/recipes/{name}.json', recipe)

for name in ('mineable/pickaxe',):
    path = RES / f'data/minecraft/tags/blocks/{name}.json'
    value = json.loads(path.read_text())
    for block_name in ['cook_pot', *[f'mess_hall_mk{i}' for i in range(1, 5)]]:
        entry = 'arsenal_beacon:' + block_name
        if entry not in value['values']:
            value['values'].append(entry)
    write(path, value)

lang_path = ASSETS / 'lang/en_us.json'
lang = json.loads(lang_path.read_text())
lang.update({f'block.arsenal_beacon.mess_hall_mk{i}': f'Mess Hall Mk {roman}' for i, roman in enumerate(['I', 'II', 'III', 'IV'], 1)})
lang.update({'block.arsenal_beacon.cook_pot': 'Cook Pot', 'item.arsenal_beacon.prepared_sandwich': 'Prepared Sandwich'})
strings = {
    'kitchen.sandwich': 'Sandwich', 'kitchen.stew': 'Stew', 'kitchen.prepare': 'Prepare',
    'kitchen.ingredients': 'Ingredients', 'kitchen.output': 'Output',
    'kitchen.effects': '%s / %s meal effects', 'kitchen.servings': '%s servings',
    'kitchen.quantity': 'Food: %s / %s', 'kitchen.quantity_hint': 'Batch consumes %s ingredient items; %s available.',
    'kitchen.consume': 'Consumes %s from this slot.',
    'kitchen.links': 'Pots: %s / %s', 'kitchen.pot_row': 'Pot %s: %s left', 'kitchen.pot_row_empty': 'Pot %s: Empty',
    'kitchen.pot_coordinates': 'Cook Pot at %s, %s, %s', 'kitchen.pot_empty': 'Empty Cook Pot',
    'kitchen.pot_status': '%s | %s servings. Bring a bowl to eat.',
    'kitchen.no_hall': 'No available linked Mess Hall within 8 blocks.',
    'kitchen.served': 'Meal served. %s servings left; your bowl is reusable.',
    'kitchen.footprint': 'Footprint: %s', 'kitchen.sandwich_hint': 'Portable field ration. Combine a staple with other ingredients for up to two effects. Take sandwiches from the output slot.',
    'kitchen.problem.ingredients': 'Insert at least two different ingredient types.',
    'kitchen.problem.sandwich_types': 'Use one staple and one or two fillings (up to three types).',
    'kitchen.problem.batch_types': 'Use at most four ingredient types for a Mk I stew.',
    'kitchen.problem.quantity': 'Add more ingredient items to meet the batch cost shown at right.',
    'kitchen.problem.ingredient': 'Remove ingredients without a meal trait.',
    'kitchen.problem.staple': 'Sandwiches need a staple: bread, wheat or baked potato.',
    'kitchen.problem.effects': 'Add a filling with a meal effect.',
    'kitchen.problem.pot': 'Place an empty Cook Pot within 8 blocks. Linked pots must be loaded.',
    'kitchen.problem.pot_full': 'Selected Cook Pot is full. Select an empty pot.',
    'kitchen.problem.output': 'Take the sandwiches from the output slot first.',
    'kitchen.upgrade': 'Upgrade to Mk %s', 'kitchen.upgrade_cost': 'Cost: %s × %s', 'kitchen.upgrade_have': 'Carrying: %s',
    'meal.effect.vitality': 'Vitality', 'meal.effect.fortitude': 'Fortitude',
    'meal.effect.steadiness': 'Steadiness', 'meal.effect.mobility': 'Mobility',
    'meal.effect.firepower': 'Firepower', 'meal.effect.quick_hands': 'Quick Hands',
    'meal.effect.brawler': 'Brawler', 'meal.effect.heavy_hand': 'Heavy Hand', 'meal.effect.demolition': 'Demolition',
    'meal.bonus': '%s %s', 'meal.name.sandwich': '%s Sandwich', 'meal.name.stew': '%s Stew',
    'meal.bonus.vitality': '%s max health', 'meal.bonus.fortitude': '%s armor', 'meal.bonus.steadiness': '%s knockback resistance', 'meal.bonus.mobility': '%s movement speed',
    'meal.bonus.firepower': '%s gun damage', 'meal.bonus.quick_hands': '%s reload speed',
    'meal.bonus.brawler': '%s gun bash damage', 'meal.bonus.heavy_hand': '%s gun bash knockback', 'meal.bonus.demolition': '%s grenade / rocket radius',
    'meal.duration': '%s minutes of field time', 'meal.home_hint': 'At home: double strength; field timer freezes.',
    'meal.preview_field': 'FIELD: %s', 'meal.preview_home': 'HOME: %s',
    'meal.timer': 'Field time: %s', 'meal.home': 'HOME ENHANCEMENT | 2× | FROZEN', 'meal.field': 'FIELD | Normal strength',
    'guide.kitchen.title': 'Mess Hall',
    'guide.kitchen.body': 'Craft a Mess Hall Mk I and a Cook Pot, then place both inside the beacon zone. Right-click the hall and insert at least two ingredients. Sandwich makes portable rations; Stew stocks a selected nearby empty pot. Bring a normal bowl and use a filled pot to eat a communal serving. Your empty bowl is reusable.',
    'guide.kitchen.detail': 'Sandwiches use one staple and one or two fillings, with 1–2 effects. Stew supports up to 3 effects and needs two different ingredient types. Mk I–IV batches consume 4/7/10/12 food items to make 4/8/12/16 servings. More items fund the batch; only different ingredients score effects. Split stacks do not improve strength. Containers return to the cook.\n\nChicken, mutton, rabbit and eggs add health; vegetables and milk add armor; mushrooms add knockback resistance; cooked fish and sweets add movement speed. Beef gives Firepower (gun damage); honey gives Quick Hands (reload speed); pork gives Brawler (gun bash damage); golden carrot gives Heavy Hand (gun bash knockback); pufferfish gives Demolition (grenade/rocket radius).\n\nMeals last 30 minutes of field time. Inside a valid beacon zone their strength doubles and the timer freezes; returning home freezes the remaining time without refreshing it. Logout pauses the timer; dimension travel preserves it; death clears the meal. New meals replace old ones. Pot links reach 8 blocks. Mk I–IV support 1/2/3/4 pots. Upgrade through the hall menu; stored food and links are retained.',
    'guide.kitchen.detail.pack': 'Mess Hall upgrades cost 8, 16 and 32 Reinforced Plating.',
    'guide.kitchen.detail.standalone': 'Mess Hall upgrades cost 4, 8 and 14 Ardent Energy.',
}
lang.update({'gui.arsenal_beacon.' + key: text for key, text in strings.items()})
write(lang_path, lang)
