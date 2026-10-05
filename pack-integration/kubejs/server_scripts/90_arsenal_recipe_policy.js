ServerEvents.recipes(event => {
  const policy = Java.loadClass('dev.createarsenal.beacon.RecipePolicy');
  policy.nativeStations().forEach(id => event.remove({output: String(id)}));
  const armorPolicy = Java.loadClass('dev.createarsenal.beacon.ArmorPlatform');
  armorPolicy.managedItems().forEach(id => event.remove({output: String(id)}));
  ['omegarecon:armory','omegarecon:armory_block','caps_awim_tactical_gear_rework:crafttable',
   'caps_awim_tactical_gear_rework:craft_table'].forEach(id => event.remove({output: id}));
  let conversions = 0;
  event.forEachRecipe({type: 'tacz:gun_smith_table_crafting'}, recipe => {
    let before = recipe.json.toString();
    let after = policy.balanceConversion(before);
    if (String(after) !== String(before)) { recipe.merge(JSON.parse(String(after))); conversions++; }
  });
  console.info('[Create Arsenal] Native station acquisition disabled; reduced conversion materials to 25% in '+conversions+' recipes.');
});
