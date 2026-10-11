package dev.createarsenal.beacon;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.List;

/**
 * The FOB Beacon (0.0.23), the owner's FOB station model ({@code models/block/fob_station.json}): one block wide and deep, two tall, the upper
 * cell an invisible structure part as for the Exchange Shop. Planted in a vanilla village outside the beacon zone; it cannot be mined, blown up
 * or pushed, and only the Recovery Shovel takes it back ({@link Fobs#requestRemoval}). Everything it does is in {@link Fobs}.
 */
final class FobBlocks {
    private FobBlocks(){}
    static final DirectionProperty FACING=HorizontalDirectionalBlock.FACING;
    /** From the owner's model: y 0 to 32 px (two blocks), x and z inside one block (the flag's tip reaches 0.2 px past it). See FobTest. */
    static final int TALL=2,WIDE=1,DEEP=1;

    static final class FobBlock extends Block {
        FobBlock(){super(Properties.of().strength(-1f,3600000f).noOcclusion().lightLevel(s->7).sound(SoundType.METAL));registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH));}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING);}
        @Override public BlockState getStateForPlacement(BlockPlaceContext c){return defaultBlockState().setValue(FACING,c.getHorizontalDirection().getOpposite());}
        @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return ArsenalStructures.cell(s,0,0,0);}
        @Override public PushReaction getPistonPushReaction(BlockState s){return PushReaction.BLOCK;}
        /** Withers and the dragon cannot break it either. */
        @Override public boolean canEntityDestroy(BlockState s,BlockGetter l,BlockPos pos,Entity e){return false;}
        @Override public void onPlace(BlockState s,Level l,BlockPos pos,BlockState old,boolean moving){super.onPlace(s,l,pos,old,moving);if(l instanceof ServerLevel)ArsenalStructures.install(l,pos,s);}
        @Override public void tick(BlockState s,ServerLevel l,BlockPos pos,net.minecraft.util.RandomSource random){ArsenalStructures.install(l,pos,s);}
        @Override public void setPlacedBy(Level l,BlockPos pos,BlockState s,LivingEntity who,ItemStack stack){
            super.setPlacedBy(l,pos,s,who,stack);
            if(l instanceof ServerLevel server&&who instanceof ServerPlayer p)Fobs.planted(server,pos,p);
        }
        @Override public void onRemove(BlockState s,Level l,BlockPos pos,BlockState next,boolean moving){
            if(!s.is(next.getBlock())){ArsenalStructures.remove(l,pos);if(l instanceof ServerLevel server)Fobs.blockGone(server,pos);}
            super.onRemove(s,l,pos,next,moving);
        }
        /** The shovel asks to take it back, a renamed Name Tag renames the FOB, anything else opens the FOB screen. */
        @Override public InteractionResult use(BlockState s,Level l,BlockPos pos,Player p,InteractionHand hand,BlockHitResult hit){
            if(p instanceof ServerPlayer sp){
                var stack=sp.getItemInHand(hand);
                if(stack.is(ArsenalBeacon.CONTROLLER.get()))Fobs.requestRemoval(sp,pos);
                else if(stack.is(Items.NAME_TAG))Fobs.rename(sp,pos,stack);
                else Fobs.open(sp,pos);
            }
            return InteractionResult.sidedSuccess(l.isClientSide);
        }
    }

    /** Refuses, with the reason, every spot the FOB rules do not allow ({@link Fobs#plantProblem}) and a spot without room for both cells. */
    static final class FobItem extends BlockItem {
        FobItem(Block block){super(block,new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));}
        @Override public InteractionResult place(BlockPlaceContext c){
            if(c.getLevel() instanceof ServerLevel server){
                String problem=Fobs.plantProblem(server,c.getClickedPos());
                if(problem!=null){if(c.getPlayer() instanceof ServerPlayer p)Fobs.say(p,"fob.refused."+problem,FobRules.MAX_FOBS,FobRules.MIN_VILLAGERS);return InteractionResult.FAIL;}
                var state=getBlock().getStateForPlacement(c);
                if(state==null||!ArsenalStructures.available(server,c.getClickedPos(),state)){if(c.getPlayer() instanceof ServerPlayer p)Fobs.say(p,"fob.refused.no_room");return InteractionResult.FAIL;}
            }
            return super.place(c);
        }
        @Override public void appendHoverText(ItemStack stack,Level level,List<Component> lines,TooltipFlag flag){
            lines.add(Component.translatable("tooltip.arsenal_beacon.fob_beacon").withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("tooltip.arsenal_beacon.fob_beacon.rules",FobRules.MIN_VILLAGERS,FobRules.MAX_FOBS).withStyle(ChatFormatting.DARK_AQUA));
            lines.add(Component.translatable("tooltip.arsenal_beacon.fob_beacon.raid").withStyle(ChatFormatting.RED));
        }
    }

    /**
     * The FOB screen's menu: no slots. The FOB's state travels in the opening buffer and is sent again in place ({@link Hunts.TableState});
     * the shop and the raid call are vanilla menu buttons, each checked again on the server ({@link Fobs#button}).
     */
    static final class FobMenu extends AbstractContainerMenu {
        final BlockPos pos;final CompoundTag data;
        FobMenu(int id,Inventory inv,BlockPos pos,CompoundTag data){super(ArsenalBeacon.FOB_MENU.get(),id);this.pos=pos;this.data=data;}
        static FobMenu client(int id,Inventory inv,FriendlyByteBuf buf){var pos=buf.readBlockPos();var n=buf.readNbt();return new FobMenu(id,inv,pos,n==null?new CompoundTag():n);}
        @Override public boolean clickMenuButton(Player p,int id){if(p instanceof ServerPlayer sp)Fobs.button(sp,pos,id);return true;}
        @Override public ItemStack quickMoveStack(Player p,int index){return ItemStack.EMPTY;}
        @Override public boolean stillValid(Player p){return p.level().getBlockState(pos).is(ArsenalBeacon.FOB_BEACON.get())&&p.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<=64;}
    }
}
