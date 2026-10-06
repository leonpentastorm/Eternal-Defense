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
    @Test void theFieldGuideQuotesTheRealNumbers(){
        String text=guide("support.detail")+guide("support.body");
        for(String expected:List.of(SupportRules.SUPPLY_FLARE_PRICE+" energy","Return Flare "+SupportRules.RETURN_FLARE_PRICE,"Fire Support Flare "+SupportRules.FIRE_FLARE_PRICE,"Support Cannon "+SupportRules.CANNON_PRICE,
                SupportRules.SUPPLY_SECONDS_OUTSIDE+" seconds",SupportRules.SUPPLY_SECONDS_UNDERGROUND+" seconds underground",
                "Fire Support lasts "+SupportRules.FIRE_BLASTS*SupportRules.FIRE_INTERVAL_TICKS/20+" seconds ("+SupportRules.FIRE_BLASTS+" shells)",
                SupportRules.FIRE_DAMAGE+" damage","within "+SupportRules.BLAST_RADIUS+" blocks of the flare","within "+SupportRules.CANNON_RANGE+" blocks",
                "portal stays open for "+SupportRules.PORTAL_LIFETIME_TICKS/20+" seconds",SupportRules.PARCEL_LIFETIME_TICKS/1200+" minutes",
                SupportRules.UPGRADE_PLATING[1]+", "+SupportRules.UPGRADE_PLATING[2]+" and "+SupportRules.UPGRADE_PLATING[3]))
            assertTrue(text.contains(expected),"guide does not mention '"+expected+"'");
    }
    private static String guide(String key){
        try(var in=SupportTest.class.getResourceAsStream("/assets/arsenal_beacon/lang/en_us.json")){
            var json=com.google.gson.JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
            return json.get("gui.arsenal_beacon.guide."+key).getAsString();
        }catch(Exception ex){throw new IllegalStateException(ex);}
    }
}
