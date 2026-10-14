package dev.eternaldefense.beacon;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;

/** The Loadout Station's menu: the kit to pack, Make coin, the 9 shared coin slots, Set active and Print; a second page shows the locker. */
final class LoadoutMenu extends AbstractContainerMenu {
    /** 240 tall: it fits a 720p window at GUI scale 3. The coin slots stand in a column (the wardrobe), the kit in rows. */
    static final int W=408,H=240,INV_X=238,INV_Y=148,ROW_A=42,ROW_B=62,LOCKER_Y=42,OFFHAND_X=316,MAKE_X=340;
    static final int WARD_X=150,WARD_W=80,COIN_X=WARD_X+15,COIN_Y=44,COIN_ROW=20;
    static final int MAKE=0,SET_ACTIVE=1,PRINT=2,FORGET=3,PAGE=4,SELECT=10;
    /** Slot indices: the kit, the coin slots, the locker, the remembered kinds (never shown as slots), the player's inventory. */
    static final int PACK=0,COINS=PACK+LoadoutRules.KIT_SLOTS,LOCKER=COINS+LoadoutRules.COIN_SLOTS,KINDS=LOCKER+LoadoutRules.LOCKER_SLOTS,INV=KINDS+LoadoutRules.COIN_SLOTS;
    /** Synced numbers: selected slot, the player's active slot, page, busy, seconds of cooldown left, plating and Ardent Energy the fee can reach. */
    static final int D_SELECTED=0,D_ACTIVE=1,D_PAGE=2,D_BUSY=3,D_COOLDOWN=4,D_PLATING=5,D_ARDENT=6;
    private static final EquipmentSlot[] ARMOR={EquipmentSlot.FEET,EquipmentSlot.LEGS,EquipmentSlot.CHEST,EquipmentSlot.HEAD};

    final BlockPos anchor;final LoadoutData.Machine machine;final Player player;
    final SimpleContainer pack=new SimpleContainer(LoadoutRules.KIT_SLOTS);final Container coins,locker,kinds;
    final ContainerData data;private int ticks;

    LoadoutMenu(int id,Inventory inv,LoadoutData.Machine machine){this(id,inv,machine,machine.anchor,machine.shelf,machine.locker,new Kinds(machine),new SimpleContainerData(7));}
    private LoadoutMenu(int id,Inventory inv,LoadoutData.Machine machine,BlockPos anchor,Container coins,Container locker,Container kinds,ContainerData data){
        super(EternalDefense.LOADOUT_MENU.get(),id);this.machine=machine;this.anchor=anchor;this.player=inv.player;this.coins=coins;this.locker=locker;this.kinds=kinds;this.data=data;
        // The kit as it is worn: head to feet, the off hand, then the hotbar row
        for(int i=0;i<LoadoutRules.ARMOR;i++){int kit=LoadoutRules.ARMOR-1-i;addSlot(new PackSlot(pack,kit,INV_X+i*18,ROW_A){
            @Override public int getMaxStackSize(){return 1;}
            @Override public boolean mayPlace(ItemStack s){return super.mayPlace(s)&&s.canEquip(ARMOR[getContainerSlot()],player);}
        });}
        addSlot(new PackSlot(pack,LoadoutRules.OFFHAND_SLOT,OFFHAND_X,ROW_A));
        for(int i=0;i<LoadoutRules.HOTBAR;i++)addSlot(new PackSlot(pack,LoadoutRules.HOTBAR_FIRST+i,INV_X+i*18,ROW_B));
        for(int i=0;i<LoadoutRules.COIN_SLOTS;i++)addSlot(new Slot(coins,i,COIN_X,COIN_Y+i*COIN_ROW){
            @Override public boolean mayPlace(ItemStack s){return acceptsCoin(getContainerSlot(),s);}
            @Override public int getMaxStackSize(){return LoadoutRules.COIN_STACK;}
        });
        for(int i=0;i<LoadoutRules.LOCKER_SLOTS;i++)addSlot(new Slot(locker,i,INV_X+i%9*18,LOCKER_Y+i/9*18){
            @Override public boolean isActive(){return page()==1;}
            @Override public boolean mayPlace(ItemStack s){return false;}
        });
        for(int i=0;i<LoadoutRules.COIN_SLOTS;i++)addSlot(new Slot(kinds,i,-10000,-10000){
            @Override public boolean isActive(){return false;}
            @Override public boolean mayPlace(ItemStack s){return false;}
            @Override public boolean mayPickup(Player p){return false;}
        });
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inv,9+row*9+col,INV_X+col*18,INV_Y+row*18));
        for(int col=0;col<9;col++)addSlot(new Slot(inv,col,INV_X+col*18,INV_Y+58));
        addDataSlots(data);
        if(machine!=null&&player instanceof ServerPlayer sp){var d=LoadoutData.get(sp.serverLevel());data.set(D_ACTIVE,d.players.active(sp.getUUID()));data.set(D_SELECTED,d.players.active(sp.getUUID()));refresh(sp);}
    }
    static LoadoutMenu client(int id,Inventory inv,FriendlyByteBuf buf){
        return new LoadoutMenu(id,inv,null,buf.readBlockPos(),new SimpleContainer(LoadoutRules.COIN_SLOTS),new SimpleContainer(LoadoutRules.LOCKER_SLOTS),new SimpleContainer(LoadoutRules.COIN_SLOTS),new SimpleContainerData(7));
    }

    /** A kit slot: anything but a coin, shown on the first page. */
    private class PackSlot extends Slot {
        PackSlot(Container c,int i,int x,int y){super(c,i,x,y);}
        @Override public boolean isActive(){return page()==0;}
        @Override public boolean mayPlace(ItemStack s){return !s.is(EternalDefense.LOADOUT_COIN.get());}
    }
    /** The remembered coin kinds of the machine, read only. */
    private static final class Kinds extends SimpleContainer {
        final LoadoutData.Machine m;Kinds(LoadoutData.Machine m){super(LoadoutRules.COIN_SLOTS);this.m=m;}
        @Override public ItemStack getItem(int i){return m.kind(i);}
    }

    int page(){return data.get(D_PAGE);}
    int selected(){return data.get(D_SELECTED);}
    int active(){return data.get(D_ACTIVE);}
    boolean busy(){return data.get(D_BUSY)!=0;}
    int cooldown(){return data.get(D_COOLDOWN);}
    /** The coin kind slot i wants (empty when it takes any coin). */
    ItemStack kind(int i){return kinds.getItem(i);}
    boolean acceptsCoin(int i,ItemStack s){
        if(!s.is(EternalDefense.LOADOUT_COIN.get()))return false;
        if(machine!=null)return machine.accepts(i,s);
        var kind=kind(i);return kind.isEmpty()||ItemStack.isSameItemSameTags(kind,s);
    }

    @Override public boolean stillValid(Player p){
        if(p.isSpectator()||machine==null||!machine.placed||!machine.anchor.equals(anchor)||!(p.level() instanceof ServerLevel level)||level.dimension()!=machine.dimension)return false;
        return BaseZone.problem(level,anchor)==null&&p.distanceToSqr(anchor.getCenter())<=100&&!LoadoutPrint.holding(p);
    }
    @Override public void removed(Player p){super.removed(p);if(p instanceof ServerPlayer)clearContainer(p,pack);}
    @Override public void broadcastChanges(){if(machine!=null&&player instanceof ServerPlayer sp&&++ticks%10==0)refresh(sp);super.broadcastChanges();}
    private void refresh(ServerPlayer sp){
        var d=LoadoutData.get(sp.serverLevel());var w=Supply.at(sp,anchor);
        data.set(D_ACTIVE,d.players.active(sp.getUUID()));data.set(D_BUSY,LoadoutPrint.at(machine.id)!=null?1:0);
        data.set(D_COOLDOWN,Math.min(Short.MAX_VALUE,LoadoutRules.secondsLeft(d.players.readyAt(sp.getUUID()),sp.server.overworld().getGameTime())));
        data.set(D_PLATING,Math.min(Short.MAX_VALUE,Supply.count(w,EternalDefense.PLATING.get())));data.set(D_ARDENT,Math.min(Short.MAX_VALUE,Supply.count(w,EternalDefense.ARDENT_ENERGY.get())));
    }
    @Override public boolean clickMenuButton(Player p,int button){
        if(!(p instanceof ServerPlayer sp)||machine==null||!stillValid(p))return false;
        var d=LoadoutData.get(sp.serverLevel());
        if(button>=SELECT&&button<SELECT+LoadoutRules.COIN_SLOTS){data.set(D_SELECTED,button-SELECT);return true;}
        switch(button){
            case MAKE->make(sp);
            case SET_ACTIVE->{d.players.setActive(sp.getUUID(),selected());d.setDirty();data.set(D_ACTIVE,selected());LoadoutPrint.say(sp,"active_set",ChatFormatting.AQUA,selected()+1);}
            case PRINT->LoadoutPrint.request(sp,machine,selected());
            case FORGET->{if(machine.forget(selected()))LoadoutPrint.say(sp,"forgot",ChatFormatting.AQUA,selected()+1);else LoadoutPrint.say(sp,"forget_full",ChatFormatting.RED,selected()+1);}
            case PAGE->data.set(D_PAGE,page()==0?1:0);
            default->{return false;}
        }
        refresh(sp);return true;
    }
    /** Make coin: the kit and the fee, all or nothing; the coin goes into the inventory. */
    private void make(ServerPlayer sp){
        var kit=new ArrayList<ItemStack>();for(int i=0;i<LoadoutRules.KIT_SLOTS;i++)kit.add(pack.getItem(i));
        String problem=LoadoutCoin.problem(kit,LoadoutCoin.limitBytes());
        if(problem!=null){LoadoutPrint.say(sp,"coin."+problem,ChatFormatting.RED,DefenseConfig.LOADOUT_KIT_LIMIT_KB.get());sp.playNotifySound(SoundEvents.VILLAGER_NO,SoundSource.PLAYERS,.6f,1.2f);return;}
        var coin=LoadoutCoin.create(kit);var w=Supply.at(sp,anchor);var costs=Economy.coinFee();
        if(!InventoryPayment.commit(w,costs,coin)){sp.displayClientMessage(Component.literal(InventoryPayment.failure(w,costs)).withStyle(ChatFormatting.RED),true);sp.playNotifySound(SoundEvents.VILLAGER_NO,SoundSource.PLAYERS,.6f,1.2f);return;}
        pack.clearContent();
        LoadoutPrint.say(sp,"coin.made",ChatFormatting.GREEN,LoadoutRules.kitItems(coin.getTag()));
        sp.playNotifySound(SoundEvents.ANVIL_USE,SoundSource.BLOCKS,.5f,1.6f);sp.playNotifySound(SoundEvents.FIRE_EXTINGUISH,SoundSource.BLOCKS,.3f,1.4f);
        com.mojang.logging.LogUtils.getLogger().info("[Loadout Station] {} made a coin of {} stacks ({} bytes)",sp.getGameProfile().getName(),LoadoutRules.kitItems(coin.getTag()),LoadoutRules.bytes(coin.getTag()));
    }

    @Override public ItemStack quickMoveStack(Player p,int index){
        if(index<0||index>=slots.size())return ItemStack.EMPTY;var slot=slots.get(index);if(!slot.hasItem()||!slot.mayPickup(p))return ItemStack.EMPTY;
        var stack=slot.getItem();var copy=stack.copy();
        if(index<INV){if(!moveItemStackTo(stack,INV,INV+36,true))return ItemStack.EMPTY;}
        else{
            boolean moved;
            if(stack.is(EternalDefense.LOADOUT_COIN.get()))moved=moveItemStackTo(stack,COINS+selected(),COINS+selected()+1,false)||moveItemStackTo(stack,COINS,COINS+LoadoutRules.COIN_SLOTS,false);
            else if(page()!=0)return ItemStack.EMPTY;
            else moved=moveItemStackTo(stack,PACK,PACK+LoadoutRules.ARMOR,false)||moveItemStackTo(stack,PACK+LoadoutRules.ARMOR+1,PACK+LoadoutRules.KIT_SLOTS,false)||moveItemStackTo(stack,PACK+LoadoutRules.ARMOR,PACK+LoadoutRules.ARMOR+1,false);
            if(!moved)return ItemStack.EMPTY;
        }
        if(stack.isEmpty())slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();
        return copy;
    }
}
