package dev.eternaldefense.beacon;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** 1.1.24: the Industrial Spotlight is made at Workshop Fabrication, shines one way and turns in steps of 45 degrees. */
final class Release1124Test {
    @Test void itTurnsToEightDirections(){
        var seen=new HashSet<String>();
        for(int r=0;r<8;r++){
            var d=Spotlight.direction(r);
            assertEquals(1,d.length(),1e-9);assertEquals(0,d.y,1e-9);
            seen.add(Math.round(d.x*10)+","+Math.round(d.z*10));
            assertEquals(45,Math.toDegrees(Math.acos(d.dot(Spotlight.direction((r+1)%8)))),1e-6,"a turn is 45 degrees");
            assertTrue(d.cross(Spotlight.direction((r+1)%8)).y<0,"clockwise seen from above");
        }
        assertEquals(8,seen.size());
        assertEquals(-1,Spotlight.direction(0).z,1e-9,"0 is north");assertEquals(1,Spotlight.direction(2).x,1e-9,"2 is east");
        assertEquals(1,Spotlight.direction(4).z,1e-9,"4 is south");assertEquals(-1,Spotlight.direction(6).x,1e-9,"6 is west");
    }
    @Test void itFacesTheWayThePlayerLooks(){
        // Minecraft yaw: 0 south, 90 west, 180 north, 270 east
        assertEquals(0,Spotlight.rotationFor(180));assertEquals(2,Spotlight.rotationFor(-90));assertEquals(2,Spotlight.rotationFor(270));
        assertEquals(4,Spotlight.rotationFor(0));assertEquals(6,Spotlight.rotationFor(90));
        assertEquals(1,Spotlight.rotationFor(-135));assertEquals(7,Spotlight.rotationFor(135));assertEquals(3,Spotlight.rotationFor(-45));
        assertEquals(0,Spotlight.rotationFor(180+20),"rounds to the nearest of the 8");assertEquals(1,Spotlight.rotationFor(180+25));
        for(float yaw=-720;yaw<=720;yaw+=7.5f){int r=Spotlight.rotationFor(yaw);assertTrue(r>=0&&r<8);
            double look=Math.toRadians(yaw);var d=Spotlight.direction(r);assertTrue(-Math.sin(look)*d.x+Math.cos(look)*d.z>=Math.cos(Math.toRadians(22.5))-1e-6,"faces where yaw "+yaw+" looks");}
    }
    @Test void theBeamLightsColumnsOutToItsLength(){
        var stand=new BlockPos(10,64,-5);
        for(int r=0;r<8;r++){
            var columns=Spotlight.columns(stand,r);
            assertEquals((Spotlight.BEAM_LENGTH-Spotlight.GLOW_FIRST)/Spotlight.GLOW_STEP+1,columns.size());
            var d=Spotlight.direction(r);double last=0;
            for(var c:columns){
                assertEquals(stand.getY(),c.getY());
                double along=(c.getX()-stand.getX())*d.x+(c.getZ()-stand.getZ())*d.z,off=Math.abs((c.getX()-stand.getX())*d.z-(c.getZ()-stand.getZ())*d.x);
                assertTrue(along>last&&along<=Spotlight.BEAM_LENGTH+1,"in front, nearest first");assertTrue(off<.75,"on the beam's line");last=along;
            }
        }
        assertEquals(stand.north(Spotlight.GLOW_FIRST),Spotlight.columns(stand,0).get(0));
        assertEquals(stand.east(23),Spotlight.columns(stand,2).get(5));
        assertEquals(new BlockPos(12,64,-7),Spotlight.columns(stand,1).get(0),"north-east: x up, z down");
        assertTrue(Spotlight.GLOW_STEP<=Spotlight.GLOW_LIGHT/3,"no dark gap between two lights of the beam");
    }
    @Test void itIsFabricatedNotSold() throws Exception{
        assertFalse(DefenseDepot.PRODUCTS.stream().anyMatch(p->p.id().equals("spotlight")),"no longer at the Defense Depot");
        assertTrue(WorkshopFabrication.ITEMS.contains("spotlight"));
        assertTrue(WorkshopFabrication.ITEMS.indexOf("spotlight")>WorkshopFabrication.ITEMS.indexOf("defense_depot"),"in the Defense group");
        assertEquals(WorkshopFabrication.ITEMS.size()-1,WorkshopFabrication.ITEMS.indexOf("spotlight"),"the Defense group is last");
        String economy=Release114Test.source("Economy.java");
        assertTrue(economy.contains("case \"spotlight\"->List.of(WeaponPlatform.cost(\"minecraft:iron_ingot\",SPOTLIGHT_IRON),WeaponPlatform.cost(\"minecraft:copper_ingot\",SPOTLIGHT_COPPER),WeaponPlatform.cost(\"minecraft:glass\",SPOTLIGHT_GLASS),WeaponPlatform.cost(\"minecraft:lantern\",SPOTLIGHT_LANTERNS));"));
        assertFalse(economy.contains("DEPOT_SPOTLIGHT"));
        assertTrue(Release114Test.source("WorkshopFabrication.java").contains("\"cog_dynamo\",\"spotlight\"->\". Place it inside your beacon zone or a FOB's zone.\""));
        var lang=Release114Test.lang();
        assertFalse(lang.get("gui.eternal_defense.fabrication.name.spotlight").getAsString().isBlank());
        assertTrue(lang.get("gui.eternal_defense.fabrication.desc.spotlight").getAsString().contains("45 degrees"));
    }
    @Test void theGuideQuotesTheRealNumbers() throws Exception{
        String body=Release114Test.text("guide.defense.body"),detail=Release114Test.text("guide.defense.detail");
        assertTrue(body.contains("A beam of bright light "+Spotlight.BEAM_LENGTH+" blocks long"));
        assertTrue(body.contains("for "+Economy.SPOTLIGHT_IRON+" iron ingots, "+Economy.SPOTLIGHT_COPPER+" copper ingots, "+Economy.SPOTLIGHT_GLASS+" glass and "+Economy.SPOTLIGHT_LANTERNS+" lanterns."));
        assertTrue(body.contains("turn it 45 degrees clockwise, so it can face 8 ways"));
        assertFalse(body.contains("Electric Wire and the Industrial Spotlight"),"the Depot no longer sells it");
        int last=Spotlight.columns(BlockPos.ZERO,0).size()-1;
        assertTrue(detail.contains("a beam "+Spotlight.BEAM_LENGTH+" blocks long, with light level "+Spotlight.GLOW_LIGHT+" every "+Spotlight.GLOW_STEP+" blocks from "+Spotlight.GLOW_FIRST+" blocks out"));
        assertEquals(Spotlight.GLOW_FIRST+last*Spotlight.GLOW_STEP,-Spotlight.columns(BlockPos.ZERO,0).get(last).getZ());
        var lang=Release114Test.lang();
        for(int r=0;r<8;r++)assertFalse(lang.get("gui.eternal_defense.spotlight.dir."+r).getAsString().isBlank());
        assertTrue(lang.get("gui.eternal_defense.spotlight.off").getAsString().contains("%3$s blocks"),"the pole's reach is the third argument");
    }
    @Test void theGlowIsInvisibleAndCleansItselfUp() throws Exception{
        String source=Release114Test.source("Spotlight.java");
        assertTrue(source.contains("Properties.of().replaceable().noCollission().noOcclusion().noLootTable()"),"anything placed there replaces it; it has no drops");
        assertFalse(source.contains(".air()"),"an air block would be skipped in an empty section");
        assertTrue(source.contains("else l.setBlock(pos,Blocks.AIR.defaultBlockState(),3);"),"a glow nobody claims goes out");
        assertTrue(Release114Test.source("BaseSurvey.java").contains("!(state.getBlock() instanceof Spotlight.Glow)"),"the base score ignores it");
        assertTrue(Release114Test.source("EternalDefense.java").contains("SPOTLIGHT_GLOW=BLOCKS.register(\"spotlight_glow\",Spotlight.Glow::new)"),"a block with no item");
        String client=Release114Test.source("TowerClient.java");
        assertTrue(client.contains("pose.mulPose(Axis.YP.rotationDegrees(-45*rotation))"),"drawn turned to any of the 8 ways");
        assertTrue(Release114Test.source("PlacementPreviewClient.java").contains("TowerClient.turn(pose,state.getValue(Spotlight.ROTATION))"),"the preview shows the way it will face");
        assertTrue(Release114Test.source("PowerClient.java").contains("registerBlockEntityRenderer(EternalDefense.SPOTLIGHT_ENTITY.get(),TowerClient.SpotlightRenderer::new)"));
        var state=Files.readString(Path.of("src/main/resources/assets/eternal_defense/blockstates/spotlight.json"));
        assertTrue(state.contains("\"\": {")&&!state.contains("facing"),"one model for every state; the renderer turns it");
        for(var model:List.of("spotlight","spotlight_on"))assertTrue(Files.exists(Path.of("src/main/resources/assets/eternal_defense/models/block/"+model+".json")));
        assertTrue(Files.readString(Path.of("src/main/resources/assets/eternal_defense/blockstates/spotlight_glow.json")).contains("minecraft:block/light_15"),"the beam's light has a model: vanilla's light block, nothing drawn");
    }
}
