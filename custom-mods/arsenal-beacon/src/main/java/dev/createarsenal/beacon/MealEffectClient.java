package dev.createarsenal.beacon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.client.extensions.common.IClientMobEffectExtensions;

/** Inventory text of the meal effects: what the bonus is worth right now, and the field time (or "frozen" at home). */
final class MealEffectClient implements IClientMobEffectExtensions {
    /** The effect is a supplier: Forge asks for this extension from inside the MobEffect constructor, before the subclass fields exist. */
    private final java.util.function.Supplier<MealRules.Effect> source;private final boolean home;
    MealEffectClient(java.util.function.Supplier<MealRules.Effect> source,boolean home){this.source=source;this.home=home;}
    private static final String[] ROMAN={"I","II","III"};
    /** Draws one line, shrunk (down to 70%) instead of cut off when it is wider than the box. */
    private static void line(GuiGraphics g,net.minecraft.client.gui.Font font,Component text,int x,int y,int max,int color,boolean shadow){
        int w=font.width(text);float scale=w<=max?1f:Math.max(.7f,max/(float)w);
        g.pose().pushPose();g.pose().translate(x,y+(scale<1f?(1-scale)*4f:0),0);g.pose().scale(scale,scale,1f);
        g.drawString(font,w*scale>max?Ui.fit(font,text,(int)(max/scale)):text.getVisualOrderText(),0,0,color,shadow);g.pose().popPose();
    }
    @Override public boolean renderInventoryText(MobEffectInstance instance,EffectRenderingInventoryScreen<?> screen,GuiGraphics g,int x,int y,int blit){
        var mc=Minecraft.getInstance();var font=mc.font;int left=x+28,width=88;
        if(home){
            line(g,font,Component.translatable(instance.getEffect().getDescriptionId()),left,y+4,width,0xffe8c17b,true);
            int row=y+14;for(var part:font.split(Component.translatable(instance.getDescriptionId()+".description"),(int)(width/.8f)).stream().limit(3).toList()){
                g.pose().pushPose();g.pose().translate(left,row,0);g.pose().scale(.8f,.8f,1f);g.drawString(font,part,0,0,0xffb7c8d0,false);g.pose().popPose();row+=7;}
            return true;
        }
        var effect=source.get();var bonus=MealData.Bonus.ofAmplifier(effect,instance.getAmplifier());boolean enhanced=mc.player!=null&&mc.player.hasEffect(MealEffects.HOME.get());
        Component name=Component.translatable(instance.getEffect().getDescriptionId()).append(" "+ROMAN[bonus.strength()-1]);
        if(bonus.pair())name=name.copy().append(Component.literal(" \u00d72").withStyle(net.minecraft.ChatFormatting.GOLD));
        line(g,font,name,left,y+3,width,0xffffffff,true);
        line(g,font,Component.translatable("gui.arsenal_beacon.meal.bonus."+effect.id,bonus.amountText(enhanced)),left,y+12,width,enhanced?0xffe8c17b:0xff6cd4ef,false);
        Component time=instance.isInfiniteDuration()?Ui.t("meal.frozen"):Component.literal(net.minecraft.world.effect.MobEffectUtil.formatDuration(instance,1).getString());
        line(g,font,time,left,y+21,width,0xff8aa0ab,false);
        return true;
    }
}
