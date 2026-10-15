package dev.eternaldefense.beacon;

import net.minecraft.server.level.ServerPlayer;
import java.util.*;

/** Raid modifiers: harder raids, chosen at the beacon for the next raid, that pay more and drop more Ardent Energy. */
final class RaidModifiers {
    private RaidModifiers(){}
    /** One modifier: its id, the extra payout of the raid's completion cache (percent) and the Ardent drop chance it adds (percent of the base chance) */
    record Mod(String id,int payout,int drops){}
    static final List<Mod> MODS=List.of(
        new Mod("reinforcements",20,8),
        new Mod("elite_cadre",15,9),
        new Mod("hardened",15,8),
        new Mod("forced_march",10,6),
        new Mod("fog_of_war",10,5),
        new Mod("exposed_beacon",15,6));
    /** A special raid chosen here (one at a time): its own Ardent bonus, and this much more drop chance (with every modifier, half the base chance more) */
    static final int SPECIAL_DROPS=8;
    /** What the modifiers change */
    static final double REINFORCEMENTS=1.3,HARDENED_HEALTH=.35,FORCED_MARCH_SPEED=.2,EXPOSED_BEACON=1.5;
    static final int ELITE_CADRE=1;

    static Mod of(String id){for(var m:MODS)if(m.id.equals(id))return m;return null;}
    /** Pure: extra payout of a raid with these modifiers (percent) */
    static int payoutPercent(Collection<String> mods){int p=0;for(String id:mods){var m=of(id);if(m!=null)p+=m.payout;}return p;}
    /** Pure: extra Ardent drop chance of its mobs (percent): the modifiers and a special raid */
    static int dropPercent(Collection<String> mods,String raidType){int p=RaidTypes.special(raidType)?SPECIAL_DROPS:0;for(String id:mods){var m=of(id);if(m!=null)p+=m.drops;}return p;}
    /** Pure: a count raised by a payout bonus, rounded up */
    static int raised(int count,int percent){return percent<=0?count:(int)Math.ceil(count*(1+percent/100.0));}
    /** Pure: attackers of a wave of this raid type with these modifiers */
    static int attackers(int ordinary,String type,Collection<String> mods){int n=RaidTypes.count(type,ordinary);return mods.contains("reinforcements")?(int)Math.ceil(n*REINFORCEMENTS):n;}
    /** Pure: elites a wave on average with these modifiers */
    static double elites(int level,Collection<String> mods){return Elites.perWave(level)+(mods.contains("elite_cadre")?ELITE_CADRE:0);}
    static boolean on(CampaignData d,String id){return d.phase.equals("raid")&&d.activeMods.contains(id);}

    /** A press in Raid Control: a modifier on or off, or a special raid chosen (special:air) or cleared */
    static String toggle(ServerPlayer p,String id){
        var d=CampaignData.get(p.server.overworld());
        if(!d.installed()||!EternalDefense.near(p,d)||!d.phase.equals("preparation"))return "Choose raid modifiers near the beacon between raids.";
        if(!d.introCompleted)return "Finish the introductory raid first.";
        if(id.startsWith("special:")){
            String type=id.substring(8);if(!RaidTypes.special(type)&&!type.equals("normal"))return "Unknown special raid.";
            d.nextRaidType=d.nextRaidType.equals(type)?"normal":type;d.setDirty();
            BeaconNetwork.announceOthers(p,"next_raid",0,0,d.nextRaidType,"");
            return d.nextRaidType.equals("normal")?"Next raid: ordinary.":"Next raid: "+RaidTypes.name(type).getString()+".";
        }
        return flip(d,id);
    }
    /** A modifier on or off for the next raid, whoever asks (the screen checks where and when first) */
    static String flip(CampaignData d,String id){
        if(of(id)==null)return "Unknown raid modifier.";
        if(!d.chosenMods.remove(id))d.chosenMods.add(id);d.setDirty();
        return "Next raid: payout +"+payoutPercent(d.chosenMods)+"%, Ardent from raid mobs +"+dropPercent(d.chosenMods,d.nextRaidType)+"%.";
    }
    /** The raid starts: its modifiers take effect and the choice is cleared for the raid after */
    static void begin(CampaignData d){d.activeMods.clear();d.activeMods.addAll(d.chosenMods);d.chosenMods.clear();d.setDirty();}
}
