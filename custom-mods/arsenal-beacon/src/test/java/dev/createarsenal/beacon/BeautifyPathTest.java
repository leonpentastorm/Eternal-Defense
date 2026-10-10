package dev.createarsenal.beacon;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The pure parts of the Beautify Path (0.0.16): the falling ordnance, the scrolling cannon menu and the six new fire supports. */
final class BeautifyPathTest {
    @Test void ordnanceFallsInItsFlightTimeThenTravelsOn(){
        assertEquals(0,Ordnance.Fall.drop(48,30,0,0,0),1e-9);
        assertEquals(24,Ordnance.Fall.drop(48,30,0,0,15),1e-9);
        assertEquals(48,Ordnance.Fall.drop(48,30,0,0,30),1e-9,"it lands exactly when the flare resolves the shot");
        assertEquals(48,Ordnance.Fall.drop(48,30,0,0,40),1e-9,"no travel after landing without an after-time");
        assertEquals(52,Ordnance.Fall.drop(48,30,20,8,40),1e-9,"a bomb bores on through its crater");
        assertEquals(56,Ordnance.Fall.drop(48,30,20,8,90),1e-9,"and stops at the bottom");
        assertEquals(0,Ordnance.Fall.drop(48,30,0,0,-3),1e-9);
        assertEquals(SupportRules.BLAST_FLIGHT_TICKS,30,"1.5 seconds: long enough to see and hear the shell fall");
    }
    @Test void theShakeFadesWithDistance(){
        assertEquals(OrdnanceClient.BOMB_SHAKE,OrdnanceClient.shakeAt(OrdnanceClient.BOMB_SHAKE,64,0),1e-6);
        assertEquals(OrdnanceClient.BOMB_SHAKE/4,OrdnanceClient.shakeAt(OrdnanceClient.BOMB_SHAKE,64,32),1e-6);
        assertEquals(0,OrdnanceClient.shakeAt(OrdnanceClient.SHELL_SHAKE,28,28),1e-9);
        assertEquals(0,OrdnanceClient.shakeAt(OrdnanceClient.SHELL_SHAKE,28,300),1e-9);
    }
    @Test void theTypeListScrollsToKeepTheChoiceInView(){
        assertEquals(12,CannonScreen.visibleRows(400,17,46,12),"room for all");
        assertEquals(8,CannonScreen.visibleRows(46+8*17+5,17,46,12));
        assertEquals(3,CannonScreen.visibleRows(10,17,46,12),"never fewer than 3 rows");
        assertEquals(0,CannonScreen.scrollTo(0,2,8,12));
        assertEquals(4,CannonScreen.scrollTo(0,11,8,12),"the last type brings the list to its end");
        assertEquals(1,CannonScreen.scrollTo(4,1,8,12),"a choice above the window scrolls up to it");
        assertEquals(4,CannonScreen.scrollTo(99,5,8,12),"never past the end");
        assertEquals(0,CannonScreen.scrollTo(3,0,20,12),"nothing to scroll when all fit");
    }
    @Test void theUpgradesWithLevelsComeFirst(){
        var order=CannonScreen.ORDER;
        assertEquals(CannonUpgrades.Upgrade.values().length,order.size());
        int lastMulti=-1,firstSingle=order.size();
        for(int i=0;i<order.size();i++)if(order.get(i).max()>1)lastMulti=i;else firstSingle=Math.min(firstSingle,i);
        assertTrue(lastMulti<firstSingle,"three-level upgrades on top");
        assertTrue(order.indexOf(CannonUpgrades.Upgrade.AOE)<firstSingle,"Area of effect sits with the three-level upgrades");
    }
    @Test void theSixNewFireSupportsHaveTheirShellCounts(){
        assertEquals(5,CannonUpgrades.volleys(CannonUpgrades.FireType.CLUSTER,0));
        assertEquals(4,CannonUpgrades.volleys(CannonUpgrades.FireType.CRYO,0));
        assertEquals(4,CannonUpgrades.volleys(CannonUpgrades.FireType.NAPALM,0));
        assertEquals(3,CannonUpgrades.volleys(CannonUpgrades.FireType.GRAVITY,0));
        assertEquals(4,CannonUpgrades.volleys(CannonUpgrades.FireType.SHOCKWAVE,0));
        assertEquals(1,CannonUpgrades.volleys(CannonUpgrades.FireType.STARSHELL,3),"one star, whatever the volley upgrade");
        assertEquals(9,CannonUpgrades.volleys(CannonUpgrades.FireType.GRAVITY,3));
        assertFalse(CannonUpgrades.FireType.STARSHELL.damaging());assertTrue(CannonUpgrades.FireType.NAPALM.damaging());
        // the new types were added after the old ones, so saved choices and packets keep their meaning
        assertEquals(5,CannonUpgrades.FireType.CURSE.ordinal());assertEquals(6,CannonUpgrades.FireType.CLUSTER.ordinal());
        assertEquals(CannonUpgrades.FireType.values().length,SupportHud.ACCENT.length,"every type has its colour");
    }
    @Test void theNewFireSupportsQuoteTheirNumbers(){
        assertEquals(15,CannonUpgrades.hit(CannonUpgrades.BOMBLET_SHARE,1),1e-4);
        assertEquals(8,CannonUpgrades.hit(CannonUpgrades.CRYO_SHARE,1),1e-4);
        assertEquals(5,CannonUpgrades.napalm(1,1),1e-4);assertEquals(10,CannonUpgrades.napalm(2,1),1e-4);assertEquals(0,CannonUpgrades.napalm(0,1),1e-9);
        assertEquals(20,CannonUpgrades.hit(CannonUpgrades.GRAVITY_SHARE,1),1e-4);
        assertEquals(12,CannonUpgrades.hit(CannonUpgrades.SHOCKWAVE_SHARE,1),1e-4);
        assertEquals(7,CannonUpgrades.radius(CannonUpgrades.FireType.CLUSTER,0),1e-9);
        assertEquals(16,CannonUpgrades.radius(CannonUpgrades.FireType.STARSHELL,0),1e-9);assertEquals(22,CannonUpgrades.radius(CannonUpgrades.FireType.STARSHELL,3),1e-9);
        String text=SupportTest.guide("support.detail")+SupportTest.guide("support.body");
        for(String expected:java.util.List.of("6 bomblets","15 damage within 2.5 blocks","1.75 times","5 seconds","5 damage a second","20 damage","12 damage","burns 30 seconds","within 16 blocks","adds 2 blocks","1.5 seconds to fall"))
            assertTrue(text.contains(expected),"guide does not mention '"+expected+"'");
    }
    @Test void gravityAndShockwaveMoveBossesLess(){
        assertEquals(1,CannonUpgrades.moveScale(0,false),1e-9);
        assertEquals(.5,CannonUpgrades.moveScale(.5,false),1e-9);
        assertEquals(0,CannonUpgrades.moveScale(1.4,false),1e-9);
        assertEquals(CannonUpgrades.BOSS_MOVE,CannonUpgrades.moveScale(0,true),1e-9);
        double[] in=CannonUpgrades.pull(10,0,1);assertEquals(-CannonUpgrades.GRAVITY_PULL,in[0],1e-9);assertEquals(0,in[1],1e-9);
        double[] near=CannonUpgrades.pull(.1,.1,1);assertEquals(0,near[0],1e-9);
        double[] close=CannonUpgrades.pull(0,.2,1);assertEquals(0,close[1],1e-9,"never past the centre");
        double[] last=CannonUpgrades.pull(0,1,10);assertEquals(-.5,last[1],1e-9,"at most half the way in one tick");
        double[] out=CannonUpgrades.push(3,4,1);assertEquals(.6*CannonUpgrades.SHOCKWAVE_PUSH,out[0],1e-9);assertEquals(.8*CannonUpgrades.SHOCKWAVE_PUSH,out[2],1e-9);assertTrue(out[1]>0);
        double[] up=CannonUpgrades.push(0,0,1);assertEquals(0,up[0],1e-9);assertTrue(up[1]>0);
    }
}
