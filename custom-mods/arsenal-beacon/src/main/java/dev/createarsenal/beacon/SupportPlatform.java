package dev.createarsenal.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;
import java.util.*;

/** The Support Platform: supply grid, shop and Mk upgrades. One per world, in the Overworld, with its front kept clear. */
final class SupportPlatform {
    private SupportPlatform(){}
    static final IntegerProperty MK=IntegerProperty.create("mk",1,4);
    static final DirectionProperty FACING=HorizontalDirectionalBlock.FACING;
    static final int GRID_LEFT=12,GRID_TOP=60,INV_LEFT=134,INV_TOP=60;

    static final class PlatformBlock extends Block implements EntityBlock {
        private static final VoxelShape SHAPE=Block.box(0,0,0,16,10,16);
        PlatformBlock(){super(Properties.of().strength(3,6).noOcclusion().lightLevel(s->6).sound(SoundType.METAL));registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(MK,1));}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING,MK);}
        @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return SHAPE;}
        @Override public BlockState getStateForPlacement(BlockPlaceContext c){
            Direction d=c.getHorizontalDirection().getOpposite();var level=c.getLevel();BlockPos pos=c.getClickedPos();BlockPos front=pos.relative(d);
            var player=c.getPlayer();
            if(!level.getBlockState(front).canBeReplaced()&&!level.getBlockState(front).getCollisionShape(level,front).isEmpty()){
                if(player!=null)player.displayClientMessage(Component.translatable("gui.arsenal_beacon.support.blocked"),true);return null;
            }
            if(level instanceof ServerLevel server){
                if(server.dimension()!=Level.OVERWORLD){if(player!=null)player.displayClientMessage(Component.translatable("gui.arsenal_beacon.support.overworld"),true);return null;}
                var data=SupportData.get(server);
                if(data.hasPlatform()&&!data.platform.equals(pos)&&server.getBlockState(data.platform).getBlock()==ArsenalBeacon.SUPPORT_PLATFORM.get()){
                    if(player!=null)player.displayClientMessage(Component.translatable("gui.arsenal_beacon.support.only_one"),true);return null;
                }
            }
            return defaultBlockState().setValue(FACING,d);
        }
        @Override public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos,BlockState s){return new PlatformEntity(pos,s);}
        @Override public void onPlace(BlockState s,Level l,BlockPos pos,BlockState old,boolean moving){
            super.onPlace(s,l,pos,old,moving);
            if(l instanceof ServerLevel server){var d=SupportData.get(server);d.platform=pos.immutable();d.facing=s.getValue(FACING);d.mk=s.getValue(MK);d.setDirty();}
        }
        @Override public void onRemove(BlockState s,Level l,BlockPos pos,BlockState next,boolean moving){
            if(!s.is(next.getBlock())){
                if(l.getBlockEntity(pos) instanceof PlatformEntity be){Containers.dropContents(l,pos,new SimpleContainer(be.grid.items));}
                if(l instanceof ServerLevel server){var d=SupportData.get(server);if(pos.equals(d.platform)){d.platform=null;Arrays.fill(d.grid,ItemStack.EMPTY);d.mk=1;d.setDirty();}}
            }
            super.onRemove(s,l,pos,next,moving);
        }
        @Override public InteractionResult use(BlockState s,Level l,BlockPos pos,Player p,InteractionHand hand,BlockHitResult hit){
            if(!l.isClientSide&&p instanceof ServerPlayer sp&&l.getBlockEntity(pos) instanceof PlatformEntity be)
                NetworkHooks.openScreen(sp,be,buf->{buf.writeBlockPos(pos);buf.writeVarInt(s.getValue(MK));});
            return InteractionResult.sidedSuccess(l.isClientSide);
        }
    }

    static final class PlatformEntity extends BlockEntity implements MenuProvider {
        final GridContainer grid=new GridContainer(this::gridChanged);
        PlatformEntity(BlockPos pos,BlockState s){super(ArsenalBeacon.SUPPORT_PLATFORM_ENTITY.get(),pos,s);}
        void gridChanged(){
            setChanged();
            if(level instanceof ServerLevel server&&getBlockPos().equals(SupportData.get(server).platform)){
                var d=SupportData.get(server);d.mk=getBlockState().getValue(MK);d.facing=getBlockState().getValue(FACING);
                for(int i=0;i<36;i++)d.grid[i]=grid.items[i].copy();d.setDirty();
            }
        }
        @Override public void onLoad(){super.onLoad();if(level instanceof ServerLevel)gridChanged();}
        @Override protected void saveAdditional(CompoundTag n){
            super.saveAdditional(n);var list=new ListTag();
            for(int i=0;i<36;i++)if(!grid.items[i].isEmpty()){var c=new CompoundTag();grid.items[i].save(c);c.putInt("Slot",i);list.add(c);}
            n.put("Grid",list);
        }
        @Override public void load(CompoundTag n){
            super.load(n);java.util.Arrays.fill(grid.items,ItemStack.EMPTY);
            for(var t:n.getList("Grid",Tag.TAG_COMPOUND)){var c=(CompoundTag)t;int slot=c.getInt("Slot");if(slot>=0&&slot<36)grid.items[slot]=ItemStack.of(c);}
        }
        @Override public Component getDisplayName(){return Component.translatable("block.arsenal_beacon.support_platform");}
        @Override public AbstractContainerMenu createMenu(int id,Inventory inv,Player p){return new PlatformMenu(id,inv,grid,getBlockPos(),getBlockState().getValue(MK));}
    }

    /** Grid + player inventory. Costs and purchase feedback travel as synced data slots, so no extra packets are needed. */
    static final class PlatformMenu extends AbstractContainerMenu {
        final GridContainer grid;final BlockPos pos;final int mk;
        final DataSlot cost=DataSlot.standalone(),result=DataSlot.standalone(),serial=DataSlot.standalone();
        /** Client only: slots are hidden while the Shop or Upgrade tab is showing. */
        boolean slotsVisible=true;
        final List<Slot> gridSlots=new ArrayList<>();
        PlatformMenu(int id,Inventory inv,GridContainer grid,BlockPos pos,int mk){
            super(ArsenalBeacon.SUPPORT_MENU.get(),id);this.grid=grid;this.pos=pos;this.mk=mk;
            int n=SupportRules.grid(mk);
            for(int r=0;r<n;r++)for(int c=0;c<n;c++){var slot=new Slot(grid,r*6+c,GRID_LEFT+1+c*18,GRID_TOP+1+r*18){@Override public boolean isActive(){return slotsVisible;}};gridSlots.add(slot);addSlot(slot);}
            for(int r=0;r<3;r++)for(int c=0;c<9;c++)addSlot(new Slot(inv,9+r*9+c,INV_LEFT+1+c*18,INV_TOP+1+r*18){@Override public boolean isActive(){return slotsVisible;}});
            for(int c=0;c<9;c++)addSlot(new Slot(inv,c,INV_LEFT+1+c*18,INV_TOP+1+58){@Override public boolean isActive(){return slotsVisible;}});
            addDataSlot(cost);addDataSlot(result);addDataSlot(serial);
            if(!inv.player.level().isClientSide)recompute();
        }
        static PlatformMenu client(int id,Inventory inv,FriendlyByteBuf buf){return new PlatformMenu(id,inv,new GridContainer(null),buf.readBlockPos(),buf.readVarInt());}
        /** Re-prices the grid; cheap enough to run on every change. */
        void recompute(){
            var entries=new ArrayList<SupportCosts.Entry>();
            for(var slot:gridSlots){var s=slot.getItem();if(!s.isEmpty())entries.add(SupportCalls.entry(s));}
            cost.set(Math.min(32767,SupportCosts.cost(entries,SupportCalls.overrides())));
        }
        @Override public void slotsChanged(Container c){super.slotsChanged(c);recompute();}
        void report(int code){result.set(code);serial.set((serial.get()+1)&0x7fff);}
        @Override public ItemStack quickMoveStack(Player p,int index){
            var slot=slots.get(index);if(slot==null||!slot.hasItem())return ItemStack.EMPTY;
            var stack=slot.getItem();var copy=stack.copy();int gridCount=gridSlots.size();
            if(index<gridCount){if(!moveItemStackTo(stack,gridCount,slots.size(),true))return ItemStack.EMPTY;}
            else if(!moveItemStackTo(stack,0,gridCount,false))return ItemStack.EMPTY;
            if(stack.isEmpty())slot.set(ItemStack.EMPTY);else slot.setChanged();
            return copy;
        }
        @Override public boolean stillValid(Player p){return p.level().getBlockState(pos).getBlock()==ArsenalBeacon.SUPPORT_PLATFORM.get()&&p.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<=64;}
    }
}
