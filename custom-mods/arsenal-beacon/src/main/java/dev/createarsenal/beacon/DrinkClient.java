package dev.createarsenal.beacon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** 0.0.35 (owner): the client side of the drinks: the station screen's view, the station's tap icons, the Brew Pot's liquid and board, the Latte's band. */
public final class DrinkClient {
    private DrinkClient(){}
    static void view(DrinkStationMenu.View p){
        var mc=Minecraft.getInstance();
        if(mc.player!=null&&mc.player.containerMenu instanceof DrinkStationMenu menu&&menu.containerId==p.menu()&&menu.pos.equals(p.pos())){
            var data=p.data();
            if(data.contains("Catalogue")){menu.catalogue.clear();for(var t:data.getList("Catalogue",net.minecraft.nbt.Tag.TAG_STRING)){var id=ResourceLocation.tryParse(t.getAsString());if(id!=null)menu.catalogue.add(id);}data.remove("Catalogue");}
            menu.view=data;
        }
    }

    /**
     * The icon of each linked pot's drink on the station's taps: the owner's blank 3 x 3 px slots (model frame facing north), slot 0 the
     * viewer's left. Pots are taken in the station's own order, so tap N shows pot N.
     */
    static final class StationRenderer implements BlockEntityRenderer<DrinkStation.StationEntity> {
        /** Per tier: the slots' centres (x, y in model pixels) and the face's z. */
        static final float[][][] SLOTS={{},{{25.75f,17.125f}},{{12.25f,19.375f},{6.25f,19.375f}},{{22f,18.625f},{16f,18.625f},{10f,18.625f}}};
        static final float[] FACE_Z={0,4.1f,6.6f,6.1f};
        StationRenderer(BlockEntityRendererProvider.Context context){}
        @Override public void render(DrinkStation.StationEntity station,float partial,PoseStack pose,MultiBufferSource buffer,int light,int overlay){
            int mk=station.mk();if(mk<2||station.getLevel()==null)return;var slots=SLOTS[mk-1];
            var links=new ArrayList<>(station.links);links.sort(Comparator.comparingLong(net.minecraft.core.BlockPos::asLong));
            pose.pushPose();pose.translate(.5,0,.5);pose.mulPose(Axis.YP.rotationDegrees(-ArsenalStructures.angle(station.getBlockState().getValue(ArsenalStructures.FACING))));pose.translate(-.5,0,-.5);
            for(int i=0;i<slots.length&&i<links.size();i++){
                if(!(station.getLevel().getBlockEntity(links.get(i)) instanceof BrewPot.PotEntity pot)||pot.drink==null)continue;
                var effect=pot.drink.parts().get(0).mobEffect();if(effect==null)continue;
                var sprite=Minecraft.getInstance().getMobEffectTextures().get(effect);
                var vc=buffer.getBuffer(RenderType.entityCutoutNoCull(sprite.atlasLocation()));
                float cx=slots[i][0]/16f,cy=slots[i][1]/16f,z=FACE_Z[mk-1]/16f-.002f,h=1.5f/16f;
                var m=pose.last().pose();var n=pose.last().normal();int full=LightTexture.FULL_BRIGHT;
                // the face looks north (-z): from there +x is the viewer's left
                vc.vertex(m,cx+h,cy-h,z).color(255,255,255,255).uv(sprite.getU0(),sprite.getV1()).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(full).normal(n,0,0,-1).endVertex();
                vc.vertex(m,cx-h,cy-h,z).color(255,255,255,255).uv(sprite.getU1(),sprite.getV1()).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(full).normal(n,0,0,-1).endVertex();
                vc.vertex(m,cx-h,cy+h,z).color(255,255,255,255).uv(sprite.getU1(),sprite.getV0()).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(full).normal(n,0,0,-1).endVertex();
                vc.vertex(m,cx+h,cy+h,z).color(255,255,255,255).uv(sprite.getU0(),sprite.getV0()).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(full).normal(n,0,0,-1).endVertex();
            }
            pose.popPose();
        }
        @Override public boolean shouldRenderOffScreen(DrinkStation.StationEntity station){return true;}
    }
    /** The Brew Pot: the owner's liquid surface in the drink's colour when filled, and the live chalkboard (the Cook Pot's board area). */
    static final class PotRenderer implements BlockEntityRenderer<BrewPot.PotEntity> {
        static final ResourceLocation LIQUID=new ResourceLocation(ArsenalBeacon.ID,"block/drink_pot_liquid");
        PotRenderer(BlockEntityRendererProvider.Context context){}
        @Override public void render(BrewPot.PotEntity pot,float partial,PoseStack pose,MultiBufferSource buffer,int light,int overlay){
            pose.pushPose();pose.translate(.5,0,.5);pose.mulPose(Axis.YP.rotationDegrees(-ArsenalStructures.angle(pot.getBlockState().getValue(ArsenalStructures.FACING))));pose.translate(-.5,0,-.5);
            if(!pot.empty()){int c=pot.drink.color();SupportClient.drawTinted(pose,buffer,RenderType.translucent(),LIQUID,light,overlay,(c>>16&255)/255f,(c>>8&255)/255f,(c&255)/255f);}
            var lines=new ArrayList<Component>();
            if(pot.empty())lines.add(Ui.t("drinks.pot_empty_short"));else{lines.add(pot.drink.name());for(var part:pot.drink.parts())lines.add(part.describe());lines.add(Ui.t("drinks.minutes",DrinkRules.minutes(pot.drink.duration())));}
            pose.translate(.5,1.63,13.25/16-.004);pose.mulPose(Axis.YP.rotationDegrees(180));pose.scale(.0068f,-.0068f,.0068f);
            var font=Minecraft.getInstance().font;int y=0;
            for(var line:lines){var fitted=Ui.fit(font,line,96);font.drawInBatch(fitted,-font.width(fitted)/2f,y,0xffe8e2c8,false,pose.last().pose(),buffer,Font.DisplayMode.NORMAL,0,LightTexture.FULL_BRIGHT);y+=9;}
            pose.popPose();
        }
        @Override public boolean shouldRenderOffScreen(BrewPot.PotEntity pot){return true;}
    }
    /** The Cup Dispenser's stock: one stack of Paper Cups behind a gauge, and the two ways to use the block. */
    static final class CupScreen extends AbstractContainerScreen<CupDispenser.DispenserMenu> {
        CupScreen(CupDispenser.DispenserMenu menu,Inventory inv,Component title){super(menu,inv,title);imageWidth=BowlDispenserScreen.WIDTH;imageHeight=BowlDispenserScreen.HEIGHT;}
        @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
            Ui.panel(g,leftPos,topPos,imageWidth,imageHeight);Ui.header(g,font,leftPos,topPos,imageWidth,title,Ui.t("dispenser.subtitle.cup"));
            int count=menu.stock.getItem(0).getCount();boolean low=count<=8;int accent=count==0?Ui.RED:low?Ui.ORANGE:Ui.BRASS;
            Ui.card(g,leftPos+8,topPos+32,160,46,accent);
            Ui.slot(g,leftPos+CupDispenser.DispenserMenu.STOCK_X-1,topPos+CupDispenser.DispenserMenu.STOCK_Y-1,accent);
            if(menu.stock.getItem(0).isEmpty())Ui.ghost(g,new ItemStack(ArsenalBeacon.PAPER_CUP.get()),leftPos+CupDispenser.DispenserMenu.STOCK_X,topPos+CupDispenser.DispenserMenu.STOCK_Y);
            Ui.text(g,font,Ui.t("cups.label"),leftPos+42,topPos+40,Ui.INK,70);
            Ui.right(g,font,Ui.t("cups.count",count,DrinkRules.CUP_STOCK),leftPos+162,topPos+40,count==0?Ui.RED:low?Ui.ORANGE:Ui.CYAN);
            Ui.bar(g,leftPos+42,topPos+53,120,7,count/(float)DrinkRules.CUP_STOCK,accent);
            Ui.text(g,font,Ui.t(count==0?"cups.empty_short":"cups.note"),leftPos+42,topPos+65,count==0?Ui.ORANGE:Ui.MUTED,120);
            var use=Minecraft.getInstance().options.keyUse.getTranslatedKeyMessage();var sneak=Minecraft.getInstance().options.keyShift.getTranslatedKeyMessage();
            int y=Ui.keyHint(g,font,use,Ui.t("dispenser.use_cup"),leftPos+8,topPos+84,160);
            Ui.keyHint(g,font,sneak,Ui.t("dispenser.stock"),leftPos+8,y,160);
            Ui.text(g,font,playerInventoryTitle,leftPos+8,topPos+CupDispenser.DispenserMenu.INV_Y-12,Ui.MUTED,160);
            Ui.inventory(g,leftPos+7,topPos+CupDispenser.DispenserMenu.INV_Y-1);
        }
        @Override protected void renderLabels(GuiGraphics g,int mx,int my){}
        @Override public void render(GuiGraphics g,int mx,int my,float partial){
            renderBackground(g);super.render(g,mx,my,partial);renderTooltip(g,mx,my);
            if(hoveredSlot==null&&Ui.inside(mx,my,leftPos+8,topPos+32,160,46))g.renderTooltip(font,font.split(Ui.t("cups.tip"),180),mx,my);
            else if(hoveredSlot==null&&Ui.inside(mx,my,leftPos+8,topPos+84,160,13))g.renderTooltip(font,font.split(Ui.t("dispenser.tip.use_cup"),180),mx,my);
            else if(hoveredSlot==null&&Ui.inside(mx,my,leftPos+8,topPos+99,160,13))g.renderTooltip(font,font.split(Ui.t("dispenser.tip.stock"),180),mx,my);
            else if(hoveredSlot!=null&&hoveredSlot.container==menu.stock&&!hoveredSlot.hasItem())g.renderTooltip(font,Ui.t("cups.slot_tip"),mx,my);
        }
    }
    @Mod.EventBusSubscriber(modid=ArsenalBeacon.ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
    public static final class DrinkRegistration {
        @SubscribeEvent public static void setup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent e){
            e.enqueueWork(()->net.minecraft.client.gui.screens.MenuScreens.register(ArsenalBeacon.DRINK_STATION_MENU.get(),DrinkStationScreen::new));
            e.enqueueWork(()->net.minecraft.client.gui.screens.MenuScreens.register(ArsenalBeacon.CUP_DISPENSER_MENU.get(),CupScreen::new));
        }
        @SubscribeEvent public static void models(ModelEvent.RegisterAdditional e){e.register(PotRenderer.LIQUID);}
        @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e){
            e.registerBlockEntityRenderer(ArsenalBeacon.DRINK_STATION_ENTITY.get(),StationRenderer::new);e.registerBlockEntityRenderer(ArsenalBeacon.BREW_POT_ENTITY.get(),PotRenderer::new);
        }
        @SubscribeEvent public static void colors(RegisterColorHandlersEvent.Item e){e.register((stack,tint)->tint==0?Latte.color(stack):-1,ArsenalBeacon.LATTE.get());}
    }
}
