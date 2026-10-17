package dev.eternaldefense.beacon;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import java.util.List;

/** The Handheld Radio: carries the four flares, plants the chosen one where you look, and picks the Loadout kit a call brings. */
final class HandheldRadio {
    private HandheldRadio(){}
    static final SupportCalls.Kind[] KINDS=SupportCalls.Kind.values();

    // What the radio holds
    static int count(ItemStack radio,SupportCalls.Kind kind){var t=radio.getTag();return t==null?0:Math.max(0,t.getCompound("Flares").getInt(kind.id));}
    static void count(ItemStack radio,SupportCalls.Kind kind,int n){var flares=radio.getOrCreateTag().getCompound("Flares");flares.putInt(kind.id,Math.max(0,Math.min(SupportRules.RADIO_FLARES,n)));radio.getOrCreateTag().put("Flares",flares);}
    static SupportCalls.Kind call(ItemStack radio){var t=radio.getTag();if(t!=null)for(var k:KINDS)if(k.id.equals(t.getString("Call")))return k;return SupportCalls.Kind.FIRE;}
    static void call(ItemStack radio,SupportCalls.Kind kind){radio.getOrCreateTag().putString("Call",kind.id);}
    static Item flare(SupportCalls.Kind kind){return switch(kind){case SUPPLY->EternalDefense.SUPPLY_FLARE.get();case RETURN->EternalDefense.RETURN_FLARE.get();case FIRE->EternalDefense.FIRE_FLARE.get();case LOADOUT->EternalDefense.LOADOUT_FLARE.get();};}

    /** Pure: where a marker lands: on the top face it was aimed at, else on the ground under the block in front of the face (null when there is none) */
    static Vec3 marker(Vec3 hit,Direction face,BlockPos front,Double groundTop){
        if(face==Direction.UP)return hit;
        return groundTop==null?null:new Vec3(front.getX()+.5,groundTop,front.getZ()+.5);
    }
    /** The spot the player looks at, up to SupportRules.RADIO_RANGE blocks away (null when it is not ground) */
    static Vec3 target(ServerPlayer p){
        var level=p.level();Vec3 eye=p.getEyePosition(),end=eye.add(p.getViewVector(1).scale(SupportRules.RADIO_RANGE));
        var hit=level.clip(new ClipContext(eye,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
        if(hit.getType()!=HitResult.Type.BLOCK)return null;
        var front=hit.getBlockPos().relative(hit.getDirection());Double top=null;
        if(hit.getDirection()!=Direction.UP&&level.getBlockState(front).getCollisionShape(level,front).isEmpty())
            for(int i=1;i<=SupportRules.RADIO_DROP&&top==null;i++){var below=front.below(i);var shape=level.getBlockState(below).getCollisionShape(level,below);if(!shape.isEmpty())top=below.getY()+shape.max(Direction.Axis.Y);}
        return marker(hit.getLocation(),hit.getDirection(),front,top);
    }

    static final class RadioItem extends Item {
        RadioItem(){super(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));}
        @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
            var stack=player.getItemInHand(hand);
            if(level.isClientSide)return InteractionResultHolder.success(stack);
            if(!(player instanceof ServerPlayer sp))return InteractionResultHolder.fail(stack);
            if(sp.isShiftKeyDown()){open(sp,hand);return InteractionResultHolder.consume(stack);}
            plant(sp,stack);return InteractionResultHolder.consume(stack);
        }
        @Override public void appendHoverText(ItemStack s,Level l,List<Component> lines,TooltipFlag f){
            lines.add(Component.translatable("tooltip.eternal_defense.radio.use").withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("tooltip.eternal_defense.radio.calls",flare(call(s)).getDescription()).withStyle(ChatFormatting.AQUA));
            for(var k:KINDS)lines.add(Component.translatable("tooltip.eternal_defense.radio.holds",flare(k).getDescription(),count(s,k),SupportRules.RADIO_FLARES).withStyle(count(s,k)>0?ChatFormatting.DARK_AQUA:ChatFormatting.DARK_GRAY));
        }
    }
    /** Plants the chosen flare at the spot looked at; one stored flare is used when the call goes out */
    static void plant(ServerPlayer p,ItemStack radio){
        var kind=call(radio);
        if(count(radio,kind)<=0){say(p,"radio.empty",ChatFormatting.YELLOW,flare(kind).getDescription());return;}
        var at=target(p);
        if(at==null){say(p,"radio.no_ground",ChatFormatting.YELLOW,SupportRules.RADIO_RANGE);return;}
        if(!SupportFlares.call(p,kind,at))return;
        if(!p.getAbilities().instabuild)count(radio,kind,count(radio,kind)-1);
        p.level().playSound(null,p.getX(),p.getY(),p.getZ(),DefenseSounds.RADIO.get(),SoundSource.PLAYERS,.7f,1.15f);
    }
    static void say(ServerPlayer p,String key,ChatFormatting color,Object... args){p.displayClientMessage(Component.translatable("gui.eternal_defense."+key,args).withStyle(color),true);}
    static void open(ServerPlayer p,InteractionHand hand){
        var station=LoadoutCall.station(p.server);var data=LoadoutData.get(p.server);
        p.level().playSound(null,p.getX(),p.getY(),p.getZ(),DefenseSounds.RADIO.get(),SoundSource.PLAYERS,.8f,1f);
        NetworkHooks.openScreen(p,new SimpleMenuProvider((id,inv,player)->new RadioMenu(id,inv,hand,station),p.getItemInHand(hand).getHoverName()),buf->{
            buf.writeBoolean(hand==InteractionHand.MAIN_HAND);buf.writeBoolean(station!=null);
            for(int i=0;i<LoadoutRules.COIN_SLOTS;i++){buf.writeItem(station==null?ItemStack.EMPTY:station.coins[i]);buf.writeItem(station==null?ItemStack.EMPTY:station.kind(i));}
            buf.writeVarInt(data.players.active(p.getUUID()));
        });
    }

    static final class RadioMenu extends AbstractContainerMenu {
        static final int W=232,H=254,FLARE_Y=46,COL=54,FLARE_X=8,INV_X=35,INV_Y=170,LOADOUT=10;
        final InteractionHand hand;final int locked;final Player player;
        final SimpleContainer flares=new SimpleContainer(KINDS.length);
        final DataSlot kind=DataSlot.standalone(),active=DataSlot.standalone();
        /** What the client shows of the station: each slot's coins and the coin kind it keeps */
        final ItemStack[] coins=new ItemStack[LoadoutRules.COIN_SLOTS],kinds=new ItemStack[LoadoutRules.COIN_SLOTS];boolean station;
        RadioMenu(int id,Inventory inv,InteractionHand hand,LoadoutData.Machine machine){
            this(id,inv,hand);station=machine!=null;
            for(int i=0;i<coins.length;i++){coins[i]=machine==null?ItemStack.EMPTY:machine.coins[i].copy();kinds[i]=machine==null?ItemStack.EMPTY:machine.kind(i).copy();}
            var radio=radio();for(int i=0;i<KINDS.length;i++){int n=count(radio,KINDS[i]);flares.setItem(i,n>0?new ItemStack(flare(KINDS[i]),n):ItemStack.EMPTY);}
            kind.set(call(radio).ordinal());active.set(LoadoutData.get(((ServerPlayer)inv.player).server).players.active(inv.player.getUUID()));
            flares.addListener(c->save());
        }
        private RadioMenu(int id,Inventory inv,InteractionHand hand){
            super(EternalDefense.RADIO_MENU.get(),id);this.hand=hand;this.player=inv.player;locked=hand==InteractionHand.MAIN_HAND?inv.selected:-1;
            java.util.Arrays.fill(coins,ItemStack.EMPTY);java.util.Arrays.fill(kinds,ItemStack.EMPTY);
            for(int i=0;i<KINDS.length;i++)addSlot(new FlareSlot(flares,i,FLARE_X+i*COL+COL/2-8,FLARE_Y));
            for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inv,9+row*9+col,INV_X+col*18,INV_Y+row*18));
            for(int col=0;col<9;col++)addSlot(col==locked?new LockedSlot(inv,col,INV_X+col*18,INV_Y+58):new Slot(inv,col,INV_X+col*18,INV_Y+58));
            addDataSlot(kind);addDataSlot(active);
        }
        static RadioMenu client(int id,Inventory inv,FriendlyByteBuf buf){
            var m=new RadioMenu(id,inv,buf.readBoolean()?InteractionHand.MAIN_HAND:InteractionHand.OFF_HAND);m.station=buf.readBoolean();
            for(int i=0;i<LoadoutRules.COIN_SLOTS;i++){m.coins[i]=buf.readItem();m.kinds[i]=buf.readItem();}
            m.active.set(buf.readVarInt());return m;
        }
        ItemStack radio(){return player.getItemInHand(hand);}
        private void save(){
            if(player.level().isClientSide)return;var radio=radio();if(!(radio.getItem() instanceof RadioItem))return;
            for(int i=0;i<KINDS.length;i++)count(radio,KINDS[i],flares.getItem(i).getCount());
        }
        SupportCalls.Kind kind(){return SupportCalls.Kind.of(kind.get());}
        @Override public boolean stillValid(Player p){return radio().getItem() instanceof RadioItem;}
        /** Call type buttons 0 to 3, Loadout slots LOADOUT to LOADOUT+8 */
        @Override public boolean clickMenuButton(Player p,int button){
            if(!(p instanceof ServerPlayer sp))return false;
            if(button>=0&&button<KINDS.length){kind.set(button);call(radio(),KINDS[button]);return true;}
            int slot=button-LOADOUT;
            if(slot>=0&&slot<LoadoutRules.COIN_SLOTS){var d=LoadoutData.get(sp.server);d.players.setActive(sp.getUUID(),slot);d.setDirty();active.set(slot);return true;}
            return false;
        }
        @Override public void clicked(int slot,int button,ClickType type,Player p){
            // The radio stays in the hand while its menu is open
            if(type==ClickType.SWAP&&(button==locked||hand==InteractionHand.OFF_HAND&&button==Inventory.SLOT_OFFHAND))return;
            super.clicked(slot,button,type,p);
        }
        @Override public ItemStack quickMoveStack(Player p,int index){
            var slot=slots.get(index);if(!slot.hasItem()||!slot.mayPickup(p))return ItemStack.EMPTY;
            var stack=slot.getItem();var copy=stack.copy();int n=KINDS.length;
            if(index<n){if(!moveItemStackTo(stack,n,n+36,true))return ItemStack.EMPTY;}
            else{
                int target=-1;for(int i=0;i<n;i++)if(slots.get(i).mayPlace(stack))target=i;
                if(target<0||!moveItemStackTo(stack,target,target+1,false))return ItemStack.EMPTY;
            }
            if(stack.isEmpty())slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();
            return copy;
        }
        @Override public void removed(Player p){super.removed(p);save();}
    }
    /** One flare kind per slot, up to SupportRules.RADIO_FLARES */
    static final class FlareSlot extends Slot {
        FlareSlot(SimpleContainer c,int i,int x,int y){super(c,i,x,y);}
        @Override public boolean mayPlace(ItemStack s){return s.getItem() instanceof SupportFlares.FlareItem f&&f.kind==KINDS[getContainerSlot()];}
        @Override public int getMaxStackSize(){return SupportRules.RADIO_FLARES;}
    }
    private static final class LockedSlot extends Slot {
        LockedSlot(Inventory inv,int i,int x,int y){super(inv,i,x,y);}
        @Override public boolean mayPickup(Player p){return false;}
        @Override public boolean mayPlace(ItemStack s){return false;}
    }
}
