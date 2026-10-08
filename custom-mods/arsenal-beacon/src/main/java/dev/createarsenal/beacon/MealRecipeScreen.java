package dev.createarsenal.beacon;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Cookbook stays attached to the live server container; recipes are chosen through standard menu buttons. */
final class MealRecipeScreen extends Screen {
    final MessHallScreen parent;final MessHallMenu menu;int left,top,w,h,page;boolean legendary;MealRules.Effect focused=MealRules.Effect.VITALITY;
    final List<Ui.UiButton> choices=new ArrayList<>();Ui.UiButton choose,previous,next;
    MealRecipeScreen(MessHallScreen parent){super(Ui.t("kitchen.guide"));this.parent=parent;this.menu=parent.currentMenu();}
    int pageSize(){return h>=250?4:3;}
    private List<MealRules.Effect> effects(){return Arrays.stream(MealRules.Effect.values()).filter(e->e.gun()==legendary).toList();}
    private CompoundTag row(MealRules.Effect effect){for(var tag:menu.view.getList("Choices",Tag.TAG_COMPOUND)){var r=(CompoundTag)tag;if(r.getInt("Effect")==effect.ordinal())return r;}return new CompoundTag();}
    private void action(int id){minecraft.gameMode.handleInventoryButtonClick(menu.containerId,id);}
    @Override protected void init(){
        w=Math.min(470,width-16);h=Math.min(280,height-16);left=(width-w)/2;top=(height-h)/2;choices.clear();int half=(w-28)/2;
        addRenderableWidget(new Ui.UiButton(left+10,top+30,half,18,Ui.t("kitchen.ordinary"),Ui.Look.TAB,b->{legendary=false;page=0;focused=effects().get(0);rebuildWidgets();}));
        addRenderableWidget(new Ui.UiButton(left+14+half,top+30,half,18,Ui.t("kitchen.legendary"),Ui.Look.TAB,b->{legendary=true;page=0;focused=effects().get(0);rebuildWidgets();}));
        for(int i=0;i<pageSize();i++){int index=i;choices.add(addRenderableWidget(new Ui.UiButton(left+10,top+80+i*27,half,25,Component.empty(),Ui.Look.ROW,b->{int at=page*pageSize()+index;if(at<effects().size())focused=effects().get(at);}).painter((g,b,hover)->{
            int at=page*pageSize()+index;if(at>=effects().size())return;var effect=effects().get(at);var row=row(effect);g.blit(MealEffects.icon(effect),b.getX()+4,b.getY()+4,16,16,0,0,18,18,18,18);
            Ui.text(g,font,Ui.t("meal.effect."+effect.id),b.getX()+23,b.getY()+4,row.getBoolean("Available")||row.getBoolean("Selected")?Ui.INK:Ui.MUTED,b.getWidth()-27);
            Ui.text(g,font,Ui.t(row.getBoolean("Selected")?"kitchen.chosen":row.getBoolean("Available")?"kitchen.ready":"kitchen.add_food"),b.getX()+23,b.getY()+14,row.getBoolean("Selected")?Ui.CYAN:Ui.MUTED,b.getWidth()-27);
        }).noLabel()));}
        choose=addRenderableWidget(new Ui.UiButton(left+14+half,top+h-48,half,18,Ui.t("kitchen.choose"),Ui.Look.PRIMARY,b->action(40+focused.ordinal())));
        previous=addRenderableWidget(new Ui.UiButton(left+10,top+h-48,22,18,Component.literal("<"),Ui.Look.NORMAL,b->{page=Math.max(0,page-1);focused=effects().get(page*pageSize());}));
        next=addRenderableWidget(new Ui.UiButton(left+36,top+h-48,22,18,Component.literal(">"),Ui.Look.NORMAL,b->{page=Math.min((effects().size()-1)/pageSize(),page+1);focused=effects().get(page*pageSize());}));
        addRenderableWidget(new Ui.UiButton(left+10,top+h-25,half,18,Ui.t("kitchen.auto"),Ui.Look.NORMAL,b->action(4)));
        addRenderableWidget(new Ui.UiButton(left+14+half,top+h-25,half,18,Ui.t("back"),Ui.Look.NORMAL,b->onClose()));
    }
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void tick(){if(minecraft.player==null||minecraft.player.containerMenu!=menu)minecraft.setScreen(null);}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        renderBackground(g);Ui.panel(g,left,top,w,h);Ui.header(g,font,left,top,w,title,null);int half=(w-28)/2,rx=left+14+half;
        Ui.wrap(g,font,Ui.t(legendary?"kitchen.guide_legendary":"kitchen.guide_ordinary"),left+10,top+53,w-20,Ui.MUTED,2);
        for(int i=0;i<pageSize();i++){int at=page*pageSize()+i;var b=choices.get(i);b.visible=at<effects().size();b.selected=b.visible&&effects().get(at)==focused;}
        previous.active=page>0;next.active=(page+1)*pageSize()<effects().size();Ui.text(g,font,Component.literal((page+1)+" / "+((effects().size()+pageSize()-1)/pageSize())),left+64,top+h-43,Ui.MUTED,half-58);
        var row=row(focused);choose.active=row.getBoolean("Selected")||row.getBoolean("Available");choose.setMessage(Ui.t(row.getBoolean("Selected")?"kitchen.unchoose":"kitchen.choose"));
        Ui.text(g,font,Ui.t("meal.effect."+focused.id),rx,top+81,Ui.CYAN,half);
        var bonus=new MealData.Bonus(focused,1,row.getBoolean("Pair"));Ui.text(g,font,Ui.t("meal.preview_field",bonus.description(false)),rx,top+92,Ui.INK,half);
        Ui.text(g,font,Ui.t("meal.preview_home",bonus.description(true)),rx,top+103,Ui.BRASS,half);
        if(legendary){var mix=MealRules.MIXES.stream().filter(m->m.effect()==focused).findFirst().orElseThrow();Ui.wrap(g,font,Ui.t("kitchen.recipe_groups",Ui.t("kitchen.group."+mix.first()),Ui.t("kitchen.group."+mix.second())),rx,top+116,half,Ui.INK,2);}
        else Ui.wrap(g,font,Ui.t("kitchen.recipe_normal"),rx,top+116,half,Ui.INK,2);
        var sources=row.getList("Sources",Tag.TAG_COMPOUND);int cols=Math.max(1,half/16),iconY=top+139;ItemStack hover=ItemStack.EMPTY;String hoverGroup="";
        int maxRows=Math.max(1,(h-48-139)/16); // Leave selection and navigation unobstructed.
        for(int i=0;i<sources.size()&&i<cols*maxRows;i++){
            var s=sources.getCompound(i);var item=ItemStack.of(s.getCompound("Item"));int x=rx+(i%cols)*16,y=iconY+(i/cols)*16;Ui.field(g,x-1,y-1,16,16,false);g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(.875f,.875f,1);g.renderItem(item,0,0);g.pose().popPose();
            if(Ui.inside(mx,my,x,y,16,16)){hover=item;hoverGroup=s.getString("Group");}
        }
        super.render(g,mx,my,partial);
        if(!hover.isEmpty())g.renderTooltip(font,List.of(hover.getHoverName(),Ui.t("kitchen.food_group",Ui.t("kitchen.group."+hoverGroup))),Optional.empty(),mx,my);
        if(Ui.inside(mx,my,rx,top+92,half,20))g.renderComponentTooltip(font,List.of(Ui.t("meal.preview_field",bonus.description(false)),Ui.t("meal.preview_home",bonus.description(true))),mx,my);
        if(Ui.inside(mx,my,rx,top+116,half,19))g.renderTooltip(font,List.of(Ui.t(legendary?"kitchen.legendary_detail":"kitchen.ordinary_detail"),Ui.t("kitchen.double_unlock")),Optional.empty(),mx,my);
        if(!choose.active&&choose.isHovered())g.renderTooltip(font,Ui.t("kitchen.choose_problem"),mx,my);
    }
}
