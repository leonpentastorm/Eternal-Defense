package dev.createarsenal.beacon;

import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.*;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;

/** Milk is a real Forge fluid. Container transfers are all-or-nothing, including returned empties. */
final class MilkDispenser {
    static final int CAPACITY=8000,DOSE=250;
    record Fill(int amount,ItemStack empty){}
    static Fill contents(ItemStack stack){
        if(stack.isEmpty())return null;
        if(stack.is(Items.MILK_BUCKET))return new Fill(1000,new ItemStack(Items.BUCKET));
        if(stack.is(ArsenalBeacon.MILK_BOTTLE.get()))return new Fill(DOSE,new ItemStack(Items.GLASS_BOTTLE));
        var handler=FluidUtil.getFluidHandler(stack.copyWithCount(1)).resolve();if(handler.isEmpty())return null;
        var fluid=handler.get().drain(Integer.MAX_VALUE,IFluidHandler.FluidAction.SIMULATE);
        if(fluid.isEmpty()||!fluid.getFluid().isSame(ForgeMod.MILK.get())||fluid.getAmount()>CAPACITY)return null;
        var drained=handler.get().drain(fluid.getAmount(),IFluidHandler.FluidAction.EXECUTE);
        return drained.isFluidEqual(fluid)&&drained.getAmount()==fluid.getAmount()?new Fill(drained.getAmount(),handler.get().getContainer().copy()):null;
    }
    static void cleanse(ServerPlayer player){PlayerMeals.clear(player);player.removeAllEffects();}
    static final class DispenserBlock extends KitchenBlock {
        DispenserBlock(){super(false);}
        @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new DispenserEntity(pos,state);}
        @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
            if(hand!=InteractionHand.MAIN_HAND)return InteractionResult.CONSUME;
            if(player instanceof ServerPlayer p){
                if(BaseZone.disabled(level,pos,p))return InteractionResult.CONSUME;
                if(level.getBlockEntity(pos) instanceof DispenserEntity dispenser){
                    if(p.isShiftKeyDown()){
                        if(BowlDispenser.emptyHands(p))net.minecraftforge.network.NetworkHooks.openScreen(p,dispenser,b->b.writeBlockPos(pos));
                        else p.displayClientMessage(Component.translatable("gui.arsenal_beacon.dispenser.empty_hands"),true);
                    }else if(!dispenser.dispense(p))p.displayClientMessage(Component.translatable("gui.arsenal_beacon.milk.empty"),true);
                }
            }return InteractionResult.sidedSuccess(level.isClientSide);
        }
        @Override public void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){
            if(!state.is(next.getBlock())&&level.getBlockEntity(pos) instanceof DispenserEntity dispenser)Containers.dropContents(level,pos,dispenser.stock);
            super.onRemove(state,level,pos,next,moving);
        }
    }
    static final class DispenserEntity extends BlockEntity implements MenuProvider {
        final FluidTank tank=new FluidTank(CAPACITY,fluid->fluid.getFluid().isSame(ForgeMod.MILK.get())){
            @Override protected void onContentsChanged(){DispenserEntity.this.setChanged();}
        };
        final SimpleContainer stock=new SimpleContainer(2){@Override public void setChanged(){super.setChanged();DispenserEntity.this.setChanged();}};
        private LazyOptional<IFluidHandler> capability=LazyOptional.of(()->tank);
        DispenserEntity(BlockPos pos,BlockState state){super(ArsenalBeacon.MILK_DISPENSER_ENTITY.get(),pos,state);}
        boolean refill(){
            var fill=contents(stock.getItem(0));if(fill==null||tank.getSpace()<fill.amount())return false;
            var output=stock.getItem(1);var empty=fill.empty();
            if(!empty.isEmpty()&&!output.isEmpty()&&(!ItemStack.isSameItemSameTags(output,empty)||output.getCount()+empty.getCount()>output.getMaxStackSize()))return false;
            tank.fill(new FluidStack(ForgeMod.MILK.get(),fill.amount()),IFluidHandler.FluidAction.EXECUTE);stock.removeItem(0,1);
            if(!empty.isEmpty()){if(output.isEmpty())stock.setItem(1,empty);else{output.grow(empty.getCount());stock.setChanged();}}
            setChanged();return true;
        }
        boolean dispense(ServerPlayer player){
            if(tank.getFluidAmount()<DOSE)return false;tank.drain(DOSE,IFluidHandler.FluidAction.EXECUTE);cleanse(player);return true;
        }
        @Override public <T> LazyOptional<T> getCapability(Capability<T> cap,Direction side){return cap==ForgeCapabilities.FLUID_HANDLER?capability.cast():super.getCapability(cap,side);}
        @Override public void invalidateCaps(){super.invalidateCaps();capability.invalidate();}
        @Override public void reviveCaps(){super.reviveCaps();capability=LazyOptional.of(()->tank);}
        @Override protected void saveAdditional(CompoundTag n){super.saveAdditional(n);n.put("Tank",tank.writeToNBT(new CompoundTag()));n.put("Input",stock.getItem(0).save(new CompoundTag()));n.put("Output",stock.getItem(1).save(new CompoundTag()));}
        @Override public void load(CompoundTag n){
            super.load(n);tank.readFromNBT(n.getCompound("Tank"));
            if(!tank.isEmpty()&&!tank.isFluidValid(tank.getFluid()))tank.setFluid(FluidStack.EMPTY);
            if(tank.getFluidAmount()>CAPACITY)tank.setFluid(new FluidStack(tank.getFluid(),CAPACITY));
            stock.setItem(0,ItemStack.of(n.getCompound("Input")));stock.setItem(1,ItemStack.of(n.getCompound("Output")));
        }
        @Override public Component getDisplayName(){return Component.translatable("block.arsenal_beacon.milk_dispenser");}
        @Override public AbstractContainerMenu createMenu(int id,Inventory inv,Player player){return new DispenserMenu(id,inv,stock,worldPosition,this);}
    }
    static final class DispenserMenu extends AbstractContainerMenu {
        final Container stock;final BlockPos pos;final DispenserEntity owner;final ContainerData data;
        DispenserMenu(int id,Inventory inv,Container stock,BlockPos pos,DispenserEntity owner){
            super(ArsenalBeacon.MILK_DISPENSER_MENU.get(),id);this.stock=stock;this.pos=pos;this.owner=owner;
            addSlot(new Slot(stock,0,44,43){@Override public boolean mayPlace(ItemStack stack){return contents(stack)!=null;}});
            addSlot(new Slot(stock,1,80,43){@Override public boolean mayPlace(ItemStack stack){return false;}});
            data=owner==null?new SimpleContainerData(1):new ContainerData(){public int get(int index){return owner.tank.getFluidAmount();}public void set(int index,int value){}public int getCount(){return 1;}};addDataSlots(data);
            for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inv,9+row*9+col,8+col*18,101+row*18));
            for(int col=0;col<9;col++)addSlot(new Slot(inv,col,8+col*18,159));
        }
        static DispenserMenu client(int id,Inventory inv,FriendlyByteBuf buf){return new DispenserMenu(id,inv,new SimpleContainer(2),buf.readBlockPos(),null);}
        @Override public void broadcastChanges(){if(owner!=null)owner.refill();super.broadcastChanges();}
        @Override public boolean stillValid(Player p){return !p.isSpectator()&&owner!=null&&p.level().hasChunkAt(pos)&&p.level().getBlockEntity(pos)==owner&&BaseZone.problem(p.level(),pos)==null&&p.distanceToSqr(pos.getCenter())<=64;}
        @Override public ItemStack quickMoveStack(Player p,int index){
            if(index<0||index>=slots.size())return ItemStack.EMPTY;var slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;var stack=slot.getItem();var copy=stack.copy();
            if(index<2){if(!moveItemStackTo(stack,2,slots.size(),true))return ItemStack.EMPTY;}else if(contents(stack)==null||!moveItemStackTo(stack,0,1,false))return ItemStack.EMPTY;
            if(stack.isEmpty())slot.set(ItemStack.EMPTY);else slot.setChanged();slot.onTake(p,stack);return copy;
        }
    }
    static final class MilkBottle extends Item {
        MilkBottle(){super(new Item.Properties().stacksTo(16).craftRemainder(Items.GLASS_BOTTLE));}
        @Override public net.minecraft.world.item.UseAnim getUseAnimation(ItemStack stack){return UseAnim.DRINK;}
        @Override public int getUseDuration(ItemStack stack){return 32;}
        @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){return net.minecraft.world.item.ItemUtils.startUsingInstantly(level,player,hand);}
        @Override public ItemStack finishUsingItem(ItemStack stack,Level level,net.minecraft.world.entity.LivingEntity entity){
            if(entity instanceof ServerPlayer p)cleanse(p);
            if(entity instanceof Player p&&!p.getAbilities().instabuild){stack.shrink(1);if(stack.isEmpty())return new ItemStack(Items.GLASS_BOTTLE);if(!level.isClientSide&&p instanceof ServerPlayer sp)ArsenalBeacon.give(sp,new ItemStack(Items.GLASS_BOTTLE));}
            return stack;
        }
    }
}
