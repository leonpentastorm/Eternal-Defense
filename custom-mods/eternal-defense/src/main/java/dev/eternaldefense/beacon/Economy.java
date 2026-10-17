package dev.eternaldefense.beacon;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import java.util.List;
import java.util.function.Supplier;

/** Where every upgrade price lives. */
final class Economy {
    private Economy(){}
    /** A price: part is null when it is paid in Ardent Energy. */
    record Price(Supplier<Item> part,int amount){
        boolean ardent(){return part==null;}
        Item item(){return part==null?EternalDefense.ARDENT_ENERGY.get():part.get();}
    }

    // Beacon
    /** The factory part each beacon branch is paid in. */
    static String beaconPart(String branch){
        return switch(branch){case "core","defense"->"reinforced_plating";case "logistics"->"logistics_module";case "restoration"->"restoration_matrix";case "reconnaissance"->"resonance_coil";default->"";};
    }
    static final List<String> BEACON_PARTS=List.of("reinforced_plating","logistics_module","resonance_coil","restoration_matrix");
    /** One part of a beacon upgrade's price: an item id and how many. */
    record Part(String id,int amount){}
    /** What buying the next level of a beacon branch costs: 8, 16, 32... */
    static List<Part> beaconCost(String branch,int level){
        int amount=Rules.upgradeCost(level);
        if(branch.equals("vertical"))return BEACON_PARTS.stream().map(id->new Part(id,matrices(id,amount/BEACON_PARTS.size()))).toList();
        return List.of(new Part(beaconPart(branch),matrices(beaconPart(branch),amount)));
    }
    /** Every pack price paid in Beacon Restoration Matrices is half what it was (rounded up) */
    static int matrices(String id,int amount){return id.endsWith("restoration_matrix")?(amount+1)/2:amount;}
    static int repairAmount(){return 8;}
    static final int[] KITCHEN_PLATING={8,16,32};
    static Price kitchen(int mk){return mk<1||mk>=4?null:new Price(EternalDefense.PLATING::get,KITCHEN_PLATING[mk-1]);}
    /** Drink Station upgrades to Mk II, III and IV, like the Mess Hall's: 8/16/32 Reinforced Beacon Plating. */
    static final int[] DRINKS_PLATING={8,16,32};
    /** The MRE Station, as the Mess Hall Mk I: 4 Reinforced Beacon Plating and 8 iron, plus a smoker. */
    static final int MRE_STATION_KEY=4,MRE_STATION_IRON=8;
    /** The Logistics Center Hub. */
    static final int HUB_PLATING=64,HUB_IRON=64,HUB_CHESTS=16,HUB_HOPPERS=8;
    /** The Loadout Station (proposed, unplaytested): the Hub's 64 plating, with restoration matrices, iron and glass. */
    static final int LOADOUT_PLATING=64,LOADOUT_MATRICES=8,LOADOUT_IRON=32,LOADOUT_GLASS=16;
    /** A Loadout Coin's fee (unplaytested): 4 Reinforced Beacon Plating and 1 Ardent Energy. */
    static final int COIN_FEE_PLATING=4,COIN_FEE_ARDENT=1;
    /** The Loadout Flare at the Support Platform, in Ardent Energy (unplaytested). */
    static final int LOADOUT_FLARE_PRICE=5;
    /** The Handheld Radio at the Support Platform, in Ardent Energy. */
    static final int RADIO_PRICE=8;
    /** The Defense Depot (unplaytested), in Universal Ammo Coins: turrets Mk I to III, then lots of ammunition and defenses */
    static final int[] TURRET_PRICE={96,192,384},FLAME_TURRET_PRICE={128,256,512};
    static final int DEPOT_MG_AMMO=32,DEPOT_SHELLS=32,DEPOT_FUEL=40,DEPOT_SANDBAGS=16,DEPOT_BARRICADE=16,DEPOT_BARBED=16,DEPOT_SHOCK_BOX=64,DEPOT_WIRE=16;
    /** The defense kit's Workshop Fabrication (unplaytested): the depot, the pole and the two dynamos */
    static final int DEPOT_IRON=16,DEPOT_COPPER=8,DEPOT_CHESTS=2,DEPOT_PLATING=2;
    static final int POLE_IRON=6,POLE_COPPER=8;
    static final int DYNAMO_COPPER=16,DYNAMO_IRON=8,DYNAMO_ALLOY=8,DYNAMO_REDSTONE=4;
    static List<WeaponPlatform.Cost> coinFee(){return List.of(WeaponPlatform.cost("eternal_defense:reinforced_plating",COIN_FEE_PLATING),WeaponPlatform.cost("eternal_defense:ardent_energy",COIN_FEE_ARDENT));}
    static Price drinkStation(int mk){return mk<1||mk>=4?null:new Price(EternalDefense.PLATING::get,DRINKS_PLATING[mk-1]);}

    static List<WeaponPlatform.Cost> stationCosts(String kind){
        if(kind.equals("support"))return List.of(WeaponPlatform.cost("minecraft:iron_ingot",20),WeaponPlatform.cost("minecraft:copper_ingot",12),WeaponPlatform.cost("minecraft:redstone",12),WeaponPlatform.cost("minecraft:glass",4));
        if(kind.equals("exchange"))return List.of(WeaponPlatform.cost("minecraft:iron_ingot",12),WeaponPlatform.cost("minecraft:copper_ingot",20),WeaponPlatform.cost("minecraft:redstone",6),WeaponPlatform.cost("minecraft:glass",4));
        return List.of(WeaponPlatform.cost("minecraft:iron_ingot",16),WeaponPlatform.cost(kind.equals("ammo")?"minecraft:copper_ingot":"minecraft:gold_ingot",16),WeaponPlatform.cost("create:andesite_alloy",8));}

    static List<WeaponPlatform.Cost> fabrication(String id){
        return switch(id){
            case "gun_platform","ammo_platform","attachment_platform","armor_platform"->WeaponPlatform.purchaseCosts(id.replace("_platform",""));
            case "support_platform"->WeaponPlatform.purchaseCosts("support");
            case "exchange_shop"->WeaponPlatform.purchaseCosts("exchange");
            case "support_cannon"->List.of(WeaponPlatform.cost("eternal_defense:ardent_energy",12));
            // Tactical Operations: the table and the satellite take plain materials plus a factory part
            case "command_table"->List.of(WeaponPlatform.cost("minecraft:iron_ingot",24),WeaponPlatform.cost("minecraft:copper_ingot",16),WeaponPlatform.cost("minecraft:redstone",16),WeaponPlatform.cost("minecraft:glass",8),
                WeaponPlatform.cost("eternal_defense:reinforced_plating",COMMAND_TABLE_PLATING));
            case "satellite_beacon"->List.of(WeaponPlatform.cost("minecraft:iron_ingot",16),WeaponPlatform.cost("minecraft:copper_ingot",24),WeaponPlatform.cost("minecraft:redstone",8),WeaponPlatform.cost("minecraft:gold_ingot",4),
                WeaponPlatform.cost("eternal_defense:resonance_coil",SATELLITE_COILS));
            // Forward Operating Bases: plain materials plus logistics modules and plating
            case "fob_beacon"->List.of(WeaponPlatform.cost("minecraft:iron_ingot",FOB_IRON),WeaponPlatform.cost("minecraft:copper_ingot",FOB_COPPER),WeaponPlatform.cost("minecraft:redstone",FOB_REDSTONE),WeaponPlatform.cost("minecraft:glass",FOB_GLASS),WeaponPlatform.cost("eternal_defense:logistics_module",FOB_LOGISTICS),WeaponPlatform.cost("eternal_defense:reinforced_plating",FOB_PLATING));
            // The supply rail: the same plain materials for both gates, plus a logistics module
            case "supply_delivery_gate","supply_receiving_gate"->List.of(WeaponPlatform.cost("minecraft:iron_ingot",GATE_IRON),WeaponPlatform.cost("minecraft:copper_ingot",GATE_COPPER),WeaponPlatform.cost("minecraft:redstone",GATE_REDSTONE),WeaponPlatform.cost("minecraft:chest",GATE_CHESTS),
                WeaponPlatform.cost("eternal_defense:logistics_module",GATE_LOGISTICS));
            // The Tactical Enchanting Table: iron, obsidian and books, plus resonance coils
            case "tactical_enchanting_table"->List.of(WeaponPlatform.cost("minecraft:iron_ingot",ENCHANT_IRON),WeaponPlatform.cost("minecraft:obsidian",ENCHANT_OBSIDIAN),WeaponPlatform.cost("minecraft:book",ENCHANT_BOOKS),
                WeaponPlatform.cost("eternal_defense:resonance_coil",ENCHANT_COILS));
            // The Supply Catapult: timber, iron and rope, plus logistics modules
            case "supply_catapult"->List.of(new WeaponPlatform.Cost(Ingredient.of(net.minecraft.tags.ItemTags.LOGS),CATAPULT_LOGS),WeaponPlatform.cost("minecraft:iron_ingot",CATAPULT_IRON),WeaponPlatform.cost("minecraft:string",CATAPULT_STRING),
                WeaponPlatform.cost("eternal_defense:logistics_module",CATAPULT_LOGISTICS));
            case "mess_hall_mk1"->List.of(WeaponPlatform.cost("eternal_defense:reinforced_plating",4),WeaponPlatform.cost("minecraft:iron_ingot",8));
            case "cook_pot"->List.of(WeaponPlatform.cost("minecraft:iron_ingot",14),WeaponPlatform.cost("minecraft:oak_sign",1));
            case "milk_dispenser"->List.of(WeaponPlatform.cost("minecraft:iron_ingot",8),WeaponPlatform.cost("minecraft:glass",4),WeaponPlatform.cost("minecraft:copper_ingot",4));
            // The drinks corner, in the kitchen's style: the station takes a brewing stand, the pot copper
            case "drink_mixer_mk1"->List.of(WeaponPlatform.cost("eternal_defense:reinforced_plating",4),WeaponPlatform.cost("minecraft:iron_ingot",8),WeaponPlatform.cost("minecraft:brewing_stand",1));
            case "drink_pot"->List.of(WeaponPlatform.cost("minecraft:copper_ingot",14),WeaponPlatform.cost("minecraft:oak_sign",1));
            case "cup_dispenser"->List.of(WeaponPlatform.cost("minecraft:iron_ingot",8),WeaponPlatform.cost("minecraft:glass",4),WeaponPlatform.cost("minecraft:paper",4));
            // The MRE Station, in the Mess Hall's style; the smoker is its heat sealer
            case "mre_maker"->List.of(WeaponPlatform.cost("eternal_defense:reinforced_plating",MRE_STATION_KEY),WeaponPlatform.cost("minecraft:iron_ingot",MRE_STATION_IRON),WeaponPlatform.cost("minecraft:smoker",1));
            // The Logistics Center Hub, the most expensive installation: 64 plating, with iron, chests and hoppers
            case "logistics_hub"->List.of(WeaponPlatform.cost("eternal_defense:reinforced_plating",HUB_PLATING),
                WeaponPlatform.cost("minecraft:iron_ingot",HUB_IRON),WeaponPlatform.cost("minecraft:chest",HUB_CHESTS),WeaponPlatform.cost("minecraft:hopper",HUB_HOPPERS));
            // The Loadout Station, the flagship next to the Hub
            case "loadout_station"->List.of(WeaponPlatform.cost("eternal_defense:reinforced_plating",LOADOUT_PLATING),WeaponPlatform.cost("eternal_defense:restoration_matrix",LOADOUT_MATRICES),
                WeaponPlatform.cost("minecraft:iron_ingot",LOADOUT_IRON),WeaponPlatform.cost("minecraft:glass",LOADOUT_GLASS));
            // The defense kit: the depot sells the rest for coins
            case "defense_depot"->List.of(WeaponPlatform.cost("minecraft:iron_ingot",DEPOT_IRON),WeaponPlatform.cost("minecraft:copper_ingot",DEPOT_COPPER),WeaponPlatform.cost("minecraft:chest",DEPOT_CHESTS),
                WeaponPlatform.cost("eternal_defense:reinforced_plating",DEPOT_PLATING));
            case "power_pole"->List.of(WeaponPlatform.cost("minecraft:iron_ingot",POLE_IRON),WeaponPlatform.cost("minecraft:copper_ingot",POLE_COPPER));
            case "shaft_dynamo","cog_dynamo"->List.of(WeaponPlatform.cost("minecraft:copper_ingot",DYNAMO_COPPER),WeaponPlatform.cost("minecraft:iron_ingot",DYNAMO_IRON),
                WeaponPlatform.cost("create:andesite_alloy",DYNAMO_ALLOY),WeaponPlatform.cost("minecraft:redstone",DYNAMO_REDSTONE));
            case "bowl_dispenser"->List.of(WeaponPlatform.cost("minecraft:iron_ingot",8),WeaponPlatform.cost("minecraft:glass",4),new WeaponPlatform.Cost(Ingredient.of(net.minecraft.tags.ItemTags.PLANKS),4));
            default->List.of();
        };
    }

    // Cannon upgrades
    /** The part that pays for each upgrade and how many per level. */
    private static Supplier<Item> packPart(CannonUpgrades.Upgrade u){
        return switch(u){
            case TRAVERSE,AOE->EternalDefense.PLATING::get;
            case RATE,DAMAGE,SLOW->EternalDefense.COIL::get;
            case VOLLEY,PORTAL->EternalDefense.LOGISTICS::get;
            case QUANTUM,AURA,DIMENSION->EternalDefense.REPAIR::get;
        };
    }
    static final java.util.Map<CannonUpgrades.Upgrade,int[]> PACK_AMOUNTS=new java.util.EnumMap<>(java.util.Map.of(
        CannonUpgrades.Upgrade.TRAVERSE,new int[]{6,12,24},CannonUpgrades.Upgrade.RATE,new int[]{5,10,20},CannonUpgrades.Upgrade.VOLLEY,new int[]{6,12,24},
        CannonUpgrades.Upgrade.DAMAGE,new int[]{6,12,24},CannonUpgrades.Upgrade.QUANTUM,new int[]{6},CannonUpgrades.Upgrade.SLOW,new int[]{10},
        CannonUpgrades.Upgrade.PORTAL,new int[]{10},CannonUpgrades.Upgrade.AURA,new int[]{4},CannonUpgrades.Upgrade.AOE,new int[]{5,10,20},CannonUpgrades.Upgrade.DIMENSION,new int[]{10}));
    /** The price of the next level, or null when maxed. */
    static Price cannon(CannonUpgrades.Upgrade u,int level){
        if(level<0||level>=u.max())return null;
        return new Price(packPart(u),PACK_AMOUNTS.get(u)[level]);
    }
    /** Takes the price, from the pack first and then the Home Zone's Hubs (creative players pay nothing). */
    static boolean pay(Supply.Where w,Price price){
        if(price.ardent())return ArdentEnergy.spend(w,price.amount());
        return InventoryPayment.commit(w,List.of(new WeaponPlatform.Cost(Ingredient.of(price.item()),price.amount())),ItemStack.EMPTY);
    }
    /** How many of the item a request can reach: the pack, then the Home Zone's Hubs. */
    static int have(Supply.Where w,Item item){return Supply.count(w,item);}
    static int have(net.minecraft.world.entity.player.Player p,Item item){
        int n=0;for(var s:p.getInventory().items)if(s.is(item))n+=s.getCount();for(var s:p.getInventory().offhand)if(s.is(item))n+=s.getCount();return n;
    }

    // Forward Operating Bases
    /** The FOB Beacon at the Workshop: plain materials, then factory parts. */
    static final int FOB_IRON=16,FOB_COPPER=16,FOB_REDSTONE=8,FOB_GLASS=4,FOB_LOGISTICS=2,FOB_PLATING=2;
    /** Each supply gate: bought at the FOB shop (Delivery) or the Workshop (Receiving). */
    static final int GATE_IRON=16,GATE_COPPER=8,GATE_REDSTONE=8,GATE_CHESTS=2,GATE_LOGISTICS=1;
    /** A FOB's repair: a base price while it is DOWN, plus each villager lost in its raids by level */
    static final int FOB_REPAIR_BASE=3;
    static final int[] FOB_REPAIR_VILLAGER={1,1,2,2,3};
    static int fobRepairBase(){return FOB_REPAIR_BASE;}
    static int fobRepairVillager(int level){return SosRules.villagerCost(FOB_REPAIR_VILLAGER,level);}
    /** The Supply Catapult. */
    static final int CATAPULT_LOGS=16,CATAPULT_IRON=8,CATAPULT_STRING=16,CATAPULT_LOGISTICS=2;
    /** The specialty seller: Ardent Energy for one lot (a lot is worth one S-tier item, CatapultRules.LOT_VALUE) */
    static final int SELLER_LOT=2;
    static int sellerLotPrice(){return SELLER_LOT;}
    /** The Tactical Enchanting Table at the Workshop, and its Mk I to III (a permanent discount): resonance coils. */
    static final int ENCHANT_IRON=12,ENCHANT_OBSIDIAN=4,ENCHANT_BOOKS=3,ENCHANT_COILS=2;
    static final int[] ENCHANT_UPGRADE_COILS={8,16,32};
    /** The price of the table's Mk mk (1 to 3), or null past Mk III. */
    static Price enchantingUpgrade(int mk){
        if(mk<1||mk>EnchantRules.MAX_MK)return null;
        return new Price(EternalDefense.COIL::get,ENCHANT_UPGRADE_COILS[mk-1]);
    }
    /** The flares of the FOB shop cost what they cost at the Support Platform. */
    static int fobFlare(String id){return switch(id){case "supply"->SupportRules.SUPPLY_FLARE_PRICE;case "return"->SupportRules.RETURN_FLARE_PRICE;default->SupportRules.FIRE_FLARE_PRICE;};}

    // Tactical Operations
    static final int COMMAND_TABLE_PLATING=4,SATELLITE_COILS=2;
    /** Satellite Beacon upgrades by the Mk being bought (II, III): resonance coils. */
    static final int[] SATELLITE_UPGRADE_COILS={16,32};
    /** The price of the satellite's next Mk from mk, or null at Mk III. */
    static Price satellite(int mk){
        if(mk<1||mk>=TacticalRules.MAX_MK)return null;
        return new Price(EternalDefense.COIL::get,SATELLITE_UPGRADE_COILS[mk-1]);
    }
    /** A raid's guaranteed Ardent Energy at reward tier t (capped at 10): 1+t. */
    static int huntGuaranteed(int tier){return 1+Math.max(0,Math.min(10,tier));}
    /** The Exchange sells 16 Universal Ammo Coins for 3 Ardent Energy: coins in a hunt's pay are valued at that rate. */
    static final double COINS_PER_ARDENT=16/3.0,HUNT_ARDENT_SHARE=.6,HUNT_COIN_SHARE=.4;
    /** What a hunt pays into the beacon's reward chest. */
    record HuntPay(int ardent,int coins){}
    /** A hunt always pays at least this much, whatever the reward tier (a pack Patrol at tier 0 paid 0 and 1). */
    static final int HUNT_MIN_ARDENT=1,HUNT_MIN_COINS=2;
    /** A Patrol pays twice what it did, at every tier (the minimum too) */
    static final int PATROL_PAY_FACTOR=2;
    /** Pure: budget B = class share x guaranteed raid Ardent; Ardent = round(0.6 B), coins = round(0.4 B x 16/3) */
    static HuntPay hunt(TacticalRules.Kind kind,int tier){
        double budget=kind.budget*huntGuaranteed(tier);int factor=kind==TacticalRules.Kind.PATROL?PATROL_PAY_FACTOR:1;
        return new HuntPay(factor*Math.max(HUNT_MIN_ARDENT,(int)Math.round(HUNT_ARDENT_SHARE*budget)),factor*Math.max(HUNT_MIN_COINS,(int)Math.round(HUNT_COIN_SHARE*budget*COINS_PER_ARDENT)));
    }

    // Support gear (Ardent Energy)

    // Armor table
    /** One material of a set price: an item id (#tag for any item of a tag) and how many */
    record SetPart(String id,int amount){}
    /** A full set (head, chest, legs, feet) of a typical piece of each Age; a piece pays its share, the first material always */
    static final List<List<SetPart>> ARMOR_SETS=List.of(
        List.of(new SetPart("create:iron_sheet",24),new SetPart("minecraft:leather",16),new SetPart("#minecraft:wool",8)),
        List.of(new SetPart("create:iron_sheet",24),new SetPart("create:andesite_alloy",16),new SetPart("minecraft:leather",16),new SetPart("#minecraft:wool",8)),
        List.of(new SetPart("eternal_defense:reinforced_plating",21),new SetPart("create:precision_mechanism",7),new SetPart("create:andesite_alloy",16),new SetPart("minecraft:leather",16),new SetPart("#minecraft:wool",8)),
        List.of(new SetPart("eternal_defense:reinforced_plating",24),new SetPart("create:precision_mechanism",7),new SetPart("minecraft:netherite_scrap",4),new SetPart("create:andesite_alloy",16),new SetPart("minecraft:leather",16),new SetPart("#minecraft:wool",8)),
        List.of(new SetPart("eternal_defense:reinforced_plating",28),new SetPart("create:precision_mechanism",7),new SetPart("minecraft:netherite_scrap",4),new SetPart("eternal_defense:resonance_coil",6),new SetPart("create:andesite_alloy",16),new SetPart("minecraft:leather",16),new SetPart("#minecraft:wool",8)));
    static List<SetPart> armorSet(int age){return ARMOR_SETS.get(Math.max(1,Math.min(5,age))-1);}
    /** Optics and NVGs that give no armor: their own parts, more precision mechanisms at Exotic */
    static List<SetPart> vision(int age){return List.of(new SetPart("minecraft:quartz",4),new SetPart("minecraft:redstone",8),new SetPart("minecraft:amethyst_shard",4),new SetPart("create:precision_mechanism",age>=5?2:1));}
}
