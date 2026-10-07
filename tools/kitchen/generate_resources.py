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
    block(name)  # geometry: artist model (models/block/mess_hall_mkN.json), never regenerated

block('cook_pot')
# cook_pot_stew geometry: artist model, never regenerated
# A small bread-and-filling ration model uses vanilla textures until painted item art arrives.
write(ASSETS / 'models/item/prepared_sandwich.json', {'textures': {'particle': 'minecraft:block/white_terracotta', 'bread': 'minecraft:block/white_terracotta', 'filling': 'minecraft:block/green_terracotta'}, 'elements': [cube([2, 4, 3], [14, 7, 13], 'bread'), cube([2, 7, 3], [14, 9, 13], 'filling'), cube([2, 9, 3], [14, 12, 13], 'bread')], 'display': {'gui': {'rotation': [30, 225, 0], 'scale': [.85, .85, .85]}, 'thirdperson_righthand': {'scale': [.5, .5, .5]}, 'firstperson_righthand': {'rotation': [0, 45, 0], 'scale': [.6, .6, .6]}}})

families = {
    # Sandwich foundations
    'staples': (['bread', 'baked_potato', 'wheat'], True, {}),
    # Everyday foods: two different foods of one family in a stew double that family's effect
    'protein': (['cooked_chicken', 'cooked_mutton', 'cooked_rabbit', 'rabbit_stew'], False, {'vitality': 1}),
    'eggs': (['egg'], False, {'vitality': 1}),
    'fish': (['cooked_cod', 'cooked_salmon'], False, {'mobility': 1}),
    'vegetables': (['carrot', 'potato', 'beetroot', 'beetroot_soup', 'dried_kelp'], False, {'fortitude': 1}),
    'dairy': (['milk_bucket'], False, {'fortitude': 2}),
    'mushrooms': (['brown_mushroom', 'red_mushroom', 'mushroom_stew'], False, {'steadiness': 1}),
    'sweet': (['apple', 'sweet_berries', 'melon_slice', 'sugar'], False, {'mobility': 1}),
    # Gun foods
    'firepower': (['cooked_beef'], False, {'firepower': 1}),
    'quick_hands': (['honey_bottle'], False, {'quick_hands': 1}),
    'brawler': (['cooked_porkchop'], False, {'brawler': 1}),
    'heavy_hand': (['golden_carrot'], False, {'heavy_hand': 1}),
    'demolition': (['pufferfish'], False, {'demolition': 1}),
    # Round three: the rest of the vanilla food list
    'raw_meat': (['beef', 'chicken', 'mutton', 'porkchop', 'rabbit'], False, {'might': 1}),
    'raw_fish': (['cod', 'salmon', 'tropical_fish'], False, {'swim': 1}),
    'treats': (['cookie', 'pumpkin_pie'], False, {'agility': 1}),
    'lucky': (['glow_berries'], False, {'fortune': 1}),
    'mystery': (['suspicious_stew'], False, {'fortune': 2}),
    'golden': (['golden_apple'], False, {'recovery': 2}),
    'enchanted': (['enchanted_golden_apple'], False, {'recovery': 3, 'fortune': 1}),
    'grim': (['rotten_flesh', 'spider_eye', 'poisonous_potato'], False, {'hearth': 1}),
    'chorus': (['chorus_fruit'], False, {'springy': 2}),
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
