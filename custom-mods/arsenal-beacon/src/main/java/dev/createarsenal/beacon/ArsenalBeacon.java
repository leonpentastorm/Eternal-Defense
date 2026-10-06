package dev.createarsenal.beacon;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.*;
import net.minecraft.commands.Commands;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.*;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.EntityMobGriefingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.SleepingTimeCheckEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.*;
import java.util.*;

@Mod(ArsenalBeacon.ID)
public final class ArsenalBeacon {
    public static final String ID="arsenal_beacon";
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,ID);
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,ID);
    private static final DeferredRegister<CreativeModeTab> TABS=DeferredRegister.create(Registries.CREATIVE_MODE_TAB,ID);
    public static final RegistryObject<CreativeModeTab> ARSENAL_TAB=TABS.register("arsenal",()->CreativeModeTab.builder().title(Component.literal("Create Arsenal")).icon(()->new ItemStack(ArsenalBeacon.BEACON_ITEM.get())).displayItems((parameters,output)->{ITEMS.getEntries().forEach(item->output.accept(item.get()));ArsenalBeacon.RACKS.forEach(block->output.accept(block.get()));BuiltInRegistries.ITEM.forEach(item->{if(BuiltInRegistries.ITEM.getKey(item).getNamespace().equals("kubejs"))output.accept(item);});}).build());
    private static final DeferredRegister<net.minecraft.world.level.block.entity.BlockEntityType<?>> ENTITIES=DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES,ID);
    public static final RegistryObject<Block> STRUCTURE_PART=BLOCKS.register("structure_part",ArsenalStructures.Part::new);
    public static final RegistryObject<Block> BEACON=BLOCKS.register("defense_beacon",()->new DefenseBlock());
    public static final RegistryObject<Item> BEACON_ITEM=ITEMS.register("defense_beacon",()->new BeaconItems.Placement(BEACON.get()));
    public static final RegistryObject<Item> CONTROLLER=ITEMS.register("beacon_controller",()->new BeaconItems.Controller());
    public static final RegistryObject<Item> GUIDE=ITEMS.register("field_guide",()->new BeaconItems.Guide());
    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<DefenseEntity>> BEACON_ENTITY=ENTITIES.register("defense_beacon",()->net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(DefenseEntity::new,BEACON.get()).build(null));
    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<WeaponPlatform.StationEntity>> STATION_ENTITY=ENTITIES.register("station",()->net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(WeaponPlatform.StationEntity::new,ArsenalBeacon.GUN_PLATFORM.get(),ArsenalBeacon.AMMO_PLATFORM.get(),ArsenalBeacon.ATTACHMENT_PLATFORM.get(),ArsenalBeacon.ARMOR_PLATFORM.get()).build(null));
    public static final RegistryObject<Item> ARDENT_ENERGY=ITEMS.register("ardent_energy",()->new Item(new Item.Properties().rarity(net.minecraft.world.item.Rarity.UNCOMMON)));
    public static final RegistryObject<Block> EXCHANGE_SHOP=BLOCKS.register("exchange_shop",ExchangeShop.ShopBlock::new);
    public static final RegistryObject<Item> EXCHANGE_SHOP_ITEM=ITEMS.register("exchange_shop",()->new BaseZone.ZoneItem(EXCHANGE_SHOP.get()));
    // ---- base support system -------------------------------------------------------------------------
    static final DeferredRegister<net.minecraft.world.inventory.MenuType<?>> MENUS=DeferredRegister.create(ForgeRegistries.MENU_TYPES,ID);
    public static final RegistryObject<Block> SUPPORT_PLATFORM=BLOCKS.register("support_platform",SupportPlatform.PlatformBlock::new);
    public static final RegistryObject<Item> SUPPORT_PLATFORM_ITEM=ITEMS.register("support_platform",()->new SupportPlatform.PlatformItem(SUPPORT_PLATFORM.get()));
    public static final RegistryObject<Block> SUPPORT_CANNON=BLOCKS.register("support_cannon",SupportCannon.CannonBlock::new);
    public static final RegistryObject<Item> SUPPORT_CANNON_ITEM=ITEMS.register("support_cannon",()->new SupportCannon.CannonItem(SUPPORT_CANNON.get()));
    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<SupportPlatform.PlatformEntity>> SUPPORT_PLATFORM_ENTITY=ENTITIES.register("support_platform",()->net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(SupportPlatform.PlatformEntity::new,SUPPORT_PLATFORM.get()).build(null));
    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<SupportCannon.CannonEntity>> CANNON_ENTITY=ENTITIES.register("support_cannon",()->net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(SupportCannon.CannonEntity::new,SUPPORT_CANNON.get()).build(null));
    public static final RegistryObject<net.minecraft.world.inventory.MenuType<SupportPlatform.PlatformMenu>> SUPPORT_MENU=MENUS.register("support_platform",()->net.minecraftforge.common.extensions.IForgeMenuType.create(SupportPlatform.PlatformMenu::client));
    public static final RegistryObject<Item> SUPPLY_FLARE=ITEMS.register("support_flare",()->new SupportFlares.FlareItem(SupportCalls.Kind.SUPPLY)),RETURN_FLARE=ITEMS.register("return_flare",()->new SupportFlares.FlareItem(SupportCalls.Kind.RETURN)),FIRE_FLARE=ITEMS.register("fire_support_flare",()->new SupportFlares.FlareItem(SupportCalls.Kind.FIRE));
    public static final RegistryObject<Item> PLATING=part("reinforced_plating"),LOGISTICS=part("logistics_module"),COIL=part("resonance_coil"),REPAIR=part("restoration_matrix"),AMMO_COIN=part("universal_ammo_coin");
    public static final RegistryObject<Block> GUN_PLATFORM=platform("gun"),AMMO_PLATFORM=platform("ammo"),ATTACHMENT_PLATFORM=platform("attachment"),ARMOR_PLATFORM=platform("armor");
    private static RegistryObject<Block> platform(String kind){
        RegistryObject<Block> block=BLOCKS.register(kind+"_platform",()->new WeaponPlatform.Station(kind));
        ITEMS.register(kind+"_platform",()->new WeaponPlatform.StationItem(block.get()));return block;
    }
    public static final List<RegistryObject<Block>> RACKS=net.minecraftforge.fml.ModList.get().isLoaded("arsenal_displays")?dev.createarsenal.displays.GunDisplays.RACKS:List.of();
    private static RegistryObject<Item> part(String id){return ITEMS.register(id,()->new Item(new Item.Properties()));}
    private static final DeferredRegister<EntityType<?>> OBJECTIVES=DeferredRegister.create(ForgeRegistries.ENTITY_TYPES,ID);
    static final RegistryObject<EntityType<BeaconCombat.Objective>> OBJECTIVE=OBJECTIVES.register("beacon_objective",()->EntityType.Builder.<BeaconCombat.Objective>of(BeaconCombat.Objective::new,MobCategory.MISC).sized(1f,1f).clientTrackingRange(64).build(ID+":beacon_objective"));
    public static final RegistryObject<EntityType<SupportFlares.FlareEntity>> FLARE=OBJECTIVES.register("support_flare",()->EntityType.Builder.<SupportFlares.FlareEntity>of(SupportFlares.FlareEntity::new,MobCategory.MISC).sized(.25f,.25f).clientTrackingRange(10).updateInterval(5).build(ID+":support_flare"));
    public static final RegistryObject<EntityType<SupportCrate.ParcelEntity>> PARCEL=OBJECTIVES.register("support_parcel",()->EntityType.Builder.<SupportCrate.ParcelEntity>of(SupportCrate.ParcelEntity::new,MobCategory.MISC).sized(.9f,.9f).clientTrackingRange(8).updateInterval(3).build(ID+":support_parcel"));
    private static int clock;
    public ArsenalBeacon() {
        var bus=FMLJavaModLoadingContext.get().getModEventBus();BLOCKS.register(bus);ITEMS.register(bus);ENTITIES.register(bus);TABS.register(bus);OBJECTIVES.register(bus);MENUS.register(bus);bus.addListener((net.minecraftforge.event.entity.EntityAttributeCreationEvent e)->e.put(OBJECTIVE.get(),Mob.createMobAttributes().add(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH,20).build()));BeaconNetwork.init();MinecraftForge.EVENT_BUS.register(new StandaloneBalance());
        if(!BuildFlavor.STANDALONE&&Boolean.getBoolean("arsenal.integrationTests")&&!net.minecraftforge.fml.ModList.get().isLoaded("kubejs"))PlatformGameTests.registerParts(bus);
        bus.addListener((BuildCreativeModeTabContentsEvent e)->{if(e.getTabKey()==CreativeModeTabs.FUNCTIONAL_BLOCKS){e.accept(BEACON_ITEM);e.accept(CONTROLLER);e.accept(GUIDE);e.accept(PLATING);e.accept(LOGISTICS);e.accept(COIL);e.accept(REPAIR);e.accept(GUN_PLATFORM.get());e.accept(AMMO_PLATFORM.get());e.accept(ATTACHMENT_PLATFORM.get());e.accept(ARMOR_PLATFORM.get());}});
        MinecraftForge.EVENT_BUS.register(this);MinecraftForge.EVENT_BUS.register(new ArdentEnergy());MinecraftForge.EVENT_BUS.register(new SupportFlares.Safety());MinecraftForge.EVENT_BUS.register(new SupportHud.Login());net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.COMMON,ArsenalConfig.SPEC,"arsenal-beacon-common.toml");MinecraftForge.EVENT_BUS.register(new WeaponPlatform());MinecraftForge.EVENT_BUS.register(new CreateUnlocks());
        MinecraftForge.EVENT_BUS.register(new StructureMigration());MinecraftForge.EVENT_BUS.register(new BaseScoring());MinecraftForge.EVENT_BUS.register(new SpecialForcesRaids());MinecraftForge.EVENT_BUS.register(new BeaconCombat());
        if(Boolean.getBoolean("arsenal.standaloneTests"))MinecraftForge.EVENT_BUS.register(new StandaloneBeaconTests.Runner());
        if(Boolean.getBoolean("arsenal.standaloneSmoke"))MinecraftForge.EVENT_BUS.register(new StandaloneSmoke());
    }
    public static final class DefenseEntity extends BlockEntity {
        public DefenseEntity(BlockPos pos,BlockState state){super(BEACON_ENTITY.get(),pos,state);}
        @Override public net.minecraft.world.phys.AABB getRenderBoundingBox(){return new net.minecraft.world.phys.AABB(worldPosition.getX()-1,worldPosition.getY(),worldPosition.getZ()-1,worldPosition.getX()+2,worldPosition.getY()+512,worldPosition.getZ()+2);}
    }
    public static final class DefenseBlock extends Block implements EntityBlock {
        public DefenseBlock(){super(Properties.of().strength(-1f,3600000f).lightLevel(s->15).noOcclusion());registerDefaultState(stateDefinition.any().setValue(ArsenalStructures.MK,1).setValue(ArsenalStructures.FACING,Direction.NORTH));}
        @Override protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block,BlockState> b){b.add(ArsenalStructures.MK,ArsenalStructures.FACING);}
        @Override public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext c){return defaultBlockState().setValue(ArsenalStructures.FACING,c.getHorizontalDirection().getOpposite());}
        @Override public net.minecraft.world.level.material.PushReaction getPistonPushReaction(BlockState s){return net.minecraft.world.level.material.PushReaction.BLOCK;}
        @Override public void onRemove(BlockState s,Level l,BlockPos p,BlockState next,boolean moving){if(!next.is(this))ArsenalStructures.remove(l,p);super.onRemove(s,l,p,next,moving);}
        @Override public net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,net.minecraft.world.phys.shapes.CollisionContext context){return ArsenalStructures.cell(state,0,0,0);}
        @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new DefenseEntity(pos,state);}
        @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,net.minecraft.world.entity.LivingEntity placer,ItemStack stack) {
            if(!(level instanceof ServerLevel server)||!(placer instanceof ServerPlayer player))return;
            CampaignData d=CampaignData.get(server);
            if(level.dimension()!=Level.OVERWORLD||!d.phase.equals("unplaced")) {
                server.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
                player.sendSystemMessage(Component.literal("One shared beacon per world, placed in the Overworld."));return;
            }
            d.beacon=pos.immutable();d.phase="preparation";d.campaignSerial++;d.participants.add(player.getUUID());d.health=d.maximumHealth();d.setDirty();
            if(!d.introCompleted){announce(server,"INTRODUCTORY RAID: starts now at reward tier 0. Survive to earn your Weapon Platform.");begin(server,d);}else announce(server,"Beacon planted. Scheduled raids resume after seven active days. Right-click for the control panel.");BeaconNetwork.syncNearby(server,d);
        }
        @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,net.minecraft.world.InteractionHand hand,BlockHitResult hit) {
            if(player.getItemInHand(hand).getItem() instanceof BlockItem)return InteractionResult.PASS;
            if(level instanceof ServerLevel server&&player instanceof ServerPlayer p) {
                CampaignData d=CampaignData.get(server);d.participants.add(p.getUUID());
                if(player.getItemInHand(hand).is(CONTROLLER.get()))BeaconActions.requestRemoval(p);
                else BeaconNetwork.open(p,"menu","");d.setDirty();
            }
            return InteractionResult.SUCCESS;
        }
    }
    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent event) {
        if(!(event.getEntity() instanceof ServerPlayer p))return;
        CampaignData d=CampaignData.get(p.server.overworld());
        if(d.phase.equals("unplaced")&&!p.getPersistentData().getBoolean("arsenalBeaconGranted")) {
            if(p.getInventory().add(new ItemStack(BEACON_ITEM.get())))p.getPersistentData().putBoolean("arsenalBeaconGranted",true);
        }
        if(!d.phase.equals("unplaced"))d.participants.add(p.getUUID());d.setDirty();
        grantSupport(p);BeaconNetwork.sendState(p,"","","");
        grantStarter(p);
    }
    static void grantSupport(ServerPlayer p) {
        if(p.getPersistentData().getBoolean("arsenalSupportGranted03"))return;
        give(p,new ItemStack(GUIDE.get()));give(p,new ItemStack(CONTROLLER.get()));p.getPersistentData().putBoolean("arsenalSupportGranted03",true);
    }
    static void give(ServerPlayer p,ItemStack stack){if(!p.getInventory().add(stack)&&!stack.isEmpty())p.drop(stack,false);}
    static void grantStarter(ServerPlayer p) {
        if(BuildFlavor.STANDALONE){StandaloneBalance.grantStarter(p);return;}
        if(p.getPersistentData().getBoolean("arsenalStarterGranted"))return;
        Item gunItem=BuiltInRegistries.ITEM.get(new ResourceLocation("tacz:modern_kinetic_gun"));
        Item ammoItem=BuiltInRegistries.ITEM.get(new ResourceLocation("tacz:ammo"));
        if(gunItem==Items.AIR||ammoItem==Items.AIR)return;
        try {
            ItemStack gun=new ItemStack(gunItem),ammo=new ItemStack(ammoItem,64);
            gunItem.getClass().getMethod("setGunId",ItemStack.class,ResourceLocation.class).invoke(gunItem,gun,new ResourceLocation("qkl:fk15p"));
            gunItem.getClass().getMethod("setCurrentAmmoCount",ItemStack.class,int.class).invoke(gunItem,gun,0);
            gun.getOrCreateTag().putString("GunFireMode","SEMI");
            ammoItem.getClass().getMethod("setAmmoId",ItemStack.class,ResourceLocation.class).invoke(ammoItem,ammo,new ResourceLocation("qkl:16mm"));
            if(!p.getInventory().add(gun)&&!gun.isEmpty())p.drop(gun,false);
            if(!p.getInventory().add(ammo)&&!ammo.isEmpty())p.drop(ammo,false);
            p.getPersistentData().putBoolean("arsenalStarterGranted",true);
            feedback(p,"[Create Arsenal] Starter kit: one FK15P flintlock and 64 round balls. Reload with R; it holds one round and reloads slowly.");
        } catch(ReflectiveOperationException ex) {
            com.mojang.logging.LogUtils.getLogger().error("Create Arsenal starter kit could not access the installed TaCZ item API",ex);
        }
    }
    @SubscribeEvent public void clonePlayer(PlayerEvent.Clone e) {
        if(e.getOriginal().getPersistentData().getBoolean("arsenalBeaconGranted"))e.getEntity().getPersistentData().putBoolean("arsenalBeaconGranted",true);
        if(e.getOriginal().getPersistentData().getBoolean("arsenalStarterGranted"))e.getEntity().getPersistentData().putBoolean("arsenalStarterGranted",true);
        if(e.getOriginal().getPersistentData().getBoolean("arsenalSupportGranted03"))e.getEntity().getPersistentData().putBoolean("arsenalSupportGranted03",true);
    }
    @SubscribeEvent public void serverStopped(net.minecraftforge.event.server.ServerStoppedEvent e){clock=0;BeaconActions.clear();}
    @SubscribeEvent public void commands(RegisterCommandsEvent event) {
        // Explicit opt-in for our isolated integration server; normal packs expose no test commands.
        if(Boolean.getBoolean("arsenal.integrationTests")) {
            if(net.minecraft.gametest.framework.GameTestRegistry.getAllTestFunctions().isEmpty())
                net.minecraft.gametest.framework.GameTestRegistry.register(BeaconGameTests.class);
                net.minecraft.gametest.framework.GameTestRegistry.register(PlatformGameTests.class);
                net.minecraft.gametest.framework.GameTestRegistry.register(CompatibilityGameTests.class);
            net.minecraft.gametest.framework.GameTestRegistry.register(UpgradeGameTests.class);
            net.minecraft.gametest.framework.GameTestRegistry.register(ReleaseSevenGameTests.class);
            net.minecraft.gametest.framework.GameTestRegistry.register(CampaignEightGameTests.class);
            net.minecraft.gametest.framework.GameTestRegistry.register(ReleaseNineGameTests.class);
            net.minecraft.gametest.framework.GameTestRegistry.register(RemodelGameTests.class);
            net.minecraft.gametest.framework.GameTestRegistry.register(ReleaseElevenGameTests.class);
            net.minecraft.gametest.framework.GameTestRegistry.register(ReleaseTwelveGameTests.class);
            net.minecraft.gametest.framework.GameTestRegistry.register(ReleaseThirteenGameTests.class);
            net.minecraft.gametest.framework.GameTestRegistry.register(SoundResourceGameTests.class);
            net.minecraft.gametest.framework.GameTestRegistry.register(GunPackSyncGameTests.class);
            net.minecraft.gametest.framework.GameTestRegistry.register(BrowserGameTests.class);
            net.minecraft.gametest.framework.GameTestRegistry.register(DisplayRackGameTests.class);
            net.minecraft.gametest.framework.GameTestRegistry.register(SpawnSixteenGameTests.class);
            net.minecraft.gametest.framework.GameTestRegistry.register(ArmorGameTests.class);
            net.minecraft.gametest.framework.GameTestRegistry.register(AmmoCatalogueGameTests.class);
            net.minecraft.gametest.framework.TestCommand.register(event.getDispatcher());
            event.getDispatcher().register(Commands.literal("arsenal-integration").requires(s->s.hasPermission(2)).executes(c->{IntegrationTests.start(c.getSource().getLevel());return 1;}));
        }
        event.getDispatcher().register(Commands.literal("arsenal")
            .executes(c->{BeaconNetwork.open(c.getSource().getPlayerOrException(),"menu","");return 1;})
            .then(Commands.literal("boundary").executes(c->{var d=CampaignData.get(c.getSource().getServer().overworld());d.showBoundary=!d.showBoundary;d.setDirty();return 1;}))
            .then(Commands.literal("claim").executes(c->{claim(c.getSource().getPlayerOrException(),CampaignData.get(c.getSource().getServer().overworld()));return 1;}))
            .then(Commands.literal("upgrade").then(Commands.argument("branch",StringArgumentType.word()).suggests((c,b)->{for(String s:List.of("core","logistics","defense","restoration","reconnaissance","vertical"))b.suggest(s);return b.buildFuture();}).executes(c->upgrade(c.getSource().getPlayerOrException(),StringArgumentType.getString(c,"branch")))))
            .then(Commands.literal("repair").executes(c->repair(c.getSource().getPlayerOrException())))
            .then(Commands.literal("test-raid").requires(s->s.hasPermission(2)).executes(c->{var l=c.getSource().getServer().overworld();var d=CampaignData.get(l);if(!d.phase.equals("preparation"))return 0;begin(l,d);return 1;})));
    }
    static int feedback(ServerPlayer p,String text){
        if(BeaconActions.inAction())BeaconNetwork.sendState(p,"","",text);else p.sendSystemMessage(Component.literal(text));return 1;
    }
    private static void status(ServerPlayer p,CampaignData d) {
        int days=Rules.intervalDays(d.rewardTier);
        p.sendSystemMessage(Component.literal("Beacon: "+d.phase+" | HP "+d.health+"/"+d.maximumHealth()+" | last reward tier "+d.rewardTier+" | raids every "+days+" days | next due in "+Math.max(0,days*24000L-d.preparationTicks)/24000.0+" days"));
        p.sendSystemMessage(Component.literal("Radius "+d.radius()+"; height -"+d.below()+"/+"+d.above()+" | base score "+d.score+" | victories "+d.victories+" | core/logistics/defense/restoration: "+d.core+"/"+d.logistics+"/"+d.defense+"/"+d.restoration));
        p.sendSystemMessage(Component.literal("/arsenal upgrade <core|logistics|defense|restoration|reconnaissance> | /arsenal claim | /arsenal repair | /arsenal boundary"));
        String[] branches={"core","logistics","defense","restoration","reconnaissance","vertical"};int[] grades={d.core,d.logistics,d.defense,d.restoration,d.reconnaissance,d.vertical};
        for(int i=0;i<branches.length;i++) {
            String branch=branches[i];int grade=grades[i],limit=branch.equals("reconnaissance")?1:4;
            p.sendSystemMessage(Component.literal("["+branch+" "+grade+"/"+limit+(grade<limit?" â€” upgrade: "+Rules.upgradeCost(grade)+" components":" â€” complete")+"]")
                .withStyle(style->style.withColor(ChatFormatting.AQUA).withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,"/arsenal upgrade "+branch))));
        }
    }
    static boolean near(ServerPlayer p,CampaignData d){return p.level().dimension()==Level.OVERWORLD&&p.blockPosition().distSqr(d.beacon)<(d.radius()+64.0)*(d.radius()+64.0);}
    static int upgrade(ServerPlayer p,String branch) {
        var d=CampaignData.get(p.server.overworld());if(!near(p,d)||d.active()||!d.installed())return feedback(p,"Upgrades require being near a planted beacon between raids.");
        int grade;Item part;
        switch(branch){case "core"-> {grade=d.core;part=PLATING.get();}case "logistics"->{grade=d.logistics;part=LOGISTICS.get();}case "defense"->{grade=d.defense;part=COIL.get();}case "restoration"->{grade=d.restoration;part=REPAIR.get();}case "reconnaissance"->{grade=d.reconnaissance;part=COIL.get();}case "vertical"->{grade=d.vertical;part=LOGISTICS.get();}default->{return feedback(p,"Branches: core, logistics, defense, restoration, reconnaissance, vertical.");}}
        if(grade>=Rules.branchMaximum(branch))return feedback(p,"That branch is fully upgraded.");
        if(branch.equals("core")){var wanted=p.server.overworld().getBlockState(d.beacon).setValue(ArsenalStructures.MK,ArsenalStructures.mark(d.core+1));if(!ArsenalStructures.available(p.server.overworld(),d.beacon,wanted))return feedback(p,"Clear a 3 x 3 area, two blocks tall, centered on the beacon before upgrading to Mk-4. Nothing consumed.");}
        int cost=Rules.upgradeCost(grade);if(!consume(p,part,cost))return feedback(p,"Upgrade needs "+cost+" "+part.getDescription().getString()+". See its factory recipe in JEI.");
        switch(branch){case "core"->{int oldMaximum=d.maximumHealth();d.core++;d.health=Math.min(d.maximumHealth(),d.health+d.maximumHealth()-oldMaximum);ArsenalStructures.syncBeacon(p.server.overworld(),d);}case "logistics"->d.logistics++;case "defense"->d.defense++;case "restoration"->d.restoration++;case "reconnaissance"->d.reconnaissance++;case "vertical"->d.vertical++;}
        d.setDirty();BaseSurvey.request(p.server.overworld());
        if(branch.equals("reconnaissance"))return feedback(p,"Reconnaissance online: every raid attacker is highlighted from the moment it spawns.");
        return feedback(p,switch(branch){case "core"->"Core upgraded: Mk-"+(d.core+1)+", radius "+d.radius()+", "+d.maximumHealth()+" maximum HP.";case "vertical"->"Vertical zone upgraded: "+d.below()+" below / "+d.above()+" above. Beacon model and HP unchanged.";case "defense"->"Defense upgraded: "+Rules.defensePercent(d.defense)+"% less beacon damage.";case "restoration"->"Restoration upgraded: heal "+Rules.healingPercent(d.restoration)+"% of maximum HP after victory; repair "+(32+d.restoration*32)+" blocks per tick.";default->"Logistics upgraded: +"+d.logistics+" payout tiers. Better paid tiers shorten the following interval.";});
    }
    static int repair(ServerPlayer p) {
        var d=CampaignData.get(p.server.overworld());if(!d.installed()||!near(p,d)||d.active())return feedback(p,"Repair between raids, near the beacon.");
        if(d.health>=d.maximumHealth())return feedback(p,"Already at full health.");
        if(!consume(p,PLATING.get(),8))return feedback(p,"Needs 8 reinforced plating. JEI shows the recipe.");
        d.health=Math.min(d.maximumHealth(),d.health+250);d.phase="preparation";d.setDirty();return feedback(p,"Repaired +250 HP. Campaign resumed.");
    }
    static boolean consume(ServerPlayer p,Item item,int cost) {
        if(p.isCreative())return true;int found=0;for(ItemStack s:p.getInventory().items)if(s.is(item))found+=s.getCount();if(found<cost)return false;
        for(ItemStack s:p.getInventory().items)if(s.is(item)){int take=Math.min(cost,s.getCount());s.shrink(take);cost-=take;if(cost==0)break;}p.getInventory().setChanged();return true;
    }
    static void claim(ServerPlayer p,CampaignData d) {
        if(!near(p,d))return;
        feedback(p,RewardCache.claim(p,d));
    }
    static void decommission(ServerLevel l,CampaignData d,ServerPlayer p) {
        for(UUID id:d.raiders){Entity e=l.getEntity(id);if(e!=null)e.discard();}
        if(l.hasChunkAt(d.beacon)&&l.getBlockState(d.beacon).is(BEACON.get()))l.setBlock(d.beacon,Blocks.AIR.defaultBlockState(),3);
        d.campaignSerial++;d.resetProgress();d.phase="decommissioning";d.setDirty();
        if(p!=null)give(p,new ItemStack(BEACON_ITEM.get()));
        if(d.damage.isEmpty()&&d.destroyedTurrets.isEmpty())d.finishDecommission();
        BeaconNetwork.syncNearby(l,d);
    }
    static boolean reconcileMissing(ServerLevel l,CampaignData d) {
        if(d.installed()&&l.hasChunkAt(d.beacon)&&!l.getBlockState(d.beacon).is(BEACON.get())) {decommission(l,d,null);return true;}return false;
    }
    private static void announce(ServerLevel l,String s){l.getServer().getPlayerList().broadcastSystemMessage(Component.literal("[Create Arsenal] "+s).withStyle(ChatFormatting.GOLD),false);}
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        if(Boolean.getBoolean("arsenal.integrationTests"))IntegrationTests.tick();
        ServerLevel l=event.getServer().overworld();CampaignData d=CampaignData.get(l);clock++;if(clock%20==0)SpecialForcesRaids.cleanup(l);BeaconCombat.tick(l,d);BaseSurvey.tick(l,d);HardRaids.tick(l,d);
        if(clock%20==0){
            if(reconcileMissing(l,d))announce(l,"Missing beacon cleared. Place a beacon to start a fresh campaign.");
            BeaconNetwork.syncNearby(l,d);
        }
        if(d.phase.equals("decommissioning")){restore(l,d);return;}
        if(d.phase.equals("unplaced"))return;
        // No offline escalation. Timer uses active ticks rather than absolute day count.
        if(l.getServer().getPlayerList().getPlayers().isEmpty())return;
        switch(d.phase) {
            case "preparation" -> {
                if(RaidRespite.waitTick(d))break;
                d.preparationTicks++;RaidWarnings.tick(l,d);
                long time=l.getDayTime()%24000;
                if(d.preparationTicks>=Rules.intervalDays(d.rewardTier)*24000L&&time>=13000&&time<21000&&l.players().stream().anyMatch(p->near(p,d)))begin(l,d);
                if(clock%1200==0)d.setDirty();
            }
            case "snapshot" -> scan(l,d);
            case "raid" -> raid(l,d);
            case "restore" -> restore(l,d);
            default -> {}
        }
    }
    private static void boundary(ServerLevel l,CampaignData d) {
        int step=d.active()?8:20;
        for(int a=0;a<360;a+=step){double t=Math.toRadians(a);l.sendParticles(ParticleTypes.END_ROD,d.beacon.getX()+0.5+Math.cos(t)*d.radius(),d.beacon.getY()+0.2,d.beacon.getZ()+0.5+Math.sin(t)*d.radius(),1,0,0,0,0);}
        if(d.active())for(int y=1;y<32;y+=2)l.sendParticles(ParticleTypes.END_ROD,d.beacon.getX()+0.5,d.beacon.getY()+y,d.beacon.getZ()+0.5,1,0,0,0,0);
    }
    static void begin(ServerLevel l,CampaignData d) {
        if(!d.damage.isEmpty()||!d.destroyedTurrets.isEmpty()){announce(l,"Complete pending restoration before starting another raid.");return;}
        RaidWarnings.reset();d.introRaid=!d.introCompleted;d.victoryRestoration=false;d.phase="snapshot";d.scanCursor=0;d.snapshot.clear();d.baseCounts.clear();d.wave=0;d.deaths=0;d.raidTicks=0;d.raiders.clear();d.setDirty();
        announce(l,"Raid warning! Saving the marked base area in small batches. Building is locked until the raid ends.");
    }
    private static void scan(ServerLevel l,CampaignData d) {
        int width=d.radius()*2+1,total=width*width*d.height(),budget=4096;
        for(int k=0;k<budget&&d.scanCursor<total;k++,d.scanCursor++){
            int i=d.scanCursor;int x=i%width-d.radius();int z=(i/width)%width-d.radius();int y=i/(width*width)-d.below();
            BlockPos pos=d.beacon.offset(x,y,z);if(!d.inside(pos)||pos.getY()<l.getMinBuildHeight()||pos.getY()>=l.getMaxBuildHeight())continue;
            if(!l.hasChunkAt(pos)){fail(l,d,"Protected area unloaded during snapshot; no raid started.");return;}
            BlockState s=l.getBlockState(pos);if(s.isAir())continue;d.snapshot.put(pos.asLong(),s);
            auditBlock(d,pos,s);
        }
        if(d.scanCursor>=total){d.score=BaseScoring.analyze(d,d.snapshot,BaseScoring.Ledger.get(l).placed).total();d.raidTier=d.introRaid?0:RaidRespite.previewTier(d,Rules.rewardTier(d.logistics,d.score));RaidRespite.started(d);d.phase="raid";HardRaids.start(d);if(d.introRaid)d.hardRaid=false;BeaconNetwork.announce(l,"raid_start",d.raidsStarted,d.hardRaid?1:0,"","");if(d.hardRaid)announce(l,"HARD RAID #"+d.raidsStarted+"! A boss leads the final wave. Survive for a 50% larger resource cache and bonus medical kits/grenades.");nextWave(l,d);d.setDirty();announce(l,"Base saved. Score "+d.score+", payout tier "+d.raidTier+". Defend the beacon!");}
    }
    private static void nextWave(ServerLevel l,CampaignData d) {
        d.wave++;d.waveTicks=0;d.wavePlayers=RaidBalance.defenders(l,d);d.waveVeteran=Rules.veteranPressure(d.raidTier,d.victories);d.spawnRemaining=Rules.waveEnemies(d.raidTier,d.wave,d.wavePlayers,d.waveVeteran,d.hardRaid);d.spawnCooldown=0;
        BeaconNetwork.announce(l,"wave",d.wave,Rules.waves(d.raidTier),Integer.toString(d.spawnRemaining),"");
        announce(l,"Wave "+d.wave+" / "+Rules.waves(d.raidTier)+" incoming. "+d.wavePlayers+" defender(s), "+d.spawnRemaining+" attackers"+(d.waveVeteran>0?", veteran reinforcements +"+(d.waveVeteran*10)+"%":"")+".");d.setDirty();
    }
    static void auditBlock(CampaignData d,BlockPos pos,BlockState s){
        if(!d.inside(pos))return;
        String id=BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();
        if(s.getBlock() instanceof WeaponPlatform.Station)d.baseCounts.merge(id+"#platform",s.getValue(WeaponPlatform.AGE),Math::max);
        else d.baseCounts.merge(id,1,Integer::sum);
    }
    private static void raid(ServerLevel l,CampaignData d) {
        d.raidTicks++;d.waveTicks++;
        if(d.reconnaissance==0&&d.waveTicks==1200)announce(l,"One minute elapsed: remaining attackers are now highlighted through walls.");
        if(d.raidTicks>24000L){fail(l,d,"Raid timed out. Beacon loses 20% health; damaged structures stay damaged.");return;}
        if(d.health<=0){fail(l,d,"Beacon disabled. Repair it between raids; your base remains in the world.");return;}
        if(!l.hasChunkAt(d.beacon)){fail(l,d,"Beacon area unloaded. Retreat counts as a lost defense.");return;}
        if(d.spawnRemaining>0&&--d.spawnCooldown<=0&&RaidBalance.canSpawn(l,d)){if(spawn(l,d))d.spawnRemaining--;d.spawnCooldown=20;d.setDirty();}
        if(clock%20!=0)return;
        for(var it=d.raiders.iterator();it.hasNext();) {
            Entity e=l.getEntity(it.next());if(e==null||!e.isAlive()){it.remove();continue;}
            if(SpecialForcesRaids.defeated(e)){SpecialForcesRaids.retire(e,l);it.remove();d.setDirty();continue;}
            if(!(e instanceof Mob mob))continue;
            if(mob.getPersistentData().getBoolean("arsenalBoss"))HardRaids.balance(mob,d.raidTier);else if(d.raidTier<=1)balanceEarly(mob,d.raidTier,d.wave);
            else if(mob.getPersistentData().getBoolean("arsenalSpecialForces"))SpecialForcesRaids.balance(mob,d.raidTier);
            else RaidBalance.balance(mob,d.raidTier);
            mob.setGlowingTag(Rules.highlightAttackers(d.reconnaissance,d.waveTicks));
            // Real attack goals target the objective; contact alone never damages it.
            BeaconCombat.attach(mob);
            if(l.getGameTime()%40==0&&(mob.horizontalCollision||mob.getNavigation().isDone()||mob.getTarget() instanceof BeaconCombat.Objective&&!mob.hasLineOfSight(mob.getTarget()))&&BeaconCombat.needsBreach(mob,d))RaidBreaching.breach(l,d,mob);
            // All hostile breach attempts run through BeaconCombat and the same damage journal.
        }
        if(d.spawnRemaining==0&&d.raiders.isEmpty()){
            reward(d,Items.IRON_INGOT,RaidRewards.waveIron(d.raidTier)); // Each cleared wave has a claimable supply reward.
            if(d.wave>=Rules.waves(d.raidTier)){
                if(d.hardRaid&&(!d.bossSpawned||!d.bossKilled)){fail(l,d,"Boss was not defeated. No completion cache awarded; surviving wave rewards remain.");return;}
                d.victories++;d.rewardTier=d.raidTier;
                RaidRewards.queue(d,RaidRewards.completion(l,d.rewardTier,d.hardRaid&&d.bossKilled));
                if(d.introRaid&&!d.introCompleted){RaidRewards.queue(d,java.util.List.of(new ItemStack(GUN_PLATFORM.get())));d.introCompleted=true;d.introRaid=false;announce(l,"Introduction complete! Claim your Weapon Platform at the beacon.");}
                BeaconNetwork.announce(l,"victory",d.rewardTier,0,"","");d.victoryRestoration=true;d.phase="restore";announce(l,"Defense won! Wave rewards are ready at the beacon. Repairing raid damage; ammunition stays spent.");
            }
            else nextWave(l,d);
            d.setDirty();
        } else d.setDirty();
    }
    private static boolean spawn(ServerLevel l,CampaignData d) {
        BlockPos spawnPos=RaidSpawns.find(l,d);
        if(spawnPos==null)return false;
        int x=spawnPos.getX(),y=spawnPos.getY(),z=spawnPos.getZ();
        if(d.hardRaid&&d.wave==Rules.waves(d.raidTier)&&!d.bossSpawned)return HardRaids.spawn(l,d,spawnPos);
        int soldiers=SpecialForcesRaids.count(d.raidTier,d.wavePlayers);
        if(soldiers>0&&d.spawnRemaining<=soldiers&&net.minecraftforge.fml.ModList.get().isLoaded("taczsf"))return SpecialForcesRaids.spawn(l,d,spawnPos,d.spawnRemaining==soldiers);
        String role=d.introRaid?"grunt":RaidBalance.role(d.raidTier,l.random.nextInt(100));
        EntityType<?> type=d.introRaid?EntityType.HUSK:RaidBalance.type(l,d.raidTier,role);
        Entity entity=type.create(l);if(!(entity instanceof Mob mob))return false;
        mob.moveTo(x+0.5,y,z+0.5,l.random.nextFloat()*360,0);mob.setPersistenceRequired();mob.getPersistentData().putBoolean("arsenalRaider",true);
        mob.setGlowingTag(Rules.highlightAttackers(d.reconnaissance,d.waveTicks));
        if(!l.noCollision(mob)||!l.addFreshEntity(mob))return false; // Let pack spawn handlers run first.
        mob.getPersistentData().putString("arsenalRole",role);
        if(d.raidTier<=1)balanceEarly(mob,d.raidTier,d.wave);else RaidBalance.balance(mob,d.raidTier);
        mob.setHealth(mob.getMaxHealth());
        d.raiders.add(mob.getUUID());
        return true;
    }
    static void balanceEarly(Mob mob,int tier,int wave) {
        for(EquipmentSlot slot:EquipmentSlot.values()){
            if(slot.getType()==EquipmentSlot.Type.ARMOR||slot==EquipmentSlot.OFFHAND||slot==EquipmentSlot.MAINHAND&&!(mob instanceof net.minecraft.world.entity.monster.AbstractSkeleton))mob.setItemSlot(slot,ItemStack.EMPTY);
        }
        if(mob instanceof net.minecraft.world.entity.monster.AbstractSkeleton&&(!mob.getMainHandItem().is(Items.BOW)||mob.getMainHandItem().isEnchanted()))mob.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.BOW));
        for(var attr:List.of(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR,net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS,net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE)){
            var value=mob.getAttribute(attr);if(value!=null){value.removeModifiers();value.setBaseValue(0);}
        }
        var health=mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if(health!=null){float current=mob.getHealth();health.removeModifiers();health.setBaseValue(20+Math.min(3,wave)*2+tier*8);mob.setHealth(Math.min(current,mob.getMaxHealth()));}
        var damage=mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);if(damage!=null){damage.removeModifiers();damage.setBaseValue(2+tier);}
        mob.setCanPickUpLoot(false);if(mob instanceof net.minecraft.world.entity.monster.Zombie zombie)zombie.setBaby(false);
    }
    private static void breach(ServerLevel l,CampaignData d,Mob mob) {
        Vec3 dir=Vec3.atCenterOf(d.beacon).subtract(mob.position()).normalize();BlockPos ahead=BlockPos.containing(mob.getX()+dir.x*1.4,mob.getY(),mob.getZ()+dir.z*1.4);
        for(int dy=0;dy<2;dy++){BlockPos p=ahead.above(dy);if(d.inside(p)&&!p.equals(d.beacon)&&!l.getBlockState(p).isAir()&&l.getBlockState(p).getDestroySpeed(l,p)>=0)damageBlock(l,d,p);}
    }
    static void damageBlock(ServerLevel l,CampaignData d,BlockPos p) {
        p=ArsenalStructures.anchor(l,p);
        if(net.minecraftforge.fml.ModList.get().isLoaded("arsenal_displays"))p=dev.createarsenal.displays.DisplayRacks.anchor(l,p);
        if(p.equals(d.beacon)||!d.inside(p)||!d.snapshot.containsKey(p.asLong()))return;
        BlockState current=l.getBlockState(p);if(current.isAir()||current.getDestroySpeed(l,p)<0)return;
        if(current.getBlock() instanceof DoorBlock){
            var partner=current.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)==net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER?p.above():p.below();
            var other=l.getBlockState(partner);if(d.inside(partner)&&other.getBlock()==current.getBlock()&&d.snapshot.containsKey(partner.asLong())){d.damage.putIfAbsent(partner.asLong(),new CampaignData.Damage(d.snapshot.get(partner.asLong()),null));l.setBlock(partner,Blocks.AIR.defaultBlockState(),2);}
        }
        BlockEntity be=l.getBlockEntity(p);CompoundTag live=be==null?null:be.saveWithFullMetadata();
        d.damage.putIfAbsent(p.asLong(),new CampaignData.Damage(d.snapshot.get(p.asLong()),live));d.setDirty();
        // Remove the entity before the block: inventories and fuel are held in the journal, not dropped.
        // Repairs use the latest state at destruction, never the pre-raid inventory snapshot.
        l.removeBlockEntity(p);l.setBlock(p,Blocks.AIR.defaultBlockState(),3);
    }
    static void restore(ServerLevel l,CampaignData d) {
        int budget=32+d.restoration*32;
        for(var it=d.damage.entrySet().iterator();it.hasNext()&&budget-->0;) {
            var entry=it.next();BlockPos p=BlockPos.of(entry.getKey());if(!l.hasChunkAt(p))continue;
            BlockState now=l.getBlockState(p);if(!now.isAir()&&now.getFluidState().isEmpty()) {it.remove();continue;}
            var saved=entry.getValue();boolean door=saved.before().getBlock() instanceof DoorBlock;l.setBlock(p,saved.before(),door?2:3);
            if(door){var partner=saved.before().getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)==net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER?p.above():p.below();if(l.getBlockState(partner).getBlock()==saved.before().getBlock())l.updateNeighborsAt(p,saved.before().getBlock());}
            if(saved.entity()!=null){BlockEntity be=l.getBlockEntity(p);if(be!=null){be.load(saved.entity());be.setChanged();l.sendBlockUpdated(p,saved.before(),saved.before(),3);}}
            it.remove();
        }
        if(d.damage.isEmpty()) {
            for(var it=d.destroyedTurrets.iterator();it.hasNext();) {
                CompoundTag n=it.next();ListTag pos=n.getList("Pos",Tag.TAG_DOUBLE);if(pos.size()<3){it.remove();continue;}
                BlockPos p=BlockPos.containing(pos.getDouble(0),pos.getDouble(1),pos.getDouble(2));if(!l.hasChunkAt(p))continue;
                if(n.hasUUID("UUID")&&l.getEntity(n.getUUID("UUID"))!=null){it.remove();continue;}
                Entity e=EntityType.loadEntityRecursive(n,l,entity->entity);
                if(e instanceof LivingEntity living){living.setHealth(living.getMaxHealth());living.deathTime=0;}
                if(e!=null&&l.addFreshEntity(e))it.remove();
            }
        }
        d.setDirty();if(d.damage.isEmpty()&&d.destroyedTurrets.isEmpty()){
            if(d.phase.equals("decommissioning")){d.finishDecommission();BeaconNetwork.syncNearby(l,d);return;}
            d.snapshot.clear();d.phase=d.health>0?"preparation":"disabled";d.preparationTicks=0;
            if(d.victoryRestoration)d.health=Math.min(d.maximumHealth(),d.health+(int)Math.ceil(d.maximumHealth()*Rules.healingPercent(d.restoration)/100.0));
            announce(l,(d.victoryRestoration?"Victory repairs complete.":"Machine and turret contents recovered; defeated wall damage remains.")+" Next interval: "+Rules.intervalDays(d.rewardTier)+" active days.");
        }
    }
    private static void fail(ServerLevel l,CampaignData d,String reason) {
        d.health=Math.max(0,d.health-d.maximumHealth()/5);d.victoryRestoration=false;d.phase="restore";d.preparationTicks=0;d.spawnRemaining=0;
        BeaconNetwork.announce(l,"defeat",0,0,"","");for(UUID id:d.raiders){Entity e=l.getEntity(id);if(e!=null)e.discard();}d.raiders.clear();
        // Keep all machine journals until restoration succeeds, even if a chunk is unloaded.
        // Empty wall entries are deliberately not repaired after a failed defense.
        d.damage.entrySet().removeIf(e->e.getValue().entity()==null);d.setDirty();announce(l,reason);
    }
    private static void reward(CampaignData d,Item item,int count){while(count>0){int n=Math.min(64,count);d.rewards.add(new ItemStack(item,n).save(new CompoundTag()));count-=n;}}
    private static void rewardModItem(CampaignData d,String id,int count){Item item=BuiltInRegistries.ITEM.get(new ResourceLocation(id));if(item!=Items.AIR)reward(d,item,count);}
    @SubscribeEvent public void died(LivingDeathEvent event) {
        if(event.getEntity().level() instanceof ServerLevel bossLevel)HardRaids.killed(bossLevel,event.getEntity());
        if(event.getEntity().level() instanceof ServerLevel l&&l.dimension()==Level.OVERWORLD) {
            CampaignData d=CampaignData.get(l);
            String id=BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType()).toString();
            if(d.phase.equals("raid")&&d.inside(event.getEntity().blockPosition())&&id.startsWith("tacz_turrets:")) {
                CompoundTag n=new CompoundTag();event.getEntity().save(n);n.putShort("DeathTime",(short)0);n.putShort("HurtTime",(short)0);
                d.destroyedTurrets.add(n);d.setDirty();event.setCanceled(true);event.getEntity().discard();return;
            }
        }
        if(!(event.getEntity() instanceof ServerPlayer p)||p.level().dimension()!=Level.OVERWORLD)return;
        CampaignData d=CampaignData.get(p.server.overworld());if(!d.phase.equals("raid")||!near(p,d))return;
        d.deaths++;int penalty=Rules.deathPenalty(d.deaths,3,d.maximumHealth());if(penalty>0){d.health-=penalty;announce(p.server.overworld(),"Three defender deaths: beacon loses 10% health.");}d.setDirty();
    }
    @SubscribeEvent public void explosion(ExplosionEvent.Detonate event) {
        if(!(event.getLevel() instanceof ServerLevel l)||l.dimension()!=Level.OVERWORLD)return;
        CampaignData d=CampaignData.get(l);if(!d.phase.equals("raid"))return;
        LivingEntity cause=event.getExplosion().getIndirectSourceEntity();
        if(cause==null||!cause.getPersistentData().getBoolean("arsenalRaider"))return;
        for(var it=event.getAffectedBlocks().iterator();it.hasNext();) {BlockPos p=it.next();if(d.inside(p)){damageBlock(l,d,p);it.remove();}}
    }
    @SubscribeEvent public void mobGrief(EntityMobGriefingEvent event) {
        if(!(event.getEntity().level() instanceof ServerLevel l)||l.dimension()!=Level.OVERWORLD)return;
        CampaignData d=CampaignData.get(l);
        // Raid attackers use our journaled breaching. Ambient mob grief is suppressed inside the locked area.
        if(d.active()&&d.inside(event.getEntity().blockPosition()))event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
    }
    @SubscribeEvent public void loadedRaider(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        if(!event.loadedFromDisk()||!(event.getLevel() instanceof ServerLevel l)||l.dimension()!=Level.OVERWORLD||!event.getEntity().getPersistentData().getBoolean("arsenalRaider"))return;
        var d=CampaignData.get(l);
        if(!d.phase.equals("raid")||!d.raiders.contains(event.getEntity().getUUID())){event.setCanceled(true);event.getEntity().discard();}
    }
    @SubscribeEvent public void breakBlock(BlockEvent.BreakEvent event) {
        if(!(event.getLevel() instanceof ServerLevel l)||l.dimension()!=Level.OVERWORLD)return;
        CampaignData d=CampaignData.get(l);
        if(ArsenalStructures.beacon(l,event.getPos())){event.setCanceled(true);if(event.getPlayer() instanceof ServerPlayer p)feedback(p,"Use the Beacon Recovery Shovel to remove it. Confirmation required.");return;}
        if(d.active()&&d.inside(event.getPos())){event.setCanceled(true);if(event.getPlayer() instanceof ServerPlayer p)feedback(p,"The beacon area is locked until the raid and repairs end.");}
    }
    @SubscribeEvent public void placeBlock(BlockEvent.EntityPlaceEvent event) {
        if(!(event.getLevel() instanceof ServerLevel l)||l.dimension()!=Level.OVERWORLD)return;
        CampaignData d=CampaignData.get(l);if(d.active()&&d.inside(event.getPos())&&event.getEntity() instanceof Player)event.setCanceled(true);
    }
    @SubscribeEvent public void sleep(SleepingTimeCheckEvent event) {
        if(!(event.getEntity().level() instanceof ServerLevel l)||l.dimension()!=Level.OVERWORLD)return;
        if(CampaignData.get(l).active())event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
    }
    @SubscribeEvent public void wrench(PlayerInteractEvent.RightClickBlock event) {
        if(!(event.getLevel() instanceof ServerLevel l)||l.dimension()!=Level.OVERWORLD)return;
        CampaignData d=CampaignData.get(l);ResourceLocation item=BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem());
        if(d.active()&&d.inside(event.getPos())&&item.getPath().contains("wrench")){event.setCanceled(true);if(event.getEntity() instanceof ServerPlayer p)feedback(p,"Keep Create contraptions assembled as static defenses during the raid.");}
    }
}
