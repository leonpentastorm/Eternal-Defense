package dev.createarsenal.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.List;

/**
 * The four special raids. From the fourth raid on, every raid has a 50% chance to be one of them (boss raids included), decided
 * at the end of the raid before and announced then, so the base can be prepared:
 * <ul>
 * <li><b>air</b>: vexes, phantoms and blazes. They ignore walls, so the base needs anti-air coverage, not thicker walls.</li>
 * <li><b>paratroopers</b>: heavy mobs come down by red parachute from high above the base, slowly enough to be shot dead
 *     before they land; if nobody does they take no fall damage.</li>
 * <li><b>siege</b>: ranged-heavy waves. Creepers blow holes in the walls and the skeletons and pillagers shoot through them.</li>
 * <li><b>swarm</b> ("They are thousands"): no heavy and no ranged mobs, only basic zombies, at a far higher rate.</li>
 * </ul>
 * A won special raid adds Ardent Energy to the reward that grows with the reward tier.
 */
final class RaidTypes {
    private RaidTypes(){}
    static final List<String> SPECIAL=List.of("air","paratroopers","siege","swarm");
    /** Raids before this number (counting the introduction) are always ordinary, so a base can afford support before they come. */
    static final int FIRST_SPECIAL_RAID=4;
    static final int CHANCE_PERCENT=50;
    /** Height above the beacon from which paratroopers jump, their fall speed (blocks per tick) and how long they float. */
    static final int DROP_HEIGHT=46;static final double FALL_SPEED=.16;

    /** Pure: the kind of raid number {@code raidNumber}, given two random numbers (0-99 and 0-3). */
    static String roll(int raidNumber,int percent,int pick){
        if(raidNumber<FIRST_SPECIAL_RAID||percent>=CHANCE_PERCENT)return "normal";
        return SPECIAL.get(Math.floorMod(pick,SPECIAL.size()));
    }
    static boolean special(String type){return SPECIAL.contains(type);}
    /** Extra Ardent Energy for winning a special raid: more at higher reward tiers. */
    static int bonusEnergy(int tier){return 3+2*Math.max(0,Math.min(10,tier));}
    /** Attackers of a wave relative to an ordinary raid. */
    static double countFactor(String type){return switch(type){case "swarm"->2.0;case "paratroopers"->.5;default->1.0;};}
    static int count(String type,int ordinary){return Math.max(1,Math.min(200,(int)Math.ceil(ordinary*countFactor(type))));}
    /** Ticks between spawns. */
    static int cooldown(String type){return type.equals("swarm")?8:20;}
    /** How many attackers may be alive at once, relative to the ordinary limit. */
    static int concurrent(String type,int ordinary){return type.equals("swarm")?Math.min(96,ordinary*2):ordinary;}

    static String role(String type,int tier,int roll){
        return switch(type){
            case "paratroopers"->tier>=3?"heavy":"specialist";
            case "air","siege","swarm"->"grunt";
            default->RaidBalance.role(tier,roll);
        };
    }
    /** The mob for one spawn of a special raid (never a heavy or ranged one in a swarm). */
    static EntityType<?> type(String type,int tier,String role,ServerLevel l){
        int r=l.random.nextInt(100);
        return switch(type){
            case "air"->r<45?EntityType.VEX:r<80||tier<2?EntityType.PHANTOM:EntityType.BLAZE;
            case "siege"->r<30?EntityType.CREEPER:r<68?EntityType.SKELETON:r<90||tier<3?(tier>=1?EntityType.PILLAGER:EntityType.SKELETON):EntityType.STRAY;
            case "swarm"->r<75?EntityType.ZOMBIE:EntityType.HUSK;
            default->RaidBalance.type(l,tier,role);
        };
    }
    static boolean flyer(Mob mob){return mob instanceof net.minecraft.world.entity.monster.Vex||mob instanceof net.minecraft.world.entity.monster.Phantom||mob instanceof net.minecraft.world.entity.monster.Blaze;}

    /** Where a paratrooper leaves the plane: high above a random spot over the protected area. */
    static BlockPos dropPoint(ServerLevel l,CampaignData d){
        int reach=Math.max(2,(int)(d.radius()*.7));
        int x=d.beacon.getX()+l.random.nextInt(reach*2+1)-reach,z=d.beacon.getZ()+l.random.nextInt(reach*2+1)-reach;
        int y=Math.min(l.getMaxBuildHeight()-6,d.beacon.getY()+DROP_HEIGHT);
        return new BlockPos(x,y,z);
    }
    /** Gives a mob its parachute: it floats down slowly and takes no fall damage; the effect shows the red canopy on clients. */
    static void parachute(Mob mob){
        mob.getPersistentData().putBoolean("arsenalChute",true);tell(mob,true);
        mob.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING,20*90,0,false,false,false));
    }
    /** Tells every client whether this mob wears its parachute (mob effects are not synced to clients, so a small packet does it). */
    static void tell(Mob mob,boolean on){BeaconNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.ALL.noArg(),new Chute(mob.getId(),on));}
    record Chute(int id,boolean on){
        static void encode(Chute p,net.minecraft.network.FriendlyByteBuf b){b.writeVarInt(p.id);b.writeBoolean(p.on);}
        static Chute decode(net.minecraft.network.FriendlyByteBuf b){return new Chute(b.readVarInt(),b.readBoolean());}
        static void handle(Chute p,java.util.function.Supplier<net.minecraftforge.network.NetworkEvent.Context> ctx){ctx.get().enqueueWork(()->net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,()->()->{if(p.on)CHUTED.add(p.id);else CHUTED.remove(p.id);}));ctx.get().setPacketHandled(true);}
    }
    /** Client: ids of the mobs that are floating down under a red parachute. */
    static final java.util.Set<Integer> CHUTED=java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** Things a special raid's mobs need beyond their role: phantoms must not burn in daylight. */
    static void prepare(Mob mob,String type){
        if(mob instanceof net.minecraft.world.entity.monster.Phantom)mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE,Integer.MAX_VALUE,0,false,false,false));
    }

    // ---- announcements ----------------------------------------------------------------------------------------------------------
    static Component name(String type){return Component.translatable("gui.arsenal_beacon.raidtype."+type);}
    /** At the end of a raid: decide the next one's kind and tell everybody (chat and a toast). */
    static void finish(ServerLevel l,CampaignData d){
        d.nextRaidType=roll(d.raidsStarted+1,l.random.nextInt(100),l.random.nextInt(SPECIAL.size()));d.setDirty();
        var text=Component.translatable("gui.arsenal_beacon.raidtype.next",name(d.nextRaidType)).withStyle(special(d.nextRaidType)?net.minecraft.ChatFormatting.GOLD:net.minecraft.ChatFormatting.GRAY);
        l.getServer().getPlayerList().broadcastSystemMessage(text,false);
        if(special(d.nextRaidType))l.getServer().getPlayerList().broadcastSystemMessage(Component.translatable("gui.arsenal_beacon.raidtype.tip."+d.nextRaidType).withStyle(net.minecraft.ChatFormatting.YELLOW),false);
        BeaconNetwork.announce(l,"next_raid",0,0,d.nextRaidType,"");
    }

    // ---- paratroopers: a gentle fall --------------------------------------------------------------------------------------------
    public static final class Events {
        @SubscribeEvent public void tick(LivingEvent.LivingTickEvent e){
            var mob=e.getEntity();
            if(mob.level().isClientSide||!mob.getPersistentData().getBoolean("arsenalChute"))return;
            if(mob.onGround()||mob.isInWater()){mob.getPersistentData().remove("arsenalChute");mob.removeEffect(MobEffects.SLOW_FALLING);if(mob instanceof Mob m)tell(m,false);return;}
            if(mob.tickCount%40==0&&mob instanceof Mob m)tell(m,true);
            var v=mob.getDeltaMovement();
            if(v.y<-FALL_SPEED)mob.setDeltaMovement(v.x,-FALL_SPEED,v.z);
            mob.fallDistance=0;
        }
    }
}
