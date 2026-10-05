package dev.createarsenal.beacon;

import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
import java.util.*;

/** Owned footprint cells forward interaction; expansions never replace another structure. */
final class ArsenalStructures {
    static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    static final BooleanProperty WIDE=BooleanProperty.create("wide");
    static final BooleanProperty TALL=BooleanProperty.create("tall");
    static final IntegerProperty MK=IntegerProperty.create("mk",1,4);
    // Vanilla integer state properties are nonnegative: X/Z store delta+1.
    static final IntegerProperty X=IntegerProperty.create("offset_x",0,2),Y=IntegerProperty.create("offset_y",0,1),Z=IntegerProperty.create("offset_z",0,2);
    static final Map<String,VoxelShape> shapes=new java.util.concurrent.ConcurrentHashMap<>();
    static final Map<String,VoxelShape> clippedShapes=new java.util.concurrent.ConcurrentHashMap<>();
    static int mark(int core){return 1+Math.max(0,Math.min(3,core));}
    static int angle(Direction facing){return switch(facing){case EAST->90;case SOUTH->180;case WEST->270;default->0;};}
    static BlockPos second(BlockPos pos,BlockState state){return pos.relative(state.getValue(FACING).getClockWise());}
    static BlockPos anchor(BlockGetter level,BlockPos pos){var s=level.getBlockState(pos);return s.is(ArsenalBeacon.STRUCTURE_PART.get())?pos.offset(1-s.getValue(X),-s.getValue(Y),1-s.getValue(Z)):pos;}
    static boolean beacon(BlockGetter level,BlockPos pos){return level.getBlockState(anchor(level,pos)).is(ArsenalBeacon.BEACON.get());}
    static List<BlockPos> cells(BlockPos root,BlockState state){
        List<BlockPos> cells=new ArrayList<>();
        if(state.is(ArsenalBeacon.BEACON.get())&&state.getValue(MK)==4){for(int y=0;y<=1;y++)for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)if(x!=0||y!=0||z!=0)cells.add(root.offset(x,y,z));}
        else if(state.getBlock() instanceof WeaponPlatform.Station){if(state.getValue(WIDE))cells.add(second(root,state));if(state.getValue(TALL)){cells.add(root.above());if(state.getValue(WIDE))cells.add(second(root,state).above());}}return cells;
    }
    static boolean available(Level level,BlockPos root,BlockState wanted){
        for(var p:cells(root,wanted)){
            if(!level.isInWorldBounds(p)||!level.getWorldBorder().isWithinBounds(p)||!level.hasChunkAt(p))return false;
            var s=level.getBlockState(p);
            if(s.is(ArsenalBeacon.STRUCTURE_PART.get())&&anchor(level,p).equals(root))continue;
            if(!s.canBeReplaced()||level.getBlockEntity(p)!=null)return false;
            if(!level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,new AABB(p),e->!e.isSpectator()).isEmpty())return false;
        }return true;
    }
    static boolean install(Level level,BlockPos root,BlockState state){
        if(level.isClientSide||!available(level,root,state))return false;
        for(var p:cells(root,state)){var s=ArsenalBeacon.STRUCTURE_PART.get().defaultBlockState().setValue(X,1+p.getX()-root.getX()).setValue(Y,p.getY()-root.getY()).setValue(Z,1+p.getZ()-root.getZ());if(level.getBlockState(p)!=s)level.setBlock(p,s,3);}return true;
    }
    static void remove(Level level,BlockPos root){
        if(level.isClientSide)return;
        for(int y=0;y<=1;y++)for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){var p=root.offset(x,y,z);if(!p.equals(root)&&level.hasChunkAt(p)&&level.getBlockState(p).is(ArsenalBeacon.STRUCTURE_PART.get())&&anchor(level,p).equals(root))level.setBlock(p,Blocks.AIR.defaultBlockState(),3);}
    }
    static boolean syncBeacon(ServerLevel level,CampaignData d){
        if(!d.installed()||!level.hasChunkAt(d.beacon))return false;var current=level.getBlockState(d.beacon);if(!current.is(ArsenalBeacon.BEACON.get()))return false;
        var wanted=current.setValue(MK,mark(d.core));
        if(!available(level,d.beacon,wanted))return false;
        if(current!=wanted)level.setBlock(d.beacon,wanted,3);install(level,d.beacon,wanted);return true;
    }
    static AABB hurtbox(BlockPos root,int mark){return mark==4?new AABB(root.getX()-1,root.getY(),root.getZ()-1,root.getX()+2,root.getY()+2,root.getZ()+2):new AABB(root);}
    static int actualMark(BlockGetter level,BlockPos pos){var state=level.getBlockState(pos);return state.is(ArsenalBeacon.BEACON.get())?state.getValue(MK):1;}
    static VoxelShape full(BlockState state){
        // Collision is a simple installation envelope, independent of decorative mesh detail.
        // Exact unions of dozens of fractional cuboids create enormous voxel grids on every raycast.
        if(state.is(ArsenalBeacon.BEACON.get()))return state.getValue(MK)==4?Shapes.create(-1,0,-1,2,2,2):Shapes.block();
        boolean wide=state.getValue(WIDE),tall=state.getValue(TALL);Direction facing=state.getValue(FACING);String key=wide+":"+tall+":"+facing;
        return shapes.computeIfAbsent(key,k->{double height=tall?2:1;if(!wide)return Shapes.create(0,0,0,1,height,1);return switch(facing){case EAST->Shapes.create(0,0,0,1,height,2);case SOUTH->Shapes.create(-1,0,0,1,height,1);case WEST->Shapes.create(0,0,-1,1,height,1);default->Shapes.create(0,0,0,2,height,1);};});
    }
    static VoxelShape cell(BlockState state,int x,int y,int z){String key=(state.is(ArsenalBeacon.BEACON.get())?"mk"+state.getValue(MK):state.getValue(WIDE)+":"+state.getValue(TALL)+":"+state.getValue(FACING))+":"+x+":"+y+":"+z;return clippedShapes.computeIfAbsent(key,k->Shapes.join(full(state),Shapes.create(x,y,z,x+1,y+1,z+1),BooleanOp.AND).move(-x,-y,-z));}
    public static final class Part extends Block {
        Part(){super(Properties.of().strength(3,6).noOcclusion().dynamicShape());registerDefaultState(stateDefinition.any().setValue(X,1).setValue(Y,0).setValue(Z,1));}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(X,Y,Z);}
        @Override public RenderShape getRenderShape(BlockState state){return RenderShape.INVISIBLE;}
        @Override public PushReaction getPistonPushReaction(BlockState state){return PushReaction.BLOCK;}
        @Override public VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){var root=anchor(level,pos);var owner=level.getBlockState(root);if(!(owner.getBlock() instanceof WeaponPlatform.Station)&&!owner.is(ArsenalBeacon.BEACON.get()))return Shapes.empty();return cell(owner,state.getValue(X)-1,state.getValue(Y),state.getValue(Z)-1);}
        @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player p,InteractionHand hand,BlockHitResult hit){var root=anchor(level,pos);var owner=level.getBlockState(root);return owner.use(level,p,hand,new BlockHitResult(hit.getLocation(),hit.getDirection(),root,hit.isInside()));}
        @Override public void playerWillDestroy(Level level,BlockPos pos,BlockState state,Player p){var root=anchor(level,pos);if(!level.isClientSide&&level.getBlockState(root).getBlock() instanceof WeaponPlatform.Station)level.destroyBlock(root,!p.isCreative(),p);super.playerWillDestroy(level,pos,state,p);}
        @Override public void onRemove(BlockState s,Level l,BlockPos pos,BlockState next,boolean moving){if(!l.isClientSide&&!next.is(this)){var root=pos.offset(1-s.getValue(X),-s.getValue(Y),1-s.getValue(Z));if(l.getBlockState(root).getBlock() instanceof WeaponPlatform.Station)l.scheduleTick(root,l.getBlockState(root).getBlock(),1);}super.onRemove(s,l,pos,next,moving);}
        @Override public void tick(BlockState s,ServerLevel l,BlockPos pos,net.minecraft.util.RandomSource random){var root=anchor(l,pos);if(!l.hasChunkAt(root)){l.scheduleTick(pos,this,20);return;}var owner=l.getBlockState(root);if(!(owner.getBlock() instanceof WeaponPlatform.Station)&&!owner.is(ArsenalBeacon.BEACON.get())||!cells(root,owner).contains(pos))l.setBlock(pos,Blocks.AIR.defaultBlockState(),3);}
        @Override public void neighborChanged(BlockState s,Level l,BlockPos pos,Block block,BlockPos from,boolean moving){if(!l.isClientSide)l.scheduleTick(pos,this,1);}
    }
}
