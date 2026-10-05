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

/** Client presentation only. Every purchase and confirmation is validated on the server. */
@Mod.EventBusSubscriber(modid=ArsenalBeacon.ID,value=Dist.CLIENT)
public final class BeaconClient {
    private static CompoundTag state=new CompoundTag();
    private static long receivedAt;
    private static final int INK=0xffedf5f8,MUTED=0xff9fb3bc,CYAN=0xff5ae2df,PANEL=0xf2111e28,CARD=0xff1c303d;
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
    private static boolean current(){var mc=Minecraft.getInstance();return mc.level!=null&&mc.level.dimension()==Level.OVERWORLD&&mc.level.getGameTime()-receivedAt<80&&state.getBoolean("installed");}
    private static boolean holding(){var p=Minecraft.getInstance().player;return p!=null&&(p.getMainHandItem().is(ArsenalBeacon.CONTROLLER.get())||p.getOffhandItem().is(ArsenalBeacon.CONTROLLER.get()));}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){state=new CompoundTag();receivedAt=0;BeaconStartup.clear();BeaconAlerts.clear();GunPackSync.clearReceiving();}
    @Mod.EventBusSubscriber(modid=ArsenalBeacon.ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
    public static final class Registration {
        @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e){e.registerBlockEntityRenderer(ArsenalBeacon.BEACON_ENTITY.get(),Beam::new);e.registerEntityRenderer(ArsenalBeacon.OBJECTIVE.get(),net.minecraft.client.renderer.entity.NoopRenderer::new);}
        @SubscribeEvent public static void overlays(RegisterGuiOverlaysEvent e){e.registerAboveAll("beacon_status",(gui,g,partial,w,h)->hud(g,w,h));}
    }
    private static void hud(GuiGraphics g,int w,int h){
        var mc=Minecraft.getInstance();if(mc.options.hideGui||mc.screen!=null||!current()||!Rules.showHud(state.getString("phase"),holding(),state.getBoolean("near")))return;
        int x=8,y=h-84,width=156;g.fill(x,y,x+width,y+49,PANEL);g.fill(x,y,x+3,y+49,CYAN);
        g.drawString(mc.font,"DEFENSE BEACON",x+9,y+7,CYAN,false);
        g.drawString(mc.font,state.getInt("health")+" / "+state.getInt("maximum")+" HP",x+9,y+19,INK,false);
        g.drawString(mc.font,state.getString("phase").equals("raid")?"Wave "+state.getInt("wave")+"/"+state.getInt("waves")+"  |  Mobs "+state.getInt("attackers"):label(state.getString("phase")),x+9,y+31,MUTED,false);
        int fill=(int)(Math.max(0,Math.min(1,state.getInt("health")/(float)Math.max(1,state.getInt("maximum"))))*(width-18));
        g.fill(x+9,y+43,x+width-9,y+45,0xff354c59);g.fill(x+9,y+43,x+9+fill,y+45,state.getInt("health")>0?CYAN:0xffed7369);
    }
    static String label(String phase){return switch(phase){case "preparation"->"Preparing";case "raid"->"Raid active";case "disabled"->"Disabled - repair needed";case "restore"->"Repairing damage";case "snapshot"->"Saving base";case "decommissioning"->"Finishing recovery";default->"Not planted";};}
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
        var box=ArsenalStructures.hurtbox(BlockPos.of(state.getLong("beacon")),state.getInt("mk"));var camera=event.getCamera().getPosition();var pose=event.getPoseStack();pose.pushPose();pose.translate(-camera.x,-camera.y,-camera.z);var buffers=Minecraft.getInstance().renderBuffers().bufferSource();LevelRenderer.renderLineBox(pose,buffers.getBuffer(RenderType.lines()),box.inflate(.003),1f,.12f,.16f,.9f);buffers.endBatch(RenderType.lines());pose.popPose();
    }
    @SubscribeEvent public static void outline(RenderLevelStageEvent e){
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS||!current()||!state.getBoolean("near")||!state.getBoolean("outline"))return;
        var mc=Minecraft.getInstance();BlockPos pos=BlockPos.of(state.getLong("beacon"));var camera=e.getCamera().getPosition();
        PoseStack pose=e.getPoseStack();pose.pushPose();pose.translate(pos.getX()-camera.x,pos.getY()-camera.y,pos.getZ()-camera.z);
        var buffers=mc.renderBuffers().bufferSource();VertexConsumer lines=buffers.getBuffer(RenderType.lines());var transform=pose.last();double r=state.getInt("radius");
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
        PanelScreen(String title){super(Component.literal(title));}
        @Override protected void init(){pw=Math.min(520,width-16);ph=Math.min(290,height-16);left=(width-pw)/2;top=(height-ph)/2;}
        @Override public boolean isPauseScreen(){return false;}
        void panel(GuiGraphics g){renderBackground(g);g.fill(left,top,left+pw,top+ph,PANEL);g.fill(left,top,left+pw,top+3,CYAN);g.drawString(font,title,left+14,top+13,INK,false);}
        void text(GuiGraphics g,String text,int x,int y,int color){g.drawString(font,text,x,y,color,false);}
        void wrap(GuiGraphics g,String text,int x,int y,int w,int color){for(var row:font.split(Component.literal(text),w)){g.drawString(font,row,x,y,color,false);y+=11;}}
        Button button(String label,int x,int y,int w,java.util.function.Consumer<Button> click){return addRenderableWidget(Button.builder(Component.literal(label),click::accept).bounds(x,y,w,20).build());}
        void footer(GuiGraphics g){if(!message.isEmpty())wrap(g,message,left+14,top+ph-29,pw-28,CYAN);}
        Hover itemAt(double x,double y){return null;}
    }
    record Hover(ItemStack item,int x,int y){}
    static final class ControlScreen extends PanelScreen {
        int tab;Button claim,repair,outline,beam,hurtbox,remove,respite,startRaid,lowerTier,higherTier;final java.util.List<Button> upgrades=new java.util.ArrayList<>();
        static final String[] BRANCHES={"core","logistics","defense","restoration","reconnaissance","vertical"};
        static final String[] PARTS={"reinforced_plating","logistics_module","resonance_coil","restoration_matrix","resonance_coil","logistics_module"};
        static final String[] EFFECTS={"Next Mk / larger protected zone","Better rewards / raids sooner","Less damage to beacon","Faster repairs / more healing","Reveal attackers instantly","Expand zone height, not HP"};
        ControlScreen(){super("DEFENSE BEACON");}
        @Override protected void init(){
            super.init();ph=Math.min(340,height-16);top=(height-ph)/2;upgrades.clear();claim=repair=outline=beam=hurtbox=remove=respite=startRaid=lowerTier=higherTier=null;int tw=(pw-28)/4;
            for(int i=0;i<4;i++){int page=i;String name=new String[]{"Overview","Upgrades","Raid break","Settings"}[i];button(i==tab?"[ "+name+" ]":name,left+14+i*tw,top+34,tw-3,b->{tab=page;rebuildWidgets();});}
            button("X",left+pw-34,top+8,20,b->onClose());
            button("Guide",left+pw-102,top+8,62,b->BeaconNetwork.action("guide",""));
            if(tab==0){
                int bw=(pw-34)/3,y=top+ph-45;
                claim=button("Reward chest",left+14,y,bw,b->BeaconNetwork.action("claim",""));
                repair=button("Repair: 8 plating",left+17+bw,y,bw,b->BeaconNetwork.action("repair",""));
                button("Rewards & tiers",left+20+bw*2,y,bw,b->BeaconNetwork.action("rewards",""));
            }else if(tab==1){
                int cw=(pw-34)/2,ch=(ph-99)/3;
                for(int i=0;i<BRANCHES.length;i++){String branch=BRANCHES[i];int x=left+14+(i%2)*(cw+6),y=top+65+(i/2)*ch;
                    upgrades.add(button("Upgrade",x+cw-133,y+ch-24,129,b->BeaconNetwork.action("upgrade:"+branch,"")));
                }
            }else if(tab==2){
                int half=(pw-34)/2;
                lowerTier=button("Lower level",left+14,top+112,half,b->BeaconNetwork.action("raid-level:"+Math.max(0,state.getInt("raidLimit")-1),""));
                higherTier=button("Raise level",left+20+half,top+112,half,b->BeaconNetwork.action("raid-level:"+Math.min(10,state.getInt("raidLimit")+1),""));
                respite=button("Buy five active days",left+14,top+ph-52,half,b->BeaconNetwork.action("respite",""));
                startRaid=button("Start raid now",left+20+half,top+ph-52,half,b->BeaconNetwork.action("start-raid",""));
                startRaid.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Starts at the displayed level after saving the base. Ends purchased break time. Rewards match that level; every third raid still has a boss.")));
                respite.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Each purchase adds five online days, costs more, and adds one next-raid tier (cap +3 / tier 10). The selected raid cap also limits the bonus. Ambient enemies remain active.")));
            }else{
                int gap=Math.min(47,Math.max(26,(ph-96)/4));
                outline=button("",left+14,top+66,pw-28,b->BeaconNetwork.action("outline",""));
                beam=button("",left+14,top+66+gap,pw-28,b->BeaconNetwork.action("beam",""));
                hurtbox=button("",left+14,top+66+gap*2,pw-28,b->BeaconNetwork.action("hurtbox",""));
                remove=button("Remove & reset campaign...",left+14,top+66+gap*3,pw-28,b->BeaconNetwork.action("remove",""));
            }
        }
        @Override public void render(GuiGraphics g,int mx,int my,float partial){
            panel(g);boolean ready=state.getBoolean("installed")&&state.getBoolean("near")&&!state.getBoolean("active");
            if(tab==0){
                int y=top+65;boolean expanded=ph>=310;int live=state.getInt("liveScore"),tier=state.getInt("prospectiveTier"),goal=state.getInt("nextScore"),previous=Math.max(0,goal-500);
                text(g,"Mk-"+state.getInt("mk")+" | "+label(state.getString("phase"))+" | HP "+state.getInt("health")+" / "+state.getInt("maximum"),left+14,y,state.getInt("health")==0?0xffed7369:CYAN);
                String due=state.getBoolean("active")?"Wave "+state.getInt("wave")+" / "+state.getInt("waves")+" | Attackers: "+state.getInt("attackers")+" | Defenders: "+state.getInt("defenders"):"Next raid: "+String.format(java.util.Locale.ROOT,"%.1f",state.getLong("untilRaid")/24000.0)+" active days";
                text(g,due,left+14,y+15,INK);
                text(g,"Next payout: Tier "+tier+" / 10  |  Last paid: Tier "+state.getInt("tier"),left+14,y+30,CYAN);
                int by=y+44,bw=pw-28;g.fill(left+14,by,left+14+bw,by+8,0xff354c59);int fill=goal==0?bw:(int)(bw*Math.max(0,Math.min(1,(live-previous)/(float)Math.max(1,goal-previous))));g.fill(left+14,by,left+14+fill,by+8,CYAN);
                String scoreLine=goal==0?"Score "+live+" | Building 5/5"+(tier==10?" | Maximum tier":" | Upgrade Logistics for more"):"Score: "+live+" / "+goal+" | Need "+Math.max(0,goal-live)+" for Tier "+(tier+1);
                text(g,font.plainSubstrByWidth(scoreLine,pw-28),left+14,y+57,INK);
                text(g,"Building "+state.getInt("buildingTier")+"/5 + Logistics "+state.getInt("logistics")+"/5 + Break "+state.getInt("nextRaidBonus")+" | Cap "+state.getInt("raidLimit")+" = Tier "+tier,left+14,y+70,MUTED);
                if(expanded){text(g,state.getBoolean("surveyBusy")?"Surveying loaded base: "+state.getInt("surveyPercent")+"%":state.getBoolean("surveyIncomplete")?"Some chunks are unloaded; preview is partial":"Live base preview; raid snapshot fixes the payout",left+14,y+86,MUTED);var parts=state.getCompound("scoreBreakdown");text(g,"Build "+parts.getInt("structure")+" | Palette "+parts.getInt("palette")+" | Details "+parts.getInt("details")+" | Lighting "+parts.getInt("lighting"),left+14,y+103,MUTED);text(g,"Furnishings "+parts.getInt("furnishings")+" | Layout "+parts.getInt("layout")+" | Factory "+parts.getInt("factory")+" | Platforms "+parts.getInt("platforms"),left+14,y+116,MUTED);}
                text(g,state.getBoolean("expansionBlocked")?"Mk-4 needs clear 3 x 3 space, two blocks tall":"Cover your beacon from hostile mobs",left+14,y+(expanded?133:83),MUTED);
                if(ph>=230)text(g,state.getBoolean("active")?(state.getBoolean("hardRaid")?"HARD RAID — boss in final wave":"Normal raid | "+state.getInt("defenders")+" defender(s) | Veterans +"+state.getInt("veteranPercent")+"%"):("Next: Raid #"+(state.getInt("raidsStarted")+1)+(state.getBoolean("nextHard")?" — HARD RAID + BOSS":" — normal raid")),left+14,y+(expanded?148:96),state.getBoolean("nextHard")||state.getBoolean("hardRaid")?0xffffa36c:INK);
                claim.active=state.getBoolean("installed")&&state.getBoolean("near")&&(state.getInt("rewards")>0||state.getBoolean("rewardChest"));
                repair.active=ready&&state.getInt("health")<state.getInt("maximum");
            }else if(tab==1){
                int cw=(pw-34)/2,ch=(ph-99)/3;
                for(int i=0;i<BRANCHES.length;i++){
                    int x=left+14+(i%2)*(cw+6),y=top+65+(i/2)*ch,grade=state.getInt(BRANCHES[i]),max=Rules.branchMaximum(BRANCHES[i]),cost=Rules.upgradeCost(grade);
                    g.fill(x,y,x+cw,y+ch-5,CARD);text(g,font.plainSubstrByWidth(capitalize(BRANCHES[i])+" "+grade+"/"+max,cw-137),x+6,y+5,INK);
                    if(ch>=58)text(g,font.plainSubstrByWidth(i==0?"Next Mk: radius "+Rules.radius(grade+1)+" | HP "+Rules.maximumHealth(grade+1):EFFECTS[i],cw-137),x+6,y+17,MUTED);
                    var icon=new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(new ResourceLocation(ArsenalBeacon.ID,PARTS[i])));
                    g.renderItem(icon,x+cw-51,y+ch-43);
                    Button b=upgrades.get(i);String effect=i==2?" | -"+Rules.defensePercent(Math.min(max,grade+1))+"% dmg":i==3?" | heal "+Rules.healingPercent(Math.min(max,grade+1))+"%":i==5?" | -"+Rules.below(Math.min(max,grade+1))+"/+"+Rules.above(Math.min(max,grade+1)):"";b.setMessage(Component.literal(grade>=max?"Complete"+(i==2?" | -"+Rules.defensePercent(grade)+"% dmg":i==3?" | heal "+Rules.healingPercent(grade)+"%":""):(state.getBoolean("creative")?"Free":"Buy: "+cost)+effect));b.active=ready&&grade<max;
                    if(ch>=53)text(g,"Have "+state.getInt("stock_"+PARTS[i]),x+6,y+ch-20,MUTED);
                }
                if(!ready&&message.isEmpty())text(g,"Upgrade near the beacon between raids.",left+14,top+ph-25,MUTED);
            }else if(tab==2){
                text(g,"RAID LEVEL / SHARED TEAM",left+14,top+66,CYAN);
                int pauseDays=(int)Math.ceil(state.getLong("respiteTicks")/24000.0);
                text(g,"Selected cap: "+state.getInt("raidLimit")+" / 10 | Next raid & payout: "+state.getInt("prospectiveTier"),left+14,top+82,INK);
                text(g,"Lower levels keep upgrades; rewards match the chosen fight.",left+14,top+98,MUTED);
                text(g,"Break: "+pauseDays+" days | Bonus: +"+state.getInt("nextRaidBonus")+" (within cap)",left+14,top+141,INK);
                var costs=state.getList("respiteCosts",net.minecraft.nbt.Tag.TAG_COMPOUND);
                for(int i=0;i<costs.size();i++){var row=costs.getCompound(i);var item=ItemStack.of(row.getCompound("item"));int cy=top+161,cx=left+14+i*(pw-28)/3;g.renderItem(item,cx,cy);text(g,row.getInt("have")+" / "+row.getInt("count"),cx+21,cy+4,row.getInt("have")>=row.getInt("count")?INK:0xffffa36c);}
                if(ph>=310)wrap(g,"Buy breaks for expeditions, or start now to farm this level. Starting ends the break. Time counts only while someone is online.",left+14,top+203,pw-28,MUTED);
                boolean controls=ready&&state.getString("phase").equals("preparation")&&state.getBoolean("introCompleted");respite.active=startRaid.active=controls;lowerTier.active=controls&&state.getInt("raidLimit")>0;higherTier.active=controls&&state.getInt("raidLimit")<10;
            }else{
                int gap=Math.min(47,Math.max(26,(ph-96)/4));
                outline.setMessage(Component.literal("3D base outline: "+(state.getBoolean("outline")?"ON":"OFF")));outline.active=state.getBoolean("installed")&&state.getBoolean("near");
                if(gap>=42)wrap(g,"Protected zone: "+state.getInt("below")+" blocks below / "+state.getInt("above")+" above.",left+14,top+92,pw-28,MUTED);
                beam.setMessage(Component.literal("Beacon beam: "+(state.getBoolean("beam")?"ON":"OFF")));beam.active=state.getBoolean("installed")&&state.getBoolean("near");
                if(gap>=42)text(g,"Visible sky marker. Shared setting for the whole team.",left+14,top+66+gap+26,MUTED);
                hurtbox.setMessage(Component.literal("Red beacon damage outline: "+(state.getBoolean("hurtbox")?"ON":"OFF")));hurtbox.active=state.getBoolean("installed")&&state.getBoolean("near");
                if(gap>=42)text(g,"Shows the actual attackable beacon volume.",left+14,top+66+gap*2+26,MUTED);
                remove.active=state.getBoolean("installed")&&state.getBoolean("controller")&&state.getBoolean("canRemove");
                wrap(g,ph>=300?"Hold the recovery shovel within 8 blocks to remove. This stops raids and erases all upgrades. A confirmation appears first.":"Recovery shovel required. Reset asks for confirmation.",left+14,top+66+gap*3+26,pw-28,MUTED);
            }
            super.render(g,mx,my,partial);footer(g);
            Hover hovered=itemAt(mx,my);if(hovered!=null)g.renderComponentTooltip(font,java.util.List.of(hovered.item.getHoverName(),Component.literal(ControlHints.jei())),mx,my);
            if(hovered==null&&tab==1)for(int i=0;i<upgrades.size();i++){
                Button button=upgrades.get(i);
                if(mx<button.getX()||mx>=button.getX()+button.getWidth()||my<button.getY()||my>=button.getY()+button.getHeight())continue;
                boolean complete=state.getInt(BRANCHES[i])>=Rules.branchMaximum(BRANCHES[i]);
                g.renderComponentTooltip(font,java.util.List.of(Component.literal(EFFECTS[i]),Component.literal(complete?"Branch complete":"Need "+Rules.upgradeCost(state.getInt(BRANCHES[i]))+" "+PARTS[i].replace('_',' ')),Component.literal("Make with Create. See JEI.")),mx,my);
                break;
            }
        }
        @Override Hover itemAt(double mx,double my){
            if(tab==2){var costs=state.getList("respiteCosts",net.minecraft.nbt.Tag.TAG_COMPOUND);for(int i=0;i<costs.size();i++){int x=left+14,y=top+119+i*22;if(mx>=x&&mx<x+16&&my>=y&&my<y+16)return new Hover(ItemStack.of(costs.getCompound(i).getCompound("item")),x,y);}return null;}
            if(tab!=1)return null;int cw=(pw-34)/2,ch=(ph-99)/3;
            for(int i=0;i<BRANCHES.length;i++){int x=left+14+(i%2)*(cw+6)+cw-51,y=top+65+(i/2)*ch+ch-43;
                if(mx>=x&&mx<x+16&&my>=y&&my<y+16)return new Hover(new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(new ResourceLocation(ArsenalBeacon.ID,PARTS[i]))),x,y);
            }return null;
        }
        static String capitalize(String s){return Character.toUpperCase(s.charAt(0))+s.substring(1);}
    }
    static final class ConfirmScreen extends PanelScreen {
        final String kind,token;
        ConfirmScreen(String kind,String token){super("ARE YOU SURE ABOUT THIS?");this.kind=kind;this.token=token;}
        @Override protected void init(){super.init();button("Cancel",left+14,top+ph-50,(pw-34)/2,b->onClose());button(kind.equals("place")?"Plant beacon":"Remove & reset",left+20+(pw-34)/2,top+ph-50,(pw-34)/2,b->BeaconNetwork.action("confirm",token));}
        @Override public void onClose(){BeaconNetwork.action("cancel",token);super.onClose();}
        @Override public void render(GuiGraphics g,int mx,int my,float partial){
            panel(g);text(g,kind.equals("place")?"START THE SHARED CAMPAIGN":"RESET THE SHARED CAMPAIGN",left+14,top+43,kind.equals("place")?CYAN:0xffed7369);
            String explanation=kind.equals("place")?"This starts raids for your whole team.\n\n"+(state.getBoolean("introCompleted")?"Introduction already complete. Scheduled raids resume after seven active days.":"First raid: immediately after planting, fixed at tier 0. Win it to earn the Weapon Platform. Later raids start weekly.")+" Every third started raid is a boss raid with a bonus cache. Richer rewards make attacks more frequent, down to one every two days.\n\nBuild factories and defenses inside the cyan zone. The recovery shovel can pack the beacon up later.":"Stops raids immediately.\n\nALL upgrades, victories and reward levels reset to 0. Unclaimed rewards are discarded.\n\nYour base remains. Tracked damage finishes recovery without refunding spent ammo. You get one fresh beacon back.";
            wrap(g,explanation,left+14,top+65,pw-28,INK);super.render(g,mx,my,partial);footer(g);
        }
    }
    static final class GuideScreen extends PanelScreen {
        final Screen parent;int page,scroll;
        static final String[] TITLES={"01 / START HERE","02 / YOUR BASE ZONE","03 / DEFEND & COLLECT","04 / FACTORY UPGRADES","05 / REPAIR OR PACK UP","06 / CO-OP & CONTROLS","07 / UNIVERSAL PLATFORMS"};
        static final String[] PAGES={
            "Right-click a beacon item on the ground. Read the confirmation, then plant it. There is one shared beacon per world.\n\nPlanting starts an introductory tier-0 raid immediately. Win it and claim your Weapon Platform. Later raids wait seven ACTIVE Minecraft days. Nobody online? The timer pauses.\n\nRight-click the beacon for Overview, Upgrades and Settings. No commands needed.",
            "Ammo Coins are earned only by completing raids. At the ammo table, Buy with coins purchases the shown batch instead of its material costs. Compatibility and Age/Create locks still apply in Survival. Cost scales with Age and the strongest compatible gun damage, including pellets and explosives.\n\nSettings > 3D base outline shows an exact square of blocks. The cyan lines sit on the OUTER faces of included blocks. One-block ticks help you count.\n\nThe initial footprint is 17 x 17 blocks: 8 blocks each direction plus the beacon block. Three Core upgrades give Mk-2, Mk-3 and Mk-4, with radii 12, 18 and 24. Mk-4 needs a clear 3 x 3 footprint, two blocks tall. Settings toggles a separate red outline showing the actual attackable beacon volume. Height starts three below to eight above. Vertical Zone upgrades independently expand it to sixteen below and forty-eight above, without changing the model or HP. Corners count too.\n\nAll newly placed building materials earn equal construction credit. A beautiful base can earn palette, detail, lighting, furnishing and usable-room bonuses. Naturally generated terrain earns no bulk construction credit. Existing crafted blocks are recognized; occupied covered rooms also credit raw stone/dirt. No extra points for expensive ore blocks. Rewards & tiers previews exact shared payouts, including wave iron and hard-raid bonuses.\n\nBuild Create machines, turrets and Universal Platforms inside. Higher station Ages boost score. Only the highest Age of each of the four station types counts. Core upgrades widen the zone. The moving grid covers all four walls, the ceiling and the floor.",
            "Cover your beacon from hostile mobs. Ambient enemies need line of sight and must be within 24 blocks to detect it. Raid attackers know its location and can breach doors or walls. Build walls and defenses around the beacon. Every third started raid is a hard raid with 25% more attackers and a modded boss in its final wave. A red boss bar tracks its health. Defeat it for 50% more completion resources plus bonus medical kits and grenades. Defeats still count toward the three-raid cadence. Scheduled raids arrive at night; the introduction starts immediately. Protect the beacon and kill every attacker. Remaining attackers glow after one minute; Reconnaissance makes them glow immediately.\n\nThree defender deaths nearby cost the beacon health. Ordinary enemies have bounded health and no armor; specialists have light armor and marked heavies are uncommon. Extra nearby Survival/Adventure defenders increase reinforcement counts by 65% each, not ordinary enemy health. The count locks at each wave and is shown in Overview. Solo alive-enemy limits are at most 24; co-op limits are at most 48. Spectators, Creative players and distant players do not raise the count. Veteran victories add at most 30% reinforcements, with no hidden health growth. Boss health adds 45% per extra defender, capped at four defenders. Special Forces begin at reward tier 4; solo tiers 4-6 contain one soldier per wave, with heavies starting at tier 7. When warned, get behind sandbags for a shootout.\n\nOverview > Reward chest creates a shared double chest on clear ground inside the base zone. Its 54 slots keep supplies out of your carried inventory. Collect again to refill it after taking supplies. Overflow stays queued at the beacon, including across world reloads. Leave two adjacent ground-level blocks clear with headroom, at least three blocks from the beacon. Create the first chest between raids. Downed Special Forces count as defeated; their bodies remain lootable for up to ten active minutes. Higher reward levels shorten the next interval: 7, 6, 5, 4, 3, then 2 days.\n\nRaid break: buy five ACTIVE days before the normal countdown resumes. Each purchase costs more brass, precision mechanisms and gold. Stack purchases for long expeditions. Choose a raid level cap from 0 to 10 with Lower level / Raise level. This cap limits both enemies and payouts, including the purchased bonus. Your building score and purchased Logistics upgrades remain intact. Start raid now begins a snapshot at the displayed level, without waiting for night or using commands. Starting ends purchased break time. Every third started raid still has a boss. Purchases add up to three tiers within the chosen cap. Offline time pauses the break. Ambient enemies still attack. The next started raid consumes the bonus and resets the purchase cost.",
            "JEI shows the Create recipes for each upgrade component. Manufacture enough, carry them, then click Buy between raids.\n\nCore: larger zone and more HP.\nLogistics: five upgrade levels add five reward tiers. Building contributes up to five more, for tier 10 maximum. Better paid tiers shorten intervals to a minimum of two days. Creative purchases and upgrades are free.\nDefense: 15 / 30 / 45 / 60% less beacon damage.\nRestoration: faster rebuilding and 5 / 10 / 15 / 20 / 25% maximum-HP healing after victory.\nVertical: expand height separately, with no HP or model change.\nReconnaissance: immediate attacker glow.\n\nUpgrades show their material icon above Buy. Hover that icon and press your JEI recipe or uses shortcut (default R / U).",
            "Disabled beacon? Overview > Repair. Eight reinforced plating restore 250 HP and restart the timer.\n\nTo stop the campaign, hold the Beacon Recovery Shovel and use it on the beacon, or choose Settings > Remove. Read the confirmation.\n\nRemoval returns one beacon and resets ALL upgrades and rewards. Your base is kept. Repairs never refund spent ammo or fuel. Park and disassemble moving Create contraptions before raids.",
            "Invite your friend through Essential. Join one FTB Team to share quests. Use the same pack version.\n\n{reload}: reload   {emotes}: emote wheel\n{shaders}: shader reload   {backpack}: backpack\n{quests}: quests   {map}: world map\n\nAll players share the selected raid cap and reward chest. The red BEACON IS BEING ATTACKED warning reaches every player, in any dimension, without holding a tool. The beam can be switched off under Settings. Hold the recovery shovel to see beacon status outside raids. Its HUD hides when you put it away.\n\nLost this book or recovery shovel? Their cheap recipes are in JEI. Starter flintlock: one shot, slow reload. Ammo remains spent.",
            "Win the introductory raid and claim your Weapon Platform. Its recovery crafting recipe is also in JEI. The Armor Platform is a dedicated tactical gear bench; open it empty-handed and filter helmets, vests, pouches, plates and wearable optics. Medical kits, grenades, food, tools and gear materials are in the Weapon Platform Supplies category. It upgrades independently through five Ages at the Weapon Platform. Modern protection and NVGs require a Nether visit and Create precision parts. Its Workshop sells Ammo, Attachment and Armor tables and upgrades nearby placed tables within 8 blocks. Hold your gun and press H while looking at an Ammo or Attachment table, or right-click with the gun. Only compatible components available at its Age appear. The ammo table includes reusable EMPTY magazines as well as loose rounds. Cycle the filter to Loose ammunition or Compatible magazines. Craft an empty shell, then fill it using the magazine addon and your loose rounds. Expanded magazines require a matching extension installed on your gun and a later Age or Create milestone. Coins can purchase shells too, but never include free rounds. The menu counts components that need later Ages.\n\nEach station upgrades independently: Frontier > World Wars > Modern > Advanced > Exotic. Choose Workshop at the Weapon Platform to see upgrade materials. Use the colored Age filter for weapon eras. Your team must visit the Nether and manufacture its upgrade parts before Modern unlocks, including modern pistols. Gauss and cyberpunk weapons are Exotic.\n\nPlace stations in the square base zone: higher Ages give 40 / 90 / 180 / 300 / 450 base score. Only the highest Age per station type counts. Benches occupy two blocks across their width and two blocks tall, one block deep, and turn to face the player. Either half opens the same menu. Mining either half returns one station and preserves its Age. Raid repairs do too. Create Armory, Turrets and Supplies have separate category buttons. Supplies follow shared Create research instead of table Ages: simple field gear starts unlocked; medical kits and grenades need Workshop research, advanced supplies need Precision research. They do not use Ages. Operate a press and mixer for Workshop production, a deployer and a precision mechanism for Precision production, and a steam engine for heavy Steam weapons. Search the expanded gun catalogue for Colt, Winchester, Garand, Thompson, MP40, MG42 and modern tactical weapons. Near-future MS-Mobius weapons need later factory Ages; Helldivers weapons require Exotic. Guns, compatible loose ammo, reusable magazines and attachments share the same progression. Shared milestones also unlock matching Create Armory components. Turrets require Precision production and factory materials."};
        static final String[] STANDALONE_PAGES={
            "Plant your beacon after reading its confirmation. One shared campaign runs in the Overworld. An immediate tier-0 introduction earns your Weapon Platform. Later raids start weekly, falling to two active days as paid rewards improve. Offline time pauses the clock.",
            "Build inside the moving square grid. It marks every included block, including the ceiling and floor. Building, palette, detail, furnishings and rooms earn score without favoring expensive materials. Core upgrades expand the footprint and change Mk-1 through Mk-4. Vertical upgrades expand height separately. Settings also controls the beam and red beacon hurtbox.",
            "Cover your beacon from ambient hostile mobs. Raid attackers know its position and breach walls. Survive to restore tracked damage; spent ammo stays spent. Every third raid adds a boss and 50% bonus completion materials. Missing optional boss mods use a vanilla Ravager. Rewards use vanilla resources, Ammo Coins and healing potions. Installed tactical consumables can also appear. Claim into a shared double chest; overflow stays queued. Raid break buys five active days with copper, diamonds and gold; stacking raises cost. Lower the level cap or start a raid now in that tab.",
            "Craft reinforced plating, logistics modules, resonance coils and restoration matrices from vanilla materials. Carry them to buy upgrades between raids. Core adds radius and HP. Logistics adds reward tiers. Defense reduces damage. Restoration heals after victories. Reconnaissance reveals all attackers. Vertical upgrades add height only. Their recipes are ordinary crafting-table recipes; JEI is optional.",
            "Overview > Repair consumes eight reinforced plating and restores 250 HP. The Recovery Shovel opens controls in the air, and asks for confirmation when used on the beacon. Removing resets all upgrades, progress and unclaimed rewards while keeping your base and completing required recovery. Its red damage outline and cyan zone can be toggled in Settings.",
            "Install the same standalone beacon and TaCZ builds on every player and server. Gun Guide is client-only and optional. It displays your current gun bindings for five seconds, then collapses to an icon with its configurable shortcut. Controls are preserved; this standalone release does not remap other mods or mute their chat. Use normal Minecraft Controls to change keys.",
            "Weapon Platform uses installed TaCZ gun, ammo and attachment indexes, even without native crafting recipes. Unknown firearms get conservative age and vanilla costs; admins can override both in config/arsenal-beacon-standalone.json, then restart. Known historical guns keep their era. Automatic prices are starting balance, not an exact assessment of every addon. Gunsmith menus are redirected to our platforms. Ammo and attachment tables require a held compatible gun; press H at the table or right-click. Ammo Coins are raid-only currency. Buy and upgrade Ammo, Attachment and Armor tables in the Weapon Workshop. Armor holds worn protection, pouches, plates and wearable optics; food, medical items, explosives and tools belong in Supplies. Modern and later table Ages require the team's Nether visit. If Arsenal Displays is installed, its vanilla recipes defer to Supplies while beacon is present."
        };
        String pageText(){return BuildFlavor.STANDALONE?STANDALONE_PAGES[page]:PAGES[page];}
        GuideScreen(Screen parent){super("CREATE ARSENAL / FIELD GUIDE");this.parent=parent;}
        @Override protected void init(){super.init();button("< Back",left+14,top+ph-35,80,b->{page=Math.max(0,page-1);scroll=0;});button("Done",left+pw/2-35,top+ph-35,70,b->onClose());button("Next >",left+pw-94,top+ph-35,80,b->{page=Math.min(PAGES.length-1,page+1);scroll=0;});}
        @Override public boolean mouseScrolled(double x,double y,double amount){int max=Math.max(0,font.split(Component.literal(ControlHints.guide(pageText())),pw-28).size()*11-(ph-105));scroll=Math.max(0,Math.min(max,scroll-(int)(amount*22)));return true;}
        @Override public void onClose(){minecraft.setScreen(parent);}
        @Override public void render(GuiGraphics g,int mx,int my,float partial){panel(g);text(g,TITLES[page],left+14,top+40,CYAN);text(g,"Scroll to read",left+pw-100,top+40,MUTED);g.enableScissor(left+10,top+57,left+pw-10,top+ph-44);wrap(g,ControlHints.guide(pageText()),left+14,top+59-scroll,pw-28,INK);g.disableScissor();super.render(g,mx,my,partial);}
    }
}
