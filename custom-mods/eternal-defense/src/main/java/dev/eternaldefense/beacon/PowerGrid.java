package dev.eternaldefense.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.*;

/** The power networks of a level: poles joined by wire, the dynamos under them, and what draws on them */
final class PowerGrid {
    private PowerGrid(){}

    /** Something a network powers: a tower or a spotlight in a pole's reach, or a shock box wired to a pole */
    interface Consumer {
        /** Stress it asks for, per RPM of the dynamos */
        float stress();
        void power(boolean on);
    }
    /** What a network looked like the last time it was worked out (for a pole's status line) */
    record Summary(int dynamos,int turning,float rpm,float demand,boolean running,boolean overstressed,int turrets,int boxes,float su){}

    static final class Grid {
        final Set<BlockPos> poles=new HashSet<>(),turrets=new HashSet<>(),boxes=new HashSet<>();
        /** Barbed wire made live last time, so it can be switched off again */
        Set<BlockPos> live=new HashSet<>();
        /** Dynamos under a pole last time: one whose pole is gone sheds its stress */
        Set<BlockPos> dynamos=new HashSet<>();
        final Map<BlockPos,Summary> summaries=new HashMap<>();
    }
    private static final Map<ServerLevel,Grid> GRIDS=new WeakHashMap<>();
    static Grid of(ServerLevel l){return GRIDS.computeIfAbsent(l,k->new Grid());}
    static void joinPole(ServerLevel l,BlockPos pos){of(l).poles.add(pos.immutable());}
    /** A tower or a spotlight: powered by the nearest pole whose reach covers it */
    static void joinTurret(ServerLevel l,BlockPos pos){of(l).turrets.add(pos.immutable());}
    static void joinBox(ServerLevel l,BlockPos pos){of(l).boxes.add(pos.immutable());}
    static Summary summary(ServerLevel l,BlockPos pole){return of(l).summaries.get(pole);}
    /** The pole nearest to pos whose reach covers it, or null */
    static BlockPos poleFor(ServerLevel l,BlockPos pos){
        BlockPos best=null;double d=Double.MAX_VALUE;
        for(var p:of(l).poles)if(PowerKit.inAura(p,pos)){double dist=p.distSqr(pos);if(dist<d){d=dist;best=p;}}
        return best;
    }

    // Union-find over the poles
    private static BlockPos find(Map<BlockPos,BlockPos> parent,BlockPos p){var r=p;while(!parent.get(r).equals(r))r=parent.get(r);
        var c=p;while(!c.equals(r)){var n=parent.get(c);parent.put(c,r);c=n;}return r;}

    static void tick(ServerLevel l){
        var g=GRIDS.get(l);if(g==null)return;
        // Only the blocks still standing and loaded take part
        var poles=new HashMap<BlockPos,PowerPoles.PoleEntity>();
        for(var it=g.poles.iterator();it.hasNext();){var p=it.next();if(!l.isLoaded(p)||!(l.getBlockEntity(p) instanceof PowerPoles.PoleEntity be)){it.remove();continue;}poles.put(p,be);}
        var parent=new HashMap<BlockPos,BlockPos>();for(var p:poles.keySet())parent.put(p,p);
        for(var e:poles.entrySet())for(var t:e.getValue().links)if(poles.containsKey(t)){var a=find(parent,e.getKey());var b=find(parent,t);if(!a.equals(b))parent.put(a,b);}
        // The networks: dynamos, demand, the consumers each one feeds
        record Net(List<PowerKit.Dynamo> dynamos,List<Consumer> consumers,List<PowerPoles.PoleEntity> poles,int[] counts){}
        var nets=new HashMap<BlockPos,Net>();var dynamos=new HashSet<BlockPos>();
        for(var e:poles.entrySet()){
            var root=find(parent,e.getKey());var net=nets.computeIfAbsent(root,k->new Net(new ArrayList<>(),new ArrayList<>(),new ArrayList<>(),new int[2]));
            net.poles.add(e.getValue());
            if(l.getBlockEntity(e.getKey().below()) instanceof PowerKit.Dynamo dynamo){net.dynamos.add(dynamo);dynamos.add(e.getKey().below());}
        }
        for(var p:g.dynamos)if(!dynamos.contains(p)&&l.isLoaded(p)&&l.getBlockEntity(p) instanceof PowerKit.Dynamo gone){gone.demand(0);gone.report(null);}
        g.dynamos=dynamos;
        var fed=new HashSet<BlockPos>();
        for(var e:poles.entrySet())for(var t:e.getValue().links)if(g.boxes.contains(t)&&l.isLoaded(t)&&l.getBlockEntity(t) instanceof ShockBoxes.BoxEntity box&&fed.add(t)){var net=nets.get(find(parent,e.getKey()));net.consumers.add(box);net.counts[1]++;}
        for(var it=g.turrets.iterator();it.hasNext();){
            var p=it.next();if(!l.isLoaded(p)||!(l.getBlockEntity(p) instanceof Consumer turret)||turret instanceof ShockBoxes.BoxEntity){it.remove();continue;}
            BlockPos best=null;double d=Double.MAX_VALUE;for(var pole:poles.keySet())if(PowerKit.inAura(pole,p)){double dist=pole.distSqr(p);if(dist<d){d=dist;best=pole;}}
            if(best==null){turret.power(false);continue;}
            var net=nets.get(find(parent,best));net.consumers.add(turret);if(turret instanceof Turrets.TurretEntity)net.counts[0]++;
        }
        for(var it=g.boxes.iterator();it.hasNext();){var p=it.next();if(!l.isLoaded(p)||!(l.getBlockEntity(p) instanceof ShockBoxes.BoxEntity box)){it.remove();continue;}if(!fed.contains(p))box.power(false);}
        g.summaries.clear();
        for(var net:nets.values()){
            float demand=0;for(var c:net.consumers)demand+=c.stress();
            var turning=net.dynamos.stream().filter(dy->dy.rpm()>=PowerKit.MIN_RPM).toList();
            boolean over=turning.stream().anyMatch(PowerKit.Dynamo::overstressed),running=PowerKit.running(turning.size(),over);
            float share=PowerKit.share(demand,turning.size());
            for(var dy:net.dynamos)dy.demand(turning.contains(dy)?share:0);
            for(var c:net.consumers)c.power(running);
            float rpm=(float)turning.stream().mapToDouble(PowerKit.Dynamo::rpm).max().orElse(0);
            float su=PowerKit.su(share,turning.stream().mapToDouble(PowerKit.Dynamo::rpm).sum());
            var summary=new Summary(net.dynamos.size(),turning.size(),rpm,demand,running,over,net.counts[0],net.counts[1],su);
            for(var dy:net.dynamos)dy.report(summary);
            for(var pole:net.poles){pole.live(running);g.summaries.put(pole.getBlockPos(),summary);}
        }
        // Shock boxes with charge make the barbed wire they are wired to live
        var live=new HashSet<BlockPos>();
        for(var p:g.boxes)if(l.getBlockEntity(p) instanceof ShockBoxes.BoxEntity box){box.drain(PowerKit.GRID_TICKS);if(box.charged())live.addAll(spread(box.links,q->l.isLoaded(q)&&l.getBlockState(q).getBlock() instanceof DefenseWalls.BarbedWire,PowerKit.SHOCK_REACH));}
        for(var p:live)setLive(l,p,true);
        for(var p:g.live)if(!live.contains(p))setLive(l,p,false);
        g.live=live;
    }
    /** Pure: the wire one box makes live: from the blocks it is wired to, through joined wire, nearest first, at most max blocks */
    static List<BlockPos> spread(Collection<BlockPos> starts,java.util.function.Predicate<BlockPos> wire,int max){
        var queue=new ArrayDeque<BlockPos>(starts);var seen=new HashSet<BlockPos>();var out=new ArrayList<BlockPos>();
        while(!queue.isEmpty()&&out.size()<max){
            var p=queue.poll();if(!seen.add(p)||!wire.test(p))continue;
            out.add(p);
            for(var dir:Direction.Plane.HORIZONTAL)queue.add(p.relative(dir));
            queue.add(p.above());queue.add(p.below());
        }
        return out;
    }
    private static void setLive(ServerLevel l,BlockPos p,boolean on){
        if(!l.isLoaded(p))return;BlockState s=l.getBlockState(p);
        if(s.getBlock() instanceof DefenseWalls.BarbedWire&&s.getValue(DefenseWalls.BarbedWire.POWERED)!=on)l.setBlock(p,s.setValue(DefenseWalls.BarbedWire.POWERED,on),3);
    }
    /** Barbed wire left live with no shock box behind it (after a restart) */
    static boolean liveNow(ServerLevel l,BlockPos p){var g=GRIDS.get(l);return g!=null&&g.live.contains(p);}

    static final class GridEvents {
        @SubscribeEvent public void gridTick(TickEvent.LevelTickEvent e){
            if(e.phase!=TickEvent.Phase.END||!(e.level instanceof ServerLevel l)||l.getGameTime()%PowerKit.GRID_TICKS!=0)return;
            tick(l);
        }
    }
}
