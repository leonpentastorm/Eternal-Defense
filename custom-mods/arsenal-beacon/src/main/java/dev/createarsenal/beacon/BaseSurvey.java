package dev.createarsenal.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** Bounded read-only survey. Never force chunks or reuse its contents as a repair snapshot. */
final class BaseSurvey {
    private static final Map<ServerLevel,BaseSurvey> SURVEYS=new WeakHashMap<>();
    final Map<Long,BlockState> blocks=new HashMap<>();BlockPos origin;int radius,below,height,cursor;long next,serial;boolean scanning,incomplete;BaseScoring.Score score;
    static BaseSurvey get(ServerLevel l){return SURVEYS.computeIfAbsent(l,k->new BaseSurvey());}
    static void invalidate(ServerLevel l){var s=get(l);s.next=Math.min(s.next,l.getGameTime()+40);}
    static void request(ServerLevel l){get(l).next=0;}
    int total(){return (radius*2+1)*(radius*2+1)*height;}
    int percent(){return scanning?Math.min(99,cursor*100/Math.max(1,total())):score==null?0:100;}
    static void tick(ServerLevel l,CampaignData d){
        if(!d.installed()||d.active()||l.getServer().getPlayerList().getPlayers().isEmpty())return;var s=get(l);
        if(!d.beacon.equals(s.origin)||s.radius!=d.radius()||s.height!=d.height()||s.below!=d.below()||s.serial!=d.campaignSerial){s.origin=d.beacon;s.radius=d.radius();s.below=d.below();s.height=d.height();s.serial=d.campaignSerial;s.scanning=false;s.score=null;s.next=0;}
        if(!s.scanning&&l.getGameTime()>=s.next){s.blocks.clear();s.cursor=0;s.scanning=true;s.incomplete=false;}
        if(!s.scanning)return;int width=s.radius*2+1;
        for(int budget=0;budget<4096&&s.cursor<s.total();budget++,s.cursor++){
            int i=s.cursor;var p=s.origin.offset(i%width-s.radius,i/(width*width)-s.below,(i/width)%width-s.radius);
            if(p.getY()<l.getMinBuildHeight()||p.getY()>=l.getMaxBuildHeight())continue;
            if(!l.hasChunkAt(p)){s.incomplete=true;continue;}var state=l.getBlockState(p);if(!state.isAir())s.blocks.put(p.asLong(),state);
        }
        if(s.cursor>=s.total()){s.score=BaseScoring.analyze(d,s.blocks,BaseScoring.Ledger.get(l).placed);s.scanning=false;s.blocks.clear();s.next=l.getGameTime()+400;}
    }
}
