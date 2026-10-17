package dev.eternaldefense.beacon;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.*;

/** The Industrial Spotlight: a work light, 2 blocks tall, that throws a strong beam one way while a powered Power Pole reaches it; a right-click turns it 45 degrees */
final class Spotlight {
    private Spotlight(){}
    static final BooleanProperty LIT=BooleanProperty.create("lit");
    /** Which way it shines: 0 north, then clockwise in steps of 45 degrees */
    static final IntegerProperty ROTATION=IntegerProperty.create("rotation",0,7);
    /** Stress it asks for, per RPM of the dynamos */
    static final int STRESS=2;
    /** The stand's own light while lit; the beam's light every GLOW_STEP blocks from GLOW_FIRST out to BEAM_LENGTH */
    static final int LAMP_LIGHT=7,GLOW_LIGHT=15,GLOW_FIRST=3,GLOW_STEP=4,BEAM_LENGTH=24;
    /** A glow under the beam sits on the ground at most this far below the lamp's block */
    static final int GLOW_DROP=4;
    /** The lenses' height above the stand's block (blocks) */
    static final double LENS_HEIGHT=28.625/16;
    /** How often a lit spotlight checks its beam (ticks) */
    static final int REFRESH=40;

    /** Pure: the beam's direction for a rotation (x east, z south) */
    static Vec3 direction(int rotation){double a=Math.toRadians(45*rotation);return new Vec3(Math.sin(a),0,-Math.cos(a));}
    /** Pure: the rotation that faces the way a player looks (yaw 180 is north) */
    static int rotationFor(float yaw){return Math.floorMod(Math.round((yaw-180)/45f),8);}
    /** Pure: the columns the beam lights, nearest first, from the stand's block */
    static List<BlockPos> columns(BlockPos stand,int rotation){
        var out=new ArrayList<BlockPos>();var d=direction(rotation);
        for(int r=GLOW_FIRST;r<=BEAM_LENGTH;r+=GLOW_STEP)out.add(stand.offset((int)Math.round(d.x*r),0,(int)Math.round(d.z*r)));
        return out;
    }

    static final class LightBlock extends Block implements EntityBlock,DefenseStructures.Footprint {
        LightBlock(){super(Properties.of().strength(3,6).noOcclusion().sound(SoundType.METAL).lightLevel(s->s.getValue(LIT)?LAMP_LIGHT:0));registerDefaultState(stateDefinition.any().setValue(ROTATION,0).setValue(LIT,false));}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(ROTATION,LIT);}
        @Override public BlockState getStateForPlacement(BlockPlaceContext c){return defaultBlockState().setValue(ROTATION,c.getPlayer()==null?0:rotationFor(c.getPlayer().getYRot()));}
        /** The block entity's renderer draws it, turned to any of the 8 directions */
        @Override public RenderShape getRenderShape(BlockState s){return RenderShape.INVISIBLE;}
        @Override public List<BlockPos> extra(BlockPos root,BlockState state){return List.of(root.above());}
        @Override public VoxelShape whole(BlockState state){return Shapes.box(.03,0,.03,.97,1.98,.97);}
        @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return DefenseStructures.cell(s,0,0,0);}
        @Override public PushReaction getPistonPushReaction(BlockState s){return PushReaction.BLOCK;}
        @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new LightEntity(pos,state);}
        @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l,BlockState s,BlockEntityType<T> type){
            return l.isClientSide||type!=EternalDefense.SPOTLIGHT_ENTITY.get()?null:(level,pos,state,be)->((LightEntity)be).tick((ServerLevel)level);
        }
        @Override public void onPlace(BlockState s,Level l,BlockPos pos,BlockState old,boolean moving){super.onPlace(s,l,pos,old,moving);if(l instanceof ServerLevel)DefenseStructures.install(l,pos,s);}
        @Override public void tick(BlockState s,ServerLevel l,BlockPos pos,RandomSource random){DefenseStructures.install(l,pos,s);}
        @Override public void onRemove(BlockState s,Level l,BlockPos pos,BlockState next,boolean moving){
            if(!s.is(next.getBlock())){if(l instanceof ServerLevel server&&l.getBlockEntity(pos) instanceof LightEntity be)be.dark(server);DefenseStructures.remove(l,pos);}
            super.onRemove(s,l,pos,next,moving);
        }
        /** A right-click turns it 45 degrees clockwise, a sneaking one back */
        @Override public InteractionResult use(BlockState s,Level l,BlockPos pos,Player p,InteractionHand hand,BlockHitResult hit){
            if(p.getItemInHand(hand).getItem() instanceof PowerPoles.WireItem)return InteractionResult.PASS;
            if(p instanceof ServerPlayer sp&&l instanceof ServerLevel server){
                var turned=s.setValue(ROTATION,Math.floorMod(s.getValue(ROTATION)+(p.isShiftKeyDown()?-1:1),8));
                l.setBlock(pos,turned,3);
                l.playSound(null,pos,SoundEvents.IRON_TRAPDOOR_CLOSE,SoundSource.BLOCKS,.5f,1.6f);
                if(l.getBlockEntity(pos) instanceof LightEntity be)be.refresh(server);
                sp.displayClientMessage(status(turned,getName()),true);
            }
            return InteractionResult.sidedSuccess(l.isClientSide);
        }
        static Component status(BlockState s,Component name){
            var way=Component.translatable("gui.eternal_defense.spotlight.dir."+s.getValue(ROTATION));
            return s.getValue(LIT)?Component.translatable("gui.eternal_defense.spotlight.on",name,way).withStyle(ChatFormatting.AQUA)
                :Component.translatable("gui.eternal_defense.spotlight.off",name,way,PowerKit.AURA).withStyle(ChatFormatting.GOLD);
        }
    }

    /** Draws a little power from the pole that reaches it, like a turret, and keeps the glow under its beam */
    static final class LightEntity extends BlockEntity implements PowerGrid.Consumer {
        /** The glow blocks it placed */
        final Set<BlockPos> glow=new LinkedHashSet<>();
        LightEntity(BlockPos pos,BlockState state){super(EternalDefense.SPOTLIGHT_ENTITY.get(),pos,state);}
        @Override public void onLoad(){super.onLoad();if(level instanceof ServerLevel l){PowerGrid.joinTurret(l,worldPosition);for(var g:glow)owners(l).put(g,worldPosition);}}
        @Override public float stress(){return STRESS;}
        @Override public void power(boolean on){
            var s=getBlockState();if(level==null||!s.hasProperty(LIT)||s.getValue(LIT)==on)return;
            level.setBlock(worldPosition,s.setValue(LIT,on),3);
            if(level instanceof ServerLevel l)refresh(l);
        }
        void tick(ServerLevel l){if(l.getGameTime()%REFRESH==Math.floorMod(worldPosition.hashCode(),REFRESH))refresh(l);}
        /** Lights the ground under the beam, or puts it out; glow it no longer wants goes */
        void refresh(ServerLevel l){
            var s=getBlockState();var want=s.hasProperty(LIT)&&s.getValue(LIT)?beam(l,worldPosition,s.getValue(ROTATION)):Set.<BlockPos>of();
            for(var it=glow.iterator();it.hasNext();){var g=it.next();if(want.contains(g))continue;clear(l,g);it.remove();}
            for(var g:want){
                if(l.getBlockState(g).getBlock() instanceof Glow){glow.add(g);owners(l).put(g.immutable(),worldPosition);continue;}
                if(!l.getBlockState(g).isAir())continue;
                l.setBlock(g,EternalDefense.SPOTLIGHT_GLOW.get().defaultBlockState(),3);glow.add(g.immutable());owners(l).put(g.immutable(),worldPosition);
            }
            setChanged();
        }
        void dark(ServerLevel l){for(var g:glow)clear(l,g);glow.clear();setChanged();}
        private void clear(ServerLevel l,BlockPos g){owners(l).remove(g,worldPosition);if(l.isLoaded(g)&&l.getBlockState(g).getBlock() instanceof Glow)l.setBlock(g,Blocks.AIR.defaultBlockState(),3);}
        @Override protected void saveAdditional(CompoundTag tag){super.saveAdditional(tag);tag.put("Glow",new LongArrayTag(glow.stream().mapToLong(BlockPos::asLong).toArray()));}
        @Override public void load(CompoundTag tag){super.load(tag);glow.clear();if(tag.contains("Glow",Tag.TAG_LONG_ARRAY))for(long p:tag.getLongArray("Glow"))glow.add(BlockPos.of(p));}
        @Override public AABB getRenderBoundingBox(){return new AABB(worldPosition).inflate(BEAM_LENGTH*.7+2);}
    }

    /** Where the beam lights the ground: one cell for each column out to the first one a wall or hill hides from the lenses */
    static Set<BlockPos> beam(Level l,BlockPos stand,int rotation){
        var out=new LinkedHashSet<BlockPos>();var lens=Vec3.atBottomCenterOf(stand).add(0,LENS_HEIGHT,0);
        for(var column:columns(stand,rotation)){
            var top=column.above();
            if(l.clip(new Beam(lens,Vec3.atCenterOf(top),stand)).getType()!=HitResult.Type.MISS)break;
            var at=top;
            for(int i=0;i<GLOW_DROP+1&&open(l,at.below());i++)at=at.below();
            if(open(l,at))out.add(at);
        }
        return out;
    }
    private static boolean open(Level l,BlockPos p){var s=l.getBlockState(p);return s.isAir()||s.getBlock() instanceof Glow;}
    /** A sight line from the lenses that the spotlight's own blocks do not stop */
    static final class Beam extends ClipContext {
        final BlockPos stand;
        Beam(Vec3 from,Vec3 to,BlockPos stand){super(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,null);this.stand=stand;}
        @Override public VoxelShape getBlockShape(BlockState s,BlockGetter l,BlockPos p){return p.equals(stand)||p.equals(stand.above())?Shapes.empty():super.getBlockShape(s,l,p);}
    }

    /** Which spotlight placed each glow block (server); a glow nobody claims goes out on its next check */
    private static final Map<Level,Map<BlockPos,BlockPos>> OWNERS=new WeakHashMap<>();
    static Map<BlockPos,BlockPos> owners(Level l){return OWNERS.computeIfAbsent(l,k->new HashMap<>());}

    /** The light under a spotlight's beam: invisible, without collision or drops, replaced by anything placed there */
    static final class Glow extends Block {
        static final int CHECK=100;
        Glow(){super(Properties.of().replaceable().noCollission().noOcclusion().noLootTable().instabreak().lightLevel(s->GLOW_LIGHT).pushReaction(PushReaction.DESTROY));}
        @Override public RenderShape getRenderShape(BlockState s){return RenderShape.INVISIBLE;}
        @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return Shapes.empty();}
        @Override public void onPlace(BlockState s,Level l,BlockPos pos,BlockState old,boolean moving){super.onPlace(s,l,pos,old,moving);if(!l.isClientSide)l.scheduleTick(pos,this,CHECK);}
        @Override public void tick(BlockState s,ServerLevel l,BlockPos pos,RandomSource random){
            var owner=owners(l).get(pos);
            if(owner!=null&&l.isLoaded(owner)&&l.getBlockEntity(owner) instanceof LightEntity be&&be.glow.contains(pos))l.scheduleTick(pos,this,CHECK);
            else l.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
        }
    }
}
