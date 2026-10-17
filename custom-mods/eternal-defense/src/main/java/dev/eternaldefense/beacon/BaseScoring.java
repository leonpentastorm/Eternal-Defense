package dev.eternaldefense.beacon;

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
    /** The mod's installations that can stand in the Home Zone */
    static final List<String> INSTALLATIONS=List.of("exchange_shop","support_platform","support_cannon","gun_platform","ammo_platform","attachment_platform",
        "armor_platform","mess_hall","cook_pot","bowl_dispenser","milk_dispenser","drink_mixer","drink_pot","cup_dispenser","mre_maker","logistics_hub",
        "command_table","satellite_beacon","supply_receiving_gate","tactical_enchanting_table");
    /** The installation a block of the mod is, or null: its id without the namespace and a Mk suffix. */
    static String installation(String id){
        if(!id.startsWith(EternalDefense.ID+":"))return null;
        String path=id.substring(EternalDefense.ID.length()+1).replaceFirst("_mk[1-4]$","");
        return INSTALLATIONS.contains(path)?path:null;
    }
    public record Score(int structure,int palette,int details,int lighting,int furnishings,int layout,int factory,int platforms,int installations){
        public int total(){return structure+palette+details+lighting+furnishings+layout+factory+platforms+installations;}
        CompoundTag tag(){var n=new CompoundTag();n.putInt("structure",structure);n.putInt("palette",palette);n.putInt("details",details);n.putInt("lighting",lighting);n.putInt("furnishings",furnishings);n.putInt("layout",layout);n.putInt("factory",factory);n.putInt("platforms",platforms);n.putInt("installations",installations);n.putInt("installationCount",Math.round(installations*(float)INSTALLATIONS.size()/Rules.INSTALLATION_SET_SCORE));n.putInt("total",total());return n;}
    }
    static final class Ledger extends SavedData {
        final Set<Long> placed=new HashSet<>();
        static Ledger get(ServerLevel l){return l.getDataStorage().computeIfAbsent(Ledger::load,Ledger::new,"eternal_defense_base_construction");}
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
    /** What a block counts as for the score. */
    record Kind(String id,boolean part,boolean beacon,boolean terrain,boolean furniture,boolean machine,boolean createEntity,boolean detail,String installation){}
    private static final Map<Block,Kind> KINDS=new java.util.concurrent.ConcurrentHashMap<>();
    static Kind kind(BlockState s){
        var block=s.getBlock();var known=KINDS.get(block);if(known!=null)return known;
        String id=BuiltInRegistries.BLOCK.getKey(block).toString();boolean station=block instanceof WeaponPlatform.Station,machine=false;
        if(!station&&!id.contains("turret"))for(Class<?> c=block.getClass();c!=null;c=c.getSuperclass())if(c.getSimpleName().contains("Kinetic")||c.getSimpleName().contains("Cannon")){machine=true;break;}
        var kind=new Kind(id,block instanceof DefenseStructures.Part,block==EternalDefense.BEACON.get(),terrain(id),furniture(id),!station&&(id.contains("turret")||machine),!station&&id.startsWith("create:"),detail(id,s),installation(id));
        KINDS.put(block,kind);return kind;
    }
    /** How many of INSTALLATIONS stand among these blocks (each counted once). */
    static int installations(Map<Long,BlockState> blocks){var found=new HashSet<String>();for(var s:blocks.values()){var k=kind(s);if(k.installation()!=null)found.add(k.installation());}return found.size();}
    /** Same answer as factory(BlockState,String), from the cached kind. */
    static boolean factory(BlockState s,Kind k){return k.machine()||k.createEntity()&&s.hasBlockEntity();}

    /** Light and furniture positions, bucketed in cells of REACH blocks */
    static final class Anchors {
        static final int REACH=12,REACH_SQUARED=REACH*REACH;
        private final it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<it.unimi.dsi.fastutil.longs.LongArrayList> cells=new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
        private int size;
        private static long cell(int x,int y,int z){return BlockPos.asLong(Math.floorDiv(x,REACH),Math.floorDiv(y,REACH),Math.floorDiv(z,REACH));}
        void add(long pos){int x=BlockPos.getX(pos),y=BlockPos.getY(pos),z=BlockPos.getZ(pos);cells.computeIfAbsent(cell(x,y,z),k->new it.unimi.dsi.fastutil.longs.LongArrayList()).add(pos);size++;}
        boolean isEmpty(){return size==0;}
        int size(){return size;}
        /** True when an anchor lies within REACH blocks of p */
        boolean near(BlockPos p){
            int px=p.getX(),py=p.getY(),pz=p.getZ(),cx=Math.floorDiv(px,REACH),cy=Math.floorDiv(py,REACH),cz=Math.floorDiv(pz,REACH);
            for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)for(int dz=-1;dz<=1;dz++){
                var list=cells.get(BlockPos.asLong(cx+dx,cy+dy,cz+dz));if(list==null)continue;
                for(int i=0;i<list.size();i++){long a=list.getLong(i);long x=BlockPos.getX(a)-px,y=BlockPos.getY(a)-py,z=BlockPos.getZ(a)-pz;if(x*x+y*y+z*z<=REACH_SQUARED)return true;}
            }
            return false;
        }
    }

    public static Score analyze(CampaignData d,Map<Long,BlockState> blocks,Set<Long> placed){
        Set<Long> designed=new HashSet<>();Map<String,Integer> furnishing=new HashMap<>(),machines=new HashMap<>();Set<String> palette=new HashSet<>(),installed=new HashSet<>();var anchors=new Anchors();int lights=0,details=0;int[] ages=new int[4];
        for(var e:blocks.entrySet()){
            var s=e.getValue();var k=kind(s);String id=k.id();if(k.part())continue;if(s.isAir()||!s.getFluidState().isEmpty()||k.beacon())continue;
            if(k.installation()!=null)installed.add(k.installation());   // Counted in addition to the block's own category.
            if(s.getBlock() instanceof WeaponPlatform.Station station){int kind=station.kind.equals("gun")?0:station.kind.equals("ammo")?1:station.kind.equals("attachment")?2:3;ages[kind]=Math.max(ages[kind],s.getValue(WeaponPlatform.AGE));continue;}
            if(factory(s,k)){machines.merge(id,1,Integer::sum);continue;}
            if(placed.contains(e.getKey())||!k.terrain())designed.add(e.getKey());
            if(k.furniture()){furnishing.merge(id,1,Integer::sum);anchors.add(e.getKey());}
            if(s.getLightEmission()>0&&!(s.getBlock() instanceof BaseFireBlock)){lights++;anchors.add(e.getKey());}
        }
        // A usable covered floor has two blocks of headroom, a roof, three nearby walls, and light/furniture nearby.
        int floors=0;Set<Integer> levels=new HashSet<>();
        int deep=d.beacon.getY()-15;
        if(!anchors.isEmpty())for(var e:blocks.entrySet()){
            // The cheap tests first, on the packed position: most blocks have a block above them and stop here without
            long key=e.getKey();int bx=BlockPos.getX(key),by=BlockPos.getY(key),bz=BlockPos.getZ(key);
            if(!d.inside(bx,by+2,bz)||kind(e.getValue()).terrain()&&by<deep)continue;
            if(!e.getValue().isSolid()||blocks.containsKey(BlockPos.asLong(bx,by+1,bz))||blocks.containsKey(BlockPos.asLong(bx,by+2,bz)))continue;
            BlockPos p=new BlockPos(bx,by,bz);
            if(!anchors.near(p))continue;
            BlockPos roof=null;for(int y=3;y<=10&&d.inside(p.above(y));y++)if(blocks.getOrDefault(p.above(y).asLong(),Blocks.AIR.defaultBlockState()).isSolid()){roof=p.above(y);break;}
            if(roof==null)continue;List<BlockPos> walls=new ArrayList<>();
            for(Direction dir:List.of(Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST))for(int distance=1;distance<=12;distance++){
                BlockPos wall=p.above().relative(dir,distance);if(!d.inside(wall))break;
                var s=blocks.get(wall.asLong());if(s!=null&&(s.isSolid()||s.getBlock() instanceof DoorBlock||s.getBlock() instanceof FenceBlock)){walls.add(wall);break;}
            }
            if(walls.size()<3)continue;floors++;levels.add(p.getY());designed.add(p.asLong());designed.add(roof.asLong());for(var wall:walls)for(int y=0;y<roof.getY()-wall.getY();y++)if(blocks.containsKey(wall.above(y).asLong()))designed.add(wall.above(y).asLong());
            if(floors>=72+(d.core>=3?4:Math.max(0,d.core))*180)break;
        }
        for(long p:designed){var s=blocks.get(p);if(s==null)continue;var k=kind(s);if(k.part())continue;palette.add(k.id());if(k.detail())details++;}
        int core=d.core>=3?4:Math.max(0,d.core);
        return new Score(Math.min(400,designed.size()),Math.min(8,palette.size())*20,Math.min(120+core*30,details*2),Math.min(80+core*10,lights*4),Math.min(180+core*30,furnishing.values().stream().mapToInt(n->Math.min(6,n)*6).sum()),Math.min(180+core*355,floors*2+Math.min(3,levels.size())*24),Math.min(500,machines.values().stream().mapToInt(n->Math.min(4,n)*20).sum()),Arrays.stream(ages).map(Rules::platformScore).sum(),Rules.installationScore(installed.size(),INSTALLATIONS.size()));
    }
}
