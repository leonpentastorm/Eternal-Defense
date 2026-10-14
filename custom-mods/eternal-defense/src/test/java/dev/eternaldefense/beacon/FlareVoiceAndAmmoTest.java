package dev.eternaldefense.beacon;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** The supply and recall radio lines, the ammo recipes' factory parts, the minimum pay of a hunt. */
final class FlareVoiceAndAmmoTest {
    static String source(String name) throws Exception{return Files.readString(Path.of("src/main/java/dev/eternaldefense/beacon/"+name));}

    // The radio
    @Test void everyFlareHasItsOwnLines(){
        assertEquals("flare.chatter_3",SupportChatter.event(SupportCalls.Kind.FIRE,3),"the six fire support lines keep their names");
        assertEquals("flare.supply_chatter_2",SupportChatter.event(SupportCalls.Kind.SUPPLY,2));
        assertEquals("flare.recall_chatter_1",SupportChatter.event(SupportCalls.Kind.RETURN,1));
        assertArrayEquals(new int[]{1,2},SupportChatter.lines(SupportCalls.Kind.SUPPLY));
        assertArrayEquals(new int[]{1,2,3},SupportChatter.lines(SupportCalls.Kind.RETURN));
        assertEquals("flare.loadout_chatter_4",SupportChatter.event(SupportCalls.Kind.LOADOUT,4),"a Loadout call has its own four lines");
        assertArrayEquals(new int[]{1,2,3,4},SupportChatter.lines(SupportCalls.Kind.LOADOUT));
        for(var kind:SupportCalls.Kind.values())for(int line:SupportChatter.lines(kind))assertTrue(SupportChatter.ticks(kind,line)<=SupportChatter.LONGEST,"no line outlasts LONGEST: "+kind+" "+line);
    }
    @Test void aPlayersLastLineIsKeptPerFlareKind() throws Exception{
        String chatter=source("SupportChatter.java");
        assertTrue(chatter.contains("last[kind.ordinal()]"),"a supply line does not stop the next fire support call from using the same number");
        assertTrue(chatter.contains("DefenseSounds.chatter(kind,line)"));
        assertTrue(source("SupportFlares.java").contains("if(t==0&&dest==null)SupportChatter.say(server,position(),kind,owner);"),"every kind of flare a player threw answers, return flares too");
    }
    @Test void theImportScriptKnowsTheNewGroups() throws Exception{
        String script=Files.readString(Path.of("../../tools/audio/import_chatter.py"));
        assertTrue(script.contains("(\"Supplies flare chatter\", \"supply_chatter\", 2)")&&script.contains("(\"Recall flare chatter\", \"recall_chatter\", 3)"));
        assertTrue(script.contains("(\"Loadout chatter\", \"loadout_chatter\", 4)"));
    }

    // Ammo: twice the propellant, 20 percent more cartridge cases (rounded up)
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
        String rules=source("RecipeRules.java");
        assertTrue(rules.contains("row(PART+\"cartridge_case\",AmmoInputs.casings(batch,id))")&&rules.contains("row(PART+\"propellant\",AmmoInputs.propellant(batch,id))"),"the gun smith recipes, with the ammo id for the specials");
        String platform=source("WeaponPlatform.java");
        assertTrue(platform.contains("costs.add(cost(\"eternal_defense:cartridge_case\",AmmoInputs.casings(e.output.getCount(),ammo)));")&&platform.contains("costs.add(cost(\"eternal_defense:propellant\",AmmoInputs.propellant(e.output.getCount(),ammo)));"),"the Ammo Platform");
        assertTrue(platform.contains("costs.removeIf(c->is(c,\"eternal_defense:cartridge_case\")||is(c,\"eternal_defense:propellant\"));"),"0.0.26: whatever the recipe (or an older pack script) lists is replaced, so the platform always charges these amounts");
        assertTrue(java.lang.reflect.Modifier.isPublic(AmmoInputs.class.getModifiers()),"public for the other packages");
        for(String m:List.of("casings","propellant"))assertTrue(java.lang.reflect.Modifier.isPublic(AmmoInputs.class.getMethod(m,int.class).getModifiers()),m+" is public");
        for(String m:List.of("casings","propellant"))assertTrue(java.lang.reflect.Modifier.isPublic(AmmoInputs.class.getMethod(m,int.class,String.class).getModifiers()),m+"(batch, id) is public");
    }
    // Weak special rounds keep one case and one propellant
    @Test void weakSpecialRoundsKeepTheirOldPrice(){
        for(String id:List.of("hamster:flares_ammo","ronmc:bean_bag","ronmc:68pepperball","daffas_arsenal:water","bf1:medkit")){
            assertTrue(AmmoInputs.special(id),id+" is a weak special");
            assertEquals(1,AmmoInputs.casings(1,id),id+": one case");assertEquals(1,AmmoInputs.propellant(1,id),id+": one propellant");
            assertEquals(1,AmmoInputs.casings(8,id));assertEquals(1,AmmoInputs.propellant(16,id),"the old price: one case per 8 rounds, one propellant per 16");
        }
        for(String id:List.of("tacz:9mm","tacz:556x45","tacz:12g","tacz:50bmg","tacz:rpg_rocket","hamster:compact_ammo","ea:9x39","zeta:railgun_ammo","")){
            assertFalse(AmmoInputs.special(id),id+" is ordinary ammo");
            for(int batch:new int[]{1,8,30,64})assertTrue(AmmoInputs.casings(batch,id)==AmmoInputs.casings(batch)&&AmmoInputs.propellant(batch,id)==AmmoInputs.propellant(batch),id+": the 0.0.22 amounts");
        }
        assertEquals(2,AmmoInputs.casings(1,"tacz:9mm"),"ordinary ammo is never as cheap as a flare");assertEquals(2,AmmoInputs.propellant(1,"tacz:9mm"));
        assertFalse(AmmoInputs.special(null));
    }
    // Every flare needs the cannon, the Return Flare too
    @Test void theReturnFlareNeedsTheCannon() throws Exception{
        String calls=source("SupportCalls.java");
        assertFalse(calls.contains("if(kind!=Kind.RETURN){"),"no flare is spared the cannon check");
        assertTrue(calls.indexOf("if(base==null||base.cannon==null)return \"no_cannon\";")<calls.indexOf("if(kind==Kind.SUPPLY&&!platform.hasItems())"),"the cannon check comes for every kind");
        String hunts=source("Hunts.java");int gate=hunts.indexOf("static boolean returnGate(");
        assertTrue(gate>0&&!hunts.substring(gate,hunts.indexOf("\n    }",gate)).contains("SupportCalls.problem"),"an operation's return gate asks for no cannon");
    }

    // The report: every operation paid 0 Ardent Energy and 1 coin
    @Test void huntsPayAndFightAtTheBeaconsNextPayoutTier() throws Exception{
        String hunts=source("Hunts.java"),warband=source("HuntWarband.java"),network=source("BeaconNetwork.java");
        assertTrue(hunts.contains("static int tier(ServerLevel l,CampaignData d){return Math.max(0,Math.min(10,BeaconNetwork.prospectiveTier(l.getServer(),d)));}"));
        assertTrue(hunts.contains("var pay=Economy.hunt(m.offer.kind,tier(l,d));"),"the pay at the clear");
        assertTrue(hunts.contains("var offers=new ListTag();int tier=tier(l,d);"),"the pay and danger on the offer cards");
        assertTrue(warband.contains("int tier=Hunts.tier(l,d);"),"the warband's strength");
        for(String s:List.of(hunts,warband))assertFalse(s.contains("Economy.hunt(m.offer.kind,d.rewardTier)")||s.contains("Math.min(10,d.rewardTier))"),"no hunt reads the last won raid's tier");
        assertTrue(network.contains("prospective=prospectiveTier(p.server,d)"),"the beacon's Next payout uses the same function");
    }

    // A hunt always pays something
    @Test void aHuntPaysAtLeastOneArdentAndTwoCoins() throws Exception{
        for(var kind:TacticalRules.Kind.values())for(int t=-2;t<=12;t++){
            var pay=Economy.hunt(kind,t);
            assertTrue(pay.ardent()>=1&&pay.coins()>=2,kind+" at tier "+t+" pays "+pay);
        }
        assertEquals(new Economy.HuntPay(2,4),Economy.hunt(TacticalRules.Kind.PATROL,0),"a Patrol at tier 0 paid 0 and 1 before 0.0.22, the minimum 1 and 2 since, twice that since 0.0.28");
        assertEquals(new Economy.HuntPay(1,2),Economy.hunt(TacticalRules.Kind.GARRISON,0),"other classes keep the plain minimum");
        assertEquals(new Economy.HuntPay(4,15),Economy.hunt(TacticalRules.Kind.WARLORD,3),"above the minimum nothing changes");
        assertTrue(RaidFixesTest.lang().get("gui.eternal_defense.guide.tactical.detail").contains("Every mission pays at least "+Economy.HUNT_MIN_ARDENT+" Ardent Energy and "+Economy.HUNT_MIN_COINS+" ammo coins, a Patrol at least "+Economy.PATROL_PAY_FACTOR*Economy.HUNT_MIN_ARDENT+" and "+Economy.PATROL_PAY_FACTOR*Economy.HUNT_MIN_COINS+"."));
    }
}
