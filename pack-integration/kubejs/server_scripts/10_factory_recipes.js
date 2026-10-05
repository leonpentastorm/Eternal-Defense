// Factory parts are the material gate for every gun-smith recipe.
ServerEvents.recipes(event => {
  event.recipes.create.pressing('kubejs:pressed_receiver', 'create:iron_sheet').id('kubejs:arsenal/pressed_receiver');
  event.recipes.create.pressing('8x kubejs:cartridge_case', 'create:copper_sheet').id('kubejs:arsenal/cartridge_case');
  event.recipes.create.mixing('8x kubejs:propellant', ['minecraft:gunpowder', 'minecraft:charcoal', 'minecraft:flint']).id('kubejs:arsenal/propellant');
  let b = 'kubejs:incomplete_brass_receiver';
  event.recipes.create.sequenced_assembly(['kubejs:brass_receiver'], 'kubejs:pressed_receiver', [
    event.recipes.create.deploying(b,[b,'create:brass_sheet']),
    event.recipes.create.deploying(b,[b,'create:cogwheel']),
    event.recipes.create.pressing(b,b)
  ]).transitionalItem(b).loops(2).id('kubejs:arsenal/brass_receiver');
  event.recipes.create.mixing('4x kubejs:hardened_alloy', ['4x minecraft:iron_ingot','minecraft:obsidian','minecraft:blaze_powder']).heated().id('kubejs:arsenal/hardened_alloy');
  let h = 'kubejs:incomplete_hardened_receiver';
  event.recipes.create.sequenced_assembly(['kubejs:hardened_receiver'], 'kubejs:brass_receiver', [
    event.recipes.create.deploying(h,[h,'kubejs:hardened_alloy']),
    event.recipes.create.deploying(h,[h,'create:precision_mechanism']),
    event.recipes.create.filling(h,[h,Fluid.of('minecraft:lava',100)]),
    event.recipes.create.pressing(h,h)
  ]).transitionalItem(h).loops(2).id('kubejs:arsenal/hardened_receiver');
  event.recipes.create.mechanical_crafting('4x kubejs:exotic_receiver', [' H H ','HPSPH',' SN S','HPSPH',' H H '],{
    H:'kubejs:hardened_receiver',P:'create:precision_mechanism',S:'minecraft:netherite_scrap',N:'minecraft:nether_star'
  }).id('kubejs:arsenal/exotic_receiver');
});
