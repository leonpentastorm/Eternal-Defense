package dev.eternaldefense.beacon;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import java.util.ArrayList;
import java.util.List;

/** The FOB screen: the FOB's name and state, its village, the raid */
@Mod.EventBusSubscriber(modid=EternalDefense.ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class FobClient {
    private FobClient(){}
    @SubscribeEvent public static void screens(FMLClientSetupEvent e){e.enqueueWork(()->MenuScreens.register(EternalDefense.FOB_MENU.get(),FobScreen::new));}
    /** The FOB's olive green, as on the FOB Zone effect. */
    static final int OLIVE=0xff9cc26a;
    static int stateColor(String state){return switch(state){case "active"->OLIVE;case "down"->Ui.RED;default->Ui.ORANGE;};}
    static Item flare(String id){return switch(id){case "supply"->EternalDefense.SUPPLY_FLARE.get();case "return"->EternalDefense.RETURN_FLARE.get();default->EternalDefense.FIRE_FLARE.get();};}
    /** "6 d 23 h" or "5 h" of play left to answer an SOS. */
    static Component sosTime(long ticks){long hours=Math.max(0,ticks)/1000;return hours>=24?Ui.t("fob.sos.days",hours/24,hours%24):Ui.t("fob.sos.hours",Math.max(1,hours));}
    /** "Farmer, Apprentice" for a lost villager (vanilla's own names for professions and levels). */
    static Component trade(String profession,int level){
        var id=net.minecraft.resources.ResourceLocation.tryParse(profession);
        var job=id==null?Component.literal(profession):Component.translatable("entity."+id.getNamespace()+".villager."+id.getPath());
        return Component.translatable("gui.eternal_defense.fob.repair.trade",job,Component.translatable("merchant.level."+Math.max(1,Math.min(5,level))));
    }

    static final class FobScreen extends AbstractContainerScreen<FobBlocks.FobMenu> {
        private CompoundTag data;
        private int colW,shopW,leftX,midX,rightX,bodyY;
        private long receivedAt=net.minecraft.Util.getMillis();
        private Ui.UiButton callButton,repairButton,outlineButton;
        /** The zone outline's switch sits where the zone line was. */
        static final int OUTLINE_Y=39;
        private final List<Ui.UiButton> buy=new ArrayList<>();private Ui.UiButton gateButton,catapultButton;
        private String layout;
        FobScreen(FobBlocks.FobMenu menu,Inventory inv,Component title){super(menu,inv,title);data=menu.data;layout=layout(data);}
        /** The state comes every second (the tribute's countdown) */
        void update(CompoundTag next){data=next;receivedAt=net.minecraft.Util.getMillis();String now=layout(next);if(!now.equals(layout)){layout=now;rebuildWidgets();}}
        private static String layout(CompoundTag d){
            var b=new StringBuilder();
            for(String key:List.of("state","showZone","sections","mayCallRaid","raidsPossible","creative","energy","hasGate","gateCost","catapultCost","lost","repairTotal","mayRepair","level","researchXp","carry.emerald","carry.diamond","carry.ardent"))b.append(d.get(key)).append(';');
            for(String id:Fobs.SHOP)b.append(d.getInt("price."+id)).append(';');
            return b.toString();
        }
        CompoundTag data(){return data;}
        private String state(){return data.getString("state");}
        private boolean active(){return state().equals("active");}

        /** The open tab, kept while the game runs: 0 the FOB (village, tribute, shop), 1 its research. */
        static int tab;
        @Override protected void init(){
            // Three columns: the village, the tribute (its four loot slots), the shop
            imageWidth=Math.min(width-12,660);imageHeight=Math.min(height-12,300);
            super.init();clearWidgets();buy.clear();
            int inner=imageWidth-48;shopW=Math.min(210,Math.max(150,inner*2/5));colW=(inner-shopW)/2;
            leftX=leftPos+12;midX=leftX+colW+12;rightX=midX+colW+12;bodyY=topPos+34;
            addRenderableWidget(new Ui.UiButton(leftPos+imageWidth-28,topPos+4,22,18,Component.literal("X"),Ui.Look.NORMAL,b->onClose()));
            // The two tabs in the header, left of X
            String[] tabs={"fob.tab.overview","fob.tab.research"};int tx=leftPos+imageWidth-32;
            for(int i=tabs.length-1;i>=0;i--){int index=i,tw=font.width(Ui.t(tabs[i]))+14;tx-=tw+2;
                var b=addRenderableWidget(new Ui.UiButton(tx,topPos+4,tw,18,Ui.t(tabs[i]),Ui.Look.TAB,x->{tab=index;rebuildWidgets();}));b.selected=tab==i;}
            tabsX=tx;
            callButton=repairButton=outlineButton=gateButton=catapultButton=null;
            if(tab==1){research();return;}
            // A narrow column (a 426 px window) has room only for the short label; the tooltip says the rest
            Component outline=font.width(Ui.t("fob.screen.outline"))<=colW-30-font.width(Ui.t("off"))-24?Ui.t("fob.screen.outline"):Ui.t("fob.screen.outline.short");
            outlineButton=addRenderableWidget(new Ui.UiButton(leftX,bodyY+OUTLINE_Y,colW,18,outline,Ui.Look.TOGGLE,b->click(Fobs.OUTLINE)));
            outlineButton.on=data.getBoolean("showZone");
            outlineButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Ui.t("fob.screen.outline.tip",data.getInt("sections"))));
            // The raid call, only while the FOB waits for its first won raid
            callButton=addRenderableWidget(new Ui.UiButton(leftX,topPos+imageHeight-50,colW,20,Ui.t("fob.screen.call"),Ui.Look.PRIMARY,b->click(Fobs.CALL_RAID)));
            callButton.visible=state().equals("planted");callButton.active=data.getBoolean("mayCallRaid")&&data.getBoolean("raidsPossible");
            callButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Ui.t("fob.screen.call.tip")));
            for(int i=0;i<Fobs.SHOP.size();i++){
                String id=Fobs.SHOP.get(i);int price=data.getInt("price."+id);
                var button=addRenderableWidget(new Ui.UiButton(rightX+shopW-46,bodyY+16+i*26+3,44,18,Ui.t("fob.shop.buy"),Ui.Look.PRIMARY,b->click(Fobs.BUY_SUPPLY+Fobs.SHOP.indexOf(id))));
                boolean afford=data.getBoolean("creative")||data.getInt("energy")>=price;
                button.active=active()&&afford;button.warning=active()&&!afford;buy.add(button);
            }
            // The second listing: this FOB's Supply Delivery Gate
            boolean has=data.getBoolean("hasGate"),enough=data.getBoolean("creative")||enough(data.getList("gateCost",net.minecraft.nbt.Tag.TAG_COMPOUND));
            gateButton=addRenderableWidget(new Ui.UiButton(rightX+shopW-46,gateRowY()+3,44,18,has?Ui.t("fob.shop.placed"):Ui.t("fob.shop.buy"),Ui.Look.PRIMARY,b->click(Fobs.BUY_GATE)));
            gateButton.active=active()&&!has&&enough;gateButton.warning=active()&&!has&&!enough;
            // The third listing: a Supply Catapult, for the same materials as at Workshop Fabrication
            boolean catapultEnough=data.getBoolean("creative")||enough(data.getList("catapultCost",net.minecraft.nbt.Tag.TAG_COMPOUND));
            catapultButton=addRenderableWidget(new Ui.UiButton(rightX+shopW-46,catapultRowY()+3,44,18,Ui.t("fob.shop.buy"),Ui.Look.PRIMARY,b->click(Fobs.BUY_CATAPULT)));
            catapultButton.active=active()&&catapultEnough;catapultButton.warning=active()&&!catapultEnough;
            // The repair (DOWN: on the right, instead of the shop) or bringing villagers lost in a won raid back (left)
            int lost=lost().size(),total=data.getInt("repairTotal");boolean afford=data.getBoolean("creative")||data.getInt("energy")>=total;
            if(down())repairButton=addRenderableWidget(new Ui.UiButton(rightX,topPos+imageHeight-50,shopW,20,Ui.t("fob.repair.button",total),Ui.Look.PRIMARY,b->click(Fobs.REPAIR)));
            else repairButton=addRenderableWidget(new Ui.UiButton(leftX,topPos+imageHeight-50,colW,20,Ui.t("fob.repair.bring",lost,total),Ui.Look.PRIMARY,b->click(Fobs.REPAIR)));
            repairButton.visible=down()||lost>0&&!state().equals("planted");
            repairButton.active=data.getBoolean("mayRepair")&&afford;repairButton.warning=data.getBoolean("mayRepair")&&!afford;
            repairButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Ui.t(down()?"fob.repair.tip.down":"fob.repair.tip.lost")));
            if(down()){for(var b:buy)b.visible=false;gateButton.visible=false;catapultButton.visible=false;}
        }
        private int tabsX;
        private record Hover(int x,int y,ItemStack stack,int level){}
        private final List<Hover> hoverItems=new ArrayList<>();
        /** Where the Supply Catapult's line was drawn (for its tooltip), or null. */
        private int[] catapultLine;
        private final List<Ui.UiButton> give=new ArrayList<>();
        /** The research tab's columns: what the levels open on the left, what to give on the right. */
        private int resLeftW(){return (imageWidth-24)*55/100;}
        private int resRightX(){return leftPos+12+resLeftW()+12;}
        private int resRightW(){return leftPos+imageWidth-12-resRightX();}
        private int giveRowY(int i){return bodyY+50+12+i*22;}
        private static final String[] KINDS={"emerald","diamond","ardent"};
        /** The buttons of the research tab: give one, or ten, of each. */
        private void research(){
            give.clear();int x=resRightX(),w=resRightW();boolean open=active(),max=data.getInt("level")>=RouteRules.MAX_LEVEL;
            for(int i=0;i<KINDS.length;i++){int kind=i,y=giveRowY(i);int have=data.getBoolean("creative")?99:data.getInt("carry."+KINDS[i]);
                var one=addRenderableWidget(new Ui.UiButton(x+w-56,y+1,24,18,Component.literal("+1"),Ui.Look.PRIMARY,b->click(Fobs.RESEARCH+kind)));
                var ten=addRenderableWidget(new Ui.UiButton(x+w-30,y+1,30,18,Component.literal("+10"),Ui.Look.PRIMARY,b->click(Fobs.RESEARCH+Fobs.RESEARCH_TEN+kind)));
                for(var b:List.of(one,ten)){b.active=open&&!max&&have>0;b.warning=open&&!max&&have<=0;
                    b.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Ui.t("fob.research.give.tip",Fobs.researchXp(kind))));give.add(b);}
            }
        }
        private boolean down(){return state().equals("down");}
        private net.minecraft.nbt.ListTag lost(){return data.getList("lost",net.minecraft.nbt.Tag.TAG_COMPOUND);}
        private int gateRowY(){return bodyY+16+Fobs.SHOP.size()*26;}
        /** A materials card is 24 tall, 34 when its materials need a second line beside the button. */
        private int costCardH(String key){int costW=0;for(var e:data.getList(key,net.minecraft.nbt.Tag.TAG_COMPOUND))costW+=12+font.width(Integer.toString(((CompoundTag)e).getInt("count")));return rightX+27+costW>rightX+shopW-50?34:24;}
        private int catapultRowY(){return gateRowY()+costCardH("gateCost")+4;}
        private static boolean enough(net.minecraft.nbt.ListTag costs){for(var c:costs){var t=(CompoundTag)c;if(t.getInt("have")<t.getInt("count"))return false;}return true;}
        private void click(int id){BeaconNetwork.menuButton(menu.containerId,id);}

        @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
            Ui.panel(g,leftPos,topPos,imageWidth,imageHeight);
            // The header's title is cut short before the tabs
            Ui.header(g,font,leftPos,topPos,imageWidth,Component.literal(font.plainSubstrByWidth(data.getString("name"),tabsX-leftPos-32)),Ui.t("fob.screen.subtitle"));
            if(tab==1)drawResearch(g);
            else{
                drawVillage(g);
                drawTribute(g);
                if(down())drawRepair(g);else drawShop(g);
            }
            var message=data.getString("message");
            if(!message.isEmpty()){boolean good=message.startsWith("fob.result.bought")||message.equals("fob.result.raid_called")||message.startsWith("fob.result.repaired")||message.equals("fob.result.villagers_back")||message.equals("fob.research.given")||message.equals("fob.research.up");
                Ui.text(g,font,Component.translatable("gui.eternal_defense."+message),leftX,topPos+imageHeight-14,good?OLIVE:Ui.ORANGE,imageWidth-24);}
        }
        /** The village's research: its level and bar, what each level opens */
        private void drawResearch(GuiGraphics g){
            int x=leftPos+12,w=imageWidth-24,y=bodyY,xp=data.getInt("researchXp"),level=data.getInt("level"),max=RouteRules.RESEARCH_XP[RouteRules.MAX_LEVEL];
            var specialty=RouteRules.Specialty.orFirst(data.getString("specialty"));
            hoverItems.clear();
            GateClient.tributeIcon(g,"research",x,y-3);
            Component title=Ui.t("fob.research.title"),lv=Ui.t("fob.research.level",level,RouteRules.MAX_LEVEL);
            Ui.text(g,font,title,x+20,y+1,Ui.BRASS,w-28-font.width(lv));Ui.right(g,font,lv,x+w,y+1,level>0?OLIVE:Ui.MUTED);
            // The bar, a tick and a number at each level
            int by=y+16;g.fill(x,by,x+w,by+6,Ui.TRACK);g.fill(x,by,x+(int)Math.round(w*Math.min(1.0,xp/(double)max)),by+6,OLIVE);
            for(int l=1;l<=RouteRules.MAX_LEVEL;l++){int tx=x+(int)Math.round(w*RouteRules.RESEARCH_XP[l]/(double)max);g.fill(tx-1,by-2,tx,by+8,l<=level?Ui.INK:Ui.EDGE);
                String n=Integer.toString(l);Ui.text(g,font,Component.literal(n),Math.min(x+w-font.width(n),tx-font.width(n)/2-1),by+10,l<=level?OLIVE:Ui.MUTED);}
            Component points=level>=RouteRules.MAX_LEVEL?Ui.t("fob.research.top",xp):Ui.t("fob.research.points",xp,RouteRules.RESEARCH_XP[level+1],RouteRules.toNext(xp),level+1);
            Ui.text(g,font,points,x,y+36,Ui.MUTED,w);
            // Left: what each level opens
            int lw=resLeftW(),ly=y+50;
            Ui.text(g,font,Ui.t("fob.research.opens"),x,ly,Ui.BRASS,lw);ly+=12;
            for(int l=1;l<=RouteRules.MAX_LEVEL;l++){
                boolean reached=l<=level;int tier=l==RouteRules.UNLOCK_S?5:l==RouteRules.UNLOCK_A?4:0;
                var items=new ArrayList<RouteRules.Loot>();if(tier>0)for(var o:specialty.options())if(o.tier()==tier)items.add(o);
                g.fill(x,ly+3,x+11,ly+14,reached?OLIVE:Ui.TRACK);Ui.text(g,font,Component.literal(Integer.toString(l)),x+3,ly+5,reached?0xff101418:Ui.MUTED);
                // The top level opens the Supply Catapult and the specialty shop, said plainly with the catapult beside it
                boolean seller=l==CatapultRules.SELLER_LEVEL;
                Component what=seller?(font.width(Ui.t("fob.research.seller"))+16+20<=lw?Ui.t("fob.research.seller"):Ui.t("fob.research.seller.short"))
                    :items.isEmpty()?Ui.t("fob.research.bonus",Math.round(RouteRules.SPECIALTY_PER_LEVEL*100),Math.round(RouteRules.OTHER_PER_LEVEL*100))
                    :Ui.t("fob.research.tier",RouteRules.TIERS.substring(tier,tier+1));
                int tw=Math.min(font.width(what),lw-16-(seller?20:0));Ui.text(g,font,what,x+16,ly+5,reached?Ui.INK:seller?Ui.BRASS:items.isEmpty()?Ui.MUTED:GateClient.tierColor(tier),tw);
                if(seller){var cat=new ItemStack(EternalDefense.SUPPLY_CATAPULT_ITEM.get());int cx=x+16+tw+4;g.renderItem(cat,cx,ly+1);hoverItems.add(new Hover(cx,ly+1,cat,l));
                    if(!reached){g.pose().pushPose();g.pose().translate(0,0,200);g.fill(cx,ly+1,cx+16,ly+17,0x90101418);g.pose().popPose();}}
                // The items it opens, as many as fit
                int ix=x+16+tw+4;
                for(var o:items){if(ix+16>x+lw)break;var stack=GateClient.icon(o.id());g.renderItem(stack,ix,ly+1);hoverItems.add(new Hover(ix,ly+1,stack,l));if(!reached){g.pose().pushPose();g.pose().translate(0,0,200);g.fill(ix,ly+1,ix+16,ly+17,0x90101418);g.pose().popPose();}ix+=17;}
                ly+=18;
            }
            // Right: what to give, and what research gives now
            int rx=resRightX(),rw=resRightW(),ry=y+50;
            Ui.text(g,font,Ui.t("fob.research.give"),rx,ry,Ui.BRASS,rw);
            for(int i=0;i<KINDS.length;i++){int gy=giveRowY(i);
                g.renderItem(new ItemStack(Fobs.researchItem(i)),rx,gy+2);
                Ui.text(g,font,Ui.t("fob.research.worth",Fobs.researchXp(i)),rx+19,gy+1,Ui.INK,rw-19-60);
                Ui.text(g,font,data.getBoolean("creative")?Ui.t("fob.shop.creative.short"):Ui.t("fob.research.carry",data.getInt("carry."+KINDS[i])),rx+19,gy+11,Ui.MUTED,rw-19-60);
            }
            int ny=giveRowY(KINDS.length)+4;
            Ui.text(g,font,Ui.t("fob.research.now"),rx,ny,Ui.BRASS,rw);ny+=12;
            int iw=GateClient.specialtyIcons(g,specialty.id(),rx,ny);
            Component spec=Component.literal("+"+Math.round((RouteRules.slotMultiplier(RouteRules.Slot.SPECIALTY,level)-1)*100)+"%");
            Ui.text(g,font,spec,rx+iw+2,ny+4,level>0?OLIVE:Ui.MUTED);
            int ox=rx+iw+2+font.width(spec)+8;
            for(String slot:List.of("emerald","mine","farm")){GateClient.tributeIcon(g,"slot_"+slot,ox,ny);ox+=17;}
            Ui.text(g,font,Component.literal("+"+Math.round((RouteRules.slotMultiplier(RouteRules.Slot.FARM,level)-1)*100)+"%"),ox+1,ny+4,level>0?OLIVE:Ui.MUTED);
            if(!active())Ui.wrap(g,font,Ui.t("fob.research.closed"),rx,ny+22,rw,Ui.ORANGE,2);
        }
        /** Left: the state, the village, and the raid (or what to do next). */
        private void drawVillage(GuiGraphics g){
            int x=leftX,w=colW,y=bodyY;String state=state();catapultLine=null;
            Ui.chip(g,font,Ui.t("fob.state."+state),x,y,stateColor(state),w);y+=17;
            Ui.text(g,font,Ui.t("fob.screen.villagers",data.getInt("villagers")),x,y,Ui.INK,w);y+=11;
            Ui.text(g,font,Ui.t("fob.screen.places",data.getInt("beds"),data.getInt("jobs")),x,y,Ui.MUTED,w);y+=11;
            y=bodyY+OUTLINE_Y+22;   // The zone outline switch
            // The Supply Catapult, online or offline and why
            String cat=data.getString("catapult");
            if(!cat.isEmpty()){Component line=cat.equals("online")?Ui.t("fob.catapult.online"):Ui.t("fob.catapult.offline."+cat,CatapultRules.SELLER_LEVEL);
                int end=Ui.wrap(g,font,line,x,y,w,cat.equals("online")?OLIVE:Ui.MUTED,2);catapultLine=new int[]{x,y,Math.min(w,font.width(line)),end-y};y=end+3;}
            int bottom=topPos+imageHeight-(callButton.visible||repairButton.visible&&!down()?54:20);
            if(data.getBoolean("sos")&&!data.getBoolean("raid")){   // An SOS from this FOB, no raid yet
                Ui.card(g,x,y,w,Math.min(48,bottom-y),Ui.RED);
                Ui.text(g,font,Ui.t("fob.screen.sos",sosTime(data.getLong("sosLeft"))),x+8,y+5,Ui.RED,w-12);
                Ui.wrap(g,font,Ui.t("fob.screen.sos.how"),x+8,y+17,w-12,Ui.INK,3);
                return;
            }
            if(!down()&&!lost().isEmpty()&&!data.getBoolean("raid")){Ui.text(g,font,Ui.t("fob.screen.lost",lost().size()),x,y,Ui.ORANGE,w);y+=12;}
            if(data.getBoolean("raid")){
                Ui.card(g,x,y,w,Math.min(56,bottom-y),Ui.RED);
                int cx=x+8,cw=w-12,cy=y+5;
                Component head=!data.contains("waves")?Ui.t("fob.screen.raid.far"):data.getInt("wave")==0?Ui.t("fob.screen.raid.first"):Ui.t("fob.screen.raid",data.getInt("wave"),data.getInt("waves"),data.getInt("raiders"));
                Ui.text(g,font,head,cx,cy,Ui.RED,cw);cy+=12;
                int start=data.getInt("raidStart"),alive=data.getInt("raidAlive");
                Ui.text(g,font,Ui.t("fob.screen.raid.villagers",alive,start,(start+1)/2),cx,cy,Ui.INK,cw);cy+=11;
                Ui.bar(g,cx,cy,cw-4,5,start<=0?0:alive/(float)start,alive*2<start+2?Ui.RED:OLIVE);cy+=9;
                Ui.text(g,font,Ui.t("fob.screen.raid.open"),cx,cy,Ui.MUTED,cw);
                return;
            }
            Component about=!data.getBoolean("raidsPossible")&&state.equals("planted")?Ui.t("fob.screen.about.no_raids"):Ui.t("fob.screen.about."+state);
            Ui.wrap(g,font,about,x,y,w,state.equals("down")?Ui.ORANGE:Ui.MUTED,Math.max(1,(bottom-y)/10));
        }
        /** Middle: the tribute, a summary of the four loot slots chosen at the Supply Gates */
        private void drawTribute(GuiGraphics g){
            int x=midX,w=colW,y=bodyY;
            Ui.text(g,font,Ui.t("fob.tribute.title"),x,y+2,Ui.BRASS,w);Ui.rule(g,x+font.width(Ui.t("fob.tribute.title"))+6,y+6,Math.max(0,w-font.width(Ui.t("fob.tribute.title"))-6));y+=16;
            int iw=GateClient.specialtyIcons(g,data.getString("specialty"),x,y-3)+3;   // The specialty's icon, its research level
            y=Ui.wrap(g,font,Ui.t("fob.tribute.specialty",Ui.t("gate.specialty."+data.getString("specialty")),data.getInt("level")),x+iw,y,w-iw,Ui.MUTED,2)+3;
            var slots=data.getList("slots",net.minecraft.nbt.Tag.TAG_COMPOUND);double perHour=data.getDouble("tripsPerHour");
            for(int i=0;i<slots.size();i++){
                var s=slots.getCompound(i);boolean on=s.getBoolean("on");var stack=GateClient.icon(s.getString("item"));
                g.renderItem(stack,x,y+2);
                Ui.text(g,font,stack.getHoverName(),x+19,y,on?Ui.INK:Ui.MUTED,w-19);
                Ui.text(g,font,on?GateClient.rate(s.getDouble("perTrip"),0):Ui.t("fob.tribute.off"),x+19,y+10,on?Ui.CYAN:Ui.MUTED,w-19);
                y+=imageHeight>=300?24:21;
            }
            int bottom=topPos+imageHeight-20;
            if(!data.getBoolean("hasRoute")){Ui.wrap(g,font,Ui.t("fob.tribute.no_route"),x,y+2,w,Ui.ORANGE,Math.max(1,(bottom-y)/10));return;}
            long next=data.getLong("nextIn");if(next>=0)next=Math.max(0,next-(net.minecraft.Util.getMillis()-receivedAt)/50);
            // Each line wraps on a narrow window, and stops at the bottom of the column
            y=Ui.wrap(g,font,next>=0?Ui.t("fob.tribute.next",GateClient.time(next)):Ui.t("fob.tribute.paused"),x,y+2,w,next>=0?Ui.INK:Ui.MUTED,rows(y,bottom,2))-1;
            if(perHour>0&&y+10<=bottom)y=Ui.wrap(g,font,Ui.t("fob.tribute.trips",GateClient.number(perHour)),x,y+2,w,Ui.MUTED,rows(y,bottom,2))-1;
            if(y+10<=bottom)y=Ui.wrap(g,font,Ui.t("fob.tribute.storage",data.getInt("stored"),data.getInt("depotSlots")),x,y+2,w,data.getBoolean("full")?Ui.ORANGE:Ui.MUTED,rows(y,bottom,2))-1;
            // The hint only when it fits whole (the Field Guide says it too)
            int hint=Ui.lines(font,Ui.t("fob.tribute.where"),w);
            if(hint<=3&&hint<=rows(y+2,bottom,3)&&y+4+hint*11<=bottom)Ui.wrap(g,font,Ui.t("fob.tribute.where"),x,y+4,w,Ui.MUTED,hint);
        }
        /** How many 11 px lines fit from y to bottom, at least 1 and at most max. */
        private static int rows(int y,int bottom,int max){return Math.max(1,Math.min(max,(bottom-y-2)/11));}
        /** Right, while DOWN: each lost villager with its price, the base price, the total and what the player carries. */
        private void drawRepair(GuiGraphics g){
            int x=rightX,w=shopW,y=bodyY,bottom=topPos+imageHeight-54;
            Ui.text(g,font,Ui.t("fob.repair.title"),x,y+2,Ui.RED,w);Ui.rule(g,x+font.width(Ui.t("fob.repair.title"))+6,y+6,w-font.width(Ui.t("fob.repair.title"))-6);y+=16;
            var lost=lost();int rows=Math.max(0,(bottom-y-40)/20),shown=lost.size()>rows?Math.max(0,rows-1):lost.size();
            if(lost.isEmpty()){Ui.text(g,font,Ui.t("fob.repair.none"),x,y,Ui.MUTED,w);y+=12;}
            for(int i=0;i<shown;i++){
                var r=lost.getCompound(i);
                Ui.text(g,font,Component.literal(r.getString("name")),x,y,Ui.INK,w-24);price(g,r.getInt("cost"),x+w,y);
                Ui.text(g,font,trade(r.getString("profession"),r.getInt("level")),x+6,y+9,Ui.MUTED,w-30);y+=20;
            }
            if(shown<lost.size()){int rest=0;for(int i=shown;i<lost.size();i++)rest+=lost.getCompound(i).getInt("cost");
                Ui.text(g,font,Ui.t("fob.repair.more",lost.size()-shown),x,y,Ui.MUTED,w-24);price(g,rest,x+w,y);y+=12;}
            Ui.text(g,font,Ui.t("fob.repair.base"),x,y,Ui.INK,w-24);price(g,data.getInt("repairBase"),x+w,y);y+=12;
            Ui.rule(g,x,y+1,w);y+=5;
            Ui.text(g,font,Ui.t("fob.repair.total",data.getInt("repairTotal")),x,y,Ui.BRASS,w);y+=11;
            Ui.text(g,font,data.getBoolean("creative")?Ui.t("fob.shop.creative"):Ui.t("fob.shop.energy",data.getInt("energy")),x,y,Ui.MUTED,w);y+=11;
            if(!data.getBoolean("activated")&&y<bottom)Ui.wrap(g,font,Ui.t("fob.repair.never_won"),x,y,w,Ui.ORANGE,Math.max(1,(bottom-y)/10));
        }
        /** A price at the right edge: the number and a small Ardent Energy icon. */
        private void price(GuiGraphics g,int amount,int right,int y){
            var n=Component.literal(Integer.toString(amount));int nx=right-font.width(n)-10;
            Ui.text(g,font,n,nx,y,Ui.CYAN,30);
            g.pose().pushPose();g.pose().translate(right-8,y,0);g.pose().scale(.5f,.5f,1);g.renderItem(new ItemStack(EternalDefense.ARDENT_ENERGY.get()),0,0);g.pose().popPose();
        }
        /** Right: the three flares, the energy carried, and what each needs at home. */
        private void drawShop(GuiGraphics g){
            int x=rightX,w=shopW,y=bodyY;
            Ui.text(g,font,Ui.t("fob.shop.title"),x,y+2,Ui.BRASS,w);Ui.rule(g,x+font.width(Ui.t("fob.shop.title"))+6,y+6,w-font.width(Ui.t("fob.shop.title"))-6);
            for(int i=0;i<Fobs.SHOP.size();i++){
                String id=Fobs.SHOP.get(i);int ry=y+16+i*26,price=data.getInt("price."+id);
                Ui.card(g,x,ry,w,24,active()?OLIVE:Ui.EDGE);
                var stack=new ItemStack(flare(id));g.renderItem(stack,x+7,ry+4);
                Ui.text(g,font,stack.getHoverName(),x+27,ry+3,active()?Ui.INK:Ui.MUTED,w-27-50);
                g.pose().pushPose();g.pose().translate(x+27,ry+13,0);g.pose().scale(.5f,.5f,1);g.renderItem(new ItemStack(EternalDefense.ARDENT_ENERGY.get()),0,0);g.pose().popPose();
                boolean afford=data.getBoolean("creative")||data.getInt("energy")>=price;
                Ui.text(g,font,Component.literal(Integer.toString(price)),x+37,ry+14,afford?Ui.CYAN:Ui.ORANGE,30);
            }
            // The Supply Delivery Gate row and the Supply Catapult row: their materials, like the Workshop
            costCard(g,x,gateRowY(),w,new ItemStack(EternalDefense.DELIVERY_GATE_ITEM.get()),"gateCost");
            costCard(g,x,catapultRowY(),w,new ItemStack(EternalDefense.SUPPLY_CATAPULT_ITEM.get()),"catapultCost");
            int ny=catapultRowY()+costCardH("catapultCost")+4,bottom=topPos+imageHeight-20;
            Ui.text(g,font,data.getBoolean("creative")?Ui.t("fob.shop.creative"):Ui.t("fob.shop.energy",data.getInt("energy")),x,ny,Ui.MUTED,w);ny+=12;
            if(!active()){Ui.wrap(g,font,Ui.t("fob.shop.closed"),x,ny,w,Ui.ORANGE,Math.max(1,(bottom-ny)/10));return;}
            if(!data.getBoolean("hasPlatform")&&ny<bottom)ny=Ui.wrap(g,font,Ui.t("fob.shop.no_platform"),x,ny,w,Ui.ORANGE,Math.max(1,Math.min(3,(bottom-ny)/10)))+2;
            if(!data.getBoolean("hasCannon")&&ny<bottom)Ui.wrap(g,font,Ui.t("fob.shop.no_cannon"),x,ny,w,Ui.ORANGE,Math.max(1,Math.min(3,(bottom-ny)/10)));
        }
        /** One materials card of the shop: the item, its name and what it costs (orange where the pack is short) */
        private void costCard(GuiGraphics g,int x,int y,int w,ItemStack item,String key){
            var costs=data.getList(key,net.minecraft.nbt.Tag.TAG_COMPOUND);int cardH=costCardH(key);
            Ui.card(g,x,y,w,cardH,active()?OLIVE:Ui.EDGE);
            g.renderItem(item,x+7,y+4);
            Ui.text(g,font,item.getHoverName(),x+27,y+3,active()?Ui.INK:Ui.MUTED,w-27-50);
            int cx=x+27,cy=y+13;
            for(var e:costs){var c=(CompoundTag)e;var icon=ItemStack.of(c.getCompound("item"));boolean short_=!data.getBoolean("creative")&&c.getInt("have")<c.getInt("count");
                var n=Component.literal(Integer.toString(c.getInt("count")));int cw=12+font.width(n);
                if(cy==y+13&&cx+cw>x+w-50){cx=x+27;cy=y+23;}
                g.pose().pushPose();g.pose().translate(cx,cy,0);g.pose().scale(.5f,.5f,1);g.renderItem(icon,0,0);g.pose().popPose();
                Ui.text(g,font,n,cx+9,cy+1,short_?Ui.ORANGE:Ui.MUTED,30);cx+=cw;}
        }
        @Override protected void renderLabels(GuiGraphics g,int mx,int my){}
        @Override public void render(GuiGraphics g,int mx,int my,float partial){
            renderBackground(g);super.render(g,mx,my,partial);
            if(tab==1){   // The research tab: an item it opens, its name and the level
                for(var h:hoverItems)if(Ui.inside(mx,my,h.x(),h.y(),16,16)){g.renderComponentTooltip(font,List.of(h.stack().getHoverName(),Ui.t("fob.research.opens.at",h.level())),mx,my);return;}
                return;
            }
            if(catapultLine!=null&&Ui.inside(mx,my,catapultLine[0],catapultLine[1],catapultLine[2],catapultLine[3])){g.renderTooltip(font,font.split(Ui.t("fob.catapult.tip"),200),mx,my);return;}
            if(down())return;   // The repair replaces the shop
            // A shop row: the flare's own tooltip
            for(int i=0;i<Fobs.SHOP.size();i++){int ry=bodyY+16+i*26;if(Ui.inside(mx,my,rightX,ry,shopW-50,24)){g.renderTooltip(font,new ItemStack(flare(Fobs.SHOP.get(i))),mx,my);return;}}
            if(Ui.inside(mx,my,rightX,catapultRowY(),shopW-50,24)){g.renderComponentTooltip(font,List.of(new ItemStack(EternalDefense.SUPPLY_CATAPULT_ITEM.get()).getHoverName(),Ui.t("fob.shop.catapult.tip",CatapultRules.SELLER_LEVEL)),mx,my);return;}
            if(Ui.inside(mx,my,rightX,gateRowY(),shopW-50,24)){g.renderComponentTooltip(font,List.of(new ItemStack(EternalDefense.DELIVERY_GATE_ITEM.get()).getHoverName(),Ui.t(data.getBoolean("hasGate")?"fob.shop.gate.placed":"fob.shop.gate.tip")),mx,my);return;}
            for(int i=0;i<buy.size();i++)if(buy.get(i).isHovered()&&!buy.get(i).active){g.renderComponentTooltip(font,List.of(Ui.t(active()?"fob.result.no_energy":"fob.shop.closed")),mx,my);return;}
        }
    }
}
