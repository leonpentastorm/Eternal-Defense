package dev.eternaldefense.beacon;

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

/** One anchor plus owned cells. */
abstract class KitchenBlock extends Block implements EntityBlock {
    final boolean hall;
    KitchenBlock(boolean hall){this(hall,0);}
    /** Light: the block light the kitchen block gives off (the Cook Pot's lamp over its chalkboard). */
    KitchenBlock(boolean hall,int light){super(Properties.of().strength(3,6).noOcclusion().sound(SoundType.METAL).lightLevel(s->light));this.hall=hall;registerDefaultState(stateDefinition.any().setValue(DefenseStructures.FACING,Direction.NORTH));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(DefenseStructures.FACING);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext c){return defaultBlockState().setValue(DefenseStructures.FACING,c.getHorizontalDirection().getOpposite());}
    List<BlockPos> cells(BlockPos root,BlockState state){var right=state.getValue(DefenseStructures.FACING).getClockWise();return hall?List.of(root.relative(right),root.above(),root.relative(right).above()):List.of(root.above());}
    VoxelShape full(BlockState state){var root=BlockPos.ZERO;int minX=0,minZ=0,maxX=1,maxZ=1,maxY=1;for(var p:cells(root,state)){minX=Math.min(minX,p.getX());minZ=Math.min(minZ,p.getZ());maxX=Math.max(maxX,p.getX()+1);maxZ=Math.max(maxZ,p.getZ()+1);maxY=Math.max(maxY,p.getY()+1);}return Shapes.create(minX,0,minZ,maxX,maxY,maxZ);}
    @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return DefenseStructures.cell(s,0,0,0);}
    @Override public PushReaction getPistonPushReaction(BlockState s){return PushReaction.BLOCK;}
    @Override public void onPlace(BlockState s,Level l,BlockPos p,BlockState old,boolean moving){super.onPlace(s,l,p,old,moving);if(!s.is(old.getBlock())&&!l.isClientSide&&!DefenseStructures.install(l,p,s))l.scheduleTick(p,this,20);}
    @Override public void tick(BlockState s,ServerLevel l,BlockPos p,net.minecraft.util.RandomSource random){if(!DefenseStructures.install(l,p,s))l.scheduleTick(p,this,20);}
    @Override public void onRemove(BlockState s,Level l,BlockPos p,BlockState next,boolean moving){
        boolean upgrade=s.getBlock() instanceof MessHall.HallBlock&&next.getBlock() instanceof MessHall.HallBlock||s.getBlock() instanceof DrinkStation.StationBlock&&next.getBlock() instanceof DrinkStation.StationBlock;
        if(!s.is(next.getBlock())&&!upgrade){if(l.getBlockEntity(p) instanceof MessHall.HallEntity h)h.removed();DefenseStructures.remove(l,p);}super.onRemove(s,l,p,next,moving);
    }
    @Override public InteractionResult use(BlockState s,Level l,BlockPos p,Player player,InteractionHand hand,BlockHitResult hit){
        if(player instanceof ServerPlayer sp){if(BaseZone.disabled(l,p,player))return InteractionResult.CONSUME;
            if(l.getBlockEntity(p) instanceof MessHall.HallEntity h){h.discover();net.minecraftforge.network.NetworkHooks.openScreen(sp,h,b->{b.writeBlockPos(p);b.writeVarInt(h.mk());});}
            else if(l.getBlockEntity(p) instanceof CookPot.PotEntity pot)pot.use(sp,hand);
            else if(l.getBlockEntity(p) instanceof DrinkStation.StationEntity st){st.discover();net.minecraftforge.network.NetworkHooks.openScreen(sp,st,b->{b.writeBlockPos(p);b.writeVarInt(st.mk());});}
            else if(l.getBlockEntity(p) instanceof BrewPot.PotEntity pot)pot.use(sp,hand);
        }return InteractionResult.sidedSuccess(l.isClientSide);
    }
    static final class KitchenItem extends BlockItem {
        KitchenItem(Block block){super(block,new Item.Properties());}
        @Override public String getDescriptionId(){return getBlock()==EternalDefense.MESS_HALL_I.get()?"item.eternal_defense.mess_hall":getBlock()==EternalDefense.DRINK_STATION_I.get()?"item.eternal_defense.drink_station":super.getDescriptionId();}
        @Override public InteractionResult place(BlockPlaceContext c){
            var s=getBlock().getStateForPlacement(c);var level=c.getLevel();var root=c.getClickedPos();
            // Anything in the way is refused on both sides (the preview is red)
            if(PlacementGuard.blocked(c,s)){if(s!=null&&!level.isClientSide)BaseZone.say(c.getPlayer(),"no_room");return PlacementGuard.refuse(c);}
            if(!level.isClientSide){var all=new ArrayList<>(((KitchenBlock)getBlock()).cells(root,s));all.add(root);
                for(var p:all){var problem=BaseZone.problem(level,p);if(problem!=null){BaseZone.say(c.getPlayer(),"zone."+problem);return PlacementGuard.refuse(c);}if(level instanceof ServerLevel server&&ReturnZone.inside(server,p)){BaseZone.say(c.getPlayer(),"keep_clear");return PlacementGuard.refuse(c);}}
            }return PlacementGuard.settled(c,super.place(c));
        }
        @Override public void appendHoverText(ItemStack stack,Level level,List<net.minecraft.network.chat.Component> lines,TooltipFlag flag){lines.add(net.minecraft.network.chat.Component.translatable("tooltip.eternal_defense.zone_only"));if(getBlock() instanceof BowlDispenser.DispenserBlock||getBlock() instanceof MilkDispenser.DispenserBlock||getBlock() instanceof CupDispenser.DispenserBlock)lines.add(net.minecraft.network.chat.Component.translatable("gui.eternal_defense.dispenser.empty_hands"));lines.add(net.minecraft.network.chat.Component.translatable("gui.eternal_defense.kitchen.footprint",((KitchenBlock)getBlock()).hall?"2 wide, 1 deep, 2 tall":"1 wide, 1 deep, 2 tall"));}
    }
}
