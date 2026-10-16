package dev.eternaldefense.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.Vec3;

/** Raiders lay plank bridges across water, lava and chasms on their way; the raid takes every bridge away when it ends. */
final class RaidBridges {
    private RaidBridges(){}
    /** Cells checked ahead, how far ahead an edge is spotted, the longest bridge one raider lays in one go, the most a raid lays, ticks between two planks */
    static final int LOOK=12,EDGE=3,MAX_LENGTH=24,LIMIT=384,PLACE_TICKS=8,STALL_TICKS=60;
    /** A fall of this many blocks or more is a chasm */
    static final int CHASM=3;

    enum Floor{SOLID,FLUID,AIR}
    /** Pure: how many cells ahead the bridge starts (the first open cell, at most EDGE ahead over firm level ground), or -1.
     *  Cells 1 to n along the line: a wall at feet or head height, the floor under it, and for an open floor how far it falls
     *  (Integer.MAX_VALUE into water or lava). targetRise is how much higher the target stands than the raider. */
    static int edge(boolean[] wall,Floor[] floor,int[] fall,double targetRise){
        for(int g=1;g<floor.length&&g<=EDGE;g++){
            if(wall[g])return -1;   // A wall or a step up: digging or walking, not bridging
            if(floor[g]==Floor.SOLID)continue;
            if(floor[g]==Floor.AIR&&fall[g]<CHASM)return -1;   // A step down is walked
            if(floor[g]==Floor.FLUID||fall[g]==Integer.MAX_VALUE||targetRise>=-1)return g;
            // A drop toward a lower target is walked down, unless land comes back at the same height (a moat or a ravine)
            for(int i=g+1;i<floor.length;i++){if(wall[i])return -1;if(floor[i]==Floor.SOLID)return g;}
            return -1;
        }
        return -1;
    }
    /** Pure: the unit step along the larger of the two distances (bridges run straight along x or z) */
    static int[] axis(double dx,double dz){
        if(Math.abs(dx)<1e-6&&Math.abs(dz)<1e-6)return new int[]{0,0};
        return Math.abs(dx)>=Math.abs(dz)?new int[]{(int)Math.signum(dx),0}:new int[]{0,(int)Math.signum(dz)};
    }
    /** Pure: sideways offsets of a bridge as wide as the raider (a giant gets a wide one) */
    static int[] widths(float bbWidth){int w=Math.max(1,(int)Math.ceil(bbWidth-.01));int[] out=new int[w];for(int i=0;i<w;i++)out[i]=i-(w-1)/2;return out;}

    /** A bridge job: from the raider's own column, along one axis, at the height of the floor it stands on */
    record Plan(BlockPos start,int dx,int dz,int floorY,int[] widths){
        BlockPos cell(int step,int side){return new BlockPos(start.getX()+dx*step+(dz!=0?side:0),floorY,start.getZ()+dz*step+(dx!=0?side:0));}
    }
    /** Where the raider is going: its target, or the beacon */
    static Vec3 goal(CampaignData d,Mob mob){LivingEntity t=mob.getTarget();return t!=null&&t.isAlive()?t.position():Vec3.atBottomCenterOf(d.beacon);}
    static boolean wall(ServerLevel l,BlockPos p){return !l.getBlockState(p).getCollisionShape(l,p).isEmpty();}
    /** A bridge plan when water, lava or a chasm starts within EDGE blocks ahead of a raider walking on firm ground */
    static Plan plan(ServerLevel l,CampaignData d,Mob mob){
        if(!mob.onGround()||mob.isInWater()||mob.isInLava()||d.bridges.size()>=LIMIT)return null;
        var goal=goal(d,mob);int[] step=axis(goal.x-mob.getX(),goal.z-mob.getZ());
        if(step[0]==0&&step[1]==0||Math.abs(goal.x-mob.getX())+Math.abs(goal.z-mob.getZ())<2)return null;
        int floorY=Mth.floor(mob.getY()-.5);var start=new BlockPos(mob.getBlockX(),floorY,mob.getBlockZ());
        boolean[] wall=new boolean[LOOK+1];Floor[] floor=new Floor[LOOK+1];int[] fall=new int[LOOK+1];
        for(int i=1;i<=LOOK;i++){
            var f=start.offset(step[0]*i,0,step[1]*i);if(!l.hasChunkAt(f))return null;
            wall[i]=wall(l,f.above())||wall(l,f.above(2));
            var s=l.getBlockState(f);
            if(!s.getFluidState().isEmpty()&&s.getCollisionShape(l,f).isEmpty())floor[i]=Floor.FLUID;
            else if(!s.getCollisionShape(l,f).isEmpty())floor[i]=Floor.SOLID;
            else{floor[i]=Floor.AIR;fall[i]=fall(l,f);}
            if(i<=EDGE&&(wall[i]||floor[i]==Floor.AIR&&fall[i]<CHASM))break;   // A wall or a step down first: nothing to bridge from here
            if(i==EDGE&&floor[1]==Floor.SOLID&&floor[2]==Floor.SOLID&&floor[3]==Floor.SOLID)return null;   // Firm ground for a while
        }
        int g=edge(wall,floor,fall,goal.y-mob.getY());if(g<0)return null;
        return new Plan(start.offset(step[0]*(g-1),0,step[1]*(g-1)),step[0],step[1],floorY,widths(mob.getBbWidth()));
    }
    /** Blocks of open air under an open floor cell before something solid (MAX_VALUE into a fluid), counted to 8 */
    private static int fall(ServerLevel l,BlockPos floor){
        for(int n=1;n<=8;n++){var p=floor.below(n-1);var s=l.getBlockState(p);
            if(!s.getFluidState().isEmpty())return Integer.MAX_VALUE;
            if(!s.getCollisionShape(l,p).isEmpty())return n-1;}
        return 8;
    }
    /** A cell a plank may go into: air, water, lava or a plant, no block entity, not the beacon */
    static boolean open(ServerLevel l,BlockPos p){
        if(!l.hasChunkAt(p)||l.getBlockEntity(p)!=null||DefenseStructures.beacon(l,p))return false;
        var s=l.getBlockState(p);return s.isAir()||s.getBlock() instanceof LiquidBlock||s.getCollisionShape(l,p).isEmpty()&&(s.canBeReplaced()||!s.getFluidState().isEmpty());
    }
    /** One tick of a bridge job; false when the job is over */
    static boolean work(ServerLevel l,CampaignData d,Mob mob,RaidDigging.Job job,long now){
        var plan=job.bridge;
        // Walk to the edge first, then onto the last plank
        var last=plan.cell(job.step-1,0);double x=last.getX()+.5,z=last.getZ()+.5;
        if(Math.max(Math.abs(mob.getX()-x),Math.abs(mob.getZ()-z))>.4){
            mob.getNavigation().stop();mob.getMoveControl().setWantedPosition(x,plan.floorY()+1,z,.9);
            return now-job.moved<=STALL_TICKS;
        }
        job.moved=now;
        var next=plan.cell(job.step,0);
        mob.getNavigation().stop();mob.getMoveControl().setWantedPosition(mob.getX(),mob.getY(),mob.getZ(),0);
        mob.getLookControl().setLookAt(next.getX()+.5,next.getY()+.5,next.getZ()+.5);
        if(job.lastSwing!=Long.MIN_VALUE&&now-job.lastSwing<PLACE_TICKS)return true;
        var goal=goal(d,mob);
        if(job.step>MAX_LENGTH||d.bridges.size()>=LIMIT||Math.abs(goal.x-(next.getX()+.5))+Math.abs(goal.z-(next.getZ()+.5))<1.5)return false;
        if(wall(l,next.above())||wall(l,next.above(2)))return false;   // A wall ahead: digging takes over
        if(!open(l,next))return false;   // Land again
        int laid=0;for(int side:plan.widths()){var c=plan.cell(job.step,side);if(open(l,c)&&lay(l,d,c))laid++;}
        if(laid==0)return false;
        job.lastSwing=now;job.step++;
        RaidDigging.swing(l,mob);RaidMarch.breached(mob,now);RaidTargets.worked(mob,now);
        l.playSound(null,next,SoundEvents.WOOD_PLACE,SoundSource.HOSTILE,1f,.8f);
        return true;
    }
    /** Puts one plank in and remembers what was there */
    static boolean lay(ServerLevel l,CampaignData d,BlockPos p){
        var before=l.getBlockState(p);
        if(!l.setBlock(p,EternalDefense.RAIDER_BRIDGE.get().defaultBlockState(),3))return false;
        d.bridges.putIfAbsent(p.asLong(),before.getFluidState().isEmpty()?net.minecraft.world.level.block.Blocks.AIR.defaultBlockState():before.getFluidState().createLegacyBlock());d.setDirty();
        return true;
    }
    /** Takes the planks away and puts back the water, lava or air they replaced (later for unloaded ones) */
    static int remove(ServerLevel l,CampaignData d,int budget){
        int done=0;
        for(var it=d.bridges.entrySet().iterator();it.hasNext()&&done<budget;){
            var e=it.next();var p=BlockPos.of(e.getKey());if(!l.hasChunkAt(p))continue;
            var now=l.getBlockState(p);
            if(now.is(EternalDefense.RAIDER_BRIDGE.get())||now.isAir())l.setBlock(p,e.getValue(),3);
            it.remove();done++;
        }
        if(done>0)d.setDirty();return done;
    }
    /** Once a second outside a raid: planks still standing are taken away */
    static void tick(ServerLevel l,CampaignData d){if(!d.bridges.isEmpty()&&!d.phase.equals("raid"))remove(l,d,64);}

    /** The plank raiders lay: it drops nothing, players may break it even inside the zone, and it falls apart outside a raid */
    static final class BridgeBlock extends Block {
        BridgeBlock(){super(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).strength(.8f).sound(net.minecraft.world.level.block.SoundType.WOOD).randomTicks().noLootTable());}
        @Override public void randomTick(BlockState state,ServerLevel l,BlockPos pos,RandomSource random){
            if(l.dimension()!=Level.OVERWORLD||!CampaignData.get(l).phase.equals("raid")){
                var d=l.dimension()==Level.OVERWORLD?CampaignData.get(l):null;var before=d==null?null:d.bridges.remove(pos.asLong());
                l.setBlock(pos,before==null?net.minecraft.world.level.block.Blocks.AIR.defaultBlockState():before,3);if(d!=null)d.setDirty();
            }
        }
    }
}
