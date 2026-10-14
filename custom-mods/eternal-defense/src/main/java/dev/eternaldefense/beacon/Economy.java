package dev.eternaldefense.beacon;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import java.util.List;
import java.util.function.Supplier;

/**
 * Where every upgrade price lives. Everything that upgrades (beacon branches, repairs, cannon) is paid in the factory parts of the mod
 * (reinforced plating, logistics modules, resonance coils, restoration matrices), made with Create. Ardent Energy buys flares, the cannon
 * itself and goods at the Exchange; farmers can buy parts there at a premium, factories can sell their surplus.
 * Rough income that the numbers are tuned against (Ardent Energy): a raid pays a guaranteed 1+t for reward tier t, plus about one drop per
 * 15 kills of its attackers (a tier 3 raid has some 80), plus a bonus for the special raid types.
 * 0.0.41: the standalone edition (no Create, upgrades in Ardent Energy) is discontinued; its prices left with it (last built at 0.0.40).
 */
final class Economy {
    private Economy(){}
    /** A price: {@code part} is null when it is paid in Ardent Energy. */
    record Price(Supplier<Item> part,int amount){
        boolean ardent(){return part==null;}
        Item item(){return part==null?EternalDefense.ARDENT_ENERGY.get():part.get();}
    }

    // ---- beacon ----------------------------------------------------------------------------------------------------
    /** The factory part each beacon branch is paid in (0.0.18: Defense in plating instead of coils). The Vertical zone takes all four. */
    static String beaconPart(String branch){
        return switch(branch){case "core","defense"->"reinforced_plating";case "logistics"->"logistics_module";case "restoration"->"restoration_matrix";case "reconnaissance"->"resonance_coil";default->"";};
    }
    static final List<String> BEACON_PARTS=List.of("reinforced_plating","logistics_module","resonance_coil","restoration_matrix");
    /** One part of a beacon upgrade's price: an item id and how many. */
    record Part(String id,int amount){}
    /**
     * What buying the next level of a beacon branch costs: 8, 16, 32... of the branch's part; the Vertical
     * zone instead takes a quarter of that of every one of the four parts (2 of each at first; 0.0.18, it was 8 logistics modules).
     */
    static List<Part> beaconCost(String branch,int level){
        int amount=Rules.upgradeCost(level);
        if(branch.equals("vertical"))return BEACON_PARTS.stream().map(id->new Part(id,matrices(id,amount/BEACON_PARTS.size()))).toList();
        return List.of(new Part(beaconPart(branch),matrices(beaconPart(branch),amount)));
    }
    /**
     * 0.0.28 (owner): every pack price paid in Beacon Restoration Matrices is half what it was (rounded up), and the matrix recipe makes 8 instead
     * of 4 (pack-integration/kubejs/server_scripts/15_beacon_recipes.js). Pure: {@code amount} of part {@code id}, halved when it is the matrix.
     */
    static int matrices(String id,int amount){return id.endsWith("restoration_matrix")?(amount+1)/2:amount;}
    static int repairAmount(){return 8;}
    static final int[] KITCHEN_PLATING={8,16,32};
    static Price kitchen(int mk){return mk<1||mk>=4?null:new Price(EternalDefense.PLATING::get,KITCHEN_PLATING[mk-1]);}
    /** 0.0.35 (owner): Drink Station upgrades to Mk II, III and IV, like the Mess Hall's: 8/16/32 Reinforced Beacon Plating. */
    static final int[] DRINKS_PLATING={8,16,32};
    /** 0.0.38: the MRE Station, as the Mess Hall Mk I: 4 Reinforced Beacon Plating and 8 iron, plus a smoker. */
    static final int MRE_STATION_KEY=4,MRE_STATION_IRON=8;
    /**
     * 0.0.40: the Logistics Center Hub. Twice the Mess Hall Mk IV's last upgrade (32 plating) and more than a whole Mess Hall brought to Mk IV
     * (60 plating), so it is clearly the dearest installation: 64 plating, plus 64 iron, 16 chests (its 270 slots) and 8 hoppers.
     */
    static final int HUB_PLATING=64,HUB_IRON=64,HUB_CHESTS=16,HUB_HOPPERS=8;
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
            // Tactical Operations (0.0.20): the table and the satellite take plain materials plus a factory part
            case "command_table"->List.of(WeaponPlatform.cost("minecraft:iron_ingot",24),WeaponPlatform.cost("minecraft:copper_ingot",16),WeaponPlatform.cost("minecraft:redstone",16),WeaponPlatform.cost("minecraft:glass",8),
                WeaponPlatform.cost("eternal_defense:reinforced_plating",COMMAND_TABLE_PLATING));
            case "satellite_beacon"->List.of(WeaponPlatform.cost("minecraft:iron_ingot",16),WeaponPlatform.cost("minecraft:copper_ingot",24),WeaponPlatform.cost("minecraft:redstone",8),WeaponPlatform.cost("minecraft:gold_ingot",4),
                WeaponPlatform.cost("eternal_defense:resonance_coil",SATELLITE_COILS));
            // Forward Operating Bases (0.0.23): plain materials plus logistics modules and plating
            case "fob_beacon"->List.of(WeaponPlatform.cost("minecraft:iron_ingot",FOB_IRON),WeaponPlatform.cost("minecraft:copper_ingot",FOB_COPPER),WeaponPlatform.cost("minecraft:redstone",FOB_REDSTONE),WeaponPlatform.cost("minecraft:glass",FOB_GLASS),WeaponPlatform.cost("eternal_defense:logistics_module",FOB_LOGISTICS),WeaponPlatform.cost("eternal_defense:reinforced_plating",FOB_PLATING));
            // the supply rail (0.0.24): the same plain materials for both gates, plus a logistics module
            // 0.0.31: two chests, for the Receiving Gate's tribute storage (the owner put them in both gates' recipes)
            case "supply_delivery_gate","supply_receiving_gate"->List.of(WeaponPlatform.cost("minecraft:iron_ingot",GATE_IRON),WeaponPlatform.cost("minecraft:copper_ingot",GATE_COPPER),WeaponPlatform.cost("minecraft:redstone",GATE_REDSTONE),WeaponPlatform.cost("minecraft:chest",GATE_CHESTS),
                WeaponPlatform.cost("eternal_defense:logistics_module",GATE_LOGISTICS));
            // the Tactical Enchanting Table (0.0.27): iron, obsidian and books, plus resonance coils
            case "tactical_enchanting_table"->List.of(WeaponPlatform.cost("minecraft:iron_ingot",ENCHANT_IRON),WeaponPlatform.cost("minecraft:obsidian",ENCHANT_OBSIDIAN),WeaponPlatform.cost("minecraft:book",ENCHANT_BOOKS),
                WeaponPlatform.cost("eternal_defense:resonance_coil",ENCHANT_COILS));
            // the Supply Catapult (0.0.33): timber, iron and rope, plus logistics modules
            case "supply_catapult"->List.of(new WeaponPlatform.Cost(Ingredient.of(net.minecraft.tags.ItemTags.LOGS),CATAPULT_LOGS),WeaponPlatform.cost("minecraft:iron_ingot",CATAPULT_IRON),WeaponPlatform.cost("minecraft:string",CATAPULT_STRING),
                WeaponPlatform.cost("eternal_defense:logistics_module",CATAPULT_LOGISTICS));
            case "mess_hall_mk1"->List.of(WeaponPlatform.cost("eternal_defense:reinforced_plating",4),WeaponPlatform.cost("minecraft:iron_ingot",8));
            case "cook_pot"->List.of(WeaponPlatform.cost("minecraft:iron_ingot",14),WeaponPlatform.cost("minecraft:oak_sign",1));
            case "milk_dispenser"->List.of(WeaponPlatform.cost("minecraft:iron_ingot",8),WeaponPlatform.cost("minecraft:glass",4),WeaponPlatform.cost("minecraft:copper_ingot",4));
            // 0.0.35 (owner): the drinks corner, in the kitchen's style: the station takes a brewing stand, the pot copper, the dispenser paper
            case "drink_mixer_mk1"->List.of(WeaponPlatform.cost("eternal_defense:reinforced_plating",4),WeaponPlatform.cost("minecraft:iron_ingot",8),WeaponPlatform.cost("minecraft:brewing_stand",1));
            case "drink_pot"->List.of(WeaponPlatform.cost("minecraft:copper_ingot",14),WeaponPlatform.cost("minecraft:oak_sign",1));
            case "cup_dispenser"->List.of(WeaponPlatform.cost("minecraft:iron_ingot",8),WeaponPlatform.cost("minecraft:glass",4),WeaponPlatform.cost("minecraft:paper",4));
            // 0.0.38 (owner): the MRE Station, in the Mess Hall's style; the smoker is its heat sealer
            case "mre_maker"->List.of(WeaponPlatform.cost("eternal_defense:reinforced_plating",MRE_STATION_KEY),WeaponPlatform.cost("minecraft:iron_ingot",MRE_STATION_IRON),WeaponPlatform.cost("minecraft:smoker",1));
            // 0.0.40 (owner): the Logistics Center Hub, the most expensive installation: 64 plating, with iron, chests and hoppers (proposed
            // numbers, for the owner to adjust: see HANDOFF)
            case "logistics_hub"->List.of(WeaponPlatform.cost("eternal_defense:reinforced_plating",HUB_PLATING),
                WeaponPlatform.cost("minecraft:iron_ingot",HUB_IRON),WeaponPlatform.cost("minecraft:chest",HUB_CHESTS),WeaponPlatform.cost("minecraft:hopper",HUB_HOPPERS));
            case "bowl_dispenser"->List.of(WeaponPlatform.cost("minecraft:iron_ingot",8),WeaponPlatform.cost("minecraft:glass",4),new WeaponPlatform.Cost(Ingredient.of(net.minecraft.tags.ItemTags.PLANKS),4));
            default->List.of();
        };
    }

    // ---- cannon upgrades --------------------------------------------------------------------------------------------------
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
    /** Takes the price, from the pack first and then the Home Zone's Hubs (creative players pay nothing). False, with nothing taken, if short. */
    static boolean pay(Supply.Where w,Price price){
        if(price.ardent())return ArdentEnergy.spend(w,price.amount());
        return InventoryPayment.commit(w,List.of(new WeaponPlatform.Cost(Ingredient.of(price.item()),price.amount())),ItemStack.EMPTY);
    }
    /** 0.0.40: how many of the item a request can reach: the pack, then the Home Zone's Hubs. */
    static int have(Supply.Where w,Item item){return Supply.count(w,item);}
    static int have(net.minecraft.world.entity.player.Player p,Item item){
        int n=0;for(var s:p.getInventory().items)if(s.is(item))n+=s.getCount();for(var s:p.getInventory().offhand)if(s.is(item))n+=s.getCount();return n;
    }

    // ---- Forward Operating Bases (0.0.23) ------------------------------------------------------------------------------------------
    /** The FOB Beacon at the Workshop: plain materials, then factory parts. */
    static final int FOB_IRON=16,FOB_COPPER=16,FOB_REDSTONE=8,FOB_GLASS=4,FOB_LOGISTICS=2,FOB_PLATING=2;
    /** Each supply gate (0.0.24): bought at the FOB shop (Delivery) or the Workshop (Receiving). */
    static final int GATE_IRON=16,GATE_COPPER=8,GATE_REDSTONE=8,GATE_CHESTS=2,GATE_LOGISTICS=1;
    /**
     * A FOB's repair (0.0.25): a base price while it is DOWN, plus each villager lost in its raids by level (Novice to Master), in Ardent
     * Energy (a raid pays 1+t of it).
     */
    static final int FOB_REPAIR_BASE=3;
    static final int[] FOB_REPAIR_VILLAGER={1,1,2,2,3};
    static int fobRepairBase(){return FOB_REPAIR_BASE;}
    static int fobRepairVillager(int level){return SosRules.villagerCost(FOB_REPAIR_VILLAGER,level);}
    /** The Supply Catapult (0.0.33). */
    static final int CATAPULT_LOGS=16,CATAPULT_IRON=8,CATAPULT_STRING=16,CATAPULT_LOGISTICS=2;
    /**
     * The specialty seller (0.0.33): Ardent Energy for one lot (a lot is worth one S-tier item, {@link CatapultRules#LOT_VALUE}): 2 (Ardent
     * Energy is scarce: a raid pays 1+t).
     */
    static final int SELLER_LOT=2;
    static int sellerLotPrice(){return SELLER_LOT;}
    /** The Tactical Enchanting Table at the Workshop, and its Mk I to III (a permanent discount): resonance coils. */
    static final int ENCHANT_IRON=12,ENCHANT_OBSIDIAN=4,ENCHANT_BOOKS=3,ENCHANT_COILS=2;
    static final int[] ENCHANT_UPGRADE_COILS={8,16,32};
    /** The price of the table's Mk {@code mk} (1 to 3), or null past Mk III. */
    static Price enchantingUpgrade(int mk){
        if(mk<1||mk>EnchantRules.MAX_MK)return null;
        return new Price(EternalDefense.COIL::get,ENCHANT_UPGRADE_COILS[mk-1]);
    }
    /** The flares of the FOB shop cost what they cost at the Support Platform. */
    static int fobFlare(String id){return switch(id){case "supply"->SupportRules.SUPPLY_FLARE_PRICE;case "return"->SupportRules.RETURN_FLARE_PRICE;default->SupportRules.FIRE_FLARE_PRICE;};}

    // ---- Tactical Operations (0.0.20) ---------------------------------------------------------------------------------------------
    static final int COMMAND_TABLE_PLATING=4,SATELLITE_COILS=2;
    /** Satellite Beacon upgrades by the Mk being bought (II, III): resonance coils. */
    static final int[] SATELLITE_UPGRADE_COILS={16,32};
    /** The price of the satellite's next Mk from {@code mk}, or null at Mk III. */
    static Price satellite(int mk){
        if(mk<1||mk>=TacticalRules.MAX_MK)return null;
        return new Price(EternalDefense.COIL::get,SATELLITE_UPGRADE_COILS[mk-1]);
    }
    /** A raid's guaranteed Ardent Energy at reward tier t (capped at 10): 1+t. A hunt pays a share of it. */
    static int huntGuaranteed(int tier){return 1+Math.max(0,Math.min(10,tier));}
    /** The Exchange sells 16 Universal Ammo Coins for 3 Ardent Energy: coins in a hunt's pay are valued at that rate. */
    static final double COINS_PER_ARDENT=16/3.0,HUNT_ARDENT_SHARE=.6,HUNT_COIN_SHARE=.4;
    /** What a hunt pays into the beacon's reward chest. */
    record HuntPay(int ardent,int coins){}
    /** 0.0.22 (owner): a hunt always pays at least this much, whatever the reward tier (a pack Patrol at tier 0 paid 0 and 1). */
    static final int HUNT_MIN_ARDENT=1,HUNT_MIN_COINS=2;
    /** 0.0.28 (owner): a Patrol pays twice what it did, at every tier (the minimum too): some bases are offered almost nothing but Patrols. */
    static final int PATROL_PAY_FACTOR=2;
    /**
     * Pure: budget B = class share x guaranteed raid Ardent; Ardent = round(0.6 B), coins = round(0.4 B x 16/3); never below the minimum; a
     * Patrol's pay then doubled.
     */
    static HuntPay hunt(TacticalRules.Kind kind,int tier){
        double budget=kind.budget*huntGuaranteed(tier);int factor=kind==TacticalRules.Kind.PATROL?PATROL_PAY_FACTOR:1;
        return new HuntPay(factor*Math.max(HUNT_MIN_ARDENT,(int)Math.round(HUNT_ARDENT_SHARE*budget)),factor*Math.max(HUNT_MIN_COINS,(int)Math.round(HUNT_COIN_SHARE*budget*COINS_PER_ARDENT)));
    }

    // ---- support gear (Ardent Energy) -----------------------------------------------------------------------------------------
    // see SupportRules: cannon 12, supply flare 3, return flare 2, fire support flare 6.
}
