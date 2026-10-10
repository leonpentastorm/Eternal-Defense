package dev.createarsenal.beacon;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.ArrayList;
import java.util.List;

/** Client presentation only. Every purchase and confirmation is validated on the server. */
@Mod.EventBusSubscriber(modid=ArsenalBeacon.ID,value=Dist.CLIENT)
public final class BeaconClient {
    private static CompoundTag state=new CompoundTag();
    private static long receivedAt;
    private static int n(String key){return state.getInt(key);}
    private static boolean on(String key){return state.getBoolean(key);}
    static void receive(BeaconNetwork.State packet){
        state=packet.data();Minecraft mc=Minecraft.getInstance();receivedAt=mc.level==null?0:mc.level.getGameTime();
        BeaconAlerts.receive(state.getBoolean("underAttack"));
        if(BeaconStartup.defer(packet))return;
        if(mc.screen instanceof PanelScreen screen&&!packet.message().isEmpty())screen.message=packet.message();
        switch(packet.screen()){
            case "menu" -> mc.setScreen(new ControlScreen());
            case "rewards" -> mc.setScreen(new RaidRewardScreen(mc.screen,state));
            case "guide" -> mc.setScreen(new GuideScreen(mc.screen instanceof ControlScreen?mc.screen:null));
            case "place","remove" -> mc.setScreen(new ConfirmScreen(packet.screen(),packet.token()));
            case "close" -> {if(mc.screen instanceof PanelScreen)mc.setScreen(null);}
        }
    }
    /** The campaign state is refreshed every second; only a long silence (a lost connection) hides the outlines. A few seconds of server lag, as when the cannon fires, must not. */
    static final int STALE_TICKS=600;
    /** Own buffer for the zone and hurtbox lines, so nothing else drawing lines in the same frame can end or reuse their batch. */
    private static final net.minecraft.client.renderer.MultiBufferSource.BufferSource ZONE_BUFFER=net.minecraft.client.renderer.MultiBufferSource.immediate(new com.mojang.blaze3d.vertex.BufferBuilder(1<<20));
    private static boolean current(){var mc=Minecraft.getInstance();return mc.level!=null&&mc.level.dimension()==Level.OVERWORLD&&mc.level.getGameTime()-receivedAt<STALE_TICKS&&state.getBoolean("installed");}
    /** Which raid theme should play for this player now (see {@link RaidMusic#cue}); "" for none. */
    static String raidMusicCue(){return RaidMusic.cue(current(),state.getString("phase"),state.getBoolean("near"),state.getBoolean("hardRaid"),state.getString("raidType"));}
    private static boolean holding(){var p=Minecraft.getInstance().player;return p!=null&&(p.getMainHandItem().is(ArsenalBeacon.CONTROLLER.get())||p.getOffhandItem().is(ArsenalBeacon.CONTROLLER.get()));}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){state=new CompoundTag();receivedAt=0;BeaconStartup.clear();BeaconAlerts.clear();GunPackSync.clearReceiving();}
    @Mod.EventBusSubscriber(modid=ArsenalBeacon.ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
    public static final class Registration {
        @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e){e.registerBlockEntityRenderer(ArsenalBeacon.BEACON_ENTITY.get(),Beam::new);e.registerEntityRenderer(ArsenalBeacon.OBJECTIVE.get(),net.minecraft.client.renderer.entity.NoopRenderer::new);}
        @SubscribeEvent public static void overlays(RegisterGuiOverlaysEvent e){e.registerAboveAll("beacon_status",(gui,g,partial,w,h)->hud(g,w,h));}
    }
    /**
     * Status card shown near a planted beacon, during raids and while holding the recovery shovel.
     * It sits at the left edge, vertically centred, clear of chat and the hotbar (bottom), the Gun Guide
     * (right edge), the TaCZ weapon HUD (bottom right) and block-information overlays (top centre).
     */
    private static void hud(GuiGraphics g,int w,int h){
        var mc=Minecraft.getInstance();if(mc.options.hideGui||mc.screen!=null||!current()||!Rules.showHud(state.getString("phase"),holding(),state.getBoolean("near")))return;
        drawHud(g,w,h);
    }
    static void drawHud(GuiGraphics g,int w,int h){
        var mc=Minecraft.getInstance();var font=mc.font;boolean raid=state.getString("phase").equals("raid"),dead=n("health")<=0;
        Component title=Ui.t("hud.title"),hp=Ui.t("hud.hp",n("health"),n("maximum")),line=raid?Ui.t("hud.raid",n("wave"),n("waves"),n("attackers")):phase(state.getString("phase"));
        int width=Math.min(Math.max(140,Math.max(font.width(title),Math.max(font.width(hp),font.width(line)))+24),Math.max(96,w/3)),height=48+(raid&&on("hardRaid")?14:0);
        int x=6,y=Math.max(8,(h-height)/2);
        float fraction=Math.max(0,Math.min(1,n("health")/(float)Math.max(1,n("maximum"))));
        g.fill(x-1,y-1,x+width+1,y+height+1,Ui.SHADOW);g.fill(x,y,x+width,y+height,0xe6111e28);
        g.fill(x,y,x+3,y+height,dead?Ui.RED:Ui.CYAN);g.fill(x+3,y,x+width,y+1,Ui.SLATE_HI);
        Ui.text(g,font,title,x+9,y+6,dead?Ui.RED:Ui.CYAN,width-14);
        Ui.text(g,font,hp,x+9,y+18,Ui.INK,width-14);
        Ui.bar(g,x+9,y+30,width-18,5,fraction,dead||fraction<=.25f?Ui.RED:Ui.CYAN);
        Ui.text(g,font,line,x+9,y+39,Ui.MUTED,width-14);
        if(raid&&on("hardRaid"))Ui.text(g,font,Ui.t("hud.boss"),x+9,y+50,Ui.BRASS,width-14);
    }
    static Component phase(String phase){
        return Ui.t("phase."+switch(phase){case "preparation","raid","disabled","restore","snapshot","decommissioning"->phase;default->"unplaced";});
    }
    public static final class Beam implements BlockEntityRenderer<ArsenalBeacon.DefenseEntity> {
        public Beam(BlockEntityRendererProvider.Context context){}
        @Override public void render(ArsenalBeacon.DefenseEntity entity,float partial,PoseStack pose,MultiBufferSource buffer,int light,int overlay){
            if(!current()||!state.getBoolean("beam")||!entity.getBlockPos().equals(BlockPos.of(state.getLong("beacon"))))return;
            float[] color=state.getInt("health")>0?new float[]{.22f,1f,.88f}:new float[]{1f,.35f,.18f};
            pose.pushPose();pose.translate(0,state.getInt("mk")==4?2:1,0);BeaconRenderer.renderBeaconBeam(pose,buffer,BeaconRenderer.BEAM_LOCATION,partial,1f,entity.getLevel().getGameTime(),0,512,color,.2f,.28f);pose.popPose();
        }
        @Override public boolean shouldRenderOffScreen(ArsenalBeacon.DefenseEntity entity){return true;}
        @Override public int getViewDistance(){return 256;}
    }
    @SubscribeEvent public static void damageOutline(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS||!current()||!state.getBoolean("near")||!state.getBoolean("hurtbox"))return;
        var box=ArsenalStructures.hurtbox(BlockPos.of(state.getLong("beacon")),state.getInt("mk"));var camera=event.getCamera().getPosition();var pose=event.getPoseStack();pose.pushPose();pose.translate(-camera.x,-camera.y,-camera.z);var buffers=ZONE_BUFFER;LevelRenderer.renderLineBox(pose,buffers.getBuffer(RenderType.lines()),box.inflate(.003),1f,.12f,.16f,.9f);buffers.endBatch(RenderType.lines());pose.popPose();
    }
    @SubscribeEvent public static void outline(RenderLevelStageEvent e){
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS||!current()||!state.getBoolean("near")||!state.getBoolean("outline"))return;
        var mc=Minecraft.getInstance();BlockPos pos=BlockPos.of(state.getLong("beacon"));var camera=e.getCamera().getPosition();
        PoseStack pose=e.getPoseStack();pose.pushPose();pose.translate(pos.getX()-camera.x,pos.getY()-camera.y,pos.getZ()-camera.z);
        var buffers=ZONE_BUFFER;VertexConsumer lines=buffers.getBuffer(RenderType.lines());var transform=pose.last();double r=state.getInt("radius");
        // Exact outer block faces: [-radius, radius+1), with an inclusive 65-block height.
        double lo=-r,hi=r+1;int bottom=-state.getInt("below"),ceiling=state.getInt("above")+1;
        for(int y:new int[]{bottom,0,ceiling}){
            line(lines,transform,lo,y,lo,hi,y,lo);line(lines,transform,hi,y,lo,hi,y,hi);
            line(lines,transform,hi,y,hi,lo,y,hi);line(lines,transform,lo,y,hi,lo,y,lo);
        }
        for(double x:new double[]{lo,hi})for(double z:new double[]{lo,hi})line(lines,transform,x,bottom,z,x,ceiling,z);
        // One-block ticks on the base plane; vertical guides every eight blocks.
        for(int i=(int)lo;i<=hi;i++){
            line(lines,transform,i,0,lo,i,0,lo+.35);line(lines,transform,i,0,hi,i,0,hi-.35);
            line(lines,transform,lo,0,i,lo+.35,0,i);line(lines,transform,hi,0,i,hi-.35,0,i);
            if((i+(int)r)%8==0)for(double side:new double[]{lo,hi}){line(lines,transform,i,bottom,side,i,ceiling,side);line(lines,transform,side,bottom,i,side,ceiling,i);}
        }
        // Moving interior grid lines stay clipped to the six exact faces; edges never drift.
        double shift=((mc.level.getGameTime()+e.getPartialTick())*.025)%4;
        for(double i=lo+shift;i<hi;i+=4)for(double side:new double[]{lo,hi}){
            line(lines,transform,i,bottom,side,i,ceiling,side,80);
            line(lines,transform,side,bottom,i,side,ceiling,i,80);
        }
        for(double y=bottom+shift;y<ceiling;y+=4)for(double side:new double[]{lo,hi}){
            line(lines,transform,lo,y,side,hi,y,side,80);
            line(lines,transform,side,y,lo,side,y,hi,80);
        }
        for(double y:new double[]{bottom,ceiling})for(double i=lo+shift;i<hi;i+=4){
            line(lines,transform,i,y,lo,i,y,hi,120);
            line(lines,transform,lo,y,i,hi,y,i,120);
        }
        double scan=bottom+((mc.level.getGameTime()+e.getPartialTick())*.12)%(ceiling-bottom);
        for(double side:new double[]{lo,hi}){line(lines,transform,lo,scan,side,hi,scan,side,235);line(lines,transform,side,scan,lo,side,scan,hi,235);}
        buffers.endBatch(RenderType.lines());pose.popPose();
    }
    private static void line(VertexConsumer v,PoseStack.Pose p,double x,double y,double z,double xx,double yy,double zz){
        line(v,p,x,y,z,xx,yy,zz,180);
    }
    private static void line(VertexConsumer v,PoseStack.Pose p,double x,double y,double z,double xx,double yy,double zz,int alpha){
        float dx=(float)(xx-x),dy=(float)(yy-y),dz=(float)(zz-z),length=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);if(length==0)return;
        v.vertex(p.pose(),(float)x,(float)y,(float)z).color(70,235,221,alpha).normal(p.normal(),dx/length,dy/length,dz/length).endVertex();
        v.vertex(p.pose(),(float)xx,(float)yy,(float)zz).color(70,235,221,alpha).normal(p.normal(),dx/length,dy/length,dz/length).endVertex();
    }
    abstract static class PanelScreen extends Screen {
        int left,top,pw,ph;String message="";
        private String shownMessage="";private long shownAt;
        PanelScreen(Component title){super(title);}
        @Override protected void init(){pw=Math.min(520,width-16);ph=Math.min(290,height-16);left=(width-pw)/2;top=(height-ph)/2;}
        @Override public boolean isPauseScreen(){return false;}
        Component subtitle(){return null;}
        void panel(GuiGraphics g){renderBackground(g);Ui.panel(g,left,top,pw,ph);Ui.header(g,font,left,top,pw,title,subtitle());}
        Ui.UiButton button(Component label,int x,int y,int w,Ui.Look look,java.util.function.Consumer<Button> click){return button(label,x,y,w,20,look,click);}
        Ui.UiButton button(Component label,int x,int y,int w,int h,Ui.Look look,java.util.function.Consumer<Button> click){return addRenderableWidget(new Ui.UiButton(x,y,w,h,label,look,click::accept));}
        void closeButton(){button(Component.literal("X"),left+pw-30,top+4,22,18,Ui.Look.NORMAL,b->onClose());}
        /** Server feedback sits in one stable strip and disappears by itself. Returns whether it was drawn. */
        boolean footer(GuiGraphics g){
            if(!messageVisible())return false;
            int y=top+ph-19;Ui.inset(g,left+10,y,pw-20,13);Ui.text(g,font,Component.literal(message),left+15,y+3,Ui.CYAN,pw-30);return true;
        }
        /** Same feedback as one plain line, for screens whose bottom edge is already occupied. */
        boolean footerLine(GuiGraphics g,int y){
            if(!messageVisible())return false;
            Ui.text(g,font,Component.literal(message),left+14,y,Ui.CYAN,pw-28);return true;
        }
        private boolean messageVisible(){
            long now=net.minecraft.Util.getMillis();
            if(!message.equals(shownMessage)){shownMessage=message;shownAt=now;}
            return !message.isEmpty()&&now-shownAt<=7000;
        }
        void hint(GuiGraphics g,Component text){hint(g,text,Ui.MUTED);}
        void hint(GuiGraphics g,Component text,int color){int y=top+ph-17;Ui.text(g,font,text,left+14,y,color,pw-28);}
        Hover itemAt(double x,double y){return null;}
    }
    record Hover(ItemStack item,int x,int y){}

    static final class ControlScreen extends PanelScreen {
        int tab;final List<Ui.UiButton> fabricationButtons=new ArrayList<>();net.minecraft.nbt.ListTag upgradeHoverCosts;Button claim,repair,outline,beam,hurtbox,remove,respite,startRaid,lowerTier,higherTier;final List<Button> upgrades=new ArrayList<>();
        private final List<Hover> icons=new ArrayList<>();
        static final String[] BRANCHES={"core","logistics","defense","restoration","reconnaissance","vertical"};
        static final String[] PARTS={"reinforced_plating","logistics_module","resonance_coil","restoration_matrix","resonance_coil","logistics_module"};
        static final String[] TABS={"overview","upgrades","fabrication","raid"};
        ControlScreen(){super(Ui.t("title"));}
        @Override Component subtitle(){return Ui.t("subtitle");}
        private static ItemStack part(int branch){return new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(new ResourceLocation(ArsenalBeacon.ID,PARTS[branch])));}
        private int contentTop(){return top+54;}
        private boolean ready(){return on("installed")&&on("near")&&!on("active");}
        @Override protected void init(){
            super.init();ph=Math.min(340,height-16);top=(height-ph)/2;upgrades.clear();fabricationButtons.clear();claim=repair=outline=beam=hurtbox=remove=respite=startRaid=lowerTier=higherTier=null;
            int total=0;for(String key:TABS)total+=font.width(Ui.t("tab."+key))+14;int tx=left+14,space=pw-28;
            for(int i=0;i<TABS.length;i++){int page=i,tw=(font.width(Ui.t("tab."+TABS[i]))+14)*space/total;var b=button(Ui.t("tab."+TABS[i]),tx,top+31,tw-2,18,Ui.Look.TAB,x->{tab=page;rebuildWidgets();});b.selected=i==tab;tx+=tw;}
            closeButton();
            button(Ui.t("guide"),left+pw-92,top+4,56,18,Ui.Look.NORMAL,b->BeaconNetwork.action("guide",""));
            button(Ui.t("tab.settings"),left+pw-157,top+4,61,18,Ui.Look.NORMAL,b->{tab=4;rebuildWidgets();});
            int x=left+14,w=pw-28;
            if(tab==0){
                int bw=(w-8)/3,y=top+ph-48;
                claim=button(Ui.t("overview.chest"),x,y,bw,Ui.Look.PRIMARY,b->BeaconNetwork.action("claim",""));
                repair=button(Economy.standalone()?Ui.t("overview.repair.standalone"):Ui.t("overview.repair"),x+bw+4,y,bw,Ui.Look.NORMAL,b->BeaconNetwork.action("repair",""));
                button(Ui.t("overview.tiers"),x+(bw+4)*2,y,w-(bw+4)*2,Ui.Look.NORMAL,b->BeaconNetwork.action("rewards",""));
            }else if(tab==1){
                int cw=(w-6)/2,stride=(ph-54-26)/3,ch=stride-4;
                for(int i=0;i<BRANCHES.length;i++){String branch=BRANCHES[i];int cx=x+(i%2)*(cw+6),cy=contentTop()+(i/2)*stride;
                    upgrades.add(button(Component.empty(),cx+cw-88,cy+ch-21,82,18,Ui.Look.NORMAL,b->BeaconNetwork.action("upgrade:"+branch,"")));
                }
            }else if(tab==3){
                var lay=raidLayout();
                lowerTier=button(Ui.t("raid.lower"),x+10,lay.levelY,96,Ui.Look.NORMAL,b->BeaconNetwork.action("raid-level:"+Math.max(0,n("raidLimit")-1),""));
                higherTier=button(Ui.t("raid.raise"),x+w-106,lay.levelY,96,Ui.Look.NORMAL,b->BeaconNetwork.action("raid-level:"+Math.min(10,n("raidLimit")+1),""));
                int half=(w-4)/2,y=top+ph-48;
                respite=button(Ui.t("raid.buy"),x,y,half,Ui.Look.PRIMARY,b->BeaconNetwork.action("respite",""));
                startRaid=button(Ui.t("raid.start"),x+half+4,y,w-half-4,Ui.Look.NORMAL,b->BeaconNetwork.action("start-raid",""));
                startRaid.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Ui.t("raid.start.tip")));
                respite.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Ui.t("raid.buy.tip")));
            }else if(tab==2){
                for(int i=0;i<WorkshopFabrication.ITEMS.size();i++){
                    int index=i;var cell=fabricationCell(i,x,w);
                    fabricationButtons.add(button(Ui.t("fabrication.make"),cell.x+cell.w-41,cell.y+(cell.h-18)/2,37,18,Ui.Look.PRIMARY,b->{var rows=state.getList("fabrications",net.minecraft.nbt.Tag.TAG_COMPOUND);if(index<rows.size())BeaconNetwork.action("fabricate:"+rows.getCompound(index).getString("id"),"");}));
                }
            }else{
                int gap=settingsGap();
                outline=button(Component.empty(),x,contentTop()+2,w,22,Ui.Look.TOGGLE,b->BeaconNetwork.action("outline",""));
                beam=button(Component.empty(),x,contentTop()+2+gap,w,22,Ui.Look.TOGGLE,b->BeaconNetwork.action("beam",""));
                hurtbox=button(Component.empty(),x,contentTop()+2+gap*2,w,22,Ui.Look.TOGGLE,b->BeaconNetwork.action("hurtbox",""));
                remove=button(Ui.t("settings.remove"),x,contentTop()+2+gap*3,w,22,Ui.Look.DANGER,b->BeaconNetwork.action("remove",""));
            }
        }
        private int settingsGap(){return Math.min(48,Math.max(33,(ph-54-28)/4));}
        private record RaidLayout(int levelY,int cardH,int breakY,boolean roomy){}
        private RaidLayout raidLayout(){boolean roomy=ph>=270;int cardH=roomy?72:60;return new RaidLayout(contentTop()+20,cardH,contentTop()+cardH+4,roomy);}
        @Override public void render(GuiGraphics g,int mx,int my,float partial){
            panel(g);icons.clear();upgradeHoverCosts=null;List<Component> tooltip=null;
            int x=left+14,w=pw-28;
            if(tab==0)overview(g,x,w);
            else if(tab==1)tooltip=upgradeCards(g,mx,my,x,w);
            else if(tab==3)raidTab(g,x,w);
            else if(tab==2)fabricationTab(g,mx,my,x,w);
            else settingsTab(g,x,w);
            super.render(g,mx,my,partial);
            boolean notice=footer(g);
            if(!notice&&tab==1&&!ready())hint(g,Ui.t("upgrades.near"));
            Hover hovered=itemAt(mx,my);
            if(hovered!=null)g.renderComponentTooltip(font,List.of(hovered.item().getHoverName(),Component.literal(ControlHints.jei())),mx,my);
            else if(tooltip!=null){if(upgradeHoverCosts!=null)Ui.materialTooltip(g,font,tooltip,upgradeHoverCosts,mx,my);else g.renderComponentTooltip(font,tooltip,mx,my);}
            if(tab==2&&upgradeHoverCosts!=null){
                var rows=state.getList("fabrications",net.minecraft.nbt.Tag.TAG_COMPOUND);var fabItem=ItemStack.EMPTY;String id="";
                for(int i=0;i<fabricationButtons.size()&&i<rows.size();i++){var cell=fabricationCell(i,left+14,pw-28);if(Ui.inside(mx,my,cell.x,cell.y,cell.w-44,cell.h)){fabItem=ItemStack.of(rows.getCompound(i).getCompound("item"));id=rows.getCompound(i).getString("id");}}
                Ui.materialTooltip(g,font,fabItem.isEmpty()?List.of(Ui.t("tab.fabrication")):List.of(fabItem.getHoverName(),Ui.t("fabrication.desc."+id)),upgradeHoverCosts,mx,my);
            }
        }
        /** Starter installations in three groups: the four tables, the kitchen, and support and trade. */
        private static final int[] FAB_GROUP_START={0,4,8};
        private static final int[] FAB_ACCENT={Ui.BRASS,Ui.CYAN,0xff6cd4ef};
        private static final String[] FAB_GROUP={"tables","kitchen","support"};
        private record FabCell(int x,int y,int w,int h){}
        private int fabricationRowHeight(){return Math.max(20,Math.min(34,(ph-54-14-3*12)/6-2));}
        private FabCell fabricationCell(int index,int x,int w){
            int group=index>=FAB_GROUP_START[2]?2:index>=FAB_GROUP_START[1]?1:0,inGroup=index-FAB_GROUP_START[group],rowH=fabricationRowHeight();
            int cw=(w-6)/2,y=contentTop()+group*(12+2*(rowH+2))+12+(inGroup/2)*(rowH+2);
            return new FabCell(x+(inGroup%2)*(cw+6),y,cw,rowH);
        }
        private void fabricationTab(GuiGraphics g,int mx,int my,int x,int w){
            var rows=state.getList("fabrications",net.minecraft.nbt.Tag.TAG_COMPOUND);boolean here=on("installed")&&on("near");
            for(int group=0;group<3;group++){
                int y=contentTop()+group*(12+2*(fabricationRowHeight()+2));var label=Ui.t("fabrication.group."+FAB_GROUP[group]);
                Ui.text(g,font,label,x,y+1,FAB_ACCENT[group],w/2);Ui.rule(g,x+font.width(label)+6,y+5,w-font.width(label)-6);
            }
            for(int i=0;i<fabricationButtons.size();i++){
                var button=fabricationButtons.get(i);button.visible=i<rows.size();if(!button.visible)continue;
                var row=rows.getCompound(i);var id=row.getString("id");var item=ItemStack.of(row.getCompound("item"));var costs=row.getList("costs",net.minecraft.nbt.Tag.TAG_COMPOUND);
                boolean enough=on("creative")||costs.stream().allMatch(c->((net.minecraft.nbt.CompoundTag)c).getInt("have")>=((net.minecraft.nbt.CompoundTag)c).getInt("count"));
                int group=i>=FAB_GROUP_START[2]?2:i>=FAB_GROUP_START[1]?1:0;var cell=fabricationCell(i,x,w);
                Ui.card(g,cell.x,cell.y,cell.w,cell.h,enough&&here?FAB_ACCENT[group]:Ui.EDGE);
                g.renderItem(item,cell.x+7,cell.y+(cell.h-16)/2);
                Ui.text(g,font,Ui.t("fabrication.name."+id),cell.x+27,cell.y+(cell.h>=26?4:2),enough&&here?Ui.INK:Ui.MUTED,cell.w-27-44);
                int costX=cell.x+27,costY=cell.y+cell.h-(cell.h>=26?12:10);
                for(var entry:costs){var cost=(net.minecraft.nbt.CompoundTag)entry;var icon=ItemStack.of(cost.getCompound("item"));int need=cost.getInt("count"),have=cost.getInt("have");boolean short_=!on("creative")&&have<need;
                    g.pose().pushPose();g.pose().translate(costX,costY-1,0);g.pose().scale(.5f,.5f,1);g.renderItem(icon,0,0);g.pose().popPose();
                    var count=Component.literal(Integer.toString(need));g.drawString(font,count,costX+9,costY,short_?Ui.ORANGE:Ui.MUTED,false);costX+=12+font.width(count);
                }
                button.active=here&&enough;button.warning=!enough&&here;
                if(Ui.inside(mx,my,cell.x,cell.y,cell.w-44,cell.h))upgradeHoverCosts=costs;
            }
            if(!here)hint(g,Ui.t(on("installed")?"fabrication.near":"fabrication.plant"),Ui.ORANGE);
        }
        // ---- Overview: "How is my base doing?" ------------------------------------------------
        private void overview(GuiGraphics g,int x,int w){
            int y=contentTop();boolean roomy=ph>=270,dead=n("health")<=0,active=on("active");
            int statusH=roomy?58:46;
            Ui.card(g,x,y,w,statusH,dead?Ui.RED:Ui.CYAN);
            int mk=Ui.chip(g,font,Ui.t("overview.mk",n("mk")),x+10,y+4,Ui.BRASS,72);
            Ui.text(g,font,phase(state.getString("phase")),x+16+mk,y+6,dead?Ui.RED:Ui.INK,Math.max(20,w-mk-130));
            Ui.right(g,font,Ui.t("overview.hp",n("health"),n("maximum")),x+w-8,y+6,dead?Ui.RED:Ui.CYAN);
            float hp=Math.max(0,Math.min(1,n("health")/(float)Math.max(1,n("maximum"))));
            Ui.bar(g,x+10,y+19,w-20,6,hp,dead||hp<=.25f?Ui.RED:Ui.CYAN);
            Component due=active?Ui.t("overview.wave",n("wave"),n("waves"),n("attackers"),n("defenders")):Ui.t("overview.next",String.format(java.util.Locale.ROOT,"%.1f",state.getLong("untilRaid")/24000.0));
            Ui.text(g,font,due,x+10,y+31,Ui.INK,w-20);
            if(roomy){
                boolean hard=on("nextHard")||on("hardRaid");
                Component kind=active?(on("hardRaid")?Ui.t("overview.hard_active"):Ui.t("overview.normal_active",n("veteranPercent"))):Ui.t("overview.upcoming",n("raidsStarted")+1,hard?Ui.t("overview.kind_hard"):Ui.t("overview.kind_normal"));
                String shown=active?state.getString("raidType"):state.getString("nextRaidType");
                if(RaidTypes.special(shown))kind=kind.copy().append(" | ").append(Ui.t("raidtype."+shown));
                Ui.text(g,font,kind,x+10,y+44,RaidTypes.special(shown)?Ui.ORANGE:hard?Ui.BRASS:Ui.MUTED,w-20);
            }
            y+=statusH+4;
            int live=n("liveScore"),tier=n("prospectiveTier"),goal=n("nextScore"),previous=Math.max(0,goal-500);
            Ui.card(g,x,y,w,58,Ui.CYAN);
            Ui.text(g,font,Ui.t("overview.payout",tier),x+10,y+5,Ui.CYAN,w-130);
            Ui.right(g,font,Ui.t("overview.paid",n("tier")),x+w-8,y+5,Ui.MUTED);
            float progress=goal==0?1:Math.max(0,Math.min(1,(live-previous)/(float)Math.max(1,goal-previous)));
            Ui.bar(g,x+10,y+18,w-20,8,progress,Ui.CYAN);
            Component score=goal==0?(tier==10?Ui.t("overview.score_max",live):Ui.t("overview.score_full",live)):Ui.t("overview.score",live,goal,Math.max(0,goal-live),tier+1);
            Ui.text(g,font,score,x+10,y+30,Ui.INK,w-20);
            Ui.text(g,font,Ui.t("overview.formula",n("buildingTier"),n("logistics"),n("nextRaidBonus"),n("raidLimit"),tier),x+10,y+43,Ui.MUTED,w-20);
            y+=62;
            int bottom=top+ph-52;
            if(bottom-y>=68){
                var parts=state.getCompound("scoreBreakdown");
                Component status=on("surveyBusy")?Ui.t("overview.survey",n("surveyPercent")):on("surveyIncomplete")?Ui.t("overview.partial"):Ui.t("overview.live");
                Ui.text(g,font,Ui.t("overview.breakdown"),x+2,y+2,Ui.MUTED,w/3);
                Ui.right(g,font,status,x+w-2,y+2,Ui.MUTED);
                String[] keys={"structure","palette","details","lighting","furnishings","layout","factory","platforms"};
                int cell=w/4;
                for(int i=0;i<keys.length;i++){
                    int cx=x+(i%4)*cell,cy=y+15+(i/4)*24;
                    g.fill(cx,cy,cx+cell-3,cy+22,Ui.DEEP);g.fill(cx,cy,cx+2,cy+22,Ui.CYAN_DIM);
                    Ui.text(g,font,Ui.t("score."+keys[i]),cx+7,cy+3,Ui.MUTED,cell-14);
                    Ui.text(g,font,Component.literal(Integer.toString(parts.getInt(keys[i]))),cx+7,cy+13,Ui.INK,cell-14);
                }
                y+=15+48+3;
            }
            if(bottom-y>=10)Ui.text(g,font,on("expansionBlocked")?Ui.t("overview.blocked"):Ui.t("overview.cover"),x+2,y+1,on("expansionBlocked")?Ui.ORANGE:Ui.MUTED,w-4);
            claim.active=on("installed")&&on("near")&&(n("rewards")>0||on("rewardChest"));
            repair.active=ready()&&n("health")<n("maximum");
        }
        // ---- Upgrades: "What should I improve next?" -----------------------------------------
        private List<Component> upgradeCards(GuiGraphics g,int mx,int my,int x,int w){
            int cw=(w-6)/2,stride=(ph-54-26)/3,ch=stride-4;List<Component> tooltip=null;boolean creative=on("creative");
            for(int i=0;i<BRANCHES.length;i++){
                String branch=BRANCHES[i];int cx=x+(i%2)*(cw+6),cy=contentTop()+(i/2)*stride;
                int grade=n(branch),max=Rules.branchMaximum(branch),cost=Economy.beaconAmount(grade),have=n("stock_"+(Economy.standalone()?"ardent_energy":PARTS[i]));boolean complete=grade>=max,short_=!complete&&!creative&&have<cost;
                Ui.card(g,cx,cy,cw,ch,complete?Ui.BRASS:Ui.CYAN);
                Ui.text(g,font,Ui.t("upgrade."+branch+".name"),cx+9,cy+3,Ui.INK,cw-24-Ui.pipsWidth(max));
                Ui.pips(g,cx+cw-8-Ui.pipsWidth(max),cy+4,grade,max,complete?Ui.BRASS:Ui.CYAN);
                int rowY=cy+ch-21;
                Component effect=complete?completeText(i,grade):nextText(i,grade,max);
                int lines=Math.max(1,(rowY-(cy+14)-1)/11),textY=cy+14;
                if(lines>=3&&!complete){Ui.text(g,font,nowText(i,grade),cx+9,textY,Ui.MUTED,cw-16);textY+=13;lines--;}
                Ui.wrap(g,font,effect,cx+9,textY,cw-16,complete?Ui.MUTED:Ui.INK,lines);
                var icon=Economy.standalone()?new ItemStack(ArsenalBeacon.ARDENT_ENERGY.get()):part(i);g.renderItem(icon,cx+8,rowY+1);icons.add(new Hover(icon,cx+8,rowY+1));
                Ui.text(g,font,complete?Ui.t("upgrade.stock",have):Ui.t("upgrade.have",have,cost),cx+28,rowY+6,complete?Ui.MUTED:short_?Ui.ORANGE:Ui.INK,cw-28-92);
                Ui.UiButton b=(Ui.UiButton)upgrades.get(i);
                b.setPosition(cx+cw-88,rowY);b.setWidth(82);
                b.setMessage(complete?Ui.t("upgrade.complete"):creative?Ui.t("upgrade.free"):short_?Ui.t("upgrade.short",cost-have):Ui.t("upgrade.buy",cost));
                b.warning=short_;b.look=complete?Ui.Look.NORMAL:Ui.Look.PRIMARY;b.active=ready()&&!complete;
                if(Ui.inside(mx,my,cx,cy,cw,ch)&&!Ui.inside(mx,my,cx+8,rowY+1,16,16)&&tooltip==null){
                    tooltip=new ArrayList<>(List.of(Ui.t("upgrade."+branch+".name"),effect));
                    if(complete)tooltip.add(Ui.t("upgrade.branch_complete"));
                    else{upgradeHoverCosts=Ui.singleCost(icon,cost,have);tooltip.add(Ui.edition("upgrade.craft"));}
                }
            }
            return tooltip;
        }
        private static Component nextText(int branch,int grade,int max){
            int next=Math.min(max,grade+1);
            return switch(BRANCHES[branch]){
                case "core" -> Ui.t("upgrade.core.next",next+1,Rules.radius(next),Rules.maximumHealth(next));
                case "defense" -> Ui.t("upgrade.defense.next",Rules.defensePercent(next));
                case "restoration" -> Ui.t("upgrade.restoration.next",Rules.healingPercent(next));
                case "vertical" -> Ui.t("upgrade.vertical.next",Rules.below(next),Rules.above(next));
                default -> Ui.t("upgrade."+BRANCHES[branch]+".next");
            };
        }
        private static Component nowText(int branch,int grade){
            return switch(BRANCHES[branch]){
                case "core" -> Ui.t("upgrade.core.now",grade+1,Rules.radius(grade),Rules.maximumHealth(grade));
                case "logistics" -> Ui.t("upgrade.logistics.now",grade);
                case "defense" -> Ui.t("upgrade.defense.now",Rules.defensePercent(grade));
                case "restoration" -> Ui.t("upgrade.restoration.now",Rules.healingPercent(grade));
                case "vertical" -> Ui.t("upgrade.vertical.now",Rules.below(grade),Rules.above(grade));
                default -> Ui.t(grade>0?"upgrade.reconnaissance.now_on":"upgrade.reconnaissance.now_off");
            };
        }
        private static Component completeText(int branch,int grade){
            return switch(BRANCHES[branch]){
                case "core" -> Ui.t("upgrade.core.done",grade+1,Rules.radius(grade),Rules.maximumHealth(grade));
                case "defense" -> Ui.t("upgrade.defense.done",Rules.defensePercent(grade));
                case "restoration" -> Ui.t("upgrade.restoration.done",Rules.healingPercent(grade));
                case "vertical" -> Ui.t("upgrade.vertical.done",Rules.below(grade),Rules.above(grade));
                default -> Ui.t("upgrade."+BRANCHES[branch]+".done");
            };
        }
        // ---- Raid break: "Can I take a break or fight now?" ----------------------------------
        private void raidTab(GuiGraphics g,int x,int w){
            var lay=raidLayout();int y=contentTop();
            Ui.card(g,x,y,w,lay.cardH,Ui.CYAN);
            Ui.text(g,font,Ui.t("raid.level_title"),x+10,y+5,Ui.CYAN,w-20);
            Component level=Ui.t("raid.level",n("raidLimit"));
            g.pose().pushPose();g.pose().translate(x+w/2f,lay.levelY+4,0);g.pose().scale(1.5f,1.5f,1);
            g.drawString(font,level.getVisualOrderText(),-font.width(level)/2,0,Ui.INK,false);g.pose().popPose();
            Ui.text(g,font,Ui.t("raid.payout",n("prospectiveTier")),x+10,lay.levelY+26,Ui.INK,w-20);
            if(lay.roomy())Ui.wrap(g,font,Ui.t("raid.level_note"),x+10,lay.levelY+38,w-20,Ui.MUTED,2);
            int by=lay.breakY;int breakH=Math.min(top+ph-52-by,lay.roomy()?84:58);
            Ui.card(g,x,by,w,breakH,Ui.BRASS);
            int pauseDays=(int)Math.ceil(state.getLong("respiteTicks")/24000.0);
            Ui.text(g,font,Ui.t("raid.break_title"),x+10,by+5,Ui.BRASS,w-20);
            Ui.text(g,font,Ui.t("raid.break",pauseDays,n("nextRaidBonus")),x+10,by+17,Ui.INK,w-20);
            var costs=state.getList("respiteCosts",net.minecraft.nbt.Tag.TAG_COMPOUND);
            Component priceLabel=Ui.t("raid.price");
            int labelWidth=Math.min(70,font.width(priceLabel)+8);
            Ui.text(g,font,priceLabel,x+10,by+32,Ui.MUTED,labelWidth);
            int slot=(w-20-labelWidth)/Math.max(3,costs.size());
            for(int i=0;i<costs.size();i++){
                var row=costs.getCompound(i);var item=ItemStack.of(row.getCompound("item"));int cx=x+10+labelWidth+i*slot,cy=by+28;
                g.renderItem(item,cx,cy);icons.add(new Hover(item,cx,cy));
                Ui.text(g,font,Ui.t("raid.cost",row.getInt("have"),row.getInt("count")),cx+19,cy+4,row.getInt("have")>=row.getInt("count")?Ui.INK:Ui.ORANGE,slot-22);
            }
            if(lay.roomy()&&breakH>=76)Ui.wrap(g,font,Ui.t("raid.note"),x+10,by+50,w-20,Ui.MUTED,2);
            boolean controls=ready()&&state.getString("phase").equals("preparation")&&on("introCompleted");
            respite.active=startRaid.active=controls;lowerTier.active=controls&&n("raidLimit")>0;higherTier.active=controls&&n("raidLimit")<10;
        }
        // ---- Settings: "What is protected?" ---------------------------------------------------
        private void settingsTab(GuiGraphics g,int x,int w){
            int gap=settingsGap(),y=contentTop()+2;boolean here=on("installed")&&on("near");
            String[] keys={"outline","beam","hurtbox"};Button[] toggles={outline,beam,hurtbox};
            for(int i=0;i<3;i++){
                var b=(Ui.UiButton)toggles[i];b.on=on(keys[i]);b.setMessage(Ui.t("settings."+keys[i]));b.active=here;
                Component note=i==0?Ui.t("settings.outline.note",n("radius"),n("below"),n("above")):Ui.t("settings."+keys[i]+".note");
                Ui.text(g,font,note,x+4,y+gap*i+25,Ui.MUTED,w-8);
            }
            remove.active=on("installed")&&on("controller")&&on("canRemove");
            Ui.wrap(g,font,ph>=300?Ui.t("settings.remove.note"):Ui.t("settings.remove.short"),x+4,y+gap*3+25,w-8,Ui.MUTED,ph>=300?2:1);
        }
        @Override Hover itemAt(double mx,double my){for(var icon:icons)if(Ui.inside(mx,my,icon.x(),icon.y(),16,16))return icon;return null;}
        static String capitalize(String s){return Character.toUpperCase(s.charAt(0))+s.substring(1);}
    }

    static final class ConfirmScreen extends PanelScreen {
        final String kind,token;
        private RichText text;
        ConfirmScreen(String kind,String token){super(Ui.t("confirm.title"));this.kind=kind;this.token=token;}
        private boolean place(){return kind.equals("place");}
        @Override Component subtitle(){return Ui.t("confirm.subtitle");}
        @Override protected void init(){
            super.init();pw=Math.min(460,width-16);
            String raw=place()?Ui.plain("confirm.place.body",state.getBoolean("introCompleted")?Ui.plain("confirm.place.intro_done"):Ui.plain("confirm.place.intro_new")):Ui.plain("confirm.remove.body");
            text=RichText.layout(font,ControlHints.guide(raw),pw-28-10,place()?Ui.CYAN:Ui.RED);
            // Sized to its copy: a short warning should not float in a tall empty panel.
            ph=Math.min(height-16,Math.max(150,34+22+8+text.height+10+44));left=(width-pw)/2;top=(height-ph)/2;
            int half=(pw-36)/2;
            // Cancel comes first and is the default focus so Enter never destroys a campaign.
            var cancel=button(Ui.t("confirm.cancel"),left+14,top+ph-34,half,Ui.Look.PRIMARY,b->onClose());
            button(place()?Ui.t("confirm.place.go"):Ui.t("confirm.remove.go"),left+22+half,top+ph-34,half,place()?Ui.Look.NORMAL:Ui.Look.DANGER,b->BeaconNetwork.action("confirm",token));
            setInitialFocus(cancel);
        }
        @Override public void onClose(){BeaconNetwork.action("cancel",token);super.onClose();}
        @Override public void render(GuiGraphics g,int mx,int my,float partial){
            panel(g);int x=left+14,w=pw-28,y=top+34;
            if(place()){g.fill(x,y,x+w,y+22,Ui.CYAN_FILL);g.fill(x,y+21,x+w,y+22,Ui.CYAN);}
            else Ui.hazard(g,x,y,w,22,0xff2b1214,0xff5c2226);
            Ui.text(g,font,place()?Ui.t("confirm.place.head"):Ui.t("confirm.remove.head"),x+8,y+7,place()?Ui.CYAN:Ui.RED,w-16);
            int top0=y+30,bottom=top+ph-42;
            g.enableScissor(x,top0,x+w,bottom);text.draw(g,font,x+4,top0,0,top0,bottom);g.disableScissor();
            super.render(g,mx,my,partial);footer(g);
        }
    }

    static final class GuideScreen extends PanelScreen {
        static final String[] IDS=GuideText.IDS;
        final Screen parent;int page,scroll;
        private RichText cached;private int cachedPage=-1,cachedWidth;private String cachedLanguage="";
        private int navX,navTop,navWidth,navHeight,textX,textWidth,textTop,textBottom;
        /** True: icon and title on every row. False: a two-column rail of icons whose names show on hover. */
        boolean fullList;
        final List<Ui.UiButton> nav=new ArrayList<>();
        GuideScreen(Screen parent){super(Ui.t("guide.title"));this.parent=parent;}
        @Override Component subtitle(){return Ui.edition("guide.subtitle");}
        static String raw(String id,String part,boolean standalone){return GuideText.raw(id,part,standalone,key->Ui.has(key)?Ui.plain(key):null);}
        private String pageText(){return ControlHints.guide(GuideText.page(IDS[page],BuildFlavor.STANDALONE,key->Ui.has(key)?Ui.plain(key):null));}
        /** The item that stands for each page in the side menu. */
        static ItemStack icon(String id){
            return new ItemStack(switch(id){
                case "start"->ArsenalBeacon.GUIDE.get();
                case "zone"->ArsenalBeacon.BEACON_ITEM.get();
                case "raids"->net.minecraft.world.item.Items.ZOMBIE_HEAD;
                case "control"->ArsenalBeacon.CONTROLLER.get();
                case "upgrades"->ArsenalBeacon.PLATING.get();
                case "repair"->ArsenalBeacon.REPAIR.get();
                case "stations"->ArsenalBeacon.GUN_PLATFORM.get().asItem();
                case "energy"->ArsenalBeacon.ARDENT_ENERGY.get();
                case "support"->ArsenalBeacon.SUPPORT_CANNON_ITEM.get();
                case "kitchen"->ArsenalBeacon.MESS_HALL_I.get().asItem();
                case "mixes"->ArsenalBeacon.SANDWICH.get();
                case "gear"->ArsenalBeacon.RACKS.isEmpty()?net.minecraft.world.item.Items.CROSSBOW:ArsenalBeacon.RACKS.get(0).get().asItem();
                case "coop"->net.minecraft.world.item.Items.PLAYER_HEAD;
                default->net.minecraft.world.item.Items.WRITABLE_BOOK;
            });
        }
        private RichText text(){
            String language=Minecraft.getInstance().getLanguageManager().getSelected();
            if(cached==null||cachedPage!=page||cachedWidth!=textWidth||!cachedLanguage.equals(language)){cached=RichText.layout(font,pageText(),textWidth-10,Ui.BRASS);cachedPage=page;cachedWidth=textWidth;cachedLanguage=language;}
            return cached;
        }
        /** Back/Next, arrow keys and the side menu all end up here, and the menu highlight follows in render(). */
        private void go(int target){page=Math.max(0,Math.min(IDS.length-1,target));scroll=0;cached=null;}
        @Override protected void init(){
            super.init();nav.clear();
            // The guide may be taller than the other panels: 14 rows with full-size icons need the room.
            ph=Math.min(330,height-16);top=(height-ph)/2;
            int rows=IDS.length;
            // The menu is a full-height strip beside the page. It never goes away: when 14 icon-and-title rows do not fit,
            // it becomes a two-column rail of icons and the names move to a tooltip.
            navX=left+10;navTop=top+32;navHeight=ph-40;
            int step=(navHeight-4)/rows;
            fullList=pw>=430&&step>=17;
            navWidth=fullList?142:54;
            textX=navX+navWidth+10;textWidth=left+pw-14-textX;textTop=top+56;textBottom=top+ph-40;
            closeButton();
            for(int i=0;i<rows;i++){
                int index=i;String id=IDS[i];ItemStack icon=icon(id);
                Ui.UiButton b;
                if(fullList)b=new Ui.UiButton(navX+3,navTop+2+i*step,navWidth-6,step-1,Ui.t("guide."+id+".title"),Ui.Look.ROW,x->go(index));
                else{
                    int rowsPerColumn=(rows+1)/2,cell=Math.min(22,(navHeight-4)/rowsPerColumn-2);
                    b=new Ui.UiButton(navX+4+(i/rowsPerColumn)*(cell+2),navTop+3+(i%rowsPerColumn)*(cell+3),cell,cell,Ui.t("guide."+id+".title"),Ui.Look.ROW,x->go(index));
                }
                b.noLabel().painter((g,self,hover)->paintNav(g,self,icon));
                nav.add(addRenderableWidget(b));
            }
            int w=textWidth,side=Math.min(80,(w-82)/2),y=top+ph-30;
            button(Ui.t("back"),textX,y,side,Ui.Look.NORMAL,b->go(page-1));
            button(Ui.t("done"),textX+w/2-35,y,70,Ui.Look.PRIMARY,b->onClose());
            button(Ui.t("next"),textX+w-side,y,side,Ui.Look.NORMAL,b->go(page+1));
        }
        private void paintNav(GuiGraphics g,Ui.UiButton self,ItemStack icon){
            int x=self.getX(),y=self.getY(),w=self.getWidth(),h=self.getHeight();
            if(fullList){
                // Full-size icon when the row is tall enough, otherwise the item at three quarters. A lighter tile keeps dark items readable.
                boolean big=h>=19;int size=big?16:12,ix=x+4,iy=y+(h-size)/2;
                g.fill(ix,iy,ix+size,iy+size,Ui.EDGE);
                g.pose().pushPose();g.pose().translate(ix,iy,0);if(!big)g.pose().scale(0.75f,0.75f,1f);g.renderItem(icon,0,0);g.pose().popPose();
                g.drawString(font,Ui.fit(font,self.getMessage(),w-size-14),ix+size+6,y+(h-8)/2,self.selected?Ui.INK:Ui.MUTED,false);
            }else{
                int ix=x+(w-16)/2,iy=y+(h-16)/2;
                g.fill(ix,iy,ix+16,iy+16,Ui.EDGE);g.renderItem(icon,ix,iy);
            }
        }
        private int maxScroll(){return Math.max(0,text().height-(textBottom-textTop));}
        @Override public boolean mouseScrolled(double x,double y,double amount){scroll=Math.max(0,Math.min(maxScroll(),scroll-(int)(amount*22)));return true;}
        @Override public boolean keyPressed(int key,int scan,int modifiers){
            switch(key){
                case 264 -> {scroll=Math.min(maxScroll(),scroll+22);return true;}
                case 265 -> {scroll=Math.max(0,scroll-22);return true;}
                case 267 -> {scroll=Math.min(maxScroll(),scroll+textBottom-textTop-11);return true;}
                case 266 -> {scroll=Math.max(0,scroll-(textBottom-textTop-11));return true;}
                case 262 -> {if(!(getFocused() instanceof Button)){go(page+1);return true;}}
                case 263 -> {if(!(getFocused() instanceof Button)){go(page-1);return true;}}
            }
            return super.keyPressed(key,scan,modifiers);
        }
        @Override public void onClose(){minecraft.setScreen(parent);}
        @Override public void render(GuiGraphics g,int mx,int my,float partial){
            panel(g);
            scroll=Math.min(scroll,maxScroll());
            for(int i=0;i<nav.size();i++)nav.get(i).selected=i==page;
            Ui.inset(g,navX,navTop,navWidth,navHeight);
            boolean more=text().height>textBottom-textTop&&scroll<maxScroll();
            Ui.text(g,font,more?Ui.t("guide.page_more",page+1,IDS.length):Ui.t("guide.page",page+1,IDS.length),textX,top+34,Ui.CYAN,textWidth-12);
            Ui.text(g,font,Ui.t("guide."+IDS[page]+".title"),textX,top+44,Ui.INK,textWidth-12);
            var text=text();
            g.enableScissor(textX,textTop,textX+textWidth,textBottom);text.draw(g,font,textX+2,textTop+2,scroll,textTop,textBottom);g.disableScissor();
            if(text.height>textBottom-textTop){
                int track=textBottom-textTop,thumb=Math.max(12,track*track/text.height),ty=textTop+(maxScroll()==0?0:(track-thumb)*scroll/maxScroll());
                g.fill(textX+textWidth-3,textTop,textX+textWidth,textBottom,Ui.TRACK);g.fill(textX+textWidth-3,ty,textX+textWidth,ty+thumb,Ui.CYAN);
            }
            super.render(g,mx,my,partial);
            if(!fullList)for(var b:nav)if(b.isHovered()){g.renderTooltip(font,b.getMessage(),mx,my);break;}
        }
    }
}
