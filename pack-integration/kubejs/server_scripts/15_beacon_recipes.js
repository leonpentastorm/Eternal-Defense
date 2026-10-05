// Four industrial upgrade paths. All quantity scaling is handled by the beacon.
ServerEvents.recipes(event => {
  event.remove({output:'tacz_turrets:turret'});
  event.recipes.create.compacting('4x arsenal_beacon:reinforced_plating', ['4x kubejs:hardened_alloy','create:brass_sheet']).heated().id('kubejs:beacon/plating');
  const l = 'kubejs:incomplete_brass_receiver';
  event.recipes.create.sequenced_assembly(['4x arsenal_beacon:logistics_module'],'create:electron_tube',[
    event.recipes.create.deploying(l,[l,'create:brass_sheet']),
    event.recipes.create.deploying(l,[l,'create:precision_mechanism']),
    event.recipes.create.pressing(l,l)
  ]).transitionalItem(l).loops(2).id('kubejs:beacon/logistics');
  event.recipes.create.mixing('4x arsenal_beacon:resonance_coil',['create:precision_mechanism','minecraft:ender_pearl','4x minecraft:redstone','2x create:golden_sheet']).heated().id('kubejs:beacon/defense');
  event.recipes.create.mechanical_crafting('4x arsenal_beacon:restoration_matrix',[' B B ','BPEPB',' EHE ','BPEPB',' B B '],{
    B:'create:brass_sheet',P:'create:precision_mechanism',E:'minecraft:ender_pearl',H:'kubejs:hardened_receiver'
  }).id('kubejs:beacon/restoration');
});
