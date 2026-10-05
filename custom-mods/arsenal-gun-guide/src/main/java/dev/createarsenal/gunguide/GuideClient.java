package dev.createarsenal.gunguide;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.*;
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
    @SubscribeEvent public static void render(RenderGuiEvent.Post event){
        Minecraft mc=Minecraft.getInstance();if(mc.player==null||mc.screen!=null||mc.options.hideGui||!gun(mc.player.getMainHandItem()))return;
        boolean jammed=jammed(mc.player.getMainHandItem());var g=event.getGuiGraphics();int w=g.guiWidth(),h=g.guiHeight();
        if(jammed&&GunGuide.JAM.get()){
            String key="Unbound";for(var mapping:mc.options.keyMappings)if(mapping.getName().equals("key.tacz.inspect.desc"))key=mapping.isUnbound()?"Unbound":mapping.getTranslatedKeyMessage().getString();
            String label="JAM";int x=w/2+14,y=h/2-5;g.fill(x-3,y-2,x+mc.font.width(label)+3,y+11,0xc0200505);g.drawString(mc.font,label,x,y,0xffff4545,true);
            g.drawCenteredString(mc.font,"["+key+"] Clear jam",w/2,h/2+17,0xffff8585);
        }
        if(STATE.mode()==GuideState.Mode.HIDDEN||!GunGuide.CONTROLS.get())return;
        if(Boolean.getBoolean("arsenal.uiSmoke")){
            if(!smokeExpanded&&STATE.expansion()>.99){smokeExpanded=true;com.mojang.logging.LogUtils.getLogger().info("GUN_GUIDE_EXPANDED_RENDER_PASS");}
            if(smokeExpanded&&!smokeCollapsed&&STATE.expansion()<.001){smokeCollapsed=true;com.mojang.logging.LogUtils.getLogger().info("GUN_GUIDE_COLLAPSED_ICON_PASS");}
        }
        String shortcut=TOGGLE.isUnbound()?"Bind in Controls":TOGGLE.getTranslatedKeyMessage().getString();
        if(STATE.expansion()<=0.001){
            float iconScale=GunGuide.SCALE.get().floatValue();int iconWidth=Math.max(36,mc.font.width(shortcut)+26);iconScale=Math.min(iconScale,(w-16f)/iconWidth);
            int ix=(int)(w/iconScale)-iconWidth-8,iy=Math.max(8,(int)((h/iconScale-22)/2));
            g.pose().pushPose();g.pose().scale(iconScale,iconScale,1);g.fill(ix,iy,ix+iconWidth,iy+22,0x90121c23);g.fill(ix,iy,ix+iconWidth,iy+1,jammed?0xfffa5555:0xff52d6d6);
            for(int line=0;line<3;line++)g.fill(ix+5,iy+6+line*4,ix+14,iy+8+line*4,0xff8ff0ef);
            g.drawString(mc.font,shortcut,ix+20,iy+7,0xffd9ebef,false);g.pose().popPose();return;
        }
        var bindings=new ArrayList<GuideActions.Binding>();for(var k:mc.options.keyMappings)if(GuideActions.relevant(k.getName()))bindings.add(new GuideActions.Binding(k.getName(),k.getTranslatedKeyMessage().getString(),k.getKeyModifier().name()+":"+k.getKey().getName(),k.isUnbound(),Component.translatable(k.getName()).getString()));
        var rows=GuideActions.rows(bindings,jammed);if(rows.isEmpty())return;
        float scale=GunGuide.SCALE.get().floatValue();int maxRows=Math.max(4,(int)((h-88)/scale/11)-2);int columns=(rows.size()+maxRows-1)/maxRows;int count=(rows.size()+columns-1)/columns;
        int cell=0;for(var row:rows)cell=Math.max(cell,mc.font.width(row.label())+mc.font.width("["+row.key()+"]")+18);cell=Math.min(220,Math.max(145,cell));
        // Shrink only when a tiny GUI window would otherwise clip the right-hand panel.
        scale=Math.min(scale,(w-16f)/(columns*cell+12));int width=columns*cell+12,height=22+count*11;int x=(int)(w/scale)-width-8,y=Math.max(8,(int)((h/scale-height)/2));
        double expansion=STATE.expansion();int iconWidth=Math.max(36,mc.font.width(shortcut)+26),iconHeight=22;
        int animatedWidth=(int)Math.round(iconWidth+(width-iconWidth)*expansion),animatedHeight=(int)Math.round(iconHeight+(height-iconHeight)*expansion);
        x=(int)(w/scale)-animatedWidth-8;y=Math.max(8,(int)((h/scale-animatedHeight)/2));
        g.pose().pushPose();g.pose().scale(scale,scale,1);
        g.fill(x,y,x+animatedWidth,y+animatedHeight,0x90121c23);g.fill(x,y,x+animatedWidth,y+1,jammed?0xfffa5555:0xff52d6d6);
        if(expansion<=0.001){
            for(int line=0;line<3;line++)g.fill(x+5,y+6+line*4,x+14,y+8+line*4,0xff8ff0ef);
            g.drawString(mc.font,shortcut,x+20,y+7,0xffd9ebef,false);g.pose().popPose();return;
        }
        g.enableScissor((int)(x*scale),(int)(y*scale),(int)((x+animatedWidth)*scale),(int)((y+animatedHeight)*scale));
        g.drawString(mc.font,jammed?"WEAPON CONTROLS / JAMMED":"WEAPON CONTROLS",x+6,y+6,jammed?0xffff6565:0xff8ff0ef,false);
        for(int i=0;i<rows.size();i++){var row=rows.get(i);int rx=x+6+(i/count)*cell,ry=y+20+(i%count)*11;int color=row.unbound()?0xff8b939a:row.conflict()?0xffffb15d:0xffe2ebef;String key="["+row.key()+"]";String action=mc.font.plainSubstrByWidth(row.label(),cell-mc.font.width(key)-12);g.drawString(mc.font,action,rx,ry,color,false);g.drawString(mc.font,key,rx+cell-mc.font.width(key)-8,ry,row.name().equals("key.tacz.inspect.desc")&&jammed?0xffff6565:color,false);}
        g.disableScissor();g.pose().popPose();
    }
}
