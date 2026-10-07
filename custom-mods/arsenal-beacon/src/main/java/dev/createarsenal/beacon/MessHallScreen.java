package dev.createarsenal.beacon;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import java.util.*;

/** Two separate preparation tabs; previews, capacity and failures are supplied by the server. */
final class MessHallScreen extends AbstractContainerScreen<MessHallMenu> {
    Ui.UiButton sandwich,stew,prepare,upgrade;final List<Ui.UiButton> pots=new ArrayList<>();
    MessHallScreen(MessHallMenu menu,Inventory inv,Component title){super(menu,inv,title);imageWidth=310;imageHeight=240;inventoryLabelY=149;}
    private void action(int id){minecraft.gameMode.handleInventoryButtonClick(menu.containerId,id);}
    @Override protected void init(){
        super.init();sandwich=addRenderableWidget(new Ui.UiButton(leftPos+10,topPos+29,95,18,Ui.t("kitchen.sandwich"),Ui.Look.TAB,b->action(0)));
        stew=addRenderableWidget(new Ui.UiButton(leftPos+108,topPos+29,80,18,Ui.t("kitchen.stew"),Ui.Look.TAB,b->action(1)));
        prepare=addRenderableWidget(new Ui.UiButton(leftPos+194,topPos+215,104,22,Ui.t("kitchen.prepare"),Ui.Look.PRIMARY,b->action(2)));
        upgrade=addRenderableWidget(new Ui.UiButton(leftPos+194,topPos+29,104,18,Ui.t("kitchen.upgrade",menu.mk+1),Ui.Look.NORMAL,b->action(3)));
        for(int i=0;i<4;i++){final int index=i;pots.add(addRenderableWidget(new Ui.UiButton(leftPos+194,topPos+54+i*30,104,27,Component.empty(),Ui.Look.ROW,b->action(100+index))));}
    }
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
        Ui.panel(g,leftPos,topPos,imageWidth,imageHeight);Ui.header(g,font,leftPos,topPos,imageWidth,title,null);
        Ui.text(g,font,Ui.t("kitchen.ingredients"),leftPos+12,topPos+73,Ui.MUTED,172);
        for(int i=0;i<6;i++)slot(g,leftPos+11+i*18,topPos+52);slot(g,leftPos+141,topPos+52);
        Ui.text(g,font,Ui.t("kitchen.output"),leftPos+139,topPos+73,Ui.MUTED,48);
        var view=menu.view;boolean isStew=view.getBoolean("StewMode");var meal=MealData.load(view.getCompound("Meal"));
        sandwich.selected=!isStew;stew.selected=isStew;prepare.active=view.contains("Problem")&&view.getString("Problem").isEmpty();
        if(meal!=null){Ui.text(g,font,Ui.t("kitchen.effects",meal.bonuses().size(),isStew?3:2),leftPos+12,topPos+87,Ui.CYAN,176);int y=topPos+99;for(var bonus:meal.bonuses()){Ui.text(g,font,bonus.description(false),leftPos+12,y,Ui.INK,176);y+=11;}}
        Ui.text(g,font,Ui.t("kitchen.servings",view.getInt("Servings")),leftPos+194,topPos+191,Ui.BRASS,104);
        Ui.text(g,font,Ui.t("kitchen.quantity",view.getInt("Available"),view.getInt("Required")),leftPos+194,topPos+203,prepare.active?Ui.MUTED:Ui.ORANGE,104);
        var list=view.getList("Pots",net.minecraft.nbt.Tag.TAG_COMPOUND);
        for(int i=0;i<pots.size();i++){var b=pots.get(i);b.visible=isStew&&i<list.size();if(!b.visible)continue;var row=list.getCompound(i);var pos=BlockPos.of(row.getLong("Pos"));b.selected=view.contains("Selected")&&view.getLong("Selected")==pos.asLong();b.setMessage(row.getInt("Servings")==0?Ui.t("kitchen.pot_row_empty",i+1):Ui.t("kitchen.pot_row",i+1,row.getInt("Servings")));}
        upgrade.visible=menu.mk<4;upgrade.active=view.getInt("UpgradeHave")>=view.getInt("UpgradeCost")&&view.contains("UpgradeCost");
        if(isStew)Ui.text(g,font,Ui.t("kitchen.links",view.getInt("Linked"),view.getInt("Capacity")),leftPos+194,topPos+180,Ui.MUTED,104);
        else Ui.wrap(g,font,Ui.t("kitchen.sandwich_hint"),leftPos+194,topPos+58,104,Ui.MUTED,9);
        String problem=view.getString("Problem");if(!problem.isEmpty())Ui.wrap(g,font,Ui.t("kitchen.problem."+problem),leftPos+12,topPos+135,286,Ui.ORANGE,2);
        for(int r=0;r<3;r++)for(int c=0;c<9;c++)slot(g,leftPos+11+c*18,topPos+160+r*18);
        for(int c=0;c<9;c++)slot(g,leftPos+11+c*18,topPos+218);
    }
    private void mealTooltip(GuiGraphics g,int mx,int my){
        var view=menu.view;
        if(mx>=leftPos+194&&mx<leftPos+298&&my>=topPos+201&&my<topPos+215)g.renderTooltip(font,Ui.t("kitchen.quantity_hint",view.getInt("Required"),view.getInt("Available")),mx,my);
        if(mx>=leftPos+12&&mx<leftPos+188&&my>=topPos+99&&my<topPos+133){var meal=MealData.load(view.getCompound("Meal"));if(meal!=null){var lines=new ArrayList<Component>();for(var b:meal.bonuses()){lines.add(Component.translatable("gui.arsenal_beacon.meal.effect."+b.effect().id));lines.add(Ui.t("meal.preview_field",b.description(false)));lines.add(Ui.t("meal.preview_home",b.description(true)));}g.renderTooltip(font,lines,Optional.empty(),mx,my);}}
        var spent=view.getIntArray("Spent");if(spent.length==6)for(int i=0;i<6;i++)if(mx>=leftPos+12+i*18&&mx<leftPos+28+i*18&&my>=topPos+53&&my<topPos+69)g.renderTooltip(font,Ui.t("kitchen.consume",spent[i]),mx,my);
    }
    private void slot(GuiGraphics g,int x,int y){g.fill(x,y,x+18,y+18,Ui.EDGE);Ui.inset(g,x+1,y+1,16,16);}
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){g.drawString(font,playerInventoryTitle,12,149,Ui.MUTED,false);}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){renderBackground(g);super.render(g,mx,my,partial);renderTooltip(g,mx,my);mealTooltip(g,mx,my);var rows=menu.view.getList("Pots",net.minecraft.nbt.Tag.TAG_COMPOUND);for(int i=0;i<pots.size()&&i<rows.size();i++)if(pots.get(i).visible&&pots.get(i).isHovered()){var row=rows.getCompound(i);var pos=BlockPos.of(row.getLong("Pos"));var meal=MealData.load(row.getCompound("Meal"));var lines=new ArrayList<Component>();lines.add(Ui.t("kitchen.pot_coordinates",pos.getX(),pos.getY(),pos.getZ()));lines.add(meal==null?Ui.t("kitchen.pot_empty"):meal.name());if(meal!=null)for(var bonus:meal.bonuses())lines.add(bonus.description(false));g.renderTooltip(font,lines,Optional.empty(),mx,my);}if(upgrade.visible&&upgrade.isHovered()){var item=net.minecraft.world.item.ItemStack.of(menu.view.getCompound("UpgradeItem"));g.renderTooltip(font,List.of(Ui.t("kitchen.upgrade_cost",menu.view.getInt("UpgradeCost"),item.getHoverName()),Ui.t("kitchen.upgrade_have",menu.view.getInt("UpgradeHave"))),Optional.empty(),mx,my);}}
}
