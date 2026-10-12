package dev.createarsenal.beacon;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.client.model.data.ModelData;
import java.util.HashMap;
import java.util.Map;

/**
 * 0.0.34 (owner): the Supply Catapult drawn live in the Command Table's specialty seller, however far away it stands. The server sends every
 * catapult's heading and throw each tick while a table is open ({@link SupplyCatapult.View}); this draws the owner's deck, frame, arm and box
 * from a fixed three-quarter view, turning and throwing in step with the real one, the box flying out of the cup, and a line saying what it does.
 */
final class CatapultView {
    private CatapultView(){}
    /** The last state of a catapult and its heading the tick before (for drawing between ticks). */
    record Live(SupplyCatapult.View.Entry now,float prevYaw){}
    private static final Map<Long,Live> LIVE=new HashMap<>();

    static void receive(SupplyCatapult.View m){
        var next=new HashMap<Long,Live>();
        for(var e:m.entries()){var old=LIVE.get(e.fob());next.put(e.fob(),new Live(e,old==null?e.yaw():old.now().yaw()));}
        LIVE.clear();LIVE.putAll(next);
    }
    static Live of(long fob){return LIVE.get(fob);}
    /** The camera's turn about the catapult: north is up and to the left, as a map seen from the south-east. */
    static final float CAMERA_YAW=-35,CAMERA_PITCH=24;

    /** What the catapult is doing, for the line under it. */
    static Component caption(Live live){
        if(live==null)return Ui.t("tactical.seller.view.none");
        var e=live.now();
        if(e.throwAge()>=0)return Ui.t("tactical.seller.view.throwing");
        if(e.waiting()>0)return Ui.t("tactical.seller.view.turning",e.waiting());
        if(e.flying()>0)return Ui.t("tactical.seller.view.flying",e.landsIn());
        return Ui.t("tactical.seller.view.ready");
    }

    /** Draws the catapult of FOB {@code fob} into the box {@code x, y, w, h} (GUI pixels). */
    static void draw(GuiGraphics g,long fob,int x,int y,int w,int h){
        var live=of(fob);var mc=Minecraft.getInstance();if(mc.level==null)return;
        float partial=mc.getFrameTime();
        float yaw=live==null?0:Mth.lerp(partial,live.prevYaw(),live.now().yaw());
        int age=live==null?-1:live.now().throwAge();
        float arm=age<0?0:CatapultRules.armAngle(age+partial);
        boolean cup=live!=null&&live.now().loaded()&&(age<0||age<CatapultRules.RELEASE_TICK);
        float scale=Math.min(w/3.6f,h/3.0f);
        g.flush();
        g.enableScissor(x,y,x+w,y+h);
        PoseStack pose=g.pose();pose.pushPose();
        pose.translate(x+w/2f,y+h*.60f,150);
        pose.scale(scale,scale,-scale);
        pose.mulPose(Axis.ZP.rotationDegrees(180));
        pose.mulPose(Axis.XP.rotationDegrees(-CAMERA_PITCH));
        pose.mulPose(Axis.YP.rotationDegrees(CAMERA_YAW));
        pose.translate(-.5,-.75,-.5);
        Lighting.setupForEntityInInventory();
        var buffer=g.bufferSource();var type=Sheets.cutoutBlockSheet();
        int light=LightTexture.FULL_BRIGHT,overlay=OverlayTexture.NO_OVERLAY;
        // the deck stands still; the frame turns about the block's centre, the arm pitches about the owner's pivot
        var deck=mc.getBlockRenderer().getBlockModel(ArsenalBeacon.SUPPLY_CATAPULT.get().defaultBlockState());
        mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(),buffer.getBuffer(type),null,deck,1f,1f,1f,light,overlay,ModelData.EMPTY,type);
        pose.pushPose();
        pose.translate(.5,0,.5);pose.mulPose(Axis.YP.rotationDegrees(yaw));pose.translate(-.5,0,-.5);
        SupportClient.draw(pose,buffer,type,CatapultClient.FRAME,light,overlay);
        pose.translate(CatapultClient.PIVOT_X/16,CatapultClient.PIVOT_Y/16,CatapultClient.PIVOT_Z/16);pose.mulPose(Axis.XP.rotationDegrees(arm));
        pose.translate(-CatapultClient.PIVOT_X/16,-CatapultClient.PIVOT_Y/16,-CatapultClient.PIVOT_Z/16);
        SupportClient.draw(pose,buffer,type,CatapultClient.ARM,light,overlay);
        if(cup)SupportClient.draw(pose,buffer,type,CatapultClient.PAYLOAD,light,overlay);
        pose.popPose();
        // the box on its way out of the view, as the thrown box flies in the world
        if(age>=CatapultRules.RELEASE_TICK){
            double[] at=CatapultRules.release(yaw,1.6);double px=at[0],py=at[1],pz=at[2],vx=at[3],vy=at[4],vz=at[5];
            float t=age-CatapultRules.RELEASE_TICK+partial;
            for(int i=0;i<(int)t;i++){vx*=.99;vy=vy*.99-.02;vz*=.99;px+=vx;py+=vy;pz+=vz;}
            float f=t-(int)t;px+=vx*f;py+=vy*f;pz+=vz*f;
            pose.pushPose();pose.translate(px,py,pz);pose.mulPose(Axis.XP.rotationDegrees(t*-9f));
            pose.translate(-CatapultClient.BOX_X/16,-CatapultClient.BOX_Y/16,-CatapultClient.BOX_Z/16);
            SupportClient.draw(pose,buffer,type,CatapultClient.PAYLOAD,light,overlay);
            pose.popPose();
        }
        buffer.endBatch();
        pose.popPose();
        Lighting.setupFor3DItems();
        g.disableScissor();
    }
    static boolean shown(long fob){return LIVE.containsKey(fob);}
    /** For the QA harness: the heading and throw the view has now. */
    static float[] state(long fob){var live=of(fob);return live==null?null:new float[]{live.now().yaw(),live.now().throwAge(),live.now().loaded()?1:0,live.now().waiting(),live.now().flying(),live.now().landsIn()};}
}
