package dev.createarsenal.beacon;

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
    /** Is the optional Special Forces mod (taczsf) here? Without it no soldier can exist, so nothing has to be looked for. Asked once. */
    static boolean installed(){
        var known=installed;if(known!=null)return known;
        var mods=net.minecraftforge.fml.ModList.get();if(mods==null)return false; // not booted yet (unit tests): ask again later
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
        return l.dimension()==Level.OVERWORLD&&d.phase.equals("raid")&&d.raidTier>=4&&e.getPersistentData().getBoolean("arsenalRaider")&&d.raiders.contains(e.getUUID());
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public void join(EntityJoinLevelEvent event){
        if(event.getLevel() instanceof ServerLevel l&&soldier(event.getEntity())&&!owned(event.getEntity(),l)&&!corpse(event.getEntity())){
            event.setCanceled(true);event.getEntity().discard();
        }
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    static boolean spawn(ServerLevel l,CampaignData d,BlockPos pos,boolean warn){
        if(count(d.raidTier,d.wavePlayers)==0||!d.phase.equals("raid"))return false;
        try{
            Class specialty=Class.forName("su.uTa4u.specialforces.Specialty");
            Class<?> swat=Class.forName("su.uTa4u.specialforces.entities.SwatEntity");
            String role=d.raidTier>=7&&d.spawnRemaining==1?"BULLDOZER":d.spawnRemaining>1?"SNIPER":"ASSAULTER";
            Mob mob=(Mob)swat.getMethod("withSpecialty",Level.class,specialty).invoke(null,l,Enum.valueOf(specialty,role));
            mob.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,l.random.nextFloat()*360,0);
            mob.getPersistentData().putBoolean("arsenalRaider",true);mob.getPersistentData().putBoolean("arsenalSpecialForces",true);
            mob.getPersistentData().putString("arsenalSpecialForcesRole",role);
            mob.finalizeSpawn(l,l.getCurrentDifficultyAt(pos),MobSpawnType.EVENT,null,null);
            mob.setPersistenceRequired();mob.setCanPickUpLoot(false);
            var health=mob.getAttribute(Attributes.MAX_HEALTH);if(health!=null){health.removeModifiers();health.setBaseValue(role.equals("BULLDOZER")?110:56+Math.max(0,Math.min(10,d.raidTier))*2);mob.setHealth(mob.getMaxHealth());}
            var armor=mob.getAttribute(Attributes.ARMOR);if(armor!=null){armor.removeModifiers();armor.setBaseValue(role.equals("BULLDOZER")?8:3);}
            var toughness=mob.getAttribute(Attributes.ARMOR_TOUGHNESS);if(toughness!=null){toughness.removeModifiers();toughness.setBaseValue(0);}
            mob.setGlowingTag(Rules.highlightAttackers(d.reconnaissance,d.waveTicks));
            if(!l.noCollision(mob))return false;
            d.raiders.add(mob.getUUID()); // The join guard verifies ownership before allowing insertion.
            if(!l.addFreshEntity(mob)){d.raiders.remove(mob.getUUID());return false;}
            balance(mob,d.raidTier);mob.setHealth(mob.getMaxHealth());
            d.setDirty();
            if(warn)for(var player:l.players())if(ArsenalBeacon.near(player,d)){
                player.sendSystemMessage(Component.literal("[Create Arsenal] SPECIAL FORCES INCOMING! Get behind sandbags and prepare for a shootout!"));
                player.connection.send(new ClientboundSetTitlesAnimationPacket(10,100,20));
                player.connection.send(new ClientboundSetTitleTextPacket(Component.literal("SPECIAL FORCES INCOMING")));
                player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("Get behind sandbags. Prepare for a shootout!")));
            }
            return true;
        }catch(ReflectiveOperationException ex){throw new IllegalStateException("Installed Special Forces API changed",ex);}
    }
    private static java.lang.reflect.Method stateGetter,stateSetter;
    static int nativeState(Entity e){
        if(!soldier(e))return 0;
        try{if(stateGetter==null){stateGetter=e.getClass().getMethod("getState");stateSetter=e.getClass().getDeclaredMethod("setState",byte.class);stateSetter.setAccessible(true);}return ((Number)stateGetter.invoke(e)).intValue();}
        catch(ReflectiveOperationException ex){throw new IllegalStateException("Cannot read Special Forces defeat state",ex);}
    }
    static boolean defeated(Entity e){return soldier(e)&&nativeState(e)!=0;}
    static boolean corpse(Entity e){return soldier(e)&&e.getPersistentData().getBoolean("arsenalSpecialForces")&&e.getPersistentData().getBoolean("arsenalCorpse")&&defeated(e);}
    static void retire(Entity e,ServerLevel l){
        if(!(e instanceof Mob mob)||!defeated(e))return;
        try{stateSetter.invoke(e,(byte)2);}catch(ReflectiveOperationException ex){throw new IllegalStateException("Cannot preserve defeated soldier corpse",ex);}
        // Native DEAD bodies stay lootable at one HP. No revival, targeting, glow or breach goals.
        mob.setHealth(1);mob.setTarget(null);mob.getNavigation().stop();mob.setNoAi(true);mob.setInvulnerable(true);mob.setGlowingTag(false);
        mob.getPersistentData().putBoolean("arsenalCorpse",true);mob.getPersistentData().putBoolean("arsenalRaider",false);
        if(!mob.getPersistentData().contains("arsenalCorpseUntil"))mob.getPersistentData().putLong("arsenalCorpseUntil",l.getGameTime()+12000);
    }
    static void cleanup(ServerLevel l){
        if(!installed())return; // runs every second over every entity of the Overworld: skip it when no soldier can exist
        for(Entity e:l.getAllEntities())if(corpse(e)&&l.getGameTime()>=e.getPersistentData().getLong("arsenalCorpseUntil"))e.discard();
    }
    static void balance(Mob mob,int tier){
        boolean heavy=mob.getPersistentData().getString("arsenalSpecialForcesRole").equals("BULLDOZER");
        var health=mob.getAttribute(Attributes.MAX_HEALTH);if(health!=null){float current=mob.getHealth();health.removeModifiers();health.setBaseValue(heavy?110:56+Math.max(0,Math.min(10,tier))*2);mob.setHealth(Math.min(current,mob.getMaxHealth()));}
        var armor=mob.getAttribute(Attributes.ARMOR);if(armor!=null){armor.removeModifiers();armor.setBaseValue(heavy?8:3);}
        var toughness=mob.getAttribute(Attributes.ARMOR_TOUGHNESS);if(toughness!=null){toughness.removeModifiers();toughness.setBaseValue(0);}
    }
}
