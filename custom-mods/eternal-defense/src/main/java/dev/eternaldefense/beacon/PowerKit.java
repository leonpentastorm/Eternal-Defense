package dev.eternaldefense.beacon;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
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
    /** Ticks a shock box keeps its wire live after the power is lost, and the most barbed wire blocks one box makes live */
    static final int SHOCK_CHARGE=600,SHOCK_REACH=15;
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
    /** Pure: stress units a network asks for: each turning dynamo's share times its speed */
    static float su(float share,double rpmSum){return (float)(share*rpmSum);}

    /** The Create side: a dynamo under a pole */
    interface Dynamo {
        /** Its speed, overstressed or not, in RPM (never negative) */
        float rpm();
        boolean overstressed();
        /** The stress per RPM it puts on its Create network */
        void demand(float perRpm);
        /** What its power network looked like this time, or null with no pole on it */
        default void report(PowerGrid.Summary network){}
    }

    // What a dynamo shows
    /** Pure: a dynamo's state, a language key suffix under power.dynamo */
    static String dynamoState(boolean pole,float rpm,boolean networkDown,boolean ownOverstressed){
        if(!pole)return "no_pole";if(rpm<MIN_RPM)return "still";if(networkDown)return ownOverstressed?"down_own":"down_other";return "working";
    }
    /** A dynamo's last report from its power network, kept on the dynamo and sent to the client */
    static final class Readout {
        boolean pole,running,over;int dynamos,turning,turrets,boxes;float su,perRpm;
        /** Takes a network's summary (null: no pole on it); true when anything shown changed */
        boolean take(PowerGrid.Summary s){
            boolean p=s!=null,r=p&&s.running(),o=p&&s.overstressed();int d=p?s.dynamos():0,t=p?s.turning():0,tu=p?s.turrets():0,b=p?s.boxes():0;float u=p?s.su():0,pr=p?s.demand():0;
            boolean changed=p!=pole||r!=running||o!=over||d!=dynamos||t!=turning||tu!=turrets||b!=boxes||Math.abs(u-su)>.5f||Math.abs(pr-perRpm)>1e-3f;
            pole=p;running=r;over=o;dynamos=d;turning=t;turrets=tu;boxes=b;su=u;perRpm=pr;return changed;
        }
        void write(CompoundTag tag){var t=new CompoundTag();t.putBoolean("Pole",pole);t.putBoolean("Running",running);t.putBoolean("Over",over);t.putInt("Dynamos",dynamos);t.putInt("Turning",turning);
            t.putInt("Turrets",turrets);t.putInt("Boxes",boxes);t.putFloat("Su",su);t.putFloat("PerRpm",perRpm);tag.put("Power",t);}
        void read(CompoundTag tag){var t=tag.getCompound("Power");pole=t.getBoolean("Pole");running=t.getBoolean("Running");over=t.getBoolean("Over");dynamos=t.getInt("Dynamos");turning=t.getInt("Turning");
            turrets=t.getInt("Turrets");boxes=t.getInt("Boxes");su=t.getFloat("Su");perRpm=t.getFloat("PerRpm");}
        String state(float rpm,boolean ownOverstressed){return dynamoState(pole,rpm,over,ownOverstressed);}
        /** The lines a right-click on the dynamo shows: its state, its own stress, its power network's, its Create network's */
        List<Component> lines(Component name,float rpm,float ownPerRpm,boolean ownOverstressed,float used,float capacity,boolean createNetwork){
            var out=new java.util.ArrayList<Component>();String state=state(rpm,ownOverstressed);
            var color=switch(state){case "working"->ChatFormatting.AQUA;case "down_own","down_other"->ChatFormatting.RED;default->ChatFormatting.GOLD;};
            out.add(Component.translatable(K+state,name,PowerPoles.fmt(rpm),MIN_RPM).withStyle(color));
            if(pole){
                out.add(Component.translatable(K+"own",PowerPoles.su(ownPerRpm*rpm),PowerPoles.fmt(ownPerRpm)).withStyle(ChatFormatting.GRAY));
                out.add(Component.translatable(K+"network",PowerPoles.su(su),turning,turrets,boxes).withStyle(ChatFormatting.GRAY));
            }
            if(createNetwork)out.add(Component.translatable(K+"create",PowerPoles.su(used),PowerPoles.su(capacity)).withStyle(used>capacity?ChatFormatting.RED:ChatFormatting.GRAY));
            return out;
        }
        /** The lines Engineer's Goggles show under Create's own */
        void goggles(List<Component> out,float rpm,float ownPerRpm,boolean ownOverstressed){
            String state=state(rpm,ownOverstressed);
            out.add(Component.literal("    ").append(Component.translatable(G+"title").withStyle(ChatFormatting.GRAY)));
            var color=switch(state){case "working"->ChatFormatting.AQUA;case "down_own","down_other"->ChatFormatting.RED;default->ChatFormatting.GOLD;};
            out.add(Component.literal("     ").append(Component.translatable(G+state,MIN_RPM).withStyle(color)));
            if(!pole)return;
            out.add(Component.literal("     ").append(Component.translatable(G+"own",PowerPoles.su(ownPerRpm*rpm)).withStyle(ChatFormatting.GRAY)));
            out.add(Component.literal("     ").append(Component.translatable(G+"network",PowerPoles.su(su)).withStyle(ChatFormatting.GRAY)));
            out.add(Component.literal("     ").append(Component.translatable(G+"parts",turning,turrets,boxes).withStyle(ChatFormatting.DARK_GRAY)));
        }
        static final String K="gui.eternal_defense.power.dynamo.",G="gui.eternal_defense.power.goggles.";
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
