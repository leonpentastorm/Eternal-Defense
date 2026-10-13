package dev.createarsenal.beacon;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 0.0.40: the Logistics Center Hub's screen: the vanilla six-row chest, a page turner in the title bar and the slots in use. */
final class HubScreen extends AbstractContainerScreen<HubMenu> {
    private static final ResourceLocation CHEST=new ResourceLocation("textures/gui/container/generic_54.png");
    private Button prev,next;
    HubScreen(HubMenu menu,Inventory inv,Component title){super(menu,inv,title);imageWidth=176;imageHeight=114+HubMenu.ROWS*18;inventoryLabelY=imageHeight-94;}
    @Override protected void init(){
        super.init();
        prev=addRenderableWidget(Button.builder(Component.literal("<"),b->turn(HubMenu.PREV)).bounds(leftPos+imageWidth-56,topPos+3,14,12).tooltip(Tooltip.create(Ui.t("hub.prev"))).build());
        next=addRenderableWidget(Button.builder(Component.literal(">"),b->turn(HubMenu.NEXT)).bounds(leftPos+imageWidth-20,topPos+3,14,12).tooltip(Tooltip.create(Ui.t("hub.next"))).build());
    }
    private void turn(int button){if(minecraft!=null&&minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,button);}
    @Override protected void containerTick(){super.containerTick();prev.active=menu.page()>0;next.active=menu.page()<HubStorage.PAGES-1;}
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
        int rows=HubMenu.ROWS*18+17;
        g.blit(CHEST,leftPos,topPos,0,0,imageWidth,rows);
        g.blit(CHEST,leftPos,topPos+rows,0,126,imageWidth,96);
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){
        Ui.text(g,font,title,titleLabelX,titleLabelY,0x404040,imageWidth-58-titleLabelX);
        var page=Ui.t("hub.page",menu.page()+1,HubStorage.PAGES);
        Ui.text(g,font,page,imageWidth-31-font.width(page)/2,titleLabelY,0x404040);
        Ui.text(g,font,playerInventoryTitle,inventoryLabelX,inventoryLabelY,0x404040,70);
        var used=Ui.t("hub.used",menu.used(),HubStorage.SLOTS);
        Ui.text(g,font,used,imageWidth-8-font.width(used),inventoryLabelY,menu.used()>=HubStorage.SLOTS?0xB02020:0x404040);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){renderBackground(g);super.render(g,mx,my,partial);renderTooltip(g,mx,my);}

    @Mod.EventBusSubscriber(modid=ArsenalBeacon.ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
    public static final class HubRegistration {
        @SubscribeEvent public static void hubScreens(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent e){e.enqueueWork(()->net.minecraft.client.gui.screens.MenuScreens.register(ArsenalBeacon.LOGISTICS_HUB_MENU.get(),HubScreen::new));}
    }
}
