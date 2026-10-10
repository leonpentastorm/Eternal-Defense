package dev.createarsenal.beacon;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** 0.0.18: the owner's hand-tuned sounds, the slower cannon, the cover rule of Quantum tunneling, the beacon upgrade changes and the reworked fire supports. */
final class HandTunedRoundTest {
    // ---- sounds ------------------------------------------------------------------------------------------------------------
    @Test void theOwnersSoundsAreShippedAndMonoWhereTheyArePlacedInTheWorld() throws Exception{
        var sounds=json("/assets/arsenal_beacon/sounds.json");var lang=json("/assets/arsenal_beacon/lang/en_us.json");
        var files=Map.of("cannon.fire","cannon_fire","cannon.turning","cannon_turning","cannon.turning_done","cannon_turning_done",
            "bunker.buster","bunker_buster","fire.cluster_strike","cluster_strike","fire.shockwave","shockwave","beacon.attacked","beacon_attacked");
        for(var e:files.entrySet()){
            var event=sounds.getAsJsonObject(e.getKey());assertNotNull(event,e.getKey());
            assertEquals("arsenal_beacon:sfx/"+e.getValue(),event.getAsJsonArray("sounds").get(0).getAsJsonObject().get("name").getAsString());
            assertTrue(lang.has(event.get("subtitle").getAsString()),"subtitle for "+e.getKey());
            int channels=ogg("/assets/arsenal_beacon/sounds/sfx/"+e.getValue()+".ogg").channels();
            assertEquals(e.getKey().equals("beacon.attacked")?2:1,channels,e.getValue()+": a sound placed in the world must be mono");
        }
        // (the shell and bomb whistles came back in 0.0.19, RadioAndWhistleTest)
        for(String gone:List.of("cannon.traverse","cannon.clank","cannon.lock","shell.explosion","bunker.impact","bunker.dig","fire.cluster","fire.gravity_implode",
            "ordnance.incoming"))
            assertFalse(sounds.has(gone),gone+" was replaced");
    }
    @Test void theCannonFiresWhenTheLockSoundEnds() throws Exception{
        double done=ogg("/assets/arsenal_beacon/sounds/sfx/cannon_turning_done.ogg").seconds();
        assertEquals(Math.ceil(done*20),SupportCannon.DONE_TICKS,"the gun fires the tick the lock sound ends ("+done+" s)");
        assertEquals(SupportCannon.DONE_TICKS,SupportCannon.SETTLE_TICKS);
        // the 0.0.18 incoming sound set this pace (its impact 5.15 s in); it gave way to the whistle in 0.0.19 and the owner kept the pace
        assertEquals(103,SupportRules.BLAST_FLIGHT_TICKS);
    }
    @Test void theAttackAlarmSoundsWhenTheWarningShowsAndRepeats(){
        assertTrue(BeaconAlerts.alarm(true,-1),"when the warning appears");
        assertFalse(BeaconAlerts.alarm(true,3));assertTrue(BeaconAlerts.alarm(true,BeaconAlerts.ALARM_REPEAT_SECONDS));
        assertFalse(BeaconAlerts.alarm(false,-1));assertFalse(BeaconAlerts.alarm(false,99));
    }

    // ---- the cannon ------------------------------------------------------------------------------------------------------
    @Test void theTurretTurnsHalfAgainAsLongAndItsTopLevelIsTheOldBase(){
        assertEquals(1f,CannonUpgrades.turnSpeed(3),1e-6,"the fastest is what used to be the slowest");
        for(float deg:new float[]{90,180}){
            int old=SupportCannon.turnTicks(deg,1f),now=SupportCannon.turnTicks(deg,CannonUpgrades.turnSpeed(0));
            assertEquals(old*1.5,now,deg>=180?2:6,deg+" degrees: "+old+" -> "+now);
        }
        int previous=Integer.MAX_VALUE;
        for(int lv=0;lv<=3;lv++){int t=SupportCannon.turnTicks(180,CannonUpgrades.turnSpeed(lv));assertTrue(t<previous,"every level is faster");previous=t;}
        assertEquals(CannonUpgrades.turnSpeed(3),CannonUpgrades.turnSpeed(9),1e-6);
    }
    @Test void aFlareIsUndergroundOnlyUnderThreeSolidBlocks(){
        assertFalse(SupportRules.underground(0),"an open pit, however deep");
        assertFalse(SupportRules.underground(2),"a thin roof");
        assertTrue(SupportRules.underground(3));assertTrue(SupportRules.underground(40));
    }

    // ---- the beacon ------------------------------------------------------------------------------------------------------
    @Test void defenseIsPaidInPlatingAndTheVerticalZoneInAllFourParts(){
        assertEquals("reinforced_plating",Economy.beaconPart("defense"));assertEquals("reinforced_plating",Economy.beaconPart("core"));
        assertEquals("resonance_coil",Economy.beaconPart("reconnaissance"));
        if(Economy.standalone()){
            assertEquals(List.of(new Economy.Part("ardent_energy",4)),Economy.beaconCost("vertical",0),"standalone pays Ardent Energy");
            return;
        }
        var vertical=Economy.beaconCost("vertical",0);
        assertEquals(4,vertical.size());
        for(var part:vertical)assertEquals(2,part.amount(),part.id()+": two of every part at first");
        assertEquals(Set.copyOf(Economy.BEACON_PARTS),vertical.stream().map(Economy.Part::id).collect(java.util.stream.Collectors.toSet()));
        assertEquals(16,Economy.beaconCost("vertical",3).get(0).amount());
        assertEquals(List.of(new Economy.Part("reinforced_plating",8)),Economy.beaconCost("defense",0));
    }

    // ---- the fire supports -----------------------------------------------------------------------------------------------
    @Test void theLingeringTypesAreOneRoundAndVolleyMakesThemLast(){
        assertEquals(6,CannonUpgrades.volleys(CannonUpgrades.FireType.ARROW,0),"arrow cluster: six volleys");
        assertEquals(240,CannonUpgrades.lingerTicks(CannonUpgrades.FireType.CLUSTER,0));assertEquals(240,CannonUpgrades.lingerTicks(CannonUpgrades.FireType.CLUSTER,3),"volley does nothing for the cluster");
        assertEquals(200,CannonUpgrades.lingerTicks(CannonUpgrades.FireType.NAPALM,0));assertEquals(440,CannonUpgrades.lingerTicks(CannonUpgrades.FireType.NAPALM,3));
        assertEquals(300,CannonUpgrades.lingerTicks(CannonUpgrades.FireType.CRYO,0));assertEquals(600,CannonUpgrades.lingerTicks(CannonUpgrades.FireType.CRYO,3));
        assertEquals(100,CannonUpgrades.lingerTicks(CannonUpgrades.FireType.GRAVITY,0));assertEquals(220,CannonUpgrades.lingerTicks(CannonUpgrades.FireType.GRAVITY,3));
        assertEquals(0,CannonUpgrades.lingerTicks(CannonUpgrades.FireType.EXPLOSION,3));
        var levels=new int[CannonUpgrades.Upgrade.values().length];levels[CannonUpgrades.Upgrade.VOLLEY.ordinal()]=2;
        assertEquals(360,CannonUpgrades.Config.of(levels,CannonUpgrades.FireType.NAPALM).lingerTicks(),"the config carries the volley level");
    }
    @Test void theClusterDealsEightyPercentOfAFullBarrage(){
        int pulses=CannonUpgrades.CLUSTER_TICKS/CannonUpgrades.CLUSTER_PULSE_TICKS;
        float barrage=SupportRules.FIRE_DAMAGE*CannonUpgrades.volleys(CannonUpgrades.FireType.EXPLOSION,0);
        assertEquals(barrage*.8f,CannonUpgrades.clusterPulse(1f)*pulses,1e-3);
        assertEquals(barrage*.8f*1.5f,CannonUpgrades.clusterPulse(1.5f)*pulses,1e-3,"the Damage upgrade scales it");
        assertTrue(CannonUpgrades.CLUSTER_PULSE_TICKS>=10,"pulses never fall inside a mob's hurt cooldown");
    }
    @Test void theAreasHaveTheirNewSizes(){
        assertEquals(4.8,CannonUpgrades.radius(CannonUpgrades.FireType.NAPALM,0),1e-9);assertEquals(4.8,CannonUpgrades.radius(CannonUpgrades.FireType.CRYO,0),1e-9);
        assertEquals(8,CannonUpgrades.radius(CannonUpgrades.FireType.GRAVITY,0),1e-9,"the well reaches twice as far");
        assertEquals(Rules.radius(0),CannonUpgrades.radius(CannonUpgrades.FireType.SHOCKWAVE,0),1e-9,"as wide as a level 1 beacon zone");
        assertEquals(12.5,CannonUpgrades.radius(CannonUpgrades.FireType.SHOCKWAVE,3),1e-9);
        var c=new net.minecraft.world.phys.Vec3(0,64,0);
        var ground=SupportFlares.boxFor(CannonUpgrades.FireType.NAPALM,c,4.8);
        assertEquals(CannonUpgrades.GROUND_HEIGHT+.5,ground.getYsize(),1e-9,"napalm and frost lie flat: 2 blocks high");
        assertEquals(SupportFlares.blastBox(c,8),SupportFlares.boxFor(CannonUpgrades.FireType.GRAVITY,c,8));
        assertFalse(CannonUpgrades.FireType.CRYO.damaging());assertFalse(CannonUpgrades.FireType.GRAVITY.damaging());assertFalse(CannonUpgrades.FireType.SHOCKWAVE.damaging());
    }
    @Test void theShockwaveCarriesEveryMobPastTheEdge(){
        double r=8;
        for(double d=0;d<r;d+=.25){
            double v=CannonUpgrades.shockSpeed(d,r);assertTrue(v>=.5&&v<=1.6,"speed "+v+" at "+d);
            // ground friction keeps about 55 percent of a mob's speed each tick: the push is repeated every tick for SHOCK_PUSH_TICKS
            double x=d;for(int t=0;t<CannonUpgrades.SHOCK_PUSH_TICKS&&x<r;t++)x+=CannonUpgrades.shockSpeed(x,r);
            assertTrue(x>=r,"a mob "+d+" blocks out ends up past the edge: "+x);
        }
    }
    @Test void arrowsCarryAPotionThatHarmsTheirTarget(){
        var harmful=Set.of("harming","poison","slowness","weakness","healing");
        for(int roll=0;roll<24;roll++){
            String undead=SupportFlares.arrowPotion(true,true,roll),living=SupportFlares.arrowPotion(false,true,roll),stray=SupportFlares.arrowPotion(false,false,roll);
            assertTrue(harmful.contains(undead)&&!undead.equals("harming")&&!undead.equals("poison"),"undead are healed by harming and immune to poison: "+undead);
            assertTrue(harmful.contains(living)&&!living.equals("healing"),living);
            assertTrue(stray.equals("slowness")||stray.equals("weakness"),"an arrow at no one in particular: "+stray);
        }
        var seen=new HashSet<String>();for(int roll=0;roll<24;roll++)seen.add(SupportFlares.arrowPotion(false,true,roll));
        assertEquals(Set.of("harming","poison","slowness","weakness"),seen,"every effect comes up");
    }

    private static com.google.gson.JsonObject json(String path) throws Exception{
        try(var in=HandTunedRoundTest.class.getResourceAsStream(path)){return JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(in,path),StandardCharsets.UTF_8)).getAsJsonObject();}
    }
    record Ogg(int channels,double seconds){}
    /** Channels and length of an Ogg Vorbis file, read from its identification header and its last page's granule position. */
    static Ogg ogg(String path) throws Exception{
        byte[] b;try(var in=HandTunedRoundTest.class.getResourceAsStream(path)){b=Objects.requireNonNull(in,path).readAllBytes();}
        int packet=27+(b[26]&0xff),channels=b[packet+11]&0xff,rate=0;for(int k=3;k>=0;k--)rate=rate<<8|(b[packet+12+k]&0xff);
        int last=-1;for(int i=b.length-4;i>=0;i--)if(b[i]=='O'&&b[i+1]=='g'&&b[i+2]=='g'&&b[i+3]=='S'){last=i;break;}
        long granule=0;for(int k=7;k>=0;k--)granule=granule<<8|(b[last+6+k]&0xff);
        return new Ogg(channels,granule/(double)rate);
    }
}
