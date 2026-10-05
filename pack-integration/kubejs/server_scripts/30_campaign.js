// Shared world progression: both co-op players advance the same threat tier.
const arsenalMilestones = {'kubejs:brass_receiver':1,'kubejs:hardened_receiver':2,'kubejs:exotic_receiver':3};
const arsenalBalance = JsonIO.read('kubejs/config/arsenal_balance.json');
PlayerEvents.inventoryChanged(event => {
  const next = arsenalMilestones[String(event.item.id)];
  if (next === undefined || !event.server) return;
  const data = event.server.persistentData;
  const current = Number(data.getInt('arsenalTier'));
  if (next <= current) return;
  data.putInt('arsenalTier', next);
  event.server.tell(Text.gold('Create Arsenal: factory tier '+(next+1)+' reached. New enemies are tougher.'));
});
PlayerEvents.loggedIn(event => {
  event.player.tell(Text.gold('Welcome to Create Arsenal. Right-click your Field Guide to get started.'));
});
EntityEvents.spawned(event => {
  const e = event.entity;
  if (!e.level || e.level.clientSide || !e.server || !e.isLiving()) return;
  if (String(e.type).indexOf('player') >= 0 || e.persistentData.getBoolean('arsenalScaled') || e.persistentData.getBoolean('arsenalRaider')) return;
  // Use the native hostile classification: pets, traders and passive wildlife remain safe.
  if (!e.isMonster()) return;
  const cfg = arsenalBalance;
  const tier = Math.min(3, Math.max(0,Number(e.server.persistentData.getInt('arsenalTier'))));
  const hp = e.getAttribute('minecraft:generic.max_health');
  if (!hp) return;
  const original = hp.getBaseValue();
  const cap = original >= 150 ? cfg.bossHealthCap : (original >= 50 ? cfg.eliteHealthCap : cfg.ordinaryHealthCap);
  hp.setBaseValue(Math.max(original,Math.min(cap,original*cfg.healthMultipliers[tier])));
  e.setHealth(e.getMaxHealth());
  const attack = e.getAttribute('minecraft:generic.attack_damage');
  if (attack) attack.setBaseValue(attack.getBaseValue()*cfg.damageMultipliers[tier]);
  e.persistentData.putBoolean('arsenalScaled',true);
});
