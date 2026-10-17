package dev.eternaldefense.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The Punji Spike Trap: stakes spring up under the first hostile mob that steps on it, once, and it sets itself again when the raid wave changes */
final class PunjiTrap {
    private PunjiTrap(){}
    static final BooleanProperty SPRUNG=BooleanProperty.create("sprung");
    /** The stakes' damage, and the ticks they take to shoot up and to sink back when it is set again */
    static final float DAMAGE=20;
    static final int RISE_TICKS=3,SINK_TICKS=20;
    /** How far the stakes travel (model pixels) */
    static final float TRAVEL=10.5f;
    static final VoxelShape LOW=Shapes.box(0,0,0,1,.25,1),UP=Shapes.box(0,0,0,1,12.5/16,1);

    static final class TrapBlock extends Block implements EntityBlock {
        TrapBlock(){super(Properties.of().strength(2,6).noOcclusion().sound(SoundType.BAMBOO_WOOD));registerDefaultState(stateDefinition.any().setValue(DefenseStructures.FACING,Direction.NORTH).setValue(SPRUNG,false));}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(DefenseStructures.FACING,SPRUNG);}
        @Override public BlockState getStateForPlacement(BlockPlaceContext c){return defaultBlockState().setValue(DefenseStructures.FACING,c.getHorizontalDirection().getOpposite());}
        @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return s.getValue(SPRUNG)?UP:LOW;}
        /** Mobs walk over it, up or down */
        @Override public VoxelShape getCollisionShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return LOW;}
        @Override public PushReaction getPistonPushReaction(BlockState s){return PushReaction.BLOCK;}
        @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new TrapEntity(pos,state);}
        /** Only a sprung trap keeps an eye on the raid wave */
        @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l,BlockState s,BlockEntityType<T> type){
            return l.isClientSide||!s.getValue(SPRUNG)||type!=EternalDefense.PUNJI_ENTITY.get()?null:(level,pos,state,be)->((TrapEntity)be).watch((ServerLevel)level);
        }
        @Override public void entityInside(BlockState s,Level l,BlockPos pos,Entity e){
            if(s.getValue(SPRUNG)||!(l instanceof ServerLevel server)||!(e instanceof Mob m)||!Turrets.foe(m))return;
            if(l.getBlockEntity(pos) instanceof TrapEntity be)be.spring(server,m);
        }
        /** The stakes are up: a moment after they spring */
        @Override public void tick(BlockState s,ServerLevel l,BlockPos pos,RandomSource random){
            if(!s.getValue(SPRUNG)&&l.getBlockEntity(pos) instanceof TrapEntity be&&be.springing){be.springing=false;l.setBlock(pos,s.setValue(SPRUNG,true),3);}
        }
        /** Right-click a sprung trap to set it again by hand */
        @Override public InteractionResult use(BlockState s,Level l,BlockPos pos,Player p,InteractionHand hand,BlockHitResult hit){
            if(!s.getValue(SPRUNG))return InteractionResult.PASS;
            if(l instanceof ServerLevel server&&l.getBlockEntity(pos) instanceof TrapEntity be)be.rearm(server);
            return InteractionResult.sidedSuccess(l.isClientSide);
        }
        @Override public boolean triggerEvent(BlockState s,Level l,BlockPos pos,int id,int param){super.triggerEvent(s,l,pos,id,param);var be=l.getBlockEntity(pos);return be!=null&&be.triggerEvent(id,param);}
    }

    static final class TrapEntity extends BlockEntity {
        /** The raid wave it sprang in (empty outside a raid) */
        String wave="";
        /** Server: the stakes are on their way up */
        boolean springing;
        /** Client side: when the stakes last moved, and whether up */
        long movedAt=-100;boolean rising;
        TrapEntity(BlockPos pos,BlockState state){super(EternalDefense.PUNJI_ENTITY.get(),pos,state);}
        void spring(ServerLevel l,Mob m){
            if(springing)return;springing=true;wave=waveKey(l,worldPosition);setChanged();
            var type=l.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getHolderOrThrow(DefenseWalls.key("punji_spike"));
            m.invulnerableTime=0;m.hurt(new DamageSource(type,null,null,Vec3.atCenterOf(worldPosition)),DAMAGE);
            l.blockEvent(worldPosition,getBlockState().getBlock(),1,0);
            l.scheduleTick(worldPosition,getBlockState().getBlock(),RISE_TICKS);
            l.playSound(null,worldPosition,DefenseSounds.PUNJI_SPRING.get(),SoundSource.BLOCKS,1f,.95f+l.random.nextFloat()*.1f);
            var c=Vec3.atBottomCenterOf(worldPosition);
            l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,Blocks.BAMBOO_BLOCK.defaultBlockState()),c.x,c.y+.6,c.z,14,.3,.2,.3,.1);
        }
        /** A sprung trap sets itself again once the wave it sprang in is over */
        void watch(ServerLevel l){if(l.getGameTime()%20==0&&!waveKey(l,worldPosition).equals(wave))rearm(l);}
        void rearm(ServerLevel l){
            var s=getBlockState();if(!s.getValue(SPRUNG))return;
            l.setBlock(worldPosition,s.setValue(SPRUNG,false),3);wave="";setChanged();
            l.blockEvent(worldPosition,s.getBlock(),2,0);
            l.playSound(null,worldPosition,SoundEvents.PISTON_CONTRACT,SoundSource.BLOCKS,.6f,1.3f);
        }
        @Override public boolean triggerEvent(int id,int arg){
            if(id==1||id==2){if(level!=null&&level.isClientSide){movedAt=level.getGameTime();rising=id==1;}return true;}
            return super.triggerEvent(id,arg);
        }
        @Override protected void saveAdditional(CompoundTag tag){super.saveAdditional(tag);tag.putString("Wave",wave);}
        @Override public void load(CompoundTag tag){super.load(tag);wave=tag.getString("Wave");}
    }
    /** Which raid wave is on here: the beacon's raid and its wave, or a village raid's, or none */
    static String waveKey(ServerLevel l,BlockPos pos){
        if(l.dimension()==Level.OVERWORLD){var d=CampaignData.get(l);if(d.phase.equals("raid"))return "raid "+d.raidsStarted+" "+d.wave;}
        var raid=l.getRaidAt(pos);return raid!=null&&raid.isActive()?"village "+raid.getId()+" "+raid.getGroupsSpawned():"";
    }
    /** Pure: how far below its raised place the stakes are drawn (model pixels), ticks after they last moved; NaN once they rest */
    static float stakes(boolean rising,float ticks){
        if(rising)return ticks>=RISE_TICKS+2?Float.NaN:-TRAVEL*Math.max(0,1-ticks/RISE_TICKS);
        return ticks>=SINK_TICKS?Float.NaN:-TRAVEL*Math.min(1,ticks/SINK_TICKS);
    }
}
