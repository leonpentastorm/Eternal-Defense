package dev.createarsenal.beacon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Conditional stew surface and a live menu board on the chalkboard of the pot's upper block. */
final class KitchenRenderer implements BlockEntityRenderer<CookPot.PotEntity> {
    static final ResourceLocation STEW=new ResourceLocation(ArsenalBeacon.ID,"block/cook_pot_stew");
    KitchenRenderer(BlockEntityRendererProvider.Context context){}
    @Override public void render(CookPot.PotEntity pot,float partial,PoseStack pose,MultiBufferSource buffer,int light,int overlay){
        pose.pushPose();pose.translate(.5,0,.5);pose.mulPose(Axis.YP.rotationDegrees(-ArsenalStructures.angle(pot.getBlockState().getValue(ArsenalStructures.FACING))));pose.translate(-.5,0,-.5);
        if(!pot.empty())SupportClient.draw(pose,buffer,RenderType.cutout(),STEW,light,overlay);
        var lines=new ArrayList<Component>();lines.add(pot.empty()?Ui.t("kitchen.pot_empty"):pot.stew.name());if(!pot.empty()){for(var b:pot.stew.bonuses())lines.add(b.description(false));lines.add(Ui.t("kitchen.servings",pot.servings));}
        // chalkboard of the artist model: face z=13.25/16 (north), x 2.5..13.5, y 18.5..26.5 (in 1/16 blocks); text is drawn mirrored from its right edge
        pose.translate(.5,1.63,13.25/16-.004);pose.mulPose(Axis.YP.rotationDegrees(180));pose.scale(.0068f,-.0068f,.0068f);
        var font=Minecraft.getInstance().font;int y=0;
        for(var line:lines){var fitted=Ui.fit(font,line,96);font.drawInBatch(fitted,-font.width(fitted)/2f,y,0xffe8e2c8,false,pose.last().pose(),buffer,Font.DisplayMode.NORMAL,0,light);y+=9;}
        pose.popPose();
    }
    @Override public boolean shouldRenderOffScreen(CookPot.PotEntity pot){return true;}
}
