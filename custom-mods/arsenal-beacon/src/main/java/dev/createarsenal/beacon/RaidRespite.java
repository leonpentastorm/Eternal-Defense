package dev.createarsenal.beacon;
import net.minecraft.server.level.ServerPlayer;
import java.util.List;

/** Shared active-play truce: escalating purchases reset only after the next raid begins. */
final class RaidRespite {
    static final long DAYS=5*24000L;
    static List<WeaponPlatform.Cost> costs(CampaignData d){int multiplier=1+Math.min(999,d.respitePurchases);return List.of(WeaponPlatform.cost("create:brass_ingot",16*multiplier),WeaponPlatform.cost("create:precision_mechanism",2*multiplier),WeaponPlatform.cost("minecraft:gold_ingot",4*multiplier));}
    static int previewTier(CampaignData d,int base){return RaidSelection.tier(d,base);}
    static String buy(ServerPlayer p){
        var d=CampaignData.get(p.server.overworld());
        if(!d.installed()||!ArsenalBeacon.near(p,d)||!d.phase.equals("preparation"))return "Buy a raid break near the beacon during preparation.";
        if(!d.introCompleted)return "Complete the introductory raid before buying a raid break.";
        if(d.respitePurchases>=1000)return "This preparation period already has the maximum 5,000 purchased days.";
        if(!p.isCreative()&&!InventoryPayment.commit(p,costs(d),net.minecraft.world.item.ItemStack.EMPTY))return "Missing raid-break materials. Nothing consumed.";
        d.respiteTicks+=DAYS;d.respitePurchases++;d.nextRaidBonus=Math.min(3,d.nextRaidBonus+1);d.setDirty();
        return "Added five active days without scheduled raids. Next-raid bonus: +"+d.nextRaidBonus+" tiers (within your chosen raid cap). Ambient hostiles remain active.";
    }
    static void started(CampaignData d){d.respiteTicks=0;d.respitePurchases=0;d.nextRaidBonus=0;}
    static boolean waitTick(CampaignData d){if(d.respiteTicks<=0)return false;d.respiteTicks--;if(d.respiteTicks%1200==0)d.setDirty();return true;}
}
