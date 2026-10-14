package dev.eternaldefense.beacon;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import java.util.*;

/**
 * Forward Operating Bases, slice 3 (0.0.25), on the server: the SOS (one clock for the server, one call at a time), its response timer and
 * raid, the records of villagers lost in raids at a FOB, and the repair that brings them back. The pure rules are {@link SosRules}.
 * <p>The SOS clock and the response timer count ticks of play ({@link FobData#onlineTicks}), so nothing runs out while nobody is online. A FOB
 * whose chunks are not loaded when it calls is not a problem: the call is only a timer, and its raid starts when a player stands in its village.
 */
final class FobSos {
    private FobSos(){}
    private static final Random RANDOM=new Random();
    /** Map tag: the SOS serial a map belongs to (maps of an SOS that ended burn). */
    static final String MAP_TAG="eternalSos";
    /** Seconds in a row a player has stood in the calling village with its villagers loaded (one SOS at a time). */
    private static int settled;

    // ---- once a second ----------------------------------------------------------------------------------------------------------------------------
    static void second(ServerLevel l,FobData d){
        var campaign=CampaignData.get(l.getServer().overworld());
        boolean homeRaid=campaign.active(),homeWarning=homeWarning(campaign);
        var open=d.sosFob==0?null:d.byId(d.sosFob);
        if(d.sosFob!=0&&(open==null||open.state!=FobRules.State.ACTIVE)){close(l,d,open,SosRules.End.REMOVED);open=null;}
        if(open!=null){
            if(SosRules.responseCounts(homeRaid,open.raidRunning())){
                d.sosLeft=Math.max(0,d.sosLeft-20);d.setDirty();
                if(d.sosLeft==0){close(l,d,open,SosRules.End.EXPIRED);return;}
            }
            if(!open.raidRunning()){
                // 0.0.32: the village must stay loaded for SosRules.ARRIVE_SECONDS with a player in it (entities load after the land: a player
                // teleported or riding in started the raid before its villagers existed)
                net.minecraft.server.level.ServerPlayer here=null;
                for(var p:l.getServer().getPlayerList().getPlayers())if(arrived(l,open,p)){here=p;break;}
                settled=here!=null&&villagersLoaded(l,open)?settled+1:0;
                if(here!=null&&settled>=SosRules.ARRIVE_SECONDS){settled=0;arrive(l,d,open,here,homeRaid);}
            }else settled=0;
            return;
        }
        int active=d.count(FobRules.State.ACTIVE);
        if(!SosRules.counts(active,false,homeRaid,homeWarning))return;
        if(d.sosClock<=0)d.sosClock=SosRules.delay(RANDOM.nextDouble());
        d.sosClock=Math.max(0,d.sosClock-20);d.setDirty();
        if(d.sosClock==0)fire(l,d,null);
    }
    /** The Home raid's warning day (the same window that locks Tactical Operations): only while a beacon waits for its next raid. */
    static boolean homeWarning(CampaignData c){
        return c.installed()&&c.phase.equals("preparation")&&TacticalRules.raidWarning(TacticalRules.untilRaid(c.rewardTier,c.preparationTicks,c.respiteTicks));
    }
    /**
     * 0.0.31: the village's entities are loaded (they load a moment after its land): an SOS answered by a player who just arrived started its
     * raid counting no villagers, so losing them never put the FOB DOWN and none was recorded for the repair.
     */
    static boolean villagersLoaded(ServerLevel l,FobData.Fob f){
        if(!l.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(f.pos)))return false;
        // a column whose land is loaded must have its entities too (land not loaded holds no villager that could count)
        for(long column:f.zoneColumns)if(l.hasChunk(net.minecraft.world.level.ChunkPos.getX(column),net.minecraft.world.level.ChunkPos.getZ(column))&&!l.areEntitiesLoaded(column))return false;
        return true;
    }
    static boolean arrived(ServerLevel l,FobData.Fob f,ServerPlayer p){
        return p.level()==l&&p.isAlive()&&!p.isSpectator()&&FobZone.contains(FobZone.of(l,f),p.blockPosition());
    }

    // ---- the call ---------------------------------------------------------------------------------------------------------------------------------
    /** Opens an SOS at {@code chosen} (an operator's), or at a random ACTIVE FOB. Returns the FOB, or null when none can call. */
    static FobData.Fob fire(ServerLevel l,FobData d,FobData.Fob chosen){
        if(d.sosFob!=0)return null;
        var active=new ArrayList<FobData.Fob>();for(var f:d.fobs.values())if(f.state==FobRules.State.ACTIVE)active.add(f);
        int index=SosRules.pick(active.size(),RANDOM.nextDouble());
        var f=chosen!=null?chosen:index<0?null:active.get(index);
        if(f==null||f.state!=FobRules.State.ACTIVE){d.sosClock=0;return null;}
        d.sosFob=f.id;d.sosLeft=SosRules.RESPONSE_TICKS;d.sosSerial++;d.sosMap=null;d.sosClock=0;d.setDirty();
        Fobs.announce(l,Component.translatable("gui.eternal_defense.fob.sos.called",f.name,time(SosRules.RESPONSE_TICKS)).withStyle(ChatFormatting.RED));
        for(var p:l.getServer().getPlayerList().getPlayers()){handout(p,d);p.playNotifySound(net.minecraft.sounds.SoundEvents.BELL_BLOCK,net.minecraft.sounds.SoundSource.PLAYERS,1f,.7f);}
        log("SOS called at {} ({} ticks to answer)",f.name,d.sosLeft);
        Fobs.refreshScreens(l,f);
        return f;
    }
    /** A player stands in the calling FOB's village: the raid starts with their Bad Omen I, unless the Home raid comes first. */
    static void arrive(ServerLevel l,FobData d,FobData.Fob f,ServerPlayer p,boolean homeRaid){
        switch(SosRules.arrival(homeRaid,f.raidRunning(),Fobs.raidsPossible(l))){
            case HOME_RAID->{Fobs.say(p,"fob.sos.home_raid",f.name);return;}
            case NO_RAIDS->{Fobs.say(p,"fob.raid.impossible");return;}
            case RAID_RUNNING->{return;}
            default->{}
        }
        p.removeEffect(MobEffects.BAD_OMEN);   // a Bad Omen they carry is replaced, never stacked
        p.addEffect(new MobEffectInstance(MobEffects.BAD_OMEN,600,SosRules.OMEN_AMPLIFIER,false,false,true));
        var raid=l.getRaids().createOrExtendRaid(p);
        if(raid==null){p.removeEffect(MobEffects.BAD_OMEN);Fobs.say(p,"fob.raid.impossible");return;}
        f.raidId=raid.getId();f.raidVillagers=Fobs.tagVillagers(l,f);d.setDirty();
        Fobs.announce(l,Component.translatable("gui.eternal_defense.fob.sos.raid",p.getDisplayName(),f.name,f.raidVillagers,(f.raidVillagers+1)/2));
        Fobs.horn(l,f);   // 0.0.34 (owner): the war horn
        log("{}: SOS raid {} started by {}",f.name,raid.getId(),p.getGameProfile().getName());
        Fobs.refreshScreens(l,f);
    }
    /** The raid at a FOB ended ({@link Fobs#end}): if it was the SOS's, the SOS ends with it (a raid with no result leaves the SOS open). */
    static boolean raidEnded(ServerLevel l,FobData.Fob f,FobRules.Outcome outcome){
        var d=FobData.get(l);if(d.sosFob!=f.id)return false;
        if(outcome==FobRules.Outcome.WON){close(l,d,f,SosRules.End.WON);return true;}
        if(outcome==FobRules.Outcome.LOST){close(l,d,f,SosRules.End.LOST);return true;}
        Fobs.announce(l,Component.translatable("gui.eternal_defense.fob.sos.again",f.name,time(d.sosLeft)));
        return true;
    }
    /** The FOB was taken back with the shovel (or its block is gone): its SOS is called off and the clock starts again. */
    static void removed(ServerLevel l,FobData.Fob f){var d=FobData.get(l);if(d.sosFob==f.id)close(l,d,f,SosRules.End.REMOVED);}
    /** Ends the open SOS: the FOB's new state, everyone told, the maps burnt, a new clock. */
    static void close(ServerLevel l,FobData d,FobData.Fob f,SosRules.End end){
        d.sosFob=0;d.sosLeft=0;d.sosMap=null;d.sosClock=SosRules.delay(RANDOM.nextDouble());d.setDirty();
        if(f!=null){
            var before=f.state;f.state=SosRules.after(f.state,end);
            if(f.state!=before)log("{}: {} -> {} ({})",f.name,before,f.state,end);
            switch(end){
                case WON->Fobs.announce(l,Component.translatable("gui.eternal_defense.fob.sos.won",f.name));
                case LOST->Fobs.announce(l,Component.translatable("gui.eternal_defense.fob.sos.lost",f.name));
                case EXPIRED->Fobs.announce(l,Component.translatable("gui.eternal_defense.fob.sos.expired",f.name));
                default->Fobs.announce(l,Component.translatable("gui.eternal_defense.fob.sos.off",f.name));
            }
            Fobs.refreshScreens(l,f);
        }
        for(var p:l.getServer().getPlayerList().getPlayers())burn(p,d);
    }

    // ---- the map ----------------------------------------------------------------------------------------------------------------------------------
    /** The SOS map: a vanilla filled map with the base and a red X on the calling FOB, named after it (made once per SOS, copied after). */
    static ItemStack map(ServerLevel l,FobData d,FobData.Fob f){
        if(d.sosMap==null||d.sosMap.getCompound("tag").getLong(MAP_TAG)!=d.sosSerial){
            var campaign=CampaignData.get(l.getServer().overworld());
            int bx=campaign.installed()?campaign.beacon.getX():f.pos.getX(),bz=campaign.installed()?campaign.beacon.getZ():f.pos.getZ();
            int scale=TacticalRules.mapScale(bx,bz,f.pos.getX(),f.pos.getZ());
            var map=Hunts.centredMap(l,TacticalRules.mapCentre(bx,f.pos.getX()),TacticalRules.mapCentre(bz,f.pos.getZ()),scale);
            MapItem.renderBiomePreviewMap(l,map);
            MapItemSavedData.addTargetDecoration(map,f.pos,"+",MapDecoration.Type.RED_X);
            map.setHoverName(Component.translatable("gui.eternal_defense.fob.sos.map",f.name).withStyle(ChatFormatting.RED));
            map.getOrCreateTag().putLong(MAP_TAG,d.sosSerial);
            d.sosMap=map.save(new CompoundTag());d.setDirty();
        }
        return ItemStack.of(d.sosMap);
    }
    /** Hands a player the SOS map, if one is open and they carry none (every time they need one: at the call and when they log in). */
    static void handout(ServerPlayer p,FobData d){
        var l=p.server.overworld();var f=d.sosFob==0?null:d.byId(d.sosFob);if(f==null)return;
        if(!carries(p,d.sosSerial))Hunts.give(p,map(l,d,f));
    }
    static long serial(ItemStack s){return s.is(net.minecraft.world.item.Items.FILLED_MAP)&&s.hasTag()?s.getTag().getLong(MAP_TAG):0;}
    static boolean carries(ServerPlayer p,long serial){
        for(var s:p.getInventory().items)if(serial(s)==serial)return true;
        return serial(p.getOffhandItem())==serial;
    }
    /** Burns the SOS maps a player carries that belong to no open SOS. */
    static void burn(ServerPlayer p,FobData d){
        int burned=0;
        for(var list:List.of(p.getInventory().items,p.getInventory().offhand))
            for(int i=0;i<list.size();i++){long id=serial(list.get(i));if(id!=0&&(d.sosFob==0||id!=d.sosSerial)){list.set(i,ItemStack.EMPTY);burned++;}}
        if(burned>0){
            p.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME,p.getX(),p.getY()+1,p.getZ(),10,.3,.4,.3,.02);
            p.playNotifySound(net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH,net.minecraft.sounds.SoundSource.PLAYERS,.6f,1f);
        }
    }
    static void login(ServerPlayer p){var d=FobData.get(p.server.overworld());burn(p,d);handout(p,d);}

    // ---- villagers lost in a raid --------------------------------------------------------------------------------------------------------------------
    /** A FOB's villager died while a raid ran at it: kept for a repair (name, profession, level and its saved data, trades and experience). */
    static void lost(ServerLevel l,FobData.Fob f,Villager v){
        var r=new CompoundTag();
        var name=v.hasCustomName()?v.getCustomName().getString():SosRules.villagerName(v.getUUID().getMostSignificantBits(),v.getUUID().getLeastSignificantBits());
        r.putString("name",name);r.putString("profession",net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().getProfession()).toString());
        r.putInt("level",v.getVillagerData().getLevel());r.putBoolean("baby",v.isBaby());
        r.put("data",v.saveWithoutId(new CompoundTag()));
        f.deadVillagers.add(r);while(f.deadVillagers.size()>SosRules.MAX_RECORDS)f.deadVillagers.remove(0);
        FobData.get(l).setDirty();
        log("{}: {} ({} {}) lost in the raid",f.name,name,r.getString("profession"),r.getInt("level"));
    }
    /** What a repair costs now: the base price when DOWN plus each lost villager's own price ({@link Economy#fobRepairVillager}). */
    static int cost(FobData.Fob f){
        var each=new int[f.deadVillagers.size()];
        for(int i=0;i<each.length;i++)each[i]=Economy.fobRepairVillager(f.deadVillagers.getCompound(i).getInt("level"));
        return SosRules.repairCost(f.state==FobRules.State.DOWN,Economy.fobRepairBase(),each);
    }
    /**
     * The repair, from the FOB screen: paid in Ardent Energy, the lost villagers come back from their records (with their trades and
     * experience, beside the FOB Beacon), and a DOWN FOB is ACTIVE again, or PLANTED with its activation raid called again if it never won one.
     * Returns the message key for the screen.
     */
    static String repair(ServerPlayer p,FobData.Fob f){
        var l=p.serverLevel();var d=FobData.get(l);
        if(!SosRules.mayRepair(f.state,f.deadVillagers.size(),f.raidRunning()))return "fob.result.no_repair";
        int price=cost(f);
        if(!ArdentEnergy.spend(Supply.pack(p),price))return "fob.result.no_energy";   // 0.0.40: a FOB is outside the Home Zone: the pack alone
        int back=0;
        for(int i=0;i<f.deadVillagers.size();i++)if(respawn(l,f,f.deadVillagers.getCompound(i)))back++;
        f.deadVillagers=new net.minecraft.nbt.ListTag();
        var before=f.state;f.state=SosRules.repaired(f.state,f.activated);d.setDirty();
        if(back>0)Fobs.tagVillagers(l,f);
        Fobs.announce(l,Component.translatable(before==FobRules.State.DOWN?"gui.eternal_defense.fob.repaired":"gui.eternal_defense.fob.villagers_back",p.getDisplayName(),f.name,back));
        log("{}: repaired by {} for {} Ardent ({} villagers back, {} -> {})",f.name,p.getGameProfile().getName(),price,back,before,f.state);
        if(f.state==FobRules.State.PLANTED){String problem=Fobs.callRaid(l,f,p);return problem==null?"fob.result.repaired_raid":"fob.raid."+problem;}
        return before==FobRules.State.DOWN?"fob.result.repaired":"fob.result.villagers_back";
    }
    /** One villager back from its record, on free ground beside the FOB Beacon; its saved data without what belonged to the dead body. */
    static boolean respawn(ServerLevel l,FobData.Fob f,CompoundTag record){
        var v=EntityType.VILLAGER.create(l);if(v==null)return false;
        var data=record.getCompound("data").copy();
        for(String key:List.of("UUID","Pos","Motion","Rotation","Health","DeathTime","HurtTime","HurtByTimestamp","Fire","Air","FallDistance","ActiveEffects","Brain","Leash","Passengers","SleepingX","SleepingY","SleepingZ","AbsorptionAmount"))data.remove(key);
        try{v.load(data);}catch(RuntimeException ex){log("{}: a lost villager's record could not be read ({}); a plain villager comes instead",f.name,ex.toString());v=EntityType.VILLAGER.create(l);if(v==null)return false;}
        v.setHealth(v.getMaxHealth());
        var at=spot(l,f.pos);v.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,RANDOM.nextFloat()*360f,0f);
        v.getPersistentData().putLong(Fobs.VILLAGER_TAG,f.id);
        if(!l.addFreshEntity(v))return false;
        l.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,v.getX(),v.getY()+1,v.getZ(),8,.3,.5,.3,0);
        return true;
    }
    /** Free ground two to five blocks from the FOB Beacon (the FOB Beacon's own block if there is none). */
    static BlockPos spot(ServerLevel l,BlockPos fob){
        for(int r=2;r<=5;r++)for(var dir:net.minecraft.core.Direction.Plane.HORIZONTAL)for(int dy:new int[]{0,1,-1,2,-2}){
            var c=fob.relative(dir,r).above(dy);if(SupportCalls.standable(l,c))return c;}
        return fob.above(2);
    }

    // ---- the screens ------------------------------------------------------------------------------------------------------------------------------
    /** The SOS and repair parts of a FOB screen's state. */
    static void state(FobData d,FobData.Fob f,CompoundTag n){
        if(d.sosFob==f.id){n.putBoolean("sos",true);n.putLong("sosLeft",d.sosLeft);}
        var lost=new net.minecraft.nbt.ListTag();
        for(int i=0;i<f.deadVillagers.size();i++){
            var r=f.deadVillagers.getCompound(i);var row=new CompoundTag();
            row.putString("name",r.getString("name"));row.putString("profession",r.getString("profession"));row.putInt("level",r.getInt("level"));
            row.putInt("cost",Economy.fobRepairVillager(r.getInt("level")));lost.add(row);
        }
        n.put("lost",lost);n.putInt("repairBase",f.state==FobRules.State.DOWN?Economy.fobRepairBase():0);n.putInt("repairTotal",cost(f));
        n.putBoolean("mayRepair",SosRules.mayRepair(f.state,f.deadVillagers.size(),f.raidRunning()));
    }
    /** The SOS line of every player's status card while one is open. */
    static void status(FobData d,CompoundTag n){
        var f=d.sosFob==0?null:d.byId(d.sosFob);if(f==null)return;
        var s=new CompoundTag();s.putString("name",f.name);s.putLong("left",d.sosLeft);s.putBoolean("raid",f.raidRunning());n.put("fobSos",s);
    }
    /** "6 d 23 h" (days of play) for the texts. */
    static Component time(long ticks){
        long hours=Math.max(0,ticks)/1000;   // a Minecraft hour is 1,000 ticks
        return hours>=24?Component.translatable("gui.eternal_defense.fob.sos.days",hours/24,hours%24):Component.translatable("gui.eternal_defense.fob.sos.hours",Math.max(1,hours));
    }
    static String describe(ServerLevel l,FobData d){
        var f=d.sosFob==0?null:d.byId(d.sosFob);
        if(f!=null)return "SOS open at "+f.name+": "+d.sosLeft+" ticks of play to answer"+(f.raidRunning()?", its raid running":"");
        return d.sosClock>0?"Next SOS in "+d.sosClock+" ticks of play (counts only with an ACTIVE FOB and no Home raid or raid warning)":"No SOS clock running (no ACTIVE FOB yet)";
    }
    private static void log(String text,Object... args){com.mojang.logging.LogUtils.getLogger().info("[fob] "+text,args);}
}
