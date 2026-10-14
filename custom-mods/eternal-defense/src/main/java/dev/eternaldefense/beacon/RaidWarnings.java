package dev.eternaldefense.beacon;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.ChatFormatting;

/** Chat warnings for players who are away from the base */
final class RaidWarnings {
    private RaidWarnings(){}
    static final int STEP=6000,FIRST=24000;
    private static int last=Integer.MAX_VALUE;
    /** Buckets: more than a day left = none, 4 = up to 24h, 3 = 18h, 2 = 12h, 1 = 6h, 0 = countdown over. */
    static int bucket(long remaining){
        if(remaining>FIRST)return Integer.MAX_VALUE;
        if(remaining<=0)return 0;
        return (int)((remaining+STEP-1)/STEP);
    }
    static void reset(){last=Integer.MAX_VALUE;}
    /** True when moving from previous to now should speak. */
    static boolean shouldWarn(int previous,int now){return now!=Integer.MAX_VALUE&&now<previous;}
    static void tick(ServerLevel l,CampaignData d){
        if(!d.introCompleted||d.respiteTicks>0)return;
        int now=bucket(Rules.intervalDays(d.rewardTier)*24000L-d.preparationTicks);
        if(!shouldWarn(last,now)){if(now>last)last=now;return;}
        last=now;
        Component text=now==0?Component.translatable("gui.eternal_defense.warning.imminent"):Component.translatable("gui.eternal_defense.warning.raid",now*6);
        l.getServer().getPlayerList().broadcastSystemMessage(Component.literal("[Eternal Defense] ").withStyle(ChatFormatting.GOLD).append(text.copy().withStyle(ChatFormatting.YELLOW)),false);
    }
}
