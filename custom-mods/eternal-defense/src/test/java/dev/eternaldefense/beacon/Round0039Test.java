package dev.eternaldefense.beacon;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** The animation pass: moving parts split out of the models, drawn over them with idle motions. */
final class Round0039Test {
    static final String MODELS=Round0035Test.RES+"assets/eternal_defense/models/block/";
    static String source(String name) throws Exception{return Round0035Test.source(name);}
    static String body(String src,String from,String to){return Round0036Test.body(src,from,to);}
    static JsonObject json(String path) throws Exception{return JsonParser.parseString(Files.readString(Path.of(path))).getAsJsonObject();}
    static JsonArray elements(String model) throws Exception{return json(MODELS+model+".json").getAsJsonArray("elements");}
    static JsonObject named(JsonArray elements,String name){for(var e:elements)if(name.equals(e.getAsJsonObject().get("name").getAsString()))return e.getAsJsonObject();fail("no element "+name);return null;}
    static double[] vec(JsonObject e,String key){var a=e.getAsJsonArray(key);return new double[]{a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble()};}
    /** A point turned by an element's own rotation, the way the game bakes it. */
    static double[] rotate(double[] p,JsonObject rotation){
        if(rotation==null)return p;
        double a=Math.toRadians(rotation.get("angle").getAsDouble());var o=rotation.getAsJsonArray("origin");
        double x=p[0]-o.get(0).getAsDouble(),y=p[1]-o.get(1).getAsDouble(),z=p[2]-o.get(2).getAsDouble(),c=Math.cos(a),s=Math.sin(a);
        switch(rotation.get("axis").getAsString()){
            case "x"->{double ny=y*c-z*s,nz=y*s+z*c;y=ny;z=nz;}
            case "y"->{double nx=x*c+z*s,nz=-x*s+z*c;x=nx;z=nz;}
            default->{double nx=x*c-y*s,ny=x*s+y*c;x=nx;y=ny;}
        }
        return new double[]{x+o.get(0).getAsDouble(),y+o.get(1).getAsDouble(),z+o.get(2).getAsDouble()};
    }

    // The split: lossless, and the generator and the renderer agree
    @Test void everyPartTheRendererDrawsExistsAndNothingElse() throws Exception{
        var expected=new TreeSet<String>();
        for(var entry:Animations.PARTS.entrySet())for(var p:entry.getValue())expected.add(entry.getKey()+"_"+p+".json");
        var actual=new TreeSet<String>();try(var files=Files.list(Path.of(MODELS+"anim"))){files.forEach(f->actual.add(f.getFileName().toString()));}
        assertEquals(expected,actual,"split_animated.py and Animations.PARTS name the same parts");
        var generator=Files.readString(Path.of("../../tools/art/split_animated.py"));
        for(var model:Animations.PARTS.keySet())assertTrue(generator.contains("'"+model+"'"),model);
    }
    @Test void staticAndPartsTogetherAreExactlyTheOwnersModel() throws Exception{
        for(var model:Animations.PARTS.keySet()){
            var owner=elements(model);var stat=elements(model+"_static");
            var together=new ArrayList<String>();for(var e:stat)together.add(e.toString());
            var strips=new HashMap<String,List<JsonObject>>();
            for(var p:Animations.PARTS.get(model))for(var e:json(MODELS+"anim/"+model+"_"+p+".json").getAsJsonArray("elements")){
                var o=e.getAsJsonObject();var name=o.get("name").getAsString();
                if(name.contains("_strip_"))strips.computeIfAbsent(name.substring(0,name.indexOf("_strip_")),k->new ArrayList<>()).add(o);else together.add(o.toString());
            }
            for(var entry:strips.entrySet()){
                var whole=named(owner,entry.getKey());var parts=entry.getValue();parts.sort(Comparator.comparingDouble(o->vec(o,"from")[0]));
                assertEquals(vec(whole,"from")[0],vec(parts.get(0),"from")[0],1e-9);assertEquals(vec(whole,"to")[0],vec(parts.get(parts.size()-1),"to")[0],1e-9);
                for(int i=1;i<parts.size();i++)assertEquals(vec(parts.get(i-1),"to")[0],vec(parts.get(i),"from")[0],1e-9,"strips meet edge to edge");
                for(var o:parts)assertEquals(whole.get("rotation"),o.get("rotation"),"each strip keeps the cloth's own turn");
                // The south face's u runs from the first strip's start to the last strip's end, unbroken
                var u=whole.getAsJsonObject("faces").getAsJsonObject("south").getAsJsonArray("uv");
                assertEquals(u.get(0).getAsDouble(),parts.get(0).getAsJsonObject("faces").getAsJsonObject("south").getAsJsonArray("uv").get(0).getAsDouble(),1e-9);
                assertEquals(u.get(2).getAsDouble(),parts.get(parts.size()-1).getAsJsonObject("faces").getAsJsonObject("south").getAsJsonArray("uv").get(2).getAsDouble(),1e-9);
                together.add(whole.toString());
            }
            var ownerList=new ArrayList<String>();for(var e:owner)ownerList.add(e.toString());
            Collections.sort(ownerList);Collections.sort(together);
            assertEquals(ownerList,together,model+": nothing lost, nothing added");
            assertEquals(json(MODELS+model+".json").get("textures"),json(MODELS+model+"_static.json").get("textures"));
        }
    }
    @Test void theWorldDrawsTheStaticModelsAndTheInventoryTheOwnersWhole() throws Exception{
        String states=Round0035Test.RES+"assets/eternal_defense/blockstates/";
        for(var state:List.of("fob_beacon","satellite_beacon","command_table","defense_beacon","support_platform","tactical_enchanting_table","mess_hall_mk1","cook_pot","drink_pot","mre_maker")){
            var text=Files.readString(Path.of(states+state+".json"));
            assertTrue(text.contains("_static\""),state);
            for(var model:Animations.PARTS.keySet())assertFalse(text.contains("\"eternal_defense:block/"+model+"\""),state+" still draws the whole "+model);
        }
        String items=Round0035Test.RES+"assets/eternal_defense/models/item/";
        assertTrue(Files.readString(Path.of(items+"fob_beacon.json")).contains("\"eternal_defense:block/fob_station\""),"the icon keeps the flag");
        assertTrue(Files.readString(Path.of(items+"satellite_beacon.json")).contains("\"eternal_defense:block/satellite_beacon\""));
        assertTrue(Files.readString(Path.of(MODELS+"fob_station.json")).contains("\"Flag_hoist\""),"the owner's file is untouched");
        assertTrue(source("SupportClient.java").contains("draw(pose,buffer,RenderType.cutout(),Animations.TURRET_STATIC,light,overlay);"),"the cannon draws its turret without the hook");
    }

    // The pivots are the geometry
    @Test void theFlagHingesAreTheStripsOwnEdges() throws Exception{
        for(int k=0;k<AnimationRules.FLAG_STRIPS;k++){
            var strip=json(MODELS+"anim/fob_station_flag_"+k+".json").getAsJsonArray("elements").get(0).getAsJsonObject();
            var from=vec(strip,"from");var to=vec(strip,"to");
            var edge=rotate(new double[]{from[0],27.75,(from[2]+to[2])/2},strip.getAsJsonObject("rotation"));
            assertEquals(Animations.FLAG_HINGES[k][0],edge[0],1e-4,"hinge "+k+" x");assertEquals(Animations.FLAG_HINGES[k][1],edge[2],1e-4,"hinge "+k+" z");
        }
        var hoist=named(elements("fob_station"),"Flag_hoist");assertEquals(27.75,(vec(hoist,"from")[1]+vec(hoist,"to")[1])/2,1e-9,"the hinge line's height is the cloth's middle");
    }
    @Test void everyOtherPivotSitsOnItsPart() throws Exception{
        var sat=elements("satellite_beacon");var dish=named(sat,"Dish").getAsJsonObject("rotation").getAsJsonArray("origin");
        assertEquals(21.5,dish.get(1).getAsDouble());assertEquals(9.5,dish.get(2).getAsDouble(),"the dish tilts on its own axle");
        var mast=named(sat,"Mast");assertEquals(8,(vec(mast,"from")[0]+vec(mast,"to")[0])/2);assertEquals(10,(vec(mast,"from")[2]+vec(mast,"to")[2])/2,"it turns about the mast");
        var rod=named(elements("mess_hall_mk1"),"Spit_rod");assertEquals(12.25,(vec(rod,"from")[1]+vec(rod,"to")[1])/2);assertEquals(7,(vec(rod,"from")[2]+vec(rod,"to")[2])/2,"the roast turns on its rod");
        var mre=elements("mre_maker");
        for(var r:List.of(new Object[]{"Roller_0",4.375},new Object[]{"Roller_1",11.125})){var e=named(mre,(String)r[0]);assertEquals((double)r[1],(vec(e,"from")[2]+vec(e,"to")[2])/2);assertEquals(10.5,(vec(e,"from")[1]+vec(e,"to")[1])/2);}
        var book=named(elements("enchanting_platform"),"Book_cover_L").getAsJsonObject("rotation").getAsJsonArray("origin");
        assertEquals(16,book.get(0).getAsDouble());assertEquals(18.5,book.get(1).getAsDouble());assertEquals(7,book.get(2).getAsDouble(),"the covers breathe on the spine");
        var ench=elements("enchanting_platform");
        for(int i=0;i<9;i++){var e=named(ench,"Glyph_spark_"+i);for(int a=0;a<3;a++)assertEquals((vec(e,"from")[a]+vec(e,"to")[a])/2,Animations.SPARKS[i][a],1e-6,"spark "+i);}
        for(int i=0;i<3;i++){var e=named(ench,"Rifle_spark_"+i);for(int a=0;a<3;a++)assertEquals((vec(e,"from")[a]+vec(e,"to")[a])/2,Animations.RIFLE_SPARKS[i][a],1e-6,"rifle spark "+i);}
        var cable=named(elements("support_cannon_turret"),"Crane_cable");assertEquals(30.5,vec(cable,"to")[1]);assertEquals(7.25,(vec(cable,"from")[0]+vec(cable,"to")[0])/2);assertEquals(20,(vec(cable,"from")[2]+vec(cable,"to")[2])/2,"the hook hangs from the jib");
        var whip=named(elements("fob_station"),"Whip");assertEquals(4.25,(vec(whip,"from")[0]+vec(whip,"to")[0])/2);assertEquals(14.25,vec(whip,"from")[1]);assertEquals(13,(vec(whip,"from")[2]+vec(whip,"to")[2])/2,"the whip sways on its foot");
        var anim=source("Animations.java");
        assertTrue(anim.contains("whip(c,\"fob_station\",\"whip\",4.25,14.25,13,")&&anim.contains("whip(c,\"satellite_beacon\",\"whip\",3.5,9.5,12.25,")&&anim.contains("whip(c,\"command_table\",\"antenna\",1.75,19.25,26.25,"));
        assertTrue(anim.contains("x.about(8,0,10,Axis.YP,d[0])")&&anim.contains("x.about(0,21.5,9.5,Axis.XP,d[1])")&&anim.contains("x.about(0,12.25,7,Axis.XP,")&&anim.contains("x.about(7.25,30.5,20,Axis.XP,h[0])"));
    }

    // The satellite dish keeps clear of its whip, mast and cabinet anywhere in its range
    @Test void theDishNeverPassesThroughTheRestOfTheBlock() throws Exception{
        var points=new ArrayList<double[]>();
        for(var e:json(MODELS+"anim/satellite_beacon_dish.json").getAsJsonArray("elements")){
            var o=e.getAsJsonObject();var f=vec(o,"from");var t=vec(o,"to");
            for(double x=f[0];x<=t[0]+1e-9;x+=Math.max(.5,(t[0]-f[0])/12))for(double y=f[1];y<=t[1]+1e-9;y+=Math.max(.5,(t[1]-f[1])/12))for(double z:new double[]{f[2],t[2]})points.add(rotate(new double[]{x,y,z},o.getAsJsonObject("rotation")));
        }
        var obstacles=new ArrayList<JsonObject>();for(var e:elements("satellite_beacon_static"))obstacles.add(e.getAsJsonObject());
        for(var e:json(MODELS+"anim/satellite_beacon_whip.json").getAsJsonArray("elements"))obstacles.add(e.getAsJsonObject());
        for(double el=AnimationRules.ELEVATION_MIN;el<=AnimationRules.ELEVATION_MAX;el+=5)for(double az=AnimationRules.AZIMUTH_MIN;az<=AnimationRules.AZIMUTH_MAX;az+=10){
            double a=Math.toRadians(el),b=Math.toRadians(az);
            for(var p:points){
                double y=p[1]-21.5,z=p[2]-9.5;double ny=y*Math.cos(a)-z*Math.sin(a)+21.5,nz=y*Math.sin(a)+z*Math.cos(a)+9.5;
                double x=p[0]-8,zz=nz-10;double fx=x*Math.cos(b)+zz*Math.sin(b)+8,fz=-x*Math.sin(b)+zz*Math.cos(b)+10;
                for(var o:obstacles){var f=vec(o,"from");var t=vec(o,"to");
                    assertFalse(fx>f[0]-.1&&fx<t[0]+.1&&ny>f[1]-.1&&ny<t[1]+.1&&fz>f[2]-.1&&fz<t[2]+.1,"at azimuth "+az+", elevation "+el+" the dish passes through "+o.get("name"));}
            }
        }
        for(double t=0;t<2000;t+=.37){var d=AnimationRules.dish(t,12345L);
            assertTrue(d[0]>=AnimationRules.AZIMUTH_MIN-1e-9&&d[0]<=AnimationRules.AZIMUTH_MAX+1e-9&&d[1]>=AnimationRules.ELEVATION_MIN-1e-9&&d[1]<=AnimationRules.ELEVATION_MAX+1e-9,"t="+t);}
    }

    // The motions
    @Test void motionsStayInTheirRangesAndMoveSmoothly(){
        long seed=987654321L;double dt=.05;
        double[] prevFlag=AnimationRules.flag(0,seed);double[] prevDish=AnimationRules.dish(0,seed);
        for(double t=dt;t<600;t+=dt){
            var f=AnimationRules.flag(t,seed);
            for(int j=0;j<f.length;j++){assertTrue(Math.abs(f[j])<=AnimationRules.FLAG_SWING[j]+2.5+1e-9,"flag strip "+j);assertTrue(Math.abs(f[j]-prevFlag[j])<6,"no jumps in the flag");}
            prevFlag=f;
            var d=AnimationRules.dish(t,seed);assertTrue(Math.abs(d[0]-prevDish[0])<4&&Math.abs(d[1]-prevDish[1])<4,"the dish eases, it never snaps");prevDish=d;
            var s=AnimationRules.sway(t,seed,12);assertTrue(Math.abs(s[0])<=4+1e-9&&Math.abs(s[1])<=4+1e-9,"whips sway a few degrees");
            double spin=AnimationRules.spin(t,seed,9);assertTrue(spin>=0&&spin<360);
            double idx=AnimationRules.index(t,seed,6,1.1,-1);assertTrue(idx>=0&&idx<360);
            double press=AnimationRules.press(t,seed);assertTrue(press>=0&&press<=1);
            var fl=AnimationRules.flicker(t,seed);assertTrue(fl[0]>.75&&fl[0]<1.25&&fl[1]>.9&&fl[1]<1.1,"a flame flickers but keeps its size");
            var b=AnimationRules.book(t,seed);assertTrue(Math.abs(b[0])<=.6+1e-9&&Math.abs(b[1])<=12+1e-9&&b[2]>=-4-1e-9&&b[2]<=6+1e-9);
            var sp=AnimationRules.spark(t,seed,3);assertTrue(sp[1]>=.5-1e-9&&sp[1]<=1.2+1e-9);
            var h=AnimationRules.hook(t,seed);assertTrue(Math.abs(h[0])<=AnimationRules.HOOK_SWING_X+1e-9&&Math.abs(h[1])<=AnimationRules.HOOK_SWING_Z+1e-9);
            assertTrue(Math.abs(AnimationRules.ringRise(t,seed,2))<=AnimationRules.RING_RISE+1e-9);
            assertTrue(Math.abs(AnimationRules.stir(t,seed))<=28+1e-9);
        }
        assertNotEquals(AnimationRules.flag(10,1)[3],AnimationRules.flag(10,2)[3],"two blocks side by side do not wave in step");
        assertArrayEquals(AnimationRules.flag(42.5,seed),AnimationRules.flag(42.5,seed),"the same time and block, the same pose (every client sees the same)");
    }
    @Test void theBeaconRingsRestSquareAndThePressHoldsAtBothEnds(){
        long seed=55;int square=0,samples=0;
        for(double t=0;t<300;t+=.1){double a=AnimationRules.index(t,seed,6,1.1,1);samples++;if(Math.abs(a-Math.round(a/90)*90)<1e-6)square++;}
        assertTrue(square>samples*.75,"a ring sits square with the model most of the time ("+square+" of "+samples+")");
        int up=0,down=0;for(double t=0;t<400;t+=.05){double p=AnimationRules.press(t,7);if(p==0)up++;if(p==1)down++;}
        assertTrue(up>down&&down>0,"the press holds up, comes down and holds while it seals");
        assertTrue(AnimationRules.PRESS_TRAVEL*16<=3*16,"the heater stops above the pouches: 14.75 - 2.6 > 11.9");
        assertTrue(14.75-AnimationRules.PRESS_TRAVEL>11.9);
    }

    // The plumbing
    @Test void theFourBlocksWithoutDataGetABlockEntityToDrawThrough() throws Exception{
        for(var f:List.of("FobBlocks.java","TacticalBlocks.java","TacticalEnchanting.java")){
            var s=source(f);assertTrue(s.contains("implements net.minecraft.world.level.block.EntityBlock")&&s.contains("return new AnimatedParts.PartsEntity(pos,state);"),f);
        }
        assertEquals(2,source("TacticalBlocks.java").split("return new AnimatedParts.PartsEntity",-1).length-1,"the Command Table and the Satellite Beacon");
        var reg=source("EternalDefense.java");
        assertTrue(reg.contains("ENTITIES.register(\"animated_parts\"")&&reg.contains("EternalDefense.FOB_BEACON.get(),EternalDefense.SATELLITE_BEACON.get(),EternalDefense.COMMAND_TABLE.get(),EternalDefense.ENCHANTING_TABLE.get()"));
        assertTrue(source("AnimatedParts.java").contains("getRenderBoundingBox(){return new AABB(worldPosition).inflate(2);}"),"drawn while any part is in view");
        var anim=source("Animations.java");
        assertTrue(anim.contains("registerBlockEntityRenderer(EternalDefense.ANIMATED_PARTS_ENTITY.get(),PartsRenderer::new)")&&anim.contains("registerBlockEntityRenderer(EternalDefense.MESS_HALL_ENTITY.get(),PartsRenderer::new)")&&anim.contains("registerBlockEntityRenderer(EternalDefense.MRE_STATION_ENTITY.get(),PartsRenderer::new)"));
        for(var f:List.of("BeaconClient.java","SupportClient.java","KitchenRenderer.java","DrinkClient.java"))assertTrue(source(f).contains("Animations.draw("),f+" draws its parts");
        assertTrue(source("PlacementPreviewClient.java").contains("Animations.drawRest(state,pose,consumer,"),"the placement ghost shows the whole model");
        assertTrue(body(source("SupportClient.java"),"public void render(SupportPlatform.PlatformEntity be","\n        }").indexOf("Animations.draw(")<body(source("SupportClient.java"),"public void render(SupportPlatform.PlatformEntity be","\n        }").indexOf("return;"),"before the nameplate's early exit");
    }
    @Test void restIsTheOwnersPoseAndThePlayerCanTurnItOff() throws Exception{
        var anim=source("Animations.java");
        // Every motion is read only while live; at rest each part is drawn untouched
        assertFalse(anim.replace("c.live()?AnimationRules","").replace("live?AnimationRules","").contains("AnimationRules.flag(")
            ||anim.replace("c.live()?AnimationRules","").replace("live?AnimationRules","").contains("AnimationRules.dish("),"no motion outside a live check");
        assertTrue(anim.contains("if(c.live()){x.px(0,AnimationRules.bob("),"the platform's emitter too");
        assertTrue(anim.contains("boolean live=enabled();double t=live?"),"off in Settings: the rest pose");
        var cfg=source("DefenseClientConfig.java");assertTrue(cfg.contains(".define(\"idleAnimations\",true)"),"on by default, per player");
        var json=JsonParser.parseString(Round0035Test.lang()).getAsJsonObject();
        assertEquals("Idle animations",json.get("gui.eternal_defense.settings.animations").getAsString());
        assertTrue(source("BeaconClient.java").contains("b->Animations.setEnabled(!Animations.enabled())"));
    }
}
