package dev.createarsenal.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;
import java.util.List;
import java.util.function.Supplier;

/** Purchases and Mk upgrades at the Support Platform. Results travel back through the menu's synced data slots. */
final class SupportShop {
    private SupportShop(){}
    static final int BOUGHT=1,NO_ENERGY=2,NO_PLATING=3,MAXED=4,UPGRADED=5;
    record Product(String id,Supplier<Item> item,int price){}
    static final List<Product> PRODUCTS=List.of(
        new Product("cannon",()->ArsenalBeacon.SUPPORT_CANNON_ITEM.get(),SupportRules.CANNON_PRICE),
        new Product("supply",()->ArsenalBeacon.SUPPLY_FLARE.get(),SupportRules.SUPPLY_FLARE_PRICE),
        new Product("return",()->ArsenalBeacon.RETURN_FLARE.get(),SupportRules.RETURN_FLARE_PRICE),
        new Product("fire",()->ArsenalBeacon.FIRE_FLARE.get(),SupportRules.FIRE_FLARE_PRICE));

    static void buy(ServerPlayer p,int index){
        if(!(p.containerMenu instanceof SupportPlatform.PlatformMenu menu)||!menu.stillValid(p)||index<0||index>=PRODUCTS.size())return;
        var product=PRODUCTS.get(index);
        if(!ArdentEnergy.spend(p,product.price())){menu.report(NO_ENERGY);return;}
        var stack=new ItemStack(product.item().get());
        if(!p.getInventory().add(stack))p.drop(stack,false);
        menu.report(BOUGHT);
    }
    static void upgrade(ServerPlayer p){
        if(!(p.containerMenu instanceof SupportPlatform.PlatformMenu menu)||!menu.stillValid(p))return;
        int cost=SupportRules.upgradeCost(menu.mk);
        if(cost<0){menu.report(MAXED);return;}
        var costs=List.of(new WeaponPlatform.Cost(Ingredient.of(ArsenalBeacon.PLATING.get()),cost));
        if(!InventoryPayment.commit(p,costs,ItemStack.EMPTY)){menu.report(NO_PLATING);return;}
        var level=p.serverLevel();BlockPos pos=menu.pos;var state=level.getBlockState(pos);
        level.setBlock(pos,state.setValue(SupportPlatform.MK,menu.mk+1),3);
        var be=level.getBlockEntity(pos);
        if(be instanceof SupportPlatform.PlatformEntity platform){
            platform.setChanged();
            p.closeContainer();
            NetworkHooks.openScreen(p,platform,buf->{buf.writeBlockPos(pos);buf.writeVarInt(menu.mk+1);});
            if(p.containerMenu instanceof SupportPlatform.PlatformMenu next)next.report(UPGRADED);
        }
    }

    record Buy(int index){
        static void encode(Buy p,FriendlyByteBuf b){b.writeVarInt(p.index);}
        static Buy decode(FriendlyByteBuf b){return new Buy(b.readVarInt());}
        static void handle(Buy p,Supplier<NetworkEvent.Context> ctx){var c=ctx.get();c.enqueueWork(()->{if(c.getSender()!=null)buy(c.getSender(),p.index);});c.setPacketHandled(true);}
    }
    record Upgrade(){
        static void encode(Upgrade p,FriendlyByteBuf b){}
        static Upgrade decode(FriendlyByteBuf b){return new Upgrade();}
        static void handle(Upgrade p,Supplier<NetworkEvent.Context> ctx){var c=ctx.get();c.enqueueWork(()->{if(c.getSender()!=null)upgrade(c.getSender());});c.setPacketHandled(true);}
    }
}
