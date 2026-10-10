package dev.createarsenal.beacon;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import java.util.List;
import java.util.function.Supplier;

/**
 * Where every upgrade price lives, per edition.
 * <ul>
 * <li><b>Pack</b> (Create): everything that upgrades (beacon branches, repairs, cannon) is paid in the factory parts of the mod
 *     (reinforced plating, logistics modules, resonance coils, restoration matrices). Ardent Energy only buys flares, the cannon itself
 *     and goods at the Exchange; farmers can buy parts there at a premium, factories can sell their surplus.</li>
 * <li><b>Standalone</b> (no Create): there is no factory, so upgrades are paid in Ardent Energy, and its drop rate is a little higher.</li>
 * </ul>
 * Rough income that the numbers are tuned against (Ardent Energy, standalone / pack): a raid pays a guaranteed 2+2t / 1+t for reward tier t,
 * plus about one drop per 12 / 15 kills of its attackers (a tier 3 raid has some 80), plus a bonus for the special raid types.
 */
final class Economy {
    private Economy(){}
    /** A price: {@code part} is null when it is paid in Ardent Energy. */
    record Price(Supplier<Item> part,int amount){
        boolean ardent(){return part==null;}
        Item item(){return part==null?ArsenalBeacon.ARDENT_ENERGY.get():part.get();}
    }
    static boolean standalone(){return BuildFlavor.STANDALONE;}

    // ---- beacon ----------------------------------------------------------------------------------------------------
    /** Standalone beacon upgrades in Ardent Energy by the level being bought (0-based): quick at first, steeper later. */
    static final int[] BEACON_ARDENT={4,8,14,22,32};
    static int beaconAmount(int level){return standalone()?BEACON_ARDENT[Math.max(0,Math.min(4,level))]:Rules.upgradeCost(level);}
    /** Pack edition: the factory part each beacon branch is paid in (0.0.18: Defense in plating instead of coils). The Vertical zone takes all four. */
    static String beaconPart(String branch){
        return switch(branch){case "core","defense"->"reinforced_plating";case "logistics"->"logistics_module";case "restoration"->"restoration_matrix";case "reconnaissance"->"resonance_coil";default->"";};
    }
    static final List<String> BEACON_PARTS=List.of("reinforced_plating","logistics_module","resonance_coil","restoration_matrix");
    /** One part of a beacon upgrade's price: an item id ({@code ardent_energy} in the standalone edition) and how many. */
    record Part(String id,int amount){}
    /**
     * What buying the next level of a beacon branch costs. Standalone: Ardent Energy. Pack: 8, 16, 32... of the branch's part; the Vertical
     * zone instead takes a quarter of that of every one of the four parts (2 of each at first; 0.0.18, it was 8 logistics modules).
     */
    static List<Part> beaconCost(String branch,int level){
        if(standalone())return List.of(new Part("ardent_energy",beaconAmount(level)));
        int amount=Rules.upgradeCost(level);
        if(branch.equals("vertical"))return BEACON_PARTS.stream().map(id->new Part(id,amount/BEACON_PARTS.size())).toList();
        return List.of(new Part(beaconPart(branch),amount));
    }
    static int repairAmount(){return standalone()?6:8;}
    static final int[] KITCHEN_ARDENT={4,8,14},KITCHEN_PLATING={8,16,32};
    static Price kitchen(int mk){return mk<1||mk>=4?null:new Price(standalone()?null:ArsenalBeacon.PLATING::get,(standalone()?KITCHEN_ARDENT:KITCHEN_PLATING)[mk-1]);}

    static List<WeaponPlatform.Cost> stationCosts(String kind){
        if(kind.equals("support"))return List.of(WeaponPlatform.cost("minecraft:iron_ingot",20),WeaponPlatform.cost("minecraft:copper_ingot",12),WeaponPlatform.cost("minecraft:redstone",12),WeaponPlatform.cost("minecraft:glass",4));
        if(kind.equals("exchange"))return List.of(WeaponPlatform.cost("minecraft:iron_ingot",12),WeaponPlatform.cost("minecraft:copper_ingot",20),WeaponPlatform.cost("minecraft:redstone",6),WeaponPlatform.cost("minecraft:glass",4));
        if(BuildFlavor.STANDALONE)return List.of(WeaponPlatform.cost("minecraft:iron_ingot",16),WeaponPlatform.cost("minecraft:copper_ingot",16),WeaponPlatform.cost("minecraft:redstone",8));return List.of(WeaponPlatform.cost("minecraft:iron_ingot",16),WeaponPlatform.cost(kind.equals("ammo")?"minecraft:copper_ingot":"minecraft:gold_ingot",16),WeaponPlatform.cost("create:andesite_alloy",8));}

    static List<WeaponPlatform.Cost> fabrication(String id){
        return switch(id){
            case "gun_platform","ammo_platform","attachment_platform","armor_platform"->WeaponPlatform.purchaseCosts(id.replace("_platform",""));
            case "support_platform"->WeaponPlatform.purchaseCosts("support");
            case "exchange_shop"->WeaponPlatform.purchaseCosts("exchange");
            case "support_cannon"->List.of(WeaponPlatform.cost("arsenal_beacon:ardent_energy",12));
            // Tactical Operations (0.0.20): the table and the satellite take the same plain materials in both editions, plus a part (pack) or Ardent Energy (standalone)
            case "command_table"->List.of(WeaponPlatform.cost("minecraft:iron_ingot",24),WeaponPlatform.cost("minecraft:copper_ingot",16),WeaponPlatform.cost("minecraft:redstone",16),WeaponPlatform.cost("minecraft:glass",8),
                standalone()?WeaponPlatform.cost("arsenal_beacon:ardent_energy",COMMAND_TABLE_ARDENT):WeaponPlatform.cost("arsenal_beacon:reinforced_plating",COMMAND_TABLE_PLATING));
            case "satellite_beacon"->List.of(WeaponPlatform.cost("minecraft:iron_ingot",16),WeaponPlatform.cost("minecraft:copper_ingot",24),WeaponPlatform.cost("minecraft:redstone",8),WeaponPlatform.cost("minecraft:gold_ingot",4),
                standalone()?WeaponPlatform.cost("arsenal_beacon:ardent_energy",SATELLITE_ARDENT):WeaponPlatform.cost("arsenal_beacon:resonance_coil",SATELLITE_COILS));
            case "mess_hall_mk1"->List.of(WeaponPlatform.cost(standalone()?"arsenal_beacon:ardent_energy":"arsenal_beacon:reinforced_plating",4),WeaponPlatform.cost("minecraft:iron_ingot",8));
            case "cook_pot"->List.of(WeaponPlatform.cost("minecraft:iron_ingot",14),WeaponPlatform.cost("minecraft:oak_sign",1));
            case "milk_dispenser"->List.of(WeaponPlatform.cost("minecraft:iron_ingot",8),WeaponPlatform.cost("minecraft:glass",4),WeaponPlatform.cost("minecraft:copper_ingot",4));
            case "bowl_dispenser"->List.of(WeaponPlatform.cost("minecraft:iron_ingot",8),WeaponPlatform.cost("minecraft:glass",4),new WeaponPlatform.Cost(Ingredient.of(net.minecraft.tags.ItemTags.PLANKS),4));
            default->List.of();
        };
    }

    // ---- cannon upgrades --------------------------------------------------------------------------------------------------
    /** Pack: the part that pays for each upgrade and how many per level. */
    private static Supplier<Item> packPart(CannonUpgrades.Upgrade u){
        return switch(u){
            case TRAVERSE,AOE->ArsenalBeacon.PLATING::get;
            case RATE,DAMAGE,SLOW->ArsenalBeacon.COIL::get;
            case VOLLEY,PORTAL->ArsenalBeacon.LOGISTICS::get;
            case QUANTUM,AURA,DIMENSION->ArsenalBeacon.REPAIR::get;
        };
    }
    static final java.util.Map<CannonUpgrades.Upgrade,int[]> PACK_AMOUNTS=new java.util.EnumMap<>(java.util.Map.of(
        CannonUpgrades.Upgrade.TRAVERSE,new int[]{6,12,24},CannonUpgrades.Upgrade.RATE,new int[]{5,10,20},CannonUpgrades.Upgrade.VOLLEY,new int[]{6,12,24},
        CannonUpgrades.Upgrade.DAMAGE,new int[]{6,12,24},CannonUpgrades.Upgrade.QUANTUM,new int[]{12},CannonUpgrades.Upgrade.SLOW,new int[]{10},
        CannonUpgrades.Upgrade.PORTAL,new int[]{10},CannonUpgrades.Upgrade.AURA,new int[]{8},CannonUpgrades.Upgrade.AOE,new int[]{5,10,20},CannonUpgrades.Upgrade.DIMENSION,new int[]{20}));
    /** The price of the next level, or null when maxed. */
    static Price cannon(CannonUpgrades.Upgrade u,int level){
        if(level<0||level>=u.max())return null;
        if(standalone())return new Price(null,u.prices[level]);
        return new Price(packPart(u),PACK_AMOUNTS.get(u)[level]);
    }
    /** Takes the price from the player (creative players pay nothing). False, with nothing taken, if short. */
    static boolean pay(ServerPlayer p,Price price){
        if(price.ardent())return ArdentEnergy.spend(p,price.amount());
        return InventoryPayment.commit(p,List.of(new WeaponPlatform.Cost(Ingredient.of(price.item()),price.amount())),ItemStack.EMPTY);
    }
    static int have(net.minecraft.world.entity.player.Player p,Item item){
        int n=0;for(var s:p.getInventory().items)if(s.is(item))n+=s.getCount();for(var s:p.getInventory().offhand)if(s.is(item))n+=s.getCount();return n;
    }

    // ---- Tactical Operations (0.0.20) ---------------------------------------------------------------------------------------------
    static final int COMMAND_TABLE_ARDENT=6,COMMAND_TABLE_PLATING=4,SATELLITE_ARDENT=4,SATELLITE_COILS=2;
    /** Satellite Beacon upgrades by the Mk being bought (II, III): resonance coils (pack) or Ardent Energy (standalone). */
    static final int[] SATELLITE_UPGRADE_COILS={16,32},SATELLITE_UPGRADE_ARDENT={10,20};
    /** The price of the satellite's next Mk from {@code mk}, or null at Mk III. */
    static Price satellite(int mk){
        if(mk<1||mk>=TacticalRules.MAX_MK)return null;
        return standalone()?new Price(null,SATELLITE_UPGRADE_ARDENT[mk-1]):new Price(ArsenalBeacon.COIL::get,SATELLITE_UPGRADE_COILS[mk-1]);
    }
    /** A raid's guaranteed Ardent Energy at reward tier t (capped at 10): standalone 2+2t, pack 1+t. A hunt pays a share of it. */
    static int huntGuaranteed(int tier,boolean standalone){int t=Math.max(0,Math.min(10,tier));return standalone?2+2*t:1+t;}
    /** The Exchange sells 16 Universal Ammo Coins for 3 Ardent Energy: coins in a hunt's pay are valued at that rate. */
    static final double COINS_PER_ARDENT=16/3.0,HUNT_ARDENT_SHARE=.6,HUNT_COIN_SHARE=.4;
    /** What a hunt pays into the beacon's reward chest. */
    record HuntPay(int ardent,int coins){}
    /** Pure: budget B = class share x guaranteed raid Ardent; Ardent = round(0.6 B), coins = round(0.4 B x 16/3). */
    static HuntPay hunt(TacticalRules.Kind kind,int tier,boolean standalone){
        double budget=kind.budget*huntGuaranteed(tier,standalone);
        return new HuntPay((int)Math.round(HUNT_ARDENT_SHARE*budget),(int)Math.round(HUNT_COIN_SHARE*budget*COINS_PER_ARDENT));
    }
    static HuntPay hunt(TacticalRules.Kind kind,int tier){return hunt(kind,tier,standalone());}

    // ---- support gear (Ardent Energy in both editions) ----------------------------------------------------------------------
    // see SupportRules: cannon 12, supply flare 3, return flare 2, fire support flare 6.
}
