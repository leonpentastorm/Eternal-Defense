package dev.eternaldefense.beacon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** The idle animations. */
@Mod.EventBusSubscriber(modid=EternalDefense.ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class Animations {
    private Animations(){}

    /** Every part the generator writes, by source model */
    static final Map<String,List<String>> PARTS=new LinkedHashMap<>();
    static{
        PARTS.put("fob_station",List.of("flag_0","flag_1","flag_2","flag_3","whip","emitter_ring"));
        PARTS.put("satellite_beacon",List.of("azimuth","dish","whip"));
        PARTS.put("command_table",List.of("beacon","antenna"));
        PARTS.put("defense_beacon_level1",List.of("ring_0"));
        PARTS.put("defense_beacon_level2",List.of("ring_0","ring_1","emitter_ring"));
        PARTS.put("defense_beacon_level3",List.of("ring_0","ring_1","ring_2","emitter_ring"));
        PARTS.put("defense_beacon_level4",List.of("ring_0","ring_1","ring_2","emitter_ring"));
        for(int mk=1;mk<=4;mk++)PARTS.put("support_platform_mk"+mk,List.of("emitter"));
        var enchanting=new ArrayList<>(List.of("book_l","book_r","levitator_ring"));for(int i=0;i<9;i++)enchanting.add("spark_"+i);for(int i=0;i<3;i++)enchanting.add("rifle_spark_"+i);
        PARTS.put("enchanting_platform",List.copyOf(enchanting));
        PARTS.put("mess_hall_mk1",List.of("spit","flame"));
        PARTS.put("cook_pot",List.of("flames"));
        PARTS.put("drink_pot",List.of("flames","paddle"));
        PARTS.put("mre_maker",List.of("press","roller_0","roller_1","lever"));
        PARTS.put("support_cannon_turret",List.of("hook"));
    }
    /** Part model ids, made once (drawn every frame). */
    private static final java.util.Map<String,java.util.Map<String,ResourceLocation>> PART_IDS=new java.util.concurrent.ConcurrentHashMap<>();
    static ResourceLocation part(String model,String part){
        var byPart=PART_IDS.get(model);if(byPart==null)PART_IDS.put(model,byPart=new java.util.concurrent.ConcurrentHashMap<>());
        var id=byPart.get(part);if(id==null)byPart.put(part,id=new ResourceLocation(EternalDefense.ID,"block/anim/"+model+"_"+part));
        return id;
    }
    /** The cannon's turret without its crane hook (the hook swings). */
    static final ResourceLocation TURRET_STATIC=new ResourceLocation(EternalDefense.ID,"block/support_cannon_turret_static");

    @SubscribeEvent public static void animationModels(ModelEvent.RegisterAdditional e){
        for(var entry:PARTS.entrySet())for(var p:entry.getValue())e.register(part(entry.getKey(),p));
        e.register(TURRET_STATIC);
    }
    @SubscribeEvent public static void animationRenderers(EntityRenderersEvent.RegisterRenderers e){
        e.registerBlockEntityRenderer(EternalDefense.ANIMATED_PARTS_ENTITY.get(),PartsRenderer::new);
        e.registerBlockEntityRenderer(EternalDefense.MESS_HALL_ENTITY.get(),PartsRenderer::new);
        e.registerBlockEntityRenderer(EternalDefense.MRE_STATION_ENTITY.get(),PartsRenderer::new);
    }
    /** For block entities that have nothing else to draw. */
    static final class PartsRenderer<T extends BlockEntity> implements BlockEntityRenderer<T> {
        PartsRenderer(BlockEntityRendererProvider.Context context){}
        @Override public void render(T be,float partial,PoseStack pose,MultiBufferSource buffer,int light,int overlay){draw(be,partial,pose,buffer,light);}
    }

    /** The player's choice in the beacon's Settings: off, every part stands still at its rest pose. */
    static boolean enabled(){return DefenseClientConfig.get(DefenseClientConfig.IDLE_ANIMATIONS);}
    static void setEnabled(boolean on){DefenseClientConfig.set(DefenseClientConfig.IDLE_ANIMATIONS,on);}

    // Drawing
    /** Two transforms kept in step: the one drawn with (it starts with the camera's) and the block's */
    static final class Xf {
        final PoseStack draw,own;
        Xf(PoseStack draw){this(draw,new PoseStack());}
        Xf(PoseStack draw,PoseStack own){this.draw=draw;this.own=own;}
        void push(){draw.pushPose();own.pushPose();}
        void pop(){draw.popPose();own.popPose();}
        /** A move in model pixels. */
        void px(double x,double y,double z){draw.translate(x/16,y/16,z/16);own.translate(x/16,y/16,z/16);}
        void turn(Axis axis,double degrees){var q=axis.rotationDegrees((float)degrees);draw.mulPose(q);own.mulPose(q);}
        /** A turn about the line through this point (model pixels). */
        void about(double x,double y,double z,Axis axis,double degrees){if(degrees==0)return;px(x,y,z);turn(axis,degrees);px(-x,-y,-z);}
        void scaleAbout(double x,double y,double z,double sx,double sy,double sz){px(x,y,z);draw.scale((float)sx,(float)sy,(float)sz);own.scale((float)sx,(float)sy,(float)sz);px(-x,-y,-z);}
    }
    /** What one block's parts are drawn with: where, the buffer, the light and tint */
    record Ctx(Xf x,VertexConsumer vc,MultiBufferSource buffer,int light,float r,float g,float b,boolean live,double t,long seed){
        int glow(){return buffer==null?light:LightTexture.FULL_BRIGHT;}
    }
    private static final Direction[] SIDES={Direction.DOWN,Direction.UP,Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST,null};
    private static final RandomSource RANDOM=RandomSource.create(42);
    /** Draws one part with the world's block shading */
    static void quads(Ctx c,ResourceLocation id,int light){
        var mc=Minecraft.getInstance();var baked=mc.getModelManager().getModel(id);
        var pose=c.x().draw.last();var normal=c.x().own.last().normal();int[] lights=LIGHTS;lights[0]=lights[1]=lights[2]=lights[3]=light;float[] shade=SHADE;
        for(var side:SIDES){
            RANDOM.setSeed(42);
            for(var q:baked.getQuads(null,side,RANDOM,ModelData.EMPTY,null)){
                float s=q.isShade()?diffuse(normal,q.getDirection()):1f;shade[0]=shade[1]=shade[2]=shade[3]=s;
                c.vc().putBulkData(pose,q,shade,c.r(),c.g(),c.b(),lights,OverlayTexture.NO_OVERLAY,false);
            }
        }
    }
    /** Scratch arrays for quads (render thread only; the vertex consumer copies them). */
    private static final int[] LIGHTS=new int[4];private static final float[] SHADE=new float[4];private static final org.joml.Vector3f FACE=new org.joml.Vector3f();
    static float diffuse(org.joml.Matrix3f normal,Direction face){
        var n=FACE.set(face.getStepX(),face.getStepY(),face.getStepZ());normal.transform(n);n.normalize();
        return Math.min(1f,n.x*n.x*.6f+n.y*n.y*((3f+n.y)/4f)+n.z*n.z*.8f);
    }
    static void part(Ctx c,String model,String part){quads(c,part(model,part),c.light());}
    static void glowing(Ctx c,String model,String part){quads(c,part(model,part),c.glow());}

    /** The parts of a block entity's model, moved by the clock (or at rest), turned with the block's facing. */
    static void draw(BlockEntity be,float partial,PoseStack pose,MultiBufferSource buffer,int light){
        var level=be.getLevel();if(level==null)return;
        boolean live=enabled();double t=live?(level.getGameTime()+(double)partial)/20.0:0;
        var x=new Xf(pose);x.push();
        var c=new Ctx(x,buffer.getBuffer(RenderType.cutout()),buffer,light,1f,1f,1f,live,t,be.getBlockPos().asLong());
        parts(be.getBlockState(),c);
        x.pop();
    }
    /** The parts at rest, for the placement preview's ghost (its own faint buffer and tint). */
    static void drawRest(BlockState state,PoseStack pose,VertexConsumer consumer,float r,float g,float b){
        var x=new Xf(pose);x.push();parts(state,new Ctx(x,consumer,null,LightTexture.FULL_BRIGHT,r,g,b,false,0,0));x.pop();
    }
    static boolean animated(BlockState state){return model(state)!=null;}
    /** The source model whose parts this block draws, or null. */
    static String model(BlockState state){
        var block=state.getBlock();
        if(block==EternalDefense.FOB_BEACON.get())return "fob_station";
        if(block==EternalDefense.SATELLITE_BEACON.get())return "satellite_beacon";
        if(block==EternalDefense.COMMAND_TABLE.get())return "command_table";
        if(block==EternalDefense.ENCHANTING_TABLE.get())return "enchanting_platform";
        if(block==EternalDefense.BEACON.get())return "defense_beacon_level"+state.getValue(DefenseStructures.MK);
        if(block==EternalDefense.SUPPORT_PLATFORM.get())return "support_platform_mk"+state.getValue(SupportPlatform.MK);
        if(block==EternalDefense.MESS_HALL_I.get())return "mess_hall_mk1";
        if(block==EternalDefense.COOK_POT.get())return "cook_pot";
        if(block==EternalDefense.BREW_POT.get())return "drink_pot";
        if(block==EternalDefense.MRE_STATION.get())return "mre_maker";
        return null;
    }
    private static void parts(BlockState state,Ctx c){
        String model=model(state);if(model==null)return;
        if(state.hasProperty(BlockStateProperties.HORIZONTAL_FACING))c.x().about(8,0,8,Axis.YP,-DefenseStructures.angle(state.getValue(BlockStateProperties.HORIZONTAL_FACING)));
        switch(model){
            case "fob_station"->fob(c);
            case "satellite_beacon"->satellite(c);
            case "command_table"->table(c);
            case "enchanting_platform"->enchanting(c);
            case "mess_hall_mk1"->messHall(c);
            case "cook_pot"->{flames(c,"cook_pot","flames",8,2.5,8);}
            case "drink_pot"->{flames(c,"drink_pot","flames",8,2.5,8);c.x().push();c.x().about(8,0,8,Axis.YP,c.live()?AnimationRules.stir(c.t(),c.seed()):0);part(c,model,"paddle");c.x().pop();}
            case "mre_maker"->mre(c);
            default->{
                if(model.startsWith("defense_beacon_level"))defenseBeacon(c,model);
                else if(model.startsWith("support_platform_mk"))platform(c,model);
            }
        }
    }

    // The blocks
    /** The FOB flag's hinges (model pixels, x and z): the mast, then the far edge of each strip */
    static final double[][] FLAG_HINGES={{8.5,11.75},{10.347759,10.984633},{12.195518,10.219266},{14.043277,10.984633}};
    static void fob(Ctx c){
        var x=c.x();double[] f=c.live()?AnimationRules.flag(c.t(),c.seed()):new double[AnimationRules.FLAG_STRIPS];
        x.push();
        for(int k=0;k<AnimationRules.FLAG_STRIPS;k++){x.about(FLAG_HINGES[k][0],27.75,FLAG_HINGES[k][1],Axis.YP,f[k]);part(c,"fob_station","flag_"+k);}
        x.pop();
        whip(c,"fob_station","whip",4.25,14.25,13,7.25);
        x.push();x.about(8,0,7,Axis.YP,c.live()?AnimationRules.spin(c.t(),c.seed(),9):0);part(c,"fob_station","emitter_ring");x.pop();
    }
    /** The dish turns on its turntable (about the mast, x 8 z 10) and tilts on its axle (y 21.5, z 9.5). */
    static void satellite(Ctx c){
        var x=c.x();double[] d=c.live()?AnimationRules.dish(c.t(),c.seed()):new double[2];
        x.push();x.about(8,0,10,Axis.YP,d[0]);part(c,"satellite_beacon","azimuth");
        x.about(0,21.5,9.5,Axis.XP,d[1]);part(c,"satellite_beacon","dish");x.pop();
        whip(c,"satellite_beacon","whip",3.5,9.5,12.25,12);
    }
    /** The Command Table: its antenna sways, the warning light on the canopy turns and flashes */
    static void table(Ctx c){
        var x=c.x();whip(c,"command_table","antenna",1.75,19.25,26.25,10.5);
        boolean on=c.live()&&AnimationRules.flash(c.t(),c.seed(),1.2,.4);
        x.push();x.about(29.25,0,29.25,Axis.YP,c.live()?AnimationRules.spin(c.t(),c.seed(),1.2):0);quads(c,part("command_table","beacon"),on?c.glow():c.light());x.pop();
        if(c.live()&&c.buffer()!=null)radar(c,AnimationRules.spin(c.t(),c.seed(),3));
    }
    /** A green sweep and its fading trail on the radar screen */
    static void radar(Ctx c,double angle){
        var vc=c.buffer().getBuffer(RenderType.lines());var last=c.x().draw.last();var m=last.pose();var n=last.normal();
        float cx=27f/16,cy=21f/16,z=28.2f/16,r=2.4f/16;
        float[] alpha={1f,.55f,.3f,.15f};
        for(int i=0;i<alpha.length;i++){
            double a=Math.toRadians(angle-i*9);float dx=(float)(Math.cos(a)*r),dy=(float)(Math.sin(a)*r);
            vc.vertex(m,cx,cy,z).color(.35f,1f,.45f,alpha[i]).normal(n,dx,dy,0).endVertex();
            vc.vertex(m,cx+dx,cy+dy,z).color(.35f,1f,.45f,alpha[i]).normal(n,dx,dy,0).endVertex();
        }
    }
    /** The beacon's rings rise and fall in a wave up the core and index round a quarter at a time */
    static void defenseBeacon(Ctx c,String model){
        var x=c.x();var parts=PARTS.get(model);
        for(int i=0;i<3;i++){
            if(!parts.contains("ring_"+i))continue;
            x.push();if(c.live())x.px(0,AnimationRules.ringRise(c.t(),c.seed(),i),0);
            x.about(8,0,8,Axis.YP,c.live()?AnimationRules.index(c.t(),c.seed()+i,6+i*1.3,1.1,i%2==0?1:-1):0);part(c,model,"ring_"+i);x.pop();
        }
        if(parts.contains("emitter_ring")){x.push();x.about(8,0,8,Axis.YP,c.live()?AnimationRules.spin(c.t(),c.seed(),-14):0);part(c,model,"emitter_ring");x.pop();}
    }
    /** The support platform's emitter turns and floats a little. */
    static void platform(Ctx c,String model){
        var x=c.x();x.push();
        if(c.live()){x.px(0,AnimationRules.bob(c.t(),c.seed(),2.6,.35,1),0);x.about(8,0,8,Axis.YP,AnimationRules.spin(c.t(),c.seed(),5));}
        glowing(c,model,"emitter");x.pop();
    }
    /** The spark elements' centres (model pixels): the nine glyph sparks over the book, then the three on the rifle. */
    static final double[][] SPARKS={{11.05,23.3,11.8},{12.55,22.55,10.55},{13.8,21.55,9.55},{21.3,23.55,11.8},{20.05,22.55,10.8},{18.8,21.8,9.8},{15.55,22.05,6.8},{17.05,23.05,8.05},{16.05,24.3,6.3}},
        RIFLE_SPARKS={{24.75,13.75,3.75},{27.25,14.5,4.5},{29.5,13.25,3.5}};
    /** The book floats over its levitator and turns a little, its covers breathe; the ring spins */
    static void enchanting(Ctx c){
        var x=c.x();double[] b=c.live()?AnimationRules.book(c.t(),c.seed()):new double[3];
        x.push();x.px(0,b[0],0);x.about(16,0,7,Axis.YP,b[1]);
        x.push();x.about(16,18.5,7,Axis.ZP,b[2]);part(c,"enchanting_platform","book_l");x.pop();
        x.push();x.about(16,18.5,7,Axis.ZP,-b[2]);part(c,"enchanting_platform","book_r");x.pop();
        x.pop();
        x.push();x.about(16,0,7,Axis.YP,c.live()?AnimationRules.spin(c.t(),c.seed(),4):0);part(c,"enchanting_platform","levitator_ring");x.pop();
        for(int i=0;i<SPARKS.length;i++)spark(c,"spark_"+i,SPARKS[i],i);
        for(int i=0;i<RIFLE_SPARKS.length;i++)spark(c,"rifle_spark_"+i,RIFLE_SPARKS[i],10+i);
    }
    static void spark(Ctx c,String part,double[] at,int i){
        var x=c.x();double[] s=c.live()?AnimationRules.spark(c.t(),c.seed(),i):new double[]{0,1};
        x.push();x.px(0,s[0],0);x.scaleAbout(at[0],at[1],at[2],s[1],s[1],s[1]);glowing(c,"enchanting_platform",part);x.pop();
    }
    /** The Mess Hall Mk I's roast turns on its spit (about the rod: y 12.25, z 7) over a flickering fire. */
    static void messHall(Ctx c){
        var x=c.x();x.push();x.about(0,12.25,7,Axis.XP,c.live()?AnimationRules.spin(c.t(),c.seed(),7):0);part(c,"mess_hall_mk1","spit");x.pop();
        flames(c,"mess_hall_mk1","flame",26,1,7);
    }
    /** Flames grow and shrink from their base, lit by themselves. */
    static void flames(Ctx c,String model,String part,double bx,double by,double bz){
        var x=c.x();double[] f=c.live()?AnimationRules.flicker(c.t(),c.seed()):new double[]{1,1};
        x.push();x.scaleAbout(bx,by,bz,f[1],f[0],f[1]);glowing(c,model,part);x.pop();
    }
    /** The MRE Station: the heat press comes down on the pouches and lifts, the lever is pulled */
    static void mre(Ctx c){
        var x=c.x();double f=c.live()?AnimationRules.press(c.t(),c.seed()):0;
        x.push();x.px(0,-AnimationRules.PRESS_TRAVEL*f,0);part(c,"mre_maker","press");x.pop();
        x.push();x.about(0,12.5,8,Axis.XP,-35*f);part(c,"mre_maker","lever");x.pop();
        double roll=c.live()?AnimationRules.spin(c.t(),c.seed(),1.6):0;
        x.push();x.about(0,10.5,4.375,Axis.XP,roll);part(c,"mre_maker","roller_0");x.pop();
        x.push();x.about(0,10.5,11.125,Axis.XP,roll);part(c,"mre_maker","roller_1");x.pop();
    }
    /** A whip antenna swaying about its foot. */
    static void whip(Ctx c,String model,String part,double x0,double y0,double z0,double height){
        var x=c.x();double[] s=c.live()?AnimationRules.sway(c.t(),c.seed(),height):new double[2];
        x.push();x.about(x0,y0,z0,Axis.XP,s[0]);x.about(x0,y0,z0,Axis.ZP,s[1]);part(c,model,part);x.pop();
    }
    /** The cannon's crane hook, drawn inside the cannon renderer's turret transform */
    static void cannonHook(BlockEntity be,float partial,PoseStack pose,PoseStack own,MultiBufferSource buffer,int light){
        var level=be.getLevel();boolean live=enabled()&&level!=null;double t=live?(level.getGameTime()+(double)partial)/20.0:0;
        var x=new Xf(pose,own);var c=new Ctx(x,buffer.getBuffer(RenderType.cutout()),buffer,light,1f,1f,1f,live,t,be.getBlockPos().asLong());
        double[] h=live?AnimationRules.hook(t,c.seed()):new double[2];
        x.push();x.about(7.25,30.5,20,Axis.XP,h[0]);x.about(7.25,30.5,20,Axis.ZP,h[1]);part(c,"support_cannon_turret","hook");x.pop();
    }
}
