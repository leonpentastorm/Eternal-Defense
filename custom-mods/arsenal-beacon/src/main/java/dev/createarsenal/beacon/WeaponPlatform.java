package dev.createarsenal.beacon;

import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Supplier;

/** Independently upgraded, bounded workstations. Recipes, upgrades and inventory mutations are server-owned. */
public final class WeaponPlatform {
    static final IntegerProperty AGE=IntegerProperty.create("age",1,5);
    static final String[] AGES={"","Frontier","World Wars","Modern","Advanced","Exotic"};
    static final int PAGE_SIZE=WeaponBrowser.DEFAULT_SIZE;
    private static final Map<UUID,Session> sessions=new HashMap<>();
    private static Map<String,Gate> catalogue;
    record Gate(int age,String kind,String id,String name){Gate(int age,String kind,String id){this(age,kind,id,id);}}
    record Session(ResourceLocation dimension,BlockPos pos){}
    record Cost(Ingredient ingredient,int count){}
    record Entry(ResourceLocation recipeId,Object recipe,Gate gate,ItemStack output){}
    static final class StationItem extends BlockItem{
        StationItem(Block block){super(block,new Item.Properties());}
        @Override public InteractionResult place(net.minecraft.world.item.context.BlockPlaceContext context){
            var result=super.place(context);if(result.consumesAction()&&context.getLevel() instanceof net.minecraft.server.level.ServerLevel level)PlatformRegistry.get(level).track(context.getClickedPos(),level.getBlockState(context.getClickedPos()));return result;
        }
        @Override public void appendHoverText(ItemStack stack,Level level,List<Component> tooltip,TooltipFlag flag){
            super.appendHoverText(stack,level,tooltip,flag);int age=1;
            if(stack.hasTag())try{age=Integer.parseInt(stack.getTag().getCompound("BlockStateTag").getString("age"));}catch(NumberFormatException ignored){}
            age=Math.max(1,Math.min(5,age));tooltip.add(Component.literal("Age "+age+": "+AGES[age]));tooltip.add(Component.literal("Mining and replacing preserves its Age."));
        }
    }
    public static final class StationEntity extends net.minecraft.world.level.block.entity.BlockEntity {
        public StationEntity(BlockPos pos,BlockState state){super(ArsenalBeacon.STATION_ENTITY.get(),pos,state);}
        @Override public net.minecraft.world.phys.AABB getRenderBoundingBox(){return new net.minecraft.world.phys.AABB(worldPosition).inflate(2);}
    }
    public static final class Station extends Block implements net.minecraft.world.level.block.EntityBlock {
        final String kind;
        
        Station(String kind){super(Properties.of().strength(3,6).noOcclusion());this.kind=kind;registerDefaultState(stateDefinition.any().setValue(AGE,1).setValue(ArsenalStructures.FACING,net.minecraft.core.Direction.NORTH).setValue(ArsenalStructures.WIDE,false).setValue(ArsenalStructures.TALL,false));}
        @Override public net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,net.minecraft.world.phys.shapes.CollisionContext context){return ArsenalStructures.cell(state,0,0,0);}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(AGE,ArsenalStructures.FACING,ArsenalStructures.WIDE,ArsenalStructures.TALL);}
        @Override public net.minecraft.world.level.block.RenderShape getRenderShape(BlockState s){return net.minecraft.world.level.block.RenderShape.MODEL;}
        @Override public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos,BlockState s){return new StationEntity(pos,s);}
        @Override public net.minecraft.world.level.material.PushReaction getPistonPushReaction(BlockState s){return net.minecraft.world.level.material.PushReaction.BLOCK;}
        @Override public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext c){var s=defaultBlockState().setValue(ArsenalStructures.FACING,c.getHorizontalDirection().getOpposite()).setValue(ArsenalStructures.WIDE,true).setValue(ArsenalStructures.TALL,true);return ArsenalStructures.available(c.getLevel(),c.getClickedPos(),s)?s:null;}
        @Override public void tick(BlockState s,net.minecraft.server.level.ServerLevel l,BlockPos pos,net.minecraft.util.RandomSource random){ArsenalStructures.install(l,pos,s);}
        @Override public void onPlace(BlockState s,Level l,BlockPos pos,BlockState old,boolean moving){super.onPlace(s,l,pos,old,moving);if(l instanceof net.minecraft.server.level.ServerLevel server){PlatformRegistry.get(server).track(pos,s);ArsenalStructures.install(l,pos,s);}}
        @Override public void onRemove(BlockState s,Level l,BlockPos pos,BlockState next,boolean moving){if(!next.is(this))ArsenalStructures.remove(l,pos);super.onRemove(s,l,pos,next,moving);if(l instanceof net.minecraft.server.level.ServerLevel server)PlatformRegistry.get(server).track(pos,next);}
        @Override public InteractionResult use(BlockState s,Level l,BlockPos pos,Player p,InteractionHand hand,BlockHitResult hit){
            if(p instanceof ServerPlayer server){open(server,pos);}
            return InteractionResult.sidedSuccess(l.isClientSide);
        }
    }
    public static void open(ServerPlayer p,BlockPos pos){
        pos=ArsenalStructures.anchor(p.level(),pos);
        if(!p.level().hasChunkAt(pos)||p.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)>64||!(p.level().getBlockState(pos).getBlock() instanceof Station))return;
        sessions.put(p.getUUID(),new Session(p.level().dimension().location(),pos.immutable()));send(p,new Request("browse","",0,""),true,p.level().getBlockState(pos).getValue(ArsenalStructures.TALL)?"":"Legacy bench: clear the two-wide, two-tall installation or mine and replace it. Its Age is preserved.");
    }
    static boolean heldGun(ServerPlayer p){
        ItemStack gun=p.getMainHandItem();
        try{return !gun.isEmpty()&&gun.getItem().getClass().getMethod("getGunId",ItemStack.class).invoke(gun.getItem(),gun)!=null&&validIndex(gun);}
        catch(ReflectiveOperationException ex){return false;}
    }
    static boolean compatible(ServerPlayer p,Entry entry){
        if(!heldGun(p))return p.isCreative();
        try{
            ItemStack gun=p.getMainHandItem();
            if(MagazineBridge.isMagazine(entry.output))return MagazineBridge.compatible(gun,entry.output);
            if(entry.gate.kind.equals("ammo"))return (boolean)entry.output.getItem().getClass().getMethod("isAmmoOfGun",ItemStack.class,ItemStack.class).invoke(entry.output.getItem(),gun,entry.output);
            return (boolean)gun.getItem().getClass().getMethod("allowAttachment",ItemStack.class,ItemStack.class).invoke(gun.getItem(),gun,entry.output);
        }catch(ReflectiveOperationException ex){return false;}
    }
    static List<Entry> weaponEntries(ServerPlayer p,String query,boolean supplies){
        if(!supplies)return entries(p,"gun",query).stream().filter(e->!CreateUnlocks.armory(e)).toList();
        var result=new ArrayList<>(entries(p,"attachment",query).stream().filter(CreateUnlocks::supply).toList());result.addAll(DisplayRacks.entries(query));result.addAll(ArmorPlatform.supplies(query));return result;
    }
    static List<Entry> specialEntries(ServerPlayer p,String query,int category){
        if(category==9)return weaponEntries(p,query,true);
        if(category==7)return entries(p,"gun",query).stream().filter(CreateUnlocks::armory).toList();
        var item=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(new ResourceLocation("tacz_turrets:turret"));
        var output=new ItemStack(item);if(output.isEmpty()||!query.isEmpty()&&!output.getHoverName().getString().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))return List.of();
        return List.of(new Entry(new ResourceLocation(ArsenalBeacon.ID,"turret"),null,new Gate(0,"turret","tacz_turrets:turret"),output));
    }
    static List<Cost> purchaseCosts(String kind){return Economy.stationCosts(kind);}
    static String purchase(ServerPlayer p,String kind){
        if(kind.equals("support")||kind.equals("exchange")){
            ItemStack gear=new ItemStack(kind.equals("support")?ArsenalBeacon.SUPPORT_PLATFORM_ITEM.get():ArsenalBeacon.EXCHANGE_SHOP_ITEM.get());
            return transact(p,purchaseCosts(kind),gear)?"Purchased "+(kind.equals("support")?"Support Platform":"Exchange Shop")+". Place it inside your Defense Beacon zone.":"Missing materials or inventory space. Nothing consumed.";
        }
        if(!kind.equals("ammo")&&!kind.equals("attachment")&&!kind.equals("armor"))return "Unknown station.";
        ItemStack output=new ItemStack(ArmorPlatform.station(kind).get());
        return transact(p,purchaseCosts(kind),output)?"Purchased "+kind+" platform. Place within 8 blocks of this weapon table.":"Missing materials or inventory space. Nothing consumed.";
    }
    static String manageUpgrade(ServerPlayer p,BlockPos weapon,BlockPos target){
        if(!(p.level().getBlockState(weapon).getBlock() instanceof Station source)||!source.kind.equals("gun"))return "Manage Ages at the weapon table.";
        if(!p.level().hasChunkAt(target)||target.distSqr(weapon)>64||p.distanceToSqr(target.getX()+.5,target.getY()+.5,target.getZ()+.5)>64)return "Place the station within 8 blocks of you and the weapon table.";
        if(!(p.level().getBlockState(target).getBlock() instanceof Station child)||child.kind.equals("gun"))return "Select a nearby ammo, attachment or armor station.";
        return upgrade(p,target);
    }
    private static Map<String,Gate> catalogue(){
        if(catalogue!=null)return catalogue;
        Map<String,Gate> parsed=new HashMap<>();
        try(var in=WeaponPlatform.class.getResourceAsStream("/data/arsenal_beacon/platform_catalogue.json")){
            var root=JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(in),StandardCharsets.UTF_8)).getAsJsonObject();
            root.entrySet().forEach(e->{var n=e.getValue().getAsJsonObject();parsed.put(e.getKey(),new Gate(n.get("age").getAsInt(),n.get("kind").getAsString(),n.get("id").getAsString(),n.has("name")?n.get("name").getAsString():n.get("id").getAsString()));});
        }catch(Exception e){throw new IllegalStateException("Weapon platform catalogue could not load",e);}
        return catalogue=Map.copyOf(parsed);
    }
    static Gate gate(String recipeId){return catalogue().getOrDefault(recipeId,new Gate(5,"gun",recipeId));}
    private static Map<String,Gate> indexedGuns;
    static Gate indexedGun(String id){
        if(indexedGuns==null){Map<String,Gate> guns=new HashMap<>();for(var g:catalogue().values())if(g.kind.equals("gun"))guns.merge(g.id,g,(a,b)->a.age<=b.age?a:b);indexedGuns=Map.copyOf(guns);}
        return indexedGuns.getOrDefault(id,new Gate(5,"gun",id));
    }
    static List<Entry> entries(ServerPlayer p,String kind,String query){
        if(BuildFlavor.STANDALONE&&!kind.equals("armor"))return StandaloneBalance.entries(p,kind,query);
        if(kind.equals("armor"))return ArmorPlatform.entries(p,query);
        String filter=query.toLowerCase(Locale.ROOT);List<Entry> list=new ArrayList<>();
        for(var recipe:p.level().getRecipeManager().getRecipes()){
            if(!recipe.getClass().getName().equals("com.tacz.guns.crafting.GunSmithTableRecipe"))continue;
            var id=recipe.getId();
            try{
                ItemStack output=(ItemStack)recipe.getClass().getMethod("getOutput").invoke(recipe);
                if(output.isEmpty()||!validIndex(output)||!WeaponCompatibility.craftable(output))continue;
                Gate gate=MagazineBridge.isMagazine(output)?MagazineBridge.gate(output):catalogue().get(id.toString());
                if(gate==null){var tag=output.getOrCreateTag();String type=tag.contains("GunId")?"gun":tag.contains("AmmoId")?"ammo":"attachment";
                    String indexed=tag.getString(type.equals("gun")?"GunId":type.equals("ammo")?"AmmoId":"AttachmentId");gate=new Gate(5,type,indexed.isEmpty()?id.toString():indexed);}
                if(!gate.kind.equals(kind))continue;
                list.add(new Entry(id,recipe,gate,output));
            }catch(ReflectiveOperationException e){com.mojang.logging.LogUtils.getLogger().error("Platform could not read recipe {}",id,e);}
        }
        if(kind.equals("ammo"))list=AmmoCatalogue.canonical(list);
        if(!filter.isEmpty())list=list.stream().filter(e->e.gate.name.toLowerCase(Locale.ROOT).contains(filter)||e.output.getHoverName().getString().toLowerCase(Locale.ROOT).contains(filter)||e.gate.id.toLowerCase(Locale.ROOT).contains(filter)||e.recipeId.toString().toLowerCase(Locale.ROOT).contains(filter)).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        list.sort(Comparator.comparingInt((Entry e)->e.gate.age).thenComparing(e->e.gate.id).thenComparing(e->e.recipeId.toString()));return list;
    }
    static boolean validIndex(ItemStack output){
        if(MagazineBridge.isMagazine(output))return MagazineBridge.valid(output); // Empty magazines have no AmmoId until loaded.
        // Broken optional author definitions are excluded; never hand out unusable indexed items.
        for(String kind:List.of("Gun","Ammo","Attachment"))try{
            var method=output.getItem().getClass().getMethod("get"+kind+"Id",ItemStack.class);
            ResourceLocation id=(ResourceLocation)method.invoke(output.getItem(),output);
            var api=Class.forName("com.tacz.guns.api.TimelessAPI");
            return ((Optional<?>)api.getMethod("getCommon"+kind+"Index",ResourceLocation.class).invoke(null,id)).isPresent();
        }catch(NoSuchMethodException ignored){}catch(ReflectiveOperationException ex){return false;}
        return true; // Native tactical supplies have their own item implementation.
    }
    static List<Cost> upgrades(int age){if(BuildFlavor.STANDALONE)return StandaloneBalance.upgrades(age);return switch(age){
        case 1->List.of(cost("minecraft:iron_ingot",64),cost("create:andesite_alloy",32),cost("create:cogwheel",16),cost("kubejs:pressed_receiver",16));
        case 2->List.of(cost("kubejs:hardened_receiver",16),cost("create:precision_mechanism",32),cost("minecraft:blaze_rod",8),cost("create:brass_sheet",32));
        case 3->List.of(cost("kubejs:hardened_receiver",64),cost("create:precision_mechanism",64),cost("minecraft:netherite_scrap",16),cost("arsenal_beacon:resonance_coil",16));
        case 4->List.of(cost("kubejs:exotic_receiver",32),cost("minecraft:end_rod",32),cost("minecraft:nether_star",1),cost("arsenal_beacon:restoration_matrix",16));
        default->List.of();};}
    static Cost cost(String id,int amount){if(BuildFlavor.STANDALONE)id=StandaloneBalance.material(id);return new Cost(Ingredient.of(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(new ResourceLocation(id))),amount);}
    static List<Cost> costs(Entry e)throws ReflectiveOperationException{
        if(e.recipe instanceof ArmorPlatform.Fabrication)return ArmorPlatform.costs(e);
        if(BuildFlavor.STANDALONE)return StandaloneBalance.costs(e);
        if(DisplayRacks.entry(e))return DisplayRacks.costs(e);
        if(MagazineBridge.isMagazine(e.output)){List<Cost> materials=new ArrayList<>();for(Object input:(List<?>)e.recipe.getClass().getMethod("getInputs").invoke(e.recipe))materials.add(new Cost(UniversalWood.normalize((Ingredient)input.getClass().getMethod("getIngredient").invoke(input)),(int)input.getClass().getMethod("getCount").invoke(input)));addUnlessPresent(materials,cost("create:iron_sheet",2));return InventoryPayment.consolidate(materials);}
        if(CreateUnlocks.turret(e))return List.of(cost("arsenal_beacon:reinforced_plating",4),cost("create:brass_sheet",2),cost("create:precision_mechanism",1));
        List<Cost> costs=new ArrayList<>();
        for(Object input:(List<?>)e.recipe.getClass().getMethod("getInputs").invoke(e.recipe)){
            Cost cost=new Cost(UniversalWood.normalize((Ingredient)input.getClass().getMethod("getIngredient").invoke(input)),(int)input.getClass().getMethod("getCount").invoke(input));
            if(!CreateUnlocks.armory(e)&&e.gate.age<=2&&Arrays.stream(cost.ingredient.getItems()).anyMatch(stack->{String id=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();return id.contains("nether")||id.contains("blaze")||id.contains("ender")||id.contains("brass")||id.contains("precision_mechanism")||id.contains("hardened")||id.contains("exotic");}))cost=cost("create:iron_sheet",cost.count);
            costs.add(cost);
        }
        costs=InventoryPayment.consolidate(costs);
        if(CreateUnlocks.armory(e))return costs;
        // Also enforce factory inputs on servers without KubeJS (integration tests, future hosts).
        String id=switch(e.gate.age){case 1,2->"kubejs:pressed_receiver";case 3,4->"kubejs:hardened_receiver";default->"kubejs:exotic_receiver";};
        if(e.gate.kind.equals("gun"))addUnlessPresent(costs,cost(id,e.gate.age==2?2:1));
        else if(e.gate.kind.equals("ammo")){
            addUnlessPresent(costs,cost("kubejs:cartridge_case",Math.max(1,(e.output.getCount()+7)/8)));
            addUnlessPresent(costs,cost("kubejs:propellant",Math.max(1,(e.output.getCount()+15)/16)));
        }else addUnlessPresent(costs,cost("create:iron_sheet",2));
        return InventoryPayment.consolidate(costs);
    }
    private static void addUnlessPresent(List<Cost> list,Cost cost){if(list.stream().noneMatch(c->Arrays.stream(cost.ingredient.getItems()).anyMatch(c.ingredient::test)))list.add(cost);}
    /** Plan on copies, including output capacity; commit once, never consume partial inputs. */
    static boolean transact(ServerPlayer p,List<Cost> costs,ItemStack output){
        return InventoryPayment.commit(p,costs,output);
    }
    static String upgrade(ServerPlayer p,BlockPos pos){
        var state=p.level().getBlockState(pos);if(!(state.getBlock() instanceof Station))return "Station missing.";
        CampaignData d=CampaignData.get(p.server.overworld());if(p.level().dimension()==Level.OVERWORLD&&d.active()&&d.inside(pos))return "Upgrade after the raid and repairs finish.";
        int age=state.getValue(AGE);if(age>=5)return "Maximum Age reached.";
        if(age==2&&!p.isCreative()&&!visitedNether(p))return "Your team must visit the Nether before Modern unlocks.";
        if(!transact(p,upgrades(age),ItemStack.EMPTY))return "Carry all required materials to upgrade.";
        var upgraded=state.setValue(AGE,age+1);p.level().setBlock(pos,upgraded,3);PlatformRegistry.get(p.serverLevel()).track(pos,upgraded);return "Upgraded to Age "+(age+1)+": "+AGES[age+1]+".";
    }
    static String craft(ServerPlayer p,BlockPos pos,ResourceLocation id){
        var state=p.level().getBlockState(pos);if(!(state.getBlock() instanceof Station station))return "Station missing.";
        var available=new ArrayList<>(entries(p,station.kind,""));if(station.kind.equals("gun")){available.addAll(weaponEntries(p,"",true));available.addAll(specialEntries(p,"",8));}
        var entry=available.stream().filter(e->e.recipeId.equals(id)).findFirst().orElse(null);
        if(entry==null||!WeaponCompatibility.craftable(entry.output))return "Recipe unavailable. Try refreshing the page.";
        if(ArmorPlatform.component(station.kind)&&!compatible(p,entry))return "Equip a compatible gun. This component does not fit your current weapon.";
        if(!CreateUnlocks.unlocked(p,entry,state.getValue(AGE)))return CreateUnlocks.armory(entry)||CreateUnlocks.turret(entry)?CreateUnlocks.requirement(CreateUnlocks.turret(entry)?2:CreateUnlocks.required(entry))+" to unlock this category.":"Requires Age "+entry.gate.age+": "+AGES[entry.gate.age]+".";
        try{var payment=costs(entry);return transact(p,payment,entry.output)?"Crafted "+entry.output.getCount()+" item(s).":InventoryPayment.failure(p,payment);}
        catch(ReflectiveOperationException ex){com.mojang.logging.LogUtils.getLogger().error("Platform recipe failed: {}",id,ex);return "Recipe could not load; nothing consumed.";}
    }
    @SubscribeEvent public void nativeMenu(PlayerContainerEvent.Open event){
        // Native table packets require this menu. Closing it server-side seals the Age bypass.
        if(event.getEntity() instanceof ServerPlayer p&&event.getContainer().getClass().getName().equals("com.tacz.guns.inventory.GunSmithTableMenu")){
            p.closeContainer();p.sendSystemMessage(Component.literal("Use a Universal Gun, Ammo or Attachment Platform. Their Ages unlock all packs."));
        }
        if(!BuildFlavor.STANDALONE&&event.getEntity() instanceof ServerPlayer p&&Set.of("omegarecon.world.inventory.TestGuiMenu","net.mcreator.capsawimtacticalgearrework.world.inventory.ArmorCraftingMenu").contains(event.getContainer().getClass().getName())){
            p.closeContainer();p.sendSystemMessage(Component.literal("Craft tactical gear at the Universal Armor Platform. Buy it in the beacon's Workshop Fabrication tab and upgrade it at its own table."));
        }
    }
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent e){sessions.remove(e.getEntity().getUUID());}
    @SubscribeEvent public void changedDimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getTo()==Level.NETHER&&e.getEntity() instanceof ServerPlayer p){var data=PlatformRegistry.get(p.server.overworld());data.netherVisited=true;data.setDirty();}}
    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p)visitedNether(p);}
    static boolean visitedNether(ServerPlayer p){
        var data=PlatformRegistry.get(p.server.overworld());if(data.netherVisited)return true;
        var advancement=p.server.getAdvancements().getAdvancement(new ResourceLocation("minecraft:nether/root"));
        for(var online:p.server.getPlayerList().getPlayers())if(online.level().dimension()==Level.NETHER||advancement!=null&&online.getAdvancements().getOrStartProgress(advancement).isDone()){data.netherVisited=true;data.setDirty();return true;}
        return false;
    }
    record Request(String action,String query,int page,String recipe,int ageFilter,long target,String weaponType,int pageSize){
        Request(String action,String query,int page,String recipe){this(action,query,page,recipe,0,0,"all",PAGE_SIZE);}
        Request(String action,String query,int page,String recipe,int ageFilter,long target){this(action,query,page,recipe,ageFilter,target,"all",PAGE_SIZE);}
        static void encode(Request p,FriendlyByteBuf b){b.writeUtf(p.action,16);b.writeUtf(p.query,80);b.writeVarInt(p.page);b.writeUtf(p.recipe,180);b.writeVarInt(p.ageFilter);b.writeLong(p.target);b.writeUtf(p.weaponType,16);b.writeVarInt(p.pageSize);}
        static Request decode(FriendlyByteBuf b){return new Request(b.readUtf(16),b.readUtf(80),b.readVarInt(),b.readUtf(180),b.readVarInt(),b.readLong(),b.readUtf(16),b.readVarInt());}
        static void handle(Request p,Supplier<NetworkEvent.Context> ctx){var c=ctx.get();c.enqueueWork(()->{if(c.getSender()!=null)send(c.getSender(),p,false,"");});c.setPacketHandled(true);}
    }
    record Interact(BlockPos pos){
        static void encode(Interact p,FriendlyByteBuf b){b.writeBlockPos(p.pos);}
        static Interact decode(FriendlyByteBuf b){return new Interact(b.readBlockPos());}
        static void handle(Interact p,Supplier<NetworkEvent.Context> ctx){var c=ctx.get();c.enqueueWork(()->{if(c.getSender()!=null)open(c.getSender(),p.pos);});c.setPacketHandled(true);}
    }
    record State(CompoundTag data,boolean open,String message){
        static void encode(State p,FriendlyByteBuf b){b.writeNbt(p.data);b.writeBoolean(p.open);b.writeUtf(p.message,256);}
        static State decode(FriendlyByteBuf b){var n=b.readNbt();return new State(n==null?new CompoundTag():n,b.readBoolean(),b.readUtf(256));}
        static void handle(State p,Supplier<NetworkEvent.Context> ctx){ctx.get().enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->PlatformScreen.receive(p)));ctx.get().setPacketHandled(true);}
    }
    static void initNetwork(SimpleChannel c){
        c.registerMessage(2,Request.class,Request::encode,Request::decode,Request::handle,Optional.of(NetworkDirection.PLAY_TO_SERVER));
        c.registerMessage(3,State.class,State::encode,State::decode,State::handle,Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        c.registerMessage(4,Interact.class,Interact::encode,Interact::decode,Interact::handle,Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }
    static void request(String action,String query,int page,String recipe,int ageFilter,long target){BeaconNetwork.CHANNEL.sendToServer(new Request(action,query,page,recipe,ageFilter,target));}
    static void request(String action,String query,int page,String recipe,int ageFilter,long target,String weaponType,int pageSize){BeaconNetwork.CHANNEL.sendToServer(new Request(action,query,page,recipe,ageFilter,target,weaponType,pageSize));}
    static void request(String action,String query,int page,String recipe){BeaconNetwork.CHANNEL.sendToServer(new Request(action,query,page,recipe));}
    static void send(ServerPlayer p,Request request,boolean open,String message){
        var session=sessions.get(p.getUUID());if(session==null)return;
        if(request.action.equals("close")){sessions.remove(p.getUUID());return;}
        if(!p.level().dimension().location().equals(session.dimension)||!p.level().hasChunkAt(session.pos)||p.distanceToSqr(session.pos.getX()+.5,session.pos.getY()+.5,session.pos.getZ()+.5)>64){sessions.remove(p.getUUID());return;}
        var block=p.level().getBlockState(session.pos);if(!(block.getBlock() instanceof Station station)){sessions.remove(p.getUUID());return;}
        if(request.action.equals("upgrade"))message=upgrade(p,session.pos);
        else if(request.action.equals("craft")){var id=ResourceLocation.tryParse(request.recipe);if(id!=null)message=craft(p,session.pos,id);}
        else if(request.action.equals("buyAmmoCoins")){var id=ResourceLocation.tryParse(request.recipe);if(id!=null)message=AmmoCoins.buy(p,session.pos,id);}
        var n=pageState(p,session.pos,request);
        if(!(p instanceof net.minecraftforge.common.util.FakePlayer))BeaconNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new State(n,open,message));
    }
    static CompoundTag pageState(ServerPlayer p,BlockPos pos,Request request){
        var block=p.level().getBlockState(pos);var station=(Station)block.getBlock();var n=new CompoundTag();int age=block.getValue(AGE);
        CreateUnlocks.sync(p);n.putInt("createLevel",CreateUnlocks.level(p));
        for(String type:List.of("gun","ammo","attachment","armor")){int nearbyAge=station.kind.equals(type)?age:0;double nearest=Double.MAX_VALUE;for(BlockPos test:PlatformRegistry.get(p.serverLevel()).nearbyAll(p.serverLevel(),pos)){if(test.distSqr(pos)>64||!p.level().hasChunkAt(test))continue;var candidate=p.level().getBlockState(test);if(candidate.getBlock() instanceof Station other&&other.kind.equals(type)&&test.distSqr(pos)<nearest){nearbyAge=candidate.getValue(AGE);nearest=test.distSqr(pos);}}n.putInt(type+"Age",nearbyAge);}
        n.putBoolean("standalone",BuildFlavor.STANDALONE);n.putInt("ammoCoins",AmmoCoins.balance(p));n.putBoolean("creative",p.isCreative());n.putString("kind",station.kind);n.putInt("age",age);n.putInt("score",Rules.platformScore(age));n.putString("query",request.query);n.putBoolean("netherVisited",p.isCreative()||visitedNether(p));
        boolean armor=station.kind.equals("armor"),components=ArmorPlatform.component(station.kind);int filter=Math.max(0,Math.min(station.kind.equals("ammo")?11:components||armor?5:9,request.ageFilter));n.putInt("ageFilter",filter);
        List<Entry> compatible=entries(p,station.kind,"").stream().filter(e->!components||compatible(p,e)).toList();
        n.putInt("locked",(int)compatible.stream().filter(e->!CreateUnlocks.unlocked(p,e,age)).map(e->e.gate.id).distinct().count());
        n.put("gun",p.getMainHandItem().save(new CompoundTag()));
        if(components&&!heldGun(p)&&!p.isCreative())n.putString("notice","Equip a gun");
        else if(components&&compatible.stream().noneMatch(e->CreateUnlocks.unlocked(p,e,age)))n.putString("notice","Upgrade your "+station.kind+" table");
        List<Entry> source=components||armor?entries(p,station.kind,request.query):filter>=7?specialEntries(p,request.query,filter):weaponEntries(p,request.query,filter==6);
        List<Entry> aged=source.stream().filter(e->(!components||CreateUnlocks.unlocked(p,e,age)&&compatible(p,e))&&(filter==10?!MagazineBridge.isMagazine(e.output):filter==11?MagazineBridge.isMagazine(e.output):filter==0||filter>=6||e.gate.age==filter)).toList();
        boolean typed=armor||!components&&filter!=6&&filter!=8&&filter!=9;String type=armor?ArmorPlatform.normalize(request.weaponType):WeaponBrowser.normalize(request.weaponType);int pageSize=WeaponBrowser.size(request.pageSize);n.putString("weaponType",type);n.putBoolean("typeFilterAvailable",typed);n.putInt("pageSize",pageSize);
        var counts=new CompoundTag();counts.putInt("all",aged.size());Map<Entry,String> types=new HashMap<>();if(typed)for(var e:aged){String nativeType=armor?ArmorPlatform.type(e.output):WeaponTypes.of(e);types.put(e,nativeType);counts.putInt(nativeType,counts.getInt(nativeType)+1);}n.put("typeCounts",counts);
        List<Entry> entries=aged.stream().filter(e->!typed||type.equals("all")||type.equals(types.get(e))).toList();int page=WeaponBrowser.page(request.page,entries.size(),pageSize);
        n.putInt("page",page);n.putInt("total",entries.size());ListTag recipes=new ListTag();
        for(int i=page*pageSize;i<Math.min(entries.size(),(page+1)*pageSize);i++){
            Entry e=entries.get(i);var row=new CompoundTag();row.putString("recipe",e.recipeId.toString());row.putString("id",e.gate.id);row.putInt("age",e.gate.age);row.putBoolean("unlocked",CreateUnlocks.unlocked(p,e,age));row.putBoolean("special",CreateUnlocks.armory(e)||CreateUnlocks.turret(e)||CreateUnlocks.supply(e));row.putString("progression",CreateUnlocks.label(e));row.putString("requirement",CreateUnlocks.armory(e)||CreateUnlocks.turret(e)||CreateUnlocks.supply(e)?CreateUnlocks.requirement(CreateUnlocks.turret(e)?2:CreateUnlocks.required(e)):"Upgrade this table to "+CreateUnlocks.label(e));if(BuildFlavor.STANDALONE&&CreateUnlocks.armory(e))row.putString("requirement","Upgrade this table to Age "+e.gate.age+": "+AGES[e.gate.age]);row.put("output",e.output.copyWithCount(1).save(new CompoundTag()));row.putInt("outputCount",e.output.getCount());row.putBoolean("magazine",MagazineBridge.isMagazine(e.output));if(MagazineBridge.isMagazine(e.output))row.putInt("capacity",MagazineBridge.capacity(e.output));
            try{row.put("costs",costTags(p,costs(e)));}catch(ReflectiveOperationException ex){row.putBoolean("invalid",true);}
            if(station.kind.equals("ammo"))row.putInt("coinCost",AmmoCoins.price(p,e));recipes.add(row);
        }
        n.put("recipes",recipes);n.put("upgrades",costTags(p,upgrades(age)));
        return n;
    }
    static ListTag costTags(ServerPlayer p,List<Cost> costs){
        // Count is a signed byte in ItemStack NBT. Icons always use one item; quantities travel as ints.
        ListTag rows=new ListTag();var plan=InventoryPayment.plan(p,costs);int index=0;for(Cost cost:costs){var row=new CompoundTag();ItemStack[] choices=cost.ingredient.getItems();row.put("item",(choices.length==0?ItemStack.EMPTY:choices[0].copyWithCount(1)).save(new CompoundTag()));row.putString("label",UniversalWood.label(cost.ingredient));row.putInt("count",cost.count);row.putInt("have",p.isCreative()?cost.count:plan.allocated()[index]);index++;rows.add(row);}return rows;
    }
}
