package dev.createarsenal.beacon;

import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

final class MessHall {
    private MessHall(){}
    static final class HallBlock extends KitchenBlock {
        final int mk;
        HallBlock(int mk){super(true);this.mk=mk;}
        @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new HallEntity(pos,state);}
        @Override public void onRemove(BlockState s,net.minecraft.world.level.Level l,BlockPos p,BlockState next,boolean moving){if(!s.is(next.getBlock())&&!(next.getBlock() instanceof HallBlock)&&l.getBlockEntity(p) instanceof HallEntity h)Containers.dropContents(l,p,h.ingredients);super.onRemove(s,l,p,next,moving);}
    }
    static final class HallEntity extends BlockEntity implements MenuProvider {
        UUID identity=UUID.randomUUID();final List<BlockPos> links=new ArrayList<>();
        final SimpleContainer ingredients=new SimpleContainer(8){@Override public void setChanged(){super.setChanged();HallEntity.this.setChanged();}};
        HallEntity(BlockPos pos,BlockState state){super(ArsenalBeacon.MESS_HALL_ENTITY.get(),pos,state);}
        int mk(){return ((HallBlock)getBlockState().getBlock()).mk;}
        MealRules.Tier tier(){return MealRules.tier(mk());}
        boolean upgrade(net.minecraft.server.level.ServerPlayer p){
            var price=Economy.kitchen(mk());if(price==null||BaseZone.problem(level,worldPosition)!=null)return false;
            var next=switch(mk()){case 1->ArsenalBeacon.MESS_HALL_II.get();case 2->ArsenalBeacon.MESS_HALL_III.get();default->ArsenalBeacon.MESS_HALL_IV.get();};
            var state=next.defaultBlockState().setValue(ArsenalStructures.FACING,getBlockState().getValue(ArsenalStructures.FACING));
            if(!ArsenalStructures.available(level,worldPosition,state)||!Economy.pay(p,price))return false;
            var saved=saveWithoutMetadata();level.setBlock(worldPosition,state,3);var replacement=(HallEntity)level.getBlockEntity(worldPosition);replacement.load(saved);replacement.setChanged();
            net.minecraftforge.network.NetworkHooks.openScreen(p,replacement,b->{b.writeBlockPos(worldPosition);b.writeVarInt(replacement.mk());});return true;
        }
        boolean inRange(BlockPos pos){return worldPosition.distSqr(pos)<=MealRules.LINK_RANGE*MealRules.LINK_RANGE;}
        /** Keep unloaded reservations; prune only when a loaded cell proves the old pot is gone. */
        void prune(){if(level==null||level.isClientSide)return;boolean changed=links.removeIf(p->level.hasChunkAt(p)&&(!(level.getBlockEntity(p) instanceof CookPot.PotEntity pot)||!pot.belongs(this)));if(changed)setChanged();}
        boolean link(CookPot.PotEntity pot){
            prune();if(!inRange(pot.getBlockPos())||pot.getLevel()!=level||BaseZone.problem(level,worldPosition)!=null)return false;
            if(links.contains(pot.getBlockPos())&&pot.belongs(this))return true;
            if(links.size()>=tier().pots()||pot.validLink()!=null||pot.waitingForHall())return false;
            pot.linkedHall=worldPosition;pot.hallIdentity=identity;pot.changed();links.add(pot.getBlockPos().immutable());setChanged();return true;
        }
        List<CookPot.PotEntity> pots(){prune();var out=new ArrayList<CookPot.PotEntity>();for(var p:links)if(level.hasChunkAt(p)&&level.getBlockEntity(p) instanceof CookPot.PotEntity pot&&pot.belongs(this))out.add(pot);out.sort(Comparator.comparingLong(p->p.getBlockPos().asLong()));return out;}
        void discover(){
            if(level==null||level.isClientSide)return;prune();var candidates=new ArrayList<CookPot.PotEntity>();int r=MealRules.LINK_RANGE;
            for(var p:BlockPos.betweenClosed(worldPosition.offset(-r,-r,-r),worldPosition.offset(r,r,r)))if(inRange(p)&&level.hasChunkAt(p)&&level.getBlockEntity(p) instanceof CookPot.PotEntity pot)candidates.add(pot);
            candidates.sort(Comparator.<CookPot.PotEntity>comparingDouble(p->worldPosition.distSqr(p.getBlockPos())).thenComparingLong(p->p.getBlockPos().asLong()));
            for(var pot:candidates){if(links.size()>=tier().pots())break;link(pot);}
        }
        void removed(){if(level!=null&&!level.isClientSide)for(var p:List.copyOf(links))if(level.hasChunkAt(p)&&level.getBlockEntity(p) instanceof CookPot.PotEntity pot&&pot.belongs(this))pot.unlink();}
        @Override protected void saveAdditional(CompoundTag n){super.saveAdditional(n);n.putUUID("Identity",identity);var list=new ListTag();for(var p:links){var t=new CompoundTag();t.putLong("Pos",p.asLong());list.add(t);}n.put("Pots",list);net.minecraft.world.ContainerHelper.saveAllItems(n,items());}
        private net.minecraft.core.NonNullList<ItemStack> items(){var out=net.minecraft.core.NonNullList.withSize(8,ItemStack.EMPTY);for(int i=0;i<8;i++)out.set(i,ingredients.getItem(i));return out;}
        @Override public void load(CompoundTag n){super.load(n);if(n.hasUUID("Identity"))identity=n.getUUID("Identity");links.clear();for(var t:n.getList("Pots",Tag.TAG_COMPOUND)){var p=BlockPos.of(((CompoundTag)t).getLong("Pos"));if(inRange(p)&&!links.contains(p)&&links.size()<4)links.add(p);}var items=net.minecraft.core.NonNullList.withSize(8,ItemStack.EMPTY);net.minecraft.world.ContainerHelper.loadAllItems(n,items);for(int i=0;i<8;i++)ingredients.setItem(i,items.get(i));}
        @Override public Component getDisplayName(){return getBlockState().getBlock().getName();}
        @Override public AbstractContainerMenu createMenu(int id,Inventory inv,Player player){return new MessHallMenu(id,inv,ingredients,worldPosition,mk(),this);}
    }
}
