package dev.createarsenal.beacon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.locale.Language;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared look of every Create Arsenal screen: dark steel, brass trim and cyan instrument light.
 * Everything is drawn from rectangles so panels scale to any GUI size without stretched textures.
 * Colours follow ARTIST-BRIEF.md; orange is reserved for requirements and red for danger.
 */
final class Ui {
    private Ui(){}
    static final int NAVY=0xff111e28,DEEP=0xff0a1218,SLATE=0xff1c303d,SLATE_HI=0xff2a4555,EDGE=0xff3d5c6d,
        CYAN=0xff5ae2df,CYAN_DIM=0xff2f8d8c,CYAN_FILL=0xff17424a,MUTED=0xff9fb3bc,INK=0xffedf5f8,
        BRASS=0xffc9a24b,BRASS_DK=0xff7d6428,ORANGE=0xffffa36c,RED=0xffed7369,RED_FILL=0xff4a1f21,
        TRACK=0xff354c59,DISABLED=0xff74888f,SHADOW=0xff05090d;
    /** Age accents are always printed beside the written Age name; colour is never the only cue. */
    static final int[] AGE={0xffedf5f8,0xffe8c17b,0xffa7ca8b,0xff6cd4ef,0xffc29aff,0xffff86bd,0xffedf5f8,0xffe8b85c,0xffffa36c,0xffb7e3b2,0xffedf5f8,0xff6cd4ef};

    // ---- text ---------------------------------------------------------------------------------
    /** Translatable text in this mod's {@code gui.arsenal_beacon} namespace. */
    static Component t(String key,Object... args){return Component.translatable("gui.arsenal_beacon."+key,args);}
    /** Text that differs between the full pack and the standalone release. */
    static Component edition(String key,Object... args){return t(key+(BuildFlavor.STANDALONE?".standalone":".pack"),args);}
    static boolean has(String key){return I18n.exists("gui.arsenal_beacon."+key);}
    static String plain(String key,Object... args){return t(key,args).getString();}

    static FormattedCharSequence fit(Font font,Component text,int width){
        if(font.width(text)<=width)return text.getVisualOrderText();
        int dots=font.width("…");
        return FormattedCharSequence.composite(Language.getInstance().getVisualOrder(font.substrByWidth(text,Math.max(0,width-dots))),FormattedCharSequence.forward("…",Style.EMPTY));
    }
    static void text(GuiGraphics g,Font font,Component text,int x,int y,int color,int width){g.drawString(font,fit(font,text,width),x,y,color,false);}
    static void text(GuiGraphics g,Font font,Component text,int x,int y,int color){g.drawString(font,text.getVisualOrderText(),x,y,color,false);}
    static void right(GuiGraphics g,Font font,Component text,int xRight,int y,int color){g.drawString(font,text.getVisualOrderText(),xRight-font.width(text),y,color,false);}
    /** Wraps into at most {@code maxLines}; an overflowing last line ends in an ellipsis. Returns the next free y. */
    static int wrap(GuiGraphics g,Font font,Component text,int x,int y,int width,int color,int maxLines){
        List<FormattedCharSequence> rows=font.split(text,Math.max(8,width));int shown=Math.min(rows.size(),maxLines);
        for(int i=0;i<shown;i++){
            FormattedCharSequence row=rows.get(i);
            if(i==shown-1&&rows.size()>shown)row=FormattedCharSequence.composite(row,FormattedCharSequence.forward("…",Style.EMPTY));
            g.drawString(font,row,x,y,color,false);y+=font.lineHeight+2;
        }
        return y;
    }
    static int lines(Font font,Component text,int width){return font.split(text,Math.max(8,width)).size();}

    // ---- surfaces -----------------------------------------------------------------------------
    static void panel(GuiGraphics g,int x,int y,int w,int h){
        g.fill(x-1,y-1,x+w+1,y+h+1,SHADOW);
        g.fill(x,y,x+w,y+h,NAVY);
        g.fill(x,y,x+w,y+1,SLATE_HI);g.fill(x,y,x+1,y+h,SLATE_HI);
        g.fill(x,y+h-1,x+w,y+h,DEEP);g.fill(x+w-1,y,x+w,y+h,DEEP);
        for(int[] c:new int[][]{{x+2,y+2,1,1},{x+w-6,y+2,-1,1},{x+2,y+h-6,1,-1},{x+w-6,y+h-6,-1,-1}}){
            g.fill(c[0],c[1],c[0]+4,c[1]+4,BRASS_DK);g.fill(c[0],c[1],c[0]+3,c[1]+3,BRASS);
        }
    }
    /** Header band with title and the cyan instrument line. */
    static void header(GuiGraphics g,Font font,int x,int y,int w,Component title,Component subtitle){
        g.fill(x+1,y+1,x+w-1,y+27,SLATE);
        g.fill(x+1,y+25,x+w-1,y+27,CYAN);
        beaconGlyph(g,x+11,y+7);
        g.drawString(font,title.getVisualOrderText(),x+26,y+(subtitle==null?9:5),INK,false);
        if(subtitle!=null)g.drawString(font,subtitle.getVisualOrderText(),x+26,y+15,MUTED,false);
    }
    static void card(GuiGraphics g,int x,int y,int w,int h,int accent){
        g.fill(x,y,x+w,y+h,SLATE);g.fill(x,y,x+w,y+1,SLATE_HI);g.fill(x,y+h-1,x+w,y+h,DEEP);
        g.fill(x,y,x+3,y+h,accent);
    }
    static void inset(GuiGraphics g,int x,int y,int w,int h){g.fill(x,y,x+w,y+h,DEEP);g.fill(x,y,x+w,y+1,SHADOW);g.fill(x,y+h-1,x+w,y+h,SLATE_HI);}
    /** Frame for a borderless EditBox so text fields match the buttons. */
    static void field(GuiGraphics g,int x,int y,int w,int h,boolean focused){g.fill(x,y,x+w,y+h,focused?CYAN:EDGE);g.fill(x+1,y+1,x+w-1,y+h-1,DEEP);}
    static void rule(GuiGraphics g,int x,int y,int w){g.fill(x,y,x+w,y+1,EDGE);}
    static void beaconGlyph(GuiGraphics g,int x,int y){
        for(int i=0;i<5;i++){g.fill(x+4-i,y+i,x+5+i,y+i+1,i==4?CYAN_DIM:CYAN);g.fill(x+4-i,y+8-i,x+5+i,y+9-i,i==4?CYAN_DIM:CYAN);}
        g.fill(x+4,y+3,x+5,y+6,INK);g.fill(x+1,y+10,x+8,y+12,BRASS);
    }
    static void bar(GuiGraphics g,int x,int y,int w,int h,float fraction,int color){
        g.fill(x,y,x+w,y+h,TRACK);g.fill(x,y+h-1,x+w,y+h,DEEP);
        int fill=Math.round(w*Math.max(0,Math.min(1,fraction)));if(fill>0){g.fill(x,y,x+fill,y+h,color);g.fill(x,y,x+fill,y+1,0x55ffffff);}
        if(w>=60)for(int i=1;i<10;i++){int tx=x+w*i/10;g.fill(tx,y,tx+1,y+h,0x66000000);}
    }
    /** N pips with the first {@code value} lit; used for Ages and upgrade grades. */
    static void pips(GuiGraphics g,int x,int y,int value,int max,int color){
        for(int i=0;i<max;i++){int px=x+i*6;g.fill(px,y,px+5,y+5,i<value?color:TRACK);g.fill(px,y+4,px+5,y+5,i<value?0x66000000:DEEP);}
    }
    static int pipsWidth(int max){return max*6-1;}
    /** Small tag with an accent edge. Returns its width. */
    static int chip(GuiGraphics g,Font font,Component text,int x,int y,int accent,int maxWidth){
        int w=Math.min(maxWidth,font.width(text)+10);
        g.fill(x,y,x+w,y+12,DEEP);g.fill(x,y,x+3,y+12,accent);g.fill(x,y+11,x+w,y+12,accent&0x99ffffff);
        g.drawString(font,fit(font,text,w-7),x+6,y+2,INK,false);return w;
    }
    /** Hazard stripes for destructive confirmations and the attack banner. */
    static void hazard(GuiGraphics g,int x,int y,int w,int h,int a,int b){
        g.fill(x,y,x+w,y+h,a);
        for(int sx=x-h;sx<x+w;sx+=h){
            for(int row=0;row<h;row++){int x0=Math.max(x,sx+row),x1=Math.min(x+w,sx+row+Math.max(2,h/2));if(x1>x0)g.fill(x0,y+row,x1,y+row+1,b);}
        }
    }
    static boolean inside(double mx,double my,int x,int y,int w,int h){return mx>=x&&mx<x+w&&my>=y&&my<y+h;}

    // ---- widgets ------------------------------------------------------------------------------
    enum Look{NORMAL,PRIMARY,DANGER,TAB,TOGGLE,ROW}
    @FunctionalInterface interface Painter{void paint(GuiGraphics g,UiButton button,boolean hovered);}
    /**
     * One button class for every state in the brief: normal, hover, pressed, selected, disabled,
     * locked, warning and danger. Keyboard focus draws the same cyan ring as hover plus a corner mark.
     */
    static final class UiButton extends Button {
        Look look;boolean selected,warning,locked,on,label=true;int accent=CYAN;Painter painter;
        UiButton(int x,int y,int w,int h,Component label,Look look,OnPress press){super(x,y,w,h,label,press,DEFAULT_NARRATION);this.look=look;}
        UiButton accent(int color){accent=color;return this;}
        UiButton painter(Painter p){painter=p;return this;}
        /** For rows that paint their own content. */
        UiButton noLabel(){label=false;return this;}
        @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial){
            Minecraft mc=Minecraft.getInstance();Font font=mc.font;
            boolean hover=isHovered()&&active,pressed=hover&&mc.mouseHandler.isLeftPressed(),focus=isFocused();
            int x=getX(),y=getY(),w=getWidth(),h=getHeight();
            int fill,border,ink=active?INK:DISABLED;
            switch(look){
                case PRIMARY -> {fill=!active?NAVY:pressed?0xff113a3d:hover?0xff25807f:0xff1d5f60;border=!active?SLATE:CYAN;}
                case DANGER -> {fill=!active?NAVY:pressed?0xff2c1214:hover?0xff6a2a2d:RED_FILL;border=!active?SLATE:RED;if(active)ink=0xffffd9d5;}
                case TAB -> {fill=selected?SLATE:hover?NAVY:DEEP;border=selected?EDGE:SLATE;}
                case ROW -> {fill=selected?CYAN_FILL:!active?NAVY:pressed?DEEP:hover?SLATE_HI:SLATE;border=selected?accent:hover?EDGE:SLATE;}
                default -> {fill=!active?NAVY:pressed?DEEP:selected?CYAN_FILL:hover?SLATE_HI:SLATE;border=!active?SLATE:selected?accent:EDGE;}
            }
            if(warning&&active)border=ORANGE;
            g.fill(x,y,x+w,y+h,border);g.fill(x+1,y+1,x+w-1,y+h-1,fill);
            if(active&&!pressed&&look!=Look.ROW)g.fill(x+1,y+1,x+w-1,y+2,0x22ffffff);
            if(look==Look.TAB&&selected){g.fill(x+1,y,x+w-1,y+2,CYAN);}
            if(look==Look.TAB&&!selected&&active)ink=MUTED;
            if(look==Look.TAB&&selected)ink=INK;
            if(focus){g.fill(x-1,y-1,x+w+1,y,CYAN);g.fill(x-1,y+h,x+w+1,y+h+1,CYAN);g.fill(x-1,y,x,y+h,CYAN);g.fill(x+w,y,x+w+1,y+h,CYAN);}
            if(painter!=null)painter.paint(g,this,hover);
            if(look==Look.ROW){
                if(label)g.drawString(font,fit(font,getMessage(),w-16),x+7,y+(h-8)/2,active?(selected?INK:ink):DISABLED,false);
                return;
            }
            int textWidth=w-(look==Look.TOGGLE?46:10);
            if(look==Look.TOGGLE){
                int pw=30,px=x+w-pw-6,py=y+(h-12)/2;
                g.fill(px,py,px+pw,py+12,on?CYAN_DIM:TRACK);g.fill(px,py,px+pw,py+1,0x33ffffff);
                int kx=on?px+pw-14:px+2;g.fill(kx,py+2,kx+12,py+10,on?INK:MUTED);
                g.drawString(font,(on?t("on"):t("off")).getVisualOrderText(),px-font.width(on?t("on"):t("off"))-4,y+(h-8)/2,on?CYAN:MUTED,false);
                textWidth=w-pw-font.width(t("off"))-24;
            }
            int tx=look==Look.TOGGLE?x+8:x+w/2-Math.min(textWidth,font.width(getMessage()))/2;
            if(locked){g.fill(x+6,y+h/2-1,x+12,y+h/2+4,ink);g.fill(x+7,y+h/2-4,x+8,y+h/2-1,ink);g.fill(x+10,y+h/2-4,x+11,y+h/2-1,ink);g.fill(x+7,y+h/2-4,x+11,y+h/2-3,ink);tx=Math.max(tx,x+16);}
            g.drawString(font,fit(font,getMessage(),textWidth-(locked?10:0)),tx,y+(h-8)/2,ink,false);
        }
    }
}
