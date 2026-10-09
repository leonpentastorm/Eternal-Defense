package dev.createarsenal.beacon;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;
import java.util.*;

/**
 * The kitchen on one screen, in two columns. Left: the table and every button that does something (mode, Gather, Cook, Take back, Clear, Upgrade).
 * Right: the effects to choose, the order they make and what this hall can do. The server owns all state (order, table, pots); this screen only
 * reads the preview it sends and presses buttons.
 */
final class MessHallScreen extends AbstractContainerScreen<MessHallMenu> {
    /** Accent of each hall tier: wood, copper, stone, canteen green. */
    static final int[] TIER={0xffc9a24b,0xffd9824a,0xff9fb3bc,0xff6ee07a};
    static final int WIDTH=MessHallMenu.WIDTH,HEIGHT=MessHallMenu.HEIGHT;
    /** Left column: x 8 to 176. Right column: x 192 to 368. */
    static final int LX=8,LW=168,RX=192,RW=176,DIVIDER=184;
    static final int TAB_Y=29,FX_Y=66,LINK_Y=75,ZONE_Y=82,PRIMARY_Y=103,SECOND_Y=127,UPGRADE_Y=142;
    static final int STRIP_Y=29,STRIP_H=18,EVERYDAY_HEAD=51,EVERYDAY_Y=61,LEGEND_HEAD=107,LEGEND_Y=117,ORDER_HEAD=163,CARD_Y=174,CARD_PITCH=14,CHIP_Y=221,COLS=8,PITCH=22;
    static final List<MealRules.Effect> EVERYDAY=Arrays.stream(MealRules.Effect.values()).filter(e->!e.gun()).toList(),LEGENDARY=Arrays.stream(MealRules.Effect.values()).filter(MealRules.Effect::gun).toList();
    /** What the primary button does right now. */
    static final int COOK=0,GATHER=1,MISSING=2;
    /** Table problems the Gather button can solve by rebuilding the table from the pack. */
    static final Set<String> GATHERABLE=Set.of("missing","ingredients","ingredient","sandwich_types","batch_types","quantity","staple","effects");
    private static final net.minecraft.resources.ResourceLocation BREAD_BASE=new net.minecraft.resources.ResourceLocation(ArsenalBeacon.ID,"textures/gui/bread_base.png");
    Ui.UiButton sandwich,stew,clear,takeBack,upgrade,primary,replaceYes,replaceNo;/** The "replace the stew?" question is open: only its two buttons answer. */boolean confirming;final List<Ui.UiButton> pots=new ArrayList<>();final List<Cell> cells=new ArrayList<>();
    private int lastNotice,badgeLeft;private long noticeAt;private final Map<String,ItemStack> stacks=new HashMap<>();

    MessHallScreen(MessHallMenu menu,Inventory inv,Component title){super(menu,inv,title);imageWidth=WIDTH;imageHeight=HEIGHT;}
    int uiLeft(){return leftPos;}int uiTop(){return topPos;}MessHallMenu currentMenu(){return menu;}
    private void action(int id){minecraft.gameMode.handleInventoryButtonClick(menu.containerId,id);}
    private int tierColor(){return TIER[Math.max(0,Math.min(3,menu.mk-1))];}
    private MealRules.Tier tier(){return MealRules.tier(menu.mk);}
    private CompoundTag view(){return menu.view;}
    private boolean explicit(){return view().getBoolean("Explicit");}

    /**
     * One effect to choose. A neutral cell holds the 18 px icon; unchosen icons are dimmed so a full palette stays calm and the effects you picked
     * are the brightest thing on the screen: cyan frame and a check. An alert marks a pick the pack cannot feed or one that clashes with the order.
     */
    final class Cell extends Button {
        final MealRules.Effect effect;
        Cell(int x,int y,MealRules.Effect effect){super(x,y,20,20,Ui.t("meal.effect."+effect.id),b->action(MessHallMenu.RECIPE+effect.ordinal()),DEFAULT_NARRATION);this.effect=effect;}
        @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial){
            int fit=fit(effect),x=getX(),y=getY();boolean chosen=fit>=3,hover=isHoveredOrFocused();
            g.fill(x,y,x+20,y+20,chosen?Ui.CYAN_FILL:Ui.DEEP);
            g.blit(MealEffects.icon(effect),x+1,y+1,0,0,18,18,18,18);
            int dim=chosen||hover?0:fit==0?0xb0:fit==1?0x90:explicit()?0x70:0x48;
            if(dim>0)g.fill(x+1,y+1,x+19,y+19,dim<<24);
            if(chosen){ring(g,x,y,20,Ui.CYAN);ring(g,x+1,y+1,18,Ui.CYAN);g.fill(x+11,y+11,x+19,y+19,0xe00a1218);Ui.check(g,x+12,y+12,fit==4?Ui.ORANGE:Ui.CYAN);}
            else if(hover)ring(g,x,y,20,Ui.INK);
            else if(auto(effect))ring(g,x,y,20,Ui.CYAN_DIM);
            else ring(g,x,y,20,Ui.SLATE_HI);
            if(fit==4||fit==1&&view().getIntArray("Order").length<limit()){g.fill(x+12,y+1,x+19,y+10,0xe00a1218);Ui.alert(g,x+13,y+2,Ui.ORANGE);}
            if(isFocused()&&!chosen)ring(g,x-1,y-1,22,Ui.CYAN);
        }
        private void ring(GuiGraphics g,int x,int y,int size,int color){g.fill(x,y,x+size,y+1,color);g.fill(x,y+size-1,x+size,y+size,color);g.fill(x,y,x+1,y+size,color);g.fill(x+size-1,y,x+size,y+size,color);}
    }
    /** 4 chosen but the pack has no food for it, 3 chosen, 2 can be added, 1 exists but not alongside the order, 0 nothing in the pack feeds it. */
    private int fit(MealRules.Effect e){var fit=view().getIntArray("Fit");return e.ordinal()<fit.length?fit[e.ordinal()]:0;}
    /** Picked by the automatic ranking rather than chosen. */
    private boolean auto(MealRules.Effect e){if(explicit())return false;for(var t:view().getList("Made",Tag.TAG_COMPOUND))if(((CompoundTag)t).getInt("Effect")==e.ordinal())return true;return false;}
    private int limit(){return Math.max(2,view().getInt("Limit"));}

    @Override protected void init(){
        super.init();pots.clear();cells.clear();
        sandwich=addRenderableWidget(new Ui.UiButton(leftPos+LX,topPos+TAB_Y,82,15,Component.empty(),Ui.Look.TAB,b->action(MessHallMenu.MODE_SANDWICH)).painter((g,self,h)->tab(g,self,new ItemStack(Items.BREAD),"kitchen.sandwich",false)).noLabel());
        stew=addRenderableWidget(new Ui.UiButton(leftPos+LX+86,topPos+TAB_Y,82,15,Component.empty(),Ui.Look.TAB,b->action(MessHallMenu.MODE_STEW)).painter((g,self,h)->tab(g,self,new ItemStack(ArsenalBeacon.COOK_POT.get()),"kitchen.stew",!tier().stew())).noLabel());
        for(int i=0;i<EVERYDAY.size();i++)cells.add(addRenderableWidget(new Cell(leftPos+RX+(i%COLS)*PITCH,topPos+EVERYDAY_Y+(i/COLS)*PITCH,EVERYDAY.get(i))));
        for(int i=0;i<LEGENDARY.size();i++)cells.add(addRenderableWidget(new Cell(leftPos+RX+(i%COLS)*PITCH,topPos+LEGEND_Y+(i/COLS)*PITCH,LEGENDARY.get(i))));
        for(int i=0;i<4;i++){final int index=i;pots.add(addRenderableWidget(new Ui.UiButton(leftPos+LX+i*42,topPos+ZONE_Y,40,18,Component.empty(),Ui.Look.ROW,b->action(MessHallMenu.POT+index)).painter((g,self,h)->potChip(g,self,index)).noLabel()));}
        primary=addRenderableWidget(new Ui.UiButton(leftPos+LX,topPos+PRIMARY_Y,LW,21,Component.empty(),Ui.Look.PRIMARY,b->{int mode=ctaMode();if(mode==GATHER)action(MessHallMenu.GATHER);else if(mode==COOK){if(replacing())confirming=true;else action(MessHallMenu.PREPARE);}}).painter(this::primaryFace).noLabel());
        takeBack=addRenderableWidget(new Ui.UiButton(leftPos+LX,topPos+SECOND_Y,82,12,Ui.t("kitchen.take_back"),Ui.Look.NORMAL,b->action(MessHallMenu.TAKE_BACK)));
        clear=addRenderableWidget(new Ui.UiButton(leftPos+LX+86,topPos+SECOND_Y,82,12,Ui.t("kitchen.order_clear"),Ui.Look.NORMAL,b->action(MessHallMenu.CLEAR)));
        upgrade=addRenderableWidget(new Ui.UiButton(leftPos+LX,topPos+UPGRADE_Y,LW,12,Component.empty(),Ui.Look.NORMAL,b->action(MessHallMenu.UPGRADE)).painter(this::upgradeFace).noLabel());
        replaceYes=addRenderableWidget(new Ui.UiButton(leftPos+WIDTH/2-122,topPos+HEIGHT/2+22,118,16,Ui.t("kitchen.replace.yes"),Ui.Look.DANGER,b->{confirming=false;action(MessHallMenu.REPLACE);}));
        replaceNo=addRenderableWidget(new Ui.UiButton(leftPos+WIDTH/2+4,topPos+HEIGHT/2+22,118,16,Ui.t("kitchen.replace.no"),Ui.Look.PRIMARY,b->confirming=false));
        replaceYes.visible=replaceNo.visible=false;confirming=false;
        var tierLabel=Ui.t("kitchen.tier",menu.mk);badgeLeft=leftPos+WIDTH-font.width(tierLabel)-Ui.pipsWidth(4)-31;
    }

    // ---- buttons ------------------------------------------------------------------------------------------
    /** Stew is ready to cook, but the chosen pot still has stew: cooking throws it away, so the screen asks first. */
    private boolean replacing(){var v=view();return v.getBoolean("StewMode")&&v.getString("Problem").equals("pot_full");}
    /** The chosen pot's row of the view: its servings and meal, or null. */
    private CompoundTag chosenPot(){
        var v=view();if(!v.contains("Selected"))return null;
        for(var t:v.getList("Pots",Tag.TAG_COMPOUND)){var row=(CompoundTag)t;if(row.getLong("Pos")==v.getLong("Selected"))return row;}return null;
    }
    private int ctaMode(){
        String problem=view().getString("Problem");
        if(explicit()&&GATHERABLE.contains(problem))return view().getBoolean("Gatherable")?GATHER:MISSING;
        return COOK;
    }
    private void tab(GuiGraphics g,Ui.UiButton b,ItemStack icon,String key,boolean locked){
        int ink=b.selected?Ui.INK:Ui.MUTED;
        g.renderItem(icon,b.getX()+3,b.getY()-1);Ui.text(g,font,Ui.t(key),b.getX()+22,b.getY()+4,locked?Ui.DISABLED:ink,b.getWidth()-(locked?40:26));
        if(locked)padlock(g,b.getX()+b.getWidth()-14,b.getY()+4,Ui.DISABLED);
    }
    /** A small padlock, 7 x 9 pixels. */
    private static void padlock(GuiGraphics g,int x,int y,int color){g.fill(x,y+4,x+7,y+9,color);g.fill(x+1,y+1,x+2,y+4,color);g.fill(x+5,y+1,x+6,y+4,color);g.fill(x+1,y,x+6,y+1,color);}
    /** A flickering flame, three stacked pixel layers. */
    private static void flame(GuiGraphics g,int x,int y,long t){
        int a=(int)(Math.sin(t*.011)*1.4),b=(int)(Math.sin(t*.017+1)*1.2);
        g.fill(x+2,y+6,x+8,y+10,0xffd9531e);g.fill(x+3+a,y+2,x+7+a,y+8,0xffff9a3d);g.fill(x+4+b,y+0,x+6+b,y+5,0xffffe36b);g.fill(x+4,y+7,x+6,y+9,0xfffff3b0);
    }
    private void primaryFace(GuiGraphics g,Ui.UiButton b,boolean hover){
        long t=net.minecraft.Util.getMillis();int mode=ctaMode();boolean stewMode=view().getBoolean("StewMode");
        if(b.active){
            float pulse=.5f+.5f*(float)Math.sin(t*.006);int glow=((int)(70+120*pulse)<<24)|0x5ae2df;
            g.fill(b.getX()-1,b.getY()-1,b.getX()+b.getWidth()+1,b.getY(),glow);g.fill(b.getX()-1,b.getY()+b.getHeight(),b.getX()+b.getWidth()+1,b.getY()+b.getHeight()+1,glow);
            g.fill(b.getX()-1,b.getY(),b.getX(),b.getY()+b.getHeight(),glow);g.fill(b.getX()+b.getWidth(),b.getY(),b.getX()+b.getWidth()+1,b.getY()+b.getHeight(),glow);
            if(mode==GATHER)g.renderItem(new ItemStack(Items.HOPPER),b.getX()+5,b.getY()+3);else flame(g,b.getX()+8,b.getY()+5,t);
        }else if(mode==MISSING)Ui.alert(g,b.getX()+9,b.getY()+7,Ui.ORANGE);
        else g.fill(b.getX()+8,b.getY()+10,b.getX()+16,b.getY()+12,Ui.TRACK);
        var label=Ui.t(mode==GATHER?"kitchen.gather":mode==MISSING?"kitchen.missing_foods":stewMode?"kitchen.cook_stew":"kitchen.make_sandwich");
        Ui.text(g,font,label,b.getX()+26,b.getY()+7,b.active?Ui.INK:mode==MISSING?Ui.ORANGE:Ui.DISABLED,b.getWidth()-30);
    }
    /** The label and the price on one line; the tooltip says what the next hall level adds. */
    private void upgradeFace(GuiGraphics g,Ui.UiButton b,boolean hover){
        var view=view();if(!view.contains("UpgradeItem"))return;
        var item=ItemStack.of(view.getCompound("UpgradeItem")).copyWithCount(1);int cost=view.getInt("UpgradeCost"),have=view.getInt("UpgradeHave");boolean short_=have<cost;
        Ui.text(g,font,Ui.t("kitchen.upgrade",menu.mk+1),b.getX()+6,b.getY()+2,b.active?Ui.INK:Ui.MUTED,b.getWidth()-50);
        var count=Component.literal(Integer.toString(cost));int right=b.getX()+b.getWidth()-5;
        g.drawString(font,count,right-font.width(count),b.getY()+2,short_?Ui.ORANGE:Ui.INK,false);
        g.pose().pushPose();g.pose().translate(right-font.width(count)-10,b.getY()+2,0);g.pose().scale(.5f,.5f,1f);g.renderItem(item,0,0);g.pose().popPose();
    }
    private void potChip(GuiGraphics g,Ui.UiButton b,int index){
        var list=view().getList("Pots",Tag.TAG_COMPOUND);if(index>=list.size())return;var row=list.getCompound(index);
        int servings=row.getInt("Servings"),capacity=tier().servings();var meal=MealData.load(row.getCompound("Meal"));
        g.pose().pushPose();g.pose().translate(b.getX()+2,b.getY()+1,0);g.pose().scale(.75f,.75f,1f);g.renderItem(new ItemStack(ArsenalBeacon.COOK_POT.get()),0,0);g.pose().popPose();
        Ui.text(g,font,Component.literal(Integer.toString(index+1)),b.getX()+18,b.getY()+3,b.selected?Ui.INK:Ui.MUTED,12);
        if(meal!=null)g.fill(b.getX()+27,b.getY()+4,b.getX()+31,b.getY()+8,MealEffects.color(meal.bonuses().get(0).effect()));
        Ui.bar(g,b.getX()+3,b.getY()+13,b.getWidth()-6,3,meal==null?0:servings/(float)capacity,meal==null?Ui.TRACK:MealEffects.color(meal.bonuses().get(0).effect()));
    }

    // ---- background ---------------------------------------------------------------------------------------
    private void effectIcon(GuiGraphics g,MealRules.Effect e,int x,int y,int size){g.blit(MealEffects.icon(e),x,y,size,size,0,0,18,18,18,18);}
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
        long t=net.minecraft.Util.getMillis();int accent=tierColor();var view=view();boolean isStew=view.getBoolean("StewMode");
        Ui.panel(g,leftPos,topPos,imageWidth,imageHeight);Ui.header(g,font,leftPos,topPos,imageWidth,title,null);
        for(int i=0;i<7;i++){double phase=((t*.00035+i*.143)%1.0);int sx=leftPos+120+i*23+(int)(Math.sin(t*.002+i)*3),sy=topPos+24-(int)(phase*20);g.fill(sx,sy,sx+2,sy+2,((int)((1-phase)*90)<<24)|0xe8f4f8);}
        var tierLabel=Ui.t("kitchen.tier",menu.mk);int tw=font.width(tierLabel),bx=leftPos+imageWidth-tw-Ui.pipsWidth(4)-26;
        g.fill(bx-5,topPos+5,bx+tw+Ui.pipsWidth(4)+13,topPos+21,Ui.DEEP);g.fill(bx-5,topPos+5,bx-2,topPos+21,accent);
        g.drawString(font,tierLabel,bx+2,topPos+9,accent,false);Ui.pips(g,bx+tw+8,topPos+11,menu.mk,4,accent);
        upgrade.visible=menu.mk<4;upgrade.active=view.contains("UpgradeCost")&&view.getInt("UpgradeHave")>=view.getInt("UpgradeCost");
        sandwich.selected=!isStew;stew.selected=isStew;clear.visible=explicit();
        g.fill(leftPos+DIVIDER,topPos+TAB_Y,leftPos+DIVIDER+1,topPos+HEIGHT-6,Ui.EDGE);
        strip(g);
        // the effects to choose
        Ui.text(g,font,Ui.t("kitchen.recipes_everyday"),leftPos+RX,topPos+EVERYDAY_HEAD,Ui.MUTED,100);
        Ui.right(g,font,Ui.t("kitchen.recipes_hint"),leftPos+RX+RW,topPos+EVERYDAY_HEAD,Ui.DISABLED);
        Ui.text(g,font,Ui.t("kitchen.recipes_legendary"),leftPos+RX,topPos+LEGEND_HEAD,Ui.BRASS,70);
        Ui.right(g,font,Ui.t("kitchen.recipes_legendary_hint"),leftPos+RX+RW,topPos+LEGEND_HEAD,Ui.DISABLED);
        table(g,t,accent,isStew);cards(g);chips(g);
        Ui.inventory(g,leftPos+8,topPos+158);
    }
    /** Top of the right column: for a few seconds the server's last word, otherwise what to do next. */
    private void strip(GuiGraphics g){
        int x=leftPos+RX,y=topPos+STRIP_Y;var toast=toast();
        if(toast!=null){
            int kind=view().getInt("NoticeKind");int color=kind==1?Ui.ORANGE:kind==2?Ui.CYAN:Ui.CYAN_DIM;
            Ui.card(g,x,y,RW,STRIP_H,color);Ui.wrap(g,font,toast,x+7,y+1,RW-12,Ui.INK,2);return;
        }
        var view=view();String problem=view.getString("Problem");boolean replace=replacing();boolean ready=view.contains("Problem")&&problem.isEmpty()||replace;
        Ui.inset(g,x,y,RW,STRIP_H);
        if(replace)Ui.alert(g,x+6,y+4,Ui.ORANGE);else if(ready)Ui.check(g,x+5,y+5,Ui.CYAN);else if(!problem.isEmpty())Ui.alert(g,x+6,y+4,Ui.ORANGE);
        Ui.text(g,font,hint(problem),x+(problem.isEmpty()&&!ready?6:16),y+5,replace?Ui.ORANGE:ready?Ui.CYAN:problem.isEmpty()?Ui.MUTED:Ui.ORANGE,RW-20);
    }
    private Component toast(){
        int seq=view().getInt("NoticeSeq");if(seq!=lastNotice){lastNotice=seq;noticeAt=net.minecraft.Util.getMillis();}
        if(seq==0||net.minecraft.Util.getMillis()-noticeAt>4200)return null;
        String key=view().getString("Notice");if(key.isEmpty())return null;
        var list=view().getList("NoticeArgs",Tag.TAG_STRING);var args=new Object[list.size()];for(int i=0;i<args.length;i++)args[i]=noticeArg(list.getString(i));
        return Ui.t("kitchen.notice."+key,args);
    }
    private static Object noticeArg(String raw){
        if(!raw.startsWith("fx:"))return raw;
        return String.join(", ",Arrays.stream(raw.substring(3).split(",")).map(id->Ui.t("meal.effect."+id).getString()).toList());
    }
    /** Slots with the effect each food feeds, the base and output slots of a sandwich, or the pots of a stew. */
    private void table(GuiGraphics g,long t,int accent,boolean isStew){
        int open=tier().slots(),baseX=MessHallMenu.baseX(menu.mk),outX=MessHallMenu.outX(menu.mk),frameY=topPos+MessHallMenu.MIX_Y-1;
        legendaryLinks(g,t);var fx=view().getList("SlotFx",Tag.TAG_INT_ARRAY);
        for(int i=0;i<open;i++){
            int x=leftPos+MessHallMenu.MIX_X-1+i*18;int[] effects=i<fx.size()?fx.getIntArray(i):new int[0];
            Ui.slot(g,x,frameY,effects.length==0?Ui.EDGE:MealEffects.color(MealRules.Effect.values()[effects[0]]));
            for(int k=0;k<effects.length&&k<2;k++)effectIcon(g,MealRules.Effect.values()[effects[k]],x+(effects.length==1?5:k*8+1),topPos+FX_Y,8);
        }
        takeBack.active=hasMixItems()||!menu.ingredients.getItem(7).isEmpty();
        if(!isStew){
            Ui.slot(g,leftPos+baseX-1,frameY,accent);Ui.slot(g,leftPos+outX-1,frameY,accent);
            if(menu.ingredients.getItem(7).isEmpty())g.blit(BREAD_BASE,leftPos+baseX,frameY+1,0,0,16,16,16,16);
            Ui.text(g,font,Component.literal("→"),leftPos+baseX+19,frameY+5,Ui.MUTED);
            Ui.text(g,font,Ui.t("kitchen.base"),leftPos+baseX-4,topPos+FX_Y,Ui.MUTED,26);Ui.text(g,font,Ui.t("kitchen.output_short"),leftPos+outX+1,topPos+FX_Y,Ui.MUTED,20);
            Ui.wrap(g,font,Ui.t("kitchen.sandwich_hint"),leftPos+LX,topPos+ZONE_Y+2,LW,Ui.MUTED,2);
            if(!menu.ingredients.getItem(6).isEmpty())for(int i=0;i<3;i++){double p=((t*.0009+i*.33)%1.0);g.fill(leftPos+outX+2+i*5,frameY-2-(int)(p*10),leftPos+outX+4+i*5,frameY-(int)(p*10),((int)((1-p)*200)<<24)|0xffe36b);}
        }
        var list=view().getList("Pots",Tag.TAG_COMPOUND);
        for(int i=0;i<pots.size();i++){
            var b=pots.get(i);b.visible=isStew&&i<list.size();if(!b.visible)continue;
            var pos=BlockPos.of(list.getCompound(i).getLong("Pos"));b.selected=view().contains("Selected")&&view().getLong("Selected")==pos.asLong();b.accent=accent;
        }
        if(isStew&&list.isEmpty())Ui.wrap(g,font,Ui.t("kitchen.no_pots"),leftPos+LX,topPos+ZONE_Y+2,LW,Ui.ORANGE,2);
    }
    private boolean hasMixItems(){for(int i=0;i<6;i++)if(!menu.ingredients.getItem(i).isEmpty())return true;return false;}
    private record Card(MealRules.Effect effect,boolean met,MealData.Bonus bonus){}
    /** The effects of the order, or what the foods on the table add up to when nothing is chosen. */
    private List<Card> cardRows(){
        var made=new LinkedHashMap<MealRules.Effect,MealData.Bonus>();
        for(var t:view().getList("Made",Tag.TAG_COMPOUND)){var c=(CompoundTag)t;var e=MealRules.Effect.values()[c.getInt("Effect")];made.put(e,new MealData.Bonus(e,1,c.getBoolean("Pair")));}
        var out=new ArrayList<Card>();
        if(explicit())for(int ordinal:view().getIntArray("Order")){var e=MealRules.Effect.values()[ordinal];out.add(new Card(e,made.containsKey(e),made.getOrDefault(e,new MealData.Bonus(e,1,false))));}
        else made.forEach((e,b)->out.add(new Card(e,true,b)));
        return out;
    }
    private void cards(GuiGraphics g){
        var rows=cardRows();int x=leftPos+RX;var view=view();
        var heading=explicit()?Ui.t("kitchen.heading_order_count",view.getIntArray("Order").length,limit()):Ui.t("kitchen.heading_auto");
        Ui.text(g,font,heading,x,topPos+ORDER_HEAD,explicit()?Ui.CYAN:Ui.MUTED,RW-70);
        int need=Math.max(1,view.getInt("Required")),have=view.getInt("Available");boolean enough=view.getInt("Required")==0||have>=need;
        if(view.getBoolean("StewMode"))Ui.right(g,font,Ui.t("kitchen.quantity",have,view.getInt("Required")),x+RW,topPos+ORDER_HEAD,enough?Ui.MUTED:Ui.ORANGE);
        for(int i=0;i<limit();i++){
            int y=topPos+CARD_Y+i*CARD_PITCH;
            if(i>=rows.size()){Ui.card(g,x,y,RW,13,Ui.TRACK);Ui.text(g,font,Ui.t("kitchen.effect_open"),x+22,y+3,Ui.DISABLED,RW-26);continue;}
            var card=rows.get(i);int color=MealEffects.color(card.effect());
            Ui.card(g,x,y,RW,13,card.met()?color:Ui.TRACK);
            g.setColor(1,1,1,card.met()?1f:.5f);effectIcon(g,card.effect(),x+6,y+1,11);g.setColor(1,1,1,1);
            var name=Ui.t("meal.effect."+card.effect().id);
            if(card.met()){
                var value=Component.literal(card.bonus().amountText(false));int right=x+RW-4;
                Ui.right(g,font,value,right,y+3,Ui.CYAN);right-=font.width(value)+4;
                if(card.bonus().pair()){var two=Component.literal("×2");Ui.right(g,font,two,right,y+3,Ui.BRASS);right-=font.width(two)+4;}
                Ui.text(g,font,name,x+21,y+3,Ui.INK,right-x-21);
            }else{
                var needs=card.effect().gun()?mixNeed(card.effect()):Ui.t("kitchen.card_food");int room=RW-25;
                if(font.width(name)+8+font.width(needs)<=room){Ui.right(g,font,needs,x+RW-4,y+3,Ui.ORANGE);Ui.text(g,font,name,x+21,y+3,Ui.MUTED,room-font.width(needs)-4);}
                else{Ui.alert(g,x+RW-8,y+3,Ui.ORANGE);Ui.text(g,font,name,x+21,y+3,Ui.MUTED,room-10);}
            }
        }
    }
    private Component mixNeed(MealRules.Effect effect){
        var mix=MealRules.MIXES.stream().filter(m->m.effect()==effect).findFirst().orElseThrow();
        return Ui.t("kitchen.card_pair",Ui.t("kitchen.group_short."+mix.first()),Ui.t("kitchen.group_short."+mix.second()));
    }
    // ---- what this hall can do: meal length, food slots, doubling -----------------------------------------
    private static final int CHIP_W=56,CHIP_GAP=4;
    private int chipX(int index){return leftPos+RX+index*(CHIP_W+CHIP_GAP);}
    private void chips(GuiGraphics g){
        var tier=tier();int y=topPos+CHIP_Y;
        chip(g,chipX(0),y,Ui.t("kitchen.chip_time",tier.minutes()),Ui.CYAN_DIM,Ui.INK,false);
        chip(g,chipX(1),y,Ui.t("kitchen.chip_slots",tier.slots()),tier.slots()>=6?Ui.BRASS:Ui.CYAN_DIM,Ui.INK,false);
        chip(g,chipX(2),y,Ui.t("kitchen.chip_double"),tier.doubling()?Ui.BRASS:Ui.TRACK,tier.doubling()?Ui.BRASS:Ui.DISABLED,!tier.doubling());
    }
    private void chip(GuiGraphics g,int x,int y,Component text,int accent,int ink,boolean locked){
        g.fill(x,y,x+CHIP_W,y+12,Ui.DEEP);g.fill(x,y,x+3,y+12,accent);g.fill(x,y+11,x+CHIP_W,y+12,accent&0x99ffffff);
        Ui.text(g,font,text,x+7,y+2,ink,CHIP_W-(locked?20:10));
        if(locked)padlock(g,x+CHIP_W-12,y+1,Ui.DISABLED);
    }
    /** What the cook should do next, in a few words; the button tooltip carries the full explanation. */
    private Component hint(String problem){
        if(!view().contains("Problem"))return playerInventoryTitle;
        if(problem.isEmpty())return Ui.t("kitchen.hint.ready");
        if(explicit()&&GATHERABLE.contains(problem))return Ui.t(view().getBoolean("Gatherable")?"kitchen.hint.gather":"kitchen.hint.missing");
        return Ui.t("kitchen.hint."+problem,view().getInt("Available"),view().getInt("Required"));
    }
    /** Pulsing connectors underneath the ingredient icons, tying together the foods of one legendary effect. */
    private void legendaryLinks(GuiGraphics g,long time){
        var links=view().getList("LegendaryLinks",Tag.TAG_COMPOUND);
        for(int i=0;i<links.size()&&i<3;i++){
            var row=links.getCompound(i);int[] slots=row.getIntArray("Slots");if(slots.length<2)continue;
            int effect=row.getInt("Effect");if(effect<0||effect>=MealRules.Effect.values().length)continue;
            int min=Arrays.stream(slots).min().orElse(0),max=Arrays.stream(slots).max().orElse(0);
            int x1=leftPos+17+min*18,x2=leftPos+17+max*18,y=topPos+LINK_Y+i*2,color=MealEffects.color(MealRules.Effect.values()[effect]);
            g.fill(x1,y,x2+1,y+1,color);for(int slot:slots)if(slot>=0&&slot<6){int x=leftPos+17+slot*18;g.fill(x,topPos+LINK_Y-1,x+1,y+1,color);}
            int pulse=x1+(int)((time*.025+i*11)%Math.max(1,x2-x1));g.fill(pulse,y,pulse+2,y+1,Ui.BRASS);
        }
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){}

    // ---- tooltips -----------------------------------------------------------------------------------------
    private ItemStack stackOf(String id){return stacks.computeIfAbsent(id,k->new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(k))));}
    private Set<String> carried(){var set=new HashSet<String>();for(var slot:menu.slots){var s=slot.getItem();if(!s.isEmpty())set.add(BuiltInRegistries.ITEM.getKey(s.getItem()).toString());}return set;}
    private Ui.Block iconRow(Component label,int color,List<MessHallMenu.CatalogueFood> foods,Set<String> carried){
        var sorted=new ArrayList<>(foods);sorted.sort(Comparator.<MessHallMenu.CatalogueFood>comparingInt(f->carried.contains(f.id())?0:1).thenComparing(MessHallMenu.CatalogueFood::id));
        var icons=sorted.stream().limit(11).map(f->stackOf(f.id())).toList();return Ui.Block.icons(label,color,icons,Math.max(0,sorted.size()-icons.size()));
    }
    /** The full recipe: values, what feeds it (foods you carry first), the double rule and whether your pack and order can take it. */
    private void recipeTip(GuiGraphics g,MealRules.Effect effect,int mx,int my){
        var bonus=new MealData.Bonus(effect,1,false);var blocks=new ArrayList<Ui.Block>();int fit=fit(effect);var carried=carried();var tier=tier();
        blocks.add(Ui.Block.line(Ui.t("meal.preview_field",bonus.description(false)),Ui.INK));blocks.add(Ui.Block.line(Ui.t("meal.preview_home",bonus.description(true)),Ui.BRASS));
        if(effect.gun()){
            var mix=MealRules.MIXES.stream().filter(m->m.effect()==effect).findFirst().orElseThrow();
            blocks.add(Ui.Block.line(Ui.t("kitchen.tip.legendary"),Ui.BRASS));
            for(var group:List.of(mix.first(),mix.second()))blocks.add(iconRow(Ui.t("kitchen.group."+group),Ui.MUTED,menu.catalogue.stream().filter(f->f.group().equals(group)).toList(),carried));
            blocks.add(Ui.Block.line(Ui.t("kitchen.tip.double_legendary"),Ui.DISABLED));
        }else{
            blocks.add(iconRow(Ui.t("kitchen.tip.any"),Ui.MUTED,menu.catalogue.stream().filter(f->Arrays.stream(f.effects()).anyMatch(i->i==effect.ordinal())).toList(),carried));
            blocks.add(Ui.Block.line(Ui.t("kitchen.tip.double_ordinary"),Ui.DISABLED));
        }
        if(!tier.doubling())blocks.add(Ui.Block.marked(2,Ui.t("kitchen.tip.double_locked3"),Ui.ORANGE));
        else if(effect.gun()&&tier.slots()<4)blocks.add(Ui.Block.marked(2,Ui.t("kitchen.tip.double_locked4"),Ui.ORANGE));
        else blocks.add(Ui.Block.marked(1,Ui.t("kitchen.tip.double_open"),Ui.CYAN));
        int chosen=view().getIntArray("Order").length;
        blocks.add(switch(fit){
            case 3->Ui.Block.marked(1,Ui.t("kitchen.tip.chosen"),Ui.CYAN);
            case 4->Ui.Block.marked(2,Ui.t("kitchen.tip.chosen_lacking"),Ui.ORANGE);
            case 2->Ui.Block.marked(1,Ui.t(chosen==0?"kitchen.tip.stocked":"kitchen.tip.fits"),Ui.CYAN);
            case 1->Ui.Block.marked(2,Ui.t(chosen>=limit()?"kitchen.tip.full":"kitchen.tip.conflict",limit()),Ui.ORANGE);
            default->Ui.Block.marked(2,Ui.t("kitchen.tip.no_food"),Ui.ORANGE);
        });
        if(auto(effect))blocks.add(Ui.Block.line(Ui.t("kitchen.tip.auto"),Ui.CYAN_DIM));
        g.renderTooltip(font,List.of(Ui.t("meal.effect."+effect.id).copy().withStyle(s->s.withColor(MealEffects.color(effect)&0xffffff))),Optional.of(new Ui.InfoTip(blocks)),mx,my);
    }
    private void tip(GuiGraphics g,String key,int mx,int my,Object... args){g.renderTooltip(font,font.split(Ui.t(key,args),190),mx,my);}
    private void tooltips(GuiGraphics g,int mx,int my){
        if(hoveredSlot!=null&&hoveredSlot.hasItem())return;
        for(var cell:cells)if(cell.isHovered()){recipeTip(g,cell.effect,mx,my);return;}
        var rows=cardRows();
        for(int i=0;i<rows.size();i++)if(Ui.inside(mx,my,leftPos+RX,topPos+CARD_Y+i*CARD_PITCH,RW,13)){recipeTip(g,rows.get(i).effect(),mx,my);return;}
        var toast=toast();if(toast!=null&&Ui.inside(mx,my,leftPos+RX,topPos+STRIP_Y,RW,STRIP_H)){g.renderTooltip(font,font.split(toast,190),mx,my);return;}
        if(sandwich.isHovered())tip(g,"kitchen.tip.sandwich",mx,my,3);
        else if(stew.isHovered())tip(g,tier().stew()?"kitchen.tip.stew":"kitchen.tip.stew_locked",mx,my,tier().servings());
        else if(clear.visible&&clear.isHovered())tip(g,"kitchen.tip.clear",mx,my);
        else if(takeBack.isHovered())tip(g,takeBack.active?"kitchen.tip.take_back":"kitchen.tip.take_back_empty",mx,my);
        else if(upgrade.visible&&upgrade.isHovered())Ui.materialTooltip(g,font,List.of(Ui.t("kitchen.upgrade",menu.mk+1),Ui.t("kitchen.tier_gain."+(menu.mk+1))),Ui.singleCost(ItemStack.of(view().getCompound("UpgradeItem")),view().getInt("UpgradeCost"),view().getInt("UpgradeHave")),mx,my);
        else if(primary.isHovered())primaryTip(g,mx,my);
        else if(Ui.inside(mx,my,badgeLeft,topPos+5,leftPos+WIDTH-13-badgeLeft,16))tierTip(g,mx,my);
        else if(Ui.inside(mx,my,chipX(0),topPos+CHIP_Y,CHIP_W,12))tip(g,"kitchen.tip.time",mx,my,tier().minutes());
        else if(Ui.inside(mx,my,chipX(1),topPos+CHIP_Y,CHIP_W,12))tip(g,tier().slots()>=6?"kitchen.tip.slots_all":"kitchen.tip.slots",mx,my,tier().slots());
        else if(Ui.inside(mx,my,chipX(2),topPos+CHIP_Y,CHIP_W,12))tip(g,tier().doubling()?"kitchen.tip.double_on":"kitchen.tip.double_off",mx,my);
        else if(!menu.stew&&Ui.inside(mx,my,leftPos+MessHallMenu.baseX(menu.mk)-1,topPos+MessHallMenu.MIX_Y-1,18,18)&&menu.ingredients.getItem(7).isEmpty())tip(g,"kitchen.base_hint",mx,my);
        else if(Ui.inside(mx,my,leftPos+RX+RW-80,topPos+ORDER_HEAD-2,80,11))tip(g,"kitchen.quantity_hint",mx,my,view().getInt("Required"),view().getInt("Available"));
        else if(Ui.inside(mx,my,leftPos+RX,topPos+STRIP_Y,RW,STRIP_H)&&!view().getString("Problem").isEmpty())primaryTip(g,mx,my);
        else linkTip(g,mx,my);
    }
    private void primaryTip(GuiGraphics g,int mx,int my){
        String problem=view().getString("Problem");int mode=ctaMode();
        if(mode==GATHER){tip(g,"kitchen.tip.gather",mx,my);return;}
        if(mode==MISSING){
            var lines=new ArrayList<Component>();var lacking=new ArrayList<String>();
            for(var e:MealRules.Effect.values())if(fit(e)==4)lacking.add(Ui.t("meal.effect."+e.id).getString());
            lines.add(Ui.t(lacking.isEmpty()?"kitchen.tip.missing_blocked":"kitchen.tip.missing_foods",String.join(", ",lacking)));
            g.renderTooltip(font,font.split(lines.get(0),190),mx,my);return;
        }
        if(replacing()){tip(g,"kitchen.tip.cook_replace",mx,my,view().getInt("Servings"));return;}
        if(problem.isEmpty()){tip(g,view().getBoolean("StewMode")?"kitchen.tip.cook_stew":"kitchen.tip.make_sandwich",mx,my,view().getInt("Servings"));return;}
        tip(g,"kitchen.problem."+problem,mx,my);
    }
    /** The hall at a glance: what it makes and what it unlocks. */
    private void tierTip(GuiGraphics g,int mx,int my){
        var tier=tier();var lines=new ArrayList<Component>();lines.add(title);
        lines.add(Ui.t("kitchen.stat_meal",tier.minutes()));lines.add(Ui.t("kitchen.stat_slots",tier.slots()));
        lines.add(tier.stew()?Ui.t("kitchen.stat_stew",tier.pots(),tier.servings()):Ui.t("kitchen.stat_stew_locked"));
        lines.add(tier.doubling()?Ui.t("kitchen.stat_double"):Ui.t("kitchen.stat_double_locked"));
        g.renderComponentTooltip(font,lines,mx,my);
    }
    private void linkTip(GuiGraphics g,int mx,int my){
        var links=view().getList("LegendaryLinks",Tag.TAG_COMPOUND);
        for(int i=0;i<links.size()&&i<3;i++){
            var row=links.getCompound(i);var slots=row.getIntArray("Slots");if(slots.length<2)continue;
            int x1=leftPos+17+Arrays.stream(slots).min().orElse(0)*18,x2=leftPos+17+Arrays.stream(slots).max().orElse(0)*18,y=topPos+LINK_Y+i*2;
            boolean over=mx>=x1&&mx<=x2&&my>=y&&my<y+2;
            for(int slot:slots)over|=mx>=leftPos+13+slot*18&&mx<leftPos+21+slot*18&&my>=topPos+FX_Y&&my<topPos+LINK_Y;
            if(over){g.renderTooltip(font,List.of(legendaryDescription(row),Ui.t(row.getBoolean("Doubled")?"kitchen.legendary_doubled":"kitchen.legendary_single")),Optional.empty(),mx,my);return;}
        }
    }
    private Component legendaryDescription(CompoundTag row){
        return Ui.t("kitchen.legendary_pair",Ui.t("kitchen.group."+row.getString("First")),Ui.t("kitchen.group."+row.getString("Second")),Ui.t("meal.effect."+MealRules.Effect.values()[row.getInt("Effect")].id));
    }
    /** Append to the normal Forge item tooltip so food HUD mods share one tooltip. */
    void ingredientTooltip(net.minecraftforge.event.entity.player.ItemTooltipEvent event){
        if(hoveredSlot==null||hoveredSlot.container!=menu.ingredients||hoveredSlot.getContainerSlot()>=6||hoveredSlot.getItem()!=event.getItemStack())return;
        int index=hoveredSlot.getContainerSlot();var spent=view().getIntArray("Spent");
        if(spent.length==6)event.getToolTip().add(Ui.t("kitchen.consume",spent[index]));
        var groups=view().getList("SlotGroups",Tag.TAG_STRING);
        if(index<groups.size()&&!groups.getString(index).isEmpty())event.getToolTip().add(Ui.t("kitchen.food_group",Ui.t("kitchen.group."+groups.getString(index))).copy().withStyle(net.minecraft.ChatFormatting.GOLD));
        var fx=view().getList("SlotFx",Tag.TAG_INT_ARRAY);
        if(index<fx.size())for(int e:fx.getIntArray(index))if(e>=0&&e<MealRules.Effect.values().length)event.getToolTip().add(Ui.t("meal.effect."+MealRules.Effect.values()[e].id).copy().withStyle(net.minecraft.ChatFormatting.AQUA));
        for(var entry:view().getList("LegendaryLinks",Tag.TAG_COMPOUND)){
            var link=(CompoundTag)entry;if(Arrays.stream(link.getIntArray("Slots")).anyMatch(slot->slot==index))event.getToolTip().add(legendaryDescription(link).copy().withStyle(net.minecraft.ChatFormatting.GOLD));
        }
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        var view=view();
        primary.active=view.contains("Problem")&&(ctaMode()==GATHER||ctaMode()==COOK&&(view.getString("Problem").isEmpty()||replacing()));
        if(confirming&&!replacing())confirming=false; // somebody emptied the pot, or the mode changed
        replaceYes.visible=replaceNo.visible=confirming;
        renderBackground(g);
        if(confirming){super.render(g,-1,-1,partial);confirmation(g,mx,my);return;}
        super.render(g,mx,my,partial);renderTooltip(g,mx,my);tooltips(g,mx,my);
        var list=view.getList("Pots",Tag.TAG_COMPOUND);
        for(int i=0;i<pots.size()&&i<list.size();i++)if(pots.get(i).visible&&pots.get(i).isHovered()&&!(hoveredSlot!=null&&hoveredSlot.hasItem())){
            var row=list.getCompound(i);var pos=BlockPos.of(row.getLong("Pos"));var meal=MealData.load(row.getCompound("Meal"));var lines=new ArrayList<Component>();
            lines.add(Ui.t("kitchen.pot_coordinates",pos.getX(),pos.getY(),pos.getZ()));lines.add(meal==null?Ui.t("kitchen.pot_empty"):meal.name());
            if(meal!=null){lines.add(Ui.t("kitchen.servings",row.getInt("Servings")));for(var bonus:meal.bonuses())lines.add(bonus.description(false));}
            g.renderTooltip(font,lines,Optional.empty(),mx,my);
        }
    }

    /** The question before a pot with stew is cooked over: dims the kitchen and asks. Only its two buttons answer; Escape means keep the stew. */
    private void confirmation(GuiGraphics g,int mx,int my){
        g.pose().pushPose();g.pose().translate(0,0,400); // above the item icons of the slots and the carried item
        g.fill(0,0,width,height,0xb0050a0e);
        int w=260,h=96,x=leftPos+WIDTH/2-w/2,y=topPos+HEIGHT/2-h/2+4;
        Ui.panel(g,x,y,w,h);g.fill(x,y,x+w,y+2,Ui.ORANGE);
        Ui.alert(g,x+10,y+11,Ui.ORANGE);g.drawString(font,Ui.t("kitchen.replace.title"),x+24,y+10,Ui.INK,false);
        var pot=chosenPot();var meal=pot==null?null:MealData.load(pot.getCompound("Meal"));
        var list=view().getList("Pots",Tag.TAG_COMPOUND);int number=1;for(int i=0;i<list.size();i++)if(pot!=null&&list.getCompound(i).getLong("Pos")==pot.getLong("Pos"))number=i+1;
        Ui.wrap(g,font,Ui.t("kitchen.replace.body",number,pot==null?0:pot.getInt("Servings"),meal==null?"?":meal.name().getString(),view().getInt("Servings")),x+12,y+26,w-24,Ui.MUTED,4);
        replaceYes.render(g,mx,my,0);replaceNo.render(g,mx,my,0);
        g.pose().popPose();
    }
    @Override public boolean mouseClicked(double mx,double my,int button){
        if(confirming){return replaceYes.mouseClicked(mx,my,button)||replaceNo.mouseClicked(mx,my,button)||true;}
        return super.mouseClicked(mx,my,button);
    }
    @Override public boolean mouseReleased(double mx,double my,int button){return confirming||super.mouseReleased(mx,my,button);}
    @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy){return confirming||super.mouseDragged(mx,my,button,dx,dy);}
    @Override public boolean keyPressed(int key,int scan,int mods){
        if(confirming){if(key==256)confirming=false;return true;} // Escape: keep the stew
        return super.keyPressed(key,scan,mods);
    }
}
