package dev.eternaldefense.beacon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;

/** The Handheld Radio: four flare slots, the flare a right-click plants, and the Loadout kit a call brings. */
final class RadioScreen extends AbstractContainerScreen<HandheldRadio.RadioMenu> {
    static final int W=HandheldRadio.RadioMenu.W,H=HandheldRadio.RadioMenu.H,COL=HandheldRadio.RadioMenu.COL,FLARE_X=HandheldRadio.RadioMenu.FLARE_X,
        CALL_Y=68,LOADOUT_CARD=90,COIN_Y=106,COIN_W=18,HINT_Y=142;
    private final List<Ui.UiButton> calls=new ArrayList<>(),coins=new ArrayList<>();
    RadioScreen(HandheldRadio.RadioMenu menu,Inventory inv,Component title){super(menu,inv,title);imageWidth=W;imageHeight=H;}
    @Override protected void init(){
        super.init();calls.clear();coins.clear();
        for(int i=0;i<HandheldRadio.KINDS.length;i++){int id=i;
            calls.add(addRenderableWidget(new Ui.UiButton(leftPos+FLARE_X+i*COL+1,topPos+CALL_Y,COL-2,14,Ui.t("radio.call."+HandheldRadio.KINDS[i].id),Ui.Look.NORMAL,b->BeaconNetwork.menuButton(menu.containerId,id))));
        }
        int coinX=leftPos+(W-LoadoutRules.COIN_SLOTS*COIN_W)/2;
        for(int i=0;i<LoadoutRules.COIN_SLOTS;i++){int slot=i;
            coins.add(addRenderableWidget(new Ui.UiButton(coinX+i*COIN_W,topPos+COIN_Y,COIN_W,24,Component.empty(),Ui.Look.NORMAL,b->BeaconNetwork.menuButton(menu.containerId,HandheldRadio.RadioMenu.LOADOUT+slot))
                .noLabel().painter((g,b,hover)->paintCoin(g,b,slot))));
        }
        refresh();
    }
    private ItemStack shown(int slot){return !menu.coins[slot].isEmpty()?menu.coins[slot]:menu.kinds[slot];}
    private void paintCoin(GuiGraphics g,Ui.UiButton b,int slot){
        var stack=shown(slot);int x=b.getX(),y=b.getY();
        Ui.text(g,font,Component.literal(String.valueOf(slot+1)),x+(COIN_W-font.width(String.valueOf(slot+1)))/2,y+2,slot==menu.active.get()?Ui.CYAN:Ui.MUTED);
        if(!stack.isEmpty()){if(menu.coins[slot].isEmpty())Ui.ghost(g,stack,x+1,y+8);else g.renderItem(stack,x+1,y+8);}
    }
    private void refresh(){
        for(int i=0;i<calls.size();i++){calls.get(i).selected=menu.kind.get()==i;calls.get(i).accent(Ui.BRASS);}
        for(int i=0;i<coins.size();i++){var b=coins.get(i);b.visible=menu.station;b.selected=menu.active.get()==i;b.active=!shown(i).isEmpty()||menu.active.get()==i;}
    }
    @Override protected void containerTick(){super.containerTick();refresh();}
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
        int x=leftPos,y=topPos;
        Ui.panel(g,x,y,W,H);Ui.header(g,font,x,y,W,title,Ui.t("radio.subtitle"));
        Ui.card(g,x+6,y+28,W-12,58,Ui.BRASS);
        Ui.text(g,font,Ui.t("radio.flares",SupportRules.RADIO_FLARES),x+11,y+32,Ui.MUTED,W-22);
        for(int i=0;i<HandheldRadio.KINDS.length;i++){
            int sx=x+FLARE_X+i*COL+COL/2-8,sy=y+HandheldRadio.RadioMenu.FLARE_Y;boolean chosen=menu.kind.get()==i;
            Ui.slot(g,sx-1,sy-1,chosen?Ui.BRASS:Ui.EDGE);
            if(menu.flares.getItem(i).isEmpty())Ui.ghost(g,new ItemStack(HandheldRadio.flare(HandheldRadio.KINDS[i])),sx,sy);
        }
        Ui.card(g,x+6,y+LOADOUT_CARD,W-12,48,Ui.CYAN);
        Ui.text(g,font,Ui.t("radio.loadout"),x+11,y+LOADOUT_CARD+4,Ui.MUTED,W-22);
        if(!menu.station)Ui.wrap(g,font,Ui.t("radio.no_station"),x+11,y+COIN_Y+2,W-22,Ui.ORANGE,3);
        var use=Minecraft.getInstance().options.keyUse.getTranslatedKeyMessage();
        Ui.keyHint(g,font,use,Ui.t("radio.plant_hint"),x+8,y+HINT_Y,W-16);
        Ui.text(g,font,playerInventoryTitle,x+HandheldRadio.RadioMenu.INV_X,y+HandheldRadio.RadioMenu.INV_Y-12,Ui.MUTED,W-16);
        Ui.inventory(g,x+HandheldRadio.RadioMenu.INV_X-1,y+HandheldRadio.RadioMenu.INV_Y-1);
        if(menu.locked>=0){int lx=x+HandheldRadio.RadioMenu.INV_X+menu.locked*18,ly=y+HandheldRadio.RadioMenu.INV_Y+58;g.fill(lx,ly,lx+16,ly+16,0x55c9a24b);}
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        renderBackground(g);super.render(g,mx,my,partial);renderTooltip(g,mx,my);
        if(hoveredSlot!=null){
            if(hoveredSlot.container==menu.flares&&!hoveredSlot.hasItem())g.renderTooltip(font,font.split(Ui.t("radio.slot_tip",HandheldRadio.flare(HandheldRadio.KINDS[hoveredSlot.getContainerSlot()]).getDescription()),190),mx,my);
            return;
        }
        if(Ui.inside(mx,my,leftPos+8,topPos+HINT_Y,W-16,13)){g.renderTooltip(font,font.split(Ui.t("radio.plant_tip",SupportRules.RADIO_RANGE),190),mx,my);return;}
        for(int i=0;i<calls.size();i++)if(calls.get(i).isHovered()){g.renderTooltip(font,font.split(Ui.t("radio.call_tip",HandheldRadio.flare(HandheldRadio.KINDS[i]).getDescription()),190),mx,my);return;}
        for(int i=0;i<coins.size();i++)if(coins.get(i).visible&&coins.get(i).isHovered()){
            var lines=new ArrayList<net.minecraft.util.FormattedCharSequence>();var stack=shown(i);
            lines.addAll(font.split(Ui.t("radio.coin_title",i+1,menu.coins[i].getCount()),190));
            if(!stack.isEmpty())for(var line:getTooltipFromItem(minecraft,stack))lines.addAll(font.split(line,190));
            lines.addAll(font.split(Ui.t(i==menu.active.get()?"radio.coin_active":"radio.coin_pick").copy().withStyle(net.minecraft.ChatFormatting.AQUA),190));
            g.renderTooltip(font,lines,mx,my);return;
        }
    }
}
