package dev.createarsenal.beacon;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import java.util.ArrayList;
import java.util.List;

/**
 * 0.0.38 (owner): the MRE Station, one kitchen block (1 x 1 x 2, the owner's ration packer) with one screen: six food slots, a Marines
 * Fuel slot, a Paper slot and an output slot; Make and Take back. Each MRE takes one of each food in the slots, 1 Marines Fuel and 1 Paper
 * and holds their exact totals (see {@link MreRules}). The server checks everything again on Make and makes it in one step: nothing is
 * spent unless the MRE is made. Bowls and bottles the foods came in go back to the player's pack, or drop at their feet.
 */
final class MreStation {
    private MreStation(){}
    static final int FUEL=MreRules.FOOD_SLOTS,PAPER=FUEL+1,OUTPUT=PAPER+1,SLOTS=OUTPUT+1;

    /** The food an item gives as vanilla would feed it to {@code who}: any edible item, modded ones too, except an MRE; null if it cannot go in. */
    static MreRules.Food food(ItemStack stack,LivingEntity who){
        if(stack.isEmpty()||stack.is(ArsenalBeacon.MRE.get())||!stack.isEdible())return null;
        var p=stack.getFoodProperties(who);if(p==null)return null;
        var f=new MreRules.Food(p.getNutrition(),p.getSaturationModifier());return f.valid()?f:null;
    }
    /** What a food leaves behind when eaten: its crafting remainder (the honey bottle's glass bottle, most modded bowls), or the stews' bowl. */
    static ItemStack containerOf(ItemStack food){
        if(food.hasCraftingRemainingItem())return food.getCraftingRemainingItem().copy();
        if(food.getItem() instanceof BowlFoodItem||food.getItem() instanceof SuspiciousStewItem)return new ItemStack(Items.BOWL);
        return ItemStack.EMPTY;
    }
    /** Nutrition of the food slots, leaving one slot out (the one a food is about to go into). */
    static int nutritionExcept(Container c,int skip,LivingEntity who){
        int n=0;for(int i=0;i<MreRules.FOOD_SLOTS;i++){if(i==skip)continue;var f=food(c.getItem(i),who);if(f!=null)n+=f.nutrition();}return n;
    }
    /** May this food go into food slot {@code slot}? Only food, and never past 20 nutrition for one MRE. */
    static boolean accepts(Container c,int slot,ItemStack stack,LivingEntity who){
        var f=food(stack,who);return f!=null&&MreRules.fits(nutritionExcept(c,slot,who),f.nutrition());
    }
    /** What one MRE from these slots would be, or why there is none: {@code problem} is a language key under {@code mre.problem.}. */
    record Check(String problem,MreRules.Totals totals){boolean ready(){return problem==null;}}
    static Check check(Container c,LivingEntity who){
        var foods=new ArrayList<MreRules.Food>();
        for(int i=0;i<MreRules.FOOD_SLOTS;i++){var s=c.getItem(i);if(s.isEmpty())continue;var f=food(s,who);if(f==null)return new Check("not_food",null);foods.add(f);}
        if(foods.isEmpty())return new Check("no_food",null);
        var totals=MreRules.sum(foods);
        if(totals.nutrition()>MreRules.MAX_NUTRITION)return new Check("over",totals);
        if(totals.nutrition()<1)return new Check("no_nutrition",totals);
        if(!c.getItem(FUEL).is(ArsenalBeacon.MARINES_FUEL.get()))return new Check("no_fuel",totals);
        if(!c.getItem(PAPER).is(Items.PAPER))return new Check("no_paper",totals);
        var out=c.getItem(OUTPUT);
        if(!out.isEmpty()&&!ItemStack.isSameItemSameTags(out,Mre.create(totals)))return new Check("output_other",totals);
        if(!out.isEmpty()&&out.getCount()>=Math.min(MreRules.STACK,out.getMaxStackSize()))return new Check("output_full",totals);
        return new Check(null,totals);
    }

    static final class StationBlock extends KitchenBlock {
        StationBlock(){super(false);}
        @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new StationEntity(pos,state);}
        @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
            if(hand!=InteractionHand.MAIN_HAND)return InteractionResult.CONSUME;
            if(player instanceof ServerPlayer p){
                if(BaseZone.disabled(level,pos,p))return InteractionResult.CONSUME;
                if(level.getBlockEntity(pos) instanceof StationEntity station)net.minecraftforge.network.NetworkHooks.openScreen(p,station,b->b.writeBlockPos(pos));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        @Override public void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){
            if(!state.is(next.getBlock())&&level.getBlockEntity(pos) instanceof StationEntity station)Containers.dropContents(level,pos,station.items);
            super.onRemove(state,level,pos,next,moving);
        }
    }
    static final class StationEntity extends BlockEntity implements MenuProvider {
        final SimpleContainer items=new SimpleContainer(SLOTS){@Override public void setChanged(){super.setChanged();StationEntity.this.setChanged();}};
        StationEntity(BlockPos pos,BlockState state){super(ArsenalBeacon.MRE_STATION_ENTITY.get(),pos,state);}
        @Override protected void saveAdditional(CompoundTag tag){
            super.saveAdditional(tag);var list=new ListTag();
            for(int i=0;i<SLOTS;i++){var s=items.getItem(i);if(s.isEmpty())continue;var row=s.save(new CompoundTag());row.putByte("Slot",(byte)i);list.add(row);}
            tag.put("Items",list);
        }
        @Override public void load(CompoundTag tag){
            super.load(tag);items.clearContent();
            for(var t:tag.getList("Items",Tag.TAG_COMPOUND)){var row=(CompoundTag)t;int slot=row.getByte("Slot");if(slot>=0&&slot<SLOTS)items.setItem(slot,ItemStack.of(row));}
        }
        @Override public Component getDisplayName(){return Component.translatable("block.arsenal_beacon.mre_maker");}
        @Override public AbstractContainerMenu createMenu(int id,Inventory inv,Player player){return new StationMenu(id,inv,items,worldPosition,this);}
    }

    static final class StationMenu extends AbstractContainerMenu {
        /** 238 tall: it fits a 720p window at GUI scale 3 (240). */
        static final int WIDTH=176,HEIGHT=238;
        /** Slot corners (the item's top left): the food in a 3 x 2 grid, the fuel over the paper, the output; the pack below. */
        static final int FOOD_X=17,FOOD_Y=43,FUEL_X=81,FUEL_Y=43,PAPER_Y=61,OUT_X=139,OUT_Y=52,INV_Y=158;
        static final int MAKE=0,TAKE_BACK=1;
        final Container items;final BlockPos pos;final StationEntity owner;final Player player;
        StationMenu(int id,Inventory inv,Container items,BlockPos pos,StationEntity owner){
            super(ArsenalBeacon.MRE_STATION_MENU.get(),id);this.items=items;this.pos=pos;this.owner=owner;this.player=inv.player;
            for(int i=0;i<MreRules.FOOD_SLOTS;i++)addSlot(new Slot(items,i,FOOD_X+i%3*18,FOOD_Y+i/3*18){
                @Override public boolean mayPlace(ItemStack stack){return accepts(items,getContainerSlot(),stack,player);}
            });
            addSlot(new Slot(items,FUEL,FUEL_X,FUEL_Y){@Override public boolean mayPlace(ItemStack stack){return stack.is(ArsenalBeacon.MARINES_FUEL.get());}});
            addSlot(new Slot(items,PAPER,FUEL_X,PAPER_Y){@Override public boolean mayPlace(ItemStack stack){return stack.is(Items.PAPER);}});
            addSlot(new Slot(items,OUTPUT,OUT_X,OUT_Y){@Override public boolean mayPlace(ItemStack stack){return false;}});
            for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inv,9+row*9+col,8+col*18,INV_Y+row*18));
            for(int col=0;col<9;col++)addSlot(new Slot(inv,col,8+col*18,INV_Y+58));
        }
        static StationMenu client(int id,Inventory inv,FriendlyByteBuf buf){return new StationMenu(id,inv,new SimpleContainer(SLOTS),buf.readBlockPos(),null);}
        @Override public boolean stillValid(Player p){return !p.isSpectator()&&owner!=null&&p.level().hasChunkAt(pos)&&p.level().getBlockEntity(pos)==owner&&BaseZone.problem(p.level(),pos)==null&&p.distanceToSqr(pos.getCenter())<=64;}
        Check check(){return MreStation.check(items,player);}
        @Override public boolean clickMenuButton(Player p,int button){
            if(!(p instanceof ServerPlayer sp)||p!=player||!stillValid(p))return false;
            boolean done=button==MAKE?make(sp):button==TAKE_BACK&&takeBack(sp);
            broadcastChanges();return done;
        }
        /** One MRE, all or nothing: every check first, then the slots are used up, the MRE goes to the output and the containers come back. */
        boolean make(ServerPlayer p){
            var check=check();
            if(!check.ready()){say(p,Component.translatable("gui.arsenal_beacon.mre.problem."+check.problem(),MreRules.MAX_NUTRITION).withStyle(ChatFormatting.RED));sound(p,SoundEvents.VILLAGER_NO,.6f,1.3f);return false;}
            var back=new ArrayList<ItemStack>();
            for(int i=0;i<MreRules.FOOD_SLOTS;i++){var s=items.getItem(i);if(s.isEmpty())continue;var c=containerOf(s);if(!c.isEmpty())back.add(c);}
            for(int i=0;i<MreRules.FOOD_SLOTS;i++)if(!items.getItem(i).isEmpty())items.removeItem(i,1);
            items.removeItem(FUEL,1);items.removeItem(PAPER,1);
            var out=items.getItem(OUTPUT);if(out.isEmpty())items.setItem(OUTPUT,Mre.create(check.totals()));else{out.grow(1);items.setChanged();}
            for(var c:back)ArsenalBeacon.give(p,c);
            var line=Component.translatable("gui.arsenal_beacon.mre.made",check.totals().nutrition(),MreRules.number(check.totals().saturation()));
            if(!back.isEmpty())line=line.append(Component.translatable("gui.arsenal_beacon.mre.made_back",back.size()));
            say(p,line.withStyle(ChatFormatting.GREEN));sound(p,SoundEvents.BUNDLE_INSERT,1f,.9f);sound(p,SoundEvents.FIRE_EXTINGUISH,.25f,1.8f);
            return true;
        }
        /** Everything in the station back to the pack (the rest drops at the player's feet). */
        boolean takeBack(ServerPlayer p){
            int moved=0;for(int i=0;i<SLOTS;i++){var s=items.removeItemNoUpdate(i);if(!s.isEmpty()){moved+=s.getCount();ArsenalBeacon.give(p,s);}}
            items.setChanged();
            if(moved==0){say(p,Component.translatable("gui.arsenal_beacon.mre.nothing_back").withStyle(ChatFormatting.YELLOW));return false;}
            say(p,Component.translatable("gui.arsenal_beacon.mre.taken_back",moved));sound(p,SoundEvents.BUNDLE_REMOVE_ONE,1f,1f);return true;
        }
        private void say(ServerPlayer p,Component line){p.displayClientMessage(line,true);}
        private void sound(ServerPlayer p,net.minecraft.sounds.SoundEvent s,float volume,float pitch){if(p.level() instanceof ServerLevel l)l.playSound(null,pos,s,SoundSource.BLOCKS,volume,pitch);}
        @Override public ItemStack quickMoveStack(Player p,int index){
            if(index<0||index>=slots.size())return ItemStack.EMPTY;var slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;
            var stack=slot.getItem();var copy=stack.copy();
            if(index<SLOTS){if(!moveItemStackTo(stack,SLOTS,slots.size(),true))return ItemStack.EMPTY;}
            else if(stack.is(ArsenalBeacon.MARINES_FUEL.get())){if(!moveItemStackTo(stack,FUEL,FUEL+1,false))return ItemStack.EMPTY;}
            else if(stack.is(Items.PAPER)){if(!moveItemStackTo(stack,PAPER,PAPER+1,false))return ItemStack.EMPTY;}
            else if(food(stack,p)!=null){if(!moveItemStackTo(stack,0,MreRules.FOOD_SLOTS,false))return ItemStack.EMPTY;}
            else return ItemStack.EMPTY;
            if(stack.isEmpty())slot.set(ItemStack.EMPTY);else slot.setChanged();slot.onTake(p,stack);return copy;
        }
        /** The containers the foods in the slots would give back, for the screen's line (the same rule the server uses). */
        List<ItemStack> containers(){var back=new ArrayList<ItemStack>();for(int i=0;i<MreRules.FOOD_SLOTS;i++){var s=items.getItem(i);if(!s.isEmpty()){var c=containerOf(s);if(!c.isEmpty())back.add(c);}}return back;}
    }
}
