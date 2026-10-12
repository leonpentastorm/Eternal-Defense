package dev.createarsenal.beacon;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** The owner's 0.0.36 fire support rework: Napalm Carpet sets mobs Ablaze, Spotter Arrows mark them, the Curse of Debilitation lasts until death. */
final class Round0036Test {
    static String source(String name) throws Exception{return Round0035Test.source(name);}
    static String lang() throws Exception{return Round0035Test.lang();}
    static String body(String src,String from,String to){int a=src.indexOf(from);assertTrue(a>=0,from);int b=src.indexOf(to,a+from.length());assertTrue(b>a,to);return src.substring(a,b);}

    // ---- the types keep their places -------------------------------------------------------------------------------------------------
    @Test void theThreeTypesKeepTheirIdsAndPlaces(){
        assertEquals(1,CannonUpgrades.FireType.ARROW.ordinal());assertEquals("arrow",CannonUpgrades.FireType.ARROW.id);assertEquals("fire_arrow",CannonUpgrades.FireType.ARROW.callId());
        assertEquals(5,CannonUpgrades.FireType.CURSE.ordinal());assertEquals("curse",CannonUpgrades.FireType.CURSE.id);
        assertEquals(8,CannonUpgrades.FireType.NAPALM.ordinal());assertEquals("napalm",CannonUpgrades.FireType.NAPALM.id);
        assertEquals(6,CannonUpgrades.volleys(CannonUpgrades.FireType.ARROW,0),"Spotter Arrows fire the Arrow Cluster's six volleys");
    }

    // ---- Napalm Carpet: Ablaze -------------------------------------------------------------------------------------------------------
    @Test void ablazeBurnsFourASecondScaledByDamageAndHalfForABoss(){
        assertEquals(4f,FireMarks.ablazeFor(CannonUpgrades.damage(0)),1e-6);assertEquals(6f,FireMarks.ablazeFor(CannonUpgrades.damage(2)),1e-6,"+25% a Damage level");
        assertEquals(4f,FireMarks.ablaze(4f,false),1e-6);assertEquals(2f,FireMarks.ablaze(4f,true),1e-6,"a raid boss takes half");
        assertEquals(6f,FireMarks.rekindle(4f,6f),1e-6,"set Ablaze again: the stronger burn, never the sum");assertEquals(6f,FireMarks.rekindle(6f,4f),1e-6);
        assertEquals(20,FireMarks.ABLAZE_INTERVAL,"once a second");
        assertTrue(FireMarks.canBurn(false,false));assertFalse(FireMarks.canBurn(true,false),"fire immune (blazes, Nether mobs)");assertFalse(FireMarks.canBurn(false,true),"Fire Resistance");
        var none=new int[CannonUpgrades.Upgrade.values().length];var n=CannonUpgrades.facts(CannonUpgrades.Config.of(none,CannonUpgrades.FireType.NAPALM));
        assertEquals(5,n.special(),1e-9,"the carpet's own damage a second is unchanged");assertEquals(50,n.total(),1e-9);assertEquals(4,n.ablaze(),1e-9);
        assertEquals("4",CannonUpgrades.factArgs(n)[8],"the menu's %9$s");
    }
    @Test void napalmMarksBurnableMobsAndAblazeIsTheWeaponsFire() throws Exception{
        var flares=source("SupportFlares.java");
        assertTrue(body(flares,"private void napalmTick(","/** Cryo Shell").contains("FireMarks.ignite(e,FireMarks.ablazeFor(cfg.damage()))"),"every mob in the burning carpet's box, each second");
        assertTrue(body(flares,"private void napalmTick(","/** Cryo Shell").contains("placeGround(l,Blocks.FIRE.defaultBlockState(),true)"),"the carpet itself is kept");
        var marks=source("FireMarks.java");
        assertTrue(body(marks,"static void ignite(","}\n").contains("canBurn(e.fireImmune(),e.hasEffect(MobEffects.FIRE_RESISTANCE))"));
        var tick=body(marks,"public void ablazeTick(LivingEvent.LivingTickEvent e){","\n        }");
        assertTrue(tick.indexOf("if(!(mob instanceof Enemy)||!(mob.level() instanceof ServerLevel level)||!ablaze(mob)")<tick.indexOf("RaidAdaptation.weaponFire"),"an early exit before anything else");
        assertTrue(tick.contains("RaidAdaptation.weaponFire(mob,ABLAZE_FIRE_TICKS)"),"every tick the fire is the weapon's");
        assertTrue(tick.contains("if(!mob.isInWater())")&&tick.contains("setRemainingFireTicks(ABLAZE_FIRE_TICKS)"),"lit again out of water");
        var burn=body(marks,"private static void burn(","\n    }");
        assertTrue(burn.contains("RaidAdaptation.weaponDamage(e,e.damageSources().onFire(),amount)"),"the burn counts as weapon damage");
        assertTrue(burn.contains("int invulnerable=e.invulnerableTime;e.invulnerableTime=0;")&&burn.contains("e.invulnerableTime=invulnerable;"),"on top of other damage: the invulnerability window is left as it was");
        assertTrue(body(marks,"public void ablazeAttack(LivingAttackEvent e){","\n        }").contains("!BURNING.get()&&e.getSource().is(DamageTypes.ON_FIRE)"),"vanilla's own burn is replaced, never added");
        assertTrue(body(marks,"public void marksDeath(LivingDeathEvent e){","\n        }").contains("n.remove(ABLAZE);n.remove(MARKED);"),"cleared on death");
        assertTrue(source("ArsenalBeacon.java").contains("MinecraftForge.EVENT_BUS.register(new FireMarks.MarkEvents());"));
    }

    // ---- Spotter Arrows: Marked ------------------------------------------------------------------------------------------------------
    @Test void spotterArrowsHitForFourAndAMarkAddsThirtyPercent(){
        assertEquals(4f,CannonUpgrades.spotterHit(1f),1e-6);assertEquals(5f,CannonUpgrades.spotterHit(CannonUpgrades.damage(1)),1e-6);
        assertEquals(13f,FireMarks.marked(10f,false),1e-5,"+30 percent");assertEquals(11.5f,FireMarks.marked(10f,true),1e-5,"a raid boss +15 percent");
        var none=new int[CannonUpgrades.Upgrade.values().length];var a=CannonUpgrades.facts(CannonUpgrades.Config.of(none,CannonUpgrades.FireType.ARROW));
        assertEquals(4,a.special(),1e-9,"the menu's figure is one hit");assertEquals(24,a.total(),1e-9,"six volleys");
    }
    @Test void everyMobInTheBoxIsHitAndMarkedAndTheArrowsAreOnlyForShow() throws Exception{
        var flares=source("SupportFlares.java");var arrow=body(flares,"                case ARROW->{","                case NARUKAMI->{");
        assertTrue(arrow.contains("for(LivingEntity e:spotted){FireMarks.spot(e,source,hit);if(e.isAlive())FireMarks.mark(e,this);}"),"every mob, no cap");
        assertTrue(arrow.indexOf("FireMarks.spot(")<arrow.indexOf("count++>=10"),"the cap of ten is only for the arrows that fall");
        assertTrue(arrow.contains("DamageTypes.ARROW),this,getOwner())"),"an arrow-type hit from the flare, for its owner");
        assertTrue(arrow.contains("canHitEntity(net.minecraft.world.entity.Entity e){return false;}")&&arrow.contains("setBaseDamage(0)")&&arrow.contains("Pickup.DISALLOWED"),"visual only");
        assertFalse(arrow.contains("TIPPED_ARROW")||arrow.contains("setEffectsFromItem"),"no more tipped arrows");
        var marks=source("FireMarks.java");
        assertTrue(body(marks,"static boolean spot(","\n    }").contains("RaidAdaptation.weaponDamage(e,source,amount)"),"weapon damage");
        assertTrue(marks.contains("public void spotterKnockBack(LivingKnockBackEvent e){if(SPOTTING.get())e.setCanceled(true);}"),"no knockback, for this hit only");
        var hurt=body(marks,"public void markedHurt(LivingHurtEvent e){","\n        }");
        assertTrue(hurt.contains("e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY))return;"),"what ignores invulnerability is left alone");
        assertTrue(hurt.contains("e.setAmount(marked(e.getAmount(),boss(mob)))"),"every other source, guns too: one multiplier, no stacking");
        assertTrue(body(marks,"static void mark(","\n    }").contains("MobEffectInstance.INFINITE_DURATION"),"Glowing for good");
    }

    // ---- Curse of Debilitation ---------------------------------------------------------------------------------------------------------
    @Test void theCurseLastsUntilDeathWithoutGlowingAndTheDamageUpgradeLeavesItAlone() throws Exception{
        assertEquals(List.of(new CannonUpgrades.Curse("minecraft:wither",1),new CannonUpgrades.Curse("minecraft:poison",1),new CannonUpgrades.Curse("minecraft:weakness",1),
            new CannonUpgrades.Curse("minecraft:slowness",2),new CannonUpgrades.Curse("minecraft:blindness",0)),CannonUpgrades.CURSES,"Wither II, Poison II, Weakness II, Slowness III, Blindness");
        var curse=body(source("SupportFlares.java"),"                case CURSE->{","                case CLUSTER->{");
        assertTrue(curse.contains("MobEffectInstance.INFINITE_DURATION")&&curse.contains("),this);"),"until death, from the flare (the weapon, never a trap)");
        assertFalse(curse.contains("GLOWING")||curse.contains("cfg.damage()"),"no Glowing, no Damage scaling");
    }

    // ---- what the player reads ---------------------------------------------------------------------------------------------------------
    @Test void theMenuTheGuideAndTheCallSayWhatTheTypesDoNow() throws Exception{
        var lang=lang();var json=com.google.gson.JsonParser.parseString(lang).getAsJsonObject();
        java.util.function.Function<String,String> t=k->json.get("gui.arsenal_beacon."+k).getAsString();
        assertEquals("SPOTTER ARROWS",t.apply("support.name.fire_arrow"),"the call announcement");assertEquals("Spotter Arrows",t.apply("cannon.type.arrow"));
        assertFalse(lang.contains("Arrow Cluster")||lang.contains("ARROW CLUSTER")||lang.contains("tipped arrows"),"the old name and arrows are gone");
        assertTrue(t.apply("cannon.type.arrow.effect").contains("30%% more")&&t.apply("cannon.type.arrow.effect").contains("15%%"));
        assertFalse(t.apply("cannon.type.curse.effect").contains("Glowing"));assertTrue(t.apply("cannon.type.curse.effect").contains("until it dies"));
        assertTrue(t.apply("cannon.type.curse.damage").contains("the Damage upgrade does not change them"),"menu: durations no longer scale");
        assertTrue(t.apply("cannon.up.damage.desc").contains("does not change the Curse of Debilitation")&&!t.apply("cannon.up.damage.desc").contains("longer curses"));
        assertTrue(t.apply("cannon.type.napalm.damage").contains("Ablaze: %9$s every second"));
        var guide=t.apply("guide.support.body")+t.apply("guide.support.detail");
        assertTrue(guide.contains("- Spotter Arrows: ")&&guide.contains("- Ablaze: ")&&guide.contains("Wither II, Poison II, Weakness II, Slowness III and Blindness until it dies. The Damage upgrade does not change it."));
        assertTrue(guide.contains("takes "+(int)FireMarks.ABLAZE_DAMAGE+" damage a second")&&guide.contains("for "+(int)FireMarks.SPOTTER_HIT+" damage")
            &&guide.contains((int)Math.round(FireMarks.MARKED_BONUS*100)+"%% more")&&guide.contains("(a raid boss "+(int)Math.round(FireMarks.BOSS_MARKED_BONUS*100)+"%%)"),"the guide's numbers are the code's");
    }
}
