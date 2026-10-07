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
    static int repairAmount(){return standalone()?6:8;}

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

    // ---- support gear (Ardent Energy in both editions) ----------------------------------------------------------------------
    // see SupportRules: cannon 12, supply flare 3, return flare 2, fire support flare 6.
}
