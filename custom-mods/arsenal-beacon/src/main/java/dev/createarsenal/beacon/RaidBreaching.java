package dev.createarsenal.beacon;

import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;

/** Raid-only digging with nearby doors preferred. Protected damage always uses the recovery journal. */
final class RaidBreaching {
    static boolean breakable(ServerLevel l,CampaignData d,BlockPos p){var s=l.getBlockState(p);return l.hasChunkAt(p)&&!s.isAir()&&!ArsenalStructures.beacon(l,p)&&!s.getCollisionShape(l,p).isEmpty()&&s.getDestroySpeed(l,p)>=0;}
    static BlockPos choose(ServerLevel l,CampaignData d,Mob mob){
        Vec3 target=mob.getTarget()==null?Vec3.atCenterOf(d.beacon):mob.getTarget().position();var dir=target.subtract(mob.position()).normalize();BlockPos door=null;double best=Double.MAX_VALUE;
        for(var p:BlockPos.betweenClosed(mob.blockPosition().offset(-2,0,-2),mob.blockPosition().offset(2,2,2))){var s=l.getBlockState(p);var delta=Vec3.atCenterOf(p).subtract(mob.position());if((s.getBlock() instanceof DoorBlock||s.getBlock() instanceof TrapDoorBlock)&&breakable(l,d,p)&&delta.lengthSqr()<=9&&delta.dot(dir)>0&&delta.lengthSqr()<best){door=p.immutable();best=delta.lengthSqr();}}
        if(door!=null)return door;
        var hit=l.clip(new ClipContext(mob.getEyePosition(),target.add(0,.8,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mob));
        if(hit.getType()==HitResult.Type.BLOCK&&hit.getLocation().distanceToSqr(mob.position())<=12&&breakable(l,d,hit.getBlockPos()))return hit.getBlockPos();
        var ahead=BlockPos.containing(mob.getX()+dir.x*1.2,mob.getY()+Math.min(0,dir.y),mob.getZ()+dir.z*1.2);
        for(int y=1;y>=0;y--)if(breakable(l,d,ahead.above(y)))return ahead.above(y);
        if(dir.y<-.3&&breakable(l,d,ahead.below()))return ahead.below();return null;
    }
    static boolean breach(ServerLevel l,CampaignData d,Mob mob){
        if(!d.phase.equals("raid")||!BeaconCombat.hostile(mob))return false;var p=choose(l,d,mob);if(p==null)return false;
        if(d.inside(p)){if(!d.snapshot.containsKey(p.asLong()))return false;ArsenalBeacon.damageBlock(l,d,p);}else l.destroyBlock(p,true,mob);
        mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND);mob.getNavigation().stop();return l.getBlockState(p).isAir();
    }
}
