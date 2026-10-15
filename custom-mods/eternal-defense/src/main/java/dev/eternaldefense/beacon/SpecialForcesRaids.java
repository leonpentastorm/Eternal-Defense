package dev.eternaldefense.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.*;

/** The addon's mission manager is disabled; only campaign-owned late-raid soldiers may join. */
final class SpecialForcesRaids {
    static int count(int tier){return count(tier,1);}
    static int count(int tier,int players){return tier<4?0:Math.min(5,(tier<7?1:2)+Math.max(0,Math.min(4,players)-1));}
    static boolean soldier(Entity e){return installed()&&e.getClass().getName().equals("su.uTa4u.specialforces.entities.SwatEntity");}
    private static volatile Boolean installed;
    /** Is the optional Special Forces mod (taczsf) here? */
    static boolean installed(){
        var known=installed;if(known!=null)return known;
        var mods=net.minecraftforge.fml.ModList.get();if(mods==null)return false; // Not booted yet (unit tests): ask again later
        installed=known=mods.isLoaded("taczsf");return known;
    }
    @SubscribeEvent public void start(ServerStartedEvent event){
        if(!net.minecraftforge.fml.ModList.get().isLoaded("taczsf"))return;
        try{MinecraftForge.EVENT_BUS.unregister(Class.forName("su.uTa4u.specialforces.capabilities.observation.ObservationManager"));}
        catch(ClassNotFoundException ex){com.mojang.logging.LogUtils.getLogger().warn("Special Forces mission listener unavailable; spawn guard remains active",ex);}
        for(ServerLevel level:event.getServer().getAllLevels())for(Entity entity:level.getAllEntities())if(soldier(entity)&&!owned(entity,level)&&!corpse(entity))entity.discard();
    }
    static boolean owned(Entity e,ServerLevel l){
        CampaignData d=CampaignData.get(l.getServer().overworld());
        return l.dimension()==Level.OVERWORLD&&d.phase.equals("raid")&&d.raidTier>=4&&e.getPersistentData().getBoolean("eternalRaider")&&d.raiders.contains(e.getUUID());
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public void join(EntityJoinLevelEvent event){
        if(event.getLevel() instanceof ServerLevel l&&soldier(event.getEntity())&&!owned(event.getEntity(),l)&&!corpse(event.getEntity())){
            event.setCanceled(true);event.getEntity().discard();
        }
    }
    static final String SOLDIERS="Special Forces soldiers";
    /** Can raids bring soldiers now (the mod is here and its API answered) */
    static boolean available(){return installed()&&!Compat.broken(SOLDIERS);}
    @SuppressWarnings({"unchecked","rawtypes"})
    static boolean spawn(ServerLevel l,CampaignData d,BlockPos pos,boolean warn){
        if(count(d.raidTier,d.wavePlayers)==0||!d.phase.equals("raid"))return false;
        try{
            Class specialty=Class.forName("su.uTa4u.specialforces.Specialty");
            Class<?> swat=Class.forName("su.uTa4u.specialforces.entities.SwatEntity");states(swat);
            String role=d.raidTier>=7&&d.spawnRemaining==1?"BULLDOZER":d.spawnRemaining>1?"SNIPER":"ASSAULTER";
            Mob mob=(Mob)swat.getMethod("withSpecialty",Level.class,specialty).invoke(null,l,Enum.valueOf(specialty,role));
            mob.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,l.random.nextFloat()*360,0);
            mob.getPersistentData().putBoolean("eternalRaider",true);mob.getPersistentData().putBoolean("eternalSpecialForces",true);
            mob.getPersistentData().putString("eternalSpecialForcesRole",role);
            mob.finalizeSpawn(l,l.getCurrentDifficultyAt(pos),MobSpawnType.EVENT,null,null);
            mob.setPersistenceRequired();mob.setCanPickUpLoot(false);
            var health=mob.getAttribute(Attributes.MAX_HEALTH);if(health!=null){health.removeModifiers();health.setBaseValue(role.equals("BULLDOZER")?110:56+Math.max(0,Math.min(10,d.raidTier))*2);mob.setHealth(mob.getMaxHealth());}
            var armor=mob.getAttribute(Attributes.ARMOR);if(armor!=null){armor.removeModifiers();armor.setBaseValue(role.equals("BULLDOZER")?8:3);}
            var toughness=mob.getAttribute(Attributes.ARMOR_TOUGHNESS);if(toughness!=null){toughness.removeModifiers();toughness.setBaseValue(0);}
            mob.setGlowingTag(EternalDefense.revealed(d));
            if(!l.noCollision(mob))return false;
            d.raiders.add(mob.getUUID()); // The join guard verifies ownership before allowing insertion.
            if(!l.addFreshEntity(mob)){d.raiders.remove(mob.getUUID());return false;}
            balance(mob,d.raidTier);mob.setHealth(mob.getMaxHealth());RaidSpawns.rush(mob);
            d.setDirty();
            if(warn)for(var player:l.players())if(EternalDefense.near(player,d)){
                player.sendSystemMessage(Component.literal("[Eternal Defense] SPECIAL FORCES INCOMING! Get behind sandbags and prepare for a shootout!"));
                player.connection.send(new ClientboundSetTitlesAnimationPacket(10,100,20));
                player.connection.send(new ClientboundSetTitleTextPacket(Component.literal("SPECIAL FORCES INCOMING")));
                player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("Get behind sandbags. Prepare for a shootout!")));
            }
            return true;
        }catch(ReflectiveOperationException|RuntimeException ex){Compat.warn(SOLDIERS,ex);return false;}
    }
    private static java.lang.reflect.Method stateGetter,stateSetter;
    static void states(Class<?> swat)throws ReflectiveOperationException{
        if(stateGetter!=null)return;var setter=swat.getDeclaredMethod("setState",byte.class);setter.setAccessible(true);stateGetter=swat.getMethod("getState");stateSetter=setter;
    }
    static int nativeState(Entity e){
        if(!soldier(e))return 0;
        try{states(e.getClass());return ((Number)stateGetter.invoke(e)).intValue();}
        catch(ReflectiveOperationException|RuntimeException ex){Compat.warn(SOLDIERS,ex);return 0;}
    }
    static boolean defeated(Entity e){return soldier(e)&&nativeState(e)!=0;}
    static boolean corpse(Entity e){return soldier(e)&&e.getPersistentData().getBoolean("eternalSpecialForces")&&e.getPersistentData().getBoolean("eternalCorpse")&&defeated(e);}
    static void retire(Entity e,ServerLevel l){
        if(!(e instanceof Mob mob)||!defeated(e))return;
        try{stateSetter.invoke(e,(byte)2);}catch(ReflectiveOperationException|RuntimeException ex){Compat.warn(SOLDIERS,ex);}
        // Native DEAD bodies stay lootable at one HP.
        mob.setHealth(1);mob.setTarget(null);mob.getNavigation().stop();mob.setNoAi(true);mob.setInvulnerable(true);mob.setGlowingTag(false);
        mob.getPersistentData().putBoolean("eternalCorpse",true);mob.getPersistentData().putBoolean("eternalRaider",false);
        if(!mob.getPersistentData().contains("eternalCorpseUntil"))mob.getPersistentData().putLong("eternalCorpseUntil",l.getGameTime()+12000);
    }
    static void cleanup(ServerLevel l){
        if(!installed())return; // Runs every second over every entity of the Overworld: skip it when no soldier can exist
        for(Entity e:l.getAllEntities())if(corpse(e)&&l.getGameTime()>=e.getPersistentData().getLong("eternalCorpseUntil"))e.discard();
    }
    static void balance(Mob mob,int tier){
        boolean heavy=mob.getPersistentData().getString("eternalSpecialForcesRole").equals("BULLDOZER");
        var health=mob.getAttribute(Attributes.MAX_HEALTH);if(health!=null){float current=mob.getHealth();health.removeModifiers();health.setBaseValue(heavy?110:56+Math.max(0,Math.min(10,tier))*2);mob.setHealth(Math.min(current,mob.getMaxHealth()));}
        var armor=mob.getAttribute(Attributes.ARMOR);if(armor!=null){armor.removeModifiers();armor.setBaseValue(heavy?8:3);}
        var toughness=mob.getAttribute(Attributes.ARMOR_TOUGHNESS);if(toughness!=null){toughness.removeModifiers();toughness.setBaseValue(0);}
    }
}
