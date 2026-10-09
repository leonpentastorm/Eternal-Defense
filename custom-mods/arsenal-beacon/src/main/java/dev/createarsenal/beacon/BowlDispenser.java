package dev.createarsenal.beacon;

import net.minecraft.core.*;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.nbt.CompoundTag;
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

/** A two-cell kitchen installation. Dispensing consumes stock, including in creative. */
final class BowlDispenser {
    static boolean emptyHands(Player p){return p.getMainHandItem().isEmpty()&&p.getOffhandItem().isEmpty();}
    static final class DispenserBlock extends KitchenBlock {
        DispenserBlock(){super(false);}
        @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new DispenserEntity(pos,state);}
        @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
            if(hand!=InteractionHand.MAIN_HAND)return InteractionResult.CONSUME;
            if(player instanceof ServerPlayer p){
                if(BaseZone.disabled(level,pos,p))return InteractionResult.CONSUME;
                if(level.getBlockEntity(pos) instanceof DispenserEntity dispenser){
                    if(p.isShiftKeyDown()){
                        if(emptyHands(p))net.minecraftforge.network.NetworkHooks.openScreen(p,dispenser,b->b.writeBlockPos(pos));
                        else p.displayClientMessage(Component.translatable("gui.arsenal_beacon.dispenser.empty_hands"),true);
                    }
                    else dispenser.take(p,state,(net.minecraft.server.level.ServerLevel)level,pos);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        @Override public void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){
            if(!state.is(next.getBlock())&&level.getBlockEntity(pos) instanceof DispenserEntity dispenser)Containers.dropContents(level,pos,dispenser.stock);
            super.onRemove(state,level,pos,next,moving);
        }
    }
    static final class DispenserEntity extends BlockEntity implements MenuProvider {
        final SimpleContainer stock=new SimpleContainer(1){
            @Override public boolean canPlaceItem(int slot,ItemStack stack){return stack.is(Items.BOWL);}
            @Override public void setItem(int slot,ItemStack stack){if(stack.isEmpty()||stack.is(Items.BOWL))super.setItem(slot,stack);}
            @Override public void setChanged(){super.setChanged();DispenserEntity.this.setChanged();}
        };
        DispenserEntity(BlockPos pos,BlockState state){super(ArsenalBeacon.BOWL_DISPENSER_ENTITY.get(),pos,state);}
        boolean dispense(ServerPlayer player){
            if(!stock.getItem(0).is(Items.BOWL))return false;
            var bowl=stock.removeItem(0,1);ArsenalBeacon.give(player,bowl);setChanged();return true;
        }
        /** Hands out one bowl and says so: action bar line (low stock in gold), a pop of sound and a bowl flying out of the front. */
        void take(ServerPlayer player,BlockState state,net.minecraft.server.level.ServerLevel level,BlockPos pos){
            if(!stock.getItem(0).is(Items.BOWL)){
                player.displayClientMessage(Component.translatable("gui.arsenal_beacon.bowls.empty").withStyle(net.minecraft.ChatFormatting.RED),true);
                level.playSound(null,pos,SoundEvents.DISPENSER_FAIL,SoundSource.BLOCKS,.8f,1f);return;
            }
            var bowl=stock.removeItem(0,1);boolean fitted=player.getInventory().add(bowl);if(!fitted&&!bowl.isEmpty())player.drop(bowl,false);setChanged();
            int left=stock.getItem(0).getCount();
            var line=Component.translatable(left==0?"gui.arsenal_beacon.bowls.taken_last":left<=8?"gui.arsenal_beacon.bowls.taken_low":"gui.arsenal_beacon.bowls.taken",left);
            if(left<=8)line=line.withStyle(net.minecraft.ChatFormatting.GOLD);
            if(!fitted)line=line.append(Component.translatable("gui.arsenal_beacon.bowls.dropped"));
            player.displayClientMessage(line,true);
            level.playSound(null,pos,SoundEvents.DISPENSER_DISPENSE,SoundSource.BLOCKS,.6f,1.3f);level.playSound(null,player.blockPosition(),SoundEvents.ITEM_PICKUP,SoundSource.PLAYERS,.5f,1f+level.random.nextFloat()*.3f);
            var front=state.getValue(ArsenalStructures.FACING);
            level.sendParticles(new net.minecraft.core.particles.ItemParticleOption(net.minecraft.core.particles.ParticleTypes.ITEM,new ItemStack(Items.BOWL)),pos.getX()+.5+front.getStepX()*.55,pos.getY()+.7,pos.getZ()+.5+front.getStepZ()*.55,6,.12,.1,.12,.04);
        }
        @Override protected void saveAdditional(CompoundTag tag){super.saveAdditional(tag);tag.put("Bowls",stock.getItem(0).save(new CompoundTag()));}
        @Override public void load(CompoundTag tag){super.load(tag);stock.clearContent();var stack=ItemStack.of(tag.getCompound("Bowls"));if(stack.is(Items.BOWL))stock.setItem(0,stack.copyWithCount(Math.min(64,stack.getCount())));}
        @Override public Component getDisplayName(){return Component.translatable("block.arsenal_beacon.bowl_dispenser");}
        @Override public AbstractContainerMenu createMenu(int id,Inventory inv,Player player){return new DispenserMenu(id,inv,stock,worldPosition,this);}
    }
    static final class DispenserMenu extends AbstractContainerMenu {
        static final int STOCK_X=15,STOCK_Y=47,INV_Y=125;
        final Container stock;final BlockPos pos;final DispenserEntity owner;
        DispenserMenu(int id,Inventory inv,Container stock,BlockPos pos,DispenserEntity owner){
            super(ArsenalBeacon.BOWL_DISPENSER_MENU.get(),id);this.stock=stock;this.pos=pos;this.owner=owner;
            addSlot(new Slot(stock,0,STOCK_X,STOCK_Y){@Override public boolean mayPlace(ItemStack stack){return stack.is(Items.BOWL);}@Override public int getMaxStackSize(){return 64;}});
            for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inv,9+row*9+col,8+col*18,INV_Y+row*18));
            for(int col=0;col<9;col++)addSlot(new Slot(inv,col,8+col*18,INV_Y+58));
        }
        static DispenserMenu client(int id,Inventory inv,FriendlyByteBuf buf){return new DispenserMenu(id,inv,new SimpleContainer(1),buf.readBlockPos(),null);}
        @Override public boolean stillValid(Player p){return !p.isSpectator()&&p.level().hasChunkAt(pos)&&owner!=null&&p.level().getBlockEntity(pos)==owner&&BaseZone.problem(p.level(),pos)==null&&p.distanceToSqr(pos.getCenter())<=64;}
        @Override public ItemStack quickMoveStack(Player p,int index){
            if(index<0||index>=slots.size())return ItemStack.EMPTY;var slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;
            var stack=slot.getItem();var copy=stack.copy();
            if(index==0){if(!moveItemStackTo(stack,1,slots.size(),true))return ItemStack.EMPTY;}
            else if(!stack.is(Items.BOWL)||!moveItemStackTo(stack,0,1,false))return ItemStack.EMPTY;
            if(stack.isEmpty())slot.set(ItemStack.EMPTY);else slot.setChanged();slot.onTake(p,stack);return copy;
        }
    }
}
