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
    /** From the owner's right half (facing north, in the anchor's frame): the front plate at z 2.6, the back at z 13.4; text area x 11.5..30.5, y 27.25..30.25 (pixels). */
    static final float PLATE_FRONT=2.6f/16,PLATE_BACK=13.4f/16,TEXT_X=21f/16,TEXT_Y=28.75f/16,TEXT_WIDTH=19f/16,TEXT_HEIGHT=3f/16,SCALE=.016f;
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

    /** "1.5" or "12": a rate for the screens. */
    static String number(double x){if(x>=10)return Long.toString(Math.round(x));String s=String.format(java.util.Locale.ROOT,"%.1f",x);return s.endsWith(".0")?s.substring(0,s.length()-2):s;}
    /** "2 per trip" or "1 every 3.5 trips", with "about 9 an hour of play" when the route runs. */
    static Component rate(double perTrip,double tripsPerHour){
        if(perTrip<=0)return Ui.t("gate.rate.off");
        Component trip=perTrip>=1-1e-9?Ui.t("gate.rate.per_trip",number(perTrip)):Ui.t("gate.rate.every",number(1/perTrip));
        return tripsPerHour>0?Ui.t("gate.rate.hour",trip,number(perTrip*tripsPerHour)):trip;
    }
    static ItemStack icon(String id){return new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(id)));}
    static Component slotTitle(String slot,String specialty){return slot.equals("specialty")?Ui.t("gate.slot.specialty",Ui.t("gate.specialty."+specialty)):Ui.t("gate.slot."+slot);}
    static int slotColor(int index){return switch(index){case 0->0xff4fd18b;case 1->0xffb0b8c0;case 2->FobClient.OLIVE;default->Ui.BRASS;};}

    /**
     * 0.0.31 (owner): the gate screen takes the whole window. A Receiving Gate shows its tribute storage (take only) and the inventory on the
     * left; the right side has the light and why, the countdown to the next delivery, and two tabs: Tribute (the four loot slots, each with
     * its switch, its item, how many trips one item takes and a Choose button) and Route (the checklist). Choose opens a grid of the slot's
     * items. On a narrow window (1920 x 1080 at GUI scale 4, 1280 x 720 at GUI scale 3) the storage has a tab of its own and the slot cards
     * use two lines. The server sends the state again every second, so the countdown and the rates are always current.
     */
    static final class GateScreen extends AbstractContainerScreen<RailGates.GateMenu> {
        /** The storage column's width (its left margin, 9 slots, a gap) and the least room the tabs need beside it. */
        static final int STORE_COL=RailGates.GateMenu.STORE_X+162+14,SPLIT_MIN=290,TAB_W=80;
        private CompoundTag data;private boolean relink;private long receivedAt;private String layout="";
        /** The open tab (0 tribute, 1 route, 2 storage on a narrow window) and the slot whose items are being chosen (-1 none), kept while the screen is open. */
        int tab,picking=-1,pickScroll;
        private int rx,rw,bodyY,cardsY,cardH;private boolean split,changeBelow;
        final List<Ui.UiButton> pickButtons=new ArrayList<>();
        GateScreen(RailGates.GateMenu menu,Inventory inv,Component title){super(menu,inv,title);data=menu.data;receivedAt=net.minecraft.Util.getMillis();layout=layout(data);}
        void update(CompoundTag next){
            data=next;receivedAt=net.minecraft.Util.getMillis();
            String now=layout(next);if(!now.equals(layout)){layout=now;relink=false;rebuildWidgets();}
        }
        /** What the buttons depend on: rebuilt only when it changes (the state comes every second). */
        private static String layout(CompoundTag d){
            var b=new StringBuilder(d.getString("kind")).append(d.getBoolean("linked")).append(d.getString("specialty")).append(d.getList("options",Tag.TAG_COMPOUND).size());
            for(var t:d.getList("slots",Tag.TAG_COMPOUND)){var s=(CompoundTag)t;b.append(s.getBoolean("on")).append(s.getInt("choice")).append(s.getBoolean("mayToggle"));}
            return b.toString();
        }
        private boolean receiving(){return data.getString("kind").equals(RailGates.Kind.RECEIVING.id);}
        private boolean linked(){return data.getBoolean("linked");}
        private boolean choosing(){return receiving()&&(!linked()||relink);}
        private List<CompoundTag> slots(){var out=new ArrayList<CompoundTag>();for(var t:data.getList("slots",Tag.TAG_COMPOUND))out.add((CompoundTag)t);return out;}
        private RouteRules.Specialty specialty(){for(var s:RouteRules.Specialty.values())if(s.id().equals(data.getString("specialty")))return s;return RouteRules.Specialty.FISHING;}
        /** The countdown, counted on from the last state (ticks of play pass while the player is here). */
        long nextIn(){long n=data.getLong("nextIn");return n<0?-1:Math.max(0,n-(net.minecraft.Util.getMillis()-receivedAt)/50);}
        private int countH(){return imageHeight>=300?36:22;}
        /** A slot card's own height (the cards are {@code cardH} apart) and whether it has room for three lines. */
        private int cardInner(){return cardH-3;}
        private boolean tall(){return cardInner()>=33;}
        boolean split(){return split;}
        CompoundTag data(){return data;}

        @Override protected void init(){
            // the whole window, with a small margin (the storage column needs 240 px of height)
            imageWidth=Math.max(300,width-16);imageHeight=Math.max(RailGates.GateMenu.STORE_BOTTOM,height-16);
            super.init();clearWidgets();pickButtons.clear();
            // a wide window shows the storage beside the tabs; a narrow one gives it a tab of its own
            split=menu.storage&&imageWidth-STORE_COL-12>=SPLIT_MIN;
            if(tab==2&&(!menu.storage||split))tab=0;
            menu.slotsShown=menu.storage&&(split||tab==2&&linked()&&!choosing());
            rx=leftPos+(menu.slotsShown?STORE_COL:12);rw=leftPos+imageWidth-12-rx;bodyY=topPos+32;
            addRenderableWidget(new Ui.UiButton(leftPos+imageWidth-28,topPos+4,22,18,Component.literal("X"),Ui.Look.NORMAL,b->onClose()));
            if(choosing()){
                var options=data.getList("options",Tag.TAG_COMPOUND);int y=bodyY+30;
                for(int i=0;i<options.size()&&y<topPos+imageHeight-40;i++){
                    var o=options.getCompound(i);int id=Routes.LINK+i;
                    var b=addRenderableWidget(new Ui.UiButton(rx,y,rw,20,Ui.t("gate.link.option",o.getString("name"),Ui.t("fob.state."+o.getString("state"))),Ui.Look.PRIMARY,x->click(id)));
                    b.active=!o.getBoolean("current");y+=24;
                }
                if(linked())addRenderableWidget(new Ui.UiButton(rx,topPos+imageHeight-30,90,20,Ui.t("gate.link.back"),Ui.Look.NORMAL,x->{relink=false;rebuildWidgets();}));
                return;
            }
            if(!linked())return;
            // the countdown card's height shrinks on a small window; the four cards share what is left
            int tabsY=bodyY+24+countH()+6;cardsY=tabsY+22;
            cardH=Math.max(26,Math.min(46,(topPos+imageHeight-14-cardsY-13)/4));
            var labels=new ArrayList<String>(List.of("gate.tab.tribute","gate.tab.route"));if(menu.storage&&!split)labels.add("gate.tab.storage");
            // the tabs share the row when it is narrow (the Storage tab beside the storage on a 426 px window)
            int tabW=Math.min(TAB_W,(rw-2*(labels.size()-1))/labels.size());
            for(int i=0;i<labels.size();i++){int index=i;
                var t=addRenderableWidget(new Ui.UiButton(rx+i*(tabW+2),tabsY,tabW,18,Ui.t(labels.get(i)),Ui.Look.TAB,b->{tab=index;picking=-1;rebuildWidgets();}));t.selected=tab==i;}
            if(tab==1){
                // Change FOB beside the tabs when there is room, else at the bottom
                int tabsEnd=rx+labels.size()*(tabW+2);changeBelow=tabsEnd+4+110>rx+rw;
                if(receiving())addRenderableWidget(new Ui.UiButton(rx+rw-110,changeBelow?topPos+imageHeight-30:tabsY,110,changeBelow?20:18,Ui.t("gate.link.change"),Ui.Look.NORMAL,x->{relink=true;rebuildWidgets();}));
                return;
            }
            if(tab==2)return;
            if(picking>=0){picker();return;}
            var slots=slots();int h=cardInner();
            for(int i=0;i<slots.size();i++){
                var s=slots.get(i);int index=i,y=cardsY+13+i*cardH;boolean on=s.getBoolean("on");
                var toggle=addRenderableWidget(new Ui.UiButton(rx+6,y+(h-16)/2,34,16,Ui.t(on?"gate.slot.on":"gate.slot.off"),on?Ui.Look.PRIMARY:Ui.Look.NORMAL,b->click(Routes.TOGGLE+index)));
                toggle.active=s.getBoolean("mayToggle");
                toggle.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Ui.t(toggle.active?"gate.slot.toggle.tip":"gate.slot.last")));
                if(i>0){
                    var choose=addRenderableWidget(new Ui.UiButton(rx+rw-66,y+(h-18)/2,60,18,Ui.t("gate.slot.choose"),Ui.Look.NORMAL,b->{picking=index;pickScroll=0;rebuildWidgets();}));
                    choose.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Ui.t("gate.slot.choose.tip."+s.getString("slot"))));
                }
            }
        }
        /** The grid of a slot's items: one button each (its icon), scrolled by the wheel when it is longer than the space. */
        private void picker(){
            var slot=RouteRules.Slot.values()[picking];var options=RouteRules.options(slot,specialty());
            int cell=22,cols=Math.max(1,(rw-12)/cell),top=cardsY+13,rows=Math.max(1,(topPos+imageHeight-44-top)/cell);
            int total=(options.size()+cols-1)/cols;pickScroll=Math.max(0,Math.min(pickScroll,Math.max(0,total-rows)));
            int chosen=slots().size()>picking?slots().get(picking).getInt("choice"):0;
            for(int i=pickScroll*cols;i<options.size()&&i<(pickScroll+rows)*cols;i++){
                int index=i,col=(i-pickScroll*cols)%cols,row=(i-pickScroll*cols)/cols;var loot=options.get(i);var stack=icon(loot.id());
                var b=new Ui.UiButton(rx+6+col*cell,top+row*cell,cell-2,cell-2,Component.empty(),Ui.Look.NORMAL,x->{click(Routes.chooseButton(slot,index));picking=-1;rebuildWidgets();});
                b.selected=i==chosen;b.painter((g,self,hover)->g.renderItem(stack,self.getX()+2,self.getY()+2)).noLabel();
                pickButtons.add(addRenderableWidget(b));
            }
            addRenderableWidget(new Ui.UiButton(rx+rw-90,topPos+imageHeight-30,90,20,Ui.t("gate.pick.back"),Ui.Look.NORMAL,x->{picking=-1;rebuildWidgets();}));
        }
        private void click(int id){BeaconNetwork.menuButton(menu.containerId,id);}
        @Override public boolean mouseScrolled(double mx,double my,double delta){
            if(picking>=0){pickScroll=Math.max(0,pickScroll-(int)Math.signum(delta));rebuildWidgets();return true;}
            return super.mouseScrolled(mx,my,delta);
        }

        @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
            Ui.panel(g,leftPos,topPos,imageWidth,imageHeight);
            Component title=linked()?Component.literal(data.getString("name")):Ui.t("gate.unlinked");
            Ui.header(g,font,leftPos,topPos,imageWidth,title,Component.translatable("block.arsenal_beacon."+data.getString("kind")));
            if(menu.slotsShown)drawStorage(g);
            if(choosing()){
                Ui.wrap(g,font,Ui.t(data.getList("options",Tag.TAG_COMPOUND).isEmpty()?"gate.link.none":"gate.link.help"),rx,bodyY,rw,Ui.MUTED,3);
            }else if(!linked()){
                Ui.chip(g,font,Ui.t("gate.signal.red"),rx,bodyY,Ui.RED,90);
                Ui.wrap(g,font,Ui.t("gate.reason.unlinked"),rx,bodyY+18,rw,Ui.RED,3);
            }else{
                drawStatus(g);
                drawCountdown(g);
                if(tab==1)drawChecklist(g);
                else if(tab==2)drawStorageTab(g);
                else if(picking>=0)drawPicker(g);
                else drawSlots(g);
            }
            var message=data.getString("message");
            if(!message.isEmpty()){boolean good=message.endsWith(".linked")||message.endsWith(".chosen")||message.endsWith(".slot_on")||message.endsWith(".slot_off");
                Ui.text(g,font,Component.translatable("gui.arsenal_beacon."+message),rx,topPos+imageHeight-12,good?FobClient.OLIVE:Ui.ORANGE,rw-(tab==1&&changeBelow||picking>=0?120:0));}
        }
        /** The storage (take only) and the player's inventory, each slot drawn where the menu put it. */
        private void drawStorage(GuiGraphics g){
            int x=leftPos+RailGates.GateMenu.STORE_X,titleY=topPos+RailGates.GateMenu.STORE_Y-10;
            Ui.text(g,font,Ui.t("gate.storage.title"),x,titleY,Ui.BRASS,110);
            if(linked())Ui.right(g,font,Ui.t("gate.storage.short",data.getInt("stored"),data.getInt("depotSlots")),x+162,titleY,data.getBoolean("full")?Ui.ORANGE:Ui.MUTED);
            for(var slot:menu.slots){boolean store=slot.container!=minecraft.player.getInventory();Ui.slot(g,leftPos+slot.x-1,topPos+slot.y-1,store?FobClient.OLIVE:Ui.EDGE);}
            Ui.text(g,font,Ui.t("gate.storage.inventory"),x,topPos+RailGates.GateMenu.INV_Y-10,Ui.MUTED,162);
        }
        /** A narrow window's Storage tab: beside the storage, how full it is and what it does. */
        private void drawStorageTab(GuiGraphics g){
            int y=cardsY+2;
            Ui.text(g,font,Ui.t("gate.check.storage",data.getInt("stored"),data.getInt("depotSlots")),rx,y,data.getBoolean("full")?Ui.ORANGE:Ui.INK,rw);y+=14;
            y=Ui.wrap(g,font,Ui.t("gate.storage.tip"),rx,y,rw,Ui.MUTED,5)+4;
            if(data.getBoolean("full"))Ui.wrap(g,font,Ui.t("gate.reason.full"),rx,y,rw,Ui.ORANGE,4);
        }
        private void drawStatus(GuiGraphics g){
            String signal=data.getString("signal");
            int chip=Ui.chip(g,font,Ui.t("gate.signal."+signal),rx,bodyY,color(signal),90);
            Ui.wrap(g,font,reason(),rx+chip+8,bodyY+2,rw-chip-8,color(signal),2);
        }
        /** The countdown to the next delivery, big, and how often they come. */
        private void drawCountdown(GuiGraphics g){
            int y=bodyY+24,h=countH();
            Ui.card(g,rx,y,rw,h,FobClient.OLIVE);
            long next=nextIn();long period=data.getLong("period");
            Component when=next>=0?time(next):Ui.t("gate.next.paused");
            int half=rw/2;
            if(h>=30){
                Ui.text(g,font,Ui.t("gate.next.title"),rx+8,y+4,Ui.BRASS,half-16);
                // 1.5 times as big: the delivery the player waits for
                if(font.width(when)*1.5f<=half-16){g.pose().pushPose();g.pose().translate(rx+8,y+16,0);g.pose().scale(1.5f,1.5f,1);
                    g.drawString(font,when,0,0,next>=0?Ui.INK:Ui.MUTED,false);g.pose().popPose();}
                else Ui.wrap(g,font,when,rx+8,y+15,half-16,next>=0?Ui.INK:Ui.MUTED,2);
            }
            Component every=period>0?Ui.t("gate.next.every",time(period),number(data.getDouble("tripsPerHour"))):Ui.t("gate.next.unopened");
            if(h<30){
                // a small window: two lines across the card, the countdown on top
                Ui.text(g,font,Ui.t("gate.next.short",when),rx+8,y+2,next>=0?Ui.INK:Ui.MUTED,rw-16);
                if(period>0&&font.width(every)>rw-16)every=Ui.t("gate.next.every.short",time(period),number(data.getDouble("tripsPerHour")));
                Ui.text(g,font,every,rx+8,y+12,Ui.MUTED,rw-16);
                return;
            }
            Ui.wrap(g,font,every,rx+half,y+3,half-8,Ui.MUTED,2);
            long ago=data.getLong("lastAgo");
            if(ago>=0&&h>=30)Ui.text(g,font,Ui.t("gate.last."+(data.getString("lastKind").equals("ride")?"ride":"passive"),data.getInt("lastItems"),time(ago)),rx+half,y+h-11,Ui.MUTED,half-8);
        }
        private Component reason(){
            if(!linked())return Ui.t("gate.reason.unlinked");
            if(!data.getBoolean("partner"))return Ui.t("gate.reason.partner");
            if(!data.getString("state").equals("active"))return Ui.t("gate.reason.fob",Ui.t("fob.state."+data.getString("state")));
            if(!data.getBoolean("commissioned"))return Ui.t("gate.reason.open");
            if(data.getLong("clock")<=0)return Ui.t("gate.reason.overdue");
            if(data.getBoolean("riding"))return Ui.t("gate.reason.riding");
            if(data.getBoolean("full"))return Ui.t("gate.reason.full");
            if(data.getBoolean("warn"))return Ui.t("gate.reason.warn");
            return Ui.t("gate.reason.running");
        }
        /** The four loot slots: the facts line, then one card each (three lines when there is room: its name, its item, its rate). */
        private void drawSlots(GuiGraphics g){
            Ui.text(g,font,Ui.t("gate.facts",data.getInt("villagers"),data.getInt("distance"),Math.round(data.getDouble("bonus")*100)),rx,cardsY+2,Ui.MUTED,rw);
            var slots=slots();double perHour=data.getDouble("tripsPerHour");int h=cardInner();
            for(int i=0;i<slots.size();i++){
                var s=slots.get(i);int y=cardsY+13+i*cardH;boolean on=s.getBoolean("on");
                Ui.card(g,rx,y,rw,h,on?slotColor(i):Ui.EDGE);
                int tx=rx+46+20,tw=rw-(tx-rx)-(i>0?72:8);
                g.renderItem(icon(s.getString("item")),rx+46,y+(h-16)/2);
                Component name=slotTitle(s.getString("slot"),data.getString("specialty")),item=icon(s.getString("item")).getHoverName(),rate=rate(s.getDouble("perTrip"),perHour);
                if(tall()){
                    Ui.text(g,font,name,tx,y+4,on?slotColor(i):Ui.MUTED,tw);
                    Ui.text(g,font,item,tx,y+14,on?Ui.INK:Ui.MUTED,tw);
                    Ui.text(g,font,rate,tx,y+24,on?Ui.CYAN:Ui.MUTED,tw);
                }else{
                    // two lines: the slot's name and its item, then the rate
                    int top=y+(h-18)/2,nameW=Math.min(font.width(name),tw*2/3);
                    Ui.text(g,font,name,tx,top,on?slotColor(i):Ui.MUTED,nameW);
                    if(tw-nameW-6>12)Ui.text(g,font,item,tx+nameW+6,top,on?Ui.INK:Ui.MUTED,tw-nameW-6);
                    Ui.text(g,font,rate,tx,top+10,on?Ui.CYAN:Ui.MUTED,tw);
                }
            }
        }
        private void drawPicker(GuiGraphics g){
            var slot=RouteRules.Slot.values()[picking];
            Ui.text(g,font,Ui.t("gate.pick.title",slotTitle(slot.id(),data.getString("specialty"))),rx,cardsY+2,Ui.BRASS,rw);
            var options=RouteRules.options(slot,specialty());int cell=22,cols=Math.max(1,(rw-12)/cell),total=(options.size()+cols-1)/cols;
            int top=cardsY+13,rows=Math.max(1,(topPos+imageHeight-44-top)/cell);
            if(total>rows)Ui.text(g,font,Ui.t("gate.pick.scroll",pickScroll+1,total-rows+1),rx,topPos+imageHeight-26,Ui.MUTED,rw-100);
        }
        /** The requirements, a tick or a cross each, the fix next to every cross. */
        private void drawChecklist(GuiGraphics g){
            int x=rx,w=rw,y=cardsY+2;
            boolean fob=data.getString("state").equals("active");
            y=line(g,x,y,w,fob,Ui.t("gate.check.fob"),Ui.t(data.getString("state").equals("down")?"gate.fix.fob.down":"gate.fix.fob"));
            y=line(g,x,y,w,data.getBoolean("partner"),Ui.t("gate.check.partner"),Ui.t(receiving()?"gate.fix.partner.receiving":"gate.fix.partner.delivery"));
            if(data.getBoolean("partner")){boolean far=data.getInt("distance")>=data.getInt("minDistance");
                y=line(g,x,y,w,far,Ui.t("gate.check.distance",data.getInt("distance")),Ui.t("gate.fix.distance",data.getInt("minDistance")));}
            boolean open=data.getBoolean("commissioned");
            y=line(g,x,y,w,open,Ui.t("gate.check.route"),Ui.t("gate.fix.route"),3);
            long clock=data.getLong("clock");
            if(!open){Ui.text(g,font,Component.literal("-"),x+1,y,Ui.MUTED,8);Ui.text(g,font,Ui.t("gate.check.clock.later"),x+9,y,Ui.MUTED,w-9);y+=12;}
            else if(clock>0){y=line(g,x,y,w,true,Ui.t("gate.check.clock",time(clock)),Component.empty());if(y+20<topPos+imageHeight-(changeBelow?34:16))y=Ui.wrap(g,font,Ui.t("gate.check.clock.renews"),x+9,y-2,w-9,Ui.MUTED,2)+2;}
            else y=line(g,x,y,w,false,Ui.t("gate.check.clock.none"),Ui.t("gate.fix.clock"));
            y=line(g,x,y,w,!data.getBoolean("full"),Ui.t(receiving()?"gate.check.storage":"gate.storage.remote",data.getInt("stored"),data.getInt("depotSlots")),Ui.t("gate.fix.storage"));
            if(y+26<topPos+imageHeight-(changeBelow?34:16))Ui.wrap(g,font,Ui.t("gate.ride",RouteRules.ACTIVE_FACTOR),x,y+4,w,Ui.MUTED,2);
        }
        private int line(GuiGraphics g,int x,int y,int w,boolean ok,Component what,Component fix){return line(g,x,y,w,ok,what,fix,2);}
        private int line(GuiGraphics g,int x,int y,int w,boolean ok,Component what,Component fix,int fixLines){
            int bottom=topPos+imageHeight-(changeBelow?34:16);if(y+9>bottom)return y;   // no room left (a very small window): the rest is cut, never drawn over
            fixLines=Math.max(0,Math.min(fixLines,(bottom-y-10)/11));
            if(ok)Ui.check(g,x,y+1,FobClient.OLIVE);else Ui.cross(g,x,y+1,Ui.RED);
            Ui.text(g,font,what,x+9,y,ok?Ui.INK:Ui.ORANGE,w-9);
            if(ok||fixLines==0)return y+12;
            return Ui.wrap(g,font,fix,x+9,y+10,w-9,Ui.MUTED,fixLines)+3;
        }
        @Override protected void renderLabels(GuiGraphics g,int mx,int my){}
        @Override public void render(GuiGraphics g,int mx,int my,float partial){
            renderBackground(g);super.render(g,mx,my,partial);
            if(picking>=0&&tab==0){
                var slot=RouteRules.Slot.values()[picking];var options=RouteRules.options(slot,specialty());
                var row=slots().size()>picking?slots().get(picking):new CompoundTag();
                // the slot's share of a trip as it is now, in tribute value: what any item of the list would bring
                var current=RouteRules.loot(slot,specialty(),row.getInt("choice"));double budget=row.getBoolean("on")?row.getDouble("perTrip")*current.value():0;
                int cell=22,cols=Math.max(1,(rw-12)/cell);
                for(int i=0;i<pickButtons.size();i++)if(pickButtons.get(i).isHovered()){
                    var loot=options.get(pickScroll*cols+i);var lines=new ArrayList<Component>();lines.add(icon(loot.id()).getHoverName());
                    lines.add(Ui.t("gate.pick.rate",rate(budget/loot.value(),data.getDouble("tripsPerHour"))));
                    if(slot==RouteRules.Slot.SPECIALTY&&specialty()==RouteRules.Specialty.POTIONS)lines.add(Ui.t("gate.pick.tier","DDCBAS".substring(loot.note(),loot.note()+1)));
                    if(slot==RouteRules.Slot.SPECIALTY&&specialty()==RouteRules.Specialty.TRADER)lines.add(Ui.t("gate.pick.price",loot.note()));
                    g.renderComponentTooltip(font,lines,mx,my);return;
                }
            }
            if(menu.slotsShown&&Ui.inside(mx,my,leftPos+RailGates.GateMenu.STORE_X,topPos+RailGates.GateMenu.STORE_Y-11,162,10))g.renderComponentTooltip(font,List.of(Ui.t("gate.storage.tip")),mx,my);
            renderTooltip(g,mx,my);
        }
    }
}
