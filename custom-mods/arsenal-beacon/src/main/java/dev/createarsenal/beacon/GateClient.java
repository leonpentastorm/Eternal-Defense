package dev.createarsenal.beacon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Client side of the supply rail (0.0.24): the FOB's name on both nameplates of each gate (front and back, scaled to fit, like a vanilla sign),
 * and the gate screen: what to do (a picture strip), the light and why, a checklist with the fix next to every cross, the stockpile and the
 * next deliveries. The server decides everything ({@link Routes}); this only shows it and sends menu buttons.
 */
@Mod.EventBusSubscriber(modid=ArsenalBeacon.ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class GateClient {
    private GateClient(){}
    @SubscribeEvent public static void screens(FMLClientSetupEvent e){e.enqueueWork(()->MenuScreens.register(ArsenalBeacon.GATE_MENU.get(),GateScreen::new));}
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e){e.registerBlockEntityRenderer(ArsenalBeacon.GATE_ENTITY.get(),GateRenderer::new);}

    // ---- the nameplates --------------------------------------------------------------------------------------------------------------------------
    /** From the owner's model (facing north): the front plate at z 2.6, the back at z 13.4; text area x -4.5..20.5, y 17.75..20.5 (pixels). */
    static final float PLATE_FRONT=2.6f/16,PLATE_BACK=13.4f/16,TEXT_X=8f/16,TEXT_Y=19.125f/16,TEXT_WIDTH=25f/16,TEXT_HEIGHT=2.75f/16,SCALE=.018f;
    static final int INK=0xff2a2a2a,UNLINKED_INK=0xff8a1c1c;
    static final class GateRenderer implements BlockEntityRenderer<RailGates.GateEntity> {
        private final Font font;
        GateRenderer(BlockEntityRendererProvider.Context ctx){font=ctx.getFont();}
        @Override public void render(RailGates.GateEntity be,float partial,PoseStack pose,MultiBufferSource buffer,int light,int overlay){
            boolean linked=!be.name.isEmpty();
            String text=linked?be.name.toUpperCase(java.util.Locale.ROOT):Ui.plain("gate.unlinked");
            var facing=be.getBlockState().getValue(RailGates.FACING);
            pose.pushPose();
            pose.translate(.5,0,.5);pose.mulPose(Axis.YP.rotationDegrees(-((facing.toYRot()+180f)%360f)));pose.translate(-.5,0,-.5);
            plate(pose,buffer,text,linked?INK:UNLINKED_INK,light,PLATE_FRONT-.002f,true);
            plate(pose,buffer,text,linked?INK:UNLINKED_INK,light,PLATE_BACK+.002f,false);
            pose.popPose();
        }
        /** One plate: centred, shrunk to fit the plate (down to half size), then cut with an ellipsis. */
        private void plate(PoseStack pose,MultiBufferSource buffer,String text,int color,int light,float z,boolean front){
            float scale=Math.min(SCALE,Math.min(TEXT_WIDTH/Math.max(1,font.width(text)),TEXT_HEIGHT/9f));
            String shown=text;
            if(scale<SCALE*.5f){scale=SCALE*.5f;int room=(int)(TEXT_WIDTH/scale);shown=font.plainSubstrByWidth(text,room-font.width("..."))+"...";}
            pose.pushPose();
            pose.translate(TEXT_X,TEXT_Y,z);
            if(front)pose.mulPose(Axis.YP.rotationDegrees(180f));
            pose.scale(scale,-scale,scale);
            font.drawInBatch(shown,-font.width(shown)/2f,-4f,color,false,pose.last().pose(),buffer,Font.DisplayMode.POLYGON_OFFSET,0,light);
            pose.popPose();
        }
        @Override public boolean shouldRenderOffScreen(RailGates.GateEntity be){return true;}
    }

    // ---- the gate screen ------------------------------------------------------------------------------------------------------------------------
    static void state(CompoundTag data){if(net.minecraft.client.Minecraft.getInstance().screen instanceof GateScreen s)s.update(data);}
    static int color(String signal){return switch(signal){case "green"->FobClient.OLIVE;case "amber"->Ui.ORANGE;default->Ui.RED;};}
    /** "6 h 12 min", "3 min 12 s", "40 s". */
    static Component time(long ticks){
        long s=Math.max(0,ticks/20);
        if(s>=3600)return Ui.t("gate.time.hours",s/3600,(s%3600)/60);
        if(s>=60)return Ui.t("gate.time.minutes",s/60,s%60);
        return Ui.t("gate.time.seconds",s);
    }

    static final class GateScreen extends AbstractContainerScreen<RailGates.GateMenu> {
        private CompoundTag data;private boolean relink;
        private int leftX,rightX,colW,bodyY;
        GateScreen(RailGates.GateMenu menu,Inventory inv,Component title){super(menu,inv,title);data=menu.data;}
        void update(CompoundTag next){data=next;relink=false;rebuildWidgets();}
        private boolean receiving(){return data.getString("kind").equals(RailGates.Kind.RECEIVING.id);}
        private boolean linked(){return data.getBoolean("linked");}
        private boolean choosing(){return receiving()&&(!linked()||relink);}

        @Override protected void init(){
            imageWidth=Math.min(width-12,430);imageHeight=Math.min(height-12,262);
            super.init();clearWidgets();
            colW=(imageWidth-36)/2;leftX=leftPos+12;rightX=leftX+colW+12;bodyY=topPos+34;
            addRenderableWidget(new Ui.UiButton(leftPos+imageWidth-28,topPos+4,22,18,Component.literal("X"),Ui.Look.NORMAL,b->onClose()));
            if(choosing()){
                var options=data.getList("options",Tag.TAG_COMPOUND);int y=bodyY+30;
                for(int i=0;i<options.size()&&y<topPos+imageHeight-40;i++){
                    var o=options.getCompound(i);int id=Routes.LINK+i;
                    var b=addRenderableWidget(new Ui.UiButton(leftX,y,imageWidth-24,20,Ui.t("gate.link.option",o.getString("name"),Ui.t("fob.state."+o.getString("state"))),Ui.Look.PRIMARY,x->click(id)));
                    b.active=!o.getBoolean("current");y+=24;
                }
                if(linked())addRenderableWidget(new Ui.UiButton(leftX,topPos+imageHeight-30,90,20,Ui.t("gate.link.back"),Ui.Look.NORMAL,x->{relink=false;rebuildWidgets();}));
                return;
            }
            if(receiving()&&linked())addRenderableWidget(new Ui.UiButton(rightX+colW-110,topPos+imageHeight-30,110,20,Ui.t("gate.link.change"),Ui.Look.NORMAL,x->{relink=true;rebuildWidgets();}));
        }
        private void click(int id){if(minecraft!=null&&minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,id);}

        @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
            Ui.panel(g,leftPos,topPos,imageWidth,imageHeight);
            Component title=linked()?Component.literal(data.getString("name")):Ui.t("gate.unlinked");
            Ui.header(g,font,leftPos,topPos,imageWidth,title,Component.translatable("block.arsenal_beacon."+data.getString("kind")));
            if(choosing()){
                Ui.wrap(g,font,Ui.t(data.getList("options",Tag.TAG_COMPOUND).isEmpty()?"gate.link.none":"gate.link.help"),leftX,bodyY,imageWidth-24,Ui.MUTED,3);
            }else{
                strip(g);
                drawStatus(g);
                drawChecklist(g);
                drawStock(g);
            }
            var message=data.getString("message");
            if(!message.isEmpty())Ui.text(g,font,Component.translatable("gui.arsenal_beacon."+message),leftX,topPos+imageHeight-14,message.endsWith(".linked")?FobClient.OLIVE:Ui.ORANGE,imageWidth/2);
        }
        /** The picture strip: Receiving Gate, a cart with a rider, the Delivery Gate, and back. */
        private void strip(GuiGraphics g){
            int y=bodyY,x=leftX;
            Ui.card(g,x,y,imageWidth-24,26,Ui.EDGE);
            ItemStack receiving=new ItemStack(ArsenalBeacon.RECEIVING_GATE_ITEM.get()),cart=new ItemStack(Items.MINECART),delivery=new ItemStack(ArsenalBeacon.DELIVERY_GATE_ITEM.get());
            int cx=x+8;
            g.renderItem(receiving,cx,y+5);Ui.text(g,font,Ui.t("gate.strip.home"),cx+19,y+9,Ui.INK,60);cx+=19+font.width(Ui.t("gate.strip.home"))+6;
            Ui.text(g,font,Component.literal("→"),cx,y+9,Ui.CYAN,10);cx+=12;
            g.renderItem(cart,cx,y+5);Ui.text(g,font,Ui.t("gate.strip.ride"),cx+19,y+9,Ui.INK,80);cx+=19+font.width(Ui.t("gate.strip.ride"))+6;
            Ui.text(g,font,Component.literal("→"),cx,y+9,Ui.CYAN,10);cx+=12;
            g.renderItem(delivery,cx,y+5);Ui.text(g,font,Ui.t("gate.strip.fob"),cx+19,y+9,Ui.INK,60);cx+=19+font.width(Ui.t("gate.strip.fob"))+6;
            Ui.text(g,font,Ui.t("gate.strip.back"),cx,y+9,Ui.MUTED,x+imageWidth-24-cx-6);
        }
        /** The light and what it means. */
        private void drawStatus(GuiGraphics g){
            String signal=data.getString("signal");int y=bodyY+30;
            Ui.chip(g,font,Ui.t("gate.signal."+signal),leftX,y,color(signal),90);
            Ui.text(g,font,reason(),leftX+96,y+2,color(signal),imageWidth-24-96);
        }
        private Component reason(){
            if(!linked())return Ui.t("gate.reason.unlinked");
            if(!data.getBoolean("partner"))return Ui.t("gate.reason.partner");
            if(!data.getString("state").equals("active"))return Ui.t("gate.reason.fob",Ui.t("fob.state."+data.getString("state")));
            if(!data.getBoolean("commissioned"))return Ui.t("gate.reason.open");
            if(data.getLong("clock")<=0)return Ui.t("gate.reason.overdue");
            if(data.getBoolean("riding"))return Ui.t("gate.reason.riding");
            if(data.getInt("queued")>=data.getInt("queueCap"))return Ui.t("gate.reason.backlog");
            if(data.getBoolean("warn"))return Ui.t("gate.reason.warn");
            return Ui.t("gate.reason.running");
        }
        /** The requirements, a tick or a cross each, the fix next to every cross. */
        private void drawChecklist(GuiGraphics g){
            int x=leftX,w=colW,y=bodyY+48;
            Ui.text(g,font,Ui.t("gate.check.title"),x,y,Ui.BRASS,w);y+=12;
            boolean fob=linked()&&data.getString("state").equals("active");
            y=line(g,x,y,w,fob,Ui.t("gate.check.fob"),linked()?Ui.t("gate.fix.fob"):Ui.t(receiving()?"gate.fix.link":"gate.fix.unlinked"));
            y=line(g,x,y,w,data.getBoolean("partner"),Ui.t("gate.check.partner"),Ui.t(receiving()?"gate.fix.partner.receiving":"gate.fix.partner.delivery"));
            // the distance only means something once both gates stand
            if(data.getBoolean("partner")){boolean far=data.getInt("distance")>=data.getInt("minDistance");
                y=line(g,x,y,w,far,Ui.t("gate.check.distance",data.getInt("distance")),Ui.t("gate.fix.distance",data.getInt("minDistance")));}
            boolean open=data.getBoolean("commissioned");
            y=line(g,x,y,w,open,Ui.t("gate.check.route"),Ui.t("gate.fix.route"),3);   // the whole instruction, never cut
            long clock=data.getLong("clock");
            if(!open){Ui.text(g,font,Component.literal("-"),x+1,y,Ui.MUTED,8);Ui.text(g,font,Ui.t("gate.check.clock.later"),x+9,y,Ui.MUTED,w-9);y+=12;}
            else if(clock>0){y=line(g,x,y,w,true,Ui.t("gate.check.clock",time(clock)),Component.empty());Ui.text(g,font,Ui.t("gate.check.clock.renews"),x+9,y-2,Ui.MUTED,w-9);y+=10;}
            else y=line(g,x,y,w,false,Ui.t("gate.check.clock.none"),Ui.t("gate.fix.clock"));
            line(g,x,y,w,data.getInt("queued")<data.getInt("queueCap"),Ui.t("gate.check.backlog",data.getInt("queued"),data.getInt("queueCap")),Ui.t("gate.fix.backlog"));
        }
        private int line(GuiGraphics g,int x,int y,int w,boolean ok,Component what,Component fix){return line(g,x,y,w,ok,what,fix,2);}
        private int line(GuiGraphics g,int x,int y,int w,boolean ok,Component what,Component fix,int fixLines){
            if(ok)Ui.check(g,x,y+1,FobClient.OLIVE);else Ui.cross(g,x,y+1,Ui.RED);
            Ui.text(g,font,what,x+9,y,ok?Ui.INK:Ui.ORANGE,w-9);
            if(ok)return y+12;
            return Ui.wrap(g,font,fix,x+9,y+10,w-9,Ui.MUTED,fixLines)+3;
        }
        /** The stockpile, the next delivery, what a ride carries, the last delivery. */
        private void drawStock(GuiGraphics g){
            int x=rightX,w=colW,y=bodyY+48;
            Ui.text(g,font,Ui.t("gate.stock.title",data.getInt("workers")),x,y,Ui.BRASS,w);y+=12;
            var stock=data.getList("stock",Tag.TAG_COMPOUND);
            for(int i=0;i<stock.size();i++){
                var row=stock.getCompound(i);int cx=x+(i%4)*(w/4),cy=y+(i/4)*20;
                var item=BuiltInRegistries.ITEM.get(new ResourceLocation(row.getString("id")));
                g.renderItem(new ItemStack(item),cx,cy);
                Ui.text(g,font,Component.literal(Integer.toString(row.getInt("amount"))),cx+17,cy+5,row.getInt("amount")>=row.getInt("cap")?Ui.ORANGE:Ui.INK,w/4-18);
            }
            y+=((stock.size()+3)/4)*20+4;
            long next=data.getLong("nextIn");
            Ui.wrap(g,font,next>=0?Ui.t("gate.next",time(next),data.getInt("capacity")):Ui.t("gate.next.paused"),x,y,w,next>=0?Ui.INK:Ui.MUTED,2);y+=22;
            Ui.wrap(g,font,Ui.t("gate.ride",data.getInt("active")),x,y,w,Ui.MUTED,2);y+=22;
            long ago=data.getLong("lastAgo");
            if(ago>=0)Ui.wrap(g,font,Ui.t("gate.last."+(data.getString("lastKind").equals("ride")?"ride":"passive"),data.getInt("lastItems"),time(ago)),x,y,w,Ui.MUTED,2);
        }
        @Override protected void renderLabels(GuiGraphics g,int mx,int my){}
        @Override public void render(GuiGraphics g,int mx,int my,float partial){
            renderBackground(g);super.render(g,mx,my,partial);
            if(!choosing()){
                var stock=data.getList("stock",Tag.TAG_COMPOUND);int y=bodyY+60;
                for(int i=0;i<stock.size();i++){int cx=rightX+(i%4)*(colW/4),cy=y+(i/4)*20;
                    if(Ui.inside(mx,my,cx,cy,16,16)){var row=stock.getCompound(i);var item=BuiltInRegistries.ITEM.get(new ResourceLocation(row.getString("id")));
                        g.renderComponentTooltip(font,List.of(item.getDescription(),Ui.t("gate.stock.tip",row.getInt("amount"),row.getInt("cap"))),mx,my);}}
            }
        }
    }
}
