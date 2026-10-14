package dev.eternaldefense.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.*;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.brewing.BrewingRecipeRegistry;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import java.util.*;
import java.util.function.Supplier;

/** The Drink Station's one screen. */
final class DrinkStationMenu extends AbstractContainerMenu {
    static final int WIDTH=424,HEIGHT=238;
    /** Left column: the stand (the vanilla texture's region from u 12, v 12, 108 x 66) and the storage under it. */
    static final int BREW_X=8,BREW_Y=30,BREW_U=12,BREW_V=12,BREW_W=108,BREW_H=66,STORE_X=8,STORE_Y=114,STORE_COLS=9;
    /** Right column (x 192 to 416): tabs, notices, one row of effect tiles */
    static final int RX=192,RW=224,TAB_Y=29,STRIP_Y=46,TILE_Y=63,TILE=18,TILE_PITCH=20,TILE_COLS=11,POTS_Y=85,POT_W=40,POT_PITCH=42,SLOT_Y=106,PREVIEW_Y=127,BUTTON_Y=138,INV_X=193,INV_Y=159,HOT_Y=217;
    static final int CUP_X=193,MIX_X=217,MIX_PITCH=20,OUT_X=287,SWEET_X=287,BAR_X=308,BAR_W=62,STEPS_X=374,STEPS_W=40;
    static int brewSlotX(int u){return BREW_X+u-BREW_U;}
    static int brewSlotY(int v){return BREW_Y+v-BREW_V;}
    static final int MODE_POT=0,MODE_LATTE=1,BREW=2,UPGRADE=3,CLEAR=4,GATHER=5,TAKE_BACK=6,REPLACE=7,STEPS=8,EFFECT=40,POT=100,STORE=200;
    static final int SLOT_BREW=0,SLOT_MIX=5,SLOT_CUP=8,SLOT_OUT=9,SLOT_SWEET=10,PLAYER_START=11;
    static final int INFO=0,WARN=1,DONE=2;
    final Container brew,mix;final BlockPos pos;final int mk;final DrinkStation.StationEntity station;final Player player;
    CompoundTag view=new CompoundTag(),lastView;List<BlockPos> offered=List.of();BlockPos selected;
    /** Client copy of the effect list (sent once per menu). */
    final List<ResourceLocation> catalogue=new ArrayList<>();
    private boolean catalogueSent;private int noticeSeq,noticeKind;private String noticeKey="";private List<String> noticeArgs=List.of();
    DrinkStationMenu(int id,Inventory inv,Container brew,Container mix,BlockPos pos,int mk,DrinkStation.StationEntity station){
        super(EternalDefense.DRINK_STATION_MENU.get(),id);this.brew=brew;this.mix=mix;this.pos=pos;this.mk=mk;this.station=station;this.player=inv.player;
        // The brewing stand, at the vanilla screen's own places
        int[][] bottles={{56,51},{79,58},{102,51}};
        for(int i=0;i<3;i++)addSlot(new Slot(brew,i,brewSlotX(bottles[i][0]),brewSlotY(bottles[i][1])){
            @Override public boolean mayPlace(ItemStack s){return s.is(Items.GLASS_BOTTLE)||BrewingRecipeRegistry.isValidInput(s);}
            @Override public int getMaxStackSize(){return 1;}
        });
        addSlot(new Slot(brew,3,brewSlotX(79),brewSlotY(17)){@Override public boolean mayPlace(ItemStack s){return BrewingRecipeRegistry.isValidIngredient(s);}});
        addSlot(new Slot(brew,4,brewSlotX(17),brewSlotY(17)){@Override public boolean mayPlace(ItemStack s){return s.is(Items.BLAZE_POWDER);}});
        // The mixing slots: one potion each, the order's ghost shown when empty
        for(int i=0;i<3;i++){final int mixing=i;addSlot(new Slot(mix,i,MIX_X+i*MIX_PITCH,SLOT_Y){
            @Override public boolean isActive(){return mixing<limit();}
            @Override public boolean mayPlace(ItemStack s){if(!isActive())return false;var why=refusal(s,mixing);if(!why.isEmpty()&&!player.level().isClientSide)notice(WARN,"refuse."+why);return why.isEmpty();}
            @Override public int getMaxStackSize(){return 1;}
        });}
        addSlot(new Slot(mix,3,CUP_X,SLOT_Y){
            @Override public boolean isActive(){return latte()&&DrinkRules.tier(mk).mixing();}
            @Override public boolean mayPlace(ItemStack s){return isActive()&&CupDispenser.cup(s);}
        });
        addSlot(new Slot(mix,4,OUT_X,SLOT_Y){
            @Override public boolean isActive(){return latte()&&DrinkRules.tier(mk).mixing();}
            @Override public boolean mayPlace(ItemStack s){return false;}
        });
        // The sweetener sits where the Latte's output is: only one of them shows (no sweetener for the Latte)
        addSlot(new Slot(mix,5,SWEET_X,SLOT_Y){
            @Override public boolean isActive(){return DrinkRules.tier(mk).mixing()&&!latte();}
            @Override public boolean mayPlace(ItemStack s){if(!isActive())return false;boolean ok=DrinkData.sweetener(s)>0;if(!ok&&!player.level().isClientSide)notice(WARN,"sweet_only");return ok;}
            @Override public int getMaxStackSize(){return 1;}
        });
        for(int r=0;r<3;r++)for(int c=0;c<9;c++)addSlot(new Slot(inv,9+r*9+c,INV_X+c*18,INV_Y+r*18));
        for(int c=0;c<9;c++)addSlot(new Slot(inv,c,INV_X+c*18,HOT_Y));
    }
    static DrinkStationMenu client(int id,Inventory inv,FriendlyByteBuf buf){return new DrinkStationMenu(id,inv,new SimpleContainer(5),new SimpleContainer(6),buf.readBlockPos(),buf.readVarInt(),null);}
    @Override public boolean stillValid(Player p){return !p.isSpectator()&&station!=null&&p.level().hasChunkAt(pos)&&p.level().getBlockEntity(pos)==station&&BaseZone.problem(p.level(),pos)==null&&p.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<=64;}
    boolean latte(){return station!=null?station.latteMode:view.getBoolean("Latte");}
    int limit(){return latte()?DrinkRules.latteEffects(mk):DrinkRules.tier(mk).effects();}
    /** Why this potion cannot go into mixing slot index: the rules, plus "an effect already in another slot". */
    String refusal(ItemStack stack,int index){
        var others=new HashSet<MobEffect>();for(int i=0;i<3;i++)if(i!=index){var fx=DrinkData.effectOf(mix.getItem(i));if(fx!=null)others.add(fx.getEffect());}
        return DrinkData.refusal(stack,others);
    }
    private void notice(int kind,String key,Object... args){noticeKind=kind;noticeKey=key;noticeArgs=Arrays.stream(args).map(String::valueOf).toList();noticeSeq++;}
    /** Effect names travel as ids ("fx:minecraft:speed,...") and are translated by the client. */
    private static String fx(Collection<ResourceLocation> ids){return "fx:"+String.join(",",ids.stream().map(ResourceLocation::toString).toList());}
    private void sound(net.minecraft.sounds.SoundEvent event,float pitch){if(player instanceof ServerPlayer sp)sp.playNotifySound(event,SoundSource.BLOCKS,.7f,pitch);}

    // What is on the table
    /** The drink the active slots make, in slot order: one part per mixable potion. */
    List<DrinkData.Part> parts(){
        var out=new ArrayList<DrinkData.Part>();
        for(int i=0;i<limit();i++){var s=mix.getItem(i);if(s.isEmpty()||!DrinkData.mixable(s))continue;var fx=DrinkData.effectOf(s);var id=DrinkData.id(fx.getEffect());if(out.stream().noneMatch(p->p.effect().equals(id)))out.add(new DrinkData.Part(id,fx.getAmplifier()));}
        return out;
    }
    DrinkData preview(){var parts=parts();return parts.isEmpty()?null:new DrinkData(parts,latte()?DrinkRules.BASE_TICKS:DrinkRules.duration(station.steps),latte());}
    private List<BrewPot.PotEntity> pots(){return station.pots();}
    private BrewPot.PotEntity target(){if(selected==null)return null;return pots().stream().filter(p->p.getBlockPos().equals(selected)).findFirst().orElse(null);}
    /** "" when Brew (or Make Latte) would work, else what is in the way. */
    String problem(){
        if(!DrinkRules.tier(mk).mixing())return "locked";
        for(int i=0;i<3;i++){var s=mix.getItem(i);if(!s.isEmpty()&&(i>=limit()||!DrinkData.mixable(s)))return "invalid";}
        var parts=parts();var have=new HashSet<ResourceLocation>();parts.forEach(p->have.add(p.effect()));
        if(!station.order.isEmpty()){if(!have.containsAll(station.order))return parts.isEmpty()?"empty":"missing";if(!station.order.containsAll(have))return "extra";}
        if(parts.isEmpty())return "empty";
        if(latte()){
            if(!CupDispenser.cup(mix.getItem(3)))return "cup";
            var out=mix.getItem(4);var made=Latte.create(preview());
            return out.isEmpty()||ItemStack.isSameItemSameTags(out,made)&&out.getCount()<out.getMaxStackSize()?"":"output";
        }
        if(station.tank<DrinkRules.sweetenerCost(station.steps))return "sweet_short";
        var pot=target();return pot==null?"pot":!pot.empty()?"pot_full":"";
    }

    boolean brew(boolean replace){
        if(!stillValid(player))return false;var problem=problem();
        if(!problem.isEmpty()&&!(replace&&problem.equals("pot_full"))){notice(WARN,"problem."+problem);return false;}
        var drink=preview();var ids=drink.parts().stream().map(DrinkData.Part::effect).toList();
        if(latte()){
            mix.removeItem(3,1);var out=mix.getItem(4);if(out.isEmpty())mix.setItem(4,Latte.create(drink));else{out.grow(1);mix.setChanged();}
            notice(DONE,"latte_made",fx(ids));
        }else{
            var pot=target();boolean replaced=!pot.empty();String old=replaced?pot.drink.name().getString():"";
            if(!pot.fill(station,drink,replace))return false;
            station.tank-=DrinkRules.sweetenerCost(station.steps);station.setChanged();
            int number=pots().indexOf(pot)+1;
            if(replaced)notice(DONE,"replaced",fx(ids),number,DrinkRules.minutes(drink.duration()),old);else notice(DONE,"brewed",fx(ids),number,DrinkRules.minutes(drink.duration()));
        }
        // The potions are used up; their bottles come back
        for(int i=0;i<limit();i++)if(!mix.getItem(i).isEmpty()){mix.removeItem(i,1);if(player instanceof ServerPlayer sp)EternalDefense.give(sp,new ItemStack(Items.GLASS_BOTTLE));}
        if(player.level() instanceof ServerLevel level){
            level.playSound(null,pos,SoundEvents.BREWING_STAND_BREW,SoundSource.BLOCKS,.8f,latte()?1.3f:1f);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.EFFECT,pos.getX()+.5,pos.getY()+1.1,pos.getZ()+.5,6,.25,.1,.25,.02);
        }
        broadcastChanges();return true;
    }

    // The standing order
    private boolean toggle(ResourceLocation effect){
        if(!DrinkRules.tier(mk).mixing()){notice(WARN,"problem.locked");return false;}
        var order=station.order;
        if(order.remove(effect)){station.setChanged();return true;}
        if(order.size()>=limit()){notice(WARN,"order_full",limit());return false;}
        order.add(effect);station.setChanged();return true;
    }
    /** A mixable potion of effect: the station's storage first (the strongest kind), then the pack. */
    private ItemStack fetch(MobEffect effect,ServerPlayer sp){
        int slot=station.find(effect);if(slot>=0)return station.takeOne(slot);
        var inv=sp.getInventory();int best=-1,level=-1;
        for(int i=0;i<inv.items.size();i++){var s=inv.items.get(i);if(!DrinkData.mixable(s))continue;var fx=DrinkData.effectOf(s);if(fx.getEffect()==effect&&fx.getAmplifier()>level){best=i;level=fx.getAmplifier();}}
        if(best>=0)return inv.items.get(best).split(1);
        // Not in the storage or the pack: the Home Zone's Logistics Center Hubs
        var w=Supply.at(sp,pos);Supply.Borrowed pick=null;
        for(var b:Supply.borrow(w,s->DrinkData.mixable(s)&&DrinkData.effectOf(s).getEffect()==effect)){int amp=DrinkData.effectOf(b.stack).getAmplifier();if(pick==null||amp>level){pick=b;level=amp;}}
        if(pick==null)return ItemStack.EMPTY;
        var one=pick.stack.split(1);Supply.settle(w,List.of(pick));return one;
    }
    /** Returns a potion from the table: to the storage when it fits, else to the pack. */
    private void putBack(ServerPlayer sp,ItemStack s){if(s.isEmpty())return;int n=station.insert(s,s.getCount());s.shrink(n);if(!s.isEmpty())EternalDefense.give(sp,s);}
    private boolean gather(ServerPlayer sp){
        if(station.order.isEmpty()){notice(WARN,"gather_choose");return false;}
        var wanted=new ArrayList<ResourceLocation>(station.order);var missing=new ArrayList<ResourceLocation>();
        // Keep what already matches the order, return the rest
        for(int i=0;i<3;i++){
            var s=mix.getItem(i);if(s.isEmpty())continue;var fx=DrinkData.effectOf(s);var id=fx==null?null:DrinkData.id(fx.getEffect());
            if(i<limit()&&DrinkData.mixable(s)&&id!=null&&wanted.remove(id))continue;
            putBack(sp,mix.removeItemNoUpdate(i));
        }
        for(var id:wanted){
            int free=-1;for(int i=0;i<limit();i++)if(mix.getItem(i).isEmpty()){free=i;break;}
            var effect=net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getValue(id);var got=free<0||effect==null?ItemStack.EMPTY:fetch(effect,sp);
            if(got.isEmpty())missing.add(id);else mix.setItem(free,got);
        }
        boolean cupMissing=false;
        if(latte()&&mix.getItem(3).isEmpty()){
            var inv=sp.getInventory();int at=-1;for(int i=0;i<inv.items.size();i++)if(CupDispenser.cup(inv.items.get(i))){at=i;break;}
            if(at>=0)mix.setItem(3,inv.items.get(at).split(1));
            else{   // A Paper Cup from a Hub in the Home Zone
                var w=Supply.at(sp,pos);var cups=Supply.borrow(w,CupDispenser::cup);
                if(cups.isEmpty())cupMissing=true;else{mix.setItem(3,cups.get(0).stack.split(1));Supply.settle(w,List.of(cups.get(0)));}
            }
        }
        sp.getInventory().setChanged();mix.setChanged();
        if(!missing.isEmpty())notice(WARN,"gather_missing",fx(missing));
        else if(cupMissing)notice(WARN,"gather_cup");
        else notice(DONE,"gathered",station.order.size());
        sound(SoundEvents.BUNDLE_INSERT,1f);return missing.isEmpty()&&!cupMissing;
    }
    private boolean takeBack(ServerPlayer sp){
        int moved=0;for(int i=0;i<4;i++){var s=mix.removeItemNoUpdate(i);if(s.isEmpty())continue;moved+=s.getCount();if(i==3)EternalDefense.give(sp,s);else putBack(sp,s);}
        mix.setChanged();if(moved==0){notice(INFO,"table_empty");return false;}
        notice(INFO,"returned",moved);sound(SoundEvents.BUNDLE_REMOVE_ONE,1f);return true;
    }
    /** A click on storage slot slot: with a potion in hand it goes */
    private boolean storage(ServerPlayer sp,int slot,boolean shift){
        var carried=getCarried();
        if(!carried.isEmpty()){
            if(!DrinkStation.StationEntity.storable(carried)){notice(WARN,"storage_only");return false;}
            int n=station.insert(carried,carried.getCount());if(n==0){notice(WARN,"storage_full");return false;}
            carried.shrink(n);setCarried(carried);sound(SoundEvents.BOTTLE_FILL,1.2f);return true;
        }
        if(slot<0||slot>=DrinkRules.STORAGE_SLOTS||station.kinds[slot].isEmpty())return false;
        if(!shift){setCarried(station.takeOne(slot));sound(SoundEvents.BOTTLE_EMPTY,1.2f);return true;}
        int taken=0;var inv=sp.getInventory();
        while(!station.kinds[slot].isEmpty()){var one=station.takeOne(slot);if(!inv.add(one)){station.insert(one,1);break;}taken++;}
        if(taken==0){notice(WARN,"pack_full");return false;}
        inv.setChanged();notice(INFO,"took",taken);sound(SoundEvents.BOTTLE_EMPTY,1.2f);return true;
    }

    @Override public boolean clickMenuButton(Player p,int button){
        if(!stillValid(p)||p!=player||!(p instanceof ServerPlayer sp))return false;
        boolean done=true;
        if(button>=STORE&&button<STORE+DrinkRules.STORAGE_SLOTS*2)done=storage(sp,(button-STORE)/2,(button-STORE)%2==1);
        else if(button==UPGRADE)return station.upgrade(sp);
        else if(button==MODE_POT||button==MODE_LATTE){if(!DrinkRules.tier(mk).mixing()){notice(WARN,"problem.locked");done=false;}else{station.setMode(button==MODE_LATTE);station.discover();}}
        else if(button==BREW)done=brew(false);
        else if(button==REPLACE)done=brew(true);
        else if(button==CLEAR){station.order.clear();station.setChanged();}
        else if(button==GATHER)done=gather(sp);
        else if(button==TAKE_BACK)done=takeBack(sp);
        else if(button==STEPS){station.steps=(station.steps+1)%(DrinkRules.MAX_STEPS+1);station.setChanged();}
        else if(button>=EFFECT&&button<EFFECT+DrinkData.catalogue().size())done=toggle(DrinkData.id(DrinkData.catalogue().get(button-EFFECT)));
        else if(button>=POT&&button<POT+offered.size())selected=offered.get(button-POT);
        else return false;
        broadcastChanges();return done;
    }

    // What the screen is told
    /** Effects a mixable potion of which is in the storage, the pack or on the table. */
    private Set<MobEffect> available(ServerPlayer sp){
        var set=new HashSet<MobEffect>();
        java.util.function.Consumer<ItemStack> add=s->{if(!s.isEmpty()&&DrinkData.mixable(s))set.add(DrinkData.effectOf(s).getEffect());};
        for(var k:station.kinds)add.accept(k);for(var s:sp.getInventory().items)add.accept(s);for(int i=0;i<3;i++)add.accept(mix.getItem(i));
        return set;
    }
    private int fitTick;private int[] fit=new int[0];
    @Override public void broadcastChanges(){
        if(station!=null&&player instanceof ServerPlayer who&&!mix.getItem(5).isEmpty()&&station.sweeten(who))sound(SoundEvents.BOTTLE_EMPTY,.9f);
        super.broadcastChanges();if(!(player instanceof ServerPlayer sp)||station==null)return;
        if(sp.tickCount%100==0)station.discover();var pots=pots();offered=pots.stream().map(BrewPot.PotEntity::getBlockPos).toList();
        if(selected==null||!offered.contains(selected))selected=pots.stream().filter(BrewPot.PotEntity::empty).map(BrewPot.PotEntity::getBlockPos).findFirst().orElse(offered.isEmpty()?null:offered.get(0));
        var cat=DrinkData.catalogue();
        if(fit.length!=cat.size()||fitTick++%5==0){
            var have=available(sp);fit=new int[cat.size()];
            for(int i=0;i<cat.size();i++)fit[i]=station.order.contains(DrinkData.id(cat.get(i)))?(have.contains(cat.get(i))?3:4):have.contains(cat.get(i))?2:0;
        }
        var n=new CompoundTag();n.putBoolean("Latte",station.latteMode);n.putInt("Steps",station.steps);n.putInt("Tank",station.tank);n.putString("Problem",problem());
        var drink=DrinkRules.tier(mk).mixing()?preview():null;if(drink!=null)n.put("Preview",drink.save());
        n.putIntArray("Fit",fit);var order=new int[station.order.size()];for(int i=0;i<order.length;i++)order[i]=indexOf(cat,station.order.get(i));n.putIntArray("Order",order);
        boolean gatherable=!station.order.isEmpty();for(var id:station.order){int at=indexOf(cat,id);if(at<0||fit[at]<3)gatherable=false;}
        n.putBoolean("Gatherable",gatherable);n.putInt("Limit",limit());
        n.putInt("BrewTime",station.brewTime);n.putInt("Fuel",station.fuel);n.putInt("Total",station.total());
        var store=new ListTag();for(int i=0;i<station.kinds.length;i++)if(!station.kinds[i].isEmpty()){var t=station.kinds[i].save(new CompoundTag());t.putByte("Slot",(byte)i);t.putInt("Bottles",station.counts[i]);store.add(t);}n.put("Storage",store);
        n.putInt("NoticeSeq",noticeSeq);n.putInt("NoticeKind",noticeKind);n.putString("Notice",noticeKey);var args=new ListTag();noticeArgs.forEach(a->args.add(StringTag.valueOf(a)));n.put("NoticeArgs",args);
        if(selected!=null)n.putLong("Selected",selected.asLong());
        var list=new ListTag();for(var pot:pots){var c=new CompoundTag();c.putLong("Pos",pot.getBlockPos().asLong());if(pot.drink!=null)c.put("Drink",pot.drink.save());list.add(c);}n.put("Pots",list);n.putInt("Capacity",DrinkRules.tier(mk).pots());
        var price=Economy.drinkStation(mk);if(price!=null){n.put("UpgradeItem",new ItemStack(price.item(),price.amount()).save(new CompoundTag()));n.putInt("UpgradeCost",price.amount());n.putInt("UpgradeHave",sp.isCreative()?price.amount():Economy.have(Supply.at(sp,pos),price.item()));}
        if(!n.equals(lastView)||!catalogueSent){
            lastView=n.copy();var packet=n.copy();
            if(!catalogueSent){var ids=new ListTag();for(var e:cat)ids.add(StringTag.valueOf(DrinkData.id(e).toString()));packet.put("Catalogue",ids);catalogueSent=true;}
            BeaconNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(()->sp),new View(containerId,pos,packet));
        }
    }
    private static int indexOf(List<MobEffect> cat,ResourceLocation id){for(int i=0;i<cat.size();i++)if(DrinkData.id(cat.get(i)).equals(id))return i;return -1;}

    @Override public ItemStack quickMoveStack(Player p,int index){
        if(index<0||index>=slots.size())return ItemStack.EMPTY;var slot=slots.get(index);if(!slot.hasItem()||!slot.mayPickup(p))return ItemStack.EMPTY;var stack=slot.getItem();var copy=stack.copy();
        if(index<PLAYER_START){if(!moveItemStackTo(stack,PLAYER_START,slots.size(),true))return ItemStack.EMPTY;}
        else if(stack.is(Items.BLAZE_POWDER)){if(!moveItemStackTo(stack,4,5,false)&&!moveItemStackTo(stack,3,4,false))return ItemStack.EMPTY;}
        else if(BrewingRecipeRegistry.isValidIngredient(stack)&&!DrinkData.isPotion(stack)){if(!moveItemStackTo(stack,3,4,false))return ItemStack.EMPTY;}
        else if(CupDispenser.cup(stack)&&slots.get(SLOT_CUP).isActive()){if(!moveItemStackTo(stack,SLOT_CUP,SLOT_CUP+1,false))return ItemStack.EMPTY;}
        else if(stack.is(Items.GLASS_BOTTLE)||DrinkData.isPotion(stack)&&DrinkData.effects(stack).isEmpty()&&BrewingRecipeRegistry.isValidInput(stack)){
            // Water, awkward and other plain bottles go to the stand first, then the storage
            if(!moveItemStackTo(stack,0,3,false)){if(!DrinkData.isPotion(stack)||p.level().isClientSide||station==null)return ItemStack.EMPTY;int n=station.insert(stack,stack.getCount());if(n==0)return ItemStack.EMPTY;stack.shrink(n);}
        }
        else if(DrinkData.isPotion(stack)){
            // Every other potion goes into the counted storage (the server moves it; the client waits for the update)
            if(p.level().isClientSide||station==null)return ItemStack.EMPTY;
            int n=station.insert(stack,stack.getCount());if(n==0){notice(WARN,"storage_full");return ItemStack.EMPTY;}stack.shrink(n);
        }
        else return ItemStack.EMPTY;
        if(stack.isEmpty())slot.set(ItemStack.EMPTY);else slot.setChanged();slot.onTake(p,stack);return copy;
    }
    record View(int menu,BlockPos pos,CompoundTag data){
        static void encode(View p,FriendlyByteBuf b){b.writeVarInt(p.menu);b.writeBlockPos(p.pos);b.writeNbt(p.data);}
        static View decode(FriendlyByteBuf b){int id=b.readVarInt();var pos=b.readBlockPos();var n=b.readNbt();return new View(id,pos,n==null?new CompoundTag():n);}
        static void handle(View p,Supplier<NetworkEvent.Context> ctx){ctx.get().enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->DrinkClient.view(p)));ctx.get().setPacketHandled(true);}
    }
}
