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
    /**
     * A village's specialty. 0.0.34 (owner): fishing and animal husbandry are one specialty, "Fishing & Animal Husbandry", with both lists
     * and both icons (fishing alone was too thin to fill a slot). Saved by {@link #id()} since 0.0.34; before, by the ordinal of the five of
     * 0.0.32 ({@link #legacy}).
     */
    enum Specialty{FISHING_HUSBANDRY,POTIONS,TRADER,MONSTERS;String id(){return name().toLowerCase(Locale.ROOT);}
        List<Loot> options(){return switch(this){case FISHING_HUSBANDRY->FISHING_HUSBANDRY_LOOT;case POTIONS->POTION_LOOT;case TRADER->TRADER_LOOT;case MONSTERS->MONSTER_LOOT;};}
        /** Its icons (textures/gui/tribute): the merged one shows the fisher's and the herder's side by side. */
        List<String> icons(){return this==FISHING_HUSBANDRY?List.of("spec_fishing","spec_husbandry"):List.of("spec_"+id());}
        static Specialty of(int ordinal){var v=values();return v[Math.floorMod(ordinal,v.length)];}
        /** The specialty a 0.0.31 to 0.0.33 save meant by its ordinal (fishing, husbandry, potions, trader, monsters). */
        static Specialty legacy(int ordinal){return switch(Math.floorMod(ordinal,5)){case 0,1->FISHING_HUSBANDRY;case 2->POTIONS;case 3->TRADER;default->MONSTERS;};}
        /** By id; null for none. */
        static Specialty byId(String id){for(var s:values())if(s.id().equals(id))return s;return null;}
        /** By id as the client gets it, Fishing & Animal Husbandry for an unknown one. */
        static Specialty orFirst(String id){var s=byId(id);return s==null?FISHING_HUSBANDRY:s;}
    }
    /**
     * One item a slot can send and what it is worth in tribute value (one common item is 1): the higher the value, the more trips one item
     * takes. {@code tier}: how hard it is to get, 1 (D) to 5 (S), 0 when it has none; A (4) and S (5) are locked until the FOB's research
     * opens them (0.0.32). {@code price}: a wandering trader item's price in emeralds, 0 else.
     */
    record Loot(String id,double value,int tier,int price){}
    /** 0.0.32: tier D to S worth 1, 2, 4, 8, 16: each tier twice the one below (index 0: no tier). */
    static final double[] TIER_VALUE={0,1,2,4,8,16};
    static final String TIERS="-DCBAS";
    static Loot tiered(String id,int tier){return new Loot(id,TIER_VALUE[tier],tier,0);}
    static Loot plain(String id,double value){return new Loot(id,value,0,0);}
    /**
     * 0.0.32: an emerald is worth 4, a quarter of an S-tier item (the standalone Exchange pays 1 Ardent Energy for 2 emeralds and 2 for a
     * ghast tear: the same per value). Every switched-on emerald slot sends at least {@link #EMERALD_MIN} a trip.
     */
    static final double EMERALD_VALUE=4,EMERALD_MIN=1/3.0;
    static final Loot EMERALD=plain("minecraft:emerald",EMERALD_VALUE);
    static final List<Loot> EMERALD_LOOT=List.of(EMERALD);
    /** The mine: redstone, iron, andesite, flint, gunpowder or charcoal (owner's list). */
    static final List<Loot> MINE_LOOT=List.of(plain("minecraft:redstone",1),plain("minecraft:iron_ingot",2),plain("minecraft:andesite",.5),
        plain("minecraft:flint",1),plain("minecraft:gunpowder",1.5),plain("minecraft:charcoal",1));
    /** The farm: every plant food material of vanilla 1.20.1. */
    static final List<Loot> FARM_LOOT=List.of(plain("minecraft:wheat",1),plain("minecraft:carrot",1),plain("minecraft:potato",1),
        plain("minecraft:beetroot",1),plain("minecraft:melon_slice",.5),plain("minecraft:pumpkin",1.5),plain("minecraft:apple",2),
        plain("minecraft:sweet_berries",1),plain("minecraft:glow_berries",1.5),plain("minecraft:cocoa_beans",1.5),plain("minecraft:sugar_cane",1),
        plain("minecraft:brown_mushroom",1.5),plain("minecraft:red_mushroom",1.5));
    /** Specialty, fishing: every aquatic food. */
    static final List<Loot> FISHING_LOOT=List.of(plain("minecraft:cod",1),plain("minecraft:salmon",1.5),plain("minecraft:tropical_fish",2),
        plain("minecraft:pufferfish",2),plain("minecraft:dried_kelp",.5));
    /** Specialty, animal husbandry: every meat and leather. */
    static final List<Loot> HUSBANDRY_LOOT=List.of(plain("minecraft:beef",1.5),plain("minecraft:porkchop",1.5),plain("minecraft:chicken",1),
        plain("minecraft:mutton",1),plain("minecraft:rabbit",2),plain("minecraft:leather",1.5),plain("minecraft:rabbit_hide",1));
    /** 0.0.34: the merged specialty's list, the fisher's first. */
    static final List<Loot> FISHING_HUSBANDRY_LOOT=java.util.stream.Stream.concat(FISHING_LOOT.stream(),HUSBANDRY_LOOT.stream()).toList();
    /**
     * Specialty, potion makers: every brewing ingredient of 1.20.1, by the owner's tiers, cheapest first. D: sugar, redstone, glowstone,
     * gunpowder. C: golden carrot, glistering melon, spider eye, fermented spider eye. B: magma cream, nether wart. A: phantom membrane,
     * pufferfish, blaze powder. S: ghast tear, rabbit's foot, turtle shell, dragon's breath (0.0.32, owner: dragon's breath to S). (The owner's
     * breeze rod, cobweb, slime block and stone brew only from 1.21.)
     */
    static final List<Loot> POTION_LOOT=List.of(tiered("minecraft:sugar",1),tiered("minecraft:redstone",1),tiered("minecraft:glowstone_dust",1),tiered("minecraft:gunpowder",1),
        tiered("minecraft:golden_carrot",2),tiered("minecraft:glistering_melon_slice",2),tiered("minecraft:spider_eye",2),tiered("minecraft:fermented_spider_eye",2),
        tiered("minecraft:magma_cream",3),tiered("minecraft:nether_wart",3),
        tiered("minecraft:phantom_membrane",4),tiered("minecraft:pufferfish",4),tiered("minecraft:blaze_powder",4),
        tiered("minecraft:ghast_tear",5),tiered("minecraft:rabbit_foot",5),tiered("minecraft:turtle_helmet",5),tiered("minecraft:dragon_breath",5));
    /**
     * Specialty, the monster hunter guild (0.0.32, owner): every drop of a vanilla 1.20.1 monster except the bosses (the Ender Dragon, the
     * Wither, the Elder Guardian), by how hard it is to farm; an item two lists share has the same tier in both. Not squid ink (squids are
     * not monsters), not rare equipment drops (tridents, crossbows, armour) and not music discs. 0.0.34 (owner): no totem of undying
     * (far too strong to receive by the hour; a FOB that chose it falls back to the list's first item).
     */
    static final List<Loot> MONSTER_LOOT=List.of(tiered("minecraft:rotten_flesh",1),tiered("minecraft:bone",1),tiered("minecraft:string",1),tiered("minecraft:arrow",1),
        tiered("minecraft:gunpowder",1),tiered("minecraft:coal",1),tiered("minecraft:glowstone_dust",1),
        tiered("minecraft:spider_eye",2),tiered("minecraft:slime_ball",2),tiered("minecraft:gold_nugget",2),tiered("minecraft:prismarine_shard",2),
        tiered("minecraft:magma_cream",3),tiered("minecraft:prismarine_crystals",3),
        tiered("minecraft:ender_pearl",4),tiered("minecraft:blaze_rod",4),tiered("minecraft:phantom_membrane",4),tiered("minecraft:nautilus_shell",4),
        tiered("minecraft:ghast_tear",5),tiered("minecraft:shulker_shell",5),tiered("minecraft:wither_skeleton_skull",5));
    /**
     * A wandering trader's sale: {@code count} of it for {@code emeralds}. 0.0.32: its value follows its price, the dearest trade (blue ice,
     * 6 emeralds) worth an S-tier item, so every trader item comes within the 30 minute promise ({@link #S_HOURS}).
     */
    static final double TRADER_PER_EMERALD=TIER_VALUE[5]/6;
    static Loot trade(String id,int emeralds,int count){return trade(id,emeralds,count,0);}
    static Loot trade(String id,int emeralds,int count,int tier){return new Loot(id,TRADER_PER_EMERALD*emeralds/count,tier,emeralds);}
    /**
     * Specialty, wandering trader hub: everything a vanilla 1.20.1 wandering trader sells, at its emerald price. 0.0.32 (owner): the endgame
     * goods are locked by research: A the slime ball, packed ice, the five coral blocks and the two fish buckets; S the nautilus shell and
     * blue ice.
     */
    static final List<Loot> TRADER_LOOT;
    static{
        var t=new java.util.ArrayList<Loot>();
        t.add(trade("minecraft:sea_pickle",2,1));t.add(trade("minecraft:slime_ball",4,1,4));t.add(trade("minecraft:glowstone",2,1));t.add(trade("minecraft:nautilus_shell",5,1,5));
        t.add(trade("minecraft:fern",1,1));t.add(trade("minecraft:sugar_cane",1,1));t.add(trade("minecraft:pumpkin",1,1));t.add(trade("minecraft:kelp",3,1));t.add(trade("minecraft:cactus",3,1));
        for(String f:List.of("dandelion","poppy","blue_orchid","allium","azure_bluet","red_tulip","orange_tulip","white_tulip","pink_tulip","oxeye_daisy","cornflower","lily_of_the_valley"))t.add(trade("minecraft:"+f,1,1));
        for(String seed:List.of("wheat_seeds","beetroot_seeds","pumpkin_seeds","melon_seeds"))t.add(trade("minecraft:"+seed,1,1));
        for(String tree:List.of("acacia_sapling","birch_sapling","dark_oak_sapling","jungle_sapling","oak_sapling","spruce_sapling","cherry_sapling","mangrove_propagule"))t.add(trade("minecraft:"+tree,5,1));
        for(String dye:List.of("red","white","blue","pink","black","green","light_gray","magenta","yellow","gray","purple","light_blue","lime","orange","brown","cyan"))t.add(trade("minecraft:"+dye+"_dye",1,3));
        for(String coral:List.of("brain","bubble","fire","horn","tube"))t.add(trade("minecraft:"+coral+"_coral_block",3,1,4));
        t.add(trade("minecraft:vine",1,1));t.add(trade("minecraft:brown_mushroom",1,1));t.add(trade("minecraft:red_mushroom",1,1));t.add(trade("minecraft:lily_pad",1,2));
        t.add(trade("minecraft:small_dripleaf",1,2));t.add(trade("minecraft:sand",1,8));t.add(trade("minecraft:red_sand",1,4));t.add(trade("minecraft:pointed_dripstone",1,2));
        t.add(trade("minecraft:rooted_dirt",1,2));t.add(trade("minecraft:moss_block",1,2));
        t.add(trade("minecraft:tropical_fish_bucket",5,1,4));t.add(trade("minecraft:pufferfish_bucket",5,1,4));t.add(trade("minecraft:packed_ice",3,1,4));t.add(trade("minecraft:blue_ice",6,1,5));
        t.add(trade("minecraft:gunpowder",1,1));t.add(trade("minecraft:podzol",3,3));
        TRADER_LOOT=List.copyOf(t);
    }
    /** The items a slot can send (the specialty's list for the specialty slot). */
    static List<Loot> options(Slot slot,Specialty specialty){
        return switch(slot){case EMERALD->EMERALD_LOOT;case MINE->MINE_LOOT;case FARM->FARM_LOOT;case SPECIALTY->specialty.options();};
    }
    static Loot loot(Slot slot,Specialty specialty,int choice){var o=options(slot,specialty);return o.get(Math.floorMod(choice,o.size()));}
    /** The choice as it works now: a locked choice (research not there yet) sends the slot's first open item instead. */
    static int effectiveChoice(Slot slot,Specialty specialty,int choice,int level){
        var o=options(slot,specialty);int c=Math.floorMod(choice,o.size());if(unlocked(o.get(c),level))return c;
        for(int i=0;i<o.size();i++)if(unlocked(o.get(i),level))return i;
        return 0;
    }

    // ---- village research (0.0.32, owner): a bar per FOB, paid at its FOB Beacon ------------------------------------------------------------------
    /** Research points needed for levels 0 to 5 (in total). */
    static final int[] RESEARCH_XP={0,16,48,96,160,240};
    static final int MAX_LEVEL=RESEARCH_XP.length-1;
    /** What one item given to the research is worth in points: an emerald 1, a diamond 4, an Ardent Energy 2 (the standalone Exchange's prices). */
    static final int XP_EMERALD=1,XP_DIAMOND=4,XP_ARDENT=2;
    /** The level research opens a tier at: A at 2, S at 4. */
    static final int UNLOCK_A=2,UNLOCK_S=4;
    /** Each level: the specialty +25 percent, emeralds, the mine and the farm +5 percent. */
    static final double SPECIALTY_PER_LEVEL=.25,OTHER_PER_LEVEL=.05;
    static int level(int xp){int l=0;while(l<MAX_LEVEL&&xp>=RESEARCH_XP[l+1])l++;return l;}
    static int unlockLevel(Loot l){return l.tier()>=5?UNLOCK_S:l.tier()>=4?UNLOCK_A:0;}
    static boolean unlocked(Loot l,int level){return level>=unlockLevel(l);}
    static double slotMultiplier(Slot slot,int level){return 1+Math.max(0,Math.min(MAX_LEVEL,level))*(slot==Slot.SPECIALTY?SPECIALTY_PER_LEVEL:OTHER_PER_LEVEL);}
    /** Points still needed for the next level (0 at the top). */
    static int toNext(int xp){int l=level(xp);return l>=MAX_LEVEL?0:RESEARCH_XP[l+1]-xp;}

    // ---- how much a FOB makes (0.0.32: per hour of play, the owner's 30 minute promise) ------------------------------------------------------------
    /**
     * The tribute value an hour of the slowest FOB that can make anything: 3 villagers, gates 128 blocks apart, every slot on. With research
     * at 4 (S open, the specialty at x2) its specialty slot makes 64 / 4 x 2 = 32 an hour: one S-tier item every 30 minutes of play, the owner's
     * floor. Everything else is a multiple of it.
     */
    static final double SLOWEST_PER_HOUR=64;
    static final double S_HOURS=.5;
    /** More villagers: 3 count 1, 20 count 2 (linear between; fewer than 3, after losses, count less). */
    static final int MIN_VILLAGERS=3,MAX_WORKERS=20;static final double MAX_VILLAGER_FACTOR=2;
    static double villagerFactor(int villagers){
        if(villagers<=0)return 0;if(villagers<MIN_VILLAGERS)return villagers/(double)MIN_VILLAGERS;
        return 1+(Math.min(MAX_WORKERS,villagers)-MIN_VILLAGERS)*(MAX_VILLAGER_FACTOR-1)/(MAX_WORKERS-MIN_VILLAGERS);
    }
    /** A longer line: 128 blocks count 1, 1,000 blocks 2 (linear between, never more). */
    static final double FULL_DISTANCE=1000,MAX_DISTANCE_FACTOR=2;
    static double distanceFactor(double distance){return 1+Math.max(0,Math.min(1,(distance-MIN_SEPARATION)/(FULL_DISTANCE-MIN_SEPARATION)))*(MAX_DISTANCE_FACTOR-1);}
    /** The owner's bonus for absurdly far bases: none up to {@link #BONUS_FROM} blocks, then rising to {@link #MAX_BONUS} at {@link #BONUS_FULL}. */
    static final double BONUS_FROM=1000,BONUS_FULL=5000,MAX_BONUS=.20;
    static double distanceBonus(double distance){return MAX_BONUS*Math.max(0,Math.min(1,(distance-BONUS_FROM)/(BONUS_FULL-BONUS_FROM)));}
    /** Pure: the tribute value a FOB makes an hour, all slots together (before research). The best is 4.8 times the slowest. */
    static double perHour(double distance,int villagers){return SLOWEST_PER_HOUR*villagerFactor(villagers)*distanceFactor(distance)*(1+distanceBonus(distance));}
    /** A vanilla cart's top speed on a straight rail: the route's period at this speed is the one the value an hour is promised at. */
    static final double REF_SPEED=.4;
    static long refPeriod(double distance){return period(Math.round(distance/REF_SPEED));}
    /**
     * Pure: the hours of making one trip carries: the route's own period, but never less than at a vanilla cart's speed, so a slow rail
     * never makes less an hour than promised and a fast one delivers the same trips more often (it pays).
     */
    static double tripHours(double distance,long period){return Math.max(refPeriod(distance),period)/(double)HOUR;}
    /** Everything a trip depends on: the slots on, the line, the villagers, the route's period, the research level. */
    record Yield(boolean[] on,double distance,int villagers,long period,int level){}
    static int enabled(boolean[] on){int n=0;for(boolean b:on)if(b)n++;return n;}
    /** Pure: the tribute value one trip gives a slot: the trip's value shared by the slots that are on, times the slot's research bonus. */
    static double budget(Slot slot,Yield y){
        if(!y.on()[slot.ordinal()])return 0;
        double hours=tripHours(y.distance(),y.period());
        double value=perHour(y.distance(),y.villagers())*hours/Math.max(1,enabled(y.on()))*slotMultiplier(slot,y.level());
        return slot==Slot.SPECIALTY?Math.min(value,SPECIALTY_MAX_PER_HOUR*deliveryHours(y)):value;
    }
    /** The hours between two deliveries of this route as they really come (a fast rail delivers more often than the hours a trip carries). */
    static double deliveryHours(Yield y){return (y.period()>0?y.period():refPeriod(y.distance()))/(double)HOUR;}
    /**
     * 0.0.34 (owner, after a playtest of the potion makers at research 5 with every other slot off: 12 S-tier items an hour): the specialty
     * slot makes at most 4 S-tier items' worth an hour (64 value), however big the FOB, the research, the boost from slots switched off or
     * the rail's speed (counted against the real time between deliveries).
     * The 30-minute floor (2 an hour at research 4 with every slot on, the slowest FOB) is under it.
     */
    static final double SPECIALTY_MAX_PER_HOUR=4*TIER_VALUE[5];
    /** Pure: the value a slot adds to its tally per trip (the emerald slot never less than {@link #EMERALD_MIN} emeralds). */
    static double perTrip(Slot slot,Yield y){
        double b=budget(slot,y);
        return slot==Slot.EMERALD&&y.on()[slot.ordinal()]?Math.max(b,EMERALD_MIN*EMERALD_VALUE):b;
    }
    /** Pure: items of {@code loot} a slot makes per trip (a fraction: one every 1/x trips). */
    static double itemsPerTrip(Slot slot,Loot loot,Yield y){return perTrip(slot,y)/loot.value();}
    /** A slot may be switched off only while another stays on. */
    static boolean mayToggle(boolean[] on,Slot slot){return !on[slot.ordinal()]||enabled(on)>1;}
    /**
     * Pure: one trip into the tallies (value per slot, kept between trips): {@code trips} trips are added, then every whole item is taken out.
     * Returns the whole items per slot; {@code tally} keeps what is left. A changed choice keeps the tally (it is value, not items).
     */
    static int[] trip(double[] tally,Loot[] loot,Yield y,double trips){
        int[] out=new int[Slot.values().length];
        for(var slot:Slot.values()){int i=slot.ordinal();if(!y.on()[i])continue;
            tally[i]+=trips*perTrip(slot,y);
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
