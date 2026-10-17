package dev.eternaldefense.beacon;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

/** 1.1.20: the Defense Depot costs four times as much, turret sounds are louder and carry further (a new shotgun shot), a pole's reach is drawn as see-through walls. */
final class Release1120Test {
    @Test void everyDepotPriceIsFourTimesTheOldOne(){
        String[] ids={"mg_turret_mk1","mg_turret_mk2","mg_turret_mk3","shotgun_turret_mk1","shotgun_turret_mk2","shotgun_turret_mk3","mg_turret_ammo","shotgun_turret_ammo","flame_turret_fuel","sandbag","barricade","barbed_wire","shock_box","electric_wire"};
        int[] old={24,48,96,24,48,96,8,8,10,4,4,4,16,4};
        var now=new java.util.HashMap<String,Integer>();for(var p:DefenseDepot.PRODUCTS)now.put(p.id(),p.price());
        for(int i=0;i<old.length;i++)assertEquals(4*old[i],now.get(ids[i]),ids[i]+" (the flame turrets cost what the others do since 1.1.23)");
    }
    @Test void theGuideQuotesEveryDepotPrice() throws Exception{
        String body=Release114Test.text("guide.defense.body");
        assertTrue(body.contains("Buy 8 for "+Economy.DEPOT_SANDBAGS+" Universal Ammo Coins at the Defense Depot."),"sandbags");
        assertTrue(body.contains("Buy 4 for "+Economy.DEPOT_BARRICADE+" Universal Ammo Coins at the Defense Depot."),"barricades");
        assertTrue(body.contains("Buy 8 for "+Economy.DEPOT_WIRE+" Universal Ammo Coins at the Defense Depot, or craft 4"),"electric wire");
        assertEquals(Economy.DEPOT_SANDBAGS,Economy.DEPOT_BARBED,"barbed wire is quoted with the same line as sandbags");
        assertFalse(body.contains("for 24, 48 and 96")||body.contains("for 32, 64 and 128")||body.contains("for 8, 8 and 10"),"no old price is left");
    }
    /** The gain of one line of the sound import table, in dB */
    static double gain(String table,String file,String target){
        var m=Pattern.compile("\\(\""+Pattern.quote(file)+"\", \""+target+"\", [^\\n]*, (-?[0-9.]+)\\),").matcher(table);
        assertTrue(m.find(),target);return Double.parseDouble(m.group(1));
    }
    @Test void turretSoundsAreTwentyPercentLouderAndTheShotgunTwentyFive() throws Exception{
        String table=Files.readString(Path.of("../../tools/audio/import_sfx.py"));
        assertEquals(.7*1.2,Math.pow(10,gain(table,"LMG_Eternal_Defense.mp3","turret_lmg")/20),.01,"the machine gun: 30% quieter, then 20% louder");
        for(var part:new String[]{"flame_start","flame_loop","flame_end"})assertEquals(1.2,Math.pow(10,gain(table,"Flamethrower.mp3",part)/20),.01,part);
        assertEquals(.7*1.25,Math.pow(10,gain(table,"M1014-3.wav","turret_shotgun")/20),.01,"the new shotgun shot: 30% quieter, then 25% louder");
        assertFalse(table.contains("\"M1014-4Suppressor.wav\""),"the old shotgun clip is gone");
        assertTrue(table.contains("REVERB = {\"turret_shotgun\""),"the shotgun keeps its reverb");
        String turrets=Release114Test.source("Turrets.java");
        assertTrue(turrets.contains("SoundEvents.ARMOR_EQUIP_IRON,SoundSource.BLOCKS,.96f,")&&turrets.contains("SoundEvents.DISPENSER_FAIL,SoundSource.BLOCKS,.72f,"),"the loading and empty sounds 20% louder too");
    }
    @Test void turretsAreHeardFromFurtherAway() throws Exception{
        var sounds=com.google.gson.JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/eternal_defense/sounds.json"))).getAsJsonObject();
        for(var event:new String[]{"turret.mg_shot","turret.shotgun_shot","turret.flame_start","turret.flame","turret.flame_end"}){
            var entry=sounds.getAsJsonObject(event).getAsJsonArray("sounds").get(0).getAsJsonObject();
            assertEquals(DefenseSounds.TURRET_RANGE,entry.get("attenuation_distance").getAsInt(),event+" fades out over the turret range, not Minecraft's 16 blocks");
        }
        assertEquals(48,DefenseSounds.TURRET_RANGE);
        assertTrue(Release114Test.source("DefenseSounds.java").contains("SoundEvent.createFixedRangeEvent(new ResourceLocation(EternalDefense.ID,id),TURRET_RANGE)"),"the server sends a shot to everyone in that range");
        assertTrue(Release114Test.source("DefenseSounds.java").contains("MG_SHOT=far(")&&Release114Test.source("DefenseSounds.java").contains("SHOTGUN_SHOT=far("));
        assertTrue(TurretEffectsClient.VOLUME<=1f,"no louder up close, and the roar's fade is heard (Minecraft caps loudness at 1)");
        assertTrue(Release114Test.source("Turrets.java").contains("DefenseSounds.MG_SHOT.get(),SoundSource.BLOCKS,1f,")&&Release114Test.source("Turrets.java").contains("DefenseSounds.SHOTGUN_SHOT.get(),SoundSource.BLOCKS,1f,"));
    }
    @Test void reachWallsSitJustInsideEveryEdge() throws Exception{
        for(var edge:PowerPoles.REACH_EDGES){
            int in=PowerPoles.inward(edge);boolean alongX=edge[2]==1;
            int inside=(alongX?edge[1]:edge[0])+(in>0?0:-1),outside=(alongX?edge[1]:edge[0])+(in>0?-1:0);
            assertTrue(alongX?PowerKit.inAura(edge[0],0,inside):PowerKit.inAura(inside,0,edge[1]),"the inward side is in reach: "+java.util.Arrays.toString(edge));
            assertFalse(alongX?PowerKit.inAura(edge[0],0,outside):PowerKit.inAura(outside,0,edge[1]),"the other side is not: "+java.util.Arrays.toString(edge));
        }
        String client=Release114Test.source("PowerClient.java");
        assertTrue(client.contains("setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE)"),"see-through, both sides, hides nothing drawn later");
        assertTrue(client.contains("BUFFER.getBuffer(Walls.TYPE)")&&client.contains("BUFFER.endBatch(Walls.TYPE)"),"the walls are drawn with the outline");
    }
}
