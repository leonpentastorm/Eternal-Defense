// TaCZ JS integrates gunpack recipes with the standard recipe manager.
ServerEvents.recipes(event => {
  var ages = JsonIO.read('kubejs/config/eternal_defense_weapon_ages.json') || {};
  var componentAges = JsonIO.read('kubejs/config/eternal_defense_component_ages.json') || {};
  let modified = 0;
  const historical = Java.loadClass('dev.eternaldefense.beacon.HistoricalGuns');
  // Cartridge cases and propellant per ammo batch, shared with the Ammo Platform
  const ammoInputs = Java.loadClass('dev.eternaldefense.beacon.AmmoInputs');
  event.forEachRecipe({type:'tacz:gun_smith_table_crafting'}, recipe => {
    var json = JSON.parse(recipe.json.toString());
    if (!json.result || !json.materials) return;
    var id = String(json.result.id || '');
    if (id.indexOf('create_armorer:') === 0 || id === 'lrl:db_long_super') return; // Shared Create milestones, never Ages.
    var age = Math.max(1, Math.min(5, Number(componentAges[String(json.result.type)+'|'+id] || ages[id] || 5)));
    json = JSON.parse(String(historical.normalize(JSON.stringify(json), age)));
    let part = null;
    if (json.result.type === 'gun') {
      part = age <= 2 ? 'pressed_receiver' : age <= 4 ? 'hardened_receiver' : 'exotic_receiver';
      json.materials.push({item:{item:'kubejs:'+part},count:age===2?2:1});
    } else if (json.result.type === 'ammo') {
      // Keep pack-specific requirements; replace conventional copper and powder inputs.
      json.materials = json.materials.filter(m => {
        var s = JSON.stringify(m.item);
        return s.indexOf('gunpowder') < 0 && s.indexOf('ingots/copper') < 0;
      });
      var batch = Math.max(1, Number(json.result.count || 1));
      var heavy = /rocket|40mm|grenade|missile|rpg/.test(id);
      json.materials.push({item:{item:'kubejs:cartridge_case'},count:Number(ammoInputs.casings(batch,id))});
      json.materials.push({item:{item:'kubejs:propellant'},count:Number(ammoInputs.propellant(batch,id))});
      if (heavy) json.materials.push({item:{item:'kubejs:hardened_alloy'},count:2});
    } else if (json.result.type === 'attachment') {
      json.materials.push({item:{item:'create:iron_sheet'},count:2});
    } else return;
    // Historical author recipes sometimes contain modern factory ingredients.
    if (age <= 2) json.materials = json.materials.map(m => {
      if (/nether|blaze|ender|brass|precision_mechanism|hardened|exotic/.test(JSON.stringify(m.item)))
        return {item:{item:'create:iron_sheet'},count:Math.max(1,Number(m.count || 1))};
      return m;
    });
    recipe.merge(json); modified++;
  });
  console.info('[Eternal Defense] Factory gates applied to '+modified+' TaCZ recipes.');
});
