package dev.eternaldefense.beacon;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.*;

/** Elite raiders: a raid mob with one modifier, named over its head, worth Ardent Energy and a wave bonus. */
final class Elites {
    private Elites(){}
    enum Kind {
        BADASS("badass",25),SPEEDSTER("speedster",12),HEALER("healer",9),MATRIOSKA("matrioska",9),LONG_SHOT("long_shot",10),
        FLAK_ARMOR("flak_armor",9),BODY_ARMOR("body_armor",9),VAMPIRIC("vampiric",6),FROSTBITE("frostbite",6),BERSERKER("berserker",6);
        final String id;final int weight;
        Kind(String id,int weight){this.id=id;this.weight=weight;}
        static Kind of(String id){for(var k:values())if(k.id.equals(id))return k;return null;}
    }
    static final String TAG="eternalElite",LEVEL="eternalEliteLevel",GEN="eternalEliteGen",SCALE="eternalEliteScale",LONG_SHOT_ARROW="eternalLongShot";

    // Rules (pure)
    /** Elites a wave brings on average at a raid level: 1 per wave at level 5, 3 at level 10 */
    static double perWave(int level){int l=Math.max(0,Math.min(10,level));return l<=5?l/5.0:1+(l-5)*.4;}
    /** Pure: the elites of one wave. Below level 5 the raid's chosen wave always has one; Elite Cadre adds one a wave. */
    static int count(int level,int wave,int chosenWave,double roll,boolean cadre){
        double e=perWave(level)+(cadre?RaidModifiers.ELITE_CADRE:0);int n=(int)e+(roll<e-(int)e?1:0);
        if(level<5&&wave==chosenWave)n=Math.max(n,1);
        return n;
    }
    /** Pure: whether this spawn is an elite, spreading the wave's elites over its spawns (the last ones are certain) */
    static boolean pick(int left,int remaining,double roll){return left>0&&(remaining<=left||roll<left/(double)Math.max(1,remaining));}
    /** Badass levels, each rarer than the one before; the raid level caps them (I at level 0, V from level 8) */
    static final int[] BADASS_WEIGHTS={40,25,17,11,7};
    static int maxBadass(int raidLevel){return Math.max(1,Math.min(5,1+Math.max(0,raidLevel)/2));}
    static int badassLevel(int raidLevel,double roll){
        int max=maxBadass(raidLevel),total=0;for(int i=0;i<max;i++)total+=BADASS_WEIGHTS[i];
        double r=roll*total;for(int i=0;i<max;i++){r-=BADASS_WEIGHTS[i];if(r<0)return i+1;}
        return max;
    }
    /** Badass sizes by level: up to a giant five times as big */
    static final float[] BADASS_SIZES={1.5f,2.2f,3.0f,4.0f,5.0f};
    /** From this size a Badass smashes the blocks its body runs into */
    static final float GIANT=2;
    /** Badass: +100% health a level */
    static double healthBonus(Kind kind,int level){return switch(kind){case BADASS->Math.max(1,level);case MATRIOSKA->-.5;case FLAK_ARMOR,BODY_ARMOR->.5;default->0;};}
    static float scale(Kind kind,int level){return kind==Kind.BADASS?BADASS_SIZES[Math.max(1,Math.min(5,level))-1]:1;}
    /** Pure: extra step height of a mob this many times its size (a giant climbs what it could step over at its own size) */
    static double stride(float scale){return scale<=1?0:.6*(scale-1);}
    /** Badass: 5% slower a level; a creeper 1.5 times as much */
    static final double BADASS_SLOW=.05,CREEPER_SLOW=1.5;
    /** Pure: speed lost by a Badass of this level (a fraction) */
    static double slowdown(int level,boolean creeper){return BADASS_SLOW*Math.max(1,Math.min(5,level))*(creeper?CREEPER_SLOW:1);}
    /** Pure: a Badass creeper's blast radius grows with its size */
    static int blast(int radius,float scale){return Math.max(radius,Math.round(radius*scale));}
    /** Pure: the modifier for one spawn; Long Shot only for a mob that shoots */
    static Kind choose(double roll,boolean ranged){
        int total=0;for(var k:Kind.values())if(ranged||k!=Kind.LONG_SHOT)total+=k.weight;
        double r=roll*total;for(var k:Kind.values()){if(!ranged&&k==Kind.LONG_SHOT)continue;r-=k.weight;if(r<0)return k;}
        return Kind.BADASS;
    }
    /** Pure: Ardent Energy an elite always drops: its Badass level, 2 for the stronger kinds, 1 otherwise; a Matrioska's copies none */
    static int reward(Kind kind,int level,int generation){
        if(kind==null||generation>0)return 0;
        return switch(kind){case BADASS->Math.max(1,level);case LONG_SHOT,MATRIOSKA,HEALER->2;default->1;};
    }
    /** Pure: damage taken by an armored elite, by source kind (explosion, bullet, melee or other) */
    static double damageFactor(Kind kind,String source){
        if(kind==Kind.FLAK_ARMOR)return switch(source){case "explosion"->.4;case "bullet","melee"->1.2;default->1;};
        if(kind==Kind.BODY_ARMOR)return switch(source){case "bullet"->.4;case "explosion","melee"->1.2;default->1;};
        return 1;
    }
    /** Pure: a Berserker hits harder as it is hurt, up to twice as hard near death */
    static double berserk(float health,float max){return 1+Math.max(0,Math.min(1,1-health/Math.max(1,max)));}
    /** Matrioska copies split this many times (the first one, its 2 copies, their 4) */
    static final int MATRIOSKA_SPLITS=2,MATRIOSKA_COPIES=2;
    static final float MATRIOSKA_BLAST=2.2f,LONG_SHOT_BLAST=1.6f;
    /** Speedster: twice the spawn rush (Speed II), kept until death */
    static final double SPEEDSTER_SPEED=.8;
    static final double HEALER_RADIUS=8;static final float HEALER_HEAL=2;
    static final double VAMPIRIC_SHARE=.5;
    /** Long Shot: five times a bow's reach, a far straighter shot */
    static final double LONG_SHOT_RANGE=75,LONG_SHOT_CLOSE=10;static final float LONG_SHOT_SPEED=3.0f,LONG_SHOT_SPREAD=.5f;static final int LONG_SHOT_INTERVAL=40;

    // Attribute changes the raid's own balancing keeps
    static final UUID HEALTH=UUID.fromString("5e1c0a1e-0001-4c2a-9d1e-e7e7e7e7e701"),SPEED=UUID.fromString("5e1c0a1e-0002-4c2a-9d1e-e7e7e7e7e702"),
        RANGE=UUID.fromString("5e1c0a1e-0003-4c2a-9d1e-e7e7e7e7e703"),RAID_HEALTH=UUID.fromString("5e1c0a1e-0004-4c2a-9d1e-e7e7e7e7e704"),RAID_SPEED=UUID.fromString("5e1c0a1e-0005-4c2a-9d1e-e7e7e7e7e705"),
        STEP=UUID.fromString("5e1c0a1e-0006-4c2a-9d1e-e7e7e7e7e706");
    static final Set<UUID> OURS=Set.of(HEALTH,SPEED,RANGE,RAID_HEALTH,RAID_SPEED,STEP);
    static void modifier(Mob mob,Attribute attribute,UUID id,String name,double amount,AttributeModifier.Operation op){
        var a=mob.getAttribute(attribute);if(a==null||amount==0)return;a.removeModifier(id);a.addPermanentModifier(new AttributeModifier(id,name,amount,op));
    }
    /** Removes every modifier of an attribute except the mod's own elite and raid ones, then sets its base */
    static void reset(net.minecraft.world.entity.ai.attributes.AttributeInstance a,double base){
        if(a==null)return;for(var m:List.copyOf(a.getModifiers()))if(!OURS.contains(m.getId()))a.removeModifier(m.getId());a.setBaseValue(base);
    }

    // Server
    static Kind kind(Entity e){return e==null?null:Kind.of(e.getPersistentData().getString(TAG));}
    static boolean elite(Entity e){return kind(e)!=null;}
    /** A Badass big enough to smash its way through */
    static boolean giant(Entity e){return kind(e)==Kind.BADASS&&e.getPersistentData().getFloat(SCALE)>=GIANT;}
    /** Server: elites made by the test command outside a raid (their size is sent to clients too) */
    static final Set<UUID> LOOSE=java.util.concurrent.ConcurrentHashMap.newKeySet();
    static int reward(Entity e){var k=kind(e);return k==null?0:reward(k,e.getPersistentData().getInt(LEVEL),e.getPersistentData().getInt(GEN));}
    static boolean ranged(Mob mob){return mob instanceof net.minecraft.world.entity.monster.AbstractSkeleton||mob instanceof net.minecraft.world.entity.monster.Pillager;}
    static Component title(Kind kind,int level,Entity mob){
        var name=Component.translatable("gui.eternal_defense.elite."+kind.id);
        if(kind==Kind.BADASS)name=Component.translatable("gui.eternal_defense.elite.badass_level",ROMAN[Math.max(1,Math.min(5,level))]);
        return Component.empty().append(name).append(" ").append(mob.getType().getDescription()).withStyle(kind==Kind.BADASS&&level>=4?ChatFormatting.RED:ChatFormatting.GOLD);
    }
    static final String[] ROMAN={"","I","II","III","IV","V"};
    /** Makes a fresh raider an elite of this kind (Badass level, Matrioska generation) */
    static void make(Mob mob,Kind kind,int level,int generation){
        var n=mob.getPersistentData();n.putString(TAG,kind.id);n.putInt(LEVEL,level);n.putInt(GEN,generation);
        double bonus=healthBonus(kind,level);if(bonus!=0)modifier(mob,Attributes.MAX_HEALTH,HEALTH,"Elite health",bonus,AttributeModifier.Operation.MULTIPLY_TOTAL);
        if(kind==Kind.SPEEDSTER)modifier(mob,Attributes.MOVEMENT_SPEED,SPEED,"Elite speed",SPEEDSTER_SPEED,AttributeModifier.Operation.MULTIPLY_TOTAL);
        if(kind==Kind.BADASS)modifier(mob,Attributes.MOVEMENT_SPEED,SPEED,"Elite weight",-slowdown(level,mob instanceof Creeper),AttributeModifier.Operation.MULTIPLY_TOTAL);
        if(kind==Kind.BADASS&&mob instanceof Creeper creeper)setBlast(creeper,blast(blast(creeper),scale(kind,level)));
        if(kind==Kind.LONG_SHOT){modifier(mob,Attributes.FOLLOW_RANGE,RANGE,"Elite range",LONG_SHOT_RANGE+10,AttributeModifier.Operation.ADDITION);mob.goalSelector.addGoal(LongShotGoal.PRIORITY,new LongShotGoal(mob));}
        float s=scale(kind,level);if(s!=1){n.putFloat(SCALE,s);mob.refreshDimensions();modifier(mob,net.minecraftforge.common.ForgeMod.STEP_HEIGHT_ADDITION.get(),STEP,"Elite stride",stride(s),AttributeModifier.Operation.ADDITION);}
        mob.setCustomName(title(kind,level,mob));mob.setCustomNameVisible(true);
        mob.setHealth(mob.getMaxHealth());
    }
    private static java.lang.reflect.Field radius;
    /** A creeper's blast radius (3 for a vanilla one) */
    static int blast(Creeper creeper){try{return radiusField().getInt(creeper);}catch(ReflectiveOperationException|RuntimeException e){return 3;}}
    static void setBlast(Creeper creeper,int r){try{radiusField().setInt(creeper,r);}catch(ReflectiveOperationException|RuntimeException e){com.mojang.logging.LogUtils.getLogger().warn("[Eternal Defense] creeper blast radius not set: {}",e.toString());}}
    private static java.lang.reflect.Field radiusField(){if(radius==null)radius=net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(Creeper.class,"f_32272_");return radius;}
    /** A raider loaded from a save keeps its Long Shot aim */
    static void restore(Mob mob){if(kind(mob)==Kind.LONG_SHOT&&mob.goalSelector.getAvailableGoals().stream().noneMatch(g->g.getGoal() instanceof LongShotGoal))mob.goalSelector.addGoal(LongShotGoal.PRIORITY,new LongShotGoal(mob));}
    /** The wave's next spawn: maybe an elite (never a boss or a special-forces soldier) */
    static Kind roll(ServerLevel l,CampaignData d,Mob mob){
        if(d.introRaid||!pick(d.eliteLeft,d.spawnRemaining,l.random.nextDouble()))return null;
        var kind=choose(l.random.nextDouble(),ranged(mob));int level=kind==Kind.BADASS?badassLevel(d.raidTier,l.random.nextDouble()):1;
        make(mob,kind,level,0);d.eliteLeft--;
        if(!d.eliteThisWave){d.eliteThisWave=true;d.eliteWaves++;
            EternalDefense.announce(l,Component.translatable("gui.eternal_defense.elite.sighted",d.wave,title(kind,level,mob)));}
        d.setDirty();return kind;
    }
    /** Once a second: a Healer mends the hostiles around it */
    static void tick(ServerLevel l,Mob mob){
        if(kind(mob)!=Kind.HEALER)return;
        var box=mob.getBoundingBox().inflate(HEALER_RADIUS);int healed=0;
        for(var other:l.getEntitiesOfClass(LivingEntity.class,box,e->e instanceof Enemy&&e.isAlive()&&e.getHealth()<e.getMaxHealth()))
            if(other.distanceToSqr(mob)<=HEALER_RADIUS*HEALER_RADIUS){other.heal(HEALER_HEAL);healed++;l.sendParticles(ParticleTypes.HEART,other.getX(),other.getY()+other.getBbHeight()+.3,other.getZ(),1,.2,.1,.2,0);}
        l.sendParticles(ParticleTypes.HAPPY_VILLAGER,mob.getX(),mob.getY()+1,mob.getZ(),healed>0?12:4,HEALER_RADIUS/3,.4,HEALER_RADIUS/3,0);
    }
    /** The kind of damage, for the armored elites */
    static String source(DamageSource s){
        if(s.is(DamageTypeTags.IS_EXPLOSION))return "explosion";
        var direct=s.getDirectEntity();
        if(direct!=null&&BuiltInRegistries.ENTITY_TYPE.getKey(direct.getType()).getNamespace().equals("tacz"))return "bullet";
        if(s.typeHolder().unwrapKey().map(k->k.location().getNamespace().equals("tacz")).orElse(false)||s.is(DamageTypeTags.IS_PROJECTILE))return "bullet";
        if(direct instanceof LivingEntity&&direct==s.getEntity())return "melee";
        return "other";
    }

    /** Long Shot: stands and fires far, straight, explosive arrows at whatever it targets */
    static final class LongShotGoal extends Goal {
        /** Ahead of the raid's own beacon attack (0); a gate channel still takes over */
        static final int PRIORITY=-1;
        final Mob mob;int cooldown=LONG_SHOT_INTERVAL/2;
        LongShotGoal(Mob mob){this.mob=mob;setFlags(EnumSet.of(Goal.Flag.MOVE,Goal.Flag.LOOK));}
        LivingEntity target(){var t=mob.getTarget();return t!=null&&t.isAlive()?t:null;}
        @Override public boolean canUse(){var t=target();if(t==null||RaiderGates.channeling(mob.getUUID()))return false;double d=mob.distanceToSqr(t);return d<=LONG_SHOT_RANGE*LONG_SHOT_RANGE&&d>=LONG_SHOT_CLOSE*LONG_SHOT_CLOSE&&mob.getSensing().hasLineOfSight(t);}
        @Override public boolean canContinueToUse(){return canUse();}
        @Override public boolean requiresUpdateEveryTick(){return true;}
        @Override public void tick(){
            var t=target();if(t==null)return;
            mob.getNavigation().stop();mob.getLookControl().setLookAt(t,30,30);
            if(--cooldown>0)return;cooldown=LONG_SHOT_INTERVAL;
            var arrow=ProjectileUtil.getMobArrow(mob,new ItemStack(Items.ARROW),1f);
            double dx=t.getX()-mob.getX(),dz=t.getZ()-mob.getZ(),dy=t.getY(.5)-arrow.getY(),flat=Math.sqrt(dx*dx+dz*dz);
            double ticks=flat/LONG_SHOT_SPEED;dy+=.05*ticks*ticks*.55;   // Lifts the aim by the arrow's drop over the distance
            arrow.shoot(dx,dy,dz,LONG_SHOT_SPEED,LONG_SHOT_SPREAD);arrow.getPersistentData().putBoolean(LONG_SHOT_ARROW,true);
            mob.playSound(net.minecraft.sounds.SoundEvents.SKELETON_SHOOT,1f,.6f);mob.level().addFreshEntity(arrow);
        }
    }

    public static final class Events {
        @SubscribeEvent public void eliteHurt(LivingHurtEvent e){
            if(e.getEntity().level().isClientSide)return;
            var victim=kind(e.getEntity());
            if(victim==Kind.FLAK_ARMOR||victim==Kind.BODY_ARMOR)e.setAmount((float)(e.getAmount()*damageFactor(victim,source(e.getSource()))));
            if(!(e.getSource().getEntity() instanceof Mob attacker)||!(e.getSource().getDirectEntity()==attacker))return;
            var kind=kind(attacker);if(kind==null)return;
            switch(kind){
                case BERSERKER->e.setAmount((float)(e.getAmount()*berserk(attacker.getHealth(),attacker.getMaxHealth())));
                case VAMPIRIC->attacker.heal((float)(e.getAmount()*VAMPIRIC_SHARE));
                case FROSTBITE->{e.getEntity().addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,60,1));e.getEntity().addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN,60,0));}
                default->{}
            }
        }
        @SubscribeEvent public void eliteDeath(LivingDeathEvent e){
            if(!(e.getEntity() instanceof Mob mob)||!(mob.level() instanceof ServerLevel l)||kind(mob)!=Kind.MATRIOSKA)return;
            mob.level().explode(mob,mob.getX(),mob.getY()+.5,mob.getZ(),MATRIOSKA_BLAST,Level.ExplosionInteraction.TNT);
            int generation=mob.getPersistentData().getInt(GEN);if(generation>=MATRIOSKA_SPLITS)return;
            var d=CampaignData.get(l.getServer().overworld());if(!d.phase.equals("raid")||l!=l.getServer().overworld())return;
            String role=mob.getPersistentData().getString("eternalRole");if(role.isEmpty())role="grunt";
            // Side by side when there is room, else its own block, the other sides or one up (its blast may have left a crater)
            var here=mob.blockPosition();
            for(int i=0;i<MATRIOSKA_COPIES;i++){
                Mob copy=null;
                for(var at:List.of(here.offset(i==0?-1:1,0,0),here,here.offset(0,0,i==0?-1:1),here.offset(i==0?1:-1,0,0),here.above()))
                    if((copy=EternalDefense.raider(l,d,mob.getType(),role,d.raidType,at))!=null)break;
                if(copy!=null){make(copy,Kind.MATRIOSKA,1,generation+1);copy.setTarget(mob.getTarget());}
            }
            RaidTypes.broadcast(l,d);d.setDirty();
        }
        @SubscribeEvent public void eliteImpact(ProjectileImpactEvent e){
            var p=e.getProjectile();if(p.level().isClientSide||!p.getPersistentData().getBoolean(LONG_SHOT_ARROW))return;
            var at=e.getRayTraceResult().getLocation();p.getPersistentData().remove(LONG_SHOT_ARROW);
            p.level().explode(p,at.x,at.y,at.z,LONG_SHOT_BLAST,Level.ExplosionInteraction.TNT);
            p.discard();e.setImpactResult(ProjectileImpactEvent.ImpactResult.STOP_AT_CURRENT_NO_DAMAGE);
        }
        @SubscribeEvent public void eliteTick(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent e){
            var entity=e.getEntity();if(entity.tickCount%20!=0||!(entity.level() instanceof ServerLevel l)||!(entity instanceof Mob mob)||kind(mob)!=Kind.HEALER)return;
            tick(l,mob);
        }
        @SubscribeEvent public void eliteJoin(net.minecraftforge.event.entity.EntityJoinLevelEvent e){if(!e.getLevel().isClientSide&&e.getEntity() instanceof Mob mob)restore(mob);}
        @SubscribeEvent public void eliteSize(EntityEvent.Size e){
            var entity=e.getEntity();float s;
            if(entity.level()==null)return;
            if(entity.level().isClientSide){Float f=SCALES.get(entity.getId());s=f==null?1:f;}
            else s=entity.getPersistentData().contains(SCALE)?entity.getPersistentData().getFloat(SCALE):1;
            if(s==1||s<=0)return;
            EntityDimensions old=e.getNewSize();e.setNewSize(old.scale(s),true);
        }
    }
    /** Client: the size of each elite in view, sent with the raid's marks */
    static final Map<Integer,Float> SCALES=new java.util.concurrent.ConcurrentHashMap<>();
}
