package dev.createarsenal.beacon;

import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class SupportTest {
    @Test void platformGridGrowsFromThreeToSix(){
        assertArrayEquals(new int[]{9,16,25,36},new int[]{SupportRules.slots(1),SupportRules.slots(2),SupportRules.slots(3),SupportRules.slots(4)});
        assertEquals(16,SupportRules.upgradeCost(1));assertEquals(32,SupportRules.upgradeCost(2));assertEquals(64,SupportRules.upgradeCost(3));assertEquals(-1,SupportRules.upgradeCost(4));
        assertEquals(3,SupportRules.grid(0),"out-of-range Mk clamps");assertEquals(6,SupportRules.grid(9));
    }
    @Test void fireSupportRunsTwelveSecondsOfExactShells(){
        assertEquals(12,SupportRules.FIRE_BLASTS*SupportRules.FIRE_INTERVAL_TICKS/20);assertEquals(40,SupportRules.FIRE_INTERVAL_TICKS);
    }
    @Test void theRedBoxIsExactlyWhereShellsHurt(){
        var box=SupportFlares.blastBox(new net.minecraft.world.phys.Vec3(10,64,-5));
        assertEquals(SupportRules.BLAST_RADIUS*2,box.getXsize(),1e-9);assertEquals(box.getXsize(),box.getZsize(),1e-9);
        assertEquals(10,(box.minX+box.maxX)/2,1e-9);assertEquals(-5,(box.minZ+box.maxZ)/2,1e-9);
        assertTrue(box.minY<64&&box.maxY>64,"the flare itself is inside the box");
    }
    @Test void supplyArrivesOnTimeWhateverTheDropHeight(){
        assertEquals(240,SupportRules.supplyTicks(true));assertEquals(140,SupportRules.supplyTicks(false));
        for(int clear:new int[]{8,20,40}){
            int fall=(int)Math.ceil(clear/SupportRules.PARCEL_FALL_SPEED);
            assertEquals(SupportRules.supplyTicks(true),SupportRules.parcelSpawnTick(clear)+fall,"a "+clear+" block fall ends at the delivery time");
            assertTrue(SupportRules.parcelSpawnTick(clear)>60,"the cannon has time to turn and fire before the parcel appears");
        }
    }
    @Test void theHeavyTurretTakesTimeToTurnAndStopsOnTarget(){
        int quarter=SupportCannon.turnTicks(90),half=SupportCannon.turnTicks(180);
        assertTrue(quarter>=45,"a quarter turn takes seconds, not a blink: "+quarter);
        assertTrue(half>quarter&&half<=140,"a half turn is slower but still answers within the supply window: "+half);
        var s=new float[]{0,0};float peak=0;for(int i=0;i<300;i++){SupportCannon.spin(s,120);peak=Math.max(peak,Math.abs(s[1]));assertTrue(Math.abs(s[1])<=SupportCannon.MAX_SPEED+1e-4);}
        assertEquals(120,s[0],1e-4);assertEquals(0,s[1],1e-4);assertEquals(SupportCannon.MAX_SPEED,peak,0.15f);
    }
    @Test void theTurretReversesWithoutSnapping(){
        var s=new float[]{0,0};for(int i=0;i<40;i++)SupportCannon.spin(s,90);
        float before=s[0];for(int i=0;i<200;i++)SupportCannon.spin(s,-45);
        assertEquals(-45,s[0],1e-4);assertTrue(before>0&&before<90);
    }
    @Test void everyPlayerKeepsTheirOwnBase(){
        var data=new SupportData();UUID a=UUID.randomUUID(),b=UUID.randomUUID();
        data.setPlatform(a,new net.minecraft.core.BlockPos(0,64,0));data.setCannon(a,new net.minecraft.core.BlockPos(8,64,0));data.setPlatform(b,new net.minecraft.core.BlockPos(100,64,0));
        assertTrue(SupportData.near(data.of(a).platform,data.of(a).cannon));assertNull(data.of(b).cannon);
        var copy=SupportData.load(data.save(new net.minecraft.nbt.CompoundTag()));
        assertEquals(data.of(a).platform,copy.of(a).platform);assertEquals(data.of(b).platform,copy.of(b).platform);
        data.clearPlatform(b,new net.minecraft.core.BlockPos(100,64,0));assertNull(data.of(b),"an empty base is forgotten");
        assertFalse(SupportData.near(new net.minecraft.core.BlockPos(0,64,0),new net.minecraft.core.BlockPos(SupportRules.CANNON_RANGE+1,64,0)));
    }
    @Test void everyFireTypeHasItsOwnShellCount(){
        var u=CannonUpgrades.class;
        assertEquals(6,CannonUpgrades.volleys(CannonUpgrades.FireType.EXPLOSION,0));
        assertEquals(8,CannonUpgrades.volleys(CannonUpgrades.FireType.ARROW,0),"arrow cluster: 2 more");
        assertEquals(5,CannonUpgrades.volleys(CannonUpgrades.FireType.NARUKAMI,0),"narukami: 1 fewer");
        assertEquals(1,CannonUpgrades.volleys(CannonUpgrades.FireType.BUNKER,0));
        assertEquals(1,CannonUpgrades.volleys(CannonUpgrades.FireType.BUNKER,3),"the bunker buster ignores volley upgrades");
        assertEquals(12,CannonUpgrades.volleys(CannonUpgrades.FireType.EXPLOSION,3),"+2 per volley level");
        assertEquals(11,CannonUpgrades.volleys(CannonUpgrades.FireType.NARUKAMI,3));
        assertEquals(CannonUpgrades.FireType.EXPLOSION,CannonUpgrades.FireType.of(-4));assertEquals(CannonUpgrades.FireType.CURSE,CannonUpgrades.FireType.of(99));
    }
    @Test void upgradesGetFasterStrongerAndCapOut(){
        assertArrayEquals(new int[]{40,33,26,20},new int[]{CannonUpgrades.interval(0),CannonUpgrades.interval(1),CannonUpgrades.interval(2),CannonUpgrades.interval(3)});
        assertEquals(20,CannonUpgrades.interval(9),"never faster than one shell a second");
        assertEquals(1.75f,CannonUpgrades.damage(3),1e-6);assertEquals(1f,CannonUpgrades.damage(-2),1e-6);
        assertEquals(60,CannonUpgrades.Upgrade.TRAVERSE.price(0));assertEquals(200,CannonUpgrades.Upgrade.TRAVERSE.price(2));assertEquals(-1,CannonUpgrades.Upgrade.TRAVERSE.price(3));
        assertEquals(1,CannonUpgrades.Upgrade.QUANTUM.max());assertEquals(-1,CannonUpgrades.Upgrade.QUANTUM.price(1));
        int[] levels=new int[CannonUpgrades.Upgrade.values().length];levels[CannonUpgrades.Upgrade.QUANTUM.ordinal()]=1;levels[CannonUpgrades.Upgrade.AURA.ordinal()]=1;
        var cfg=CannonUpgrades.Config.of(levels,CannonUpgrades.FireType.HEAL);
        assertTrue(cfg.tunnel()&&cfg.aura()&&!cfg.slow()&&!cfg.longPortal());assertEquals(CannonUpgrades.FireType.HEAL,cfg.type());
        assertEquals(6000,CannonUpgrades.portalTicks(true));assertEquals(600,CannonUpgrades.portalTicks(false));
    }
    @Test void aHeavierTraverseTurnsFaster(){
        int slow=SupportCannon.turnTicks(180),fast=SupportCannon.turnTicks(180,CannonUpgrades.turnSpeed(3));
        assertTrue(fast<slow*0.7,"three levels cut the spin time a lot: "+slow+" -> "+fast);
    }
    @Test void theChoiceAndLevelsSurviveSaving(){
        var data=new SupportData();UUID id=UUID.randomUUID();var b=data.ensure(id);b.fire=3;b.up[2]=2;b.up[4]=1;
        var copy=SupportData.load(data.save(new net.minecraft.nbt.CompoundTag())).of(id);
        assertEquals(3,copy.fire);assertEquals(2,copy.up[2]);assertEquals(1,copy.up[4]);assertEquals(CannonUpgrades.FireType.BUNKER,copy.type());
        data.setPlatform(id,new net.minecraft.core.BlockPos(1,2,3));data.clearPlatform(id,new net.minecraft.core.BlockPos(1,2,3));
        assertNotNull(data.of(id),"upgrades survive picking the gear up");
    }
    @Test void theFieldGuideQuotesTheRealNumbers(){
        String text=guide("support.detail")+guide("support.body");
        for(String expected:List.of(SupportRules.SUPPLY_FLARE_PRICE+" energy","Return Flare "+SupportRules.RETURN_FLARE_PRICE,"Fire Support Flare "+SupportRules.FIRE_FLARE_PRICE,"Support Cannon "+SupportRules.CANNON_PRICE,
                SupportRules.SUPPLY_SECONDS_OUTSIDE+" seconds",SupportRules.SUPPLY_SECONDS_UNDERGROUND+" seconds underground",
                "Fire Support lasts "+SupportRules.FIRE_BLASTS*SupportRules.FIRE_INTERVAL_TICKS/20+" seconds ("+SupportRules.FIRE_BLASTS+" shells)",
                SupportRules.FIRE_DAMAGE+" damage","within "+SupportRules.BLAST_RADIUS+" blocks of the flare","within "+SupportRules.CANNON_RANGE+" blocks",
                "portal stays open for "+SupportRules.PORTAL_LIFETIME_TICKS/20+" seconds",SupportRules.PARCEL_LIFETIME_TICKS/1200+" minutes",
                SupportRules.UPGRADE_PLATING[1]+", "+SupportRules.UPGRADE_PLATING[2]+" and "+SupportRules.UPGRADE_PLATING[3],
                "Faster traverse 60, 120 and 200","Rate of fire 80, 160 and 260","More volley 100, 200 and 320","Quantum tunneling "+CannonUpgrades.Upgrade.QUANTUM.price(0),
                "Slowness field "+CannonUpgrades.Upgrade.SLOW.price(0),"Lasting portal "+CannonUpgrades.Upgrade.PORTAL.price(0),"Healing aura "+CannonUpgrades.Upgrade.AURA.price(0),
                "adds "+CannonUpgrades.VOLLEY_STEP+" shells","5 minutes","30 seconds after the parcel is emptied"))
            assertTrue(text.contains(expected),"guide does not mention '"+expected+"'");
    }
    private static String guide(String key){
        try(var in=SupportTest.class.getResourceAsStream("/assets/arsenal_beacon/lang/en_us.json")){
            var json=com.google.gson.JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
            return json.get("gui.arsenal_beacon.guide."+key).getAsString();
        }catch(Exception ex){throw new IllegalStateException(ex);}
    }
}
