package dev.eternaldefense.beacon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.joml.Matrix4f;
import java.util.ArrayList;
import java.util.List;

/** The defense kit drawn: wire spans, pole reach, turrets that aim, recoil and show a status light, and the turret and Defense Depot screens */
@Mod.EventBusSubscriber(modid=EternalDefense.ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class PowerClient {
    private PowerClient(){}

    @SubscribeEvent public static void models(ModelEvent.RegisterAdditional e){for(var id:EternalDefense.TURRETS.keySet()){e.register(part(id,"head"));e.register(part(id,"gun"));}}
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e){
        e.registerBlockEntityRenderer(EternalDefense.POLE_ENTITY.get(),PoleRenderer::new);
        e.registerBlockEntityRenderer(EternalDefense.SHOCK_BOX_ENTITY.get(),BoxRenderer::new);
        e.registerBlockEntityRenderer(EternalDefense.TURRET_ENTITY.get(),TurretRenderer::new);
    }
    @SubscribeEvent public static void screens(FMLClientSetupEvent e){e.enqueueWork(()->{MenuScreens.register(EternalDefense.DEPOT_MENU.get(),DepotScreen::new);MenuScreens.register(EternalDefense.TURRET_MENU.get(),TurretScreen::new);});}
    static ResourceLocation part(String id,String part){return new ResourceLocation(EternalDefense.ID,"block/"+id+"_"+part);}

    // Wire
    /** Where wires meet each block (model pixels, the model facing north) */
    static final float[] POLE_IN={2,32,8},POLE_OUT={14,32,8},SHOCK_IN={4,15.2f,8},SHOCK_OUT={12,15.2f,8},WIRE_CLIP={8,16.15f,8};
    static final int SEGMENTS=24;
    static final float HALF_WIDTH=.022f;
    /** A lead point turned with the block's facing, in blocks from its corner */
    static Vec3 lead(float[] px,Direction facing){
        double x=px[0]/16,y=px[1]/16,z=px[2]/16;
        return switch(facing){case EAST->new Vec3(1-z,y,x);case SOUTH->new Vec3(1-x,y,1-z);case WEST->new Vec3(z,y,1-x);default->new Vec3(x,y,z);};
    }
    /** Pure: how far a span hangs at its middle, in blocks */
    static float sag(Vec3 a,Vec3 b){double dx=b.x-a.x,dz=b.z-a.z;return Math.min(.6f,.06f+.02f*(float)Math.sqrt(dx*dx+dz*dz));}
    /** A sagging cable like a lead: two crossed ribbons; a live one pulses cyan at full brightness */
    static void wire(PoseStack pose,MultiBufferSource buffers,Vec3 a,Vec3 b,int lightA,int lightB,boolean live,float time){
        VertexConsumer vc=buffers.getBuffer(RenderType.leash());Matrix4f m=pose.last().pose();
        float dx=(float)(b.x-a.x),dy=(float)(b.y-a.y),dz=(float)(b.z-a.z),horiz=Mth.sqrt(dx*dx+dz*dz),hx=HALF_WIDTH,hz=0,s=sag(a,b);
        if(horiz>1e-4f){hx=-dz/horiz*HALF_WIDTH;hz=dx/horiz*HALF_WIDTH;}
        for(int i=0;i<=SEGMENTS;i++)pair(vc,m,a,dx,dy,dz,s,i,hx,0,hz,lightA,lightB,live,time,false);
        for(int i=SEGMENTS;i>=0;i--)pair(vc,m,a,dx,dy,dz,s,i,0,HALF_WIDTH,0,lightA,lightB,live,time,true);
    }
    private static void pair(VertexConsumer vc,Matrix4f m,Vec3 a,float dx,float dy,float dz,float sag,int i,float ox,float oy,float oz,int lightA,int lightB,boolean live,float time,boolean second){
        float t=i/(float)SEGMENTS,x=(float)a.x+dx*t,y=(float)a.y+dy*t-sag*4*t*(1-t),z=(float)a.z+dz*t;
        int light=LightTexture.pack((int)Mth.lerp(t,LightTexture.block(lightA),LightTexture.block(lightB)),(int)Mth.lerp(t,LightTexture.sky(lightA),LightTexture.sky(lightB)));
        float shade=i%2==(second?1:0)?.72f:1,r=.11f,g=.12f,bl=.13f;
        if(live){float k=.45f+.35f*Mth.sin(time*.55f+i*1.3f);r=Mth.lerp(k,r,.48f);g=Mth.lerp(k,g,.94f);bl=Mth.lerp(k,bl,1);light=LightTexture.FULL_BRIGHT;}
        vc.vertex(m,x-ox,y-oy,z-oz).color(r*shade,g*shade,bl*shade,1).uv2(light).endVertex();
        vc.vertex(m,x+ox,y+oy,z+oz).color(r*shade,g*shade,bl*shade,1).uv2(light).endVertex();
    }
    /** The far end of a span: a pole's IN insulator, a shock box's IN, or a barbed wire post's clip */
    static Vec3 farEnd(net.minecraft.world.level.Level level,BlockPos from,BlockPos to){
        var s=level.getBlockState(to);var offset=Vec3.atLowerCornerOf(to.subtract(from));
        if(s.getBlock() instanceof PowerPoles.PoleBlock)return offset.add(lead(POLE_IN,s.getValue(DefenseStructures.FACING)));
        if(s.getBlock() instanceof ShockBoxes.BoxBlock)return offset.add(lead(SHOCK_IN,s.getValue(DefenseStructures.FACING)));
        return offset.add(lead(WIRE_CLIP,Direction.NORTH));
    }

    static final class PoleRenderer implements BlockEntityRenderer<PowerPoles.PoleEntity> {
        PoleRenderer(BlockEntityRendererProvider.Context c){}
        @Override public void render(PowerPoles.PoleEntity be,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
            var level=be.getLevel();if(level==null||be.links.isEmpty())return;
            var from=lead(POLE_OUT,be.getBlockState().getValue(DefenseStructures.FACING));int top=LevelRenderer.getLightColor(level,be.getBlockPos().above());
            for(var t:be.links)if(level.isLoaded(t))wire(pose,buffers,from,farEnd(level,be.getBlockPos(),t),top,LevelRenderer.getLightColor(level,t.above()),false,0);
        }
        @Override public boolean shouldRenderOffScreen(PowerPoles.PoleEntity be){return true;}
        @Override public int getViewDistance(){return 96;}
    }
    static final class BoxRenderer implements BlockEntityRenderer<ShockBoxes.BoxEntity> {
        BoxRenderer(BlockEntityRendererProvider.Context c){}
        @Override public void render(ShockBoxes.BoxEntity be,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
            var level=be.getLevel();if(level==null||be.links.isEmpty())return;
            var state=be.getBlockState();var from=lead(SHOCK_OUT,state.getValue(DefenseStructures.FACING));boolean live=state.getValue(BlockStateProperties.POWERED);
            float time=level.getGameTime()+partial;
            for(var t:be.links)if(level.isLoaded(t))wire(pose,buffers,from,farEnd(level,be.getBlockPos(),t),light,LevelRenderer.getLightColor(level,t),live,time);
        }
        @Override public boolean shouldRenderOffScreen(ShockBoxes.BoxEntity be){return true;}
        @Override public int getViewDistance(){return 96;}
    }

    // Turrets
    /** Pivots (model pixels): the head turns about (x, z), the gun lifts about (y, z) */
    static float[] pivots(int mk){return mk>=3?new float[]{16,16,20,15}:new float[]{8,8,19,8.75f};}
    static final float TURN=.25f,RECOIL_TICKS=3,RECOIL=.07f,LAMP_Y=2.05f,LAMP_R=.075f;
    /** Status light colours: green ready, orange out of ammo, red without power */
    static final java.util.Map<String,Integer> LIGHT=java.util.Map.of("ready",0x6fe08a,"empty",0xffa36c,"off",0xed4a42);
    static int argb(String state){return 0xff000000|LIGHT.get(state);}
    static final class TurretRenderer implements BlockEntityRenderer<Turrets.TurretEntity> {
        TurretRenderer(BlockEntityRendererProvider.Context c){}
        @Override public void render(Turrets.TurretEntity be,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
            var level=be.getLevel();if(level==null||!(be.getBlockState().getBlock() instanceof Turrets.TurretBlock block))return;
            var id=Turrets.id(block.line,block.mk);float[] p=pivots(block.mk);float now=level.getGameTime()+partial;
            aim(be,level,now,p);
            pose.pushPose();
            pose.translate(p[0]/16,0,p[1]/16);pose.mulPose(Axis.YP.rotation(be.drawYaw));pose.translate(-p[0]/16,0,-p[1]/16);
            SupportClient.draw(pose,buffers,RenderType.cutout(),part(id,"head"),light,overlay);
            pose.translate(0,p[2]/16,p[3]/16);pose.mulPose(Axis.XP.rotationDegrees(be.drawPitch));pose.translate(0,-p[2]/16,-p[3]/16);
            if(block.line!=Turrets.Line.FLAME){float kick=Math.max(0,1-(now-be.shotAt)/RECOIL_TICKS);pose.translate(0,0,kick*RECOIL);}
            SupportClient.draw(pose,buffers,RenderType.cutout(),part(id,"gun"),light,overlay);
            pose.popPose();
            lamp(pose,buffers,block.mk,be.light(),now);
        }
        /** A small diamond lamp above the gun: steady when ready, pulsing when out of ammo or without power */
        static void lamp(PoseStack pose,MultiBufferSource buffers,int mk,String state,float now){
            int rgb=LIGHT.get(state);float pulse=state.equals("ready")?1:.55f+.45f*Mth.sin(now*.2f),c=mk>=3?1:.5f,y=LAMP_Y+.03f*Mth.sin(now*.08f),r=LAMP_R,h=LAMP_R*1.4f;
            float red=(rgb>>16&255)/255f*pulse,green=(rgb>>8&255)/255f*pulse,blue=(rgb&255)/255f*pulse;
            var vc=buffers.getBuffer(RenderType.debugQuads());var m=pose.last().pose();
            float[][] ring={{r,0},{0,r},{-r,0},{0,-r}};
            for(int i=0;i<4;i++){
                float[] a=ring[i],b=ring[(i+1)%4];
                for(int up=0;up<2;up++){float tip=up==0?h:-h,shade=(up==0?1:.7f)*(i%2==0?1:.85f);
                    vc.vertex(m,c,y+tip,c).color(red*shade,green*shade,blue*shade,.95f).endVertex();
                    vc.vertex(m,c+a[0],y,c+a[1]).color(red*shade,green*shade,blue*shade,.95f).endVertex();
                    vc.vertex(m,c+b[0],y,c+b[1]).color(red*shade,green*shade,blue*shade,.95f).endVertex();
                    vc.vertex(m,c,y+tip,c).color(red*shade,green*shade,blue*shade,.95f).endVertex();}
            }
        }
        /** Swings toward the target the server named; an unpowered gun droops */
        private static void aim(Turrets.TurretEntity be,net.minecraft.world.level.Level level,float now,float[] p){
            float step=Mth.clamp(now-be.drawnAt,0,10);be.drawnAt=now;
            float yaw=be.drawYaw,pitch=be.powered?0:-12;
            if(be.powered&&be.targetId>=0&&level.getEntity(be.targetId) instanceof net.minecraft.world.entity.LivingEntity target){
                var from=Vec3.atLowerCornerOf(be.getBlockPos()).add(p[0]/16,p[2]/16,p[1]/16);var at=target.position().add(0,target.getBbHeight()*.55,0).subtract(from);
                yaw=Turrets.yaw(at.x,at.z);pitch=Turrets.clampPitch((float)Math.toDegrees(Turrets.pitch(at.y,Math.sqrt(at.x*at.x+at.z*at.z))));
            }
            float k=1-(float)Math.pow(1-TURN,step);
            be.drawYaw+=Mth.wrapDegrees((float)Math.toDegrees(yaw-be.drawYaw))*(float)Math.PI/180*k;
            be.drawPitch+=(pitch-be.drawPitch)*k;
        }
        @Override public boolean shouldRenderOffScreen(Turrets.TurretEntity be){return false;}
    }

    /** A pole's reach drawn as see-through walls and an outline: for every pole while defense gear is in hand, for a pole set to show it, and where a pole in hand would stand */
    @Mod.EventBusSubscriber(modid=EternalDefense.ID,value=Dist.CLIENT)
    public static final class Reach {
        private Reach(){}
        private static final MultiBufferSource.BufferSource BUFFER=MultiBufferSource.immediate(new com.mojang.blaze3d.vertex.BufferBuilder(1<<16));
        static final int[] LIVE={110,220,240},DOWN={255,150,60},PREVIEW={235,235,235};
        static final double RANGE=64;
        /** Wall alpha at the floor, fading to a third at the top and lowest layers; and how far a wall sits inside the edge */
        static final int WALL_ALPHA=96;
        static final double WALL_INSET=.01;
        /** See-through quads, both sides, that hide nothing drawn after them */
        static final class Walls extends RenderType {
            private Walls(){super("",com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR,com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,0,false,false,()->{},()->{});}
            static final RenderType TYPE=create(EternalDefense.ID+"_reach_wall",com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR,com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,1<<16,false,true,
                CompositeState.builder().setShaderState(POSITION_COLOR_SHADER).setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE).createCompositeState(false));
        }
        /** Defense gear in either hand */
        static boolean holding(net.minecraft.world.entity.player.Player p){for(var s:new ItemStack[]{p.getMainHandItem(),p.getOffhandItem()})if(s.getItem() instanceof PowerKit.KitItem||s.getItem() instanceof PowerPoles.WireItem)return true;return false;}
        /** Where a Power Pole in hand would stand */
        static BlockPos target(Minecraft mc){
            if(mc.player==null||!(mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit)||hit.getType()!=net.minecraft.world.phys.HitResult.Type.BLOCK)return null;
            for(var hand:net.minecraft.world.InteractionHand.values()){var s=mc.player.getItemInHand(hand);if(s.is(EternalDefense.POWER_POLE.get().asItem()))return new net.minecraft.world.item.context.BlockPlaceContext(mc.player,hand,s,hit).getClickedPos();}
            return null;
        }
        @SubscribeEvent public static void render(net.minecraftforge.client.event.RenderLevelStageEvent e){
            if(e.getStage()!=net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)return;
            var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return;
            boolean holding=holding(mc.player);var camera=e.getCamera().getPosition();
            var shown=new ArrayList<PowerPoles.PoleEntity>();
            for(var it=PowerPoles.SEEN.iterator();it.hasNext();){
                var p=it.next();if(!(mc.level.getBlockEntity(p) instanceof PowerPoles.PoleEntity pole)){it.remove();continue;}
                if((holding||pole.showReach)&&p.distToCenterSqr(camera)<RANGE*RANGE)shown.add(pole);
            }
            var preview=target(mc);
            if(shown.isEmpty()&&preview==null)return;
            var pose=e.getPoseStack();pose.pushPose();pose.translate(-camera.x,-camera.y,-camera.z);
            VertexConsumer walls=BUFFER.getBuffer(Walls.TYPE);
            for(var pole:shown)wall(walls,pose.last().pose(),pole.getBlockPos(),ground(mc.level,pole.getBlockPos()),pole.live?LIVE:DOWN,pole.showReach?1f:.75f);
            if(preview!=null)wall(walls,pose.last().pose(),preview,ground(mc.level,preview),PREVIEW,.85f);
            BUFFER.endBatch(Walls.TYPE);
            VertexConsumer lines=BUFFER.getBuffer(RenderType.lines());
            for(var pole:shown)outline(lines,pose.last(),pole.getBlockPos(),ground(mc.level,pole.getBlockPos()),pole.live?LIVE:DOWN,pole.showReach?220:170);
            if(preview!=null)outline(lines,pose.last(),preview,ground(mc.level,preview),PREVIEW,200);
            BUFFER.endBatch(RenderType.lines());
            pose.popPose();
        }
        /** The floor a pole stands on: under its dynamo, or under the pole itself */
        static int ground(net.minecraft.world.level.Level level,BlockPos pole){return level.getBlockEntity(pole.below()) instanceof PowerKit.Dynamo?pole.getY()-1:pole.getY();}
        /** The reach's edge as a wall from its lowest to its highest layer, strongest at the floor */
        static void wall(VertexConsumer v,Matrix4f m,BlockPos pole,int floor,int[] rgb,float strength){
            float bottom=pole.getY()-PowerKit.AURA_DOWN,top=pole.getY()+PowerKit.AURA_UP+1;int strong=Math.round(WALL_ALPHA*strength),faint=strong/3;
            for(var edge:PowerPoles.REACH_EDGES){
                boolean alongX=edge[2]==1;double in=PowerPoles.inward(edge)*WALL_INSET;
                float x=(float)(pole.getX()+edge[0]+(alongX?0:in)),z=(float)(pole.getZ()+edge[1]+(alongX?in:0)),xx=alongX?x+1:x,zz=alongX?z:z+1;
                quad(v,m,x,z,xx,zz,bottom,faint,floor,strong,rgb);quad(v,m,x,z,xx,zz,floor,strong,top,faint,rgb);
            }
        }
        private static void quad(VertexConsumer v,Matrix4f m,float x,float z,float xx,float zz,float low,int lowAlpha,float high,int highAlpha,int[] rgb){
            if(high<=low)return;
            v.vertex(m,x,low,z).color(rgb[0],rgb[1],rgb[2],lowAlpha).endVertex();v.vertex(m,xx,low,zz).color(rgb[0],rgb[1],rgb[2],lowAlpha).endVertex();
            v.vertex(m,xx,high,zz).color(rgb[0],rgb[1],rgb[2],highAlpha).endVertex();v.vertex(m,x,high,z).color(rgb[0],rgb[1],rgb[2],highAlpha).endVertex();
        }
        /** The reach's edge on the floor (bright), at its lowest and highest layer (faint), joined by uprights */
        static void outline(VertexConsumer v,PoseStack.Pose p,BlockPos pole,int floor,int[] rgb,int alpha){
            double ground=floor+.02,bottom=pole.getY()-PowerKit.AURA_DOWN+.02,top=pole.getY()+PowerKit.AURA_UP+.98;
            for(var edge:PowerPoles.REACH_EDGES){
                double x=pole.getX()+edge[0],z=pole.getZ()+edge[1],xx=x+(edge[2]==1?1:0),zz=z+(edge[2]==1?0:1);
                line(v,p,x,ground,z,xx,ground,zz,rgb,alpha);line(v,p,x,bottom,z,xx,bottom,zz,rgb,alpha/3);line(v,p,x,top,z,xx,top,zz,rgb,alpha/3);
            }
            double cx=pole.getX()+.5,cz=pole.getZ()+.5,r=PowerKit.AURA+.5;
            for(int i=0;i<8;i++){double a=i*Math.PI/4,x=cx+Math.cos(a)*r,z=cz+Math.sin(a)*r;line(v,p,x,bottom,z,x,top,z,rgb,alpha/3);}
        }
        private static void line(VertexConsumer v,PoseStack.Pose p,double x,double y,double z,double xx,double yy,double zz,int[] rgb,int alpha){
            float dx=(float)(xx-x),dy=(float)(yy-y),dz=(float)(zz-z),length=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);if(length==0)return;
            v.vertex(p.pose(),(float)x,(float)y,(float)z).color(rgb[0],rgb[1],rgb[2],alpha).normal(p.normal(),dx/length,dy/length,dz/length).endVertex();
            v.vertex(p.pose(),(float)xx,(float)yy,(float)zz).color(rgb[0],rgb[1],rgb[2],alpha).normal(p.normal(),dx/length,dy/length,dz/length).endVertex();
        }
        @SubscribeEvent public static void loggedOut(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut e){PowerPoles.SEEN.clear();}
    }

    /** A turret's line under the crosshair: ready with its shots, out of ammo, or without power */
    @Mod.EventBusSubscriber(modid=EternalDefense.ID,value=Dist.CLIENT)
    public static final class TurretHud {
        private TurretHud(){}
        static Turrets.TurretEntity looked(Minecraft mc){
            if(mc.level==null||!(mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit)||hit.getType()!=net.minecraft.world.phys.HitResult.Type.BLOCK)return null;
            return mc.level.getBlockEntity(DefenseStructures.anchor(mc.level,hit.getBlockPos())) instanceof Turrets.TurretEntity be&&be.getBlockState().getBlock() instanceof Turrets.TurretBlock?be:null;
        }
        @SubscribeEvent public static void hud(net.minecraftforge.client.event.RenderGuiOverlayEvent.Post e){
            if(e.getOverlay()!=net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.CROSSHAIR.type())return;
            var mc=Minecraft.getInstance();if(mc.options.hideGui||mc.screen!=null||FobOutlineClient.line()!=null)return;
            var be=looked(mc);if(be==null)return;
            var text=be.status(be.loadedShown);var g=e.getGuiGraphics();var font=mc.font;int w=e.getWindow().getGuiScaledWidth(),h=e.getWindow().getGuiScaledHeight();
            int tw=Math.min(font.width(text)+9,w-20),x=(w-tw)/2,y=h/2+14;
            g.fill(x-4,y-3,x+tw+4,y+11,0xa0101820);g.fill(x,y+1,x+5,y+6,argb(be.light()));
            Ui.text(g,font,text,x+9,y,Ui.INK,tw-9);
        }
    }

    /** A turret's screen: whether it is online, its shots and numbers, and its four ammo slots loaded and emptied by hand */
    static final class TurretScreen extends AbstractContainerScreen<Turrets.TurretMenu> {
        static final int W=176,H=222,CARD_Y=32,CARD_H=60;
        TurretScreen(Turrets.TurretMenu menu,Inventory inv,Component title){super(menu,inv,title);imageWidth=W;imageHeight=H;}
        private String light(){return Turrets.lightOf(menu.powered.get()==1,menu.loaded.get());}
        private ItemStack ammo(){return new ItemStack(Turrets.ammo(menu.line));}
        @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
            var s=Turrets.stats(menu.line,menu.mk);var light=light();int accent=argb(light),loaded=menu.loaded.get(),x=leftPos+8,y=topPos+CARD_Y;
            Ui.panel(g,leftPos,topPos,W,H);Ui.header(g,font,leftPos,topPos,W,title,Ui.t("turret.screen.subtitle",menu.mk));
            Ui.card(g,x,y,160,CARD_H,accent);
            switch(light){case "ready"->Ui.check(g,x+8,y+6,accent);case "empty"->Ui.alert(g,x+9,y+5,accent);default->Ui.cross(g,x+8,y+6,accent);}
            Ui.text(g,font,Ui.t("turret.screen."+light),x+18,y+5,accent,138);
            Ui.text(g,font,Ui.t("turret.screen.loaded"),x+8,y+17,Ui.INK,90);
            Ui.right(g,font,Component.literal(String.format(java.util.Locale.ROOT,"%,d",loaded)),x+154,y+17,loaded>0?Ui.CYAN:Ui.ORANGE);
            Ui.bar(g,x+8,y+28,146,5,loaded/(float)(Turrets.SLOTS*64*Turrets.perItem(menu.line)),loaded>0?Ui.CYAN_DIM:Ui.ORANGE);
            Ui.text(g,font,Ui.t("turret.screen.range"),x+8,y+38,Ui.MUTED,80);Ui.right(g,font,Ui.t("turret.screen.blocks",s.range()),x+154,y+38,Ui.INK);
            Ui.text(g,font,Ui.t("turret.screen.stress"),x+8,y+48,Ui.MUTED,80);Ui.right(g,font,Ui.t("turret.screen.per_rpm",s.stress()),x+154,y+48,Ui.INK);
            Ui.text(g,font,Ui.t("turret.screen.ammo",ammo().getHoverName(),Turrets.perItem(menu.line)),leftPos+8,topPos+Turrets.TurretMenu.AMMO_Y-11,Ui.MUTED,160);
            for(int i=0;i<Turrets.SLOTS;i++){
                int sx=leftPos+Turrets.TurretMenu.AMMO_X+i*18,sy=topPos+Turrets.TurretMenu.AMMO_Y;Ui.slot(g,sx-1,sy-1,Ui.BRASS);
                if(menu.getSlot(i).getItem().isEmpty())Ui.ghost(g,ammo(),sx,sy);
            }
            Ui.text(g,font,playerInventoryTitle,leftPos+8,topPos+Turrets.TurretMenu.INV_Y-12,Ui.MUTED,160);
            Ui.inventory(g,leftPos+7,topPos+Turrets.TurretMenu.INV_Y-1);
        }
        @Override protected void renderLabels(GuiGraphics g,int mx,int my){}
        @Override public void render(GuiGraphics g,int mx,int my,float partial){
            renderBackground(g);super.render(g,mx,my,partial);renderTooltip(g,mx,my);
            if(hoveredSlot==null&&Ui.inside(mx,my,leftPos+8,topPos+CARD_Y,160,CARD_H))g.renderTooltip(font,font.split(Ui.t("turret.screen.tip",PowerKit.AURA),180),mx,my);
            else if(hoveredSlot instanceof net.minecraftforge.items.SlotItemHandler&&!hoveredSlot.hasItem())g.renderTooltip(font,font.split(Ui.t("turret.screen.slot_tip",ammo().getHoverName()),180),mx,my);
        }
    }

    /** The Defense Depot: what it sells, for how many coins, and a Buy button */
    static final class DepotScreen extends AbstractContainerScreen<DefenseDepot.DepotMenu> {
        static final int W=308,H=240,ROW=26,BAR=6,LIST_Y=38,VISIBLE=(H-30-14-LIST_Y)/ROW;
        private int selected,scroll,lastSerial=-1;private boolean dragging;private long shownAt;private Component feedback=Component.empty();
        private final List<Ui.UiButton> rows=new ArrayList<>();private Ui.UiButton buyButton;
        DepotScreen(DefenseDepot.DepotMenu menu,Inventory inv,Component title){super(menu,inv,title);imageWidth=W;imageHeight=H;}
        private static List<DefenseDepot.Product> products(){return DefenseDepot.PRODUCTS;}
        @Override protected void init(){
            super.init();clearWidgets();rows.clear();scroll=Math.min(scroll,maxScroll());
            addRenderableWidget(new Ui.UiButton(leftPos+W-30,topPos+4,22,18,Component.literal("X"),Ui.Look.NORMAL,b->onClose()));
            for(int i=0;i<Math.min(VISIBLE,products().size());i++){
                int slot=i;var row=new Ui.UiButton(leftPos+12,topPos+LIST_Y+i*ROW,rowWidth(),ROW-2,Component.empty(),Ui.Look.ROW,b->selected=scroll+slot);
                row.painter((g,self,hover)->paintProduct(g,self,scroll+slot)).noLabel();rows.add(addRenderableWidget(row));
            }
            buyButton=addRenderableWidget(new Ui.UiButton(leftPos+W-12-140,topPos+H-30,140,20,Ui.t("depot.buy"),Ui.Look.PRIMARY,b->buy()));
        }
        private void buy(){if(minecraft!=null&&minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,selected);}
        private int maxScroll(){return Math.max(0,products().size()-VISIBLE);}
        private int rowWidth(){return W-24-(maxScroll()>0?BAR+4:0);}
        private int barX(){return leftPos+W-12-BAR;}
        private int trackH(){return VISIBLE*ROW-2;}
        private int thumbH(){return Math.max(12,trackH()*VISIBLE/Math.max(VISIBLE,products().size()));}
        private void scrollTo(double my){int room=trackH()-thumbH();scroll=room<=0?0:Math.max(0,Math.min(maxScroll(),(int)Math.round((my-topPos-LIST_Y-thumbH()/2.0)*maxScroll()/room)));}
        private boolean affordable(DefenseDepot.Product p){return minecraft.player.isCreative()||menu.coins.get()>=p.price();}
        private ItemStack stack(DefenseDepot.Product p){return new ItemStack(p.item().get(),p.count());}
        private void paintProduct(GuiGraphics g,Ui.UiButton row,int index){
            var product=products().get(index);var stack=stack(product);boolean ok=affordable(product);
            g.renderItem(stack,row.getX()+6,row.getY()+4);g.renderItemDecorations(font,stack,row.getX()+6,row.getY()+4);
            var tip=net.minecraft.client.gui.screens.Screen.getTooltipFromItem(minecraft,stack);
            Ui.text(g,font,stack.getHoverName(),row.getX()+28,row.getY()+4,ok?Ui.INK:Ui.MUTED,row.getWidth()-100);
            if(tip.size()>1)Ui.text(g,font,tip.get(1),row.getX()+28,row.getY()+14,Ui.MUTED,row.getWidth()-100);
            g.renderItem(new ItemStack(EternalDefense.AMMO_COIN.get()),row.getX()+row.getWidth()-22,row.getY()+4);
            Ui.right(g,font,Ui.t("exchange.cost",product.price()),row.getX()+row.getWidth()-24,row.getY()+8,ok?Ui.CYAN:Ui.ORANGE);
        }
        @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
            Ui.panel(g,leftPos,topPos,W,H);
            Ui.header(g,font,leftPos,topPos,W,Ui.t("depot.title"),Ui.t("depot.subtitle"));
            g.renderItem(new ItemStack(EternalDefense.AMMO_COIN.get()),leftPos+14,topPos+H-30);
            Ui.text(g,font,Ui.t("depot.balance",menu.coins.get()),leftPos+34,topPos+H-26,Ui.CYAN,140);
        }
        @Override protected void renderLabels(GuiGraphics g,int mx,int my){}
        @Override public void render(GuiGraphics g,int mx,int my,float partial){
            int serial=menu.serial.get();
            if(lastSerial<0)lastSerial=serial;
            if(serial!=lastSerial){lastSerial=serial;shownAt=net.minecraft.Util.getMillis();feedback=DefenseDepot.result(menu.result.get());}
            for(int i=0;i<rows.size();i++){rows.get(i).visible=scroll+i<products().size();rows.get(i).selected=scroll+i==selected;}
            var product=products().get(Math.min(selected,products().size()-1));
            buyButton.active=affordable(product);buyButton.warning=!buyButton.active;
            super.render(g,mx,my,partial);
            if(maxScroll()>0){
                int x=barX(),y=topPos+LIST_Y,ty=y+(trackH()-thumbH())*scroll/maxScroll();
                g.fill(x,y,x+BAR,y+trackH(),Ui.TRACK);g.fill(x+1,ty,x+BAR-1,ty+thumbH(),dragging||Ui.inside(mx,my,x-1,y,BAR+2,trackH())?Ui.CYAN:Ui.MUTED);
                Ui.text(g,font,Ui.t("depot.scroll",scroll+1,Math.min(products().size(),scroll+VISIBLE),products().size()),leftPos+14,topPos+LIST_Y+VISIBLE*ROW+2,Ui.MUTED,W-28);
            }
            if(!feedback.getString().isEmpty()&&net.minecraft.Util.getMillis()-shownAt<4000)Ui.text(g,font,feedback,leftPos+14,topPos+H-12,menu.result.get()==DefenseDepot.BOUGHT?Ui.CYAN:Ui.ORANGE,W-28);
            if(!dragging)for(int i=0;i<rows.size();i++){var row=rows.get(i);if(row.visible&&row.isHovered()){g.renderTooltip(font,net.minecraft.client.gui.screens.Screen.getTooltipFromItem(minecraft,stack(products().get(scroll+i))),java.util.Optional.empty(),mx,my);break;}}
        }
        @Override public boolean mouseClicked(double mx,double my,int button){
            if(button==0&&maxScroll()>0&&Ui.inside(mx,my,barX()-1,topPos+LIST_Y,BAR+2,trackH())){dragging=true;scrollTo(my);return true;}
            return super.mouseClicked(mx,my,button);
        }
        @Override public boolean mouseScrolled(double mx,double my,double amount){
            if(maxScroll()>0&&Ui.inside(mx,my,leftPos+12,topPos+LIST_Y,W-24,VISIBLE*ROW)){scroll=Math.max(0,Math.min(maxScroll(),scroll-(int)Math.signum(amount)));return true;}
            return super.mouseScrolled(mx,my,amount);
        }
        @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy){if(dragging){scrollTo(my);return true;}return super.mouseDragged(mx,my,button,dx,dy);}
        @Override public boolean mouseReleased(double mx,double my,int button){if(dragging){dragging=false;return true;}return super.mouseReleased(mx,my,button);}
    }
}
