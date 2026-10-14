package dev.eternaldefense.beacon;

import java.util.*;

/** The mission board and the one shared mission (pure; HuntData saves it and the world drives it). */
final class HuntBoard {
    enum State{OFFERED,ACTIVE,ENGAGED,CLEARED}

    /** One offer on the board: where, what, and its codename. */
    static final class Offer {
        final long id;final TacticalRules.Kind kind;final String codename;final int x,z,distance;final double bearing;
        Offer(long id,TacticalRules.Kind kind,String codename,int x,int z,int distance,double bearing){this.id=id;this.kind=kind;this.codename=codename;this.x=x;this.z=z;this.distance=distance;this.bearing=bearing;}
    }
    /** The accepted mission. */
    static final class Mission {
        final Offer offer;State state=State.ACTIVE;
        /** Every warband spawned for this mission gets a new serial */
        long serial;int total,remaining,awayTicks;
        /** How many of the warband's total are in the world (or were, and died); the rest still has to be spawned. */
        int spawned;
        /** Players who already got this offer's Return Flare */
        final Set<UUID> flared;
        Mission(Offer offer,Set<UUID> flared){this.offer=offer;this.flared=flared;}
        boolean engaged(){return state==State.ENGAGED;}
        /** Mobs still to be put into the world */
        int pending(){return engaged()?Math.max(0,total-spawned):0;}
        /** Mobs of this warband that should be alive in the world now. */
        int expectedAlive(){return engaged()?Math.max(0,spawned-(total-remaining)):0;}
    }

    final List<Offer> offers=new ArrayList<>();
    Mission mission;
    /** Per offer: who got its Return Flare. */
    final Map<Long,Set<UUID>> flared=new HashMap<>();
    Set<UUID> flared(long offerId){return flared.computeIfAbsent(offerId,k->new HashSet<>());}
    long nextId=1,lastDay=Long.MIN_VALUE;
    boolean filled;
    /** The most offers the board was filled for (the satellite's Mk at the time) */
    int capacity;

    Offer offer(long id){for(var o:offers)if(o.id==id)return o;return null;}
    boolean running(){return mission!=null&&mission.state!=State.CLEARED;}
    /** Offers that are free to accept (not the running mission). */
    List<Offer> open(){var out=new ArrayList<Offer>();for(var o:offers)if(mission==null||mission.offer.id!=o.id)out.add(o);return out;}
    List<Double> bearings(){var out=new ArrayList<Double>();for(var o:offers)out.add(o.bearing);return out;}
    int missing(int capacity){return Math.max(0,capacity-offers.size());}
    /** How many offers to add right now for a board of cap slots */
    int toFill(int cap){
        if(!filled)return missing(cap);
        return cap>capacity?Math.min(cap-capacity,missing(cap)):0;
    }
    void filledTo(int cap){filled=true;capacity=Math.max(capacity,cap);}

    /** Accept an offer: refused (a reason) when blocked is not null or the id is not on the board. */
    String accept(long id,String blocked){
        if(blocked!=null)return blocked;
        var o=offer(id);if(o==null)return "gone";
        mission=new Mission(o,flared(o.id));mission.serial=1;return null;
    }
    /** The warband spawned: ENGAGED with total mobs to kill. */
    void engage(int total){if(mission==null||mission.state!=State.ACTIVE)return;mission.state=State.ENGAGED;mission.total=mission.remaining=Math.max(1,total);mission.spawned=0;mission.awayTicks=0;}
    /** One mob of the warband was put into the world. */
    void spawnedOne(){if(mission!=null&&mission.engaged()&&mission.spawned<mission.total)mission.spawned++;}
    /** One tagged mob of the current warband died. */
    boolean killed(long missionId,long serial){
        if(mission==null||mission.state!=State.ENGAGED||mission.offer.id!=missionId||mission.serial!=serial||mission.remaining<=0)return false;
        mission.remaining--;return mission.remaining==0;
    }
    /** Fewer live mobs than there should be: some were lost without dying */
    int lost(int alive){
        if(mission==null||!mission.engaged()||mission.pending()>0)return 0;
        int missing=mission.expectedAlive()-Math.max(0,alive);if(missing<=0)return 0;
        mission.spawned-=missing;return missing;
    }
    /** The warband is dead: the mission becomes CLEARED here, before anything is paid, and only once. */
    boolean clear(){
        if(mission==null||mission.state!=State.ENGAGED||mission.remaining>0)return false;
        mission.state=State.CLEARED;offers.removeIf(o->o.id==mission.offer.id);flared.remove(mission.offer.id);return true;
    }
    /** Nobody stayed near an engaged warband: it scatters, the mission waits (ACTIVE) for the team to come back */
    void scatter(){if(mission==null||mission.state!=State.ENGAGED)return;mission.state=State.ACTIVE;mission.serial++;mission.total=mission.remaining=mission.spawned=0;mission.awayTicks=0;}
    /** Abandoned at the table: the offer goes back to the board, the warband (if any) is gone. */
    Mission abandon(){
        if(!running())return null;var ended=mission;mission=null;return ended;
    }
    /** The objective cannot be reached (no ground to spawn on): the mission ends and its offer leaves the board. */
    Mission unreachable(){if(mission==null)return null;var ended=mission;offers.removeIf(o->o.id==ended.offer.id);flared.remove(ended.offer.id);mission=null;return ended;}
    /** Forget a finished mission (after it was paid and announced). */
    void settle(){if(mission!=null&&mission.state==State.CLEARED)mission=null;}
    /** Ticks with nobody near an engaged warband; true when it should scatter. */
    boolean away(boolean nobodyNear,int ticks){
        if(mission==null||!mission.engaged())return false;
        mission.awayTicks=nobodyNear?mission.awayTicks+ticks:0;
        return mission.awayTicks>=TacticalRules.AWAY_TICKS;
    }
    /** One new offer at the first tick of a new day when the board is short */
    int dawn(long today,int capacity){int n=TacticalRules.dawnOffers(lastDay,today,missing(capacity));if(today>lastDay)lastDay=today;return n;}
}
