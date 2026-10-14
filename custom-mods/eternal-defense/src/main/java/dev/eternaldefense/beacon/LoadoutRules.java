package dev.eternaldefense.beacon;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.function.Predicate;

/** The Loadout Station's rules as plain numbers and data, so they can be tested alone. */
final class LoadoutRules {
    private LoadoutRules(){}

    // The kit: the 4 armor slots (feet, legs, chest, head, as the inventory keeps them), the off hand and the hotbar
    static final int ARMOR=4,OFFHAND_SLOT=4,HOTBAR_FIRST=5,HOTBAR=9,KIT_SLOTS=14;
    static final int COIN_SLOTS=9,LOCKER_SLOTS=27,COIN_STACK=16;
    static final String COIN_ID=EternalDefense.ID+":loadout_coin";
    /** Kit size limit in kilobytes when the config does not say. */
    static final int KIT_LIMIT_KB=64;
    /** Seconds between two prints of the same player when the config does not say. */
    static final int COOLDOWN_SECONDS=30;

    // The print sequence
    /** A player may start a print from at most this far from the door (blocks). */
    static final double REACH=4;
    /** Walking speed while held (blocks per tick); the entry lasts at least MIN_ENTRY ticks. */
    static final double WALK_SPEED=.2;
    static final int MIN_ENTRY=12,CLOSE_TICKS=12;
    /** The kit is put on at this tick of the 80-tick cycle. */
    static final int SWAP_AT=40;
    /** The exit point: the cell this many blocks in front of the door. */
    static final int EXIT_BLOCKS=2;
    /** The server puts a held player back on the path when they are farther than this from it (blocks). */
    static final double DRIFT=.75;
    /** Ticks a held player takes to turn round from the walk to the standing yaw. */
    static final int TURN_TICKS=8;
    /** Fastest a held player moves on their own screen to reach the path, when the print reached them late (blocks per tick). */
    static final double CATCH_UP=.45;
    /** Ticks a client may run behind the server's clock before its held player counts as off the path. */
    static final int LAG_TICKS=3;

    // The capsule
    /** Ticks the capsule door takes to slide, and the capsule to fold away. */
    static final int CAPSULE_DOOR_TICKS=10,CAPSULE_FOLD_TICKS=10;
    /** A capsule lands on the nearest free two-block cell within this many blocks, or becomes a parcel. */
    static final int CAPSULE_SEARCH=3;
    /** No parachute: the capsule falls from this high above its cell at this speed (blocks a tick), so the fall takes CAPSULE_FALL_TICKS. */
    static final int CAPSULE_DROP_HEIGHT=96;static final double CAPSULE_FALL_SPEED=4.0;static final int CAPSULE_FALL_TICKS=24;
    /** Ticks from the impact until the door opens by itself. */
    static final int CAPSULE_OPEN_DELAY=8;
    /** When (ticks after the cannon fired) the capsule starts its fall, so it hits at the countdown's end. */
    static int capsuleFallStart(int fireTick,boolean outside){return fireTick+SupportRules.supplyTicks(outside)-CAPSULE_FALL_TICKS;}

    /** The phases of a print. */
    enum Phase{ENTRY,CYCLE,EXIT,CLOSING,DONE}
    /** One print's timeline: entry ticks, the 80-tick cycle, the walk out and the doors closing. */
    record Timeline(int entry,int exitWalk){
        int cycleStart(){return entry;}
        int exitStart(){return entry+LoadoutModel.CYCLE_TICKS;}
        int closeStart(){return exitStart()+exitWalk;}
        int end(){return closeStart()+CLOSE_TICKS;}
        int swapTick(){return cycleStart()+SWAP_AT;}
        Phase phase(float t){return t<entry?Phase.ENTRY:t<exitStart()?Phase.CYCLE:t<closeStart()?Phase.EXIT:t<end()?Phase.CLOSING:Phase.DONE;}
        /** True while the player is held (the entry, the cycle and the walk out). */
        boolean holding(float t){return t>=0&&t<closeStart();}
        float door(float t){
            return switch(phase(t)){
                case ENTRY->LoadoutModel.DOOR_OPEN*Math.max(0f,t)/entry;
                case CYCLE->LoadoutModel.cycleDoor(t-entry);
                case EXIT->LoadoutModel.DOOR_OPEN;
                case CLOSING->LoadoutModel.DOOR_OPEN*(1f-(t-closeStart())/CLOSE_TICKS);
                case DONE->0f;
            };
        }
        float ring(float t){return phase(t)==Phase.CYCLE?LoadoutModel.cycleRing(t-entry):0f;}
        float arm(float t){return phase(t)==Phase.CYCLE?LoadoutModel.cycleArm(t-entry):0f;}
        float cycleTick(float t){return t-entry;}
    }
    static int entryTicks(double distance){return Math.max(MIN_ENTRY,walkTicks(distance));}
    static int walkTicks(double distance){return Math.max(1,(int)Math.ceil(distance/WALK_SPEED-1e-9));}
    static Timeline timeline(double entryDistance,double exitDistance){return new Timeline(entryTicks(entryDistance),walkTicks(exitDistance));}

    /** Where a held player is at tick t (blocks): along the entry, at the standing point, along the exit. */
    static double[] position(Timeline tl,float t,double[] start,double[] stand,double[] exit){
        return switch(tl.phase(t)){
            case ENTRY->lerp(start,stand,Math.max(0f,t)/tl.entry());
            case CYCLE->stand.clone();
            case EXIT->lerp(stand,exit,(t-tl.exitStart())/tl.exitWalk());
            default->exit.clone();
        };
    }
    /** The yaw a held player faces at tick t: along the walk in, turning round, out of the machine. */
    static float yaw(Timeline tl,float t,double[] start,double[] stand,float standYaw){
        float walkIn=walkYaw(start,stand,standYaw);
        if(tl.phase(t)==Phase.ENTRY)return walkIn;
        float c=t-tl.cycleStart();
        if(tl.phase(t)==Phase.CYCLE&&c<TURN_TICKS)return walkIn+wrap(standYaw-walkIn)*(c/TURN_TICKS);
        return standYaw;
    }
    static float walkYaw(double[] from,double[] to,float fallback){
        double dx=to[0]-from[0],dz=to[2]-from[2];if(dx*dx+dz*dz<1e-6)return fallback;
        return (float)Math.toDegrees(Math.atan2(-dx,dz));
    }
    static float wrap(float a){a%=360f;if(a>=180f)a-=360f;if(a<-180f)a+=360f;return a;}
    static double[] lerp(double[] a,double[] b,double f){f=Math.max(0,Math.min(1,f));return new double[]{a[0]+(b[0]-a[0])*f,a[1]+(b[1]-a[1])*f,a[2]+(b[2]-a[2])*f};}
    /** How far a held player at at is from the path at tick t: the nearest of the last LAG_TICKS + 1 ticks' positions (blocks). */
    static double drift(Timeline tl,int t,double[] at,double[] start,double[] stand,double[] exit){
        double best=Double.MAX_VALUE;for(int dt=0;dt<=LAG_TICKS;dt++)best=Math.min(best,distance(at,position(tl,Math.max(0,t-dt),start,stand,exit)));return best;
    }
    /** The next position of a held player at from heading for to: there at once when near, else CATCH_UP blocks closer. */
    static double[] toward(double[] from,double[] to){
        double d=distance(from,to);if(d<=CATCH_UP)return to.clone();
        double f=CATCH_UP/d;return new double[]{from[0]+(to[0]-from[0])*f,from[1]+(to[1]-from[1])*f,from[2]+(to[2]-from[2])*f};
    }
    static double distance(double[] a,double[] b){double x=a[0]-b[0],y=a[1]-b[1],z=a[2]-b[2];return Math.sqrt(x*x+y*y+z*z);}

    // Walking in: the player's feet in the station's own pixels (north-facing, anchor corner at 0)
    /** The door point: the middle of the doorway at floor level (px). */
    static final double[] DOOR={LoadoutModel.CENTRE,LoadoutModel.MIN,LoadoutModel.HINGE_Z};
    /** The exit point (px): the middle of the cell EXIT_BLOCKS blocks in front of the door, at floor level. */
    static double[] exitPoint(){return new double[]{LoadoutModel.CENTRE,LoadoutModel.MIN,LoadoutModel.MIN-LoadoutModel.PX*EXIT_BLOCKS+LoadoutModel.PX/2};}
    /** How far in front of the hinge plane the open leaves reach (px), from the installed door models. */
    static double leafReach(){
        double min=LoadoutModel.HINGE_Z;
        for(boolean right:new boolean[]{true,false})for(var p:LoadoutModel.doorCorners(LoadoutModel.cubes(right?"loadout_station_door_right":"loadout_station_door_left"),right,LoadoutModel.DOOR_OPEN))min=Math.min(min,p[1]);
        return min;
    }
    /**
     * Null when a player whose feet are at local px may walk in along the straight line to the standing point; otherwise
     * "front" (not in front of the doors), "far" (more than REACH blocks from the door) or "aside" (the line misses the doorway).
     */
    static String approach(double[] feet,double leafReach){
        if(feet[2]>=LoadoutModel.HINGE_Z)return "front";
        if(distance(feet,DOOR)/LoadoutModel.PX>REACH)return "far";
        double[] stand={LoadoutModel.STAND_X,LoadoutModel.STAND_Y,LoadoutModel.STAND_Z};
        for(double plane:new double[]{LoadoutModel.HINGE_Z,leafReach}){
            double x=feet[2]>=plane?feet[0]:feet[0]+(stand[0]-feet[0])*(plane-feet[2])/(stand[2]-feet[2]);
            if(x<0||x>LoadoutModel.PX)return "aside";
        }
        return null;
    }
    /** Points every tenth of a block along a straight walk (blocks), both ends included. */
    static List<double[]> samples(double[] from,double[] to){
        int n=Math.max(1,(int)Math.ceil(distance(from,to)/.1));var out=new ArrayList<double[]>();
        for(int i=0;i<=n;i++)out.add(lerp(from,to,i/(double)n));
        return out;
    }
    /** True when no sample of the walk is blocked. */
    static boolean clear(double[] from,double[] to,Predicate<double[]> blocked){for(var p:samples(from,to))if(blocked.test(p))return false;return true;}

    // The busy lock
    /** Null when player may take machine; "busy" while someone is in it, "already" while this player is in a print. */
    static String lockProblem(Map<UUID,?> byMachine,Map<UUID,?> byPlayer,UUID machine,UUID player){
        if(byMachine.containsKey(machine))return "busy";
        if(byPlayer.containsKey(player))return "already";
        return null;
    }

    // Coin slots
    /** A slot remembering kind (null: none yet) takes coin when it is empty of a kind or coin is that kind. */
    static boolean accepts(CompoundTag kind,CompoundTag coin){return coin!=null&&(kind==null||kind.equals(coin));}
    /** Where a print takes its coin from. */
    enum CoinSource{SLOT,HAND,HUB,NONE}
    /** The slot's stock first, then the coin in the main hand, then the Hub. */
    static CoinSource source(int slotStock,boolean handHasIt,boolean hubHasIt){
        return slotStock>0?CoinSource.SLOT:handHasIt?CoinSource.HAND:hubHasIt?CoinSource.HUB:CoinSource.NONE;
    }

    // The cooldown
    static long readyAt(long now,int seconds){return now+20L*Math.max(0,seconds);}
    /** Whole seconds left, 0 when ready. */
    static int secondsLeft(long readyAt,long now){return readyAt<=now?0:(int)((readyAt-now+19)/20);}

    // The kit inside a coin: Kit is a list of {Slot, Item} in slot order
    /** The coin's tag for a kit of KIT_SLOTS saved stacks (null or empty for an empty slot). */
    static CompoundTag pack(List<CompoundTag> slots){
        var list=new ListTag();
        for(int i=0;i<slots.size()&&i<KIT_SLOTS;i++){var s=slots.get(i);if(s==null||s.isEmpty())continue;var e=new CompoundTag();e.putByte("Slot",(byte)i);e.put("Item",s.copy());list.add(e);}
        var tag=new CompoundTag();tag.put("Kit",list);return tag;
    }
    /** The KIT_SLOTS saved stacks of a coin's tag (empty tags for empty slots). */
    static List<CompoundTag> unpack(CompoundTag coin){
        var out=new ArrayList<CompoundTag>();for(int i=0;i<KIT_SLOTS;i++)out.add(new CompoundTag());
        if(coin==null)return out;
        for(Tag t:coin.getList("Kit",Tag.TAG_COMPOUND)){var e=(CompoundTag)t;int i=e.getByte("Slot");if(i>=0&&i<KIT_SLOTS)out.set(i,e.getCompound("Item").copy());}
        return out;
    }
    static int kitItems(CompoundTag coin){return coin==null?0:coin.getList("Kit",Tag.TAG_COMPOUND).size();}
    /**
     * Null when the saved stacks may go into a coin, otherwise "empty", "coin" (a coin inside), "container" (a shulker box,
     * bundle or any other item that carries items) or "too_big" (over limitBytes).
     */
    static String kitProblem(List<CompoundTag> slots,int limitBytes){
        boolean any=false;
        for(var s:slots){
            if(s==null||s.isEmpty()||s.getString("id").isEmpty())continue;any=true;
            if(s.getString("id").equals(COIN_ID))return "coin";
            if(carriesItems(s))return "container";
        }
        if(!any)return "empty";
        return bytes(pack(slots))>limitBytes?"too_big":null;
    }
    /** True when a saved stack holds other items: a filled shulker box, chest or barrel, or a filled bundle. */
    static boolean carriesItems(CompoundTag stack){
        var tag=stack.getCompound("tag");
        if(!tag.getList("Items",Tag.TAG_COMPOUND).isEmpty())return true;
        return !tag.getCompound("BlockEntityTag").getList("Items",Tag.TAG_COMPOUND).isEmpty();
    }
    /** The size of a tag as it is saved, in bytes; a string too long to save counts in full. */
    static int bytes(CompoundTag tag){
        var count=new int[1];
        try(var out=new DataOutputStream(new OutputStream(){@Override public void write(int b){count[0]++;}@Override public void write(byte[] b,int off,int len){count[0]+=len;}})){NbtIo.write(tag,out);}
        catch(IOException e){throw new IllegalStateException(e);}
        return (int)Math.min(Integer.MAX_VALUE,count[0]+unsaved(tag));
    }
    /** Bytes of the strings in tag that are over the 65,535 bytes NBT can save (the game writes them empty). */
    static long unsaved(Tag tag){
        long n=0;
        if(tag instanceof CompoundTag c)for(var key:c.getAllKeys()){n+=Math.max(0,utf(key)-65535);n+=unsaved(c.get(key));}
        else if(tag instanceof ListTag l)for(var t:l)n+=unsaved(t);
        else if(tag instanceof net.minecraft.nbt.StringTag s){long u=utf(s.getAsString());if(u>65535)n+=u;}
        return n;
    }
    /** Modified UTF-8 length of a string, as DataOutput writes it. */
    static long utf(String s){long n=0;for(int i=0;i<s.length();i++){char c=s.charAt(i);n+=c>=1&&c<=0x7F?1:c<=0x7FF?2:3;}return n;}

    /** The capsule door's slide (px, 0 shut .. CAPSULE_DOOR_TRAVEL open) at game time now, opened at openAt and closed at closeAt (-1: not). */
    static float capsuleDoor(float now,long openAt,long closeAt){
        if(openAt<0)return 0f;
        float open=(float)LoadoutModel.CAPSULE_DOOR_TRAVEL*Math.max(0f,Math.min(1f,(now-openAt)/CAPSULE_DOOR_TICKS));
        if(closeAt<0||closeAt<openAt)return open;
        float at=(float)LoadoutModel.CAPSULE_DOOR_TRAVEL*Math.max(0f,Math.min(1f,(closeAt-openAt)/(float)CAPSULE_DOOR_TICKS));
        return at*Math.max(0f,1f-(now-closeAt)/CAPSULE_DOOR_TICKS);
    }
    /** How far the capsule has folded away (0 standing .. 1 gone) at game time now, folding from foldAt (-1: not). */
    static float capsuleFold(float now,long foldAt){return foldAt<0?0f:Math.max(0f,Math.min(1f,(now-foldAt)/CAPSULE_FOLD_TICKS));}

    // A Loadout Flare's coin
    /** A Loadout call: nothing is taken until the cannon fires, and then exactly once. */
    static final class Call {
        private boolean fired;private int taken;
        boolean fired(){return fired;}
        int taken(){return taken;}
        /** The cannon fires: take answers whether a coin was there; false calls the whole thing off with nothing taken. */
        boolean fire(java.util.function.BooleanSupplier take){if(fired)return true;if(!take.getAsBoolean())return false;fired=true;taken++;return true;}
    }
    /** A pending call older than this (ticks) no longer stops a new one: its flare is lost in an unloaded chunk. */
    static final int CALL_STALE=6000;

    // Why a print stops at the swap
    /** The first reason the tick-40 swap cannot happen, or null: then the coin, the stow and the kit happen together. */
    static String swapProblem(boolean online,boolean alive,boolean sameLevel,boolean machine,boolean coin,boolean room){
        if(!online)return "logout";
        if(!alive)return "death";
        if(!sameLevel)return "dimension";
        if(!machine)return "broken";
        if(!coin)return "no_coin";
        if(!room)return "no_room";
        return null;
    }
}
