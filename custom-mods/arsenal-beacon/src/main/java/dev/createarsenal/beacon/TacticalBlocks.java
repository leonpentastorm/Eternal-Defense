package dev.createarsenal.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;

/**
 * The Command Table and the Satellite Beacon (0.0.20), the owner's models. Both live only inside the beacon zone, one of each per base, and move
 * for free with the Recovery Shovel. The table is 2 wide, 2 tall and 2 deep: its anchor is the front right block (seen from the front), the
 * model reaching to the left ({@code facing.getClockWise()}) and to the back; the seven other blocks are invisible structure parts. The satellite
 * is 1 x 2 x 1 like the Exchange Shop. The uplink between them is worked out when it is needed ({@link Hunts#uplink}), never saved.
 */
final class TacticalBlocks {
    private TacticalBlocks(){}
    static final DirectionProperty FACING=HorizontalDirectionalBlock.FACING;
    /** Satellite level I to III; one model for now, so per-level models can be dropped in later through the blockstate file. */
    static final IntegerProperty MK=IntegerProperty.create("mk",1,TacticalRules.MAX_MK);
    /** The table's footprint, from its anchor: 2 wide (clockwise of the facing), 2 deep (behind), 2 tall. */
    static final int TABLE_WIDE=2,TABLE_DEEP=2,TABLE_TALL=2;

    static final class TableBlock extends Block {
        TableBlock(){super(Properties.of().strength(3,6).noOcclusion().lightLevel(s->7).sound(SoundType.METAL));registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH));}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING);}
        @Override public BlockState getStateForPlacement(BlockPlaceContext c){return defaultBlockState().setValue(FACING,c.getHorizontalDirection().getOpposite());}
        @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return ArsenalStructures.cell(s,0,0,0);}
        @Override public PushReaction getPistonPushReaction(BlockState s){return PushReaction.BLOCK;}
        @Override public void onPlace(BlockState s,Level l,BlockPos pos,BlockState old,boolean moving){super.onPlace(s,l,pos,old,moving);if(l instanceof ServerLevel)ArsenalStructures.install(l,pos,s);}
        @Override public void tick(BlockState s,ServerLevel l,BlockPos pos,net.minecraft.util.RandomSource random){ArsenalStructures.install(l,pos,s);}
        @Override public void setPlacedBy(Level l,BlockPos pos,BlockState s,LivingEntity who,ItemStack stack){super.setPlacedBy(l,pos,s,who,stack);if(l instanceof ServerLevel server){var d=HuntData.get(server);d.table=pos.immutable();d.setDirty();}}
        @Override public void onRemove(BlockState s,Level l,BlockPos pos,BlockState next,boolean moving){
            if(!s.is(next.getBlock())){ArsenalStructures.remove(l,pos);if(l instanceof ServerLevel server){var d=HuntData.get(server);if(pos.equals(d.table)){d.table=null;d.setDirty();}}}
            super.onRemove(s,l,pos,next,moving);
        }
        @Override public InteractionResult use(BlockState s,Level l,BlockPos pos,Player p,InteractionHand hand,BlockHitResult hit){
            if(p instanceof ServerPlayer sp&&!BaseZone.disabled(l,pos,sp))Hunts.open(sp,pos);
            return InteractionResult.sidedSuccess(l.isClientSide);
        }
    }

    static final class SatelliteBlock extends Block {
        SatelliteBlock(){super(Properties.of().strength(3,6).noOcclusion().lightLevel(s->4).sound(SoundType.METAL));registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(MK,1));}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING,MK);}
        /** The Mk comes from the server's record of the base ({@link HuntData#satelliteMk}), never from the item. */
        @Override public BlockState getStateForPlacement(BlockPlaceContext c){
            int mk=c.getLevel() instanceof ServerLevel server?TacticalRules.mk(HuntData.get(server).satelliteMk):1;
            return defaultBlockState().setValue(FACING,c.getHorizontalDirection().getOpposite()).setValue(MK,mk);
        }
        @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return ArsenalStructures.cell(s,0,0,0);}
        @Override public PushReaction getPistonPushReaction(BlockState s){return PushReaction.BLOCK;}
        @Override public void onPlace(BlockState s,Level l,BlockPos pos,BlockState old,boolean moving){super.onPlace(s,l,pos,old,moving);if(l instanceof ServerLevel)ArsenalStructures.install(l,pos,s);}
        @Override public void tick(BlockState s,ServerLevel l,BlockPos pos,net.minecraft.util.RandomSource random){ArsenalStructures.install(l,pos,s);}
        @Override public void setPlacedBy(Level l,BlockPos pos,BlockState s,LivingEntity who,ItemStack stack){super.setPlacedBy(l,pos,s,who,stack);if(l instanceof ServerLevel server){var d=HuntData.get(server);d.satellite=pos.immutable();d.setDirty();Hunts.fillIfLinked(server);}}
        @Override public void onRemove(BlockState s,Level l,BlockPos pos,BlockState next,boolean moving){
            if(!s.is(next.getBlock())){ArsenalStructures.remove(l,pos);if(l instanceof ServerLevel server){var d=HuntData.get(server);if(pos.equals(d.satellite)){d.satellite=null;d.setDirty();}}}
            super.onRemove(s,l,pos,next,moving);
        }
        @Override public InteractionResult use(BlockState s,Level l,BlockPos pos,Player p,InteractionHand hand,BlockHitResult hit){
            if(p instanceof ServerPlayer sp&&!BaseZone.disabled(l,pos,sp)){
                var server=sp.serverLevel();var h=HuntData.get(server);int mk=TacticalRules.mk(h.satelliteMk);
                String problem=Hunts.uplinkProblem(server,CampaignData.get(server.getServer().overworld()),h);
                sp.displayClientMessage(problem==null?Component.translatable("gui.arsenal_beacon.tactical.satellite_status",mk,TacticalRules.range(mk),TacticalRules.offers(mk))
                    :Component.translatable("gui.arsenal_beacon.tactical.satellite_problem",mk,Component.translatable("gui.arsenal_beacon.tactical.uplink."+problem)),true);
            }
            return InteractionResult.sidedSuccess(l.isClientSide);
        }
    }

    /** One Command Table per base. */
    static final class TableItem extends BaseZone.ZoneItem {
        TableItem(Block block){super(block);}
        @Override public void appendHoverText(ItemStack stack,Level level,java.util.List<Component> lines,net.minecraft.world.item.TooltipFlag flag){
            lines.add(Component.translatable("tooltip.arsenal_beacon.command_table").withStyle(net.minecraft.ChatFormatting.GRAY));super.appendHoverText(stack,level,lines,flag);
        }
        @Override String extra(BlockPlaceContext c){
            if(!(c.getLevel() instanceof ServerLevel server))return null;var d=HuntData.get(server);
            return d.table!=null&&server.hasChunkAt(d.table)&&server.getBlockState(d.table).is(ArsenalBeacon.COMMAND_TABLE.get())?"only_one_table":null;
        }
    }
    /** One Satellite Beacon per base; one moved with the shovel keeps its Mk. */
    static final class SatelliteItem extends BaseZone.ZoneItem {
        SatelliteItem(Block block){super(block);}
        @Override public void appendHoverText(ItemStack stack,Level level,java.util.List<Component> lines,net.minecraft.world.item.TooltipFlag flag){
            lines.add(Component.translatable("tooltip.arsenal_beacon.satellite_beacon").withStyle(net.minecraft.ChatFormatting.GRAY));super.appendHoverText(stack,level,lines,flag);
        }
        @Override String extra(BlockPlaceContext c){
            if(!(c.getLevel() instanceof ServerLevel server))return null;var d=HuntData.get(server);
            return d.satellite!=null&&server.hasChunkAt(d.satellite)&&server.getBlockState(d.satellite).is(ArsenalBeacon.SATELLITE_BEACON.get())?"only_one_satellite":null;
        }
    }

    /**
     * The table's menu: no slots. The board travels in the opening buffer; Accept, Abandon, Upgrade, Scan and Reprint are vanilla menu
     * buttons ({@link #clickMenuButton}), each checked again on the server ({@link Hunts#button}).
     */
    static final class TableMenu extends AbstractContainerMenu {
        final BlockPos pos;final CompoundTag data;
        TableMenu(int id,Inventory inv,BlockPos pos,CompoundTag data){super(ArsenalBeacon.TABLE_MENU.get(),id);this.pos=pos;this.data=data;}
        static TableMenu client(int id,Inventory inv,FriendlyByteBuf buf){var pos=buf.readBlockPos();var n=buf.readNbt();return new TableMenu(id,inv,pos,n==null?new CompoundTag():n);}
        @Override public boolean clickMenuButton(Player p,int id){if(p instanceof ServerPlayer sp)Hunts.button(sp,pos,id);return true;}
        @Override public ItemStack quickMoveStack(Player p,int index){return ItemStack.EMPTY;}
        @Override public boolean stillValid(Player p){return p.level().getBlockState(pos).is(ArsenalBeacon.COMMAND_TABLE.get())&&BaseZone.problem(p.level(),pos)==null&&p.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<=64;}
    }
    /** Button ids: action * 16 + argument (the offer's index on the board for Accept). */
    static final int ACCEPT=1,ABANDON=2,UPGRADE=3,SCAN=4,REPRINT=5;
    static int button(int action,int arg){return action*16+Math.max(0,Math.min(15,arg));}
}
