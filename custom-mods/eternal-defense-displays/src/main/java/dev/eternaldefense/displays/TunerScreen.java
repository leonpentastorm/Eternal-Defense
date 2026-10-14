package dev.eternaldefense.displays;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/** The tuner's screen: a side panel, so the stand stays in view while its gun changes. */
final class TunerScreen extends Screen {
    private static final int W=172,PAD=6,HEAD=30,FOOT=50,LABEL=10;
    private static final int PANEL=0xd80a1218,EDGE=0xff3d5c6d,INK=0xffedf5f8,MUTED=0xff9fb3bc,BRASS=0xffc9a24b;
    /** Settings copied for the next stand, kept until the game closes. */
    static DisplayTuning.Tune clipboard;
    private final BlockPos pos;private DisplayTuning.Tune draft;private boolean dirty;private int sinceSent;
    private final Map<DisplayTuning.Setting,Slider> sliders=new EnumMap<>(DisplayTuning.Setting.class);
    private Button paste;private int left,top,bottom,moveY,turnY;

    private TunerScreen(BlockPos pos,DisplayTuning.Tune tune){super(Component.translatable("screen.eternal_defense_displays.tuner"));this.pos=pos;this.draft=tune;}
    static void open(BlockPos pos){var mc=Minecraft.getInstance();if(mc.level!=null&&mc.level.getBlockEntity(pos) instanceof DisplayRacks.DisplayEntity d)mc.setScreen(new TunerScreen(pos,d.tune()));}
    /** What a stand shows: the open screen's settings for its own stand. */
    static DisplayTuning.Tune shown(DisplayRacks.DisplayEntity e){return Minecraft.getInstance().screen instanceof TunerScreen s&&s.pos.equals(e.getBlockPos())?s.draft:e.tune();}
    private static Component t(String key,Object... args){return Component.translatable("screen.eternal_defense_displays.tuner."+key,args);}

    @Override protected void init(){
        sliders.clear();left=6;top=6;bottom=height-6;
        int rowH=Math.max(16,Math.min(24,(bottom-FOOT-top-HEAD-2*LABEL)/7)),size=Math.min(18,rowH-2),y=top+HEAD,sw=W-2*PAD-2*(size+2);
        for(var s:DisplayTuning.Setting.values()){
            if(s==DisplayTuning.Setting.RIGHT){moveY=y;y+=LABEL;}
            if(s==DisplayTuning.Setting.TILT){turnY=y;y+=LABEL;}
            addRenderableWidget(Button.builder(Component.literal("-"),b->set(draft.nudge(s,-1))).bounds(left+PAD,y,size,size).tooltip(Tooltip.create(t("less"))).build());
            sliders.put(s,addRenderableWidget(new Slider(left+PAD+size+2,y,sw,size,s)));
            addRenderableWidget(Button.builder(Component.literal("+"),b->set(draft.nudge(s,1))).bounds(left+W-PAD-size,y,size,size).tooltip(Tooltip.create(t("more"))).build());
            y+=rowH;
        }
        int bw=(W-2*PAD-6)/4,by=bottom-PAD-20;
        addRenderableWidget(Button.builder(t("reset"),b->set(DisplayTuning.Tune.DEFAULT)).bounds(left+PAD,by,bw,20).tooltip(Tooltip.create(t("reset.tip"))).build());
        addRenderableWidget(Button.builder(t("copy"),b->clipboard=draft).bounds(left+PAD+bw+2,by,bw,20).tooltip(Tooltip.create(t("copy.tip"))).build());
        paste=addRenderableWidget(Button.builder(t("paste"),b->{if(clipboard!=null)set(clipboard);}).bounds(left+PAD+2*(bw+2),by,bw,20).tooltip(Tooltip.create(t("paste.tip"))).build());
        addRenderableWidget(Button.builder(t("done"),b->onClose()).bounds(left+PAD+3*(bw+2),by,bw,20).build());
        paste.active=clipboard!=null;
    }
    private void set(DisplayTuning.Tune tune){draft=tune.clamped();dirty=true;sliders.values().forEach(Slider::sync);}
    static Component label(DisplayTuning.Setting s,float v){
        return switch(s){
            case SIZE->t("size",Math.round(v*100));
            case RIGHT,UP,OUT->t(s.key(),signed(v));
            default->t(s.key(),signed(Math.round(v)));
        };
    }
    static String signed(float v){
        String n=v==Math.rint(v)?Integer.toString(Math.abs((int)v)):String.format(Locale.ROOT,"%.2f",Math.abs(v)).replaceAll("0+$","");
        return v>0?"+"+n:v<0?"-"+n:n;
    }

    @Override public void tick(){
        var mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null||!(mc.level.getBlockEntity(pos) instanceof DisplayRacks.DisplayEntity d)||d.gun().isEmpty()
            ||mc.player.distanceToSqr(Vec3.atCenterOf(pos))>DisplayTuning.REACH*DisplayTuning.REACH||!DisplayTuner.holds(mc.player)){onClose();return;}
        paste.active=clipboard!=null;sinceSent++;
        if(dirty&&sinceSent>=2)send();
    }
    private void send(){DisplayNetwork.CHANNEL.sendToServer(new DisplayNetwork.Tune(pos,draft));dirty=false;sinceSent=0;}
    @Override public void removed(){
        if(dirty)send();
        var mc=Minecraft.getInstance();if(mc.level!=null&&mc.level.getBlockEntity(pos) instanceof DisplayRacks.DisplayEntity d)d.preview(draft);
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        g.fill(left,top,left+W,bottom,PANEL);g.renderOutline(left,top,W,bottom-top,EDGE);
        g.drawString(font,title,left+PAD,top+6,BRASS,false);
        var mc=Minecraft.getInstance();
        if(mc.level!=null&&mc.level.getBlockEntity(pos) instanceof DisplayRacks.DisplayEntity d){
            var name=font.plainSubstrByWidth(d.gun().getHoverName().getString(),W-2*PAD);g.drawString(font,name,left+PAD,top+17,MUTED,false);
        }
        g.drawString(font,t("section.move"),left+PAD,moveY+1,INK,false);g.drawString(font,t("section.rotate"),left+PAD,turnY+1,INK,false);
        var hint=font.split(t("hint"),W-2*PAD);int hy=bottom-PAD-22-hint.size()*9;
        for(int i=0;i<Math.min(2,hint.size());i++)g.drawString(font,hint.get(i),left+PAD,hy+i*9,MUTED,false);
        super.render(g,mx,my,partial);
    }

    private final class Slider extends AbstractSliderButton {
        final DisplayTuning.Setting setting;
        Slider(int x,int y,int w,int h,DisplayTuning.Setting setting){super(x,y,w,h,Component.empty(),setting.fraction(draft.get(setting)));this.setting=setting;updateMessage();}
        void sync(){value=setting.fraction(draft.get(setting));updateMessage();}
        @Override protected void updateMessage(){setMessage(label(setting,draft.get(setting)));}
        @Override protected void applyValue(){draft=draft.with(setting,setting.at(value));dirty=true;}
    }
}
