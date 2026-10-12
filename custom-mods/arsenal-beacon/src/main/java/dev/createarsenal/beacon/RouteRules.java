package dev.createarsenal.beacon;

import java.util.List;
import java.util.Locale;

/**
 * The supply rail of a Forward Operating Base (0.0.24, FOB slice 2): the pure rules, without Minecraft types, so they can be unit tested.
 * A pair of gates (a Supply Receiving Gate at home, a Supply Delivery Gate at the FOB) is opened by one minecart ride from home to the FOB;
 * the route then delivers the FOB's tribute by itself, and a full round trip carries more and renews its inspection clock (see {@link Routes}).
 * 0.0.31 (owner): the tribute is four loot slots chosen per FOB, delivered into the Receiving Gate's own storage.
 */
final class RouteRules {
    private RouteRules(){}

    // ---- the tribute (0.0.31, owner): four loot slots, each a choice of one item -------------------------------------------------------------------
    /**
     * The four loot slots of a FOB: emeralds (every village), the mine, the farm and the village's specialty. Each can be switched off; the
     * slots left on share the whole trip between them (a farm village that switches the other three off gets four times the farm's share).
     */
    enum Slot{EMERALD,MINE,FARM,SPECIALTY;String id(){return name().toLowerCase(Locale.ROOT);}}
    /** A FOB's specialty, rolled when it is planted (only a new FOB, taken back with the shovel and planted again, rolls another). */
    enum Specialty{FISHING,HUSBANDRY,POTIONS,TRADER;String id(){return name().toLowerCase(Locale.ROOT);}
        List<Loot> options(){return switch(this){case FISHING->FISHING_LOOT;case HUSBANDRY->HUSBANDRY_LOOT;case POTIONS->POTION_LOOT;case TRADER->TRADER_LOOT;};}
        static Specialty of(int ordinal){var v=values();return v[Math.floorMod(ordinal,v.length)];}
    }
    /**
     * One item a slot can send and what it is worth in tribute value (one common item is 1): the higher the value, the more trips one
     * item takes. {@code note}: a potion ingredient's farming tier (1 = D, dirt cheap, to 5 = S) or a trader item's emerald price; 0 else.
     */
    record Loot(String id,double value,int note){}
    /**
     * One emerald is worth this much tribute, as vanilla villagers price it (about 12 to 20 crops, or 4 iron, for an emerald), so an
     * emerald-only FOB brings about what the 0.0.28 emerald share did. Every switched-on emerald slot sends at least {@link #EMERALD_MIN} a trip.
     */
    static final double EMERALD_VALUE=12,EMERALD_MIN=1/3.0;
    static final Loot EMERALD=new Loot("minecraft:emerald",EMERALD_VALUE,0);
    static final List<Loot> EMERALD_LOOT=List.of(EMERALD);
    /** The mine: redstone, iron, andesite, flint, gunpowder or charcoal (owner's list). */
    static final List<Loot> MINE_LOOT=List.of(new Loot("minecraft:redstone",1,0),new Loot("minecraft:iron_ingot",3,0),new Loot("minecraft:andesite",.5,0),
        new Loot("minecraft:flint",1,0),new Loot("minecraft:gunpowder",1.5,0),new Loot("minecraft:charcoal",1,0));
    /** The farm: every plant food material of vanilla 1.20.1. */
    static final List<Loot> FARM_LOOT=List.of(new Loot("minecraft:wheat",1,0),new Loot("minecraft:carrot",1,0),new Loot("minecraft:potato",1,0),
        new Loot("minecraft:beetroot",1,0),new Loot("minecraft:melon_slice",.5,0),new Loot("minecraft:pumpkin",1.5,0),new Loot("minecraft:apple",2,0),
        new Loot("minecraft:sweet_berries",1,0),new Loot("minecraft:glow_berries",1.5,0),new Loot("minecraft:cocoa_beans",1.5,0),new Loot("minecraft:sugar_cane",1,0),
        new Loot("minecraft:brown_mushroom",1.5,0),new Loot("minecraft:red_mushroom",1.5,0));
    /** Specialty, fishing: every aquatic food. */
    static final List<Loot> FISHING_LOOT=List.of(new Loot("minecraft:cod",1,0),new Loot("minecraft:salmon",1.5,0),new Loot("minecraft:tropical_fish",2,0),
        new Loot("minecraft:pufferfish",3,0),new Loot("minecraft:dried_kelp",.5,0));
    /** Specialty, animal husbandry: every meat and leather. */
    static final List<Loot> HUSBANDRY_LOOT=List.of(new Loot("minecraft:beef",1.5,0),new Loot("minecraft:porkchop",1.5,0),new Loot("minecraft:chicken",1,0),
        new Loot("minecraft:mutton",1,0),new Loot("minecraft:rabbit",2,0),new Loot("minecraft:leather",1.5,0),new Loot("minecraft:rabbit_hide",1,0));
    /**
     * A potion ingredient's tier (the owner's farming tier list) sets its value, each tier twice the one below: D 3, C 6, B 12, A 24, S 48. S
     * is four emeralds' worth, what the standalone Exchange pays for a ghast tear, so no ingredient beats emeralds there.
     */
    static final double[] POTION_TIER_VALUE={0,3,6,12,24,48};
    static Loot potion(String id,int tier){return new Loot(id,POTION_TIER_VALUE[tier],tier);}
    /**
     * Specialty, potion makers: every brewing ingredient of 1.20.1, by the owner's tiers. S: ghast tear, rabbit's foot, turtle shell. A: phantom
     * membrane, pufferfish, blaze powder. B: magma cream, nether wart, dragon's breath. C: golden carrot, glistering melon, spider eye,
     * fermented spider eye. D: sugar, redstone, glowstone, gunpowder. (The owner's breeze rod, cobweb, slime block and stone brew only from 1.21.)
     */
    static final List<Loot> POTION_LOOT=List.of(potion("minecraft:ghast_tear",5),potion("minecraft:rabbit_foot",5),potion("minecraft:turtle_helmet",5),
        potion("minecraft:phantom_membrane",4),potion("minecraft:pufferfish",4),potion("minecraft:blaze_powder",4),
        potion("minecraft:magma_cream",3),potion("minecraft:nether_wart",3),potion("minecraft:dragon_breath",3),
        potion("minecraft:golden_carrot",2),potion("minecraft:glistering_melon_slice",2),potion("minecraft:spider_eye",2),potion("minecraft:fermented_spider_eye",2),
        potion("minecraft:sugar",1),potion("minecraft:redstone",1),potion("minecraft:glowstone_dust",1),potion("minecraft:gunpowder",1));
    /** A wandering trader's sale: {@code count} of it for {@code emeralds}; its value is what the emeralds are worth, per item. */
    static Loot trade(String id,int emeralds,int count){return new Loot(id,EMERALD_VALUE*emeralds/count,emeralds);}
    /** Specialty, wandering trader hub: everything a vanilla 1.20.1 wandering trader sells, at its emerald price. */
    static final List<Loot> TRADER_LOOT;
    static{
        var t=new java.util.ArrayList<Loot>();
        t.add(trade("minecraft:sea_pickle",2,1));t.add(trade("minecraft:slime_ball",4,1));t.add(trade("minecraft:glowstone",2,1));t.add(trade("minecraft:nautilus_shell",5,1));
        t.add(trade("minecraft:fern",1,1));t.add(trade("minecraft:sugar_cane",1,1));t.add(trade("minecraft:pumpkin",1,1));t.add(trade("minecraft:kelp",3,1));t.add(trade("minecraft:cactus",3,1));
        for(String f:List.of("dandelion","poppy","blue_orchid","allium","azure_bluet","red_tulip","orange_tulip","white_tulip","pink_tulip","oxeye_daisy","cornflower","lily_of_the_valley"))t.add(trade("minecraft:"+f,1,1));
        for(String seed:List.of("wheat_seeds","beetroot_seeds","pumpkin_seeds","melon_seeds"))t.add(trade("minecraft:"+seed,1,1));
        for(String tree:List.of("acacia_sapling","birch_sapling","dark_oak_sapling","jungle_sapling","oak_sapling","spruce_sapling","cherry_sapling","mangrove_propagule"))t.add(trade("minecraft:"+tree,5,1));
        for(String dye:List.of("red","white","blue","pink","black","green","light_gray","magenta","yellow","gray","purple","light_blue","lime","orange","brown","cyan"))t.add(trade("minecraft:"+dye+"_dye",1,3));
        for(String coral:List.of("brain","bubble","fire","horn","tube"))t.add(trade("minecraft:"+coral+"_coral_block",3,1));
        t.add(trade("minecraft:vine",1,1));t.add(trade("minecraft:brown_mushroom",1,1));t.add(trade("minecraft:red_mushroom",1,1));t.add(trade("minecraft:lily_pad",1,2));
        t.add(trade("minecraft:small_dripleaf",1,2));t.add(trade("minecraft:sand",1,8));t.add(trade("minecraft:red_sand",1,4));t.add(trade("minecraft:pointed_dripstone",1,2));
        t.add(trade("minecraft:rooted_dirt",1,2));t.add(trade("minecraft:moss_block",1,2));
        t.add(trade("minecraft:tropical_fish_bucket",5,1));t.add(trade("minecraft:pufferfish_bucket",5,1));t.add(trade("minecraft:packed_ice",3,1));t.add(trade("minecraft:blue_ice",6,1));
        t.add(trade("minecraft:gunpowder",1,1));t.add(trade("minecraft:podzol",3,3));
        TRADER_LOOT=List.copyOf(t);
    }
    /** The items a slot can send (the specialty's list for the specialty slot). */
    static List<Loot> options(Slot slot,Specialty specialty){
        return switch(slot){case EMERALD->EMERALD_LOOT;case MINE->MINE_LOOT;case FARM->FARM_LOOT;case SPECIALTY->specialty.options();};
    }
    static Loot loot(Slot slot,Specialty specialty,int choice){var o=options(slot,specialty);return o.get(Math.floorMod(choice,o.size()));}

    // ---- how much a trip carries ---------------------------------------------------------------------------------------------------------------
    /** A trip's tribute value grows with the distance between the gates: one per this many blocks, at least 1, at most {@link #MAX_UNITS}. */
    static final int BLOCKS_PER_UNIT=200;
    static final double MAX_UNITS=50;
    static double units(double distance){return Math.max(1,Math.min(MAX_UNITS,distance/BLOCKS_PER_UNIT));}
    /** Villagers count up to {@link #MAX_WORKERS}; ten make a trip worth its units, twenty twice that. */
    static final int MAX_WORKERS=20,WORKERS_FOR_ONE=10;
    static double workers(int villagers){return Math.min(MAX_WORKERS,Math.max(0,villagers))/(double)WORKERS_FOR_ONE;}
    /** The owner's bonus for absurdly far bases: none up to {@link #BONUS_FROM} blocks, then rising to {@link #MAX_BONUS} at {@link #BONUS_FULL}. */
    static final double BONUS_FROM=1000,BONUS_FULL=5000,MAX_BONUS=.20;
    static double distanceBonus(double distance){return MAX_BONUS*Math.max(0,Math.min(1,(distance-BONUS_FROM)/(BONUS_FULL-BONUS_FROM)));}
    static int enabled(boolean[] on){int n=0;for(boolean b:on)if(b)n++;return n;}
    /** Pure: the tribute value one trip gives a slot: the trip's whole value, shared by the slots that are on. */
    static double budget(Slot slot,boolean[] on,double distance,int villagers){
        if(!on[slot.ordinal()])return 0;
        return units(distance)*workers(villagers)*(1+distanceBonus(distance))/Math.max(1,enabled(on));
    }
    /** Pure: the value a slot adds to its tally per trip (the emerald slot never less than {@link #EMERALD_MIN} emeralds). */
    static double perTrip(Slot slot,boolean[] on,double distance,int villagers){
        double b=budget(slot,on,distance,villagers);
        return slot==Slot.EMERALD&&on[slot.ordinal()]?Math.max(b,EMERALD_MIN*EMERALD_VALUE):b;
    }
    /** Pure: items of {@code loot} a slot makes per trip (a fraction: one every 1/x trips). */
    static double itemsPerTrip(Slot slot,Loot loot,boolean[] on,double distance,int villagers){return perTrip(slot,on,distance,villagers)/loot.value();}
    /** A slot may be switched off only while another stays on. */
    static boolean mayToggle(boolean[] on,Slot slot){return !on[slot.ordinal()]||enabled(on)>1;}
    /**
     * Pure: one trip into the tallies (value per slot, kept between trips): {@code trips} trips are added, then every whole item is taken out.
     * Returns the whole items per slot; {@code tally} keeps what is left. A changed choice keeps the tally (it is value, not items).
     */
    static int[] trip(double[] tally,Loot[] loot,boolean[] on,double distance,int villagers,double trips){
        int[] out=new int[Slot.values().length];
        for(var slot:Slot.values()){int i=slot.ordinal();if(!on[i])continue;
            tally[i]+=trips*perTrip(slot,on,distance,villagers);
            int n=(int)Math.floor(tally[i]/loot[i].value()+1e-9);out[i]=n;tally[i]=Math.max(0,tally[i]-n*loot[i].value());}
        return out;
    }
    static int total(int[] amounts){int t=0;for(int a:amounts)t+=a;return t;}
    /** A ridden round trip carries at most this many trips' worth (and the next passive delivery waits a whole period after it). */
    static final int ACTIVE_FACTOR=2;
    /**
     * ...but never more than this many times what the route would have sent by itself since the last delivery: riding back to back pays 2.5
     * times passive on any route, and a short loop ridden over and over makes no tribute out of nothing (0.0.30's stockpile bounded it).
     */
    static final double RIDE_BOOST=2.5;
    /** Pure: the trips a ridden round trip brings, {@code since} ticks of play after the last delivery, on a route of this period. */
    static double rideTrips(long since,long period){return Math.min(ACTIVE_FACTOR,RIDE_BOOST*Math.max(0,since)/(double)Math.max(1,period));}

    // ---- the route's numbers -------------------------------------------------------------------------------------------------------------------
    /** The gates must stand at least this far apart (straight line, sideways). */
    static final int MIN_SEPARATION=128;
    /** A passive delivery comes every 2T / 0.8: as if a cart rode the round trip continuously at 80 percent of the ride's speed. */
    static final double PASSIVE_EFFICIENCY=0.8;
    /**
     * Passive deliveries never come more often than every 5 minutes of play: every delivery brings at least a third of an emerald, so a
     * short route would otherwise out-earn a long one (at most 4 emeralds an hour from the minimum, whatever the route).
     */
    static final long MIN_PERIOD=6_000;
    /** Pure: ticks between passive deliveries for a one-way time T. */
    static long period(long baseline){return Math.max(MIN_PERIOD,Math.round(2*baseline/PASSIVE_EFFICIENCY));}
    /** Pure: trips an hour of play at this period. */
    static double tripsPerHour(long period){return period<=0?0:HOUR/(double)period;}
    static final long HOUR=72_000L;
    /** The fastest a cart covers ground (vanilla's 0.4 blocks a tick on a straight rail, with room for diagonal track): no route is timed faster. */
    static final double MAX_SPEED=0.57;
    /** Pure: a measured one-way time, never faster than a cart can cover the straight-line distance. */
    static long clamp(long ticks,double distance){return Math.max(ticks,(long)Math.ceil(distance/MAX_SPEED));}
    /** Pure: the new baseline after a round trip: the average of its two legs, clamped. */
    static long baseline(long out,long back,double distance){return clamp((out+back)/2,distance);}

    // ---- the inspection clock, the backlog ----------------------------------------------------------------------------------------------------------
    /** Ticks of play the route keeps running after a ride (8 hours); the light turns amber in its last quarter. */
    static final long CLOCK=8*HOUR;
    static final double WARN_SHARE=.25;
    static boolean warning(long clock){return clock>0&&clock<=CLOCK*WARN_SHARE;}
    /** Passive "Tribute delivered" chat lines: at most one per FOB in this many ticks of play, summing what arrived meanwhile. */
    static final long NOTE_TICKS=6_000;
    /** A leg of a ride gives up after max(10 minutes, 3 T). */
    static long legTimeout(long baseline){return Math.max(12_000L,3*Math.max(0,baseline));}

    // ---- the traffic light ---------------------------------------------------------------------------------------------------------------------
    enum Signal{RED,AMBER,GREEN;String id(){return name().toLowerCase(Locale.ROOT);}}
    /**
     * Pure, shared by the gates, the screens and the tests. RED: unlinked, no partner gate, the FOB not ACTIVE, no route yet, or the inspection
     * overdue. AMBER: a ride under way, the inspection in its last quarter, or the Receiving Gate's storage full (0.0.31). GREEN otherwise.
     */
    static Signal signal(boolean linked,boolean partner,boolean fobActive,boolean commissioned,long clock,boolean riding,boolean full){
        if(!linked||!partner||!fobActive||!commissioned||clock<=0)return Signal.RED;
        if(riding||warning(clock)||full)return Signal.AMBER;
        return Signal.GREEN;
    }
    /** Pure: may a passive delivery happen now? */
    static boolean passive(boolean fobActive,boolean commissioned,long clock,boolean full){return fobActive&&commissioned&&clock>0&&!full;}

    // ---- a pass through a gate -------------------------------------------------------------------------------------------------------------------
    /**
     * Pure: did a cart moving from (px, py, pz) to (cx, cy, cz) roll through a gate whose rail cell is at (gx, gy, gz), along the X axis
     * ({@code alongX}) or the Z axis? The cart must cross the plane through the cell's middle across the rail, between the cell's sides
     * (with {@code slack} blocks of play) and within its height. Both directions count; a cart that stops inside has not passed; a cart
     * moving several blocks in one tick still crosses (the segment is tested, not a point). Returns +1 or -1 for the direction along the
     * axis, 0 for no pass.
     */
    static int crossing(double px,double py,double pz,double cx,double cy,double cz,int gx,int gy,int gz,boolean alongX,double slack){
        return crossing(px,py,pz,cx,cy,cz,gx,gy,gz,alongX,1,slack);
    }
    /** As above for an opening {@code lanes} cells wide, from the cell (gx, gy, gz) towards +x or +z (the 0.0.30 gates have two lanes). */
    static int crossing(double px,double py,double pz,double cx,double cy,double cz,int gx,int gy,int gz,boolean alongX,int lanes,double slack){
        double a0=alongX?px:pz,a1=alongX?cx:cz,mid=(alongX?gx:gz)+.5;
        if((a0-mid)*(a1-mid)>0||a0==a1)return 0;
        if(a0==mid)return 0;   // starting exactly on the plane: the previous step counted it
        double t=(mid-a0)/(a1-a0);
        double side=(alongX?pz:px)+t*((alongX?cz:cx)-(alongX?pz:px)),y=py+t*(cy-py);
        int sideCell=alongX?gz:gx;
        if(side<sideCell-slack||side>sideCell+lanes+slack)return 0;
        if(y<gy-slack||y>gy+1.5+slack)return 0;
        return a1>a0?1:-1;
    }

    // ---- a ride ---------------------------------------------------------------------------------------------------------------------------------
    enum Gate{RECEIVING,DELIVERY}
    /** Where a ride stands: out from home towards the FOB, or back home with a load. */
    enum Stage{OUTBOUND,RETURN}
    /** What a pass did, a key under {@code route.pass.} for the rider's action bar. */
    enum Step{STARTED,RESTARTED,COMMISSIONED,LOADED,COMPLETE,BUSY,START_AT_HOME,WRONG_GATE,TOO_CLOSE}
    /** A loaded rider back at the Delivery Gate within this many ticks is on the way home through its other lane (a loop): no hint then. */
    static final long WRONG_GATE_QUIET=200;
    /**
     * Pure: the next step of a ride at a gate. {@code stage} is null when no ride runs on this route; {@code sameRider} tells whether the pass
     * is by the rider (in the same cart) of the running ride. A ride starts at the Receiving Gate (home), turns at the Delivery Gate (the FOB;
     * the first time it opens the route) and ends at the Receiving Gate again.
     */
    static Step step(Gate gate,Stage stage,boolean sameRider,boolean commissioned,double distance){
        if(stage!=null&&!sameRider)return Step.BUSY;
        if(gate==Gate.RECEIVING){
            if(stage==null)return Step.STARTED;
            return stage==Stage.RETURN?Step.COMPLETE:Step.RESTARTED;
        }
        if(stage==null)return Step.START_AT_HOME;
        if(stage==Stage.RETURN)return Step.WRONG_GATE;
        if(distance<MIN_SEPARATION)return Step.TOO_CLOSE;
        return commissioned?Step.LOADED:Step.COMMISSIONED;
    }
}
