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
import java.util.ArrayList;
import java.util.List;

/** Raiders lay plank bridges across water, lava and chasms on their way; the raid takes every bridge away when it ends. */
final class RaidBridges {
    private RaidBridges(){}
    /** Cells checked ahead, how far ahead an edge is spotted, the longest bridge one raider lays in one go, the most a raid lays, ticks between two planks */
    static final int LOOK=12,EDGE=3,MAX_LENGTH=24,LIMIT=384,PLACE_TICKS=8,STALL_TICKS=60;
    /** A raider at an edge uses a bridge whose start is this close instead of laying its own; the longest line followed */
    static final int REUSE_RANGE=16,LINE_MAX=96;
    /** A raider behind a bridge's start by up to this much along its line is still on the near side */
    static final double NEAR_SIDE=2;
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
    /** A bridge laid this raid: its line, how far its planks reach, and whether it reached land */
    static final class Line{
        final Plan plan;int length;boolean landed;
        Line(Plan plan){this.plan=plan;}
        BlockPos entrance(){return plan.cell(0,0);}
    }
    private static final List<Line> LINES=new ArrayList<>();
    static List<Line> lines(){return LINES;}
    /** A raid starts or ends */
    static void forget(){LINES.clear();}
    /** Pure: the distance to a bridge's start, or -1 when it is too far, too narrow, leads away or lies behind the raider */
    static double reuseCost(double toEntrance,double along,int width,int lineWidth,int dx,int dz,double goalDx,double goalDz){
        if(toEntrance>REUSE_RANGE||lineWidth<width||along>NEAR_SIDE)return -1;
        return dx*goalDx+dz*goalDz>0?toEntrance:-1;
    }
    static boolean plank(ServerLevel l,BlockPos p){return l.hasChunkAt(p)&&l.getBlockState(p).is(EternalDefense.RAIDER_BRIDGE.get());}
    /** The nearest bridge of this raid a raider at an edge can use (its first plank still stands, or it is just being started) */
    static Line nearest(ServerLevel l,Mob mob,Vec3 goal,int floorY){
        Line best=null;double cost=Double.MAX_VALUE;int width=widths(mob.getBbWidth()).length;
        for(var it=LINES.iterator();it.hasNext();){
            var line=it.next();var plan=line.plan;
            if(line.length>0&&!plank(l,plan.cell(1,0))){if(l.hasChunkAt(plan.cell(1,0)))it.remove();continue;}   // Taken away or broken at its start
            if(Math.abs(plan.floorY()-floorY)>1)continue;
            var e=line.entrance();double ex=e.getX()+.5-mob.getX(),ez=e.getZ()+.5-mob.getZ();
            double c=reuseCost(Math.sqrt(ex*ex+ez*ez),-(ex*plan.dx()+ez*plan.dz()),width,plan.widths().length,plan.dx(),plan.dz(),goal.x-(e.getX()+.5),goal.z-(e.getZ()+.5));
            if(c>=0&&c<cost){cost=c;best=line;}
        }
        return best;
    }
    /** Blocks of water to swim toward the goal before land to climb out on at the surface (RaidMarch.SWIM_LOOK when none is in sight) */
    static int swim(ServerLevel l,Mob mob,Vec3 goal){
        var feet=mob.blockPosition();int surface=feet.getY();
        while(surface<feet.getY()+RaidMarch.SWIM_LOOK&&!l.getFluidState(new BlockPos(feet.getX(),surface,feet.getZ())).isEmpty())surface++;
        double dx=goal.x-mob.getX(),dz=goal.z-mob.getZ(),len=Math.sqrt(dx*dx+dz*dz);
        if(len<1)return 0;
        for(int i=1;i<=RaidMarch.SWIM_LOOK;i++){
            if(i>=len)return i;   // The target is in the water: swum to
            int x=Mth.floor(mob.getX()+dx/len*i),z=Mth.floor(mob.getZ()+dz/len*i);
            if(!l.hasChunkAt(new BlockPos(x,surface,z)))break;
            for(int y=surface-1;y<=surface+1;y++)if(standable(l,new BlockPos(x,y,z)))return i;
        }
        return RaidMarch.SWIM_LOOK;
    }
    /** A dry spot with room to stand and firm ground under it */
    private static boolean standable(ServerLevel l,BlockPos p){
        if(!l.getFluidState(p).isEmpty()||wall(l,p)||wall(l,p.above()))return false;
        var below=p.below();return wall(l,below)&&l.getFluidState(below).isEmpty();
    }
    /** Where the raider is going: its target, or the beacon */
    static Vec3 goal(CampaignData d,Mob mob){LivingEntity t=mob.getTarget();return t!=null&&t.isAlive()?t.position():Vec3.atBottomCenterOf(d.beacon);}
    static boolean wall(ServerLevel l,BlockPos p){return !l.getBlockState(p).getCollisionShape(l,p).isEmpty();}
    /** At water, lava or a chasm within EDGE blocks ahead: a raid bridge nearby that leads its way, else a new one */
    static Line plan(ServerLevel l,CampaignData d,Mob mob){
        if(!mob.onGround()||mob.isInWater()||mob.isInLava())return null;
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
        var used=nearest(l,mob,goal,floorY);if(used!=null)return used;
        if(d.bridges.size()>=LIMIT)return null;
        var line=new Line(new Plan(start.offset(step[0]*(g-1),0,step[1]*(g-1)),step[0],step[1],floorY,widths(mob.getBbWidth())));LINES.add(line);
        return line;
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
        var line=job.bridge;var plan=line.plan;
        if(job.target!=job.step){job.target=job.step;job.best=Double.MAX_VALUE;}
        // First to the bridge's start (or the last cell reached), then along it
        var last=plan.cell(job.step-1,0);double x=last.getX()+.5,z=last.getZ()+.5;
        double off=Math.max(Math.abs(mob.getX()-x),Math.abs(mob.getZ()-z));
        if(off>.4){
            if(off<job.best-.5){job.best=off;job.moved=now;}
            // To a bridge's start along the shore a path is walked; on the planks, straight ahead
            if(job.step==1&&off>2){if(mob.getNavigation().isDone()||now%20==0)mob.getNavigation().moveTo(x,plan.floorY()+1,z,1);}
            else{mob.getNavigation().stop();mob.getMoveControl().setWantedPosition(x,plan.floorY()+1,z,.9);}
            return now-job.moved<=STALL_TICKS;
        }
        job.moved=now;
        // Standing on it: planks already down ahead are walked to their end
        int reached=job.step;while(job.step<LINE_MAX&&plank(l,plan.cell(job.step,0)))job.step++;
        if(job.step!=reached){line.length=Math.max(line.length,job.step-1);return true;}
        var next=plan.cell(job.step,0);
        mob.getNavigation().stop();mob.getMoveControl().setWantedPosition(mob.getX(),mob.getY(),mob.getZ(),0);
        mob.getLookControl().setLookAt(next.getX()+.5,next.getY()+.5,next.getZ()+.5);
        if(job.lastSwing!=Long.MIN_VALUE&&now-job.lastSwing<PLACE_TICKS)return true;
        var goal=goal(d,mob);
        if(job.laid>=MAX_LENGTH||job.step>=LINE_MAX||d.bridges.size()>=LIMIT||Math.abs(goal.x-(next.getX()+.5))+Math.abs(goal.z-(next.getZ()+.5))<1.5)return false;
        if(wall(l,next.above())||wall(l,next.above(2)))return false;   // A wall ahead: digging takes over
        if(!open(l,next)){line.landed=true;return false;}   // Land again
        int laid=0;for(int side:plan.widths()){var c=plan.cell(job.step,side);if(open(l,c)&&lay(l,d,c))laid++;}
        if(laid==0)return false;
        line.length=Math.max(line.length,job.step);job.laid++;job.lastSwing=now;job.step++;
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
