package dev.createarsenal.beacon;

import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
import java.util.*;

/** One anchor plus owned cells. A hall is 2 wide x 1 deep x 2 tall (anchor, the cell to its right, and both above); pots and dispensers are 1 x 1 x 2. */
abstract class KitchenBlock extends Block implements EntityBlock {
    final boolean hall;
    KitchenBlock(boolean hall){this(hall,0);}
    /** {@code light}: the block light the kitchen block gives off (the Cook Pot's lamp over its chalkboard). */
    KitchenBlock(boolean hall,int light){super(Properties.of().strength(3,6).noOcclusion().sound(SoundType.METAL).lightLevel(s->light));this.hall=hall;registerDefaultState(stateDefinition.any().setValue(ArsenalStructures.FACING,Direction.NORTH));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(ArsenalStructures.FACING);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext c){return defaultBlockState().setValue(ArsenalStructures.FACING,c.getHorizontalDirection().getOpposite());}
    List<BlockPos> cells(BlockPos root,BlockState state){var right=state.getValue(ArsenalStructures.FACING).getClockWise();return hall?List.of(root.relative(right),root.above(),root.relative(right).above()):List.of(root.above());}
    VoxelShape full(BlockState state){var root=BlockPos.ZERO;int minX=0,minZ=0,maxX=1,maxZ=1,maxY=1;for(var p:cells(root,state)){minX=Math.min(minX,p.getX());minZ=Math.min(minZ,p.getZ());maxX=Math.max(maxX,p.getX()+1);maxZ=Math.max(maxZ,p.getZ()+1);maxY=Math.max(maxY,p.getY()+1);}return Shapes.create(minX,0,minZ,maxX,maxY,maxZ);}
    @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return ArsenalStructures.cell(s,0,0,0);}
    @Override public PushReaction getPistonPushReaction(BlockState s){return PushReaction.BLOCK;}
    @Override public void onPlace(BlockState s,Level l,BlockPos p,BlockState old,boolean moving){super.onPlace(s,l,p,old,moving);if(!s.is(old.getBlock())&&!l.isClientSide&&!ArsenalStructures.install(l,p,s))l.scheduleTick(p,this,20);}
    @Override public void tick(BlockState s,ServerLevel l,BlockPos p,net.minecraft.util.RandomSource random){if(!ArsenalStructures.install(l,p,s))l.scheduleTick(p,this,20);}
    @Override public void onRemove(BlockState s,Level l,BlockPos p,BlockState next,boolean moving){if(!s.is(next.getBlock())&&!(s.getBlock() instanceof MessHall.HallBlock&&next.getBlock() instanceof MessHall.HallBlock)){if(l.getBlockEntity(p) instanceof MessHall.HallEntity h)h.removed();ArsenalStructures.remove(l,p);}super.onRemove(s,l,p,next,moving);}
    @Override public InteractionResult use(BlockState s,Level l,BlockPos p,Player player,InteractionHand hand,BlockHitResult hit){
        if(player instanceof ServerPlayer sp){if(BaseZone.disabled(l,p,player))return InteractionResult.CONSUME;
            if(l.getBlockEntity(p) instanceof MessHall.HallEntity h){h.discover();net.minecraftforge.network.NetworkHooks.openScreen(sp,h,b->{b.writeBlockPos(p);b.writeVarInt(h.mk());});}
            else if(l.getBlockEntity(p) instanceof CookPot.PotEntity pot)pot.use(sp,hand);
        }return InteractionResult.sidedSuccess(l.isClientSide);
    }
    static final class KitchenItem extends BlockItem {
        KitchenItem(Block block){super(block,new Item.Properties());}
        @Override public String getDescriptionId(){return getBlock()==ArsenalBeacon.MESS_HALL_I.get()?"item.arsenal_beacon.mess_hall":super.getDescriptionId();}
        @Override public InteractionResult place(BlockPlaceContext c){
            var s=getBlock().getStateForPlacement(c);var level=c.getLevel();var root=c.getClickedPos();
            if(s==null)return InteractionResult.FAIL;
            if(!level.isClientSide){var all=new ArrayList<>(((KitchenBlock)getBlock()).cells(root,s));all.add(root);
                for(var p:all){var problem=BaseZone.problem(level,p);if(problem!=null){BaseZone.say(c.getPlayer(),"zone."+problem);return InteractionResult.FAIL;}if(level instanceof ServerLevel server&&ReturnZone.inside(server,p)){BaseZone.say(c.getPlayer(),"keep_clear");return InteractionResult.FAIL;}}
                if(!ArsenalStructures.available(level,root,s)){BaseZone.say(c.getPlayer(),"no_room");return InteractionResult.FAIL;}
            }return super.place(c);
        }
        @Override public void appendHoverText(ItemStack stack,Level level,List<net.minecraft.network.chat.Component> lines,TooltipFlag flag){lines.add(net.minecraft.network.chat.Component.translatable("tooltip.arsenal_beacon.zone_only"));if(getBlock() instanceof BowlDispenser.DispenserBlock||getBlock() instanceof MilkDispenser.DispenserBlock)lines.add(net.minecraft.network.chat.Component.translatable("gui.arsenal_beacon.dispenser.empty_hands"));lines.add(net.minecraft.network.chat.Component.translatable("gui.arsenal_beacon.kitchen.footprint",getBlock() instanceof MessHall.HallBlock?"2 wide, 1 deep, 2 tall":"1 wide, 1 deep, 2 tall"));}
    }
}
