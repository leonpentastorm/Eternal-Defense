package dev.eternaldefense.beacon;

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

@Mod(EternalDefense.ID)
public final class EternalDefense {
    public static final String ID="eternal_defense";
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,ID);
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,ID);
    private static final DeferredRegister<CreativeModeTab> TABS=DeferredRegister.create(Registries.CREATIVE_MODE_TAB,ID);
    public static final RegistryObject<CreativeModeTab> DEFENSE_TAB=TABS.register("main",()->CreativeModeTab.builder().title(Component.literal("Eternal Defense")).icon(()->new ItemStack(EternalDefense.BEACON_ITEM.get())).displayItems((parameters,output)->CreativeCatalog.display(output)).build());
    private static final DeferredRegister<net.minecraft.world.level.block.entity.BlockEntityType<?>> ENTITIES=DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES,ID);
    public static final RegistryObject<Block> STRUCTURE_PART=BLOCKS.register("structure_part",DefenseStructures.Part::new);
    public static final RegistryObject<Block> BEACON=BLOCKS.register("defense_beacon",()->new DefenseBlock());
    /** The plank raiders lay over water and chasms (no item: it only comes from raids) */
    static final RegistryObject<Block> RAIDER_BRIDGE=BLOCKS.register("raider_bridge",RaidBridges.BridgeBlock::new);
    public static final RegistryObject<Item> BEACON_ITEM=ITEMS.register("defense_beacon",()->new BeaconItems.Placement(BEACON.get()));
    public static final RegistryObject<Item> CONTROLLER=ITEMS.register("beacon_controller",()->new BeaconItems.Controller());
    public static final RegistryObject<Item> GUIDE=ITEMS.register("field_guide",()->new BeaconItems.Guide());
    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<DefenseEntity>> BEACON_ENTITY=ENTITIES.register("defense_beacon",()->net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(DefenseEntity::new,BEACON.get()).build(null));
    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<WeaponPlatform.StationEntity>> STATION_ENTITY=ENTITIES.register("station",()->net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(WeaponPlatform.StationEntity::new,EternalDefense.GUN_PLATFORM.get(),EternalDefense.AMMO_PLATFORM.get(),EternalDefense.ATTACHMENT_PLATFORM.get(),EternalDefense.ARMOR_PLATFORM.get()).build(null));
    public static final RegistryObject<Item> ARDENT_ENERGY=ITEMS.register("ardent_energy",()->new Item(new Item.Properties().rarity(net.minecraft.world.item.Rarity.UNCOMMON)));
    public static final RegistryObject<Block> EXCHANGE_SHOP=BLOCKS.register("exchange_shop",ExchangeShop.ShopBlock::new);
    public static final RegistryObject<Item> EXCHANGE_SHOP_ITEM=ITEMS.register("exchange_shop",()->new BaseZone.ZoneItem(EXCHANGE_SHOP.get()));
    // Base support system
    static final DeferredRegister<net.minecraft.world.inventory.MenuType<?>> MENUS=DeferredRegister.create(ForgeRegistries.MENU_TYPES,ID);
    public static final RegistryObject<Block> MESS_HALL_I=kitchen("mess_hall_mk1",1),MESS_HALL_II=kitchen("mess_hall_mk2",2),MESS_HALL_III=kitchen("mess_hall_mk3",3),MESS_HALL_IV=kitchen("mess_hall_mk4",4);
    private static RegistryObject<Block> kitchen(String id,int tier){RegistryObject<Block> block=BLOCKS.register(id,()->new MessHall.HallBlock(tier));ITEMS.register(id,()->new KitchenBlock.KitchenItem(block.get()));return block;}
    public static final RegistryObject<Block> COOK_POT=BLOCKS.register("cook_pot",CookPot.PotBlock::new);
    public static final RegistryObject<Item> COOK_POT_ITEM=ITEMS.register("cook_pot",()->new KitchenBlock.KitchenItem(COOK_POT.get()));
    public static final RegistryObject<Block> BOWL_DISPENSER=BLOCKS.register("bowl_dispenser",BowlDispenser.DispenserBlock::new);
    public static final RegistryObject<Item> BOWL_DISPENSER_ITEM=ITEMS.register("bowl_dispenser",()->new KitchenBlock.KitchenItem(BOWL_DISPENSER.get()));
    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<BowlDispenser.DispenserEntity>> BOWL_DISPENSER_ENTITY=ENTITIES.register("bowl_dispenser",()->net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(BowlDispenser.DispenserEntity::new,BOWL_DISPENSER.get()).build(null));
    public static final RegistryObject<net.minecraft.world.inventory.MenuType<BowlDispenser.DispenserMenu>> BOWL_DISPENSER_MENU=MENUS.register("bowl_dispenser",()->net.minecraftforge.common.extensions.IForgeMenuType.create(BowlDispenser.DispenserMenu::client));
    public static final RegistryObject<Item> SANDWICH=ITEMS.register("prepared_sandwich",PreparedSandwich::new);
    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<MessHall.HallEntity>> MESS_HALL_ENTITY=ENTITIES.register("mess_hall",()->net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(MessHall.HallEntity::new,MESS_HALL_I.get(),MESS_HALL_II.get(),MESS_HALL_III.get(),MESS_HALL_IV.get()).build(null));
    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<CookPot.PotEntity>> COOK_POT_ENTITY=ENTITIES.register("cook_pot",()->net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(CookPot.PotEntity::new,COOK_POT.get()).build(null));
    public static final RegistryObject<net.minecraft.world.inventory.MenuType<MessHallMenu>> MESS_HALL_MENU=MENUS.register("mess_hall",()->net.minecraftforge.common.extensions.IForgeMenuType.create(MessHallMenu::client));
    // The Drink Station, the Brew Pot, the Cup Dispenser, the Paper Cup and the Latte
    public static final RegistryObject<Block> DRINK_STATION_I=drinks("drink_mixer_mk1",1),DRINK_STATION_II=drinks("drink_mixer_mk2",2),DRINK_STATION_III=drinks("drink_mixer_mk3",3),DRINK_STATION_IV=drinks("drink_mixer_mk4",4);
    private static RegistryObject<Block> drinks(String id,int tier){RegistryObject<Block> block=BLOCKS.register(id,()->new DrinkStation.StationBlock(tier));ITEMS.register(id,()->new KitchenBlock.KitchenItem(block.get()));return block;}
    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<DrinkStation.StationEntity>> DRINK_STATION_ENTITY=ENTITIES.register("drink_station",()->net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(DrinkStation.StationEntity::new,DRINK_STATION_I.get(),DRINK_STATION_II.get(),DRINK_STATION_III.get(),DRINK_STATION_IV.get()).build(null));
    public static final RegistryObject<net.minecraft.world.inventory.MenuType<DrinkStationMenu>> DRINK_STATION_MENU=MENUS.register("drink_station",()->net.minecraftforge.common.extensions.IForgeMenuType.create(DrinkStationMenu::client));
    public static final RegistryObject<Block> BREW_POT=BLOCKS.register("drink_pot",BrewPot.PotBlock::new);
    public static final RegistryObject<Item> BREW_POT_ITEM=ITEMS.register("drink_pot",()->new KitchenBlock.KitchenItem(BREW_POT.get()));
    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<BrewPot.PotEntity>> BREW_POT_ENTITY=ENTITIES.register("drink_pot",()->net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(BrewPot.PotEntity::new,BREW_POT.get()).build(null));
    public static final RegistryObject<Block> CUP_DISPENSER=BLOCKS.register("cup_dispenser",CupDispenser.DispenserBlock::new);
    public static final RegistryObject<Item> CUP_DISPENSER_ITEM=ITEMS.register("cup_dispenser",()->new KitchenBlock.KitchenItem(CUP_DISPENSER.get()));
    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<CupDispenser.DispenserEntity>> CUP_DISPENSER_ENTITY=ENTITIES.register("cup_dispenser",()->net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(CupDispenser.DispenserEntity::new,CUP_DISPENSER.get()).build(null));
    public static final RegistryObject<net.minecraft.world.inventory.MenuType<CupDispenser.DispenserMenu>> CUP_DISPENSER_MENU=MENUS.register("cup_dispenser",()->net.minecraftforge.common.extensions.IForgeMenuType.create(CupDispenser.DispenserMenu::client));
    public static final RegistryObject<Item> PAPER_CUP=ITEMS.register("drink_cup_empty",CupDispenser.PaperCup::new);
    public static final RegistryObject<Item> LATTE=ITEMS.register("drink_cup",Latte::new);
    public static final RegistryObject<Block> MILK_DISPENSER=BLOCKS.register("milk_dispenser",MilkDispenser.DispenserBlock::new);
    public static final RegistryObject<Item> MILK_DISPENSER_ITEM=ITEMS.register("milk_dispenser",()->new KitchenBlock.KitchenItem(MILK_DISPENSER.get()));
    public static final RegistryObject<Item> MILK_BOTTLE=ITEMS.register("milk_bottle",MilkDispenser.MilkBottle::new);
    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<MilkDispenser.DispenserEntity>> MILK_DISPENSER_ENTITY=ENTITIES.register("milk_dispenser",()->net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(MilkDispenser.DispenserEntity::new,MILK_DISPENSER.get()).build(null));
    public static final RegistryObject<net.minecraft.world.inventory.MenuType<MilkDispenser.DispenserMenu>> MILK_DISPENSER_MENU=MENUS.register("milk_dispenser",()->net.minecraftforge.common.extensions.IForgeMenuType.create(MilkDispenser.DispenserMenu::client));
    // The MRE Station, the MRE and Marines Fuel
    public static final RegistryObject<Block> MRE_STATION=BLOCKS.register("mre_maker",MreStation.StationBlock::new);
    public static final RegistryObject<Item> MRE_STATION_ITEM=ITEMS.register("mre_maker",()->new KitchenBlock.KitchenItem(MRE_STATION.get()));
    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<MreStation.StationEntity>> MRE_STATION_ENTITY=ENTITIES.register("mre_maker",()->net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(MreStation.StationEntity::new,MRE_STATION.get()).build(null));
    public static final RegistryObject<net.minecraft.world.inventory.MenuType<MreStation.StationMenu>> MRE_STATION_MENU=MENUS.register("mre_maker",()->net.minecraftforge.common.extensions.IForgeMenuType.create(MreStation.StationMenu::client));
    // The Logistics Center Hub, a 3 x 3 x 3 depot of 270 slots in the Home Zone; its inventory lives in HubData
    public static final RegistryObject<Block> LOGISTICS_HUB=BLOCKS.register("logistics_hub",LogisticsHub.HubBlock::new);
    public static final RegistryObject<Block> LOGISTICS_HUB_PART=BLOCKS.register("logistics_hub_part",LogisticsHub.PartBlock::new);
    public static final RegistryObject<Item> LOGISTICS_HUB_ITEM=ITEMS.register("logistics_hub",()->new LogisticsHub.HubItem(LOGISTICS_HUB.get()));
    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<LogisticsHub.HubEntity>> LOGISTICS_HUB_ENTITY=ENTITIES.register("logistics_hub",()->net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(LogisticsHub.HubEntity::new,LOGISTICS_HUB.get()).build(null));
    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<net.minecraft.world.level.block.entity.BlockEntity>> LOGISTICS_HUB_PART_ENTITY=ENTITIES.register("logistics_hub_part",()->net.minecraft.world.level.block.entity.BlockEntityType.Builder.of((pos,state)->LogisticsHub.partFactory.create(pos,state),LOGISTICS_HUB_PART.get()).build(null));
    public static final RegistryObject<net.minecraft.world.inventory.MenuType<HubMenu>> LOGISTICS_HUB_MENU=MENUS.register("logistics_hub",()->net.minecraftforge.common.extensions.IForgeMenuType.create(HubMenu::client));
    // The Loadout Station, its coin and flare, and the capsule the flare calls in
    public static final RegistryObject<Block> LOADOUT_STATION=BLOCKS.register("loadout_station",LoadoutStation.StationBlock::new);
    public static final RegistryObject<Block> LOADOUT_STATION_PART=BLOCKS.register("loadout_station_part",LoadoutStation.PartBlock::new);
    public static final RegistryObject<Item> LOADOUT_STATION_ITEM=ITEMS.register("loadout_station",()->new LoadoutStation.StationItem(LOADOUT_STATION.get()));
    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<LoadoutStation.StationEntity>> LOADOUT_STATION_ENTITY=ENTITIES.register("loadout_station",()->net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(LoadoutStation.StationEntity::new,LOADOUT_STATION.get()).build(null));
    public static final RegistryObject<net.minecraft.world.inventory.MenuType<LoadoutMenu>> LOADOUT_MENU=MENUS.register("loadout_station",()->net.minecraftforge.common.extensions.IForgeMenuType.create(LoadoutMenu::client));
    public static final RegistryObject<Item> LOADOUT_COIN=ITEMS.register("loadout_coin",LoadoutCoin::new);
    public static final RegistryObject<Item> LOADOUT_FLARE=ITEMS.register("loadout_flare",()->new SupportFlares.FlareItem(SupportCalls.Kind.LOADOUT));
    public static final RegistryObject<Block> LOADOUT_CAPSULE=BLOCKS.register("loadout_capsule",LoadoutCapsule.CapsuleBlock::new);
    public static final RegistryObject<Block> LOADOUT_CAPSULE_PART=BLOCKS.register("loadout_capsule_part",LoadoutCapsule.CapsulePart::new);
    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<LoadoutCapsule.CapsuleEntity>> LOADOUT_CAPSULE_ENTITY=ENTITIES.register("loadout_capsule",()->net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(LoadoutCapsule.CapsuleEntity::new,LOADOUT_CAPSULE.get()).build(null));
    public static final RegistryObject<net.minecraft.world.inventory.MenuType<LoadoutCapsule.CapsuleMenu>> LOADOUT_CAPSULE_MENU=MENUS.register("loadout_capsule",()->net.minecraftforge.common.extensions.IForgeMenuType.create(LoadoutCapsule.CapsuleMenu::client));
    public static final RegistryObject<Item> MRE=ITEMS.register("mre",Mre::new);
    public static final RegistryObject<Item> MARINES_FUEL=ITEMS.register("marines_fuel",Mre.MarinesFuel::new);
    public static final RegistryObject<Block> SUPPORT_PLATFORM=BLOCKS.register("support_platform",SupportPlatform.PlatformBlock::new);
    public static final RegistryObject<Item> SUPPORT_PLATFORM_ITEM=ITEMS.register("support_platform",()->new SupportPlatform.PlatformItem(SUPPORT_PLATFORM.get()));
    public static final RegistryObject<Block> SUPPORT_CANNON=BLOCKS.register("support_cannon",SupportCannon.CannonBlock::new);
    public static final RegistryObject<Item> SUPPORT_CANNON_ITEM=ITEMS.register("support_cannon",()->new SupportCannon.CannonItem(SUPPORT_CANNON.get()));
    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<SupportPlatform.PlatformEntity>> SUPPORT_PLATFORM_ENTITY=ENTITIES.register("support_platform",()->net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(SupportPlatform.PlatformEntity::new,SUPPORT_PLATFORM.get()).build(null));
    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<SupportCannon.CannonEntity>> CANNON_ENTITY=ENTITIES.register("support_cannon",()->net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(SupportCannon.CannonEntity::new,SUPPORT_CANNON.get()).build(null));
    public static final RegistryObject<net.minecraft.world.inventory.MenuType<SupportPlatform.PlatformMenu>> SUPPORT_MENU=MENUS.register("support_platform",()->net.minecraftforge.common.extensions.IForgeMenuType.create(SupportPlatform.PlatformMenu::client));
    // Tactical Operations
    public static final RegistryObject<Block> COMMAND_TABLE=BLOCKS.register("command_table",TacticalBlocks.TableBlock::new);
    public static final RegistryObject<Item> COMMAND_TABLE_ITEM=ITEMS.register("command_table",()->new TacticalBlocks.TableItem(COMMAND_TABLE.get()));
    public static final RegistryObject<Block> SATELLITE_BEACON=BLOCKS.register("satellite_beacon",TacticalBlocks.SatelliteBlock::new);
    public static final RegistryObject<Item> SATELLITE_BEACON_ITEM=ITEMS.register("satellite_beacon",()->new TacticalBlocks.SatelliteItem(SATELLITE_BEACON.get()));
    public static final RegistryObject<net.minecraft.world.inventory.MenuType<TacticalBlocks.TableMenu>> TABLE_MENU=MENUS.register("command_table",()->net.minecraftforge.common.extensions.IForgeMenuType.create(TacticalBlocks.TableMenu::client));
    // Forward Operating Bases
    public static final RegistryObject<Block> FOB_BEACON=BLOCKS.register("fob_beacon",FobBlocks.FobBlock::new);
    public static final RegistryObject<Item> FOB_BEACON_ITEM=ITEMS.register("fob_beacon",()->new FobBlocks.FobItem(FOB_BEACON.get()));
    // The supply rail
    public static final RegistryObject<Block> DELIVERY_GATE=BLOCKS.register("supply_delivery_gate",()->new RailGates.GateBlock(RailGates.Kind.DELIVERY));
    public static final RegistryObject<Block> RECEIVING_GATE=BLOCKS.register("supply_receiving_gate",()->new RailGates.GateBlock(RailGates.Kind.RECEIVING));
    public static final RegistryObject<Block> GATE_PART=BLOCKS.register("gate_part",RailGates.Part::new);
    /** The left column of each gate: it draws the left half; no item. */
    public static final RegistryObject<Block> DELIVERY_GATE_LEFT=BLOCKS.register("supply_delivery_gate_left",()->new RailGates.Column(RailGates.Kind.DELIVERY));
    public static final RegistryObject<Block> RECEIVING_GATE_LEFT=BLOCKS.register("supply_receiving_gate_left",()->new RailGates.Column(RailGates.Kind.RECEIVING));
    public static final RegistryObject<Item> DELIVERY_GATE_ITEM=ITEMS.register("supply_delivery_gate",()->new RailGates.GateItem(DELIVERY_GATE.get(),RailGates.Kind.DELIVERY));
    public static final RegistryObject<Item> RECEIVING_GATE_ITEM=ITEMS.register("supply_receiving_gate",()->new RailGates.GateItem(RECEIVING_GATE.get(),RailGates.Kind.RECEIVING));
    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<RailGates.GateEntity>> GATE_ENTITY=ENTITIES.register("supply_gate",()->net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(RailGates.GateEntity::new,DELIVERY_GATE.get(),RECEIVING_GATE.get()).build(null));
    public static final RegistryObject<net.minecraft.world.inventory.MenuType<RailGates.GateMenu>> GATE_MENU=MENUS.register("supply_gate",()->net.minecraftforge.common.extensions.IForgeMenuType.create(RailGates.GateMenu::client));
    // The Tactical Enchanting Table and the Warlord Seal
    public static final RegistryObject<Block> ENCHANTING_TABLE=BLOCKS.register("tactical_enchanting_table",TacticalEnchanting.TableBlock::new);
    public static final RegistryObject<Item> ENCHANTING_TABLE_ITEM=ITEMS.register("tactical_enchanting_table",()->new TacticalEnchanting.TableItem(ENCHANTING_TABLE.get()));
    /** The moving parts of the blocks that had no block entity (see AnimatedParts and Animations). */
    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<AnimatedParts.PartsEntity>> ANIMATED_PARTS_ENTITY=ENTITIES.register("animated_parts",()->net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(AnimatedParts.PartsEntity::new,
        EternalDefense.FOB_BEACON.get(),EternalDefense.SATELLITE_BEACON.get(),EternalDefense.COMMAND_TABLE.get(),EternalDefense.ENCHANTING_TABLE.get()).build(null));
    public static final RegistryObject<Item> WARLORD_SEAL=ITEMS.register("warlord_seal",TacticalEnchanting.SealItem::new);
    public static final RegistryObject<net.minecraft.world.inventory.MenuType<TacticalEnchanting.EnchantMenu>> ENCHANT_MENU=MENUS.register("tactical_enchanting_table",()->net.minecraftforge.common.extensions.IForgeMenuType.create(TacticalEnchanting.EnchantMenu::client));
    // The Supply Catapult: the onager, in a FOB's village; the specialty seller's deliveries
    public static final RegistryObject<Block> SUPPLY_CATAPULT=BLOCKS.register("supply_catapult",SupplyCatapult.CatapultBlock::new);
    public static final RegistryObject<Item> SUPPLY_CATAPULT_ITEM=ITEMS.register("supply_catapult",()->new SupplyCatapult.CatapultItem(SUPPLY_CATAPULT.get()));
    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<SupplyCatapult.CatapultEntity>> CATAPULT_ENTITY=ENTITIES.register("supply_catapult",()->net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(SupplyCatapult.CatapultEntity::new,SUPPLY_CATAPULT.get()).build(null));
    public static final RegistryObject<net.minecraft.world.inventory.MenuType<FobBlocks.FobMenu>> FOB_MENU=MENUS.register("fob_beacon",()->net.minecraftforge.common.extensions.IForgeMenuType.create(FobBlocks.FobMenu::client));
    public static final RegistryObject<Item> SUPPLY_FLARE=ITEMS.register("support_flare",()->new SupportFlares.FlareItem(SupportCalls.Kind.SUPPLY)),RETURN_FLARE=ITEMS.register("return_flare",()->new SupportFlares.FlareItem(SupportCalls.Kind.RETURN)),FIRE_FLARE=ITEMS.register("fire_support_flare",()->new SupportFlares.FlareItem(SupportCalls.Kind.FIRE));
    public static final RegistryObject<Item> RADIO=ITEMS.register("handheld_radio",HandheldRadio.RadioItem::new);
    public static final RegistryObject<net.minecraft.world.inventory.MenuType<HandheldRadio.RadioMenu>> RADIO_MENU=MENUS.register("handheld_radio",()->net.minecraftforge.common.extensions.IForgeMenuType.create(HandheldRadio.RadioMenu::client));
    // The defense kit: poles, wire, shock boxes, towers, walls, traps, the spotlight and the Defense Depot (the two dynamos are in the Create compat)
    public static final RegistryObject<Block> STRUCTURE_PORT=BLOCKS.register("structure_port",DefenseStructures.Port::new);
    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<DefenseStructures.PortEntity>> PORT_ENTITY=ENTITIES.register("structure_port",()->net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(DefenseStructures.PortEntity::new,STRUCTURE_PORT.get()).build(null));
    public static final RegistryObject<Block> POWER_POLE=kit("power_pole",PowerPoles.PoleBlock::new);
    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<PowerPoles.PoleEntity>> POLE_ENTITY=ENTITIES.register("power_pole",()->net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(PowerPoles.PoleEntity::new,POWER_POLE.get()).build(null));
    public static final RegistryObject<Item> ELECTRIC_WIRE=ITEMS.register("electric_wire",PowerPoles.WireItem::new);
    public static final RegistryObject<Block> SHOCK_BOX=kit("shock_box",ShockBoxes.BoxBlock::new);
    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<ShockBoxes.BoxEntity>> SHOCK_BOX_ENTITY=ENTITIES.register("shock_box",()->net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(ShockBoxes.BoxEntity::new,SHOCK_BOX.get()).build(null));
    public static final RegistryObject<Block> SANDBAG=kit("sandbag",()->new DefenseWalls.Wall("sandbag",4,net.minecraft.world.level.block.SoundType.WOOL)),
        BARRICADE=kit("barricade",()->new DefenseWalls.Wall("barricade",5,net.minecraft.world.level.block.SoundType.WOOD)),BARBED_WIRE=kit("barbed_wire",DefenseWalls.BarbedWire::new);
    public static final java.util.Map<String,RegistryObject<Block>> TURRETS=turrets();
    private static java.util.Map<String,RegistryObject<Block>> turrets(){
        var out=new java.util.LinkedHashMap<String,RegistryObject<Block>>();
        for(var line:Turrets.Line.values())for(int mk=1;mk<=line.marks;mk++){int m=mk;String id=Turrets.id(line,mk);RegistryObject<Block> block=BLOCKS.register(id,()->Turrets.block(line,m));ITEMS.register(id,()->new Turrets.TurretItem(block.get(),line,m));out.put(id,block);}
        return java.util.Collections.unmodifiableMap(out);
    }
    public static final RegistryObject<net.minecraft.world.inventory.MenuType<Turrets.TurretMenu>> TURRET_MENU=MENUS.register("turret",()->net.minecraftforge.common.extensions.IForgeMenuType.create(Turrets.TurretMenu::client));
    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<Turrets.TurretEntity>> TURRET_ENTITY=ENTITIES.register("turret",()->net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(Turrets.TurretEntity::new,TURRETS.values().stream().map(RegistryObject::get).toArray(Block[]::new)).build(null));
    public static final RegistryObject<Item> MG_AMMO=ITEMS.register("mg_turret_ammo",()->new Turrets.AmmoItem(Turrets.Line.MG)),SHOTGUN_AMMO=ITEMS.register("shotgun_turret_ammo",()->new Turrets.AmmoItem(Turrets.Line.SHOTGUN)),
        FLAME_FUEL=ITEMS.register("flame_turret_fuel",()->new Turrets.AmmoItem(Turrets.Line.FLAME)),CANNON_SHELLS=ITEMS.register("heavy_cannon_shell",()->new Turrets.AmmoItem(Turrets.Line.CANNON));
    public static final RegistryObject<Block> PUNJI_TRAP=kit("punji_trap",PunjiTrap.TrapBlock::new),SPOTLIGHT=kit("spotlight",Spotlight.LightBlock::new),
        SPOTLIGHT_GLOW=BLOCKS.register("spotlight_glow",Spotlight.Glow::new);
    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<PunjiTrap.TrapEntity>> PUNJI_ENTITY=ENTITIES.register("punji_trap",()->net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(PunjiTrap.TrapEntity::new,PUNJI_TRAP.get()).build(null));
    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<Spotlight.LightEntity>> SPOTLIGHT_ENTITY=ENTITIES.register("spotlight",()->net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(Spotlight.LightEntity::new,SPOTLIGHT.get()).build(null));
    public static final RegistryObject<Block> DEFENSE_DEPOT=kit("defense_depot",DefenseDepot.DepotBlock::new);
    public static final RegistryObject<net.minecraft.world.inventory.MenuType<DefenseDepot.DepotMenu>> DEPOT_MENU=MENUS.register("defense_depot",()->net.minecraftforge.common.extensions.IForgeMenuType.create(DefenseDepot.DepotMenu::client));
    private static RegistryObject<Block> kit(String id,java.util.function.Supplier<Block> block){RegistryObject<Block> out=BLOCKS.register(id,block);ITEMS.register(id,()->new PowerKit.KitItem(out.get(),id));return out;}
    /** The Age armor sets, Frontier to Exotic, helmet to boots */
    public static final List<RegistryObject<Item>> ARMOR_SETS=armorSets();
    private static List<RegistryObject<Item>> armorSets(){
        var out=new java.util.ArrayList<RegistryObject<Item>>();
        for(var set:ArmorSetMaterials.values())for(var type:net.minecraft.world.item.ArmorItem.Type.values())out.add(ITEMS.register(set.key()+"_"+type.getName(),()->new ArmorSetItem(set,type)));
        return List.copyOf(out);
    }
    public static final RegistryObject<Item> PLATING=part("reinforced_plating"),LOGISTICS=part("logistics_module"),COIL=part("resonance_coil"),REPAIR=part("restoration_matrix"),AMMO_COIN=part("universal_ammo_coin");
    // Factory parts
    public static final RegistryObject<Item> PRESSED_RECEIVER=part("pressed_receiver"),BRASS_RECEIVER=part("brass_receiver"),HARDENED_RECEIVER=part("hardened_receiver"),EXOTIC_RECEIVER=part("exotic_receiver"),
        INCOMPLETE_BRASS_RECEIVER=part("incomplete_brass_receiver"),INCOMPLETE_HARDENED_RECEIVER=part("incomplete_hardened_receiver"),HARDENED_ALLOY=part("hardened_alloy"),CARTRIDGE_CASE=part("cartridge_case"),PROPELLANT=part("propellant");
    public static final RegistryObject<Block> GUN_PLATFORM=platform("gun"),AMMO_PLATFORM=platform("ammo"),ATTACHMENT_PLATFORM=platform("attachment"),ARMOR_PLATFORM=platform("armor");
    private static RegistryObject<Block> platform(String kind){
        RegistryObject<Block> block=BLOCKS.register(kind+"_platform",()->new WeaponPlatform.Station(kind));
        ITEMS.register(kind+"_platform",()->new WeaponPlatform.StationItem(block.get()));return block;
    }
    public static final List<RegistryObject<Block>> RACKS=net.minecraftforge.fml.ModList.get().isLoaded("eternal_defense_displays")?dev.eternaldefense.displays.GunDisplays.RACKS:List.of();
    private static RegistryObject<Item> part(String id){return ITEMS.register(id,()->new Item(new Item.Properties()));}
    private static final DeferredRegister<EntityType<?>> OBJECTIVES=DeferredRegister.create(ForgeRegistries.ENTITY_TYPES,ID);
    static final RegistryObject<EntityType<BeaconCombat.Objective>> OBJECTIVE=OBJECTIVES.register("beacon_objective",()->EntityType.Builder.<BeaconCombat.Objective>of(BeaconCombat.Objective::new,MobCategory.MISC).sized(1f,1f).clientTrackingRange(64).build(ID+":beacon_objective"));
    public static final RegistryObject<EntityType<SupportFlares.FlareEntity>> FLARE=OBJECTIVES.register("support_flare",()->EntityType.Builder.<SupportFlares.FlareEntity>of(SupportFlares.FlareEntity::new,MobCategory.MISC).sized(.25f,.25f).clientTrackingRange(10).updateInterval(5).build(ID+":support_flare"));
    public static final RegistryObject<EntityType<SupportCrate.ParcelEntity>> PARCEL=OBJECTIVES.register("support_parcel",()->EntityType.Builder.<SupportCrate.ParcelEntity>of(SupportCrate.ParcelEntity::new,MobCategory.MISC).sized(.9f,.9f).clientTrackingRange(8).updateInterval(3).build(ID+":support_parcel"));
    /** The red gate a stuck raider channels beside (see RaiderGates) */
    /** The invisible stand-in on a defense block or door that raiders aim at (see RaidTargets) */
    static final RegistryObject<EntityType<RaidTargets.Point>> DEFENSE_POINT=OBJECTIVES.register("defense_point",()->EntityType.Builder.<RaidTargets.Point>of(RaidTargets.Point::new,MobCategory.MISC).sized(1f,1f).noSave().noSummon().fireImmune().clientTrackingRange(8).updateInterval(20).build(ID+":defense_point"));
    static final RegistryObject<EntityType<RaiderGate>> RAIDER_GATE=OBJECTIVES.register("raider_gate",()->EntityType.Builder.<RaiderGate>of(RaiderGate::new,MobCategory.MISC).sized(1.2f,2.2f).noSave().noSummon().fireImmune().clientTrackingRange(10).updateInterval(20).build(ID+":raider_gate"));
    /** A shell, bomb or starshell falling onto a fire support flare (see Ordnance) */
    static final RegistryObject<EntityType<Ordnance>> ORDNANCE=OBJECTIVES.register("ordnance",()->EntityType.Builder.<Ordnance>of(Ordnance::new,MobCategory.MISC).sized(.5f,1f).noSave().noSummon().fireImmune().clientTrackingRange(10).updateInterval(20).build(ID+":ordnance"));
    /** The box a Supply Catapult throws: only to be seen, never saved. */
    static final RegistryObject<EntityType<SupplyCatapult.ThrownSupply>> THROWN_SUPPLY=OBJECTIVES.register("thrown_supply",()->EntityType.Builder.<SupplyCatapult.ThrownSupply>of(SupplyCatapult.ThrownSupply::new,MobCategory.MISC).sized(.5f,.5f).noSave().noSummon().fireImmune().clientTrackingRange(10).updateInterval(1).build(ID+":thrown_supply"));
    /** A Loadout Capsule falling under its parachute (see LoadoutCapsule) */
    /** A Heavy Cannon shell in flight (see CannonShell) */
    static final RegistryObject<EntityType<CannonShell>> TURRET_SHELL=OBJECTIVES.register("turret_shell",()->EntityType.Builder.<CannonShell>of(CannonShell::new,MobCategory.MISC).sized(.4f,.4f).noSave().noSummon().fireImmune().clientTrackingRange(10).updateInterval(40).build(ID+":turret_shell"));
    public static final RegistryObject<EntityType<LoadoutCapsule.Drop>> CAPSULE_DROP=OBJECTIVES.register("loadout_capsule_drop",()->EntityType.Builder.<LoadoutCapsule.Drop>of(LoadoutCapsule.Drop::new,MobCategory.MISC).sized(.9f,2f).fireImmune().clientTrackingRange(10).updateInterval(3).build(ID+":loadout_capsule_drop"));
    private static int clock;
    public EternalDefense() {
        net.minecraftforge.common.ForgeMod.enableMilkFluid();
        var bus=FMLJavaModLoadingContext.get().getModEventBus();BLOCKS.register(bus);MealEffects.register(bus);ITEMS.register(bus);ENTITIES.register(bus);TABS.register(bus);OBJECTIVES.register(bus);MENUS.register(bus);DefenseSounds.SOUNDS.register(bus);bus.addListener((net.minecraftforge.event.entity.EntityAttributeCreationEvent e)->{e.put(OBJECTIVE.get(),Mob.createMobAttributes().add(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH,20).build());e.put(DEFENSE_POINT.get(),Mob.createMobAttributes().add(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH,20).build());});BeaconNetwork.init();
        // With Create: mechanical arms may use the Logistics Center Hub
        if(net.minecraftforge.fml.ModList.get().isLoaded("create"))
            try{Class.forName("dev.eternaldefense.beacon.CreateArmCompat").getMethod("register",net.minecraftforge.eventbus.api.IEventBus.class).invoke(null,bus);}
            catch(ReflectiveOperationException|LinkageError e){com.mojang.logging.LogUtils.getLogger().warn("[Logistics Hub] mechanical arms cannot use the Hub: {}",e.toString());}
        bus.addListener((BuildCreativeModeTabContentsEvent e)->{if(e.getTabKey()==CreativeModeTabs.FUNCTIONAL_BLOCKS){e.accept(BEACON_ITEM);e.accept(CONTROLLER);e.accept(GUIDE);e.accept(PLATING);e.accept(LOGISTICS);e.accept(COIL);e.accept(REPAIR);e.accept(GUN_PLATFORM.get());e.accept(AMMO_PLATFORM.get());e.accept(ATTACHMENT_PLATFORM.get());e.accept(ARMOR_PLATFORM.get());}});
        MinecraftForge.EVENT_BUS.register(this);MinecraftForge.EVENT_BUS.register(new ArdentEnergy());MinecraftForge.EVENT_BUS.register(new Elites.Events());MinecraftForge.EVENT_BUS.register(new SupportFlares.Safety());MinecraftForge.EVENT_BUS.register(new SupportHud.Login());MinecraftForge.EVENT_BUS.register(new RaidTypes.Events());MinecraftForge.EVENT_BUS.register(new RaidAdaptation.Events());MinecraftForge.EVENT_BUS.register(new FireMarks.MarkEvents());MinecraftForge.EVENT_BUS.register(new EndlessEffects.EndlessEvents());MinecraftForge.EVENT_BUS.register(new ReturnZone());MinecraftForge.EVENT_BUS.register(new FactoryTiers.Events());MinecraftForge.EVENT_BUS.register(new RecipeRules.Events());net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.COMMON,DefenseConfig.SPEC,"eternal-defense-common.toml");net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.CLIENT,DefenseClientConfig.SPEC,"eternal-defense-client.toml");MinecraftForge.EVENT_BUS.register(new WeaponPlatform());MinecraftForge.EVENT_BUS.register(new CreateUnlocks());
        MinecraftForge.EVENT_BUS.register(new LoadoutPrint.Events());MinecraftForge.EVENT_BUS.register(new StructureMigration());MinecraftForge.EVENT_BUS.register(new HubNotices.NoticeEvents());MinecraftForge.EVENT_BUS.register(new BaseScoring());MinecraftForge.EVENT_BUS.register(new SpecialForcesRaids());MinecraftForge.EVENT_BUS.register(new BeaconCombat());
        MinecraftForge.EVENT_BUS.register(new BeaconItems.RecoveryCombat());MinecraftForge.EVENT_BUS.register(new HuntWarband.Events());MinecraftForge.EVENT_BUS.register(new IngredientTraits());MinecraftForge.EVENT_BUS.register(new PlayerMeals.MealEvents());MinecraftForge.EVENT_BUS.register(new PlayerDrinks.DrinkEvents());MinecraftForge.EVENT_BUS.register(new DrinkStation.GuardEvents());MinecraftForge.EVENT_BUS.register(new Fobs.FobEvents());MinecraftForge.EVENT_BUS.register(new Routes.RouteEvents());MinecraftForge.EVENT_BUS.register(new ArmorSetBonus.SetEvents());MinecraftForge.EVENT_BUS.register(new PowerGrid.GridEvents());
        bus.addListener((net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent e)->e.enqueueWork(MealGunCompat::register));
    }
    public static final class DefenseEntity extends BlockEntity {
        public DefenseEntity(BlockPos pos,BlockState state){super(BEACON_ENTITY.get(),pos,state);}
        @Override public net.minecraft.world.phys.AABB getRenderBoundingBox(){return new net.minecraft.world.phys.AABB(worldPosition.getX()-1,worldPosition.getY(),worldPosition.getZ()-1,worldPosition.getX()+2,worldPosition.getY()+512,worldPosition.getZ()+2);}
    }
    public static final class DefenseBlock extends Block implements EntityBlock {
        public DefenseBlock(){super(Properties.of().strength(-1f,3600000f).lightLevel(s->15).noOcclusion());registerDefaultState(stateDefinition.any().setValue(DefenseStructures.MK,1).setValue(DefenseStructures.FACING,Direction.NORTH));}
        @Override protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block,BlockState> b){b.add(DefenseStructures.MK,DefenseStructures.FACING);}
        @Override public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext c){return defaultBlockState().setValue(DefenseStructures.FACING,c.getHorizontalDirection().getOpposite());}
        @Override public net.minecraft.world.level.material.PushReaction getPistonPushReaction(BlockState s){return net.minecraft.world.level.material.PushReaction.BLOCK;}
        @Override public void onRemove(BlockState s,Level l,BlockPos p,BlockState next,boolean moving){if(!next.is(this))DefenseStructures.remove(l,p);super.onRemove(s,l,p,next,moving);}
        @Override public net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,net.minecraft.world.phys.shapes.CollisionContext context){return DefenseStructures.cell(state,0,0,0);}
        @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new DefenseEntity(pos,state);}
        @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,net.minecraft.world.entity.LivingEntity placer,ItemStack stack) {
            if(!(level instanceof ServerLevel server)||!(placer instanceof ServerPlayer player))return;
            CampaignData d=CampaignData.get(server);
            if(level.dimension()!=Level.OVERWORLD||!d.phase.equals("unplaced")) {
                server.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
                player.sendSystemMessage(Component.literal("One shared beacon per world, placed in the Overworld."));return;
            }
            d.beacon=pos.immutable();d.phase="preparation";d.campaignSerial++;d.participants.add(player.getUUID());d.health=d.maximumHealth();d.setDirty();
            if(!d.introCompleted){announce(server,"INTRODUCTORY RAID: starts now at reward tier 0. Survive to earn your Weapon Platform.");begin(server,d);}else announce(server,"Beacon planted. Scheduled raids resume after ten active days, at night. Right-click for the control panel.");BeaconNetwork.syncNearby(server,d);
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
        if(d.phase.equals("unplaced")&&!p.getPersistentData().getBoolean("eternalDefenseGranted")) {
            if(p.getInventory().add(new ItemStack(BEACON_ITEM.get())))p.getPersistentData().putBoolean("eternalDefenseGranted",true);
        }
        if(!d.phase.equals("unplaced"))d.participants.add(p.getUUID());d.setDirty();
        grantSupport(p);BeaconNetwork.sendState(p,"","","");
        grantStarter(p);
        Hunts.login(p);   // A mission running: its map and Return Flare if missing, and the mission line
    }
    static void grantSupport(ServerPlayer p) {
        if(p.getPersistentData().getBoolean("eternalSupportGranted03"))return;
        give(p,new ItemStack(GUIDE.get()));give(p,new ItemStack(CONTROLLER.get()));p.getPersistentData().putBoolean("eternalSupportGranted03",true);
    }
    static void give(ServerPlayer p,ItemStack stack){if(!p.getInventory().add(stack)&&!stack.isEmpty())p.drop(stack,false);}
    /** The starter gun: TaCZ's own .22 revolver, so it exists with TaCZ alone. */
    static final ResourceLocation STARTER_GUN=new ResourceLocation("tacz:taurus943"),STARTER_AMMO=new ResourceLocation("tacz:22wmr");
    static final int STARTER_ROUNDS=64;
    static void grantStarter(ServerPlayer p) {
        if(p.getPersistentData().getBoolean("eternalStarterGranted"))return;
        Item gunItem=BuiltInRegistries.ITEM.get(new ResourceLocation("tacz:modern_kinetic_gun"));
        Item ammoItem=BuiltInRegistries.ITEM.get(new ResourceLocation("tacz:ammo"));
        if(gunItem==Items.AIR||ammoItem==Items.AIR||com.tacz.guns.api.TimelessAPI.getCommonGunIndex(STARTER_GUN).isEmpty())return;
        try {
            ItemStack gun=new ItemStack(gunItem),ammo=new ItemStack(ammoItem,STARTER_ROUNDS);
            gunItem.getClass().getMethod("setGunId",ItemStack.class,ResourceLocation.class).invoke(gunItem,gun,STARTER_GUN);
            gunItem.getClass().getMethod("setCurrentAmmoCount",ItemStack.class,int.class).invoke(gunItem,gun,0);
            gun.getOrCreateTag().putString("GunFireMode","SEMI");
            ammoItem.getClass().getMethod("setAmmoId",ItemStack.class,ResourceLocation.class).invoke(ammoItem,ammo,STARTER_AMMO);
            if(!p.getInventory().add(gun)&&!gun.isEmpty())p.drop(gun,false);
            if(!p.getInventory().add(ammo)&&!ammo.isEmpty())p.drop(ammo,false);
            p.getPersistentData().putBoolean("eternalStarterGranted",true);
            feedback(p,"[Eternal Defense] Starter kit: a .22 Model 943 revolver and 64 rounds of .22 WMR. Reload with your reload key; it holds 8 rounds.");
        } catch(ReflectiveOperationException ex) {
            com.mojang.logging.LogUtils.getLogger().error("Eternal Defense starter kit could not access the installed TaCZ item API",ex);
        }
    }
    @SubscribeEvent public void clonePlayer(PlayerEvent.Clone e) {
        if(e.getOriginal().getPersistentData().getBoolean("eternalDefenseGranted"))e.getEntity().getPersistentData().putBoolean("eternalDefenseGranted",true);
        if(e.getOriginal().getPersistentData().getBoolean("eternalStarterGranted"))e.getEntity().getPersistentData().putBoolean("eternalStarterGranted",true);
        if(e.getOriginal().getPersistentData().getBoolean("eternalSupportGranted03"))e.getEntity().getPersistentData().putBoolean("eternalSupportGranted03",true);
    }
    @SubscribeEvent public void serverStopped(net.minecraftforge.event.server.ServerStoppedEvent e){clock=0;RaidRescue.reset();BeaconActions.clear();}
    private static final com.mojang.brigadier.suggestion.SuggestionProvider<net.minecraft.commands.CommandSourceStack> RAID_TYPE_SUGGEST=(c,b)->{b.suggest("normal");for(String t:RaidTypes.SPECIAL)b.suggest(t);return b.buildFuture();};
    private static boolean raidTypeOk(String t){return t.equals("normal")||RaidTypes.special(t);}
    @SubscribeEvent public void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("eternaldefense")
            .executes(c->{BeaconNetwork.open(c.getSource().getPlayerOrException(),"menu","");return 1;})
            .then(Commands.literal("boundary").executes(c->{var d=CampaignData.get(c.getSource().getServer().overworld());d.showBoundary=!d.showBoundary;d.setDirty();return 1;}))
            .then(Commands.literal("claim").executes(c->{claim(c.getSource().getPlayerOrException(),CampaignData.get(c.getSource().getServer().overworld()));return 1;}))
            .then(Commands.literal("upgrade").then(Commands.argument("branch",StringArgumentType.word()).suggests((c,b)->{for(String s:List.of("core","logistics","defense","restoration","reconnaissance","vertical"))b.suggest(s);return b.buildFuture();}).executes(c->upgrade(c.getSource().getPlayerOrException(),StringArgumentType.getString(c,"branch")))))
            .then(Commands.literal("repair").executes(c->repair(c.getSource().getPlayerOrException())))
            .then(Hunts.command())
            .then(Fobs.command())
            .then(Commands.literal("test-gate").requires(s->s.hasPermission(2)).executes(c->{testGate(c.getSource().getPlayerOrException());return 1;}))
            .then(Commands.literal("test-raid").requires(s->s.hasPermission(2)).executes(c->{var l=c.getSource().getServer().overworld();var d=CampaignData.get(l);if(!d.phase.equals("preparation"))return 0;begin(l,d);return 1;})
                .then(Commands.argument("type",StringArgumentType.word()).suggests(RAID_TYPE_SUGGEST).executes(c->{var l=c.getSource().getServer().overworld();var d=CampaignData.get(l);String t=StringArgumentType.getString(c,"type");if(!raidTypeOk(t)){c.getSource().sendFailure(Component.literal("Unknown raid type. Use normal, air, paratroopers, siege or swarm."));return 0;}if(!d.phase.equals("preparation")){c.getSource().sendFailure(Component.literal("A raid can only be started during preparation."));return 0;}d.nextRaidType=t;d.setDirty();begin(l,d);if(d.introRaid)c.getSource().sendSuccess(()->Component.literal("The introduction raid is always an ordinary raid; finish it first, then run this again."),false);return 1;})))
            .then(Commands.literal("next-raid").requires(s->s.hasPermission(2)).then(Commands.argument("type",StringArgumentType.word()).suggests(RAID_TYPE_SUGGEST).executes(c->{var d=CampaignData.get(c.getSource().getServer().overworld());String t=StringArgumentType.getString(c,"type");if(!raidTypeOk(t)){c.getSource().sendFailure(Component.literal("Unknown raid type. Use normal, air, paratroopers, siege or swarm."));return 0;}d.nextRaidType=t;d.setDirty();c.getSource().sendSuccess(()->Component.literal("Next raid: "+RaidTypes.name(t).getString()),true);return 1;}))));
        for(var command:OperatorCommands.all())event.getDispatcher().register(Commands.literal("eternaldefense").then(command));
    }
    /** /eternaldefense test-gate (operators): opens a red gate five blocks in front of the player and sends */
    static void testGate(ServerPlayer p){
        var l=p.serverLevel();var look=p.getLookAngle();var at=BlockPos.containing(p.getX()+look.x*5,p.getY(),p.getZ()+look.z*5);
        at=l.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,at);
        RaiderGates.spawnGate(l,at,p.position());
        var zombie=EntityType.ZOMBIE.create(l);
        if(zombie!=null){zombie.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,p.getYRot()+180,0);l.addFreshEntity(zombie);}
        p.sendSystemMessage(Component.translatable("gui.eternal_defense.raid.test_gate"));
    }
    static int feedback(ServerPlayer p,String text){
        if(BeaconActions.inAction())BeaconNetwork.sendState(p,"","",text);else p.sendSystemMessage(Component.literal(text));return 1;
    }
    private static void status(ServerPlayer p,CampaignData d) {
        int days=Rules.intervalDays(d.rewardTier);
        p.sendSystemMessage(Component.literal("Beacon: "+d.phase+" | HP "+d.health+"/"+d.maximumHealth()+" | last reward tier "+d.rewardTier+" | raids every "+days+" days | next due in "+Math.max(0,days*24000L-d.preparationTicks)/24000.0+" days"));
        p.sendSystemMessage(Component.literal("Radius "+d.radius()+"; height -"+d.below()+"/+"+d.above()+" | base score "+d.score+" | victories "+d.victories+" | core/logistics/defense/restoration: "+d.core+"/"+d.logistics+"/"+d.defense+"/"+d.restoration));
        p.sendSystemMessage(Component.literal("/eternaldefense upgrade <core|logistics|defense|restoration|reconnaissance> | /eternaldefense claim | /eternaldefense repair | /eternaldefense boundary"));
        String[] branches={"core","logistics","defense","restoration","reconnaissance","vertical"};int[] grades={d.core,d.logistics,d.defense,d.restoration,d.reconnaissance,d.vertical};
        for(int i=0;i<branches.length;i++) {
            String branch=branches[i];int grade=grades[i],limit=Rules.branchMaximum(branch);
            p.sendSystemMessage(Component.literal("["+branch+" "+grade+"/"+limit+(grade<limit?" - upgrade: "+Economy.beaconCost(branch,grade).stream().mapToInt(Economy.Part::amount).sum()+" components":" - complete")+"]")
                .withStyle(style->style.withColor(ChatFormatting.AQUA).withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,"/eternaldefense upgrade "+branch))));
        }
    }
    static boolean near(ServerPlayer p,CampaignData d){return p.level().dimension()==Level.OVERWORLD&&p.blockPosition().distSqr(d.beacon)<(d.radius()+64.0)*(d.radius()+64.0);}
    static int upgrade(ServerPlayer p,String branch) {
        var d=CampaignData.get(p.server.overworld());if(!near(p,d)||d.active()||!d.installed())return feedback(p,"Upgrades require being near a planted beacon between raids.");
        int grade;
        switch(branch){case "core"->grade=d.core;case "logistics"->grade=d.logistics;case "defense"->grade=d.defense;case "restoration"->grade=d.restoration;case "reconnaissance"->grade=d.reconnaissance;case "vertical"->grade=d.vertical;default->{return feedback(p,"Branches: core, logistics, defense, restoration, reconnaissance, vertical.");}}
        if(grade>=Rules.branchMaximum(branch))return feedback(p,"That branch is fully upgraded.");
        if(branch.equals("core")){var wanted=p.server.overworld().getBlockState(d.beacon).setValue(DefenseStructures.MK,DefenseStructures.mark(d.core+1));if(!DefenseStructures.available(p.server.overworld(),d.beacon,wanted))return feedback(p,"Clear a 3 x 3 area, two blocks tall, centered on the beacon before upgrading to Mk-4. Nothing consumed.");}
        // Every part of the price must be there before anything is taken
        var cost=Economy.beaconCost(branch,grade);var missing=new java.util.ArrayList<String>();var w=Supply.at(p,d.beacon);var costs=new java.util.ArrayList<WeaponPlatform.Cost>();
        for(var part:cost)costs.add(new WeaponPlatform.Cost(net.minecraft.world.item.crafting.Ingredient.of(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(new ResourceLocation(ID,part.id()))),part.amount()));
        var plan=InventoryPayment.plan(w,costs);
        for(int i=0;i<cost.size();i++){var part=cost.get(i);Item item=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(new ResourceLocation(ID,part.id()));if(!p.isCreative()&&plan.allocated()[i]<part.amount())missing.add(part.amount()+" "+item.getDescription().getString());}
        if(!missing.isEmpty())return feedback(p,"Upgrade needs "+String.join(", ",missing)+". See its factory recipe in JEI, or buy parts at the Exchange Shop with Ardent Energy.");
        if(!InventoryPayment.commit(w,costs,ItemStack.EMPTY))return feedback(p,"Upgrade materials changed. Nothing consumed.");
        switch(branch){case "core"->{int oldMaximum=d.maximumHealth();d.core++;d.health=Math.min(d.maximumHealth(),d.health+d.maximumHealth()-oldMaximum);DefenseStructures.syncBeacon(p.server.overworld(),d);}case "logistics"->d.logistics++;case "defense"->d.defense++;case "restoration"->d.restoration++;case "reconnaissance"->d.reconnaissance++;case "vertical"->d.vertical++;}
        d.setDirty();BaseSurvey.request(p.server.overworld());
        if(branch.equals("reconnaissance"))return feedback(p,Rules.revealTicks(d.reconnaissance)==0?"Reconnaissance complete: every raid attacker is highlighted from the moment it spawns.":"Reconnaissance upgraded: raid attackers are highlighted "+Rules.revealTicks(d.reconnaissance)/1200+" minute(s) into each wave.");
        return feedback(p,switch(branch){case "core"->"Core upgraded: Mk-"+(d.core+1)+", radius "+d.radius()+", "+d.maximumHealth()+" maximum HP.";case "vertical"->"Vertical zone upgraded: "+d.below()+" below / "+d.above()+" above. Beacon model and HP unchanged.";case "defense"->"Defense upgraded: "+Rules.defensePercent(d.defense)+"% less beacon damage.";case "restoration"->"Restoration upgraded: heal "+Rules.healingPercent(d.restoration)+"% of maximum HP after victory; repair "+(32+d.restoration*32)+" blocks per tick.";default->"Logistics upgraded: +"+d.logistics+" payout tiers. Better paid tiers shorten the following interval.";});
    }
    static int repair(ServerPlayer p) {
        var d=CampaignData.get(p.server.overworld());if(!d.installed()||!near(p,d)||d.active())return feedback(p,"Repair between raids, near the beacon.");
        if(d.health>=d.maximumHealth())return feedback(p,"Already at full health.");
        if(!consume(Supply.at(p,d.beacon),PLATING.get(),Economy.repairAmount()))return feedback(p,"Needs "+Economy.repairAmount()+" reinforced plating. JEI shows the recipe.");
        d.health=Math.min(d.maximumHealth(),d.health+250);d.phase="preparation";d.setDirty();return feedback(p,"Repaired +250 HP. Campaign resumed.");
    }
    /** Takes cost of item: the pack first, then the Home Zone's Hubs; creative players pay nothing. */
    static boolean consume(Supply.Where w,Item item,int cost){return InventoryPayment.commit(w,java.util.List.of(new WeaponPlatform.Cost(net.minecraft.world.item.crafting.Ingredient.of(item),cost)),ItemStack.EMPTY);}
    static void claim(ServerPlayer p,CampaignData d) {
        if(!near(p,d))return;
        feedback(p,RewardCache.claim(p,d));
    }
    static void decommission(ServerLevel l,CampaignData d,ServerPlayer p) {
        Hunts.cancel(l);   // The running hunting mission ends and its warband leaves
        RaidRescue.reset(l);for(UUID id:d.raiders){Entity e=l.getEntity(id);if(e!=null)e.discard();}
        net.minecraft.world.Containers.dropContents(l,d.beacon,d.rewardBox);   // The chest goes with the beacon: its contents spill out
        if(l.hasChunkAt(d.beacon)&&l.getBlockState(d.beacon).is(BEACON.get()))l.setBlock(d.beacon,Blocks.AIR.defaultBlockState(),3);
        d.campaignSerial++;d.resetProgress();d.phase="decommissioning";d.setDirty();
        if(p!=null)give(p,new ItemStack(BEACON_ITEM.get()));
        if(d.damage.isEmpty()&&d.destroyedTurrets.isEmpty())d.finishDecommission();
        BeaconNetwork.syncNearby(l,d);
    }
    static boolean reconcileMissing(ServerLevel l,CampaignData d) {
        if(d.installed()&&l.hasChunkAt(d.beacon)&&!l.getBlockState(d.beacon).is(BEACON.get())) {decommission(l,d,null);return true;}return false;
    }
    static void announce(ServerLevel l,String s){l.getServer().getPlayerList().broadcastSystemMessage(Component.literal("[Eternal Defense] "+s).withStyle(ChatFormatting.GOLD),false);}
    static void announce(ServerLevel l,Component c){l.getServer().getPlayerList().broadcastSystemMessage(Component.literal("[Eternal Defense] ").append(c).withStyle(ChatFormatting.GOLD),false);}
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        ServerLevel l=event.getServer().overworld();CampaignData d=CampaignData.get(l);clock++;if(clock%20==0){SpecialForcesRaids.cleanup(l);RaidTypes.broadcast(l,d);}BeaconCombat.tick(l,d);BaseSurvey.tick(l,d);HardRaids.tick(l,d);
        Hunts.tick(l,d,clock);
        Fobs.tick(l,clock);Routes.tick(l,clock);FobOutline.tick(l,clock);SupplyCatapult.tick(l,clock);
        if(clock%20==0){
            if(reconcileMissing(l,d))announce(l,"Missing beacon cleared. Place a beacon to start a fresh campaign.");
            BeaconNetwork.syncNearby(l,d);RaidBridges.tick(l,d);   // Planks left over in unloaded land go before any repair
        }
        if(d.phase.equals("decommissioning")){restore(l,d);return;}
        if(d.phase.equals("unplaced"))return;
        // No offline escalation.
        if(l.getServer().getPlayerList().getPlayers().isEmpty())return;
        switch(d.phase) {
            case "preparation" -> {
                if(!d.raidRequested&&RaidRespite.waitTick(d))break;   // A raid booked for nightfall ends purchased break time, as starting one did
                d.preparationTicks++;RaidWarnings.tick(l,d);
                // A raid that is due (or was asked for by day) starts only at night, with a defender near the beacon
                if((d.preparationTicks>=Rules.intervalDays(d.rewardTier)*24000L||d.raidRequested)&&Rules.raidHour(l.getDayTime())&&l.players().stream().anyMatch(p->near(p,d)))begin(l,d);
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
        RaidAdaptation.begin(d);RaidRescue.reset(l);RaidSpawns.forgetGates();
        RaidWarnings.reset();d.raidRequested=false;d.introRaid=!d.introCompleted;d.victoryRestoration=false;d.phase="snapshot";d.scanCursor=0;d.snapshot.clear();d.baseCounts.clear();d.wave=0;d.deaths=0;d.raidTicks=0;d.raiders.clear();d.setDirty();
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
        if(d.scanCursor>=total){d.score=BaseScoring.analyze(d,d.snapshot,BaseScoring.Ledger.get(l).placed).total();d.raidTier=d.introRaid?0:RaidRespite.previewTier(d,Rules.rewardTier(d.logistics,d.score));RaidRespite.started(d);d.phase="raid";HardRaids.start(d);if(d.introRaid)d.hardRaid=false;d.raidType=d.introRaid?"normal":d.nextRaidType;d.nextRaidType="normal";RaidModifiers.begin(d);d.eliteWave=1+l.random.nextInt(Rules.waves(d.raidTier));d.eliteLeft=d.eliteWaves=0;d.eliteThisWave=false;announceModifiers(l,d);BeaconNetwork.announce(l,"raid_start",d.raidsStarted,d.hardRaid?1:0,d.raidType,"");if(RaidTypes.special(d.raidType)){announce(l,RaidTypes.name(d.raidType).getString()+"! "+Component.translatable("gui.eternal_defense.raidtype.tip."+d.raidType).getString());}if(d.hardRaid)announce(l,"HARD RAID #"+d.raidsStarted+"! A boss leads the final wave. Survive for a 50% larger resource cache and bonus medical kits/grenades.");nextWave(l,d);d.setDirty();announce(l,"Base saved. Score "+d.score+", payout tier "+d.raidTier+". Defend the beacon!");}
    }
    private static void nextWave(ServerLevel l,CampaignData d) {
        d.wave++;d.waveTicks=0;d.wavePlayers=RaidBalance.defenders(l,d);d.waveVeteran=Rules.veteranPressure(d.raidTier,d.victories);d.spawnRemaining=RaidModifiers.attackers(Rules.waveEnemies(d.raidTier,d.wave,d.wavePlayers,d.waveVeteran,d.hardRaid),d.raidType,d.activeMods);d.spawnCooldown=0;
        d.eliteLeft=d.introRaid?Elites.introCount(d.wave,d.eliteWave):Elites.count(d.raidTier,d.wave,d.eliteWave,l.random.nextDouble(),RaidModifiers.on(d,"elite_cadre"));d.eliteThisWave=false;
        RaidAdaptation.nextWave(l,d);
        BeaconNetwork.announce(l,"wave",d.wave,Rules.waves(d.raidTier),Integer.toString(d.spawnRemaining),"");
        announce(l,"Wave "+d.wave+" / "+Rules.waves(d.raidTier)+" incoming. "+d.wavePlayers+" defender(s), "+d.spawnRemaining+" attackers"+(d.waveVeteran>0?", veteran reinforcements +"+(d.waveVeteran*10)+"%":"")+".");d.setDirty();
    }
    /** At the raid's start: what its modifiers do, for everybody */
    static void announceModifiers(ServerLevel l,CampaignData d){
        if(d.activeMods.isEmpty())return;
        var names=Component.empty();boolean first=true;for(String id:d.activeMods){if(!first)names.append(", ");names.append(Component.translatable("gui.eternal_defense.raidmod."+id));first=false;}
        announce(l,Component.translatable("gui.eternal_defense.raidmod.active",names,RaidModifiers.payoutPercent(d.activeMods),d.dropBonus()));
    }
    static void auditBlock(CampaignData d,BlockPos pos,BlockState s){
        if(!d.inside(pos))return;
        String id=BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();
        if(s.getBlock() instanceof WeaponPlatform.Station)d.baseCounts.merge(id+"#platform",s.getValue(WeaponPlatform.AGE),Math::max);
        else d.baseCounts.merge(id,1,Integer::sum);
    }
    /** Raiders glow once Reconnaissance has found them; never under Fog of War, which switches Reconnaissance off */
    static boolean revealed(CampaignData d){return !RaidModifiers.on(d,"fog_of_war")&&Rules.highlightAttackers(d.reconnaissance,d.waveTicks);}
    private static void raid(ServerLevel l,CampaignData d) {
        d.raidTicks++;d.waveTicks++;
        int reveal=Rules.revealTicks(d.reconnaissance);
        boolean fog=RaidModifiers.on(d,"fog_of_war");
        if(!fog&&reveal>0&&d.waveTicks==reveal)announce(l,reveal/1200+(reveal==1200?" minute":" minutes")+" elapsed: remaining attackers are now highlighted through walls.");
        if(d.raidTicks>Rules.raidTimeLimit(d.wave-1)){fail(l,d,"Raid timed out. Beacon loses 20% health; damaged structures stay damaged.");return;}
        if(d.health<=0){fail(l,d,"Beacon disabled. Repair it between raids; your base remains in the world.");return;}
        if(!l.hasChunkAt(d.beacon)){fail(l,d,"Beacon area unloaded. Retreat counts as a lost defense.");return;}
        if(d.spawnRemaining>0&&--d.spawnCooldown<=0&&RaidBalance.canSpawn(l,d)){if(spawn(l,d))d.spawnRemaining--;d.spawnCooldown=RaidTypes.cooldown(d.raidType);d.setDirty();}
        if(clock%20!=0)return;
        for(var it=d.raiders.iterator();it.hasNext();) {
            Entity e=l.getEntity(it.next());if(e==null||!e.isAlive()){it.remove();continue;}
            if(SpecialForcesRaids.defeated(e)){SpecialForcesRaids.retire(e,l);it.remove();d.setDirty();continue;}
            if(!(e instanceof Mob mob))continue;
            if(mob.getPersistentData().getBoolean("eternalBoss"))HardRaids.balance(mob,d.raidTier);else if(d.raidTier<=1)balanceEarly(mob,d.raidTier,d.wave);
            else if(mob.getPersistentData().getBoolean("eternalSpecialForces"))SpecialForcesRaids.balance(mob,d.raidTier);
            else RaidBalance.balance(mob,d.raidTier);
            mob.setGlowingTag(revealed(d));Elites.restore(mob);
            // Real attack goals target the objective; contact alone never damages it.
            BeaconCombat.attach(mob);
            if(RaidRescue.tick(l,d,mob,it))continue; // Stuck: a gate or the parachute squadron moves it
            boolean idle=RaidMarch.idle(mob);
            if(d.raidType.equals("siege")&&mob instanceof net.minecraft.world.entity.monster.Creeper creeper){
                // Siege creepers dig: stuck at a wall near the base, they blow a hole for the shooters behind them
                if(idle&&creeper.getTarget()!=null&&Math.sqrt(mob.distanceToSqr(d.beacon.getX()+.5,d.beacon.getY(),d.beacon.getZ()+.5))<=d.radius()+RaidMarch.DIG_RANGE&&RaidBreaching.choose(l,d,mob)!=null)creeper.ignite();
                continue;
            }
            // Raiders lay bridges, break the defenses they came for and dig through what stops them (vexes pass through walls)
            if(!(mob instanceof net.minecraft.world.entity.monster.Vex))RaidDigging.think(l,d,mob,idle);
            if(idle&&Elites.giant(mob))RaidBreaching.smash(l,d,mob);   // A giant breaks what its body runs into
            // All hostile breach attempts run through BeaconCombat and the same damage journal.
        }
        RaidMarch.prune(d.raiders);RaiderGates.cleanup(l,d);RaidTargets.cleanup(l);RaidDigging.tick(l);RaidRescue.announceDrops(l);
        if(d.spawnRemaining==0&&d.raiders.isEmpty()){
            reward(d,Items.IRON_INGOT,RaidRewards.waveIron(d.raidTier)); // Each cleared wave has a claimable supply reward.
            if(d.wave>=Rules.waves(d.raidTier)){
                if(d.hardRaid&&(!d.bossSpawned||!d.bossKilled)){fail(l,d,"Boss was not defeated. No completion cache awarded; surviving wave rewards remain.");return;}
                d.victories++;d.rewardTier=d.raidTier;
                int payout=RaidModifiers.payoutPercent(d.activeMods);RaidRewards.queue(d,RaidRewards.completion(l,d.rewardTier,d.hardRaid&&d.bossKilled,payout));
                if(payout>0)announce(l,Component.translatable("gui.eternal_defense.raidmod.paid",payout));
                if(d.eliteWaves>0){RaidRewards.queue(d,java.util.List.of(new ItemStack(ARDENT_ENERGY.get(),d.eliteWaves)));announce(l,Component.translatable("gui.eternal_defense.elite.paid",d.eliteWaves));}
                if(RaidTypes.special(d.raidType)){int bonus=RaidTypes.bonusEnergy(d.rewardTier);RaidRewards.queue(d,java.util.List.of(new ItemStack(ARDENT_ENERGY.get(),bonus)));announce(l,RaidTypes.name(d.raidType).getString()+" survived: +"+bonus+" Ardent Energy in the reward.");}
                if(d.introRaid&&!d.introCompleted){RaidRewards.queue(d,java.util.List.of(new ItemStack(GUN_PLATFORM.get())));d.introCompleted=true;d.introRaid=false;announce(l,"Introduction complete! Claim your Weapon Platform at the beacon.");}
                BeaconNetwork.announce(l,"victory",d.rewardTier,0,"","");d.victoryRestoration=true;d.phase="restore";RaidTypes.finish(l,d);RaidRescue.reset(l);announce(l,"Defense won! Wave rewards are ready at the beacon. Repairing raid damage; ammunition stays spent.");
            }
            else{announce(l,Component.translatable("gui.eternal_defense.raid.wave_cleared",d.wave,Rules.WAVE_CLEARED_BONUS_TICKS/1200,Rules.raidMinutesLeft(d.raidTicks,d.wave)));nextWave(l,d);}
            d.setDirty();
        } else d.setDirty();
    }
    private static boolean spawn(ServerLevel l,CampaignData d) {
        String raidType=d.introRaid?"normal":d.raidType;boolean bossNow=d.hardRaid&&d.wave==Rules.waves(d.raidTier)&&!d.bossSpawned;
        BlockPos spawnPos;boolean chute=false;
        if(raidType.equals("paratroopers")&&!bossNow)spawnPos=RaidTypes.dropPoint(l,d);
        else{
            // Natural ground far out first, else a red gate closer in, else a parachute over the zone.
            var place=RaidSpawns.place(l,d);spawnPos=place==null?null:place.pos();chute=place!=null&&place.chute();
            if(place!=null&&place.gate())RaiderGates.spawnGate(l,spawnPos,net.minecraft.world.phys.Vec3.atCenterOf(d.beacon));   // The gate stands behind it, seen from the base
        }
        if(spawnPos==null)return false;
        if(!chute)return spawnAt(l,d,raidType,bossNow,spawnPos);
        var before=new HashSet<>(d.raiders);boolean spawned=spawnAt(l,d,raidType,bossNow,spawnPos);
        for(UUID id:d.raiders)if(!before.contains(id)&&l.getEntity(id) instanceof Mob mob)RaidTypes.parachute(mob);
        return spawned;
    }
    private static boolean spawnAt(ServerLevel l,CampaignData d,String raidType,boolean bossNow,BlockPos spawnPos){
        if(bossNow)return HardRaids.spawn(l,d,spawnPos);
        int soldiers=SpecialForcesRaids.count(d.raidTier,d.wavePlayers);
        if(!RaidTypes.special(raidType)&&soldiers>0&&d.spawnRemaining<=soldiers&&SpecialForcesRaids.available())return SpecialForcesRaids.spawn(l,d,spawnPos,d.spawnRemaining==soldiers);
        String role=d.introRaid?"grunt":RaidTypes.role(raidType,d.raidTier,l.random.nextInt(100));
        EntityType<?> type=d.introRaid?EntityType.HUSK:RaidTypes.type(raidType,d.raidTier,role,l);
        var mob=raider(l,d,type,role,raidType,spawnPos);if(mob==null)return false;
        Elites.roll(l,d,mob);
        RaidTypes.broadcast(l,d);
        return true;
    }
    /** One ordinary raider of this wave: armed, tagged, balanced for the raid tier, with the speed burst. */
    static Mob raider(ServerLevel l,CampaignData d,EntityType<?> type,String role,String raidType,BlockPos at){
        Entity entity=type.create(l);if(!(entity instanceof Mob mob))return null;
        mob.moveTo(at.getX()+0.5,at.getY(),at.getZ()+0.5,l.random.nextFloat()*360,0);mob.setPersistenceRequired();mob.getPersistentData().putBoolean("eternalRaider",true);
        mob.setGlowingTag(revealed(d));RaidBalance.arm(mob);   // Armed before it joins: its beacon goal depends on the weapon
        if(!l.noCollision(mob)||!l.addFreshEntity(mob))return null; // Let pack spawn handlers run first.
        mob.getPersistentData().putString("eternalRole",role);RaidTypes.prepare(mob,raidType);if(raidType.equals("paratroopers"))RaidTypes.parachute(mob);
        if(d.raidTier<=1)balanceEarly(mob,d.raidTier,d.wave);else RaidBalance.balance(mob,d.raidTier);
        if(RaidModifiers.on(d,"hardened"))Elites.modifier(mob,net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH,Elites.RAID_HEALTH,"Hardened raiders",RaidModifiers.HARDENED_HEALTH,net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL);
        if(RaidModifiers.on(d,"forced_march"))Elites.modifier(mob,net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED,Elites.RAID_SPEED,"Forced march",RaidModifiers.FORCED_MARCH_SPEED,net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL);
        mob.setHealth(mob.getMaxHealth());RaidSpawns.rush(mob);
        d.raiders.add(mob.getUUID());
        return mob;
    }
    static void balanceEarly(Mob mob,int tier,int wave) {
        for(EquipmentSlot slot:EquipmentSlot.values()){
            if(slot.getType()==EquipmentSlot.Type.ARMOR||slot==EquipmentSlot.OFFHAND||slot==EquipmentSlot.MAINHAND&&!(mob instanceof net.minecraft.world.entity.monster.AbstractSkeleton))mob.setItemSlot(slot,ItemStack.EMPTY);
        }
        if(mob instanceof net.minecraft.world.entity.monster.AbstractSkeleton&&(!mob.getMainHandItem().is(Items.BOW)||mob.getMainHandItem().isEnchanted()))mob.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.BOW));
        for(var attr:List.of(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR,net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS,net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE)){
            Elites.reset(mob.getAttribute(attr),0);
        }
        var health=mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if(health!=null){float current=mob.getHealth();Elites.reset(health,20+Math.min(3,wave)*2+tier*8);mob.setHealth(Math.min(current,mob.getMaxHealth()));}
        Elites.reset(mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE),2+tier);
        mob.setCanPickUpLoot(false);RaidBalance.arm(mob);if(mob instanceof net.minecraft.world.entity.monster.Zombie zombie)zombie.setBaby(false);
    }
    private static void breach(ServerLevel l,CampaignData d,Mob mob) {
        Vec3 dir=Vec3.atCenterOf(d.beacon).subtract(mob.position()).normalize();BlockPos ahead=BlockPos.containing(mob.getX()+dir.x*1.4,mob.getY(),mob.getZ()+dir.z*1.4);
        for(int dy=0;dy<2;dy++){BlockPos p=ahead.above(dy);if(d.inside(p)&&!p.equals(d.beacon)&&!l.getBlockState(p).isAir()&&l.getBlockState(p).getDestroySpeed(l,p)>=0)damageBlock(l,d,p);}
    }
    static void damageBlock(ServerLevel l,CampaignData d,BlockPos p) {
        p=DefenseStructures.anchor(l,p);
        if(net.minecraftforge.fml.ModList.get().isLoaded("eternal_defense_displays"))p=dev.eternaldefense.displays.DisplayRacks.anchor(l,p);
        if(p.equals(d.beacon)||!d.inside(p)||!d.snapshot.containsKey(p.asLong()))return;
        BlockState current=l.getBlockState(p);if(current.isAir()||current.getDestroySpeed(l,p)<0)return;
        if(current.getBlock() instanceof DoorBlock){
            var partner=current.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)==net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER?p.above():p.below();
            var other=l.getBlockState(partner);if(d.inside(partner)&&other.getBlock()==current.getBlock()&&d.snapshot.containsKey(partner.asLong())){d.damage.putIfAbsent(partner.asLong(),new CampaignData.Damage(d.snapshot.get(partner.asLong()),null));l.setBlock(partner,Blocks.AIR.defaultBlockState(),2);}
        }
        BlockEntity be=l.getBlockEntity(p);CompoundTag live=be==null?null:be.saveWithFullMetadata();
        d.damage.putIfAbsent(p.asLong(),new CampaignData.Damage(d.snapshot.get(p.asLong()),live));d.setDirty();
        // Remove the entity before the block: inventories and fuel are held in the journal, not dropped.
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
        BeaconNetwork.announce(l,"defeat",0,0,"","");RaidTypes.finish(l,d);for(UUID id:d.raiders){Entity e=l.getEntity(id);if(e!=null)e.discard();}d.raiders.clear();RaidRescue.reset(l);
        // Keep all machine journals until restoration succeeds, even if a chunk is unloaded.
        d.damage.entrySet().removeIf(e->e.getValue().entity()==null);d.setDirty();announce(l,reason);
    }
    private static void reward(CampaignData d,Item item,int count){while(count>0){int n=Math.min(64,count);d.rewards.add(new ItemStack(item,n).save(new CompoundTag()));count-=n;}}
    private static void rewardModItem(CampaignData d,String id,int count){Item item=BuiltInRegistries.ITEM.get(new ResourceLocation(id));if(item!=Items.AIR)reward(d,item,count);}
    @SubscribeEvent public void died(LivingDeathEvent event) {
        if(event.getEntity().level() instanceof ServerLevel bossLevel)HardRaids.killed(bossLevel,event.getEntity());
        if(event.getEntity().level() instanceof ServerLevel l&&l.dimension()==Level.OVERWORLD) {
            CampaignData d=CampaignData.get(l);
            // The type id is looked up only for deaths inside the zone during a raid
            if(d.phase.equals("raid")&&d.inside(event.getEntity().blockPosition())&&BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType()).getNamespace().equals("tacz_turrets")) {
                CompoundTag n=new CompoundTag();event.getEntity().save(n);n.putShort("DeathTime",(short)0);n.putShort("HurtTime",(short)0);
                d.destroyedTurrets.add(n);d.setDirty();event.setCanceled(true);event.getEntity().discard();return;
            }
        }
        if(!(event.getEntity() instanceof ServerPlayer p)||p.level().dimension()!=Level.OVERWORLD)return;
        CampaignData d=CampaignData.get(p.server.overworld());if(!d.phase.equals("raid")||!near(p,d))return;
        d.deaths++;int penalty=Rules.deathPenalty(d.deaths,Rules.DEATHS_PER_PENALTY,d.maximumHealth());if(penalty>0){d.health-=penalty;announce(p.server.overworld(),"Three defender deaths: beacon loses 10% health.");}d.setDirty();
    }
    @SubscribeEvent public void explosion(ExplosionEvent.Detonate event) {
        if(!(event.getLevel() instanceof ServerLevel l)||l.dimension()!=Level.OVERWORLD)return;
        CampaignData d=CampaignData.get(l);if(!d.phase.equals("raid"))return;
        LivingEntity cause=event.getExplosion().getIndirectSourceEntity();
        if(cause==null||!cause.getPersistentData().getBoolean("eternalRaider"))return;
        for(var it=event.getAffectedBlocks().iterator();it.hasNext();) {BlockPos p=it.next();if(d.inside(p)){damageBlock(l,d,p);it.remove();}}
    }
    @SubscribeEvent public void mobGrief(EntityMobGriefingEvent event) {
        if(!(event.getEntity().level() instanceof ServerLevel l)||l.dimension()!=Level.OVERWORLD)return;
        CampaignData d=CampaignData.get(l);
        // Raid attackers use the journaled breaching.
        if(d.active()&&d.inside(event.getEntity().blockPosition()))event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
    }
    @SubscribeEvent public void loadedRaider(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        if(!event.loadedFromDisk()||!(event.getLevel() instanceof ServerLevel l)||l.dimension()!=Level.OVERWORLD||!event.getEntity().getPersistentData().getBoolean("eternalRaider"))return;
        var d=CampaignData.get(l);
        if(!d.phase.equals("raid")||!d.raiders.contains(event.getEntity().getUUID())){event.setCanceled(true);event.getEntity().discard();}
    }
    @SubscribeEvent public void breakBlock(BlockEvent.BreakEvent event) {
        if(!(event.getLevel() instanceof ServerLevel l)||l.dimension()!=Level.OVERWORLD)return;
        CampaignData d=CampaignData.get(l);
        if(DefenseStructures.beacon(l,event.getPos())){event.setCanceled(true);if(event.getPlayer() instanceof ServerPlayer p)feedback(p,"Use the Beacon Recovery Shovel to remove it. Confirmation required.");return;}
        if(d.active()&&d.inside(event.getPos())&&!event.getState().is(RAIDER_BRIDGE.get())){event.setCanceled(true);if(event.getPlayer() instanceof ServerPlayer p)feedback(p,"The beacon area is locked until the raid and repairs end.");}
    }
    @SubscribeEvent public void placeBlock(BlockEvent.EntityPlaceEvent event) {
        if(!(event.getLevel() instanceof ServerLevel l)||l.dimension()!=Level.OVERWORLD)return;
        CampaignData d=CampaignData.get(l);if(d.active()&&d.inside(event.getPos())&&event.getEntity() instanceof Player){event.setCanceled(true);PlacementGuard.resyncLater(event.getEntity());}
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
