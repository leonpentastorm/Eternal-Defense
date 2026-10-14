// Expand wood inputs across every recipe serializer, including TaCZ gunpack materials.
ServerEvents.recipes(event => {
  const Woods = Java.loadClass('dev.eternaldefense.beacon.UniversalWood');
  let changed = 0;
  event.forEachRecipe({}, recipe => {
    const original = String(recipe.json);
    const updated = String(Woods.normalizeJson(original));
    if (updated !== original) { recipe.merge(JSON.parse(updated)); changed++; }
  });
  console.info('[Eternal Defense] Universal wood inputs applied to ' + changed + ' recipes.');
});
