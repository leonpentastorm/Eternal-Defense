package dev.eternaldefense.beacon;

import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;

/** Which block a blocked raider digs (nearby doors first), and the giants' wall smashing. */
final class RaidBreaching {
    static boolean breakable(ServerLevel l,CampaignData d,BlockPos p){var s=l.getBlockState(p);return l.hasChunkAt(p)&&!s.isAir()&&!DefenseStructures.beacon(l,p)&&!s.getCollisionShape(l,p).isEmpty()&&s.getDestroySpeed(l,p)>=0;}
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
    static final int SMASH_LIMIT=24;
    /** A giant stuck on its way: every block its body would run into a block further on breaks (the ground under it stays) */
    static int smash(ServerLevel l,CampaignData d,Mob mob){
        if(!d.phase.equals("raid"))return 0;
        Vec3 target=mob.getTarget()==null?Vec3.atCenterOf(d.beacon):mob.getTarget().position();var flat=new Vec3(target.x-mob.getX(),0,target.z-mob.getZ());
        if(flat.lengthSqr()<1e-4)return 0;
        var box=mob.getBoundingBox().move(flat.normalize()).deflate(.05);int floor=net.minecraft.util.Mth.floor(mob.getY()+.01),broken=0;
        for(var p:BlockPos.betweenClosed(net.minecraft.util.Mth.floor(box.minX),Math.max(floor,net.minecraft.util.Mth.floor(box.minY)),net.minecraft.util.Mth.floor(box.minZ),net.minecraft.util.Mth.floor(box.maxX),net.minecraft.util.Mth.floor(box.maxY),net.minecraft.util.Mth.floor(box.maxZ))){
            if(broken>=SMASH_LIMIT)break;
            var at=p.immutable();if(!breakable(l,d,at))continue;
            if(d.inside(at)){if(!d.snapshot.containsKey(at.asLong()))continue;EternalDefense.damageBlock(l,d,at);}else l.destroyBlock(at,true,mob);
            broken++;
        }
        if(broken>0){RaidMarch.breached(mob,l.getGameTime());mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND);}
        return broken;
    }
}
