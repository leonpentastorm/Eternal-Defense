package dev.createarsenal.beacon;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;
import java.util.*;

/**
 * The kitchen: two preparation tabs, effect icons under every ingredient, a card per meal effect (with its field and home value),
 * a tier badge, pot meters and a pulsing Prepare button. Previews, capacity and failures are supplied by the server.
 */
final class MessHallScreen extends AbstractContainerScreen<MessHallMenu> {
    /** Accent of each hall tier: wood, copper, stone, canteen green. */
    static final int[] TIER={0xffc9a24b,0xffd9824a,0xff9fb3bc,0xff6ee07a};
    static final int WIDTH=310,HEIGHT=266;
    Ui.UiButton sandwich,stew,prepare,upgrade;final List<Ui.UiButton> pots=new ArrayList<>();
    MessHallScreen(MessHallMenu menu,Inventory inv,Component title){super(menu,inv,title);imageWidth=WIDTH;imageHeight=HEIGHT;inventoryLabelY=173;}
    private void action(int id){minecraft.gameMode.handleInventoryButtonClick(menu.containerId,id);}
    private int tierColor(){return TIER[Math.max(0,Math.min(3,menu.mk-1))];}
    @Override protected void init(){
        super.init();sandwich=addRenderableWidget(new Ui.UiButton(leftPos+10,topPos+29,95,18,Component.empty(),Ui.Look.TAB,b->action(0)).painter((g,self,h)->tab(g,self,new ItemStack(Items.BREAD),"kitchen.sandwich")).noLabel());
        stew=addRenderableWidget(new Ui.UiButton(leftPos+108,topPos+29,80,18,Component.empty(),Ui.Look.TAB,b->action(1)).painter((g,self,h)->tab(g,self,new ItemStack(ArsenalBeacon.COOK_POT.get()),"kitchen.stew")).noLabel());
        prepare=addRenderableWidget(new Ui.UiButton(leftPos+194,topPos+236,104,22,Component.empty(),Ui.Look.PRIMARY,b->action(2)).painter((g,self,h)->prepareFace(g,self)).noLabel());
        upgrade=addRenderableWidget(new Ui.UiButton(leftPos+194,topPos+29,104,18,Ui.t("kitchen.upgrade",menu.mk+1),Ui.Look.NORMAL,b->action(3)));
        for(int i=0;i<4;i++){final int index=i;pots.add(addRenderableWidget(new Ui.UiButton(leftPos+194,topPos+108+i*22,104,21,Component.empty(),Ui.Look.ROW,b->action(100+index)).painter((g,self,h)->potRow(g,self,index)).noLabel()));}
    }
    private void tab(GuiGraphics g,Ui.UiButton b,ItemStack icon,String key){
        g.renderItem(icon,b.getX()+3,b.getY()+1);Ui.text(g,font,Ui.t(key),b.getX()+22,b.getY()+5,b.selected?Ui.INK:Ui.MUTED,b.getWidth()-26);
    }
    /** A flickering flame, three stacked pixel layers. */
    private static void flame(GuiGraphics g,int x,int y,long t){
        int a=(int)(Math.sin(t*.011)*1.4),b=(int)(Math.sin(t*.017+1)*1.2);
        g.fill(x+2,y+6,x+8,y+10,0xffd9531e);g.fill(x+3+a,y+2,x+7+a,y+8,0xffff9a3d);g.fill(x+4+b,y+0,x+6+b,y+5,0xffffe36b);g.fill(x+4,y+7,x+6,y+9,0xfffff3b0);
    }
    private void prepareFace(GuiGraphics g,Ui.UiButton b){
        long t=net.minecraft.Util.getMillis();
        if(b.active){float pulse=.5f+.5f*(float)Math.sin(t*.006);int alpha=(int)(70+120*pulse)<<24;g.fill(b.getX()-1,b.getY()-1,b.getX()+b.getWidth()+1,b.getY(),alpha|0x5ae2df);g.fill(b.getX()-1,b.getY()+b.getHeight(),b.getX()+b.getWidth()+1,b.getY()+b.getHeight()+1,alpha|0x5ae2df);
            g.fill(b.getX()-1,b.getY(),b.getX(),b.getY()+b.getHeight(),alpha|0x5ae2df);g.fill(b.getX()+b.getWidth(),b.getY(),b.getX()+b.getWidth()+1,b.getY()+b.getHeight(),alpha|0x5ae2df);flame(g,b.getX()+7,b.getY()+6,t);}
        else g.fill(b.getX()+8,b.getY()+14,b.getX()+14,b.getY()+16,Ui.TRACK);
        var label=Ui.t("kitchen.prepare");Ui.text(g,font,label,b.getX()+22,b.getY()+7,b.active?Ui.INK:Ui.DISABLED,b.getWidth()-26);
    }
    private void potRow(GuiGraphics g,Ui.UiButton b,int index){
        var list=menu.view.getList("Pots",net.minecraft.nbt.Tag.TAG_COMPOUND);if(index>=list.size())return;var row=list.getCompound(index);
        int servings=row.getInt("Servings"),capacity=menu.hall==null?MealRules.tier(menu.mk).servings():MealRules.tier(menu.mk).servings();
        g.renderItem(new ItemStack(ArsenalBeacon.COOK_POT.get()),b.getX()+2,b.getY()+2);
        var meal=MealData.load(row.getCompound("Meal"));
        Ui.text(g,font,meal==null?Ui.t("kitchen.pot_row_empty",index+1):Ui.t("kitchen.pot_row_name",index+1,meal.name()),b.getX()+20,b.getY()+2,meal==null?Ui.MUTED:Ui.INK,b.getWidth()-24);
        int color=meal==null?Ui.TRACK:MealEffects.color(meal.bonuses().get(0).effect());
        Ui.bar(g,b.getX()+20,b.getY()+13,b.getWidth()-26,5,servings/(float)capacity,color);
    }
    private void effectIcon(GuiGraphics g,MealRules.Effect e,int x,int y,int size){g.blit(MealEffects.icon(e),x,y,size,size,0,0,18,18,18,18);}
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
        long t=net.minecraft.Util.getMillis();int accent=tierColor();
        Ui.panel(g,leftPos,topPos,imageWidth,imageHeight);Ui.header(g,font,leftPos,topPos,imageWidth,title,null);
        // steam drifting up out of the header band
        for(int i=0;i<7;i++){double phase=((t*.00035+i*.143)%1.0);int sx=leftPos+120+i*23+(int)(Math.sin(t*.002+i)*3),sy=topPos+24-(int)(phase*20);int alpha=(int)((1-phase)*90)<<24;g.fill(sx,sy,sx+2,sy+2,alpha|0xe8f4f8);}
        // tier badge: wood, copper, stone, canteen
        var tierLabel=Ui.t("kitchen.tier",menu.mk);int tw=font.width(tierLabel);int bx=leftPos+imageWidth-tw-Ui.pipsWidth(4)-26;
        g.fill(bx-5,topPos+5,bx+tw+Ui.pipsWidth(4)+13,topPos+21,Ui.DEEP);g.fill(bx-5,topPos+5,bx-2,topPos+21,accent);
        g.drawString(font,tierLabel,bx+2,topPos+9,accent,false);Ui.pips(g,bx+tw+8,topPos+11,menu.mk,4,accent);

        var view=menu.view;boolean isStew=view.getBoolean("StewMode");var meal=MealData.load(view.getCompound("Meal"));
        sandwich.selected=!isStew;stew.selected=isStew;prepare.active=view.contains("Problem")&&view.getString("Problem").isEmpty();
        // ingredient slots, each tinted by the effect its food feeds, with the effect icons below
        var fx=view.getList("SlotFx",net.minecraft.nbt.Tag.TAG_INT_ARRAY);
                for(int i=0;i<6;i++){
            int x=leftPos+11+i*18,y=topPos+52;int[] effects=i<fx.size()?fx.getIntArray(i):new int[0];
            slot(g,x,y,effects.length==0?Ui.EDGE:MealEffects.color(MealRules.Effect.values()[effects[0]]));
            for(int k=0;k<effects.length&&k<2;k++)effectIcon(g,MealRules.Effect.values()[effects[k]],x+k*8,y+19,8);
        }
        slot(g,leftPos+141,topPos+52,accent);Ui.text(g,font,Ui.t("kitchen.output"),leftPos+141,topPos+72,Ui.MUTED,48);
        // the effect cards
        int top=topPos+98;Ui.text(g,font,meal==null?Ui.t("kitchen.effects_none"):Ui.t("kitchen.effects",meal.bonuses().size(),isStew?3:2),leftPos+12,topPos+86,Ui.CYAN,176);
        for(int row=0;row<3;row++){
            int y=top+row*16;
            if(meal==null||row>=meal.bonuses().size()){if(row<(isStew?3:2)){Ui.card(g,leftPos+12,y,176,14,Ui.TRACK);Ui.text(g,font,Ui.t("kitchen.effect_open"),leftPos+34,y+3,Ui.DISABLED,150);}continue;}
            var bonus=meal.bonuses().get(row);int color=MealEffects.color(bonus.effect());
            Ui.card(g,leftPos+12,y,176,14,color);effectIcon(g,bonus.effect(),leftPos+18,y+1,12);
            Ui.text(g,font,Component.translatable("gui.arsenal_beacon.meal.effect."+bonus.effect().id),leftPos+34,y+3,Ui.INK,70);
            Ui.pips(g,leftPos+106,y+4,bonus.strength(),3,color);
            int right=leftPos+185;if(bonus.pair()){var pair=Component.literal("×2");Ui.right(g,font,pair,right,y+3,Ui.BRASS);right-=font.width(pair)+4;}
            var home=Component.literal(bonus.amountText(true));Ui.right(g,font,home,right,y+3,Ui.BRASS);right-=font.width(home)+4;
            Ui.right(g,font,Component.literal(bonus.amountText(false)+"▸"),right,y+3,Ui.CYAN);
        }
        String problem=view.getString("Problem");
        if(!problem.isEmpty())Ui.wrap(g,font,Ui.t("kitchen.problem."+problem),leftPos+12,top+51,176,Ui.ORANGE,2);
        else if(meal!=null)Ui.text(g,font,Ui.t("kitchen.ready"),leftPos+12,top+51,0xff6ee07a,176);

        // right column
        int rx=leftPos+194;
        Ui.card(g,rx,topPos+52,104,40,accent);
        var tier=MealRules.tier(menu.mk);
        Ui.text(g,font,Ui.t("kitchen.stat_pots",tier.pots()),rx+8,topPos+56,Ui.INK,92);
        Ui.text(g,font,Ui.t("kitchen.stat_servings",tier.servings()),rx+8,topPos+66,Ui.INK,92);
        Ui.text(g,font,Ui.t("kitchen.stat_food",tier.ingredients()),rx+8,topPos+76,Ui.MUTED,92);
        if(isStew)Ui.text(g,font,Ui.t("kitchen.linked",view.getInt("Linked"),view.getInt("Capacity")),rx,topPos+97,Ui.MUTED,104);
        var list=view.getList("Pots",net.minecraft.nbt.Tag.TAG_COMPOUND);
        for(int i=0;i<pots.size();i++){var b=pots.get(i);b.visible=isStew&&i<list.size();if(!b.visible)continue;var row=list.getCompound(i);var pos=BlockPos.of(row.getLong("Pos"));b.selected=view.contains("Selected")&&view.getLong("Selected")==pos.asLong();b.accent=accent;}
        if(!isStew)Ui.wrap(g,font,Ui.t("kitchen.sandwich_hint"),rx,topPos+98,104,Ui.MUTED,9);
        upgrade.visible=menu.mk<4;upgrade.active=view.getInt("UpgradeHave")>=view.getInt("UpgradeCost")&&view.contains("UpgradeCost");
        // batch bar: how much of the food the batch needs is there
        int need=Math.max(1,view.getInt("Required")),have=view.getInt("Available");
        Ui.text(g,font,Ui.t("kitchen.quantity",have,view.getInt("Required")),rx,topPos+212,prepare.active||have>=need?Ui.MUTED:Ui.ORANGE,104);
        Ui.bar(g,rx,topPos+223,104,6,have/(float)need,have>=need?Ui.CYAN:Ui.ORANGE);
        Ui.text(g,font,Ui.t("kitchen.servings",view.getInt("Servings")),rx,topPos+201,Ui.BRASS,104);
        for(int r=0;r<3;r++)for(int c=0;c<9;c++)slot(g,leftPos+11+c*18,topPos+184+r*18,Ui.EDGE);
        for(int c=0;c<9;c++)slot(g,leftPos+11+c*18,topPos+242,Ui.EDGE);
        // a little spark over the output slot while there is something to take
        if(!menu.ingredients.getItem(6).isEmpty())for(int i=0;i<3;i++){double p=((t*.0009+i*.33)%1.0);g.fill(leftPos+143+i*5,topPos+50-(int)(p*10),leftPos+145+i*5,topPos+52-(int)(p*10),((int)((1-p)*200)<<24)|0xffe36b);}
    }
    private void mealTooltip(GuiGraphics g,int mx,int my){
        var view=menu.view;var meal=MealData.load(view.getCompound("Meal"));
        if(mx>=leftPos+194&&mx<leftPos+298&&my>=topPos+210&&my<topPos+230)g.renderTooltip(font,Ui.t("kitchen.quantity_hint",view.getInt("Required"),view.getInt("Available")),mx,my);
        if(meal!=null)for(int row=0;row<meal.bonuses().size();row++){
            int y=topPos+98+row*16;
            if(mx>=leftPos+12&&mx<leftPos+188&&my>=y&&my<y+14){
                var b=meal.bonuses().get(row);var lines=new ArrayList<Component>();
                lines.add(Component.translatable("gui.arsenal_beacon.meal.effect."+b.effect().id));lines.add(Ui.t("meal.preview_field",b.description(false)));lines.add(Ui.t("meal.preview_home",b.description(true)));
                if(b.pair())lines.add(Ui.t("kitchen.pair"));
                g.renderTooltip(font,lines,Optional.empty(),mx,my);
            }
        }
        var spent=view.getIntArray("Spent");var fx=view.getList("SlotFx",net.minecraft.nbt.Tag.TAG_INT_ARRAY);
        for(int i=0;i<6;i++)if(mx>=leftPos+12+i*18&&mx<leftPos+28+i*18&&my>=topPos+53&&my<topPos+69&&menu.getSlot(i).hasItem()){
            var lines=new ArrayList<Component>();if(spent.length==6)lines.add(Ui.t("kitchen.consume",spent[i]));
            if(i<fx.size())for(int e:fx.getIntArray(i))lines.add(Component.translatable("gui.arsenal_beacon.meal.effect."+MealRules.Effect.values()[e].id).withStyle(net.minecraft.ChatFormatting.AQUA));
            if(!lines.isEmpty())g.renderTooltip(font,lines,Optional.empty(),mx,my);
        }
    }
    private void slot(GuiGraphics g,int x,int y,int frame){g.fill(x,y,x+18,y+18,frame);Ui.inset(g,x+1,y+1,16,16);}
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){g.drawString(font,playerInventoryTitle,12,173,Ui.MUTED,false);}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        renderBackground(g);super.render(g,mx,my,partial);renderTooltip(g,mx,my);mealTooltip(g,mx,my);
        var rows=menu.view.getList("Pots",net.minecraft.nbt.Tag.TAG_COMPOUND);
        for(int i=0;i<pots.size()&&i<rows.size();i++)if(pots.get(i).visible&&pots.get(i).isHovered()){
            var row=rows.getCompound(i);var pos=BlockPos.of(row.getLong("Pos"));var meal=MealData.load(row.getCompound("Meal"));var lines=new ArrayList<Component>();
            lines.add(Ui.t("kitchen.pot_coordinates",pos.getX(),pos.getY(),pos.getZ()));lines.add(meal==null?Ui.t("kitchen.pot_empty"):meal.name());
            if(meal!=null){lines.add(Ui.t("kitchen.servings",row.getInt("Servings")));for(var bonus:meal.bonuses())lines.add(bonus.description(false));}
            g.renderTooltip(font,lines,Optional.empty(),mx,my);
        }
        if(upgrade.visible&&upgrade.isHovered()){var item=ItemStack.of(menu.view.getCompound("UpgradeItem"));g.renderTooltip(font,List.of(Ui.t("kitchen.upgrade_cost",menu.view.getInt("UpgradeCost"),item.getHoverName()),Ui.t("kitchen.upgrade_have",menu.view.getInt("UpgradeHave"))),Optional.empty(),mx,my);}
    }
}
