package dev.createarsenal.beacon;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.UUID;

/** Server logic of the base support system: validation, announcements, cannon lookups, parcel delivery and the trip home. */
final class SupportCalls {
    private SupportCalls(){}
    enum Kind{
        SUPPLY("supply"),RETURN("return"),FIRE("fire");
        final String id;Kind(String id){this.id=id;}
        static Kind of(int ordinal){var v=values();return v[Math.max(0,Math.min(v.length-1,ordinal))];}
    }

    // ---- lookups ----------------------------------------------------------------------------------
    /** The player's platform block entity, loading its chunk if needed. Null when they have none (or it is gone). */
    static SupportPlatform.PlatformEntity platform(ServerLevel any,UUID owner){
        var overworld=any.getServer().overworld();var base=SupportData.get(overworld).of(owner);
        if(base==null||base.platform==null)return null;
        overworld.getChunkAt(base.platform);
        return overworld.getBlockEntity(base.platform) instanceof SupportPlatform.PlatformEntity be&&overworld.getBlockState(base.platform).is(ArsenalBeacon.SUPPORT_PLATFORM.get())?be:null;
    }
    /** The player's cannon when its chunk is loaded. */
    static SupportCannon.CannonEntity cannon(ServerLevel any,UUID owner){
        var overworld=any.getServer().overworld();var base=SupportData.get(overworld).of(owner);
        if(base==null||base.cannon==null||!overworld.isLoaded(base.cannon))return null;
        return overworld.getBlockEntity(base.cannon) instanceof SupportCannon.CannonEntity c?c:null;
    }
    /** The cannon that can really be seen turning: loaded and ticking in the Overworld. Otherwise calls use the same timing without the visible turret. */
    static SupportCannon.CannonEntity liveCannon(ServerLevel at,UUID owner){
        if(at.dimension()!=Level.OVERWORLD)return null;
        var c=cannon(at,owner);
        return c!=null&&at.isPositionEntityTicking(c.getBlockPos())?c:null;
    }

    // ---- validation -------------------------------------------------------------------------------
    /** Null when the call may go ahead, otherwise a language key suffix under {@code support.refused.} describing what is wrong. */
    static String problem(ServerPlayer p,Kind kind){
        var overworld=p.getServer().overworld();
        var base=SupportData.get(overworld).of(p.getUUID());
        if(kind!=Kind.RETURN&&p.level().dimension()!=Level.OVERWORLD){
            // only fire support can be called in another dimension, and only with the Dimensional Link upgrade
            if(kind!=Kind.FIRE||base==null||base.up[CannonUpgrades.Upgrade.DIMENSION.ordinal()]<=0)return "wrong_dimension";
        }
        var platform=platform(overworld,p.getUUID());
        if(platform==null)return "no_platform";
        if(BaseZone.problem(overworld,platform.getBlockPos())!=null)return "disabled";
        if(kind!=Kind.RETURN){
            if(base==null||base.cannon==null)return "no_cannon";
            overworld.getChunkAt(base.cannon);   // a base far from every player (or a player in another dimension) still has its cannon
            if(!overworld.getBlockState(base.cannon).is(ArsenalBeacon.SUPPORT_CANNON.get()))return "no_cannon";
            if(BaseZone.problem(overworld,base.cannon)!=null)return "disabled";
        }
        if(kind==Kind.SUPPLY&&!platform.hasItems())return "empty_grid";
        if(kind==Kind.RETURN&&returnSpot(overworld,platform.getBlockPos(),platform.getBlockState().getValue(SupportPlatform.FACING))==null)return "return_blocked";
        return null;
    }
    /** Tells the player why nothing happened. Their flare is never used up when a call is refused. */
    static void refuse(ServerPlayer p,String problem){
        var text=Component.translatable("gui.arsenal_beacon.support.refused."+problem).withStyle(ChatFormatting.RED);
        p.sendSystemMessage(text);p.displayClientMessage(text,true);
    }
    /** A short status line (chat and action bar) under {@code support.notice.}; used for what the cannon is doing and for a barrage already under way. */
    static void notice(ServerPlayer p,String key,ChatFormatting color){
        if(p==null)return;
        var text=Component.translatable("gui.arsenal_beacon.support.notice."+key).withStyle(color);
        p.sendSystemMessage(text);p.displayClientMessage(text,true);
    }
    /** One extra grey chat line (no action bar) under {@code support.notice.}. */
    static void noticeDetail(ServerPlayer p,String key){
        if(p!=null)p.sendSystemMessage(Component.translatable("gui.arsenal_beacon.support.notice."+key).withStyle(ChatFormatting.GRAY));
    }
    /** True when the player is too far from their cannon (or in another dimension) to see or hear it. */
    static boolean farFromCannon(ServerPlayer p,ServerLevel overworld,BlockPos cannon){
        if(p==null||cannon==null)return false;
        if(p.level()!=overworld)return true;
        double dx=p.getX()-(cannon.getX()+.5),dz=p.getZ()-(cannon.getZ()+.5);
        return dx*dx+dz*dz>(double)SupportRules.CANNON_HEARING*SupportRules.CANNON_HEARING;
    }
    static void announce(ServerPlayer caller,Kind kind){announce(caller,kind.id);}
    /** Everyone sees who is calling what: a centre-screen toast and one chat line (for fire support, the caller's own chosen type). */
    static void announce(ServerPlayer caller,String what){
        BeaconNetwork.announce(caller.serverLevel(),"support",0,0,caller.getGameProfile().getName(),what);
        caller.getServer().getPlayerList().broadcastSystemMessage(Component.translatable("gui.arsenal_beacon.support.called",caller.getDisplayName(),Component.translatable("gui.arsenal_beacon.support.name."+what)).withStyle(ChatFormatting.GOLD),false);
    }

    // ---- supply drops ---------------------------------------------------------------------------------
    /** Takes everything out of the platform's grid and returns it as a saveable list. */
    static ListTag takeGrid(ServerPlayer p){
        var platform=platform(p.serverLevel(),p.getUUID());var list=new ListTag();
        if(platform!=null)for(ItemStack s:platform.takeAll())list.add(s.save(new CompoundTag()));
        return list;
    }
    /** Open blocks above {@code at}, up to the drop height. */
    static int clearAbove(ServerLevel level,BlockPos at){
        int clear=0;
        for(int dy=1;dy<=SupportRules.PARCEL_DROP_HEIGHT&&at.getY()+dy<level.getMaxBuildHeight();dy++){
            if(!level.getBlockState(at.above(dy)).getCollisionShape(level,at.above(dy)).isEmpty())break;
            clear=dy;
        }
        return clear;
    }
    /** A parcel drops from the sky onto the flare when it is outdoors, otherwise it appears at the caller's feet. */
    static UUID deliver(ServerLevel level,ServerPlayer caller,Vec3 flare,ListTag contents,boolean outside,int clear){
        var parcel=new SupportCrate.ParcelEntity(ArsenalBeacon.PARCEL.get(),level);
        parcel.setContents(contents);
        if(outside){
            parcel.setPos(flare.x,BlockPos.containing(flare).getY()+clear-0.5,flare.z);parcel.setFalling(true);
            level.sendParticles(ParticleTypes.CLOUD,flare.x,flare.y+1,flare.z,12,.3,.2,.3,.02);
        }else{
            Vec3 at=caller!=null&&caller.level()==level?caller.position().add(caller.getLookAngle().multiply(1.5,0,1.5)):flare;
            parcel.setPos(at.x,at.y,at.z);parcel.setFalling(false);
            level.sendParticles(ParticleTypes.END_ROD,at.x,at.y+.5,at.z,18,.3,.4,.3,.05);
            level.playSound(null,at.x,at.y,at.z,SoundEvents.ENDERMAN_TELEPORT,SoundSource.PLAYERS,.8f,1.4f);
        }
        level.addFreshEntity(parcel);return parcel.getUUID();
    }

    // ---- return portal -----------------------------------------------------------------------------------
    /** A cell a player can stand in: two open blocks, a solid floor, nothing harmful. */
    static boolean standable(ServerLevel level,BlockPos c){
        if(!level.hasChunkAt(c))return false;
        var feet=level.getBlockState(c);var head=level.getBlockState(c.above());var floor=level.getBlockState(c.below());
        if(!feet.getCollisionShape(level,c).isEmpty()||!head.getCollisionShape(level,c.above()).isEmpty()||floor.getCollisionShape(level,c.below()).isEmpty())return false;
        if(!feet.getFluidState().isEmpty()||!head.getFluidState().isEmpty())return false;
        return !feet.is(net.minecraft.world.level.block.Blocks.FIRE)&&!floor.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)&&!floor.is(net.minecraft.world.level.block.Blocks.CACTUS)&&!floor.is(net.minecraft.world.level.block.Blocks.CAMPFIRE);
    }
    /** The cells of a platform's return zone: the two blocks in front of it, two blocks tall. They are kept clear (no building) so the portal always has room. */
    static List<BlockPos> returnZone(BlockPos platform,Direction facing){
        var list=new java.util.ArrayList<BlockPos>();
        for(int d=1;d<=2;d++)for(int h=0;h<=1;h++)list.add(platform.relative(facing,d).above(h));
        return list;
    }
    /** Where the portal delivers a player: the front of the platform, or the nearest safe cell around it. Null when there is none (the return is cancelled, the flare is not used). */
    static Vec3 returnSpot(ServerLevel level,BlockPos platform,Direction facing){
        BlockPos front=platform.relative(facing);
        for(BlockPos c:new BlockPos[]{front,front.relative(facing),front.above(),front.below()})if(standable(level,c))return Vec3.atBottomCenterOf(c);
        BlockPos best=null;double bestD=Double.MAX_VALUE;
        for(BlockPos c:BlockPos.betweenClosed(platform.offset(-3,-2,-3),platform.offset(3,2,3))){
            if(c.getX()==platform.getX()&&c.getZ()==platform.getZ())continue;
            double d=c.distSqr(front);if(d<bestD&&standable(level,c)){bestD=d;best=c.immutable();}
        }
        return best==null?null:Vec3.atBottomCenterOf(best);
    }
    /** The spot for this player's return, or null. */
    static Vec3 returnSpotFor(ServerPlayer p){
        var home=p.getServer().overworld();var platform=platform(home,p.getUUID());if(platform==null)return null;
        return returnSpot(home,platform.getBlockPos(),platform.getBlockState().getValue(SupportPlatform.FACING));
    }
    /** The owner stepped into their return portal: send them to the front of their platform. Returns where they arrived, or null when refused. */
    static Vec3 finishReturn(ServerPlayer p){
        String problem=problem(p,Kind.RETURN);if(problem!=null){refuse(p,problem);return null;}
        var home=p.getServer().overworld();var platform=platform(home,p.getUUID());
        Direction facing=platform.getBlockState().getValue(SupportPlatform.FACING);Vec3 spot=returnSpot(home,platform.getBlockPos(),facing);
        if(spot==null){refuse(p,"return_blocked");return null;}
        travel(p,home,spot,facing.toYRot());
        return spot;
    }
    /** Where the portal back to the throw spot opens: a free cell near the platform's front, not the arrival cell itself. */
    static Vec3 backPortalSpot(ServerLevel home,UUID owner){
        var platform=platform(home,owner);if(platform==null)return null;
        Direction f=platform.getBlockState().getValue(SupportPlatform.FACING);BlockPos front=platform.getBlockPos().relative(f);
        for(BlockPos c:new BlockPos[]{front.relative(f),front.relative(f.getClockWise()),front.relative(f.getCounterClockWise()),front.relative(f,2)}){
            if(home.getBlockState(c).getCollisionShape(home,c).isEmpty()&&home.getBlockState(c.above()).getCollisionShape(home,c.above()).isEmpty()&&!home.getBlockState(c.below()).getCollisionShape(home,c.below()).isEmpty())return Vec3.atBottomCenterOf(c);
        }
        return Vec3.atBottomCenterOf(front);
    }
    static void travel(ServerPlayer p,ServerLevel to,Vec3 spot,float yaw){
        var from=p.serverLevel();from.sendParticles(ParticleTypes.PORTAL,p.getX(),p.getY()+1,p.getZ(),40,.4,.8,.4,.2);
        p.teleportTo(to,spot.x,spot.y,spot.z,yaw,0);
        to.sendParticles(ParticleTypes.PORTAL,spot.x,spot.y+1,spot.z,40,.4,.8,.4,.2);
        to.playSound(null,spot.x,spot.y,spot.z,SoundEvents.ENDERMAN_TELEPORT,SoundSource.PLAYERS,1f,1f);
    }
    /** Gives unused supplies back (used when a supply flare is destroyed before its parcel was sent). */
    static void giveBack(ServerLevel level,UUID owner,Vec3 at,List<ItemStack> items){
        var player=level.getServer().getPlayerList().getPlayer(owner);
        for(var s:items){
            if(player!=null&&player.getInventory().add(s))continue;
            net.minecraft.world.Containers.dropItemStack(level,at.x,at.y,at.z,s);
        }
    }
}
