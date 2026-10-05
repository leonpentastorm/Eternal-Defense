package dev.createarsenal.beacon;

import net.minecraft.server.level.ServerPlayer;

/** Difficulty and payout share one cap. Building and purchased upgrades are never erased. */
final class RaidSelection {
    static int tier(CampaignData d,int base){return Rules.selectedTier(d.raidLimit,base,d.nextRaidBonus);}
    static String select(ServerPlayer p,String value){
        var d=CampaignData.get(p.server.overworld());
        if(!d.installed()||!ArsenalBeacon.near(p,d)||!d.phase.equals("preparation")||!d.introCompleted)return "Change the raid level near the beacon between raids.";
        int requested;try{requested=Integer.parseInt(value);}catch(NumberFormatException ex){return "Invalid raid level.";}
        if(requested<0||requested>10)return "Choose a raid level from 0 through 10.";
        d.raidLimit=requested;d.setDirty();
        return "Raid level cap: "+requested+". Rewards match the level fought. Your base and Logistics upgrades are kept.";
    }
    static String start(ServerPlayer p){
        var l=p.server.overworld();var d=CampaignData.get(l);
        if(!d.installed()||!ArsenalBeacon.near(p,d)||!d.phase.equals("preparation")||d.health<=0)return "Start a raid near an enabled beacon between raids.";
        if(!d.introCompleted)return "Finish the introductory raid first.";
        if(!d.damage.isEmpty()||!d.destroyedTurrets.isEmpty())return "Wait for pending base restoration before starting another raid.";
        ArsenalBeacon.begin(l,d);return "Raid requested at your selected level. Purchased break time ends when the snapshot finishes. Defend the beacon!";
    }
}
