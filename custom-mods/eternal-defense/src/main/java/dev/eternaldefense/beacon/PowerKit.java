package dev.eternaldefense.beacon;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import java.util.List;

/** The power network and defense kit: its numbers, its placement rule and the dynamo's side of Create */
final class PowerKit {
    private PowerKit(){}

    // The network
    /** A pole powers what stands within this many blocks of it, from AURA_DOWN below to AURA_UP above */
    static final int AURA=8,AURA_DOWN=4,AURA_UP=6;
    /** The longest wire span, in blocks */
    static final int WIRE_MAX=16;
    /** A dynamo turning slower than this makes no power */
    static final int MIN_RPM=8;
    /** Stress a shock box asks for, per RPM of the dynamos */
    static final int SHOCK_STRESS=8;
    /** Ticks a shock box keeps its wire live after the power is lost, and barbed wire blocks it reaches along a run */
    static final int SHOCK_CHARGE=600,SHOCK_REACH=48;
    /** The grid is worked out again every this many ticks */
    static final int GRID_TICKS=10;

    /** Pure: within a pole's reach (horizontal radius AURA, AURA_DOWN below to AURA_UP above) */
    static boolean inAura(int dx,int dy,int dz){return dx*dx+dz*dz<=AURA*AURA&&dy>=-AURA_DOWN&&dy<=AURA_UP;}
    static boolean inAura(BlockPos pole,BlockPos at){return inAura(at.getX()-pole.getX(),at.getY()-pole.getY(),at.getZ()-pole.getZ());}
    /** Pure: a wire span may join these two blocks */
    static boolean spanOk(BlockPos a,BlockPos b){return !a.equals(b)&&a.distSqr(b)<=WIRE_MAX*WIRE_MAX;}
    /** Pure: each turning dynamo of a network takes an equal share of its stress (per RPM) */
    static float share(float demand,int turning){return turning<=0?0:demand/turning;}
    /** Pure: a network runs when a dynamo turns fast enough and none of them is overstressed */
    static boolean running(int turning,boolean anyOverstressed){return turning>0&&!anyOverstressed;}

    /** The Create side: a dynamo under a pole */
    interface Dynamo {
        /** Its speed, overstressed or not, in RPM (never negative) */
        float rpm();
        boolean overstressed();
        /** The stress per RPM it puts on its Create network */
        void demand(float perRpm);
    }

    // Placement: a beacon's zone or a FOB's zone
    /** Where defense gear may stand: null, or a language key suffix under support.zone */
    static String zone(Level level,BlockPos pos){
        String beacon=BaseZone.problem(level,pos);
        if(beacon==null||!(level instanceof ServerLevel server))return null;
        if(server.dimension()==Level.OVERWORLD&&Fobs.zoneAt(server,pos)!=null)return null;
        return beacon.equals("overworld")?beacon:"kit_outside";
    }
    /** Defense gear: placed in a beacon's zone or a FOB's zone only */
    static class KitItem extends BaseZone.ZoneItem {
        final String tip;
        KitItem(Block block,String tip){super(block);this.tip=tip;}
        @Override String zone(Level level,BlockPos pos){return PowerKit.zone(level,pos);}
        @Override public void appendHoverText(ItemStack stack,Level level,List<Component> lines,TooltipFlag flag){
            if(tip!=null)lines.add(Component.translatable("tooltip.eternal_defense."+tip).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("tooltip.eternal_defense.kit_zone").withStyle(ChatFormatting.DARK_AQUA));
        }
    }
}
