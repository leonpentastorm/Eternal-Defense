package dev.eternaldefense.beacon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

/** The Drink Station on one screen. */
final class DrinkStationScreen extends AbstractContainerScreen<DrinkStationMenu> {
    static final ResourceLocation STAND=new ResourceLocation("textures/gui/container/brewing_stand.png");
    /** Accent of each station tier: plank, olive, copper, canteen green. */
    static final int[] TIER={0xffc9a24b,0xff9cb35a,0xffd9824a,0xff6ee07a};
    static final int W=DrinkStationMenu.WIDTH,H=DrinkStationMenu.HEIGHT,RX=DrinkStationMenu.RX,RW=DrinkStationMenu.RW,SYRUP=0xffd2557a;
    static final int[] BUBBLES={29,24,20,16,11,6,0};
    static final int BREW=0,GATHER=1,MISSING=2;
    static final Set<String> GATHERABLE=Set.of("empty","missing","extra","cup");
    Ui.UiButton potTab,latteTab,primary,takeBack,clear,steps,upgrade,replaceYes,replaceNo;boolean confirming;
    final List<Ui.UiButton> pots=new ArrayList<>();final List<Tile> tiles=new ArrayList<>();Ui.UiButton nextPage;int tilePage;
    private int lastNotice;private long noticeAt;
    DrinkStationScreen(DrinkStationMenu menu,Inventory inv,Component title){super(menu,inv,title);imageWidth=W;imageHeight=H;}
    private CompoundTag view(){return menu.view;}
    private void action(int id){BeaconNetwork.menuButton(menu.containerId,id);}
    private int accent(){return TIER[Math.max(0,Math.min(3,menu.mk-1))];}
    private boolean mixing(){return DrinkRules.tier(menu.mk).mixing();}
    private boolean latte(){return view().getBoolean("Latte");}
    private boolean explicit(){return view().getIntArray("Order").length>0;}
    static MobEffect effect(ResourceLocation id){return ForgeRegistries.MOB_EFFECTS.getValue(id);}
    static void effectIcon(GuiGraphics g,MobEffect e,int x,int y,int size){if(e==null)return;var sprite=Minecraft.getInstance().getMobEffectTextures().get(e);g.blit(x,y,0,size,size,sprite);}

    /** One effect to choose: its vanilla icon; chosen ones are framed cyan with a check */
    final class Tile extends Button {
        final int index;
        Tile(int x,int y,int index){super(x,y,DrinkStationMenu.TILE,DrinkStationMenu.TILE,Component.empty(),b->action(DrinkStationMenu.EFFECT+index),DEFAULT_NARRATION);this.index=index;}
        int fit(){var f=view().getIntArray("Fit");return index<f.length?f[index]:0;}
        @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial){
            int x=getX(),y=getY(),s=DrinkStationMenu.TILE,fit=fit();boolean chosen=fit>=3,hover=isHoveredOrFocused();
            g.fill(x,y,x+s,y+s,chosen?Ui.CYAN_FILL:Ui.DEEP);
            if(index<menu.catalogue.size())effectIcon(g,effect(menu.catalogue.get(index)),x+1,y+1,16);
            if(!chosen&&!hover&&fit==0)g.fill(x+1,y+1,x+s-1,y+s-1,0xa0000000);
            int ring=chosen?Ui.CYAN:hover?Ui.INK:fit==2?Ui.EDGE:Ui.SLATE_HI;
            g.fill(x,y,x+s,y+1,ring);g.fill(x,y+s-1,x+s,y+s,ring);g.fill(x,y,x+1,y+s,ring);g.fill(x+s-1,y,x+s,y+s,ring);
            if(chosen){g.fill(x+10,y+10,x+s-1,y+s-1,0xe00a1218);Ui.check(g,x+11,y+11,fit==4?Ui.ORANGE:Ui.CYAN);}
        }
    }

    @Override protected void init(){
        super.init();pots.clear();tiles.clear();
        potTab=addRenderableWidget(new Ui.UiButton(leftPos+RX,topPos+DrinkStationMenu.TAB_Y,72,15,Component.empty(),Ui.Look.TAB,b->action(DrinkStationMenu.MODE_POT)).painter((g,self,h)->tab(g,self,new ItemStack(EternalDefense.BREW_POT_ITEM.get()),"drinks.tab.pot")).noLabel());
        latteTab=addRenderableWidget(new Ui.UiButton(leftPos+RX+76,topPos+DrinkStationMenu.TAB_Y,72,15,Component.empty(),Ui.Look.TAB,b->action(DrinkStationMenu.MODE_LATTE)).painter((g,self,h)->tab(g,self,new ItemStack(EternalDefense.PAPER_CUP.get()),"drinks.tab.latte")).noLabel());
        // The Brew Pots, as the Mess Hall's stew pots: the pot, its number, its drink's colour and a bar
        for(int i=0;i<3;i++){final int index=i;pots.add(addRenderableWidget(new Ui.UiButton(leftPos+RX+i*DrinkStationMenu.POT_PITCH,topPos+DrinkStationMenu.POTS_Y,DrinkStationMenu.POT_W,18,Component.empty(),Ui.Look.ROW,b->action(DrinkStationMenu.POT+index)).painter((g,self,h)->potChip(g,self,index)).noLabel()));}
        int by=topPos+DrinkStationMenu.BUTTON_Y;
        primary=addRenderableWidget(new Ui.UiButton(leftPos+RX,by,100,16,Component.empty(),Ui.Look.PRIMARY,b->{int mode=ctaMode();if(mode==GATHER)action(DrinkStationMenu.GATHER);else if(mode==BREW){if(replacing())confirming=true;else action(DrinkStationMenu.BREW);}}).painter(this::primaryFace).noLabel());
        takeBack=addRenderableWidget(new Ui.UiButton(leftPos+RX+104,by,62,16,Ui.t("drinks.take_back"),Ui.Look.NORMAL,b->action(DrinkStationMenu.TAKE_BACK)));
        clear=addRenderableWidget(new Ui.UiButton(leftPos+RX+170,by,54,16,Ui.t("drinks.clear"),Ui.Look.NORMAL,b->action(DrinkStationMenu.CLEAR)));
        steps=addRenderableWidget(new Ui.UiButton(leftPos+DrinkStationMenu.STEPS_X,topPos+DrinkStationMenu.SLOT_Y+1,DrinkStationMenu.STEPS_W,14,Component.empty(),Ui.Look.NORMAL,b->action(DrinkStationMenu.STEPS)).painter((g,self,h)->Ui.text(g,font,Ui.t("drinks.steps",view().getInt("Steps")*10),self.getX()+4,self.getY()+3,self.active?Ui.INK:Ui.DISABLED,self.getWidth()-6)).noLabel());
        upgrade=addRenderableWidget(new Ui.UiButton(leftPos+118,topPos+82,58,14,Component.empty(),Ui.Look.NORMAL,b->action(DrinkStationMenu.UPGRADE)).painter(this::upgradeFace).noLabel());
        replaceYes=addRenderableWidget(new Ui.UiButton(leftPos+W/2-122,topPos+H/2+22,118,16,Ui.t("drinks.replace.yes"),Ui.Look.DANGER,b->{confirming=false;action(DrinkStationMenu.REPLACE);}));
        replaceNo=addRenderableWidget(new Ui.UiButton(leftPos+W/2+4,topPos+H/2+22,118,16,Ui.t("drinks.replace.no"),Ui.Look.PRIMARY,b->confirming=false));
        replaceYes.visible=replaceNo.visible=false;confirming=false;
        nextPage=addRenderableWidget(new Ui.UiButton(leftPos+RX+(DrinkStationMenu.TILE_COLS-1)*DrinkStationMenu.TILE_PITCH,topPos+DrinkStationMenu.TILE_Y,DrinkStationMenu.TILE,DrinkStationMenu.TILE,Component.literal("›"),Ui.Look.NORMAL,b->{tilePage++;buildTiles();}));
        buildTiles();
    }
    /** One row of tiles: all of them when they fit (vanilla has 9), else 10 a page and a button to the next page. */
    private int perPage(){return menu.catalogue.size()>DrinkStationMenu.TILE_COLS?DrinkStationMenu.TILE_COLS-1:DrinkStationMenu.TILE_COLS;}
    private int pages(){return Math.max(1,(menu.catalogue.size()+perPage()-1)/perPage());}
    /** The tiles come with the effect list, which arrives with the first view after the screen opens. */
    private void buildTiles(){
        for(var t:tiles)removeWidget(t);tiles.clear();tilePage=tilePage%pages();builtFor=menu.catalogue.size();
        int first=tilePage*perPage();
        for(int i=first;i<menu.catalogue.size()&&i<first+perPage();i++)tiles.add(addRenderableWidget(new Tile(leftPos+RX+(i-first)*DrinkStationMenu.TILE_PITCH,topPos+DrinkStationMenu.TILE_Y,i)));
    }
    private int builtFor=-1;
    @Override protected void containerTick(){super.containerTick();if(builtFor!=menu.catalogue.size())buildTiles();}

    // Buttons
    private boolean replacing(){return !latte()&&view().getString("Problem").equals("pot_full");}
    private int ctaMode(){String p=view().getString("Problem");if(explicit()&&GATHERABLE.contains(p))return view().getBoolean("Gatherable")?GATHER:MISSING;return BREW;}
    private void tab(GuiGraphics g,Ui.UiButton b,ItemStack icon,String key){
        g.pose().pushPose();g.pose().translate(b.getX()+3,b.getY(),0);g.pose().scale(.8f,.8f,1);g.renderItem(icon,0,0);g.pose().popPose();
        Ui.text(g,font,Ui.t(key),b.getX()+19,b.getY()+4,b.selected?Ui.INK:Ui.MUTED,b.getWidth()-22);
    }
    /** A Brew Pot chip, drawn as the Mess Hall's stew pot chip */
    private void potChip(GuiGraphics g,Ui.UiButton b,int index){
        var list=view().getList("Pots",Tag.TAG_COMPOUND);if(index>=list.size())return;var drink=DrinkData.load(list.getCompound(index).getCompound("Drink"));
        g.pose().pushPose();g.pose().translate(b.getX()+2,b.getY()+1,0);g.pose().scale(.75f,.75f,1f);g.renderItem(new ItemStack(EternalDefense.BREW_POT_ITEM.get()),0,0);g.pose().popPose();
        Ui.text(g,font,Component.literal(Integer.toString(index+1)),b.getX()+18,b.getY()+3,b.selected?Ui.INK:Ui.MUTED,12);
        int color=drink==null?Ui.TRACK:0xff000000|drink.color();
        if(drink!=null)g.fill(b.getX()+27,b.getY()+4,b.getX()+31,b.getY()+8,color);
        Ui.bar(g,b.getX()+3,b.getY()+13,b.getWidth()-6,3,drink==null?0:1,color);
    }
    private void primaryFace(GuiGraphics g,Ui.UiButton b,boolean hover){
        int mode=ctaMode();
        if(b.active){if(mode==GATHER)g.renderItem(new ItemStack(Items.HOPPER),b.getX()+3,b.getY());else g.renderItem(new ItemStack(Items.BREWING_STAND),b.getX()+3,b.getY());}
        else if(mode==MISSING)Ui.alert(g,b.getX()+8,b.getY()+5,Ui.ORANGE);
        var label=Ui.t(mode==GATHER?"drinks.gather":mode==MISSING?"drinks.missing":latte()?"drinks.make_latte":"drinks.brew");
        Ui.text(g,font,label,b.getX()+22,b.getY()+4,b.active?Ui.INK:mode==MISSING?Ui.ORANGE:Ui.DISABLED,b.getWidth()-25);
    }
    private void upgradeFace(GuiGraphics g,Ui.UiButton b,boolean hover){
        var v=view();if(!v.contains("UpgradeItem"))return;var item=ItemStack.of(v.getCompound("UpgradeItem")).copyWithCount(1);int cost=v.getInt("UpgradeCost"),have=v.getInt("UpgradeHave");
        Ui.text(g,font,Ui.t("drinks.upgrade_short",menu.mk+1),b.getX()+4,b.getY()+3,b.active?Ui.INK:Ui.MUTED,30);
        var count=Component.literal(Integer.toString(cost));int right=b.getX()+b.getWidth()-3;g.drawString(font,count,right-font.width(count),b.getY()+3,have<cost?Ui.ORANGE:Ui.INK,false);
        g.pose().pushPose();g.pose().translate(right-font.width(count)-9,b.getY()+3,0);g.pose().scale(.5f,.5f,1f);g.renderItem(item,0,0);g.pose().popPose();
    }

    // Background
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
        var v=view();int accent=accent();
        Ui.panel(g,leftPos,topPos,W,H);Ui.header(g,font,leftPos,topPos,W,title,null);
        var tierLabel=Ui.t("drinks.tier",menu.mk);int tw=font.width(tierLabel),bx=leftPos+W-tw-Ui.pipsWidth(4)-26;
        g.fill(bx-5,topPos+5,bx+tw+Ui.pipsWidth(4)+13,topPos+21,Ui.DEEP);g.fill(bx-5,topPos+5,bx-2,topPos+21,accent);
        g.drawString(font,tierLabel,bx+2,topPos+9,accent,false);Ui.pips(g,bx+tw+8,topPos+11,menu.mk,4,accent);
        g.fill(leftPos+184,topPos+29,leftPos+185,topPos+H-6,Ui.EDGE);
        stand(g,v);storage(g,mx,my,v);
        boolean mix=mixing(),isLatte=latte();
        potTab.visible=latteTab.visible=mix;potTab.selected=!isLatte;latteTab.selected=isLatte;
        var list=v.getList("Pots",Tag.TAG_COMPOUND);
        for(int i=0;i<pots.size();i++){var b=pots.get(i);b.visible=mix&&!isLatte&&i<list.size();if(b.visible){b.selected=v.contains("Selected")&&v.getLong("Selected")==list.getCompound(i).getLong("Pos");b.accent=accent;}}
        primary.visible=takeBack.visible=mix;clear.visible=mix&&explicit();steps.visible=mix&&!isLatte;nextPage.visible=mix&&pages()>1;
        upgrade.visible=menu.mk<4;upgrade.active=v.contains("UpgradeCost")&&v.getInt("UpgradeHave")>=v.getInt("UpgradeCost");
        for(var t:tiles)t.visible=mix;
        if(!mix)locked(g);else{strip(g);potRow(g,v,isLatte);table(g,v,isLatte);preview(g,v);}
        Ui.inventory(g,leftPos+RX,topPos+DrinkStationMenu.INV_Y-1);
    }
    /** The vanilla brewing stand's art and its fuel, bubbles and arrow, then what it is doing in words. */
    private void stand(GuiGraphics g,CompoundTag v){
        int x=leftPos+DrinkStationMenu.BREW_X,y=topPos+DrinkStationMenu.BREW_Y;
        Ui.inset(g,x-1,y-1,DrinkStationMenu.BREW_W+2,DrinkStationMenu.BREW_H+2);
        g.blit(STAND,x,y,DrinkStationMenu.BREW_U,DrinkStationMenu.BREW_V,DrinkStationMenu.BREW_W,DrinkStationMenu.BREW_H);
        int fuel=v.getInt("Fuel"),time=v.getInt("BrewTime");
        int fuelW=Math.max(0,Math.min(18,(18*fuel+DrinkRules.FUEL_USES-1)/DrinkRules.FUEL_USES));
        if(fuelW>0)g.blit(STAND,leftPos+DrinkStationMenu.brewSlotX(60),topPos+DrinkStationMenu.brewSlotY(44),176,29,fuelW,4);
        if(time>0){
            int arrow=(int)(28f*(1f-time/(float)DrinkRules.BREW_TICKS));if(arrow>0)g.blit(STAND,leftPos+DrinkStationMenu.brewSlotX(97),topPos+DrinkStationMenu.brewSlotY(16),176,0,9,arrow);
            int bubble=BUBBLES[time/2%7];if(bubble>0)g.blit(STAND,leftPos+DrinkStationMenu.brewSlotX(63),topPos+DrinkStationMenu.brewSlotY(14)+29-bubble,185,29-bubble,12,bubble);
        }
        int ix=leftPos+118;
        Ui.text(g,font,Ui.t("drinks.stand.fuel"),ix,topPos+31,Ui.MUTED,58);Ui.bar(g,ix,topPos+41,56,4,fuel/(float)DrinkRules.FUEL_USES,Ui.BRASS);
        Ui.text(g,font,time>0?Ui.t("drinks.stand.brewing",(time+19)/20):Ui.t("drinks.stand.idle"),ix,topPos+50,time>0?Ui.CYAN:Ui.MUTED,58);
    }
    private int storeIndexAt(double mx,double my){
        int x=(int)mx-leftPos-DrinkStationMenu.STORE_X,y=(int)my-topPos-DrinkStationMenu.STORE_Y;
        if(x<0||y<0||x>=DrinkStationMenu.STORE_COLS*18||y>=6*18)return -1;return y/18*DrinkStationMenu.STORE_COLS+x/18;
    }
    private CompoundTag storedAt(int index){for(var t:view().getList("Storage",Tag.TAG_COMPOUND)){var c=(CompoundTag)t;if(c.getByte("Slot")==index)return c;}return null;}
    private void storage(GuiGraphics g,int mx,int my,CompoundTag v){
        int x0=leftPos+DrinkStationMenu.STORE_X,y0=topPos+DrinkStationMenu.STORE_Y;int total=v.getInt("Total");
        Ui.text(g,font,Ui.t("drinks.storage"),x0,y0-12,Ui.MUTED,90);
        Ui.right(g,font,Ui.t("drinks.storage_count",total),x0+162,y0-12,DrinkRules.guarded(total)?Ui.ORANGE:Ui.MUTED);
        for(int i=0;i<DrinkRules.STORAGE_SLOTS;i++)Ui.slot(g,x0+i%9*18,y0+i/9*18,Ui.EDGE);
        int hover=storeIndexAt(mx,my);
        for(var t:v.getList("Storage",Tag.TAG_COMPOUND)){
            var c=(CompoundTag)t;int i=c.getByte("Slot");if(i<0||i>=DrinkRules.STORAGE_SLOTS)continue;var stack=ItemStack.of(c);int sx=x0+i%9*18+1,sy=y0+i/9*18+1;
            g.renderItem(stack,sx,sy);g.renderItemDecorations(font,stack,sx,sy,Integer.toString(c.getInt("Bottles")));
        }
        if(hover>=0){int hx=x0+hover%9*18+1,hy=y0+hover/9*18+1;g.pose().pushPose();g.pose().translate(0,0,300);g.fill(hx,hy,hx+16,hy+16,0x80ffffff);g.pose().popPose();}
        Ui.text(g,font,Ui.t("drinks.storage_hint"),x0,y0+6*18+3,Ui.DISABLED,162);
    }
    /** Mk I: the station is a brewing stand with storage. */
    private void locked(GuiGraphics g){
        int x=leftPos+RX,y=topPos+DrinkStationMenu.TAB_Y;
        Ui.card(g,x,y,RW,64,Ui.TRACK);Ui.glyph(g,x+10,y+10,new String[]{".###.","#...#","#...#","#####","#####","#####","#####"},Ui.DISABLED);
        Ui.text(g,font,Ui.t("drinks.locked.title"),x+24,y+9,Ui.INK,RW-30);
        Ui.wrap(g,font,Ui.t("drinks.locked.body"),x+24,y+22,RW-30,Ui.MUTED,4);
    }
    /** Top of the right column: for a few seconds the server's last word, otherwise what to do next. */
    private void strip(GuiGraphics g){
        int x=leftPos+RX,y=topPos+DrinkStationMenu.STRIP_Y,h=14;var toast=toast();
        if(toast!=null){int kind=view().getInt("NoticeKind");Ui.card(g,x,y,RW,h,kind==1?Ui.ORANGE:kind==2?Ui.CYAN:Ui.CYAN_DIM);Ui.text(g,font,toast,x+7,y+3,Ui.INK,RW-10);return;}
        var p=view().getString("Problem");boolean ready=view().contains("Problem")&&p.isEmpty()||replacing();
        Ui.inset(g,x,y,RW,h);
        if(replacing())Ui.alert(g,x+5,y+4,Ui.ORANGE);else if(ready)Ui.check(g,x+4,y+5,Ui.CYAN);else Ui.alert(g,x+5,y+4,Ui.ORANGE);
        Ui.text(g,font,hint(p),x+14,y+3,replacing()?Ui.ORANGE:ready?Ui.CYAN:Ui.ORANGE,RW-18);
    }
    private Component hint(String problem){
        if(!view().contains("Problem"))return Component.empty();
        if(problem.isEmpty())return Ui.t(latte()?"drinks.hint.ready_latte":"drinks.hint.ready");
        if(explicit()&&GATHERABLE.contains(problem))return Ui.t(view().getBoolean("Gatherable")?"drinks.hint.gather":"drinks.hint.missing");
        return Ui.t("drinks.hint."+problem,DrinkRules.sweetenerCost(view().getInt("Steps")),view().getInt("Tank"));
    }
    private Component toast(){
        int seq=view().getInt("NoticeSeq");if(seq!=lastNotice){lastNotice=seq;noticeAt=net.minecraft.Util.getMillis();}
        if(seq==0||net.minecraft.Util.getMillis()-noticeAt>4200)return null;
        String key=view().getString("Notice");if(key.isEmpty())return null;
        var list=view().getList("NoticeArgs",Tag.TAG_STRING);var raw=new ArrayList<String>();for(int i=0;i<list.size();i++)raw.add(list.getString(i));
        return notice(font,key,raw,RW-10);
    }
    /** A notice in one line of width: effect lists name every effect, or the first and "+N" when that would not fit. */
    static Component notice(net.minecraft.client.gui.Font font,String key,List<String> raw,int width){
        var full=Ui.t("drinks.notice."+key,raw.stream().map(r->arg(r,false)).toArray());
        return font.width(full)<=width?full:Ui.t("drinks.notice."+key,raw.stream().map(r->arg(r,true)).toArray());
    }
    private static Object arg(String raw,boolean compact){
        if(!raw.startsWith("fx:"))return raw;
        var names=Arrays.stream(raw.substring(3).split(",")).map(id->{var e=effect(ResourceLocation.tryParse(id));return e==null?id:e.getDisplayName().getString();}).toList();
        return compact&&names.size()>1?names.get(0)+" +"+(names.size()-1):String.join(", ",names);
    }
    /** The mixing slots: the Latte's cup and output around them, or the Brew Pot */
    private void table(GuiGraphics g,CompoundTag v,boolean isLatte){
        int y=topPos+DrinkStationMenu.SLOT_Y-1,accent=accent();var order=v.getIntArray("Order");int limit=v.getInt("Limit");
        if(isLatte){
            Ui.slot(g,leftPos+DrinkStationMenu.CUP_X-1,y,accent);if(menu.mix.getItem(3).isEmpty())Ui.ghost(g,new ItemStack(EternalDefense.PAPER_CUP.get()),leftPos+DrinkStationMenu.CUP_X,y+1);
            Ui.slot(g,leftPos+DrinkStationMenu.OUT_X-1,y,accent);Ui.text(g,font,Component.literal("→"),leftPos+DrinkStationMenu.OUT_X-12,y+5,Ui.MUTED);
        }else{g.renderItem(new ItemStack(EternalDefense.BREW_POT_ITEM.get()),leftPos+DrinkStationMenu.CUP_X,y+1);sweetener(g,v);}
        Ui.text(g,font,Component.literal("+"),leftPos+DrinkStationMenu.CUP_X+18,y+5,Ui.MUTED);
        int shown=isLatte?DrinkRules.LATTE_MAX:3;
        for(int i=0;i<shown;i++){
            int x=leftPos+DrinkStationMenu.MIX_X+i*DrinkStationMenu.MIX_PITCH-1;
            if(i>=limit){g.fill(x,y,x+18,y+18,Ui.SLATE);Ui.glyph(g,x+6,y+5,new String[]{".###.","#...#","#####","#####","#####"},Ui.DISABLED);continue;}
            Ui.slot(g,x,y,Ui.EDGE);
            if(menu.mix.getItem(i).isEmpty()&&i<order.length&&order[i]<menu.catalogue.size()){var e=effect(menu.catalogue.get(order[i]));if(e!=null){var sample=DrinkData.sample(e,0);if(!sample.isEmpty())Ui.ghost(g,sample,x+1,y+1);}}
        }
    }
    private void preview(GuiGraphics g,CompoundTag v){
        int x=leftPos+RX,y=topPos+DrinkStationMenu.PREVIEW_Y;var drink=v.contains("Preview")?DrinkData.load(v.getCompound("Preview")):null;
        if(drink==null){Ui.text(g,font,Ui.t(latte()?"drinks.preview.none_latte":"drinks.preview.none"),x,y,Ui.DISABLED,RW);return;}
        var parts=new ArrayList<String>();for(var p:drink.parts())parts.add(p.describe().getString());
        g.fill(x,y+1,x+6,y+7,0xff000000|drink.color());
        Ui.text(g,font,Ui.t("drinks.preview",String.join(" + ",parts),DrinkRules.minutes(drink.duration())),x+10,y,Ui.INK,RW-10);
    }
    /** Under the tiles: the Brew Pots (Brew Pot tab), or how a Latte is made (Latte tab) */
    private void potRow(GuiGraphics g,CompoundTag v,boolean isLatte){
        int x=leftPos+RX,y=topPos+DrinkStationMenu.POTS_Y;
        if(isLatte){Ui.text(g,font,Ui.t("drinks.latte_hint"),x,y+5,Ui.MUTED,RW);return;}
        if(v.getList("Pots",Tag.TAG_COMPOUND).isEmpty())Ui.text(g,font,Ui.t("drinks.no_pots"),x,y+5,Ui.ORANGE,RW);
    }
    /** The sweetener, in the mixing row (Brew Pot tab): its slot, a bar with a mark every 1,000 mB and the mB */
    private void sweetener(GuiGraphics g,CompoundTag v){
        int mb=v.getInt("Tank"),sx=leftPos+DrinkStationMenu.SWEET_X-1,y=topPos+DrinkStationMenu.SLOT_Y-1;
        Ui.slot(g,sx,y,SYRUP);if(menu.mix.getItem(5).isEmpty())Ui.ghost(g,DrinkData.sample(net.minecraft.world.effect.MobEffects.REGENERATION,0),sx+1,y+1);
        int bx=leftPos+DrinkStationMenu.BAR_X,bw=DrinkStationMenu.BAR_W,need=DrinkRules.sweetenerCost(v.getInt("Steps"));
        g.fill(bx,y+1,bx+bw,y+8,Ui.TRACK);int fill=Math.round(bw*Math.min(1f,mb/(float)DrinkRules.TANK_MB));if(fill>0){g.fill(bx,y+1,bx+fill,y+8,SYRUP);g.fill(bx,y+1,bx+fill,y+2,0x55ffffff);}
        for(int i=1;i<DrinkRules.TANK_MB/DrinkRules.STEP_MB;i++){int tx=bx+bw*i/(DrinkRules.TANK_MB/DrinkRules.STEP_MB);g.fill(tx,y+1,tx+1,y+8,Ui.DEEP);}
        Ui.text(g,font,Ui.t("drinks.mb_amount",String.format("%,d",mb)),bx,y+10,need>mb?Ui.ORANGE:Ui.MUTED,bw);
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){}

    // Input
    @Override public boolean mouseClicked(double mx,double my,int button){
        if(confirming)return replaceYes.mouseClicked(mx,my,button)||replaceNo.mouseClicked(mx,my,button)||true;
        int index=storeIndexAt(mx,my);
        if(index>=0&&(button==0||button==1)){action(DrinkStationMenu.STORE+index*2+(hasShiftDown()?1:0));return true;}
        return super.mouseClicked(mx,my,button);
    }
    @Override public boolean mouseReleased(double mx,double my,int button){return confirming||super.mouseReleased(mx,my,button);}
    @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy){return confirming||super.mouseDragged(mx,my,button,dx,dy);}
    @Override public boolean keyPressed(int key,int scan,int mods){if(confirming){if(key==256)confirming=false;return true;}return super.keyPressed(key,scan,mods);}

    // Tooltips
    private void tip(GuiGraphics g,String key,int mx,int my,Object... args){g.renderTooltip(font,font.split(Ui.t(key,args),200),mx,my);}
    private void tooltips(GuiGraphics g,int mx,int my){
        if(hoveredSlot!=null&&hoveredSlot.hasItem())return;
        int index=storeIndexAt(mx,my);
        if(index>=0){var c=storedAt(index);if(c==null){tip(g,"drinks.tip.storage_empty",mx,my);return;}
            var stack=ItemStack.of(c);var lines=new ArrayList<>(getTooltipFromItem(minecraft,stack));lines.add(Ui.t("drinks.tip.stored",c.getInt("Bottles")).copy().withStyle(net.minecraft.ChatFormatting.GOLD));
            lines.add(Ui.t(DrinkData.mixable(stack)?"drinks.tip.mixable":"drinks.tip.storage_only").copy().withStyle(net.minecraft.ChatFormatting.GRAY));
            lines.add(Ui.t("drinks.tip.storage_take").copy().withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
            g.renderComponentTooltip(font,lines,mx,my);return;}
        for(var t:tiles)if(t.visible&&t.isHovered()){tileTip(g,t,mx,my);return;}
        var toast=toast();if(toast!=null&&Ui.inside(mx,my,leftPos+RX,topPos+DrinkStationMenu.STRIP_Y,RW,14)){g.renderTooltip(font,font.split(toast,200),mx,my);return;}
        if(hoveredSlot!=null&&!hoveredSlot.hasItem()&&hoveredSlot.container==menu.mix){int s=hoveredSlot.getContainerSlot();tip(g,s==5?"drinks.tip.sweet_slot":s==3?"drinks.tip.cup_slot":s==4?"drinks.tip.out_slot":"drinks.tip.mix_slot",mx,my,DrinkRules.REGEN_I_MB,DrinkRules.REGEN_II_MB);return;}
        if(hoveredSlot!=null&&!hoveredSlot.hasItem()&&hoveredSlot.container==menu.brew){int s=hoveredSlot.getContainerSlot();tip(g,s==4?"drinks.tip.fuel":s==3?"drinks.tip.ingredient":"drinks.tip.bottle",mx,my);return;}
        if(potTab.visible&&potTab.isHovered())tip(g,"drinks.tip.pot_tab",mx,my,DrinkRules.minutes(DrinkRules.BASE_TICKS));
        else if(latteTab.visible&&latteTab.isHovered())tip(g,"drinks.tip.latte_tab",mx,my,DrinkRules.minutes(DrinkRules.BASE_TICKS),DrinkRules.LATTE_STACK);
        else if(primary.visible&&primary.isHovered())primaryTip(g,mx,my);
        else if(takeBack.visible&&takeBack.isHovered())tip(g,"drinks.tip.take_back",mx,my);
        else if(clear.visible&&clear.isHovered())tip(g,"drinks.tip.clear",mx,my);
        else if(steps.visible&&steps.isHovered())tip(g,latte()?"drinks.tip.steps_latte":"drinks.tip.steps",mx,my,DrinkRules.STEP_MB,view().getInt("Tank"));
        else if(upgrade.visible&&upgrade.isHovered())Ui.materialTooltip(g,font,List.of(Ui.t("drinks.upgrade",menu.mk+1),Ui.t("drinks.tier_gain."+(menu.mk+1))),Ui.singleCost(ItemStack.of(view().getCompound("UpgradeItem")),view().getInt("UpgradeCost"),view().getInt("UpgradeHave")),mx,my);
        else if(!latte()&&mixing()&&Ui.inside(mx,my,leftPos+DrinkStationMenu.BAR_X,topPos+DrinkStationMenu.SLOT_Y-1,DrinkStationMenu.BAR_W,18))tip(g,"drinks.tip.tank",mx,my,String.format("%,d",view().getInt("Tank")),String.format("%,d",DrinkRules.TANK_MB),DrinkRules.REGEN_I_MB,DrinkRules.REGEN_II_MB,DrinkRules.STEP_MB);
        else if(Ui.inside(mx,my,leftPos+118,topPos+30,58,50))tip(g,"drinks.tip.stand",mx,my);
        else for(int i=0;i<pots.size();i++)if(pots.get(i).visible&&pots.get(i).isHovered()){potTip(g,i,mx,my);return;}
    }
    private void tileTip(GuiGraphics g,Tile t,int mx,int my){
        if(t.index>=menu.catalogue.size())return;var e=effect(menu.catalogue.get(t.index));if(e==null)return;var lines=new ArrayList<Component>();
        lines.add(e.getDisplayName().copy().withStyle(s->s.withColor(e.getColor())));
        int fit=t.fit();
        lines.add(Ui.t(fit==3?"drinks.tip.tile_chosen":fit==4?"drinks.tip.tile_chosen_lacking":fit==2?"drinks.tip.tile_ready":"drinks.tip.tile_none"));
        lines.add(Ui.t("drinks.tip.tile_how").copy().withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        g.renderComponentTooltip(font,lines,mx,my);
    }
    private void potTip(GuiGraphics g,int i,int mx,int my){
        var list=view().getList("Pots",Tag.TAG_COMPOUND);if(i>=list.size())return;var row=list.getCompound(i);var pos=net.minecraft.core.BlockPos.of(row.getLong("Pos"));var drink=DrinkData.load(row.getCompound("Drink"));
        var lines=new ArrayList<Component>();lines.add(Ui.t("drinks.pot_at",i+1,pos.getX(),pos.getY(),pos.getZ()));
        if(drink==null)lines.add(Ui.t("drinks.pot_empty_short"));else{lines.add(drink.name());for(var p:drink.parts())lines.add(p.describe());lines.add(Ui.t("drinks.minutes",DrinkRules.minutes(drink.duration())));}
        g.renderComponentTooltip(font,lines,mx,my);
    }
    private void primaryTip(GuiGraphics g,int mx,int my){
        int mode=ctaMode();String p=view().getString("Problem");
        if(mode==GATHER){tip(g,"drinks.tip.gather",mx,my);return;}
        if(mode==MISSING){tip(g,"drinks.tip.missing",mx,my);return;}
        if(replacing()){tip(g,"drinks.tip.replace",mx,my);return;}
        if(p.isEmpty()){tip(g,latte()?"drinks.tip.make_latte":"drinks.tip.brew",mx,my);return;}
        tip(g,"drinks.problem."+p,mx,my,DrinkRules.sweetenerCost(view().getInt("Steps")),view().getInt("Tank"));
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        var v=view();int mode=ctaMode();String p=v.getString("Problem");
        primary.active=v.contains("Problem")&&(mode==GATHER||mode==BREW&&(p.isEmpty()||replacing()));
        takeBack.active=!menu.mix.getItem(0).isEmpty()||!menu.mix.getItem(1).isEmpty()||!menu.mix.getItem(2).isEmpty()||!menu.mix.getItem(3).isEmpty();
        if(confirming&&!replacing())confirming=false;
        replaceYes.visible=replaceNo.visible=confirming;
        renderBackground(g);
        if(confirming){super.render(g,-1,-1,partial);confirmation(g,mx,my);return;}
        super.render(g,mx,my,partial);renderTooltip(g,mx,my);tooltips(g,mx,my);
    }
    /** The question before a pot that still holds a drink is brewed over. */
    private void confirmation(GuiGraphics g,int mx,int my){
        g.pose().pushPose();g.pose().translate(0,0,400);g.fill(0,0,width,height,0xb0050a0e);
        int w=260,h=96,x=leftPos+W/2-w/2,y=topPos+H/2-h/2+4;
        Ui.panel(g,x,y,w,h);g.fill(x,y,x+w,y+2,Ui.ORANGE);Ui.alert(g,x+10,y+11,Ui.ORANGE);g.drawString(font,Ui.t("drinks.replace.title"),x+24,y+10,Ui.INK,false);
        var v=view();var list=v.getList("Pots",Tag.TAG_COMPOUND);int number=1;DrinkData old=null;
        for(int i=0;i<list.size();i++)if(v.contains("Selected")&&list.getCompound(i).getLong("Pos")==v.getLong("Selected")){number=i+1;old=DrinkData.load(list.getCompound(i).getCompound("Drink"));}
        Ui.wrap(g,font,Ui.t("drinks.replace.body",number,old==null?"?":old.name().getString()),x+12,y+26,w-24,Ui.MUTED,4);
        replaceYes.render(g,mx,my,0);replaceNo.render(g,mx,my,0);g.pose().popPose();
    }
}
