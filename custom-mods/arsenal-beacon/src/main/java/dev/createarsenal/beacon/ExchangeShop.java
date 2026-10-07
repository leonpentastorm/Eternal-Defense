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

/** Buy items with Ardent Energy and sell items for it. Offers live in config/arsenal-beacon-exchange.txt ([sell] and [buy] sections) and reload each time the shop opens. */
final class ExchangeShop {
    private ExchangeShop(){}
    static final String FILE="arsenal-beacon-exchange.txt";
    record Offers(List<TextTable.Row> buy,List<TextTable.Row> sell){}
    static final String HEADER="""
        # Ardent Energy exchange shop
        #
        # [sell]  you hand in the items and receive Ardent Energy      item_id [x count] = energy you get
        # [buy]   you pay Ardent Energy and receive the items         item_id [x count] = energy you pay
        #   minecraft:diamond = 6          one diamond costs 6 energy
        #   minecraft:arrow x16 = 3        sixteen arrows cost 3 energy
        # Buying always costs more than selling, so trading in circles never pays. Lines starting with # are ignored.
        # Changes apply the next time a player opens the shop. Delete this file to get the defaults back.
        """;
    /** Pack edition: sell the surplus of a factory, buy parts at a premium when farming instead. */
    static final String PACK="""
        [sell]
        arsenal_beacon:reinforced_plating x2 = 1
        arsenal_beacon:logistics_module x2 = 1
        arsenal_beacon:resonance_coil x2 = 1
        arsenal_beacon:restoration_matrix = 1
        create:brass_ingot x16 = 1
        create:andesite_alloy x32 = 1
        create:precision_mechanism x2 = 1
        create:electron_tube x8 = 1
        kubejs:cartridge_case x32 = 1
        kubejs:propellant x32 = 1

        [buy]
        arsenal_beacon:universal_ammo_coin x16 = 3
        arsenal_beacon:reinforced_plating = 2
        arsenal_beacon:logistics_module = 2
        arsenal_beacon:resonance_coil = 3
        arsenal_beacon:restoration_matrix = 4
        create:precision_mechanism = 3
        create:brass_ingot x8 = 3
        create:andesite_alloy x16 = 2
        kubejs:cartridge_case x16 = 2
        kubejs:propellant x16 = 2
        """;
    /** Standalone edition: rare vanilla materials. */
    static final String STANDALONE="""
        [sell]
        minecraft:diamond = 2
        minecraft:blaze_rod = 1
        minecraft:ender_pearl = 1
        minecraft:ghast_tear = 2
        minecraft:netherite_scrap = 3
        minecraft:shulker_shell = 4
        minecraft:nether_star = 20
        minecraft:emerald x2 = 1
        minecraft:gold_ingot x8 = 1
        minecraft:quartz x16 = 1

        [buy]
        arsenal_beacon:universal_ammo_coin x16 = 3
        minecraft:iron_ingot x16 = 1
        minecraft:copper_ingot x16 = 1
        minecraft:gunpowder x16 = 1
        minecraft:redstone x16 = 1
        minecraft:gold_ingot x8 = 2
        minecraft:blaze_rod = 3
        minecraft:ender_pearl = 3
        minecraft:diamond = 6
        """;
    static final List<TextTable.Row> DEFAULT_BUY=List.of(new TextTable.Row("arsenal_beacon:universal_ammo_coin",16,3));
    static String template(){return HEADER+"\n"+(BuildFlavor.STANDALONE?STANDALONE:PACK);}

    /** Splits the file's lines into its [sell] and [buy] sections (anything before a header is ignored). */
    static java.util.Map<String,List<String>> sections(List<String> lines){
        var map=new java.util.HashMap<String,List<String>>();String current="";
        for(String raw:lines){
            String line=raw.trim().toLowerCase(Locale.ROOT);
            if(line.equals("[sell]")||line.equals("[buy]")){current=line.substring(1,line.length()-1);map.computeIfAbsent(current,k->new ArrayList<>());continue;}
            if(!current.isEmpty())map.get(current).add(raw);
        }
        return map;
    }
    /** Reads the file, creating it from the template if missing. An old single-list file is kept as .old and replaced. Problems are logged, never fatal. */
    static Offers offers(){
        Path file=FMLPaths.CONFIGDIR.get().resolve(FILE);
        try{
            var lines=Files.exists(file)?Files.readAllLines(file):List.<String>of();
            if(!lines.isEmpty()&&lines.stream().noneMatch(l->l.trim().equalsIgnoreCase("[sell]")||l.trim().equalsIgnoreCase("[buy]"))){
                Files.move(file,file.resolveSibling(FILE.replace(".txt",".old.txt")),StandardCopyOption.REPLACE_EXISTING);lines=List.of();
                com.mojang.logging.LogUtils.getLogger().warn("{} used the old single-list format; it was kept as an .old file and replaced with buy and sell sections",FILE);
            }
            if(lines.isEmpty()){Files.writeString(file,template());lines=Files.readAllLines(file);}
            var parts=sections(lines);java.util.function.Predicate<String> exists=id->{var rl=ResourceLocation.tryParse(id);return rl!=null&&BuiltInRegistries.ITEM.containsKey(rl)&&BuiltInRegistries.ITEM.get(rl)!=net.minecraft.world.item.Items.AIR;};
            var buy=TextTable.parse(parts.getOrDefault("buy",List.of()),exists,64,9999);var sell=TextTable.parse(parts.getOrDefault("sell",List.of()),exists,64,9999);
            for(String problem:buy.problems())com.mojang.logging.LogUtils.getLogger().warn("{} [buy]: {}",FILE,problem);
            for(String problem:sell.problems())com.mojang.logging.LogUtils.getLogger().warn("{} [sell]: {}",FILE,problem);
            return new Offers(buy.rows().isEmpty()?DEFAULT_BUY:buy.rows(),sell.rows());
        }catch(IOException ex){com.mojang.logging.LogUtils.getLogger().warn("Cannot read {}",FILE,ex);return new Offers(DEFAULT_BUY,List.of());}
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

    private static ListTag rows(List<TextTable.Row> list,boolean sell){
        var out=new ListTag();
        for(var offer:list){
            var item=BuiltInRegistries.ITEM.get(new ResourceLocation(offer.id()));var row=new CompoundTag();
            row.put("item",new ItemStack(item,Math.min(offer.count(),item.getMaxStackSize())).save(new CompoundTag()));row.putInt("cost",offer.value());out.add(row);
        }
        return out;
    }
    static void send(ServerPlayer p,BlockPos pos,String message){
        var data=new CompoundTag();data.putInt("energy",ArdentEnergy.balance(p));data.putBoolean("creative",p.isCreative());
        var all=offers();data.put("buy",rows(all.buy(),false));data.put("sell",rows(all.sell(),true));
        var have=new ListTag();   // how many of each item to sell the player is carrying
        for(var offer:all.sell()){var c=new CompoundTag();c.putInt("have",Economy.have(p,BuiltInRegistries.ITEM.get(new ResourceLocation(offer.id()))));have.add(c);}
        data.put("have",have);
        BeaconNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(()->p),new Open(pos,data,message));
    }
    static void buy(ServerPlayer p,BlockPos pos,int index,boolean sell){
        if(!p.level().hasChunkAt(pos)||p.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)>64||!(p.level().getBlockState(pos).getBlock() instanceof ShopBlock)||BaseZone.problem(p.level(),pos)!=null)return;
        var all=offers();var list=sell?all.sell():all.buy();
        if(index<0||index>=list.size()){send(p,pos,"That offer no longer exists.");return;}
        var offer=list.get(index);var item=BuiltInRegistries.ITEM.get(new ResourceLocation(offer.id()));
        var stack=new ItemStack(item,Math.min(offer.count(),item.getMaxStackSize()));
        String result;
        if(sell){
            var costs=List.of(new WeaponPlatform.Cost(Ingredient.of(item),stack.getCount()));
            result=InventoryPayment.commit(p,costs,new ItemStack(ArsenalBeacon.ARDENT_ENERGY.get(),offer.value()))?"Sold "+stack.getCount()+" x "+stack.getHoverName().getString()+" for "+offer.value()+" Ardent Energy.":"You need "+stack.getCount()+" x "+stack.getHoverName().getString()+" (and room for the energy). Nothing taken.";
        }else{
            var costs=List.of(new WeaponPlatform.Cost(Ingredient.of(ArsenalBeacon.ARDENT_ENERGY.get()),offer.value()));
            result=InventoryPayment.commit(p,costs,stack)?"Bought "+stack.getCount()+" x "+stack.getHoverName().getString()+(p.isCreative()?" (free in Creative).":" for "+offer.value()+" Ardent Energy."):InventoryPayment.failure(p,costs);
        }
        send(p,pos,result);
    }

    record Open(BlockPos pos,CompoundTag data,String message){
        static void encode(Open p,FriendlyByteBuf b){b.writeBlockPos(p.pos);b.writeNbt(p.data);b.writeUtf(p.message,256);}
        static Open decode(FriendlyByteBuf b){var pos=b.readBlockPos();var n=b.readNbt();return new Open(pos,n==null?new CompoundTag():n,b.readUtf(256));}
        static void handle(Open p,Supplier<NetworkEvent.Context> ctx){ctx.get().enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->ExchangeScreen.receive(p)));ctx.get().setPacketHandled(true);}
    }
    record Buy(BlockPos pos,int index,boolean sell){
        static void encode(Buy p,FriendlyByteBuf b){b.writeBlockPos(p.pos);b.writeVarInt(p.index);b.writeBoolean(p.sell);}
        static Buy decode(FriendlyByteBuf b){return new Buy(b.readBlockPos(),b.readVarInt(),b.readBoolean());}
        static void handle(Buy p,Supplier<NetworkEvent.Context> ctx){var c=ctx.get();c.enqueueWork(()->{if(c.getSender()!=null)buy(c.getSender(),p.pos,p.index,p.sell);});c.setPacketHandled(true);}
    }
}
