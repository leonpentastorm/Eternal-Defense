package dev.eternaldefense.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;
import java.util.List;
import java.util.function.Supplier;

/** The Defense Depot: turrets, their ammunition and the defenses, for Universal Ammo Coins */
final class DefenseDepot {
    private DefenseDepot(){}
    record Product(String id,Supplier<Item> item,int count,int price){}
    static final List<Product> PRODUCTS=List.of(
        new Product("mg_turret_mk1",()->EternalDefense.TURRETS.get("mg_turret_mk1").get().asItem(),1,Economy.TURRET_PRICE[0]),
        new Product("mg_turret_mk2",()->EternalDefense.TURRETS.get("mg_turret_mk2").get().asItem(),1,Economy.TURRET_PRICE[1]),
        new Product("mg_turret_mk3",()->EternalDefense.TURRETS.get("mg_turret_mk3").get().asItem(),1,Economy.TURRET_PRICE[2]),
        new Product("shotgun_turret_mk1",()->EternalDefense.TURRETS.get("shotgun_turret_mk1").get().asItem(),1,Economy.TURRET_PRICE[0]),
        new Product("shotgun_turret_mk2",()->EternalDefense.TURRETS.get("shotgun_turret_mk2").get().asItem(),1,Economy.TURRET_PRICE[1]),
        new Product("shotgun_turret_mk3",()->EternalDefense.TURRETS.get("shotgun_turret_mk3").get().asItem(),1,Economy.TURRET_PRICE[2]),
        new Product("flame_turret_mk1",()->EternalDefense.TURRETS.get("flame_turret_mk1").get().asItem(),1,Economy.FLAME_TURRET_PRICE[0]),
        new Product("flame_turret_mk2",()->EternalDefense.TURRETS.get("flame_turret_mk2").get().asItem(),1,Economy.FLAME_TURRET_PRICE[1]),
        new Product("flame_turret_mk3",()->EternalDefense.TURRETS.get("flame_turret_mk3").get().asItem(),1,Economy.FLAME_TURRET_PRICE[2]),
        new Product("mg_turret_ammo",()->EternalDefense.MG_AMMO.get(),4,Economy.DEPOT_MG_AMMO),
        new Product("shotgun_turret_ammo",()->EternalDefense.SHOTGUN_AMMO.get(),4,Economy.DEPOT_SHELLS),
        new Product("flame_turret_fuel",()->EternalDefense.FLAME_FUEL.get(),4,Economy.DEPOT_FUEL),
        new Product("sandbag",()->EternalDefense.SANDBAG.get().asItem(),8,Economy.DEPOT_SANDBAGS),
        new Product("barricade",()->EternalDefense.BARRICADE.get().asItem(),4,Economy.DEPOT_BARRICADE),
        new Product("barbed_wire",()->EternalDefense.BARBED_WIRE.get().asItem(),8,Economy.DEPOT_BARBED),
        new Product("shock_box",()->EternalDefense.SHOCK_BOX.get().asItem(),1,Economy.DEPOT_SHOCK_BOX),
        new Product("electric_wire",()->EternalDefense.ELECTRIC_WIRE.get(),8,Economy.DEPOT_WIRE));
    static final int BOUGHT=1,NO_COINS=2,NO_ROOM=3;

    static final class DepotBlock extends Block implements DefenseStructures.Footprint {
        DepotBlock(){super(Properties.of().strength(3,6).noOcclusion().sound(SoundType.WOOD).lightLevel(s->6));registerDefaultState(stateDefinition.any().setValue(DefenseStructures.FACING,Direction.NORTH));}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(DefenseStructures.FACING);}
        @Override public BlockState getStateForPlacement(BlockPlaceContext c){return defaultBlockState().setValue(DefenseStructures.FACING,c.getHorizontalDirection().getOpposite());}
        @Override public List<BlockPos> extra(BlockPos root,BlockState state){var side=DefenseStructures.second(root,state);return List.of(side,root.above(),side.above());}
        @Override public VoxelShape whole(BlockState state){
            var side=state.getValue(DefenseStructures.FACING).getClockWise();
            return Shapes.box(Math.min(0,side.getStepX()),0,Math.min(0,side.getStepZ()),1+Math.max(0,side.getStepX()),2,1+Math.max(0,side.getStepZ()));
        }
        @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return DefenseStructures.cell(s,0,0,0);}
        @Override public PushReaction getPistonPushReaction(BlockState s){return PushReaction.BLOCK;}
        @Override public void onPlace(BlockState s,Level l,BlockPos pos,BlockState old,boolean moving){super.onPlace(s,l,pos,old,moving);if(l instanceof ServerLevel)DefenseStructures.install(l,pos,s);}
        @Override public void tick(BlockState s,ServerLevel l,BlockPos pos,net.minecraft.util.RandomSource random){DefenseStructures.install(l,pos,s);}
        @Override public void onRemove(BlockState s,Level l,BlockPos pos,BlockState next,boolean moving){if(!s.is(next.getBlock()))DefenseStructures.remove(l,pos);super.onRemove(s,l,pos,next,moving);}
        @Override public InteractionResult use(BlockState s,Level l,BlockPos pos,Player p,InteractionHand hand,BlockHitResult hit){
            if(p instanceof ServerPlayer sp)NetworkHooks.openScreen(sp,new SimpleMenuProvider((id,inv,who)->new DepotMenu(id,inv,pos),s.getBlock().getName()),buf->buf.writeBlockPos(pos));
            return InteractionResult.sidedSuccess(l.isClientSide);
        }
    }

    /** No slots: the shop list, the coins the player can spend, the last purchase's result */
    static final class DepotMenu extends AbstractContainerMenu {
        final BlockPos pos;final Player player;
        final DataSlot coins=DataSlot.standalone(),result=DataSlot.standalone(),serial=DataSlot.standalone();
        DepotMenu(int id,Inventory inv,BlockPos pos){super(EternalDefense.DEPOT_MENU.get(),id);this.pos=pos;this.player=inv.player;addDataSlot(coins);addDataSlot(result);addDataSlot(serial);reach();}
        static DepotMenu client(int id,Inventory inv,FriendlyByteBuf buf){return new DepotMenu(id,inv,buf.readBlockPos());}
        private void reach(){if(player instanceof ServerPlayer sp)coins.set(Math.min(32767,Supply.count(Supply.at(sp,pos),EternalDefense.AMMO_COIN.get())));}
        @Override public void broadcastChanges(){reach();super.broadcastChanges();}
        @Override public boolean clickMenuButton(Player p,int id){
            if(!(p instanceof ServerPlayer sp)||id<0||id>=PRODUCTS.size())return false;
            int code=buy(sp,pos,PRODUCTS.get(id));result.set(code);serial.set((serial.get()+1)&0x7fff);return true;
        }
        @Override public ItemStack quickMoveStack(Player p,int index){return ItemStack.EMPTY;}
        @Override public boolean stillValid(Player p){return p.level().getBlockState(pos).is(EternalDefense.DEFENSE_DEPOT.get())&&p.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<=64;}
    }
    /** Pays from the player's supply (the pack, then the zone's Hubs) and hands the goods over */
    static int buy(ServerPlayer p,BlockPos pos,Product product){
        var costs=List.of(WeaponPlatform.cost("eternal_defense:universal_ammo_coin",product.price()));
        var out=new ItemStack(product.item().get(),product.count());
        if(!p.isCreative()&&Supply.count(Supply.at(p,pos),EternalDefense.AMMO_COIN.get())<product.price())return NO_COINS;
        if(p.isCreative()){if(!p.getInventory().add(out))p.drop(out,false);return BOUGHT;}
        return WeaponPlatform.transact(Supply.at(p,pos),costs,out)?BOUGHT:NO_ROOM;
    }
    static Component result(int code){return Component.translatable("gui.eternal_defense.depot.result."+switch(code){case BOUGHT->"bought";case NO_COINS->"coins";default->"room";});}
}
