package dev.createarsenal.gunguide;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModList;
import java.lang.reflect.Method;
import java.util.*;

@Mod.EventBusSubscriber(modid=GunGuide.ID,value=Dist.CLIENT)
public final class GuideClient {
    static final KeyMapping TOGGLE=new KeyMapping("key.arsenal_gun_guide.toggle",InputConstants.UNKNOWN.getValue(),"key.categories.arsenal_gun_guide");
    private static final GuideState STATE=new GuideState();
    private static boolean bindingChecked;
    private static boolean smokeExpanded,smokeCollapsed;
    private static final Map<Class<?>,Optional<Method>> GUN_API=new HashMap<>();
    @Mod.EventBusSubscriber(modid=GunGuide.ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
    public static final class Registration {
        @SubscribeEvent public static void keys(RegisterKeyMappingsEvent e){e.register(TOGGLE);}
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();
        initializeBinding(mc);
        boolean held=mc.player!=null&&gun(mc.player.getMainHandItem());
        STATE.update(held,mc.screen==null&&!mc.options.hideGui&&!mc.isPaused(),net.minecraft.Util.getMillis());
        while(TOGGLE.consumeClick())if(mc.player!=null&&mc.screen==null)STATE.press();
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){STATE.reset();}
    private static void initializeBinding(Minecraft mc){
        if(bindingChecked||mc.options==null||mc.options.keyMappings.length<20)return;bindingChecked=true;
        var marker=net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().resolve("arsenal-gun-guide-shortcut-016.applied");
        try{if(java.nio.file.Files.exists(marker))return;
            // The 0.15 shortcut started unbound. Install only this new action; preserve player edits.
            if(TOGGLE.isUnbound()){
                boolean found=false;
                for(var modifier:List.of(net.minecraftforge.client.settings.KeyModifier.NONE,net.minecraftforge.client.settings.KeyModifier.CONTROL,net.minecraftforge.client.settings.KeyModifier.ALT)){
                    for(String candidate:List.of("f6","f9","f10","f12","home","insert")){
                        var key=InputConstants.getKey("key.keyboard."+candidate);boolean occupied=Arrays.stream(mc.options.keyMappings).anyMatch(k->k!=TOGGLE&&!k.isUnbound()&&k.getKey().equals(key)&&k.getKeyModifier()==modifier);
                        if(!occupied){TOGGLE.setKeyModifierAndCode(modifier,key);found=true;break;}
                    }if(found)break;
                }
                if(found){KeyMapping.resetMapping();mc.options.save();}
            }
            java.nio.file.Files.writeString(marker,"Assigned only the new gun-guide shortcut once; later player edits are preserved.\n");
        }catch(java.io.IOException ex){com.mojang.logging.LogUtils.getLogger().warn("Gun Guide could not save shortcut preference",ex);}
    }
    private static boolean gun(ItemStack stack){
        if(stack.isEmpty())return false;var method=GUN_API.computeIfAbsent(stack.getItem().getClass(),type->{try{return Optional.of(type.getMethod("getGunId",ItemStack.class));}catch(NoSuchMethodException ex){return Optional.empty();}});
        try{return method.isPresent()&&method.get().invoke(stack.getItem(),stack)!=null;}catch(ReflectiveOperationException ex){return false;}
    }
    public static boolean jammed(ItemStack stack){return ModList.get().isLoaded("gundb")&&stack.hasTag()&&stack.getTag().getBoolean("Jammed");}
    // Palette shared with Defense Beacon (ARTIST-BRIEF.md). Orange marks conflicts, red marks the jam.
    private static final int PANEL=0xe6111e28,EDGE=0xff3d5c6d,CYAN=0xff5ae2df,CYAN_DIM=0xff2f8d8c,INK=0xffedf5f8,MUTED=0xff9fb3bc,
        ORANGE=0xffffa36c,RED=0xffed7369,CAP=0xff0b141b,DISABLED=0xff74888f;
    private static final int ROW=12;

    private static Component t(String key,Object... args){return Component.translatable("gui.arsenal_gun_guide."+key,args);}
    private static String keyName(KeyMapping mapping){return mapping.isUnbound()?t("unbound").getString():mapping.getTranslatedKeyMessage().getString();}
    /** Prefers the language file; keys of unrecognised tactical addons fall back to their own label. */
    private static Component action(GuideActions.Row row,boolean jammed){
        if(jammed&&row.name().equals("key.tacz.inspect.desc"))return t("action.clear_jam");
        String id=row.name().replaceFirst("^key\\.(tacz|tactical|lrtactical)\\.","").replaceFirst("\\.desc$","");
        String key="gui.arsenal_gun_guide.action."+id;
        return net.minecraft.client.resources.language.I18n.exists(key)?Component.translatable(key):Component.literal(row.label());
    }
    /** A key cap: dark inset with a lit edge. Returns its width so callers can reserve space. */
    private static int cap(GuiGraphics g,Font font,String label,int xRight,int y,int edge,int ink){
        int w=Math.max(15,font.width(label)+8),x=xRight-w;
        g.fill(x,y,x+w,y+ROW-1,edge);g.fill(x+1,y+1,x+w-1,y+ROW-2,CAP);g.fill(x+1,y+ROW-3,x+w-1,y+ROW-2,0xff1a2a34);
        g.drawString(font,label,x+(w-font.width(label))/2,y+2,ink,false);return w;
    }
    private static int capWidth(Font font,String label){return Math.max(15,font.width(label)+8);}
    private static void listGlyph(GuiGraphics g,int x,int y,int color){for(int line=0;line<3;line++){g.fill(x,y+line*4,x+2,y+line*4+2,color);g.fill(x+4,y+line*4,x+11,y+line*4+2,color);}}

    @SubscribeEvent public static void render(RenderGuiEvent.Post event){
        Minecraft mc=Minecraft.getInstance();if(mc.player==null||mc.screen!=null||mc.options.hideGui||!gun(mc.player.getMainHandItem()))return;
        draw(event.getGuiGraphics(),mc,jammed(mc.player.getMainHandItem()));
    }
    /** Everything after the "is a gun held" gate, so it can be exercised on its own. */
    static void draw(GuiGraphics g,Minecraft mc,boolean jammed){
        int w=g.guiWidth(),h=g.guiHeight();var font=mc.font;
        if(jammed&&GunGuide.JAM.get())jamAlert(g,font,mc,w,h);
        if(STATE.mode()==GuideState.Mode.HIDDEN||!GunGuide.CONTROLS.get())return;
        if(Boolean.getBoolean("arsenal.uiSmoke")){
            if(!smokeExpanded&&STATE.expansion()>.99){smokeExpanded=true;com.mojang.logging.LogUtils.getLogger().info("GUN_GUIDE_EXPANDED_RENDER_PASS");}
            if(smokeExpanded&&!smokeCollapsed&&STATE.expansion()<.001){smokeCollapsed=true;com.mojang.logging.LogUtils.getLogger().info("GUN_GUIDE_COLLAPSED_ICON_PASS");}
        }
        String shortcut=TOGGLE.isUnbound()?t("bind").getString():TOGGLE.getTranslatedKeyMessage().getString();
        int accent=jammed?RED:CYAN;
        int iconWidth=Math.max(40,capWidth(font,shortcut)+28),iconHeight=ROW+10;
        if(STATE.expansion()<=0.001){
            float iconScale=GunGuide.SCALE.get().floatValue();iconScale=Math.min(iconScale,(w-16f)/iconWidth);
            int ix=(int)(w/iconScale)-iconWidth-8,iy=Math.max(8,(int)((h/iconScale-iconHeight)/2));
            g.pose().pushPose();g.pose().scale(iconScale,iconScale,1);badge(g,font,ix,iy,iconWidth,iconHeight,shortcut,accent);g.pose().popPose();return;
        }
        var bindings=new ArrayList<GuideActions.Binding>();for(var k:mc.options.keyMappings)if(GuideActions.relevant(k.getName()))bindings.add(new GuideActions.Binding(k.getName(),k.getTranslatedKeyMessage().getString(),k.getKeyModifier().name()+":"+k.getKey().getName(),k.isUnbound(),Component.translatable(k.getName()).getString()));
        var rows=GuideActions.rows(bindings,jammed);if(rows.isEmpty())return;
        boolean anyConflict=rows.stream().anyMatch(GuideActions.Row::conflict);
        float scale=GunGuide.SCALE.get().floatValue();int maxRows=Math.max(4,(int)((h-96)/scale/ROW)-3);int columns=(rows.size()+maxRows-1)/maxRows;int count=(rows.size()+columns-1)/columns;
        int cell=0;for(var row:rows){String key=row.unbound()?t("unbound").getString():row.key();cell=Math.max(cell,font.width(action(row,jammed))+capWidth(font,key)+22);}cell=Math.min(230,Math.max(150,cell));
        // Shrink only when a tiny GUI window would otherwise clip the right-hand panel.
        scale=Math.min(scale,(w-16f)/(columns*cell+14));
        int width=columns*cell+14,height=26+count*ROW+(anyConflict?13:0)+4;
        double expansion=STATE.expansion();
        int animatedWidth=(int)Math.round(iconWidth+(width-iconWidth)*expansion),animatedHeight=(int)Math.round(iconHeight+(height-iconHeight)*expansion);
        int x=(int)(w/scale)-animatedWidth-8,y=Math.max(8,(int)((h/scale-animatedHeight)/2));
        g.pose().pushPose();g.pose().scale(scale,scale,1);
        if(expansion<=0.001){badge(g,font,x,y,animatedWidth,animatedHeight,shortcut,accent);g.pose().popPose();return;}
        g.fill(x-1,y-1,x+animatedWidth+1,y+animatedHeight+1,0xff05090d);
        g.fill(x,y,x+animatedWidth,y+animatedHeight,PANEL);g.fill(x,y,x+animatedWidth,y+2,accent);
        g.fill(x,y+2,x+1,y+animatedHeight,EDGE);g.fill(x+animatedWidth-1,y+2,x+animatedWidth,y+animatedHeight,0xff0a1218);
        g.enableScissor((int)(x*scale),(int)(y*scale),(int)((x+animatedWidth)*scale),(int)((y+animatedHeight)*scale));
        listGlyph(g,x+7,y+7,accent);
        Component title=jammed?t("title.jammed"):t("title");
        int hideCap=capWidth(font,shortcut);
        g.drawString(font,fit(font,title,width-hideCap-52),x+23,y+6,jammed?RED:CYAN,false);
        if(!TOGGLE.isUnbound()){g.drawString(font,t("hide").getVisualOrderText(),x+width-hideCap-10-font.width(t("hide")),y+6,MUTED,false);cap(g,font,shortcut,x+width-7,y+4,EDGE,INK);}
        g.fill(x+6,y+19,x+width-6,y+20,EDGE);
        for(int i=0;i<rows.size();i++){
            var row=rows.get(i);int rx=x+7+(i/count)*cell,ry=y+24+(i%count)*ROW;
            int ink=row.unbound()?DISABLED:row.conflict()?ORANGE:INK;
            String keyText=row.unbound()?t("unbound").getString():row.key();
            boolean jamKey=jammed&&row.name().equals("key.tacz.inspect.desc");
            int capW=capWidth(font,keyText);
            Component label=action(row,jammed);
            g.drawString(font,fit(font,label,cell-capW-24),rx+(row.conflict()?9:0),ry+2,jamKey?RED:ink,false);
            if(row.conflict()){g.fill(rx,ry+1,rx+7,ry+9,ORANGE);g.drawString(font,"!",rx+2,ry+1,CAP,false);}
            cap(g,font,keyText,rx+cell-9,ry,jamKey?RED:row.conflict()?ORANGE:row.unbound()?0xff2a3a44:EDGE,jamKey?RED:ink);
        }
        if(anyConflict)g.drawString(font,fit(font,t("conflict"),width-16),x+8,y+height-13,ORANGE,false);
        g.disableScissor();g.pose().popPose();
    }
    private static net.minecraft.util.FormattedCharSequence fit(Font font,Component text,int width){
        if(font.width(text)<=width)return text.getVisualOrderText();
        return net.minecraft.util.FormattedCharSequence.composite(net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(text,Math.max(0,width-font.width("\u2026")))),net.minecraft.util.FormattedCharSequence.forward("\u2026",net.minecraft.network.chat.Style.EMPTY));
    }
    /** Collapsed state: a quiet keycap badge that never covers the crosshair or the TaCZ ammo readout. */
    private static void badge(GuiGraphics g,Font font,int x,int y,int width,int height,String shortcut,int accent){
        g.fill(x-1,y-1,x+width+1,y+height+1,0xff05090d);g.fill(x,y,x+width,y+height,0xb0111e28);g.fill(x,y,x+width,y+2,accent);
        listGlyph(g,x+7,y+7,accent==RED?RED:CYAN_DIM);
        cap(g,font,shortcut,x+width-6,y+6,EDGE,INK);
    }
    private static void jamAlert(GuiGraphics g,Font font,Minecraft mc,int w,int h){
        String key=ControlLabel.of(mc,"key.tacz.inspect.desc");
        boolean pulse=(net.minecraft.Util.getMillis()/450)%2==0;
        Component label=t("jam");int x=w/2+14,y=h/2-7,boxW=font.width(label)*5/4+24;
        g.fill(x-1,y-1,x+boxW+1,y+15,0xff05090d);g.fill(x,y,x+boxW,y+14,pulse?0xf0701018:0xf04a0c12);
        g.fill(x,y,x+3,y+14,0xffffd23f);
        // warning triangle: shape and the word JAM both carry the meaning, not only the red.
        g.fill(x+8,y+9,x+16,y+11,0xffffd23f);g.fill(x+9,y+7,x+15,y+9,0xffffd23f);g.fill(x+10,y+5,x+14,y+7,0xffffd23f);g.fill(x+11,y+3,x+13,y+5,0xffffd23f);
        g.fill(x+11,y+6,x+13,y+8,0xff4a0c12);
        g.pose().pushPose();g.pose().translate(x+20,y+3,0);g.pose().scale(1.25f,1.25f,1);g.drawString(font,label.getVisualOrderText(),0,0,0xffffe0e0,true);g.pose().popPose();
        Component action=t("clear_jam",key);int actionW=font.width(action)+10;
        g.fill(x,y+16,x+actionW,y+28,0xd0111e28);g.fill(x,y+16,x+actionW,y+17,RED);g.drawString(font,action.getVisualOrderText(),x+5,y+19,0xffffc9c4,false);
    }
    private static final class ControlLabel{
        static String of(Minecraft mc,String name){for(var mapping:mc.options.keyMappings)if(mapping.getName().equals(name))return keyName(mapping);return t("unbound").getString();}
    }
}
