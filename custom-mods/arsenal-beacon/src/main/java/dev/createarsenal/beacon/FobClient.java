package dev.createarsenal.beacon;

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

/**
 * The FOB screen (0.0.23): the FOB's name and state, its village, the raid at it, and the FOB shop (flares at the Support Platform's prices,
 * only while the FOB is ACTIVE). The server decides everything ({@link Fobs#button}); this only shows it and sends menu buttons.
 */
@Mod.EventBusSubscriber(modid=ArsenalBeacon.ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class FobClient {
    private FobClient(){}
    @SubscribeEvent public static void screens(FMLClientSetupEvent e){e.enqueueWork(()->MenuScreens.register(ArsenalBeacon.FOB_MENU.get(),FobScreen::new));}
    /** The FOB's olive green, as on the FOB Zone effect. */
    static final int OLIVE=0xff9cc26a;
    static int stateColor(String state){return switch(state){case "active"->OLIVE;case "down"->Ui.RED;default->Ui.ORANGE;};}
    static Item flare(String id){return switch(id){case "supply"->ArsenalBeacon.SUPPLY_FLARE.get();case "return"->ArsenalBeacon.RETURN_FLARE.get();default->ArsenalBeacon.FIRE_FLARE.get();};}

    static final class FobScreen extends AbstractContainerScreen<FobBlocks.FobMenu> {
        private CompoundTag data;
        private int colW,leftX,rightX,bodyY;
        private Ui.UiButton callButton;
        private final List<Ui.UiButton> buy=new ArrayList<>();private Ui.UiButton gateButton;
        FobScreen(FobBlocks.FobMenu menu,Inventory inv,Component title){super(menu,inv,title);data=menu.data;}
        void update(CompoundTag next){data=next;rebuildWidgets();}
        private String state(){return data.getString("state");}
        private boolean active(){return state().equals("active");}

        @Override protected void init(){
            imageWidth=Math.min(width-12,400);imageHeight=Math.min(height-12,236);
            super.init();clearWidgets();buy.clear();
            colW=(imageWidth-36)/2;leftX=leftPos+12;rightX=leftX+colW+12;bodyY=topPos+34;
            addRenderableWidget(new Ui.UiButton(leftPos+imageWidth-28,topPos+4,22,18,Component.literal("X"),Ui.Look.NORMAL,b->onClose()));
            // the raid call, only while the FOB waits for its first won raid
            callButton=addRenderableWidget(new Ui.UiButton(leftX,topPos+imageHeight-50,colW,20,Ui.t("fob.screen.call"),Ui.Look.PRIMARY,b->click(Fobs.CALL_RAID)));
            callButton.visible=state().equals("planted");callButton.active=data.getBoolean("mayCallRaid")&&data.getBoolean("raidsPossible");
            callButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Ui.t("fob.screen.call.tip")));
            for(int i=0;i<Fobs.SHOP.size();i++){
                String id=Fobs.SHOP.get(i);int price=data.getInt("price."+id);
                var button=addRenderableWidget(new Ui.UiButton(rightX+colW-46,bodyY+16+i*26+3,44,18,Ui.t("fob.shop.buy"),Ui.Look.PRIMARY,b->click(Fobs.BUY_SUPPLY+Fobs.SHOP.indexOf(id))));
                boolean afford=data.getBoolean("creative")||data.getInt("energy")>=price;
                button.active=active()&&afford;button.warning=active()&&!afford;buy.add(button);
            }
            // the second listing (0.0.24): this FOB's Supply Delivery Gate
            boolean has=data.getBoolean("hasGate"),enough=data.getBoolean("creative")||enough(data.getList("gateCost",net.minecraft.nbt.Tag.TAG_COMPOUND));
            gateButton=addRenderableWidget(new Ui.UiButton(rightX+colW-46,gateRowY()+3,44,18,has?Ui.t("fob.shop.placed"):Ui.t("fob.shop.buy"),Ui.Look.PRIMARY,b->click(Fobs.BUY_GATE)));
            gateButton.active=active()&&!has&&enough;gateButton.warning=active()&&!has&&!enough;
        }
        private int gateRowY(){return bodyY+16+Fobs.SHOP.size()*26;}
        private static boolean enough(net.minecraft.nbt.ListTag costs){for(var c:costs){var t=(CompoundTag)c;if(t.getInt("have")<t.getInt("count"))return false;}return true;}
        private void click(int id){if(minecraft!=null&&minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,id);}

        @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
            Ui.panel(g,leftPos,topPos,imageWidth,imageHeight);
            Ui.header(g,font,leftPos,topPos,imageWidth,Component.literal(data.getString("name")),Ui.t("fob.screen.subtitle"));
            drawVillage(g);
            drawShop(g);
            var message=data.getString("message");
            if(!message.isEmpty()){boolean good=message.startsWith("fob.result.bought")||message.equals("fob.result.raid_called");
                Ui.text(g,font,Component.translatable("gui.arsenal_beacon."+message),leftX,topPos+imageHeight-14,good?OLIVE:Ui.ORANGE,imageWidth-24);}
        }
        /** Left: the state, the village, and the raid (or what to do next). */
        private void drawVillage(GuiGraphics g){
            int x=leftX,w=colW,y=bodyY;String state=state();
            Ui.chip(g,font,Ui.t("fob.state."+state),x,y,stateColor(state),w);y+=17;
            Ui.text(g,font,Ui.t("fob.screen.villagers",data.getInt("villagers")),x,y,Ui.INK,w);y+=11;
            Ui.text(g,font,Ui.t("fob.screen.places",data.getInt("beds"),data.getInt("jobs")),x,y,Ui.MUTED,w);y+=11;
            Ui.text(g,font,Ui.t("fob.screen.zone",data.getInt("sections")),x,y,Ui.MUTED,w);y+=15;
            int bottom=topPos+imageHeight-(callButton.visible?54:20);
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
        /** Right: the three flares, the energy carried, and what each needs at home. */
        private void drawShop(GuiGraphics g){
            int x=rightX,w=colW,y=bodyY;
            Ui.text(g,font,Ui.t("fob.shop.title"),x,y+2,Ui.BRASS,w);Ui.rule(g,x+font.width(Ui.t("fob.shop.title"))+6,y+6,w-font.width(Ui.t("fob.shop.title"))-6);
            for(int i=0;i<Fobs.SHOP.size();i++){
                String id=Fobs.SHOP.get(i);int ry=y+16+i*26,price=data.getInt("price."+id);
                Ui.card(g,x,ry,w,24,active()?OLIVE:Ui.EDGE);
                var stack=new ItemStack(flare(id));g.renderItem(stack,x+7,ry+4);
                Ui.text(g,font,stack.getHoverName(),x+27,ry+3,active()?Ui.INK:Ui.MUTED,w-27-50);
                g.pose().pushPose();g.pose().translate(x+27,ry+13,0);g.pose().scale(.5f,.5f,1);g.renderItem(new ItemStack(ArsenalBeacon.ARDENT_ENERGY.get()),0,0);g.pose().popPose();
                boolean afford=data.getBoolean("creative")||data.getInt("energy")>=price;
                Ui.text(g,font,Component.literal(Integer.toString(price)),x+37,ry+14,afford?Ui.CYAN:Ui.ORANGE,30);
            }
            // the Supply Delivery Gate row: its materials, like the Workshop
            int gy=gateRowY();Ui.card(g,x,gy,w,24,active()?OLIVE:Ui.EDGE);
            g.renderItem(new ItemStack(ArsenalBeacon.DELIVERY_GATE_ITEM.get()),x+7,gy+4);
            Ui.text(g,font,new ItemStack(ArsenalBeacon.DELIVERY_GATE_ITEM.get()).getHoverName(),x+27,gy+3,active()?Ui.INK:Ui.MUTED,w-27-50);
            int cx=x+27;
            for(var e:data.getList("gateCost",net.minecraft.nbt.Tag.TAG_COMPOUND)){var c=(CompoundTag)e;var icon=ItemStack.of(c.getCompound("item"));boolean short_=!data.getBoolean("creative")&&c.getInt("have")<c.getInt("count");
                g.pose().pushPose();g.pose().translate(cx,gy+13,0);g.pose().scale(.5f,.5f,1);g.renderItem(icon,0,0);g.pose().popPose();
                var n=Component.literal(Integer.toString(c.getInt("count")));Ui.text(g,font,n,cx+9,gy+14,short_?Ui.ORANGE:Ui.MUTED,30);cx+=12+font.width(n);}
            int ny=gy+28,bottom=topPos+imageHeight-20;
            Ui.text(g,font,data.getBoolean("creative")?Ui.t("fob.shop.creative"):Ui.t("fob.shop.energy",data.getInt("energy")),x,ny,Ui.MUTED,w);ny+=12;
            if(!active()){Ui.wrap(g,font,Ui.t("fob.shop.closed"),x,ny,w,Ui.ORANGE,Math.max(1,(bottom-ny)/10));return;}
            if(!data.getBoolean("hasPlatform"))ny=Ui.wrap(g,font,Ui.t("fob.shop.no_platform"),x,ny,w,Ui.ORANGE,3)+2;
            if(!data.getBoolean("hasCannon")&&ny<bottom)Ui.wrap(g,font,Ui.t("fob.shop.no_cannon"),x,ny,w,Ui.ORANGE,Math.max(1,Math.min(3,(bottom-ny)/10)));
        }
        @Override protected void renderLabels(GuiGraphics g,int mx,int my){}
        @Override public void render(GuiGraphics g,int mx,int my,float partial){
            renderBackground(g);super.render(g,mx,my,partial);
            // a shop row: the flare's own tooltip
            for(int i=0;i<Fobs.SHOP.size();i++){int ry=bodyY+16+i*26;if(Ui.inside(mx,my,rightX,ry,colW-50,24)){g.renderTooltip(font,new ItemStack(flare(Fobs.SHOP.get(i))),mx,my);return;}}
            if(Ui.inside(mx,my,rightX,gateRowY(),colW-50,24)){g.renderComponentTooltip(font,List.of(new ItemStack(ArsenalBeacon.DELIVERY_GATE_ITEM.get()).getHoverName(),Ui.t(data.getBoolean("hasGate")?"fob.shop.gate.placed":"fob.shop.gate.tip")),mx,my);return;}
            for(int i=0;i<buy.size();i++)if(buy.get(i).isHovered()&&!buy.get(i).active){g.renderComponentTooltip(font,List.of(Ui.t(active()?"fob.result.no_energy":"fob.shop.closed")),mx,my);return;}
        }
    }
}
