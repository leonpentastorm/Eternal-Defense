package dev.createarsenal.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import java.util.*;
import java.util.function.Supplier;

/** Standard container slots and button packets; the client sends an intent, never a meal or serving count. */
final class MessHallMenu extends AbstractContainerMenu {
    final Container ingredients;final BlockPos pos;final int mk;final MessHall.HallEntity hall;final Player player;
    boolean stew,explicitSelection;final EnumSet<MealRules.Effect> effects=EnumSet.noneOf(MealRules.Effect.class);BlockPos selected;CompoundTag view=new CompoundTag(),lastView;
    List<BlockPos> offered=List.of();
    List<IngredientTraits.Trait> catalogTraits;final Map<MealRules.Effect,ListTag> sourceCatalog=new EnumMap<>(MealRules.Effect.class);
    MessHallMenu(int id,Inventory inv,Container ingredients,BlockPos pos,int mk,MessHall.HallEntity hall){
        super(ArsenalBeacon.MESS_HALL_MENU.get(),id);this.ingredients=ingredients;this.pos=pos;this.mk=mk;this.hall=hall;this.player=inv.player;
        for(int i=0;i<6;i++)addSlot(new Slot(ingredients,i,12+i*18,53){@Override public boolean mayPlace(ItemStack stack){return player.level().isClientSide||IngredientTraits.accepts(stack);}});
        addSlot(new Slot(ingredients,6,166,53){
            @Override public boolean mayPlace(ItemStack stack){return false;}
            @Override public boolean isActive(){return !stew;}
            @Override public boolean mayPickup(Player p){return !stew;}
        });
        addSlot(new Slot(ingredients,7,142,53){
            @Override public boolean mayPlace(ItemStack stack){return stack.is(net.minecraft.world.item.Items.BREAD);}
            @Override public boolean isActive(){return !stew;}
            @Override public boolean mayPickup(Player p){return !stew;}
        });
        for(int r=0;r<3;r++)for(int c=0;c<9;c++)addSlot(new Slot(inv,9+r*9+c,12+c*18,154+r*18));
        for(int c=0;c<9;c++)addSlot(new Slot(inv,c,12+c*18,212));
    }
    static MessHallMenu client(int id,Inventory inv,FriendlyByteBuf buf){return new MessHallMenu(id,inv,new SimpleContainer(8),buf.readBlockPos(),buf.readVarInt(),null);}
    @Override public boolean stillValid(Player p){return !p.isSpectator()&&p.level().hasChunkAt(pos)&&p.level().getBlockEntity(pos)==hall&&hall!=null&&BaseZone.problem(p.level(),pos)==null&&p.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<=64;}
    private List<CookPot.PotEntity> pots(){return hall.pots();}
    private CookPot.PotEntity target(){if(selected==null)return null;return pots().stream().filter(p->p.getBlockPos().equals(selected)).findFirst().orElse(null);}
    IngredientTraits.Preview composition(){return IngredientTraits.compose(ingredients,stew,hall.mk(),explicitSelection?effects:null);}
    String problem(IngredientTraits.Preview preview){
        if(!preview.problem().isEmpty())return preview.problem();
        var batch=IngredientTraits.batch(ingredients,preview,stew,hall.mk());if(!batch.problem().isEmpty())return batch.problem();
        if(stew){var pot=target();return pot==null?"pot":!pot.empty()?"pot_full":"";}
        if(!ingredients.getItem(7).is(net.minecraft.world.item.Items.BREAD))return "staple";
        var made=PreparedSandwich.create(preview.meal());var output=ingredients.getItem(6);
        return output.isEmpty()||ItemStack.isSameItemSameTags(output,made)&&output.getCount()<made.getMaxStackSize()?"":"output";
    }
    boolean prepare(){
        if(!stillValid(player))return false;var preview=composition();if(!problem(preview).isEmpty())return false;
        var batch=IngredientTraits.batch(ingredients,preview,stew,hall.mk());
        if(stew){if(!target().fill(hall,preview.meal(),hall.tier().servings()))return false;}
        else{ingredients.removeItem(7,1);var output=ingredients.getItem(6);if(output.isEmpty())ingredients.setItem(6,PreparedSandwich.create(preview.meal()));else{output.grow(1);ingredients.setChanged();}}
        for(int i=0;i<6;i++)if(batch.spent()[i]>0){
            var unit=ingredients.getItem(i).copyWithCount(1);ingredients.removeItem(i,batch.spent()[i]);
            if(unit.hasCraftingRemainingItem()&&player instanceof ServerPlayer sp)for(int count=0;count<batch.spent()[i];count++)ArsenalBeacon.give(sp,unit.getCraftingRemainingItem().copy());
        }
        broadcastChanges();return true;
    }
    @Override public boolean clickMenuButton(Player p,int button){
        if(!stillValid(p)||p!=player)return false;
        if(button==0||button==1){stew=button==1;if(effects.size()>(stew?3:2)){effects.clear();explicitSelection=false;}hall.discover();}
        else if(button==2)return prepare();
        else if(button==3&&p instanceof ServerPlayer sp)return hall.upgrade(sp);
        else if(button==4){explicitSelection=false;effects.clear();}
        else if(button>=40&&button<40+MealRules.Effect.values().length){
            var effect=MealRules.Effect.values()[button-40];var next=explicitSelection?EnumSet.copyOf(effects):EnumSet.noneOf(MealRules.Effect.class);
            if(!next.remove(effect)){
                next.add(effect);
                if(IngredientTraits.compose(ingredients,stew,hall.mk(),next).meal()==null)return false;
            }
            effects.clear();effects.addAll(next);explicitSelection=true;
        }
        else if(button>=100&&button<100+offered.size())selected=offered.get(button-100);
        else return false;
        broadcastChanges();return true;
    }
    @Override public void broadcastChanges(){
        super.broadcastChanges();if(!(player instanceof ServerPlayer sp)||hall==null)return;
        if(sp.tickCount%100==0)hall.discover();var pots=pots();offered=pots.stream().map(CookPot.PotEntity::getBlockPos).toList();
        if(selected==null||!offered.contains(selected))selected=pots.stream().filter(CookPot.PotEntity::empty).map(CookPot.PotEntity::getBlockPos).findFirst().orElse(null);
        var preview=composition();var n=new CompoundTag();n.putBoolean("StewMode",stew);n.putString("Problem",problem(preview));n.putInt("Servings",stew?hall.tier().servings():1);n.putInt("Capacity",hall.tier().pots());n.putInt("Linked",hall.links.size());
        var batch=IngredientTraits.batch(ingredients,preview,stew,hall.mk());n.putInt("Required",batch.required());n.putInt("Available",batch.available());if(batch.spent()!=null)n.putIntArray("Spent",batch.spent());
        if(preview.meal()!=null)n.put("Meal",preview.meal().save());
        var links=IngredientTraits.legendaryLinks(ingredients,preview.meal());var linkTags=new ListTag();var slotEffects=new int[6][];
        for(int i=0;i<6;i++)slotEffects[i]=IngredientTraits.effectsOf(ingredients.getItem(i)).stream().mapToInt(Enum::ordinal).toArray();
        for(var link:links){var row=new CompoundTag();row.putInt("Effect",link.mix().effect().ordinal());row.putIntArray("Slots",link.slots());row.putString("First",link.mix().first());row.putString("Second",link.mix().second());row.putBoolean("Doubled",link.doubled());linkTags.add(row);for(int slot:link.slots())slotEffects[slot]=new int[]{link.mix().effect().ordinal()};}
        n.putBoolean("ExplicitSelection",explicitSelection);n.putIntArray("SelectedEffects",effects.stream().mapToInt(Enum::ordinal).toArray());
        var choices=new ListTag();for(var effect:MealRules.Effect.values()){
            var row=new CompoundTag();row.putInt("Effect",effect.ordinal());row.putBoolean("Selected",effects.contains(effect)&&explicitSelection);
            var next=explicitSelection?EnumSet.copyOf(effects):EnumSet.noneOf(MealRules.Effect.class);next.add(effect);
            var candidate=IngredientTraits.compose(ingredients,stew,hall.mk(),next).meal();row.putBoolean("Available",candidate!=null);
            var single=IngredientTraits.compose(ingredients,stew,hall.mk(),EnumSet.of(effect)).meal();row.putBoolean("Pair",single!=null&&single.bonuses().get(0).pair());
            row.put("Sources",sources(effect));choices.add(row);
        }n.put("Choices",choices);
        n.put("LegendaryLinks",linkTags);var fx=new ListTag();for(int[] effects:slotEffects)fx.add(new IntArrayTag(effects));n.put("SlotFx",fx);
        var groups=new ListTag();for(int i=0;i<6;i++)groups.add(StringTag.valueOf(IngredientTraits.groupOf(ingredients.getItem(i))));n.put("SlotGroups",groups);if(selected!=null)n.putLong("Selected",selected.asLong());
        var price=Economy.kitchen(mk);if(price!=null){n.put("UpgradeItem",new ItemStack(price.item(),price.amount()).save(new CompoundTag()));n.putInt("UpgradeCost",price.amount());n.putInt("UpgradeHave",sp.isCreative()?price.amount():Economy.have(sp,price.item()));}
        var list=new ListTag();for(var pot:pots){var c=new CompoundTag();c.putLong("Pos",pot.getBlockPos().asLong());c.putInt("Servings",pot.servings);if(pot.stew!=null)c.put("Meal",pot.stew.save());list.add(c);}n.put("Pots",list);
        if(!n.equals(lastView)){lastView=n.copy();BeaconNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(()->sp),new Preview(containerId,pos,n));}
    }
    private ListTag sources(MealRules.Effect effect){
        if(catalogTraits!=IngredientTraits.traits){sourceCatalog.clear();catalogTraits=IngredientTraits.traits;}
        return sourceCatalog.computeIfAbsent(effect,key->{var list=new ListTag();for(var item:net.minecraft.core.registries.BuiltInRegistries.ITEM){var stack=new ItemStack(item);
            boolean accepted=effect.gun()?MealRules.MIXES.stream().filter(m->m.effect()==effect).anyMatch(m->m.first().equals(IngredientTraits.groupOf(stack))||m.second().equals(IngredientTraits.groupOf(stack))):IngredientTraits.effectsOf(stack).contains(effect);
            if(accepted){var source=new CompoundTag();source.put("Item",stack.save(new CompoundTag()));source.putString("Group",IngredientTraits.groupOf(stack));list.add(source);}
        }return list;});
    }
    @Override public ItemStack quickMoveStack(Player p,int index){
        if(index<0||index>=slots.size())return ItemStack.EMPTY;var slot=slots.get(index);if(!slot.hasItem()||!slot.mayPickup(p))return ItemStack.EMPTY;var stack=slot.getItem();var copy=stack.copy();
        if(index<8){if(!moveItemStackTo(stack,8,slots.size(),true))return ItemStack.EMPTY;}
        else if(!stew&&stack.is(net.minecraft.world.item.Items.BREAD)){
            boolean moved=moveItemStackTo(stack,7,8,false);if(!stack.isEmpty())moved|=moveItemStackTo(stack,0,6,false);if(!moved)return ItemStack.EMPTY;
        }else if(!IngredientTraits.accepts(stack)||!moveItemStackTo(stack,0,6,false))return ItemStack.EMPTY;
        if(stack.isEmpty())slot.set(ItemStack.EMPTY);else slot.setChanged();slot.onTake(p,stack);return copy;
    }
    record Preview(int menu,BlockPos pos,CompoundTag data){
        static void encode(Preview p,FriendlyByteBuf b){b.writeVarInt(p.menu);b.writeBlockPos(p.pos);b.writeNbt(p.data);}
        static Preview decode(FriendlyByteBuf b){int id=b.readVarInt();var pos=b.readBlockPos();var n=b.readNbt();return new Preview(id,pos,n==null?new CompoundTag():n);}
        static void handle(Preview p,Supplier<NetworkEvent.Context> ctx){ctx.get().enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->MealClient.preview(p)));ctx.get().setPacketHandled(true);}
    }
}
