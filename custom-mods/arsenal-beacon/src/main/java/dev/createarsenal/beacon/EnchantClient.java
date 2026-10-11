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
    static final String[] ROMAN={"I","II","III","IV","V","VI","VII"};
    static void state(CompoundTag data){if(net.minecraft.client.Minecraft.getInstance().screen instanceof EnchantScreen s)s.update(data);}

    static final class EnchantScreen extends AbstractContainerScreen<TacticalEnchanting.EnchantMenu> {
        static final int W=384,H=236,ROW=22,LIST_X=184;
        private CompoundTag data;private ItemStack shown=ItemStack.EMPTY;private int scroll;
        private final List<Enchantment> rows=new ArrayList<>();
        EnchantScreen(TacticalEnchanting.EnchantMenu menu,Inventory inv,Component title){super(menu,inv,title);data=menu.data;imageWidth=W;imageHeight=H;}
        void update(CompoundTag next){data=next;rebuildWidgets();}
        private ItemStack gear(){return menu.getSlot(0).getItem();}
        private int fobs(){return data.getInt("fobs");}
        private int seals(Enchantment e){return data.getCompound("seals").getInt(TacticalEnchanting.id(e));}
        private int listTop(){return topPos+50;}
        private int visibleRows(){return (topPos+H-24-listTop())/ROW;}

        @Override protected void init(){
            super.init();clearWidgets();rows.clear();
            addRenderableWidget(new Ui.UiButton(leftPos+W-28,topPos+4,22,18,Component.literal("X"),Ui.Look.NORMAL,b->onClose()));
            // the table's next Mk
            int mk=data.getInt("mk");
            if(mk<EnchantRules.MAX_MK){
                var up=addRenderableWidget(new Ui.UiButton(leftPos+12,topPos+122,162,18,Ui.t("enchant.upgrade",mk+1,EnchantRules.DISCOUNT[mk+1]),Ui.Look.NORMAL,b->click(TacticalEnchanting.UPGRADE)));
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
                int bx=leftPos+W-12-e.getMaxLevel()*18;
                // a treasure enchant still locked (or its next level): one seal unlocks it
                if(treasure&&EnchantRules.sealsForNext(TacticalEnchanting.id(e),seals(e),e.getMaxLevel())>0){
                    int ti=EnchantRules.TREASURE.indexOf(TacticalEnchanting.id(e));boolean has=data.getBoolean("creative")||data.getInt("sealsCarried")>0;
                    var seal=addRenderableWidget(new Ui.UiButton(bx-50,y+2,46,16,Ui.t("enchant.unlock"),Ui.Look.NORMAL,b->click(TacticalEnchanting.UNLOCK+ti)));
                    seal.active=has;seal.warning=!has;seal.setTooltip(Tooltip.create(Ui.t(unlocked==0?"enchant.unlock.tip":"enchant.unlock.next.tip",unlocked+1)));
                }
                for(int level=1;level<=e.getMaxLevel();level++){
                    int lv=level;String problem=TacticalEnchanting.problem(gear,e,level,fobs(),seals(e));
                    int price=TacticalEnchanting.price(e,level,data.getInt("mk"));boolean afford=data.getBoolean("creative")||data.getInt("energy")>=price;
                    var b=addRenderableWidget(new Ui.UiButton(bx+(level-1)*18,y+2,16,16,Component.literal(ROMAN[Math.min(6,level-1)]),problem==null?Ui.Look.PRIMARY:Ui.Look.NORMAL,x->click(TacticalEnchanting.buyButton(index,lv))));
                    b.active=problem==null&&afford;b.warning=problem==null&&!afford;
                    var name=Component.translatable(e.getDescriptionId()).append(" "+ROMAN[Math.min(6,level-1)]);
                    b.setTooltip(Tooltip.create(problem==null?Ui.t(afford?"enchant.price":"enchant.price.short",name,price):Ui.t("enchant.locked."+problem,name,EnchantRules.fobsFor(level,e.getMaxLevel()),fobs())));
                }
            }
        }
        private void click(int id){if(minecraft!=null&&minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,id);}
        @Override protected void containerTick(){super.containerTick();if(!ItemStack.matches(gear(),shown)){scroll=0;rebuildWidgets();}}
        @Override public boolean mouseScrolled(double mx,double my,double delta){
            if(mx>=leftPos+LIST_X&&rows.size()>visibleRows()){scroll=Math.max(0,Math.min(rows.size()-visibleRows(),scroll-(int)Math.signum(delta)));rebuildWidgets();return true;}
            return super.mouseScrolled(mx,my,delta);
        }

        @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
            Ui.panel(g,leftPos,topPos,W,H);
            Ui.header(g,font,leftPos,topPos,W,title,Ui.t("enchant.subtitle"));
            int x=leftPos+12,w=162,y=topPos+34;
            // the gear
            Ui.text(g,font,Ui.t("enchant.gear"),x,y,Ui.BRASS,w);
            Ui.slot(g,leftPos+TacticalEnchanting.EnchantMenu.GEAR_X-1,topPos+TacticalEnchanting.EnchantMenu.GEAR_Y-1,ARCANE);
            var gear=gear();int tx=leftPos+TacticalEnchanting.EnchantMenu.GEAR_X+22,tw=x+w-tx;
            if(gear.isEmpty())Ui.wrap(g,font,Ui.t("enchant.gear.empty"),tx,topPos+TacticalEnchanting.EnchantMenu.GEAR_Y-2,tw,Ui.MUTED,3);
            else{
                Ui.text(g,font,gear.getHoverName(),tx,topPos+TacticalEnchanting.EnchantMenu.GEAR_Y-2,Ui.INK,tw);
                var have=EnchantmentHelper.getEnchantments(gear);int ey=topPos+TacticalEnchanting.EnchantMenu.GEAR_Y+8;
                Ui.text(g,font,have.isEmpty()?Ui.t("enchant.gear.none"):Ui.t("enchant.gear.has",have.size()),tx,ey,Ui.MUTED,tw);
            }
            for(int i=1;i<menu.slots.size();i++){var sl=menu.slots.get(i);Ui.slot(g,leftPos+sl.x-1,topPos+sl.y-1,Ui.EDGE);}
            // FOBs, the table, the seals
            y=topPos+86;
            Ui.text(g,font,Ui.t("enchant.fobs",fobs(),FobRules.MAX_FOBS),x,y,fobs()>0?FobClient.OLIVE:Ui.ORANGE,w);y+=11;
            Ui.text(g,font,Ui.t("enchant.fobs.how"),x,y,Ui.MUTED,w);y+=11;
            int mk=data.getInt("mk");
            Ui.text(g,font,mk==0?Ui.t("enchant.mk.none"):Ui.t("enchant.mk",mk,EnchantRules.DISCOUNT[mk]),x,y,Ui.INK,w);
            if(mk>=EnchantRules.MAX_MK)Ui.text(g,font,Ui.t("enchant.mk.max"),x,topPos+127,Ui.MUTED,w);
            Ui.text(g,font,data.getBoolean("creative")?Ui.t("enchant.energy.creative",data.getInt("sealsCarried")):Ui.t("enchant.energy",data.getInt("energy"),data.getInt("sealsCarried")),x,topPos+142-1,Ui.MUTED,w);
            // the list
            int lx=leftPos+LIST_X,lw=W-LIST_X-12;
            Ui.text(g,font,Ui.t("enchant.list"),lx,topPos+36,ARCANE,lw);
            if(gear.isEmpty()){Ui.wrap(g,font,Ui.t("enchant.list.empty"),lx,listTop(),lw,Ui.MUTED,6);}
            else if(rows.isEmpty())Ui.wrap(g,font,Ui.t("enchant.list.nothing"),lx,listTop(),lw,Ui.MUTED,4);
            int ry=listTop();
            for(int r=scroll;r<rows.size()&&r<scroll+visibleRows();r++,ry+=ROW){
                var e=rows.get(r);int bx=leftPos+W-12-e.getMaxLevel()*18;boolean treasure=TacticalEnchanting.treasure(e);
                int nameW=bx-lx-(treasure&&EnchantRules.sealsForNext(TacticalEnchanting.id(e),seals(e),e.getMaxLevel())>0?54:4);
                Ui.card(g,lx,ry,lw,ROW-2,treasure?0xffd4af37:ARCANE);
                Ui.text(g,font,Component.translatable(e.getDescriptionId()),lx+6,ry+3,Ui.INK,nameW);
                int have=EnchantmentHelper.getItemEnchantmentLevel(e,gear),top=EnchantRules.maxLevel(e.getMaxLevel(),fobs());
                Component sub=have>0?Ui.t("enchant.row.has",ROMAN[Math.min(6,have-1)]):treasure&&seals(e)==0?Ui.t("enchant.row.treasure"):Ui.t("enchant.row.top",ROMAN[Math.min(6,top-1)]);
                Ui.text(g,font,sub,lx+6,ry+12,Ui.MUTED,nameW);
            }
            if(rows.size()>visibleRows())Ui.text(g,font,Ui.t("enchant.scroll",scroll+1,Math.min(rows.size(),scroll+visibleRows()),rows.size()),lx,topPos+H-22,Ui.MUTED,lw);
            var message=data.getString("message");
            if(!message.isEmpty())Ui.text(g,font,Component.translatable("gui.arsenal_beacon."+message),lx,topPos+H-12,message.equals("enchant.result.done")||message.equals("enchant.result.upgraded")||message.equals("enchant.result.unlocked")?FobClient.OLIVE:Ui.ORANGE,lw);
        }
        @Override protected void renderLabels(GuiGraphics g,int mx,int my){}
        @Override public void render(GuiGraphics g,int mx,int my,float partial){renderBackground(g);super.render(g,mx,my,partial);renderTooltip(g,mx,my);}
    }
}
