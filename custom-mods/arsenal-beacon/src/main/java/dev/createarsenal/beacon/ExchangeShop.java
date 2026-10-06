package dev.createarsenal.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.NetworkEvent;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.Supplier;

/** Trade Ardent Energy for items. Offers live in config/arsenal-beacon-exchange.txt and reload each time the shop opens. */
final class ExchangeShop {
    private ExchangeShop(){}
    static final String FILE="arsenal-beacon-exchange.txt";
    static final List<TextTable.Row> DEFAULT=List.of(new TextTable.Row("arsenal_beacon:universal_ammo_coin",1,4));
    static final String TEMPLATE="""
        # Ardent Energy exchange shop
        #
        # One offer per line:   item_id [x count] = energy cost
        #   minecraft:diamond = 40            one diamond for 40 energy
        #   minecraft:arrow x16 = 20          sixteen arrows for 20 energy
        # Lines starting with # are ignored. If this file has no valid offers, the shop falls back to
        # the default below. Changes apply the next time a player opens the shop.
        #
        # Default: Ardent Energy for Universal Ammo Coins.
        arsenal_beacon:universal_ammo_coin = 4
        """;

    /** Reads the file, creating it from the template if missing. Problems are logged, never fatal. */
    static List<TextTable.Row> offers(){
        Path file=FMLPaths.CONFIGDIR.get().resolve(FILE);
        try{
            if(!Files.exists(file))Files.writeString(file,TEMPLATE);
            var result=TextTable.parse(Files.readAllLines(file),id->{var rl=ResourceLocation.tryParse(id);return rl!=null&&BuiltInRegistries.ITEM.containsKey(rl)&&BuiltInRegistries.ITEM.get(rl)!=net.minecraft.world.item.Items.AIR;},64,9999);
            for(String problem:result.problems())com.mojang.logging.LogUtils.getLogger().warn("{}: {}",FILE,problem);
            return result.rows().isEmpty()?DEFAULT:result.rows();
        }catch(IOException ex){com.mojang.logging.LogUtils.getLogger().warn("Cannot read {}",FILE,ex);return DEFAULT;}
    }

    static final class ShopBlock extends Block {
        static final DirectionProperty FACING=net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING;
        private static final VoxelShape SHAPE=net.minecraft.world.phys.shapes.Shapes.create(0,0,0,1,2,1);
        ShopBlock(){super(Properties.of().strength(3,6).noOcclusion().lightLevel(s->8).sound(net.minecraft.world.level.block.SoundType.METAL));registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH));}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING);}
        @Override public BlockState getStateForPlacement(BlockPlaceContext c){return defaultBlockState().setValue(FACING,c.getHorizontalDirection().getOpposite());}
        @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return ArsenalStructures.cell(s,0,0,0);}
        @Override public PushReaction getPistonPushReaction(BlockState s){return PushReaction.NORMAL;}
        @Override public void onPlace(BlockState s,Level l,BlockPos pos,BlockState old,boolean moving){super.onPlace(s,l,pos,old,moving);if(l instanceof net.minecraft.server.level.ServerLevel)ArsenalStructures.install(l,pos,s);}
        @Override public void tick(BlockState s,net.minecraft.server.level.ServerLevel l,BlockPos pos,net.minecraft.util.RandomSource random){ArsenalStructures.install(l,pos,s);}
        @Override public void onRemove(BlockState s,Level l,BlockPos pos,BlockState next,boolean moving){if(!s.is(next.getBlock()))ArsenalStructures.remove(l,pos);super.onRemove(s,l,pos,next,moving);}
        @Override public InteractionResult use(BlockState s,Level l,BlockPos pos,Player p,InteractionHand hand,BlockHitResult hit){
            if(p instanceof ServerPlayer server&&!BaseZone.disabled(l,pos,server))send(server,pos,"");
            return InteractionResult.sidedSuccess(l.isClientSide);
        }
    }

    static void send(ServerPlayer p,BlockPos pos,String message){
        var data=new CompoundTag();data.putInt("energy",ArdentEnergy.balance(p));data.putBoolean("creative",p.isCreative());
        var list=new ListTag();
        for(var offer:offers()){
            var item=BuiltInRegistries.ITEM.get(new ResourceLocation(offer.id()));var row=new CompoundTag();
            row.put("item",new ItemStack(item,Math.min(offer.count(),item.getMaxStackSize())).save(new CompoundTag()));row.putInt("cost",offer.value());list.add(row);
        }
        data.put("offers",list);
        BeaconNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(()->p),new Open(pos,data,message));
    }
    static void buy(ServerPlayer p,BlockPos pos,int index){
        if(!p.level().hasChunkAt(pos)||p.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)>64||!(p.level().getBlockState(pos).getBlock() instanceof ShopBlock)||BaseZone.problem(p.level(),pos)!=null)return;
        var offers=offers();
        if(index<0||index>=offers.size()){send(p,pos,"That offer no longer exists.");return;}
        var offer=offers.get(index);var item=BuiltInRegistries.ITEM.get(new ResourceLocation(offer.id()));
        var stack=new ItemStack(item,Math.min(offer.count(),item.getMaxStackSize()));
        var costs=List.of(new WeaponPlatform.Cost(Ingredient.of(ArsenalBeacon.ARDENT_ENERGY.get()),offer.value()));
        String result=InventoryPayment.commit(p,costs,stack)?"Bought "+stack.getCount()+" x "+stack.getHoverName().getString()+(p.isCreative()?" (free in Creative).":" for "+offer.value()+" Ardent Energy."):InventoryPayment.failure(p,costs);
        send(p,pos,result);
    }

    record Open(BlockPos pos,CompoundTag data,String message){
        static void encode(Open p,FriendlyByteBuf b){b.writeBlockPos(p.pos);b.writeNbt(p.data);b.writeUtf(p.message,256);}
        static Open decode(FriendlyByteBuf b){var pos=b.readBlockPos();var n=b.readNbt();return new Open(pos,n==null?new CompoundTag():n,b.readUtf(256));}
        static void handle(Open p,Supplier<NetworkEvent.Context> ctx){ctx.get().enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->ExchangeScreen.receive(p)));ctx.get().setPacketHandled(true);}
    }
    record Buy(BlockPos pos,int index){
        static void encode(Buy p,FriendlyByteBuf b){b.writeBlockPos(p.pos);b.writeVarInt(p.index);}
        static Buy decode(FriendlyByteBuf b){return new Buy(b.readBlockPos(),b.readVarInt());}
        static void handle(Buy p,Supplier<NetworkEvent.Context> ctx){var c=ctx.get();c.enqueueWork(()->{if(c.getSender()!=null)buy(c.getSender(),p.pos,p.index);});c.setPacketHandled(true);}
    }
}
