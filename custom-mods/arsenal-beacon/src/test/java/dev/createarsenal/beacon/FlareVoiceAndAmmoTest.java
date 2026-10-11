package dev.createarsenal.beacon;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** 0.0.22: the owner's supply and recall radio lines, the ammo recipes' factory parts, the minimum pay of a hunt. */
final class FlareVoiceAndAmmoTest {
    static String source(String name) throws Exception{return Files.readString(Path.of("src/main/java/dev/createarsenal/beacon/"+name));}

    // ---- the radio ------------------------------------------------------------------------------------------------------------------------
    @Test void everyFlareHasItsOwnLines(){
        assertEquals("flare.chatter_3",SupportChatter.event(SupportCalls.Kind.FIRE,3),"the six fire support lines keep their names");
        assertEquals("flare.supply_chatter_2",SupportChatter.event(SupportCalls.Kind.SUPPLY,2));
        assertEquals("flare.recall_chatter_1",SupportChatter.event(SupportCalls.Kind.RETURN,1));
        assertArrayEquals(new int[]{1,2},SupportChatter.lines(SupportCalls.Kind.SUPPLY));
        assertArrayEquals(new int[]{1,2,3},SupportChatter.lines(SupportCalls.Kind.RETURN));
    }
    @Test void aPlayersLastLineIsKeptPerFlareKind() throws Exception{
        String chatter=source("SupportChatter.java");
        assertTrue(chatter.contains("last[kind.ordinal()]"),"a supply line does not stop the next fire support call from using the same number");
        assertTrue(chatter.contains("ArsenalSounds.chatter(kind,line)"));
        assertTrue(source("SupportFlares.java").contains("if(t==0&&dest==null)SupportChatter.say(server,position(),kind,owner);"),"every kind of flare a player threw answers, return flares too");
    }
    @Test void theImportScriptKnowsTheNewGroups() throws Exception{
        String script=Files.readString(Path.of("../../tools/audio/import_chatter.py"));
        assertTrue(script.contains("(\"Supplies flare chatter\", \"supply_chatter\", 2)")&&script.contains("(\"Recall flare chatter\", \"recall_chatter\", 3)"));
    }

    // ---- ammo: twice the propellant, 20 percent more cartridge cases (rounded up) -------------------------------------------------------------
    @Test void ammoTakesTwiceThePropellantAndAFifthMoreCases(){
        for(int batch=1;batch<=256;batch++){
            int casesBefore=Math.max(1,(int)Math.ceil(batch/8.0)),propellantBefore=Math.max(1,(int)Math.ceil(batch/16.0));
            assertEquals(2*propellantBefore,AmmoInputs.propellant(batch),"propellant for "+batch+" rounds");
            assertEquals((int)Math.ceil(casesBefore*1.2-1e-9),AmmoInputs.casings(batch),"cases for "+batch+" rounds");
            assertTrue(AmmoInputs.casings(batch)>=casesBefore*1.2-1e-9,"never less than +20 percent");
        }
        assertEquals(2,AmmoInputs.casings(8),"one case becomes two: 1.2 rounded up");
        assertEquals(5,AmmoInputs.casings(32));assertEquals(10,AmmoInputs.casings(64));
        assertEquals(2,AmmoInputs.propellant(1));assertEquals(8,AmmoInputs.propellant(64));
        assertEquals(2,AmmoInputs.casings(0),"a batch is at least one round");
    }
    @Test void bothPlacesThatSetAmmoPartsUseTheSameNumbers() throws Exception{
        String js=Files.readString(Path.of("../../pack-integration/kubejs/server_scripts/20_guns_and_ammo.js"));
        assertTrue(js.contains("Java.loadClass('dev.createarsenal.beacon.AmmoInputs')"));
        assertTrue(js.contains("count:Number(ammoInputs.casings(batch))")&&js.contains("count:Number(ammoInputs.propellant(batch))"),"the KubeJS recipes");
        assertFalse(js.contains("Math.ceil(batch/16)")||js.contains("Math.ceil(batch/8)"),"the old amounts are gone");
        String platform=source("WeaponPlatform.java");
        assertTrue(platform.contains("cost(\"kubejs:cartridge_case\",AmmoInputs.casings(e.output.getCount()))")&&platform.contains("cost(\"kubejs:propellant\",AmmoInputs.propellant(e.output.getCount()))"),"the Ammo Platform without KubeJS");
        assertFalse(source("StandaloneBalance.java").contains("AmmoInputs"),"the standalone edition's ammo (vanilla materials) is not changed");
        assertTrue(java.lang.reflect.Modifier.isPublic(AmmoInputs.class.getModifiers()),"KubeJS can only load a public class");
        for(String m:List.of("casings","propellant"))assertTrue(java.lang.reflect.Modifier.isPublic(AmmoInputs.class.getMethod(m,int.class).getModifiers()),m+" is public");
    }

    // ---- the owner's report: every operation paid 0 Ardent Energy and 1 coin --------------------------------------------------------------------
    @Test void huntsPayAndFightAtTheBeaconsNextPayoutTier() throws Exception{
        String hunts=source("Hunts.java"),warband=source("HuntWarband.java"),network=source("BeaconNetwork.java");
        assertTrue(hunts.contains("static int tier(ServerLevel l,CampaignData d){return Math.max(0,Math.min(10,BeaconNetwork.prospectiveTier(l.getServer(),d)));}"));
        assertTrue(hunts.contains("var pay=Economy.hunt(m.offer.kind,tier(l,d));"),"the pay at the clear");
        assertTrue(hunts.contains("var offers=new ListTag();int tier=tier(l,d);"),"the pay and danger on the offer cards");
        assertTrue(warband.contains("int tier=Hunts.tier(l,d);"),"the warband's strength");
        for(String s:List.of(hunts,warband))assertFalse(s.contains("Economy.hunt(m.offer.kind,d.rewardTier)")||s.contains("Math.min(10,d.rewardTier))"),"no hunt reads the last won raid's tier");
        assertTrue(network.contains("prospective=prospectiveTier(p.server,d)"),"the beacon's Next payout uses the same function");
    }

    // ---- a hunt always pays something ---------------------------------------------------------------------------------------------------------
    @Test void aHuntPaysAtLeastOneArdentAndTwoCoins() throws Exception{
        for(boolean standalone:new boolean[]{false,true})for(var kind:TacticalRules.Kind.values())for(int t=-2;t<=12;t++){
            var pay=Economy.hunt(kind,t,standalone);
            assertTrue(pay.ardent()>=1&&pay.coins()>=2,kind+" at tier "+t+(standalone?" standalone":" pack")+" pays "+pay);
        }
        assertEquals(new Economy.HuntPay(1,2),Economy.hunt(TacticalRules.Kind.PATROL,0,false),"a pack Patrol at tier 0 paid 0 and 1 before 0.0.22");
        assertEquals(new Economy.HuntPay(1,2),Economy.hunt(TacticalRules.Kind.PATROL,0,true),"already at the minimum");
        assertEquals(new Economy.HuntPay(4,15),Economy.hunt(TacticalRules.Kind.WARLORD,3,false),"above the minimum nothing changes");
        assertTrue(RaidFixesTest.lang().get("gui.arsenal_beacon.guide.tactical.detail").contains("Every mission pays at least "+Economy.HUNT_MIN_ARDENT+" Ardent Energy and "+Economy.HUNT_MIN_COINS+" ammo coins."));
    }
}
