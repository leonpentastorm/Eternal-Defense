package dev.eternaldefense.beacon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Client side of FobOutline: each FOB zone near the player as an olive outline of its village sections */
@Mod.EventBusSubscriber(modid=EternalDefense.ID,value=Dist.CLIENT)
public final class FobOutlineClient {
    private FobOutlineClient(){}
    record Zone(long id,String name,String state,Set<Long> sections,int ground){}
    static List<Zone> zones=List.of();static CompoundTag preview;static Set<Long> previewSections=Set.of();
    private static final MultiBufferSource.BufferSource BUFFER=MultiBufferSource.immediate(new com.mojang.blaze3d.vertex.BufferBuilder(1<<18));
    /** Olive (a FOB's colour on its screen), green for a good spot, orange for a refused one. */
    static final int[] OLIVE={156,194,106},GOOD={110,230,120},BAD={255,150,60};

    static void receive(CompoundTag data){
        var list=new ArrayList<Zone>();
        for(var t:data.getList("zones",Tag.TAG_COMPOUND)){var z=(CompoundTag)t;list.add(new Zone(z.getLong("id"),z.getString("name"),z.getString("state"),set(z.getLongArray("sections")),z.getInt("ground")));}
        zones=List.copyOf(list);
        preview=data.contains("preview")?data.getCompound("preview"):null;
        previewSections=preview==null?Set.of():set(preview.getLongArray("sections"));
    }
    private static Set<Long> set(long[] a){var s=new HashSet<Long>();for(long v:a)s.add(v);return s;}
    @SubscribeEvent public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut e){zones=List.of();preview=null;previewSections=Set.of();}

    static boolean overworld(){var mc=Minecraft.getInstance();return mc.level!=null&&mc.level.dimension()==Level.OVERWORLD;}
    static boolean holding(){var mc=Minecraft.getInstance();return mc.player!=null&&FobOutline.holds(mc.player);}
    /** Where a FOB Beacon used now would stand, from this client's own aim (the ghost follows the crosshair at once). */
    static BlockPos target(){
        var mc=Minecraft.getInstance();
        if(mc.player==null||!(mc.hitResult instanceof BlockHitResult hit)||hit.getType()!=HitResult.Type.BLOCK)return null;
        var hand=mc.player.getMainHandItem().is(EternalDefense.FOB_BEACON_ITEM.get())?net.minecraft.world.InteractionHand.MAIN_HAND:net.minecraft.world.InteractionHand.OFF_HAND;
        return new BlockPlaceContext(mc.player,hand,mc.player.getItemInHand(hand),hit).getClickedPos();
    }
    static boolean previewGood(){return preview!=null&&preview.getString("problem").isEmpty();}
    /** Is something in the way of a FOB Beacon placed at at (its two cells, as the item checks them)? */
    static boolean blocked(BlockPos at){
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||at==null)return false;
        var state=EternalDefense.FOB_BEACON.get().defaultBlockState().setValue(FobBlocks.FACING,mc.player.getDirection().getOpposite());
        return PlacementGuard.blocked(mc.level,at,state,mc.player);
    }
    /** Does this client already know the FOB Beacon cannot stand */
    static boolean refused(BlockPos at){return blocked(at)||preview!=null&&preview.getLong("pos")==at.asLong()&&!preview.getString("problem").isEmpty();}
    /** The ghost's colour: red with something in the way, else olive (no verdict yet), green or orange. */
    static int[] colour(BlockPos at){return blocked(at)?PlacementGuard.RED:preview==null?OLIVE:previewGood()?GOOD:BAD;}

    // The world: outlines and the ghost FOB
    @SubscribeEvent public static void render(RenderLevelStageEvent e){
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS||!overworld())return;
        var mc=Minecraft.getInstance();boolean holding=holding();
        if(zones.isEmpty()&&!holding)return;
        var camera=e.getCamera().getPosition();var pose=e.getPoseStack();
        pose.pushPose();pose.translate(-camera.x,-camera.y,-camera.z);
        double time=mc.level.getGameTime()+e.getPartialTick();
        VertexConsumer lines=BUFFER.getBuffer(RenderType.lines());
        for(var z:zones)outline(lines,pose.last(),z.sections(),OLIVE,time,holding?110:150,z.ground());
        BlockPos at=holding?target():null;
        if(holding&&preview!=null&&!previewSections.isEmpty())outline(lines,pose.last(),previewSections,previewGood()?GOOD:BAD,time,210,at!=null?at.getY():(int)Math.floor(mc.player.getY()));
        if(at!=null){
            int[] c=colour(at);
            LevelRenderer.renderLineBox(pose,lines,new AABB(at.getX(),at.getY(),at.getZ(),at.getX()+1,at.getY()+FobBlocks.TALL,at.getZ()+1).inflate(.002),c[0]/255f,c[1]/255f,c[2]/255f,.9f);
        }
        BUFFER.endBatch(RenderType.lines());
        if(at!=null)ghost(pose,at,mc);
        pose.popPose();
    }
    /** The outer walls of a set of 16-block sections: every side face with no zone section beyond it, its edges */
    static void outline(VertexConsumer v,PoseStack.Pose p,Set<Long> sections,int[] rgb,double time,int alpha,int ground){
        if(sections.isEmpty())return;
        int lowY=Integer.MAX_VALUE,highY=Integer.MIN_VALUE;for(long s:sections){lowY=Math.min(lowY,SectionPos.y(s));highY=Math.max(highY,SectionPos.y(s));}
        double bottom=lowY*16,top=(highY+1)*16,scan=bottom+(time*.12)%(top-bottom),shift=(time*.025)%4;
        for(long s:sections){
            int sx=SectionPos.x(s),sy=SectionPos.y(s),sz=SectionPos.z(s);double x0=sx*16,y0=sy*16,z0=sz*16,x1=x0+16,y1=y0+16,z1=z0+16;
            boolean[] open={!sections.contains(SectionPos.asLong(sx,sy,sz-1)),!sections.contains(SectionPos.asLong(sx+1,sy,sz)),!sections.contains(SectionPos.asLong(sx,sy,sz+1)),!sections.contains(SectionPos.asLong(sx-1,sy,sz))};
            // North, east, south, west walls: (from x, from z) to (to x, to z)
            double[][] walls={{x0,z0,x1,z0},{x1,z0,x1,z1},{x1,z1,x0,z1},{x0,z1,x0,z0}};
            for(int i=0;i<4;i++){if(!open[i])continue;var w=walls[i];
                line(v,p,w[0],y0,w[1],w[2],y0,w[3],rgb,alpha);line(v,p,w[0],y1,w[1],w[2],y1,w[3],rgb,alpha);
                line(v,p,w[0],y0,w[1],w[0],y1,w[1],rgb,alpha);line(v,p,w[2],y0,w[3],w[2],y1,w[3],rgb,alpha);
                for(double d=shift;d<16;d+=4){double fx=w[0]+(w[2]-w[0])*d/16,fz=w[1]+(w[3]-w[1])*d/16;line(v,p,fx,y0,fz,fx,y1,fz,rgb,alpha/3);}
                for(double y=y0+shift;y<y1;y+=4)line(v,p,w[0],y,w[1],w[2],y,w[3],rgb,alpha/3);
                if(scan>=y0&&scan<y1)line(v,p,w[0],scan,w[1],w[2],scan,w[3],rgb,Math.min(255,alpha+40));
                if(ground>=y0&&ground<y1){
                    line(v,p,w[0],ground,w[1],w[2],ground,w[3],rgb,255);
                    // One-block ticks pointing into the zone (north wall inward is +z, east -x, south -z, west +x)
                    double ix=i==1?-.35:i==3?.35:0,iz=i==0?.35:i==2?-.35:0;
                    for(int d=0;d<=16;d++){double fx=w[0]+(w[2]-w[0])*d/16,fz=w[1]+(w[3]-w[1])*d/16;line(v,p,fx,ground,fz,fx+ix,ground,fz+iz,rgb,230);}
                }
            }
            if(!sections.contains(SectionPos.asLong(sx,sy+1,sz)))square(v,p,x0,y1,z0,x1,z1,rgb,alpha/2);
            if(!sections.contains(SectionPos.asLong(sx,sy-1,sz)))square(v,p,x0,y0,z0,x1,z1,rgb,alpha/2);
        }
    }
    private static void square(VertexConsumer v,PoseStack.Pose p,double x0,double y,double z0,double x1,double z1,int[] rgb,int alpha){
        line(v,p,x0,y,z0,x1,y,z0,rgb,alpha);line(v,p,x1,y,z0,x1,y,z1,rgb,alpha);line(v,p,x1,y,z1,x0,y,z1,rgb,alpha);line(v,p,x0,y,z1,x0,y,z0,rgb,alpha);
    }
    private static void line(VertexConsumer v,PoseStack.Pose p,double x,double y,double z,double xx,double yy,double zz,int[] rgb,int alpha){
        float dx=(float)(xx-x),dy=(float)(yy-y),dz=(float)(zz-z),length=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);if(length==0)return;
        v.vertex(p.pose(),(float)x,(float)y,(float)z).color(rgb[0],rgb[1],rgb[2],alpha).normal(p.normal(),dx/length,dy/length,dz/length).endVertex();
        v.vertex(p.pose(),(float)xx,(float)yy,(float)zz).color(rgb[0],rgb[1],rgb[2],alpha).normal(p.normal(),dx/length,dy/length,dz/length).endVertex();
    }
    /** The FOB Beacon's own model, see-through and tinted, where it would stand (facing the player, as when placed). */
    private static void ghost(PoseStack pose,BlockPos at,Minecraft mc){
        var state=EternalDefense.FOB_BEACON.get().defaultBlockState().setValue(FobBlocks.FACING,mc.player.getDirection().getOpposite());
        int[] c=colour(at);boolean red=c==PlacementGuard.RED;
        pose.pushPose();pose.translate(at.getX(),at.getY(),at.getZ());
        var consumer=red?Faint.blocked(BUFFER.getBuffer(RenderType.translucent())):new Faint(BUFFER.getBuffer(RenderType.translucent()),.45f);
        float[] rgb=PlacementGuard.tint(red,c);
        mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(),consumer,state,mc.getBlockRenderer().getBlockModel(state),
            rgb[0],rgb[1],rgb[2],LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,ModelData.EMPTY,RenderType.translucent());
        if(Animations.animated(state))Animations.drawRest(state,pose,consumer,rgb[0],rgb[1],rgb[2]);   // The flag and the rest, as placed
        BUFFER.endBatch(RenderType.translucent());
        pose.popPose();
    }
    /** Passes every vertex on with its alpha scaled down and its colour multiplied */
    record Faint(VertexConsumer to,float alpha,float red,float green,float blue) implements VertexConsumer {
        Faint(VertexConsumer to,float alpha){this(to,alpha,1f,1f,1f);}
        /** A ghost with something in the way: red, and a little less faint so the red reads. */
        static Faint blocked(VertexConsumer to){return new Faint(to,.6f,1f,.22f,.2f);}
        @Override public VertexConsumer vertex(double x,double y,double z){to.vertex(x,y,z);return this;}
        @Override public VertexConsumer color(int r,int g,int b,int a){to.color(Math.round(r*red),Math.round(g*green),Math.round(b*blue),Math.round(a*alpha));return this;}
        @Override public VertexConsumer uv(float u,float v){to.uv(u,v);return this;}
        @Override public VertexConsumer overlayCoords(int u,int v){to.overlayCoords(u,v);return this;}
        @Override public VertexConsumer uv2(int u,int v){to.uv2(u,v);return this;}
        @Override public VertexConsumer normal(float x,float y,float z){to.normal(x,y,z);return this;}
        @Override public void endVertex(){to.endVertex();}
        @Override public void defaultColor(int r,int g,int b,int a){to.defaultColor(r,g,b,a);}
        @Override public void unsetDefaultColor(){to.unsetDefaultColor();}
    }

    // The line under the crosshair
    /** What the spot looked at gives: "FOB here: 5 villagers (3 needed), 9 beds, 4 workstations" or why not. */
    static Component line(){
        if(!holding()||!overworld())return null;
        if(blocked(target()))return Ui.t("fob.preview.blocked");
        if(preview==null)return Ui.t("fob.preview.look");
        String problem=preview.getString("problem");int villagers=preview.getInt("villagers"),beds=preview.getInt("beds"),jobs=preview.getInt("jobs");
        if(problem.isEmpty())return Ui.t("fob.preview.good",villagers,FobRules.MIN_VILLAGERS,beds,jobs);
        if(problem.equals("villagers"))return Ui.t("fob.preview.villagers",villagers,FobRules.MIN_VILLAGERS,beds,jobs);
        return Ui.t("fob.preview.refused."+problem,FobRules.MAX_FOBS);
    }
    @SubscribeEvent public static void hud(RenderGuiOverlayEvent.Post e){
        if(e.getOverlay()!=VanillaGuiOverlay.CROSSHAIR.type())return;
        var mc=Minecraft.getInstance();if(mc.options.hideGui||mc.screen!=null)return;
        var text=line();if(text==null)return;
        var g=e.getGuiGraphics();var font=mc.font;int w=e.getWindow().getGuiScaledWidth(),h=e.getWindow().getGuiScaledHeight();
        int tw=Math.min(font.width(text),w-20),x=(w-tw)/2,y=h/2+14;
        g.fill(x-4,y-3,x+tw+4,y+11,0xa0101820);
        int color=blocked(target())?0xff000000|PlacementGuard.RED[0]<<16|PlacementGuard.RED[1]<<8|PlacementGuard.RED[2]:preview==null?Ui.MUTED:previewGood()?FobClient.OLIVE:Ui.ORANGE;
        Ui.text(g,font,text,x,y,color,tw);
    }
}
