// Shared world progression: both co-op players advance the same threat tier.
const eternalMilestones = {'kubejs:brass_receiver':1,'kubejs:hardened_receiver':2,'kubejs:exotic_receiver':3};
const eternalBalance = JsonIO.read('kubejs/config/eternal_defense_balance.json');
PlayerEvents.inventoryChanged(event => {
  const next = eternalMilestones[String(event.item.id)];
  if (next === undefined || !event.server) return;
  const data = event.server.persistentData;
  const current = Number(data.getInt('eternalTier'));
  if (next <= current) return;
  data.putInt('eternalTier', next);
  event.server.tell(Text.gold('Eternal Defense: factory tier '+(next+1)+' reached. New enemies are tougher.'));
});
PlayerEvents.loggedIn(event => {
  event.player.tell(Text.gold('Welcome to Eternal Defense. Right-click your Field Guide to get started.'));
});
EntityEvents.spawned(event => {
  const e = event.entity;
  if (!e.level || e.level.clientSide || !e.server || !e.isLiving()) return;
  if (String(e.type).indexOf('player') >= 0 || e.persistentData.getBoolean('eternalScaled') || e.persistentData.getBoolean('eternalRaider')) return;
  // Use the native hostile classification: pets, traders and passive wildlife remain safe.
  if (!e.isMonster()) return;
  const cfg = eternalBalance;
  const tier = Math.min(3, Math.max(0,Number(e.server.persistentData.getInt('eternalTier'))));
  const hp = e.getAttribute('minecraft:generic.max_health');
  if (!hp) return;
  const original = hp.getBaseValue();
  const cap = original >= 150 ? cfg.bossHealthCap : (original >= 50 ? cfg.eliteHealthCap : cfg.ordinaryHealthCap);
  hp.setBaseValue(Math.max(original,Math.min(cap,original*cfg.healthMultipliers[tier])));
  e.setHealth(e.getMaxHealth());
  const attack = e.getAttribute('minecraft:generic.attack_damage');
  if (attack) attack.setBaseValue(attack.getBaseValue()*cfg.damageMultipliers[tier]);
  e.persistentData.putBoolean('eternalScaled',true);
});
