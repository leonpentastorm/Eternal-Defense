package dev.createarsenal.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.loading.FMLPaths;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Server logic of the base support system: validation, payment, announcements, cannon fire, delivery and return. */
final class SupportCalls {
    private SupportCalls(){}
    enum Kind{
        SUPPLY("supply"),RETURN("return"),FIRE("fire");
        final String id;Kind(String id){this.id=id;}
    }
    static final String COSTS_FILE="arsenal-beacon-support-costs.txt";

    // ---- prices ---------------------------------------------------------------------------------
    static SupportCosts.Entry entry(ItemStack s){
        var id=BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
        return new SupportCosts.Entry(id,s.getCount(),s.getRarity().ordinal());
    }
    private static long overridesStamp=-1;private static Map<String,Double> overridesCache=Map.of();
    /** Admin overrides; created from a documented template on first use and re-read when edited. */
    static Map<String,Double> overrides(){
        Path file=FMLPaths.CONFIGDIR.get().resolve(COSTS_FILE);
        try{
            if(!Files.exists(file))Files.writeString(file,"""
                # Ardent Energy price of items in a supply drop
                #
                # item_id [x count] = energy       (the price is per count: 'minecraft:arrow x16 = 4' is 0.25 each)
                #   minecraft:diamond = 6
                #   minecraft:arrow x16 = 4
                # Items not listed use the built-in estimate: rarity-based, bulk blocks a tenth of that.
                # Applies the next time a Support Platform menu is opened or a flare is thrown.
                """);
            long stamp=Files.getLastModifiedTime(file).toMillis();
            if(stamp!=overridesStamp){
                var result=TextTable.parse(Files.readAllLines(file),id->{var rl=ResourceLocation.tryParse(id);return rl!=null&&BuiltInRegistries.ITEM.containsKey(rl);},4096,100000);
                for(String p:result.problems())com.mojang.logging.LogUtils.getLogger().warn("{}: {}",COSTS_FILE,p);
                overridesCache=SupportCosts.overridesFrom(result.rows());overridesStamp=stamp;
            }
        }catch(IOException ex){com.mojang.logging.LogUtils.getLogger().warn("Cannot read {}",COSTS_FILE,ex);}
        return overridesCache;
    }
    static int supplyCost(SupportData d){
        var entries=new ArrayList<SupportCosts.Entry>();for(var s:d.activeStacks())entries.add(entry(s));
        return SupportCosts.cost(entries,overrides());
    }
    static int callPrice(Kind k,SupportData d){return switch(k){case SUPPLY->supplyCost(d);case RETURN->SupportRules.RETURN_CALL_PRICE;case FIRE->SupportRules.FIRE_CALL_PRICE;};}

    // ---- validation -------------------------------------------------------------------------------
    /** Null when the call may go ahead, otherwise a language key suffix describing what is missing. */
    static String problem(ServerPlayer p,Kind kind){
        var level=p.serverLevel();var d=SupportData.get(level);
        if(!d.hasPlatform())return "no_platform";
        if(d.connectedCannons().isEmpty())return "no_cannon";
        if(kind==Kind.SUPPLY&&supplyCost(d)==0)return "empty_grid";
        if(ArdentEnergy.balance(p)<callPrice(kind,d)&&!p.isCreative())return "no_energy";
        return null;
    }
    static void refuse(ServerPlayer p,String problem,Kind kind){
        var d=SupportData.get(p.serverLevel());
        p.displayClientMessage(Component.translatable("gui.arsenal_beacon.support.refused."+problem,callPrice(kind,d)),true);
    }

    // ---- cannons ------------------------------------------------------------------------------------
    /** Turns every connected, loaded cannon toward {@code target} and fires it. */
    static void cannonFire(ServerLevel any,Vec3 target,boolean fire){
        var overworld=any.getServer().overworld();var d=SupportData.get(overworld);
        for(BlockPos pos:d.connectedCannons()){
            if(!overworld.isLoaded(pos))continue;
            if(overworld.getBlockEntity(pos) instanceof SupportCannon.CannonEntity be){
                be.aim(overworld,Math.atan2(-(target.x-(pos.getX()+.5)),-(target.z-(pos.getZ()+.5))));
                if(fire)be.fire(overworld);
            }
        }
    }
    static void announce(ServerPlayer caller,Kind kind){BeaconNetwork.announce(caller.serverLevel(),"support",0,0,caller.getGameProfile().getName(),kind.id);}

    // ---- supply drops ---------------------------------------------------------------------------------
    /** Validates and pays; returns the snapshot to deliver, or null after telling the player why not. */
    static ListTag beginSupply(ServerPlayer p){
        String problem=problem(p,Kind.SUPPLY);if(problem!=null){refuse(p,problem,Kind.SUPPLY);return null;}
        var d=SupportData.get(p.serverLevel());int cost=supplyCost(d);
        if(!ArdentEnergy.spend(p,cost)){refuse(p,"no_energy",Kind.SUPPLY);return null;}
        var snapshot=new ListTag();for(var s:d.activeStacks())snapshot.add(s.save(new CompoundTag()));
        announce(p,Kind.SUPPLY);cannonFire(p.serverLevel(),p.position(),true);
        if(!p.isCreative())p.displayClientMessage(Component.translatable("gui.arsenal_beacon.support.paid",cost),true);
        return snapshot;
    }
    /** Drops a parcel from the sky when the caller is above ground with open sky over the flare, else hands it over at their feet. */
    static void deliver(ServerLevel level,ServerPlayer caller,Vec3 flare,ListTag snapshot){
        boolean outside=caller!=null&&level.canSeeSky(caller.blockPosition());
        BlockPos base=BlockPos.containing(flare);int clear=0;
        for(int dy=1;dy<=SupportRules.PARCEL_DROP_HEIGHT&&base.getY()+dy<level.getMaxBuildHeight();dy++){if(!level.getBlockState(base.above(dy)).getCollisionShape(level,base.above(dy)).isEmpty())break;clear=dy;}
        var parcel=new SupportCrate.ParcelEntity(ArsenalBeacon.PARCEL.get(),level);
        parcel.setContents(snapshot);
        if(outside&&clear>=8){
            parcel.setPos(flare.x,base.getY()+clear-0.5,flare.z);parcel.setFalling(true);
            level.sendParticles(ParticleTypes.CLOUD,flare.x,flare.y+1,flare.z,12,.3,.2,.3,.02);
        }else{
            Vec3 at=caller!=null?caller.position().add(caller.getLookAngle().multiply(1.5,0,1.5)):flare;
            parcel.setPos(at.x,at.y,at.z);parcel.setFalling(false);
            level.sendParticles(ParticleTypes.END_ROD,at.x,at.y+.5,at.z,18,.3,.4,.3,.05);
            level.playSound(null,at.x,at.y,at.z,SoundEvents.ENDERMAN_TELEPORT,SoundSource.PLAYERS,.8f,1.4f);
        }
        level.addFreshEntity(parcel);
    }

    // ---- return flare -----------------------------------------------------------------------------------
    static Vec3 returnSpot(ServerLevel level,SupportData d){
        BlockPos platform=d.platform;
        BlockPos front=platform.relative(d.facing);
        for(BlockPos c:new BlockPos[]{front,front.above(),platform.above(2),front.relative(d.facing)}){
            if(level.getBlockState(c).getCollisionShape(level,c).isEmpty()&&level.getBlockState(c.above()).getCollisionShape(level,c.above()).isEmpty())return Vec3.atBottomCenterOf(c);
        }
        return Vec3.atBottomCenterOf(platform.above());
    }
    static void finishReturn(ServerPlayer p){
        String problem=problem(p,Kind.RETURN);if(problem!=null){refuse(p,problem,Kind.RETURN);return;}
        if(!ArdentEnergy.spend(p,SupportRules.RETURN_CALL_PRICE)){refuse(p,"no_energy",Kind.RETURN);return;}
        var home=p.getServer().overworld();var d=SupportData.get(home);Vec3 spot=returnSpot(home,d);
        announce(p,Kind.RETURN);cannonFire(home,p.position(),true);
        var from=p.serverLevel();from.sendParticles(ParticleTypes.PORTAL,p.getX(),p.getY()+1,p.getZ(),40,.4,.8,.4,.2);
        p.teleportTo(home,spot.x,spot.y,spot.z,d.facing.toYRot(),0);
        home.sendParticles(ParticleTypes.END_ROD,spot.x,spot.y+1,spot.z,30,.4,.8,.4,.05);
        home.playSound(null,spot.x,spot.y,spot.z,SoundEvents.ENDERMAN_TELEPORT,SoundSource.PLAYERS,1f,1f);
        if(!p.isCreative())p.displayClientMessage(Component.translatable("gui.arsenal_beacon.support.paid",SupportRules.RETURN_CALL_PRICE),true);
    }
}
