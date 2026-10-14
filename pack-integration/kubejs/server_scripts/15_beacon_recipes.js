// Four industrial upgrade paths. All quantity scaling is handled by the beacon.
ServerEvents.recipes(event => {
  event.remove({output:'tacz_turrets:turret'});
  // 0.0.41 (owner): 2 iron, 2 golden and 2 copper sheets compacted into 1 plating, no heat (was 4 hardened alloy and a brass sheet, heated, for 4).
  event.recipes.create.compacting('eternal_defense:reinforced_plating', ['2x create:iron_sheet','2x create:golden_sheet','2x create:copper_sheet']).id('kubejs:beacon/plating');
  const l = 'kubejs:incomplete_brass_receiver';
  event.recipes.create.sequenced_assembly(['4x eternal_defense:logistics_module'],'create:electron_tube',[
    event.recipes.create.deploying(l,[l,'create:brass_sheet']),
    event.recipes.create.deploying(l,[l,'create:precision_mechanism']),
    event.recipes.create.pressing(l,l)
  ]).transitionalItem(l).loops(2).id('kubejs:beacon/logistics');
  event.recipes.create.mixing('4x eternal_defense:resonance_coil',['create:precision_mechanism','minecraft:ender_pearl','4x minecraft:redstone','2x create:golden_sheet']).heated().id('kubejs:beacon/defense');
  // 0.0.28 (owner): 8 matrices per craft (was 4) and a Brass Receiver in the middle (was a Hardened Receiver); every price the mod asks in
  // matrices is halved as well (Economy.matrices, the cannon's Quantum, Aura and Dimension upgrades, the platform's last era).
  event.recipes.create.mechanical_crafting('8x eternal_defense:restoration_matrix',[' B B ','BPEPB',' EHE ','BPEPB',' B B '],{
    B:'create:brass_sheet',P:'create:precision_mechanism',E:'minecraft:ender_pearl',H:'kubejs:brass_receiver'
  }).id('kubejs:beacon/restoration');
});
