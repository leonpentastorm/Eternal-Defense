package dev.createarsenal.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import java.util.*;
import java.util.function.Supplier;

/**
 * Standard container slots and button packets; the client sends an intent, never a meal or serving count.
 * Buttons: 0/1 sandwich or stew, 2 prepare, 3 upgrade, 4 clear the order, 5 gather the order from the pack, 6 take the table back,
 * 7 cook and replace the stew still in the chosen pot (the screen asks first), 40+ toggle a recipe (one per effect, signed bytes), 100+ choose a pot.
 */
final class MessHallMenu extends AbstractContainerMenu {
    /** Two columns: the table and its buttons on the left, the effects to choose on the right. Needs a GUI at least 376 wide (every 16:9 window does). */
    static final int WIDTH=376,HEIGHT=238,MIX_X=9,MIX_Y=48,INV_Y=159,HOT_Y=217;
    /** The base bread and the sandwich output sit just after the open food slots, so a hall with three slots does not leave a gap. */
    static int baseX(int mk){return MIX_X+MealRules.tier(mk).slots()*18+10;}
    static int outX(int mk){return baseX(mk)+24;}
    static final int MODE_SANDWICH=0,MODE_STEW=1,PREPARE=2,UPGRADE=3,CLEAR=4,GATHER=5,TAKE_BACK=6,REPLACE=7,RECIPE=40,POT=100;
    /** Notice kinds shown by the screen: 0 information, 1 something to fix, 2 done. */
    static final int INFO=0,WARN=1,DONE=2;
    final Container ingredients;final BlockPos pos;final int mk;final MessHall.HallEntity hall;final Player player;
    boolean stew;BlockPos selected;CompoundTag view=new CompoundTag(),lastView;List<BlockPos> offered=List.of();
    /** Client copy of the food catalogue (sent once per menu): id, group and the ordinary effects each food feeds. */
    final List<CatalogueFood> catalogue=new ArrayList<>();
    record CatalogueFood(String id,String group,int[] effects){}
    private boolean catalogueSent,seenMode;private int noticeSeq,noticeKind;private String noticeKey="";private List<String> noticeArgs=List.of();
    private long fitSignature=Long.MIN_VALUE;private int[] fit=new int[MealRules.Effect.values().length];private boolean gatherable,covered;
    MessHallMenu(int id,Inventory inv,Container ingredients,BlockPos pos,int mk,MessHall.HallEntity hall){
        super(ArsenalBeacon.MESS_HALL_MENU.get(),id);this.ingredients=ingredients;this.pos=pos;this.mk=mk;this.hall=hall;this.player=inv.player;this.stew=this.seenMode=hall!=null&&hall.stewMode&&mk>=2;
        int open=MealRules.tier(mk).slots();
        for(int i=0;i<6;i++){final boolean usable=i<open;addSlot(new Slot(ingredients,i,MIX_X+i*18,MIX_Y){
            @Override public boolean mayPlace(ItemStack stack){return usable&&(player.level().isClientSide||IngredientTraits.accepts(stack));}
            @Override public boolean isActive(){return usable;}
        });}
        addSlot(new Slot(ingredients,6,outX(mk),MIX_Y){
            @Override public boolean mayPlace(ItemStack stack){return false;}
            @Override public boolean isActive(){return !stew;}
            @Override public boolean mayPickup(Player p){return !stew;}
        });
        addSlot(new Slot(ingredients,7,baseX(mk),MIX_Y){
            @Override public boolean mayPlace(ItemStack stack){return stack.is(Items.BREAD);}
            @Override public boolean isActive(){return !stew;}
            @Override public boolean mayPickup(Player p){return !stew;}
        });
        for(int r=0;r<3;r++)for(int c=0;c<9;c++)addSlot(new Slot(inv,9+r*9+c,MIX_X+c*18,INV_Y+r*18));
        for(int c=0;c<9;c++)addSlot(new Slot(inv,c,MIX_X+c*18,HOT_Y));
    }
    static MessHallMenu client(int id,Inventory inv,FriendlyByteBuf buf){return new MessHallMenu(id,inv,new SimpleContainer(8),buf.readBlockPos(),buf.readVarInt(),null);}
    @Override public boolean stillValid(Player p){return !p.isSpectator()&&p.level().hasChunkAt(pos)&&p.level().getBlockEntity(pos)==hall&&hall!=null&&BaseZone.problem(p.level(),pos)==null&&p.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<=64;}
    private List<CookPot.PotEntity> pots(){return hall.pots();}
    private CookPot.PotEntity target(){if(selected==null)return null;return pots().stream().filter(p->p.getBlockPos().equals(selected)).findFirst().orElse(null);}
    int limit(){return stew?3:2;}
    Set<MealRules.Effect> order(){return hall.order;}
    boolean explicit(){return hall.explicitOrder();}
    IngredientTraits.Preview composition(){return IngredientTraits.compose(ingredients,stew,hall.mk(),explicit()?hall.order:null);}
    String problem(IngredientTraits.Preview preview){
        if(stew&&!hall.tier().stew())return "stew_locked";
        if(!preview.problem().isEmpty())return preview.problem();
        var batch=IngredientTraits.batch(ingredients,preview,stew,hall.mk());if(!batch.problem().isEmpty())return batch.problem();
        if(stew){var pot=target();return pot==null?"pot":!pot.empty()?"pot_full":"";}
        if(!ingredients.getItem(7).is(Items.BREAD))return "staple";
        var made=PreparedSandwich.create(preview.meal());var output=ingredients.getItem(6);
        return output.isEmpty()||ItemStack.isSameItemSameTags(output,made)&&output.getCount()<made.getMaxStackSize()?"":"output";
    }

    // ---- notices: one line the screen shows for a few seconds ---------------------------------------
    private void notice(int kind,String key,Object... args){noticeKind=kind;noticeKey=key;noticeArgs=Arrays.stream(args).map(String::valueOf).toList();noticeSeq++;}
    /** Effect names travel as ids ("fx:firepower,mobility") and are translated by the client, which owns the language files. */
    private static String fx(Collection<MealRules.Effect> effects){return "fx:"+String.join(",",effects.stream().map(e->e.id).toList());}
    private void sound(net.minecraft.sounds.SoundEvent event,float pitch){if(player instanceof ServerPlayer sp)sp.playNotifySound(event,SoundSource.BLOCKS,.7f,pitch);}

    boolean prepare(){return prepare(false);}
    /** {@code replace}: the cook confirmed that the stew still in the chosen pot is thrown away; without it a pot with stew is refused ("pot_full"). */
    boolean prepare(boolean replace){
        if(!stillValid(player))return false;var preview=composition();var problem=problem(preview);
        if(!problem.isEmpty()&&!(replace&&problem.equals("pot_full")))return false;
        var batch=IngredientTraits.batch(ingredients,preview,stew,hall.mk());var meal=preview.meal();int potNumber=0;
        int thrownAway=0;String thrownAwayName="";
        if(stew){var pot=target();if(!pot.empty()){thrownAway=pot.servings;thrownAwayName=pot.stew.name().getString();}if(!pot.fill(hall,meal,hall.tier().servings(),replace))return false;potNumber=pots().indexOf(pot)+1;}
        else{ingredients.removeItem(7,1);var output=ingredients.getItem(6);if(output.isEmpty())ingredients.setItem(6,PreparedSandwich.create(meal));else{output.grow(1);ingredients.setChanged();}}
        for(int i=0;i<6;i++)if(batch.spent()[i]>0){
            var unit=ingredients.getItem(i).copyWithCount(1);ingredients.removeItem(i,batch.spent()[i]);
            if(unit.hasCraftingRemainingItem()&&player instanceof ServerPlayer sp)for(int count=0;count<batch.spent()[i];count++)ArsenalBeacon.give(sp,unit.getCraftingRemainingItem().copy());
        }
        var effects=fx(meal.bonuses().stream().map(MealData.Bonus::effect).toList());
        if(stew&&thrownAway>0)notice(DONE,"replaced",effects,hall.tier().servings(),potNumber,thrownAway);
        else if(stew)notice(DONE,"cooked",effects,hall.tier().servings(),potNumber);else notice(DONE,"made",effects);
        if(player.level() instanceof ServerLevel level){
            level.playSound(null,pos,stew?SoundEvents.BREWING_STAND_BREW:SoundEvents.VILLAGER_WORK_BUTCHER,SoundSource.BLOCKS,.8f,stew?1f:1.2f);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.CAMPFIRE_COSY_SMOKE,pos.getX()+.5,pos.getY()+1.1,pos.getZ()+.5,3,.2,.1,.2,.01);
        }
        broadcastChanges();return true;
    }

    // ---- the standing order ---------------------------------------------------------------------------
    private Map<String,Integer> plenty(){var map=new HashMap<String,Integer>();IngredientTraits.catalogue().foods().keySet().forEach(k->map.put(k,64));return map;}
    /** Can this order be cooked at all (any amount of food), at the hall's tier? */
    private boolean possible(Set<MealRules.Effect> order,int tier){return MealPlanner.plan(order,stew,tier,IngredientTraits.catalogue().foods(),plenty(),Set.of(),false)!=null;}
    private boolean toggle(MealRules.Effect effect){
        var next=EnumSet.copyOf(hall.order);
        if(!next.remove(effect)){
            if(next.size()>=limit()){notice(WARN,"order_full",limit());return false;}
            next.add(effect);
            if(!possible(next,hall.mk())){notice(WARN,possible(next,4)?"order_tier":"order_conflict",fx(List.of(effect)));return false;}
        }
        hall.setOrder(next);return true;
    }
    /** Fetches the foods for the order from the table and the pack in one move; nothing is consumed, only moved onto the table. */
    private boolean gather(ServerPlayer sp){
        if(!explicit()){notice(WARN,"gather_choose");return false;}
        var foods=IngredientTraits.catalogue().foods();var stock=new HashMap<String,Integer>();var onTable=new HashSet<String>();var inv=sp.getInventory();
        for(int i=0;i<6;i++){var food=IngredientTraits.foodOf(ingredients.getItem(i));if(food!=null){stock.merge(food.key(),ingredients.getItem(i).getCount(),Integer::sum);onTable.add(food.key());}}
        for(var stack:inv.items){var food=IngredientTraits.foodOf(stack);if(food!=null)stock.merge(food.key(),stack.getCount(),Integer::sum);}
        boolean needBase=!stew&&!ingredients.getItem(7).is(Items.BREAD);
        if(needBase){int bread=stock.getOrDefault("minecraft:bread",0);if(bread<1){notice(WARN,"gather_bread");return false;}stock.put("minecraft:bread",bread-1);}
        var plan=MealPlanner.plan(hall.order,stew,hall.mk(),foods,stock,onTable,true);
        if(plan==null){
            var lacking=hall.order.stream().filter(e->MealPlanner.plan(Set.of(e),stew,hall.mk(),foods,stock,onTable,false)==null).toList();
            if(lacking.isEmpty())notice(WARN,"gather_blocked");else notice(WARN,"gather_missing",fx(lacking));return false;
        }
        var lifted=new ArrayList<ItemStack>();for(int i=0;i<6;i++){var s=ingredients.removeItemNoUpdate(i);if(!s.isEmpty())lifted.add(s);}
        var sources=new ArrayList<ItemStack>(lifted);sources.addAll(inv.items);
        for(int i=0;i<plan.keys().size();i++)ingredients.setItem(i,take(sources,plan.keys().get(i),plan.units()[i]));
        if(needBase)ingredients.setItem(7,take(sources,"minecraft:bread",1));
        for(var rest:lifted)if(!rest.isEmpty())ArsenalBeacon.give(sp,rest);
        inv.setChanged();ingredients.setChanged();
        if(plan.covered())notice(DONE,"gathered",plan.keys().size());else notice(WARN,"gather_short",plan.keys().size(),Math.max(0,(stew?hall.tier().ingredients():0)-Arrays.stream(plan.units()).sum()));
        sound(SoundEvents.BUNDLE_INSERT,1f);return true;
    }
    /** Moves up to {@code need} units of one food out of {@code sources} into a single new stack. */
    private static ItemStack take(List<ItemStack> sources,String key,int need){
        var placed=ItemStack.EMPTY;
        for(var source:sources){
            if(need<=0)break;if(source.isEmpty()||!IngredientTraits.key(source).equals(key)||IngredientTraits.foodOf(source)==null)continue;
            if(!placed.isEmpty()&&!ItemStack.isSameItemSameTags(placed,source))continue;
            int moved=Math.min(need,source.getCount());if(placed.isEmpty())placed=source.copyWithCount(moved);else placed.grow(moved);source.shrink(moved);need-=moved;
        }
        return placed;
    }
    private boolean takeBack(ServerPlayer sp){
        int moved=0;for(int i=0;i<8;i++){if(i==6)continue;var s=ingredients.removeItemNoUpdate(i);if(!s.isEmpty()){moved+=s.getCount();ArsenalBeacon.give(sp,s);}}
        ingredients.setChanged();if(moved==0){notice(INFO,"table_empty");return false;}
        notice(INFO,"returned",moved);sound(SoundEvents.BUNDLE_REMOVE_ONE,1f);return true;
    }

    @Override public boolean clickMenuButton(Player p,int button){
        if(!stillValid(p)||p!=player)return false;
        if(button==MODE_STEW&&!hall.tier().stew()){notice(WARN,"stew_locked");broadcastChanges();return false;}
        if(button==MODE_SANDWICH||button==MODE_STEW){
            stew=seenMode=button==MODE_STEW;hall.setMode(stew);hall.discover();
            if(explicit()&&!possible(hall.order,hall.mk())){hall.setOrder(EnumSet.noneOf(MealRules.Effect.class));notice(WARN,stew?"order_reset_stew":"order_reset_sandwich");}
        }
        else if(button==PREPARE)return prepare();
        else if(button==REPLACE)return prepare(true);
        else if(button==UPGRADE&&p instanceof ServerPlayer sp)return hall.upgrade(sp);
        else if(button==CLEAR)hall.setOrder(EnumSet.noneOf(MealRules.Effect.class));
        else if(button==GATHER&&p instanceof ServerPlayer sp){var done=gather(sp);broadcastChanges();return done;}
        else if(button==TAKE_BACK&&p instanceof ServerPlayer sp){var done=takeBack(sp);broadcastChanges();return done;}
        else if(button>=RECIPE&&button<RECIPE+MealRules.Effect.values().length){var done=toggle(MealRules.Effect.values()[button-RECIPE]);broadcastChanges();return done;}
        else if(button>=POT&&button<POT+offered.size())selected=offered.get(button-POT);
        else return false;
        broadcastChanges();return true;
    }

    // ---- what the screen is told ----------------------------------------------------------------------
    /** Cheap fingerprint of everything that decides which recipes the pack can cook: table, pack, order, mode and tier. */
    private long signature(Inventory inv){
        long h=hall.mk()*31L+(stew?7:3)+hall.order.hashCode()*131L;
        for(int i=0;i<6;i++)h=h*1000003L+ingredients.getItem(i).getItem().hashCode()*17L+ingredients.getItem(i).getCount();
        for(var stack:inv.items)h=h*1000003L+stack.getItem().hashCode()*17L+stack.getCount();
        return h*31L+inv.items.get(0).getCount()+ingredients.getItem(7).getCount();
    }
    /** 4 chosen but nothing in the pack feeds it, 3 chosen, 2 can be added now, 1 exists but not alongside the order, 0 nothing in the pack feeds it. */
    private void refreshFit(ServerPlayer sp){
        long sig=signature(sp.getInventory());if(sig==fitSignature)return;fitSignature=sig;
        var foods=IngredientTraits.catalogue().foods();var stock=new HashMap<String,Integer>();var onTable=new HashSet<String>();
        for(int i=0;i<6;i++){var food=IngredientTraits.foodOf(ingredients.getItem(i));if(food!=null){stock.merge(food.key(),ingredients.getItem(i).getCount(),Integer::sum);onTable.add(food.key());}}
        for(var stack:sp.getInventory().items){var food=IngredientTraits.foodOf(stack);if(food!=null)stock.merge(food.key(),stack.getCount(),Integer::sum);}
        boolean needBase=!stew&&!ingredients.getItem(7).is(Items.BREAD);
        if(needBase)stock.merge("minecraft:bread",-1,Integer::sum);
        int tier=hall.mk();
        for(var effect:MealRules.Effect.values()){
            if(hall.order.contains(effect)){fit[effect.ordinal()]=MealPlanner.plan(EnumSet.of(effect),stew,tier,foods,stock,onTable,false)!=null?3:4;continue;}
            var next=EnumSet.copyOf(hall.order);next.add(effect);
            if(next.size()<=limit()&&MealPlanner.plan(next,stew,tier,foods,stock,onTable,false)!=null)fit[effect.ordinal()]=2;
            else fit[effect.ordinal()]=MealPlanner.plan(EnumSet.of(effect),stew,tier,foods,stock,onTable,false)!=null?1:0;
        }
        var plan=explicit()?MealPlanner.plan(hall.order,stew,tier,foods,stock,onTable,false):null;
        gatherable=plan!=null;covered=plan!=null&&plan.covered();
    }
    private ListTag catalogueTag(){
        var list=new ListTag();
        IngredientTraits.catalogue().foods().forEach((key,food)->{var row=new CompoundTag();row.putString("Id",key);row.putString("Group",food.group());row.putIntArray("Fx",food.weights().keySet().stream().mapToInt(Enum::ordinal).toArray());list.add(row);});
        return list;
    }
    @Override public void broadcastChanges(){
        super.broadcastChanges();if(!(player instanceof ServerPlayer sp)||hall==null)return;
        if(hall.stewMode!=seenMode){stew=seenMode=hall.stewMode;} // another cook switched the hall's mode
        if(sp.tickCount%100==0)hall.discover();var pots=pots();offered=pots.stream().map(CookPot.PotEntity::getBlockPos).toList();
        if(selected==null||!offered.contains(selected))selected=pots.stream().filter(CookPot.PotEntity::empty).map(CookPot.PotEntity::getBlockPos).findFirst().orElse(null);
        refreshFit(sp);
        var preview=composition();var n=new CompoundTag();n.putBoolean("StewMode",stew);n.putString("Problem",problem(preview));n.putInt("Servings",stew?hall.tier().servings():1);n.putInt("Capacity",hall.tier().pots());n.putInt("Linked",hall.links.size());
        var batch=IngredientTraits.batch(ingredients,preview,stew,hall.mk());n.putInt("Required",batch.required());n.putInt("Available",batch.available());if(batch.spent()!=null)n.putIntArray("Spent",batch.spent());
        if(preview.meal()!=null)n.put("Meal",preview.meal().save());
        var links=IngredientTraits.legendaryLinks(ingredients,preview.meal());var linkTags=new ListTag();var slotEffects=new int[6][];
        for(int i=0;i<6;i++)slotEffects[i]=IngredientTraits.effectsOf(ingredients.getItem(i)).stream().mapToInt(Enum::ordinal).toArray();
        for(var link:links){var row=new CompoundTag();row.putInt("Effect",link.mix().effect().ordinal());row.putIntArray("Slots",link.slots());row.putString("First",link.mix().first());row.putString("Second",link.mix().second());row.putBoolean("Doubled",link.doubled());linkTags.add(row);for(int slot:link.slots())slotEffects[slot]=new int[]{link.mix().effect().ordinal()};}
        n.putBoolean("Explicit",explicit());n.putIntArray("Order",hall.order.stream().mapToInt(Enum::ordinal).toArray());n.putIntArray("Unmet",preview.unmet().stream().mapToInt(Enum::ordinal).toArray());
        var made=new ListTag();for(var b:preview.bonuses()){var row=new CompoundTag();row.putInt("Effect",b.effect().ordinal());row.putBoolean("Pair",b.pair());made.add(row);}n.put("Made",made);
        n.putIntArray("Fit",fit);n.putBoolean("Gatherable",gatherable);n.putBoolean("Covered",covered);n.putInt("Limit",limit());
        n.putInt("NoticeSeq",noticeSeq);n.putInt("NoticeKind",noticeKind);n.putString("Notice",noticeKey);var args=new ListTag();noticeArgs.forEach(a->args.add(StringTag.valueOf(a)));n.put("NoticeArgs",args);
        n.put("LegendaryLinks",linkTags);var fx=new ListTag();for(int[] effects:slotEffects)fx.add(new IntArrayTag(effects));n.put("SlotFx",fx);
        var groups=new ListTag();for(int i=0;i<6;i++)groups.add(StringTag.valueOf(IngredientTraits.groupOf(ingredients.getItem(i))));n.put("SlotGroups",groups);if(selected!=null)n.putLong("Selected",selected.asLong());
        var price=Economy.kitchen(mk);if(price!=null){n.put("UpgradeItem",new ItemStack(price.item(),price.amount()).save(new CompoundTag()));n.putInt("UpgradeCost",price.amount());n.putInt("UpgradeHave",sp.isCreative()?price.amount():Economy.have(sp,price.item()));}
        var list=new ListTag();for(var pot:pots){var c=new CompoundTag();c.putLong("Pos",pot.getBlockPos().asLong());c.putInt("Servings",pot.servings);if(pot.stew!=null)c.put("Meal",pot.stew.save());list.add(c);}n.put("Pots",list);
        if(!n.equals(lastView)||!catalogueSent){
            lastView=n.copy();var packet=n.copy();if(!catalogueSent){packet.put("Catalogue",catalogueTag());catalogueSent=true;}
            BeaconNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(()->sp),new Preview(containerId,pos,packet));
        }
    }
    @Override public ItemStack quickMoveStack(Player p,int index){
        if(index<0||index>=slots.size())return ItemStack.EMPTY;var slot=slots.get(index);if(!slot.hasItem()||!slot.mayPickup(p))return ItemStack.EMPTY;var stack=slot.getItem();var copy=stack.copy();
        if(index<8){if(!moveItemStackTo(stack,8,slots.size(),true))return ItemStack.EMPTY;}
        else if(!stew&&stack.is(Items.BREAD)){
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
