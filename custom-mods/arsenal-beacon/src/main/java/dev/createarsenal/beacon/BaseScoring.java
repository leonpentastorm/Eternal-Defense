package dev.createarsenal.beacon;

import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.*;
import java.util.*;

/** Material-neutral construction credit plus bounded architectural and functional bonuses. */
public final class BaseScoring {
    private static final Set<String> TERRAIN=Set.of("stone","dirt","grass_block","deepslate","netherrack","sand","gravel","end_stone","water","lava","air","bedrock","tuff","granite","diorite","andesite","cobblestone","moss_block","snow","ice","packed_ice","blue_ice","mud","clay");
    public record Score(int structure,int palette,int details,int lighting,int furnishings,int layout,int factory,int platforms){
        public int total(){return structure+palette+details+lighting+furnishings+layout+factory+platforms;}
        CompoundTag tag(){var n=new CompoundTag();n.putInt("structure",structure);n.putInt("palette",palette);n.putInt("details",details);n.putInt("lighting",lighting);n.putInt("furnishings",furnishings);n.putInt("layout",layout);n.putInt("factory",factory);n.putInt("platforms",platforms);n.putInt("total",total());return n;}
    }
    static final class Ledger extends SavedData {
        final Set<Long> placed=new HashSet<>();
        static Ledger get(ServerLevel l){return l.getDataStorage().computeIfAbsent(Ledger::load,Ledger::new,"arsenal_base_construction");}
        static Ledger load(CompoundTag n){var d=new Ledger();for(long p:n.getLongArray("placed"))d.placed.add(p);return d;}
        @Override public CompoundTag save(CompoundTag n){n.putLongArray("placed",placed.stream().mapToLong(Long::longValue).toArray());return n;}
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public void placed(BlockEvent.EntityPlaceEvent e){
        if(!(e.getLevel() instanceof ServerLevel l)||l.dimension()!=net.minecraft.world.level.Level.OVERWORLD||!(e.getEntity() instanceof net.minecraft.world.entity.player.Player))return;
        var d=CampaignData.get(l);if(!d.installed()||Math.abs(e.getPos().getX()-d.beacon.getX())>224||Math.abs(e.getPos().getZ()-d.beacon.getZ())>224||Math.abs(e.getPos().getY()-d.beacon.getY())>64)return;
        var ledger=Ledger.get(l);ledger.placed.add(e.getPos().asLong());ledger.setDirty();BaseSurvey.invalidate(l);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public void broken(BlockEvent.BreakEvent e){if(e.getLevel() instanceof ServerLevel l){var ledger=Ledger.get(l);if(ledger.placed.remove(e.getPos().asLong()))ledger.setDirty();BaseSurvey.invalidate(l);}}
    static boolean furniture(String id){return id.contains("bookshelf")||id.endsWith("_bed")||id.contains("barrel")||id.contains("chest")||id.contains("crafting_table")||id.contains("chair")||id.contains("table")||id.contains("sofa")||id.contains("bench")||id.contains("shelf")||id.contains("cabinet")||id.contains("drawer")||id.contains("painting")||id.contains("flower_pot")||id.contains("carpet");}
    static boolean terrain(String id){String p=id.substring(id.indexOf(':')+1);return TERRAIN.contains(p)||p.endsWith("_ore")||p.contains("leaves")||p.contains("sapling")||p.equals("short_grass")||p.equals("tall_grass");}
    static boolean detail(String id,BlockState s){return s.getBlock() instanceof StairBlock||s.getBlock() instanceof SlabBlock||s.getBlock() instanceof FenceBlock||s.getBlock() instanceof WallBlock||s.getBlock() instanceof DoorBlock||s.getBlock() instanceof TrapDoorBlock||id.contains("glass")||id.contains("window")||id.contains("fence")||id.contains("railing");}
    static boolean factory(BlockState s,String id){
        if(s.getBlock() instanceof WeaponPlatform.Station)return false;
        if(id.contains("turret"))return true;
        for(Class<?> c=s.getBlock().getClass();c!=null;c=c.getSuperclass())if(c.getSimpleName().contains("Kinetic")||c.getSimpleName().contains("Cannon"))return true;
        return id.startsWith("create:")&&s.hasBlockEntity();
    }
    public static Score analyze(CampaignData d,Map<Long,BlockState> blocks,Set<Long> placed){
        Set<Long> designed=new HashSet<>();Map<String,Integer> furnishing=new HashMap<>(),machines=new HashMap<>();Set<String> palette=new HashSet<>();List<BlockPos> anchors=new ArrayList<>();int lights=0,details=0;int[] ages=new int[4];
        for(var e:blocks.entrySet()){
            var s=e.getValue();String id=BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();if(s.is(ArsenalBeacon.STRUCTURE_PART.get()))continue;if(s.isAir()||!s.getFluidState().isEmpty()||s.is(ArsenalBeacon.BEACON.get()))continue;
            if(s.getBlock() instanceof WeaponPlatform.Station station){int kind=station.kind.equals("gun")?0:station.kind.equals("ammo")?1:station.kind.equals("attachment")?2:3;ages[kind]=Math.max(ages[kind],s.getValue(WeaponPlatform.AGE));continue;}
            if(factory(s,id)){machines.merge(id,1,Integer::sum);continue;}
            if(placed.contains(e.getKey())||!terrain(id))designed.add(e.getKey());
            if(furniture(id)){furnishing.merge(id,1,Integer::sum);anchors.add(BlockPos.of(e.getKey()));}
            if(s.getLightEmission()>0&&!(s.getBlock() instanceof BaseFireBlock)){lights++;anchors.add(BlockPos.of(e.getKey()));}
        }
        // A usable covered floor has two blocks of headroom, a roof, three nearby walls,
        // and light/furniture nearby. Raw stone and dirt receive the same room credit.
        int floors=0;Set<Integer> levels=new HashSet<>();
        if(!anchors.isEmpty())for(var e:blocks.entrySet()){
            BlockPos p=BlockPos.of(e.getKey());if(!d.inside(p.above(2))||terrain(BuiltInRegistries.BLOCK.getKey(e.getValue().getBlock()).toString())&&p.getY()<d.beacon.getY()-15)continue;
            if(!e.getValue().isSolid()||blocks.containsKey(p.above().asLong())||blocks.containsKey(p.above(2).asLong()))continue;
            if(anchors.stream().noneMatch(a->a.distSqr(p)<=144))continue;
            BlockPos roof=null;for(int y=3;y<=10&&d.inside(p.above(y));y++)if(blocks.getOrDefault(p.above(y).asLong(),Blocks.AIR.defaultBlockState()).isSolid()){roof=p.above(y);break;}
            if(roof==null)continue;List<BlockPos> walls=new ArrayList<>();
            for(Direction dir:List.of(Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST))for(int distance=1;distance<=12;distance++){
                BlockPos wall=p.above().relative(dir,distance);if(!d.inside(wall))break;
                var s=blocks.get(wall.asLong());if(s!=null&&(s.isSolid()||s.getBlock() instanceof DoorBlock||s.getBlock() instanceof FenceBlock)){walls.add(wall);break;}
            }
            if(walls.size()<3)continue;floors++;levels.add(p.getY());designed.add(p.asLong());designed.add(roof.asLong());for(var wall:walls)for(int y=0;y<roof.getY()-wall.getY();y++)if(blocks.containsKey(wall.above(y).asLong()))designed.add(wall.above(y).asLong());
            if(floors>=72+(d.core>=3?4:Math.max(0,d.core))*180)break;
        }
        for(long p:designed){var s=blocks.get(p);if(s==null)continue;String id=BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();if(s.is(ArsenalBeacon.STRUCTURE_PART.get()))continue;palette.add(id);if(detail(id,s))details++;}
        int core=d.core>=3?4:Math.max(0,d.core);
        return new Score(Math.min(400,designed.size()),Math.min(8,palette.size())*20,Math.min(120+core*30,details*2),Math.min(80+core*10,lights*4),Math.min(180+core*30,furnishing.values().stream().mapToInt(n->Math.min(6,n)*6).sum()),Math.min(180+core*355,floors*2+Math.min(3,levels.size())*24),Math.min(500,machines.values().stream().mapToInt(n->Math.min(4,n)*20).sum()),Arrays.stream(ages).map(Rules::platformScore).sum());
    }
}
