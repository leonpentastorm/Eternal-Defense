package dev.eternaldefense.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** The Logistics Center Hub's menu, a vanilla-style chest screen: 9 columns, 54 slots a page, 5 pages. */
final class HubMenu extends AbstractContainerMenu {
    static final int PREV=0,NEXT=1,ROWS=6,INV_Y=139;
    final BlockPos anchor;final HubData.Hub hub;final Container page;
    /** The page shown (0..4) and the Hub's slots in use, synced to the client. */
    private final DataSlot pageSlot=DataSlot.standalone(),used=DataSlot.standalone();

    /** Server side: a page of the Hub's storage as a 54-slot container. */
    HubMenu(int id,Inventory inv,HubData.Hub hub,BlockPos anchor){this(id,inv,hub,anchor,null);}
    private HubMenu(int id,Inventory inv,HubData.Hub hub,BlockPos anchor,Container clientView){
        super(EternalDefense.LOGISTICS_HUB_MENU.get(),id);this.hub=hub;this.anchor=anchor;
        this.page=clientView!=null?clientView:new PageView();
        for(int row=0;row<ROWS;row++)for(int col=0;col<9;col++)addSlot(new Slot(page,col+row*9,8+col*18,18+row*18));
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inv,9+row*9+col,8+col*18,INV_Y+row*18));
        for(int col=0;col<9;col++)addSlot(new Slot(inv,col,8+col*18,INV_Y+58));
        addDataSlot(pageSlot);addDataSlot(used);
        if(hub!=null)used.set(hub.storage.usedSlots());
    }
    static HubMenu client(int id,Inventory inv,FriendlyByteBuf buf){return new HubMenu(id,inv,null,buf.readBlockPos(),new SimpleContainer(HubStorage.PAGE));}

    int page(){return pageSlot.get();}
    int used(){return used.get();}

    /** The page of the Hub this menu shows: slot i is the Hub's slot page x 54 + i. */
    private final class PageView implements Container {
        int base(){return page()*HubStorage.PAGE;}
        @Override public int getContainerSize(){return HubStorage.PAGE;}
        @Override public boolean isEmpty(){for(int i=0;i<HubStorage.PAGE;i++)if(!getItem(i).isEmpty())return false;return true;}
        @Override public ItemStack getItem(int i){return hub.storage.get(base()+i);}
        @Override public ItemStack removeItem(int i,int n){var s=getItem(i);if(s.isEmpty()||n<=0)return ItemStack.EMPTY;var out=s.split(n);hub.storage.resync(base()+i,1);return out;}
        @Override public ItemStack removeItemNoUpdate(int i){var s=getItem(i);hub.storage.set(base()+i,ItemStack.EMPTY);return s;}
        @Override public void setItem(int i,ItemStack s){hub.storage.set(base()+i,s);}
        @Override public int getMaxStackSize(){return 64;}
        /** A click may change a stack in place and only then say so: the page is re-checked. */
        @Override public void setChanged(){hub.storage.resync(base(),HubStorage.PAGE);}
        @Override public boolean stillValid(Player p){return true;}
        @Override public void clearContent(){}
    }

    @Override public boolean stillValid(Player p){
        if(p.isSpectator()||hub==null||!hub.placed||!hub.anchor.equals(anchor)||!(p.level() instanceof ServerLevel level)||level.dimension()!=hub.dimension)return false;
        return BaseZone.problem(level,anchor)==null&&p.distanceToSqr(anchor.getCenter())<=100;
    }
    @Override public boolean clickMenuButton(Player p,int button){
        if(hub==null||!stillValid(p))return false;
        int next=button==PREV?page()-1:button==NEXT?page()+1:-1;
        if(next<0||next>=HubStorage.PAGES)return false;
        pageSlot.set(next);broadcastChanges();return true;
    }
    @Override public void broadcastChanges(){if(hub!=null)used.set(hub.storage.usedSlots());super.broadcastChanges();}
    @Override public ItemStack quickMoveStack(Player p,int index){
        if(index<0||index>=slots.size())return ItemStack.EMPTY;var slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;
        var stack=slot.getItem();var copy=stack.copy();int hubSlots=HubStorage.PAGE;
        if(index<hubSlots){if(!moveItemStackTo(stack,hubSlots,slots.size(),true))return ItemStack.EMPTY;slot.setChanged();}
        else if(hub!=null){
            // Into the whole Hub, not only this page: onto the same kind first, then the first empty slots
            var rest=hub.storage.insert(stack,false);if(rest.getCount()==stack.getCount())return ItemStack.EMPTY;
            slot.set(rest);
        }else return ItemStack.EMPTY;
        if(stack.isEmpty())slot.set(ItemStack.EMPTY);
        return copy;
    }
}
