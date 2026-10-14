package dev.eternaldefense.beacon;

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

/** Buy items with Ardent Energy and sell items for it. Offers live in config/eternal-defense-exchange.txt ([sell] and [buy] sections) and reload each time the shop opens. */
final class ExchangeShop {
    private ExchangeShop(){}
    static final String FILE="eternal-defense-exchange.txt";
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
    /**
     * The default: sell the surplus of a factory, buy parts at a premium when farming instead; since 0.0.21 also rare vanilla materials (gold,
     * blaze rods, ender pearls, diamonds) and iron, plus iron in bulk. 0.0.41 (owner): a blaze rod costs 6 (was 3).
     * 0.0.43 (owner): iron costs twice as much and comes as ore (16 Iron Ore for 2, was 16 ingots for 1: smelt it, or crush it with Create
     * for more), the bulk lot is 8 iron blocks for 8 (was 16 for 8), and 16 andesite alloy cost 1 (was 2).
     */
    static final String PACK="""
        [sell]
        eternal_defense:warlord_seal = 4
        eternal_defense:reinforced_plating x2 = 1
        eternal_defense:logistics_module x2 = 1
        eternal_defense:resonance_coil x2 = 1
        eternal_defense:restoration_matrix = 1
        create:brass_ingot x16 = 1
        create:andesite_alloy x32 = 1
        create:precision_mechanism x2 = 1
        create:electron_tube x8 = 1
        kubejs:cartridge_case x32 = 1
        kubejs:propellant x32 = 1

        [buy]
        eternal_defense:universal_ammo_coin x16 = 3
        eternal_defense:reinforced_plating = 2
        eternal_defense:logistics_module = 2
        eternal_defense:resonance_coil = 3
        eternal_defense:restoration_matrix = 4
        create:precision_mechanism = 3
        create:brass_ingot x8 = 3
        create:andesite_alloy x16 = 1
        kubejs:cartridge_case x16 = 2
        kubejs:propellant x16 = 2
        minecraft:iron_ore x16 = 2
        minecraft:iron_block x8 = 8
        minecraft:gold_ingot x8 = 2
        minecraft:blaze_rod = 6
        minecraft:ender_pearl = 3
        minecraft:diamond = 6
        """;
    /** The default of 0.0.41 and 0.0.42 (16 iron ingots for 1, 16 iron blocks for 8, 16 andesite alloy for 2): an untouched file gets the new default. */
    static final String PACK_0041="""
        [sell]
        eternal_defense:warlord_seal = 4
        eternal_defense:reinforced_plating x2 = 1
        eternal_defense:logistics_module x2 = 1
        eternal_defense:resonance_coil x2 = 1
        eternal_defense:restoration_matrix = 1
        create:brass_ingot x16 = 1
        create:andesite_alloy x32 = 1
        create:precision_mechanism x2 = 1
        create:electron_tube x8 = 1
        kubejs:cartridge_case x32 = 1
        kubejs:propellant x32 = 1

        [buy]
        eternal_defense:universal_ammo_coin x16 = 3
        eternal_defense:reinforced_plating = 2
        eternal_defense:logistics_module = 2
        eternal_defense:resonance_coil = 3
        eternal_defense:restoration_matrix = 4
        create:precision_mechanism = 3
        create:brass_ingot x8 = 3
        create:andesite_alloy x16 = 2
        kubejs:cartridge_case x16 = 2
        kubejs:propellant x16 = 2
        minecraft:iron_ingot x16 = 1
        minecraft:iron_block x16 = 8
        minecraft:gold_ingot x8 = 2
        minecraft:blaze_rod = 6
        minecraft:ender_pearl = 3
        minecraft:diamond = 6
        """;
    /** The pack edition's default from 0.0.28 to 0.0.40 (a blaze rod for 3): an untouched file gets the new default. */
    static final String PACK_0028="""
        [sell]
        eternal_defense:warlord_seal = 4
        eternal_defense:reinforced_plating x2 = 1
        eternal_defense:logistics_module x2 = 1
        eternal_defense:resonance_coil x2 = 1
        eternal_defense:restoration_matrix = 1
        create:brass_ingot x16 = 1
        create:andesite_alloy x32 = 1
        create:precision_mechanism x2 = 1
        create:electron_tube x8 = 1
        kubejs:cartridge_case x32 = 1
        kubejs:propellant x32 = 1

        [buy]
        eternal_defense:universal_ammo_coin x16 = 3
        eternal_defense:reinforced_plating = 2
        eternal_defense:logistics_module = 2
        eternal_defense:resonance_coil = 3
        eternal_defense:restoration_matrix = 4
        create:precision_mechanism = 3
        create:brass_ingot x8 = 3
        create:andesite_alloy x16 = 2
        kubejs:cartridge_case x16 = 2
        kubejs:propellant x16 = 2
        minecraft:iron_ingot x16 = 1
        minecraft:iron_block x16 = 8
        minecraft:gold_ingot x8 = 2
        minecraft:blaze_rod = 3
        minecraft:ender_pearl = 3
        minecraft:diamond = 6
        """;
    /** The pack edition's default from 0.0.21 to 0.0.27 (before the Warlord Seal could be sold): an untouched file gets the new default. */
    static final String PACK_0021="""
        [sell]
        eternal_defense:reinforced_plating x2 = 1
        eternal_defense:logistics_module x2 = 1
        eternal_defense:resonance_coil x2 = 1
        eternal_defense:restoration_matrix = 1
        create:brass_ingot x16 = 1
        create:andesite_alloy x32 = 1
        create:precision_mechanism x2 = 1
        create:electron_tube x8 = 1
        kubejs:cartridge_case x32 = 1
        kubejs:propellant x32 = 1

        [buy]
        eternal_defense:universal_ammo_coin x16 = 3
        eternal_defense:reinforced_plating = 2
        eternal_defense:logistics_module = 2
        eternal_defense:resonance_coil = 3
        eternal_defense:restoration_matrix = 4
        create:precision_mechanism = 3
        create:brass_ingot x8 = 3
        create:andesite_alloy x16 = 2
        kubejs:cartridge_case x16 = 2
        kubejs:propellant x16 = 2
        minecraft:iron_ingot x16 = 1
        minecraft:iron_block x16 = 8
        minecraft:gold_ingot x8 = 2
        minecraft:blaze_rod = 3
        minecraft:ender_pearl = 3
        minecraft:diamond = 6
        """;
    /** The pack edition's default before 0.0.21: a world whose file is still exactly this gets the new default (an edited file is kept). */
    static final String PACK_BEFORE_0021="""
        [sell]
        eternal_defense:reinforced_plating x2 = 1
        eternal_defense:logistics_module x2 = 1
        eternal_defense:resonance_coil x2 = 1
        eternal_defense:restoration_matrix = 1
        create:brass_ingot x16 = 1
        create:andesite_alloy x32 = 1
        create:precision_mechanism x2 = 1
        create:electron_tube x8 = 1
        kubejs:cartridge_case x32 = 1
        kubejs:propellant x32 = 1

        [buy]
        eternal_defense:universal_ammo_coin x16 = 3
        eternal_defense:reinforced_plating = 2
        eternal_defense:logistics_module = 2
        eternal_defense:resonance_coil = 3
        eternal_defense:restoration_matrix = 4
        create:precision_mechanism = 3
        create:brass_ingot x8 = 3
        create:andesite_alloy x16 = 2
        kubejs:cartridge_case x16 = 2
        kubejs:propellant x16 = 2
        """;
    static final List<TextTable.Row> DEFAULT_BUY=List.of(new TextTable.Row("eternal_defense:universal_ammo_coin",16,3));
    static String template(){return HEADER+"\n"+PACK;}
    /** Pure: the file's text with each line's trailing spaces and the blank lines at its end dropped (to compare a file with a template). */
    static String normal(String text){return text.lines().map(String::stripTrailing).collect(java.util.stream.Collectors.joining("\n")).strip();}
    /** Pure: is this file the untouched default of an earlier version (so it may be replaced by the current one)? */
    static boolean oldDefault(String text){
        for(String old:List.of(PACK_BEFORE_0021,PACK_0021,PACK_0028,PACK_0041))if(normal(text).equals(normal(HEADER+"\n"+old)))return true;
        return false;
    }

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
            if(!lines.isEmpty()&&oldDefault(String.join("\n",lines))){
                lines=List.of();com.mojang.logging.LogUtils.getLogger().info("{} was the untouched default of an earlier version; it now has the 0.0.43 default (16 Iron Ore for 2 Ardent Energy, 8 iron blocks for 8, 16 andesite alloy for 1)",FILE);
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
        @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return DefenseStructures.cell(s,0,0,0);}
        @Override public PushReaction getPistonPushReaction(BlockState s){return PushReaction.NORMAL;}
        @Override public void onPlace(BlockState s,Level l,BlockPos pos,BlockState old,boolean moving){super.onPlace(s,l,pos,old,moving);if(l instanceof net.minecraft.server.level.ServerLevel)DefenseStructures.install(l,pos,s);}
        @Override public void tick(BlockState s,net.minecraft.server.level.ServerLevel l,BlockPos pos,net.minecraft.util.RandomSource random){DefenseStructures.install(l,pos,s);}
        @Override public void onRemove(BlockState s,Level l,BlockPos pos,BlockState next,boolean moving){if(!s.is(next.getBlock()))DefenseStructures.remove(l,pos);super.onRemove(s,l,pos,next,moving);}
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
        var w=Supply.at(p,pos);var data=new CompoundTag();data.putInt("energy",ArdentEnergy.balance(w));data.putBoolean("creative",p.isCreative());
        var all=offers();data.put("buy",rows(all.buy(),false));data.put("sell",rows(all.sell(),true));
        var have=new ListTag();   // how many of each item to sell the player is carrying
        for(var offer:all.sell()){var c=new CompoundTag();c.putInt("have",Economy.have(w,BuiltInRegistries.ITEM.get(new ResourceLocation(offer.id()))));have.add(c);}
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
            result=InventoryPayment.commit(Supply.at(p,pos),costs,new ItemStack(EternalDefense.ARDENT_ENERGY.get(),offer.value()))?"Sold "+stack.getCount()+" x "+stack.getHoverName().getString()+" for "+offer.value()+" Ardent Energy.":"You need "+stack.getCount()+" x "+stack.getHoverName().getString()+" (and room for the energy). Nothing taken.";
        }else{
            var costs=List.of(new WeaponPlatform.Cost(Ingredient.of(EternalDefense.ARDENT_ENERGY.get()),offer.value()));
            result=InventoryPayment.commit(Supply.at(p,pos),costs,stack)?"Bought "+stack.getCount()+" x "+stack.getHoverName().getString()+(p.isCreative()?" (free in Creative).":" for "+offer.value()+" Ardent Energy."):InventoryPayment.failure(Supply.at(p,pos),costs);
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
