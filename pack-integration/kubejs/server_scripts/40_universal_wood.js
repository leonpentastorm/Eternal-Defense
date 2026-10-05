// Expand wood inputs across every recipe serializer, including TaCZ gunpack materials.
// Rewrite ingredients only: never result stacks, NBT, or the quantity of a material.
ServerEvents.recipes(event => {
  const Woods = Java.loadClass('dev.createarsenal.beacon.UniversalWood');
  let changed = 0;
  event.forEachRecipe({}, recipe => {
    const original = String(recipe.json);
    const updated = String(Woods.normalizeJson(original));
    if (updated !== original) { recipe.merge(JSON.parse(updated)); changed++; }
  });
  console.info('[Create Arsenal] Universal wood inputs applied to ' + changed + ' recipes.');
});
