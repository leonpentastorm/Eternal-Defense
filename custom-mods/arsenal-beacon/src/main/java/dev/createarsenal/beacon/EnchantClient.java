package dev.createarsenal.beacon;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * The Tactical Enchanting Table's screen (0.0.27): the gear slot and the player's inventory on the left with the ACTIVE FOBs, the table's Mk
 * and the Warlord Seals; on the right every enchant the gear takes, each level a button with its price, a locked level saying why. The list
 * is worked out here from the slot and the state the server sends ({@link TacticalEnchanting#state}); every purchase is checked again there.
 */
@Mod.EventBusSubscriber(modid=ArsenalBeacon.ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class EnchantClient {
    private EnchantClient(){}
    @SubscribeEvent public static void screens(FMLClientSetupEvent e){e.enqueueWork(()->MenuScreens.register(ArsenalBeacon.ENCHANT_MENU.get(),EnchantScreen::new));}
    static final int ARCANE=0xffb48cff;
    static void state(CompoundTag data){if(net.minecraft.client.Minecraft.getInstance().screen instanceof EnchantScreen s)s.update(data);}

    static final class EnchantScreen extends AbstractContainerScreen<TacticalEnchanting.EnchantMenu> {
        /** 0.0.30: bigger and calmer (the owner saw the empty slot's line run into the FOB line). A level button fits "III" (22 wide, 23 apart). */
        static final int W=440,H=260,ROW=22,LIST_X=214,LEVEL_W=22,LEVEL_STEP=23;
        /** The left column: x 14, 184 wide; each block of text has its own band, with a rule between them. */
        static final int SIDE_X=14,SIDE_W=184,GEAR_LABEL_Y=34,GEAR_TEXT_LINES=3,FOBS_RULE_Y=82,FOBS_Y=88,MK_RULE_Y=114,MK_Y=120,UPGRADE_Y=132,ENERGY_Y=158;
        private CompoundTag data;private ItemStack shown=ItemStack.EMPTY;private int scroll;
        private final List<Enchantment> rows=new ArrayList<>();
        EnchantScreen(TacticalEnchanting.EnchantMenu menu,Inventory inv,Component title){super(menu,inv,title);data=menu.data;imageWidth=W;imageHeight=H;}
        void update(CompoundTag next){data=next;rebuildWidgets();}
        private ItemStack gear(){return menu.getSlot(0).getItem();}
        private int fobs(){return data.getInt("fobs");}
        private int seals(Enchantment e){return data.getCompound("seals").getInt(TacticalEnchanting.id(e));}
        private int listTop(){return topPos+50;}
        private int visibleRows(){return (topPos+H-24-listTop())/ROW;}
        /** The left edge of an enchant's level buttons (right-aligned in its row). */
        private int levelsX(Enchantment e){return leftPos+W-12-e.getMaxLevel()*LEVEL_STEP+1;}

        @Override protected void init(){
            super.init();clearWidgets();rows.clear();
            addRenderableWidget(new Ui.UiButton(leftPos+W-28,topPos+4,22,18,Component.literal("X"),Ui.Look.NORMAL,b->onClose()));
            // the table's next Mk
            int mk=data.getInt("mk");
            if(mk<EnchantRules.MAX_MK){
                var up=addRenderableWidget(new Ui.UiButton(leftPos+SIDE_X,topPos+UPGRADE_Y,SIDE_W,18,Ui.t("enchant.upgrade",EnchantRules.roman(mk+1),EnchantRules.DISCOUNT[mk+1]),Ui.Look.NORMAL,b->click(TacticalEnchanting.UPGRADE)));
                boolean afford=data.getBoolean("creative")||data.getInt("upgradeHave")>=data.getInt("upgradeAmount");
                up.active=afford;up.warning=!afford;
                up.setTooltip(Tooltip.create(Ui.t("enchant.upgrade.tip",data.getInt("upgradeAmount"),ItemStack.of(data.getCompound("upgradeItem")).getHoverName())));
            }
            var gear=gear();shown=gear.copy();
            if(gear.isEmpty())return;
            for(var e:TacticalEnchanting.catalogue())if(e.canEnchant(gear))rows.add(e);
            scroll=Math.max(0,Math.min(scroll,Math.max(0,rows.size()-visibleRows())));
            int y=listTop();
            for(int r=scroll;r<rows.size()&&r<scroll+visibleRows();r++,y+=ROW){
                var e=rows.get(r);int index=TacticalEnchanting.catalogue().indexOf(e);boolean treasure=TacticalEnchanting.treasure(e);
                int unlocked=treasure?EnchantRules.unlockedLevel(TacticalEnchanting.id(e),seals(e),e.getMaxLevel()):e.getMaxLevel();
                int bx=levelsX(e);
                // a treasure enchant still locked (or its next level): one seal unlocks it
                if(treasure&&EnchantRules.sealsForNext(TacticalEnchanting.id(e),seals(e),e.getMaxLevel())>0){
                    int ti=EnchantRules.TREASURE.indexOf(TacticalEnchanting.id(e));boolean has=data.getBoolean("creative")||data.getInt("sealsCarried")>0;
                    var seal=addRenderableWidget(new Ui.UiButton(bx-50,y+2,46,16,Ui.t("enchant.unlock"),Ui.Look.NORMAL,b->click(TacticalEnchanting.UNLOCK+ti)));
                    seal.active=has;seal.warning=!has;seal.setTooltip(Tooltip.create(Ui.t(unlocked==0?"enchant.unlock.tip":"enchant.unlock.next.tip",unlocked+1)));
                }
                for(int level=1;level<=e.getMaxLevel();level++){
                    int lv=level;String problem=TacticalEnchanting.problem(gear,e,level,fobs(),seals(e));
                    int price=TacticalEnchanting.price(e,level,data.getInt("mk"));boolean afford=data.getBoolean("creative")||data.getInt("energy")>=price;
                    var b=addRenderableWidget(new Ui.UiButton(bx+(level-1)*LEVEL_STEP,y+2,LEVEL_W,16,Component.literal(EnchantRules.roman(level)),problem==null?Ui.Look.PRIMARY:Ui.Look.NORMAL,x->click(TacticalEnchanting.buyButton(index,lv))));
                    b.active=problem==null&&afford;b.warning=problem==null&&!afford;
                    var name=Component.translatable(e.getDescriptionId()).append(" "+EnchantRules.roman(level));
                    b.setTooltip(Tooltip.create(problem==null?Ui.t(afford?"enchant.price":"enchant.price.short",name,price):Ui.t("enchant.locked."+problem,name,EnchantRules.fobsFor(level,e.getMaxLevel()),fobs())));
                }
            }
        }
        private void click(int id){BeaconNetwork.menuButton(menu.containerId,id);}
        @Override protected void containerTick(){super.containerTick();if(!ItemStack.matches(gear(),shown)){scroll=0;rebuildWidgets();}}
        @Override public boolean mouseScrolled(double mx,double my,double delta){
            if(mx>=leftPos+LIST_X&&rows.size()>visibleRows()){scroll=Math.max(0,Math.min(rows.size()-visibleRows(),scroll-(int)Math.signum(delta)));rebuildWidgets();return true;}
            return super.mouseScrolled(mx,my,delta);
        }

        @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
            Ui.panel(g,leftPos,topPos,W,H);
            Ui.header(g,font,leftPos,topPos,W,title,Ui.t("enchant.subtitle"));
            int x=leftPos+SIDE_X,w=SIDE_W;
            // the gear: its slot, and beside it what goes there (up to three lines, ending above the first rule) or what is in it
            Ui.text(g,font,Ui.t("enchant.gear"),x,topPos+GEAR_LABEL_Y,Ui.BRASS,w);
            int gx=leftPos+TacticalEnchanting.EnchantMenu.GEAR_X,gy=topPos+TacticalEnchanting.EnchantMenu.GEAR_Y;
            Ui.slot(g,gx-1,gy-1,ARCANE);
            var gear=gear();int tx=gx+24,tw=x+w-tx;
            if(gear.isEmpty())Ui.wrap(g,font,Ui.t("enchant.gear.empty"),tx,gy-4,tw,Ui.MUTED,GEAR_TEXT_LINES);
            else{
                Ui.text(g,font,gear.getHoverName(),tx,gy,Ui.INK,tw);
                var have=EnchantmentHelper.getEnchantments(gear);
                Ui.text(g,font,have.isEmpty()?Ui.t("enchant.gear.none"):Ui.t("enchant.gear.has",have.size()),tx,gy+11,Ui.MUTED,tw);
            }
            for(int i=1;i<menu.slots.size();i++){var sl=menu.slots.get(i);Ui.slot(g,leftPos+sl.x-1,topPos+sl.y-1,Ui.EDGE);}
            // the ACTIVE FOBs
            Ui.rule(g,x,topPos+FOBS_RULE_Y,w);
            Ui.text(g,font,Ui.t("enchant.fobs",fobs(),FobRules.MAX_FOBS),x,topPos+FOBS_Y,fobs()>0?FobClient.OLIVE:Ui.ORANGE,w);
            Ui.text(g,font,Ui.t("enchant.fobs.how"),x,topPos+FOBS_Y+12,Ui.MUTED,w);
            // the table's Mk and its upgrade button (or "Fully upgraded"), then what you carry
            Ui.rule(g,x,topPos+MK_RULE_Y,w);
            int mk=data.getInt("mk");
            Ui.text(g,font,mk==0?Ui.t("enchant.mk.none"):Ui.t("enchant.mk",EnchantRules.roman(mk),EnchantRules.DISCOUNT[mk]),x,topPos+MK_Y,Ui.INK,w);
            if(mk>=EnchantRules.MAX_MK)Ui.text(g,font,Ui.t("enchant.mk.max"),x,topPos+UPGRADE_Y+5,Ui.MUTED,w);
            Ui.text(g,font,data.getBoolean("creative")?Ui.t("enchant.energy.creative",data.getInt("sealsCarried")):Ui.t("enchant.energy",data.getInt("energy"),data.getInt("sealsCarried")),x,topPos+ENERGY_Y,Ui.MUTED,w);
            // the list
            int lx=leftPos+LIST_X,lw=W-LIST_X-12;
            Ui.text(g,font,Ui.t("enchant.list"),lx,topPos+36,ARCANE,lw);
            if(gear.isEmpty()){Ui.wrap(g,font,Ui.t("enchant.list.empty"),lx,listTop(),lw,Ui.MUTED,6);}
            else if(rows.isEmpty())Ui.wrap(g,font,Ui.t("enchant.list.nothing"),lx,listTop(),lw,Ui.MUTED,4);
            int ry=listTop();
            for(int r=scroll;r<rows.size()&&r<scroll+visibleRows();r++,ry+=ROW){
                var e=rows.get(r);int bx=levelsX(e);boolean treasure=TacticalEnchanting.treasure(e);
                int nameW=bx-lx-(treasure&&EnchantRules.sealsForNext(TacticalEnchanting.id(e),seals(e),e.getMaxLevel())>0?54:4);
                Ui.card(g,lx,ry,lw,ROW-2,treasure?0xffd4af37:ARCANE);
                Ui.text(g,font,Component.translatable(e.getDescriptionId()),lx+6,ry+3,Ui.INK,nameW);
                int have=EnchantmentHelper.getItemEnchantmentLevel(e,gear),top=EnchantRules.maxLevel(e.getMaxLevel(),fobs());
                Component sub=have>0?Ui.t("enchant.row.has",EnchantRules.roman(have)):treasure&&seals(e)==0?Ui.t("enchant.row.treasure"):Ui.t("enchant.row.top",EnchantRules.roman(top));
                Ui.text(g,font,sub,lx+6,ry+12,Ui.MUTED,nameW);
            }
            // the last result under the list; a long one (a refusal naming the enchant) takes two lines, in place of the scroll hint
            var line=message();boolean twoLines=line!=null&&font.width(line)>lw;
            if(rows.size()>visibleRows()&&!twoLines)Ui.text(g,font,Ui.t("enchant.scroll",scroll+1,Math.min(rows.size(),scroll+visibleRows()),rows.size()),lx,topPos+H-22,Ui.MUTED,lw);
            if(line!=null){String key=data.getString("message");int color=key.equals("enchant.result.done")||key.equals("enchant.result.upgraded")||key.equals("enchant.result.unlocked")?FobClient.OLIVE:Ui.ORANGE;
                if(twoLines)Ui.wrap(g,font,line,lx,topPos+H-23,lw,color,2);else Ui.text(g,font,line,lx,topPos+H-12,color,lw);}
        }
        /** The last result the server sent: its finished line when it names an enchant, else the message key's text; null when there is none. */
        Component message(){
            var key=data.getString("message");if(key.isEmpty())return null;
            return data.contains("detail")?Component.Serializer.fromJson(data.getString("detail")):Component.translatable("gui.arsenal_beacon."+key);
        }
        @Override protected void renderLabels(GuiGraphics g,int mx,int my){}
        @Override public void render(GuiGraphics g,int mx,int my,float partial){
            renderBackground(g);super.render(g,mx,my,partial);renderTooltip(g,mx,my);
            // an enchant's name (cut short in a narrow row): its full name, top level and the ACTIVE FOBs each level needs
            int ry=listTop();
            for(int r=scroll;r<rows.size()&&r<scroll+visibleRows();r++,ry+=ROW){
                var e=rows.get(r);int nameRight=levelsX(e)-(TacticalEnchanting.treasure(e)&&EnchantRules.sealsForNext(TacticalEnchanting.id(e),seals(e),e.getMaxLevel())>0?54:4);
                if(Ui.inside(mx,my,leftPos+LIST_X,ry,nameRight-leftPos-LIST_X,ROW-2)){
                    var needs=new StringBuilder();for(int l=1;l<=e.getMaxLevel();l++)needs.append(l>1?", ":"").append(EnchantRules.fobsFor(l,e.getMaxLevel()));
                    g.renderTooltip(font,font.split(Ui.t("enchant.row.tip",Component.translatable(e.getDescriptionId()),EnchantRules.roman(e.getMaxLevel()),needs.toString()),200),mx,my);
                    break;
                }
            }
        }
    }
}
