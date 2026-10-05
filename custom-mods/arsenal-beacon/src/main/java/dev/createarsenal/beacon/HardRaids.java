package dev.createarsenal.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.*;

/** One campaign-owned modded boss in the final wave of every third started raid. */
final class HardRaids {
    static final String[] TYPES={"born_in_chaos_v1:zombie_bruiser","born_in_chaos_v1:skeleton_thrasher","born_in_chaos_v1:mother_spider","cataclysm:ender_golem","cataclysm:kobolediator","cataclysm:aptrgangr"};
    private static final Map<ServerLevel,ServerBossEvent> BARS=new WeakHashMap<>();
    static boolean isHard(int raidNumber){return Rules.hardRaid(raidNumber);}
    static float health(int tier){return Rules.bossHealth(tier);}
    static void awaken(Mob mob){
        // These author APIs wake a campaign-spawned guardian without waiting at a ruin.
        String id=BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString();
        try{
            if(id.equals("cataclysm:ender_golem"))mob.getClass().getMethod("setIsAwaken",boolean.class).invoke(mob,true);
            if(id.equals("cataclysm:kobolediator")){mob.getClass().getMethod("setAwaken",boolean.class).invoke(mob,true);mob.getClass().getMethod("setSleep",boolean.class).invoke(mob,false);}
        }catch(ReflectiveOperationException e){throw new IllegalStateException("Cannot awaken raid boss "+id,e);}
    }
    static void start(CampaignData d){d.raidsStarted++;d.hardRaid=isHard(d.raidsStarted);d.bossSpawned=false;d.bossKilled=false;d.bossId=null;d.setDirty();}
    static void balance(Mob mob,int tier){
        var hp=mob.getAttribute(Attributes.MAX_HEALTH);if(hp!=null){float previous=mob.getHealth();hp.removeModifiers();hp.setBaseValue(Rules.scaledBossHealth(tier,Math.max(1,mob.getPersistentData().getInt("arsenalBossDefenders"))));mob.setHealth(Math.min(previous,mob.getMaxHealth()));}
        var armor=mob.getAttribute(Attributes.ARMOR);if(armor!=null){armor.removeModifiers();armor.setBaseValue(Math.min(6,tier));}
        var toughness=mob.getAttribute(Attributes.ARMOR_TOUGHNESS);if(toughness!=null){toughness.removeModifiers();toughness.setBaseValue(0);}
        var attack=mob.getAttribute(Attributes.ATTACK_DAMAGE);if(attack!=null){attack.removeModifiers();attack.setBaseValue(Math.min(16,4+tier*2));}
    }
    static boolean spawn(ServerLevel l,CampaignData d,BlockPos pos){
        if(!d.hardRaid||d.bossSpawned||!d.phase.equals("raid"))return false;String id=TYPES[Math.max(0,Math.min(5,d.raidTier))];
        var type=BuiltInRegistries.ENTITY_TYPE.getOptional(new ResourceLocation(id)).orElse(EntityType.RAVAGER);var entity=type.create(l);if(!(entity instanceof Mob mob))return false;
        mob.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);mob.finalizeSpawn(l,l.getCurrentDifficultyAt(pos),MobSpawnType.EVENT,null,null);
        mob.setPersistenceRequired();mob.getPersistentData().putBoolean("arsenalRaider",true);mob.getPersistentData().putBoolean("arsenalBoss",true);mob.getPersistentData().putLong("arsenalBossSerial",d.campaignSerial);mob.getPersistentData().putInt("arsenalBossDefenders",d.wavePlayers);
        mob.setCustomName(Component.literal("Raid Boss — ").append(mob.getType().getDescription()));mob.setCustomNameVisible(true);
        if(!l.noCollision(mob)||!l.addFreshEntity(mob))return false;awaken(mob);balance(mob,d.raidTier);mob.setHealth(mob.getMaxHealth());BeaconCombat.attach(mob);
        d.bossId=mob.getUUID();d.bossSpawned=true;d.raiders.add(mob.getUUID());d.setDirty();
        l.getServer().getPlayerList().broadcastSystemMessage(Component.literal("[Create Arsenal] RAID BOSS INCOMING: ").append(mob.getType().getDescription()).append("! Defeat it and clear the wave for the hard-raid cache."),false);return true;
    }
    static void killed(ServerLevel l,LivingEntity entity){var d=CampaignData.get(l);if(d.phase.equals("raid")&&d.bossId!=null&&d.bossId.equals(entity.getUUID())){d.bossKilled=true;d.setDirty();}}
    static void clear(ServerLevel l){var bar=BARS.remove(l);if(bar!=null)bar.removeAllPlayers();}
    static void tick(ServerLevel l,CampaignData d){
        if(!d.phase.equals("raid")||!d.hardRaid||d.bossId==null){clear(l);return;}
        var entity=l.getEntity(d.bossId);if(!(entity instanceof LivingEntity boss)||!boss.isAlive()){clear(l);return;}
        // Some native entities apply their configured health on their first loaded tick.
        if(boss instanceof Mob mob&&boss.getMaxHealth()!=Rules.scaledBossHealth(d.raidTier,Math.max(1,boss.getPersistentData().getInt("arsenalBossDefenders"))))balance(mob,d.raidTier);
        var bar=BARS.computeIfAbsent(l,k->new ServerBossEvent(Component.literal("RAID BOSS"),BossEvent.BossBarColor.RED,BossEvent.BossBarOverlay.PROGRESS));
        bar.setName(Component.literal("RAID BOSS: ").append(boss.getType().getDescription()).append("  "+(int)Math.ceil(boss.getHealth())+" / "+(int)boss.getMaxHealth()));bar.setProgress(Math.max(0,Math.min(1,boss.getHealth()/boss.getMaxHealth())));
        Set<net.minecraft.server.level.ServerPlayer> eligible=new HashSet<>();for(var p:l.getServer().getPlayerList().getPlayers())if(ArsenalBeacon.near(p,d)){eligible.add(p);bar.addPlayer(p);}for(var p:new ArrayList<>(bar.getPlayers()))if(!eligible.contains(p))bar.removePlayer(p);
    }
}
