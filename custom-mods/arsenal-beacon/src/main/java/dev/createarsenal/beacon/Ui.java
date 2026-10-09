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

    record MaterialTip(net.minecraft.nbt.ListTag costs) implements net.minecraft.world.inventory.tooltip.TooltipComponent {}
    static final class MaterialRenderer implements net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent {
        final net.minecraft.nbt.ListTag costs;
        MaterialRenderer(MaterialTip tip){costs=tip.costs();}
        private Component name(int i){var row=costs.getCompound(i);var item=net.minecraft.world.item.ItemStack.of(row.getCompound("item"));return Component.literal(row.getInt("count")+" × "+(row.getString("label").isEmpty()?item.getHoverName().getString():row.getString("label"))+" ("+row.getInt("have")+")");}
        @Override public int getHeight(){return costs.size()*20;}
        private boolean short_(int i){var row=costs.getCompound(i);return row.getInt("have")<row.getInt("count");}
        @Override public int getWidth(Font font){int width=0;for(int i=0;i<costs.size();i++)width=Math.max(width,font.width(name(i))+22+(short_(i)?9:0));return width;}
        /** A short row is orange and also carries an alert mark, so the shortage never rests on colour alone. */
        @Override public void renderImage(Font font,int x,int y,GuiGraphics g){
            for(int i=0;i<costs.size();i++){
                var row=costs.getCompound(i);g.renderItem(net.minecraft.world.item.ItemStack.of(row.getCompound("item")),x,y+i*20);
                g.drawString(font,name(i),x+22,y+i*20+4,short_(i)?ORANGE:INK,false);
                if(short_(i))alert(g,x+22+font.width(name(i))+5,y+i*20+3,ORANGE);
            }
        }
    }
    static net.minecraft.nbt.ListTag singleCost(net.minecraft.world.item.ItemStack item,int count,int have){var rows=new net.minecraft.nbt.ListTag();var row=new net.minecraft.nbt.CompoundTag();row.put("item",item.copyWithCount(1).save(new net.minecraft.nbt.CompoundTag()));row.putInt("count",count);row.putInt("have",have);rows.add(row);return rows;}
    static void materialTooltip(GuiGraphics g,Font font,List<Component> heading,net.minecraft.nbt.ListTag costs,int mx,int my){
        g.renderTooltip(font,heading.isEmpty()?List.of(t("platform.materials")):heading,java.util.Optional.of(new MaterialTip(costs)),mx,my);
    }

    /**
     * A tooltip made of short coloured lines, status marks and rows of item icons (the foods a recipe accepts). It draws itself,
     * so it can order text and icons freely; the vanilla tooltip only places a component after its first line.
     */
    record Block(Component text,int color,int mark,List<net.minecraft.world.item.ItemStack> icons,int more){
        static Block line(Component text,int color){return new Block(text,color,0,List.of(),0);}
        /** {@code mark}: 1 check, 2 alert. */
        static Block marked(int mark,Component text,int color){return new Block(text,color,mark,List.of(),0);}
        static Block icons(Component label,int color,List<net.minecraft.world.item.ItemStack> icons,int more){return new Block(label,color,0,icons,more);}
    }
    record InfoTip(List<Block> blocks) implements net.minecraft.world.inventory.tooltip.TooltipComponent {}
    static final class InfoRenderer implements net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent {
        static final int WRAP=190;final List<Block> blocks;
        InfoRenderer(InfoTip tip){blocks=tip.blocks();}
        private List<FormattedCharSequence> rows(Font font,Block b){return b.text==null?List.of():font.split(b.text,WRAP-(b.mark!=0?10:0));}
        @Override public int getHeight(){var font=Minecraft.getInstance().font;int h=2;for(var b:blocks)h+=rows(font,b).size()*10+(b.icons.isEmpty()?0:18);return h;}
        @Override public int getWidth(Font font){
            int w=0;for(var b:blocks){for(var row:rows(font,b))w=Math.max(w,font.width(row)+(b.mark!=0?10:0));w=Math.max(w,b.icons.size()*17+(b.more>0?font.width("+"+b.more)+5:0));}return w;
        }
        @Override public void renderImage(Font font,int x,int y,GuiGraphics g){
            y+=2;
            for(var b:blocks){
                var rows=rows(font,b);
                for(int i=0;i<rows.size();i++){
                    if(i==0&&b.mark==1)check(g,x,y+1,b.color);else if(i==0&&b.mark==2)alert(g,x+2,y+1,b.color);
                    g.drawString(font,rows.get(i),x+(b.mark!=0?10:0),y,b.color,false);y+=10;
                }
                if(!b.icons.isEmpty()){
                    for(int i=0;i<b.icons.size();i++){
                        int ix=x+i*17;field(g,ix-1,y-1,17,17,false);g.pose().pushPose();g.pose().translate(ix+1,y+1,0);g.pose().scale(.75f,.75f,1);g.renderItem(b.icons.get(i),0,0);g.pose().popPose();
                    }
                    if(b.more>0)g.drawString(font,"+"+b.more,x+b.icons.size()*17+3,y+4,MUTED,false);
                    y+=18;
                }
            }
        }
    }

    // ---- pixel marks (shapes carry meaning as well as colour) ---------------------------------------------
    static void glyph(GuiGraphics g,int x,int y,String[] rows,int color){
        for(int r=0;r<rows.length;r++)for(int c=0;c<rows[r].length();c++)if(rows[r].charAt(c)=='#')g.fill(x+c,y+r,x+c+1,y+r+1,color);
    }
    private static final String[] CHECK={"....#","...##","#.##.","###..",".#..."},ALERT={"##","##","##","##","..","##"},CROSS={"#...#",".#.#.","..#..",".#.#.","#...#"},
        DEPOSIT={"...#...","...#...","#..#..#",".#.#.#.","..###..","...#...","#######"};
    static void check(GuiGraphics g,int x,int y,int color){glyph(g,x,y,CHECK,color);}
    static void alert(GuiGraphics g,int x,int y,int color){glyph(g,x,y,ALERT,color);}
    static void cross(GuiGraphics g,int x,int y,int color){glyph(g,x,y,CROSS,color);}
    static void deposit(GuiGraphics g,int x,int y,int color){glyph(g,x,y,DEPOSIT,color);}
    /** The item drawn dim, for "put this here" hints in an empty slot. */
    static void ghost(GuiGraphics g,net.minecraft.world.item.ItemStack stack,int x,int y){
        g.renderItem(stack,x,y);g.pose().pushPose();g.pose().translate(0,0,200);g.fill(x,y,x+16,y+16,0x990a1218);g.pose().popPose();
    }
    /** A glass tube filled from the bottom, with a mark every {@code ticks}th of its height and a bright surface line. */
    static void tank(GuiGraphics g,int x,int y,int w,int h,float fraction,int liquid,int ticks){
        g.fill(x,y,x+w,y+h,EDGE);g.fill(x+1,y+1,x+w-1,y+h-1,DEEP);
        int inner=h-2,fill=Math.round(inner*Math.max(0,Math.min(1,fraction)));
        if(fill>0){
            int top=y+1+inner-fill;g.fill(x+1,top,x+w-1,y+h-1,liquid);g.fill(x+1,top,x+w-1,top+1,0xaaffffff);g.fill(x+1,top+1,x+w-1,top+2,0x33ffffff);
            g.fill(x+w-3,top+1,x+w-1,y+h-1,0x22000000);
        }
        g.fill(x+1,y+1,x+2,y+h-1,0x22ffffff);
        for(int i=1;i<ticks;i++){int ty=y+1+inner*i/ticks;g.fill(x,ty,x+(i%2==0?4:2),ty+1,SLATE_HI);g.fill(x+w-(i%2==0?4:2),ty,x+w,ty+1,SLATE_HI);}
    }

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
    /** An inventory slot: coloured frame, recessed well. {@code x,y} is the frame's corner, the item sits one pixel inside. */
    static void slot(GuiGraphics g,int x,int y,int frame){g.fill(x,y,x+18,y+18,frame);inset(g,x+1,y+1,16,16);}
    /** The player's inventory, 27 + 9 slots; the first frame is at {@code x,y} and the hotbar sits 58 px below. */
    static void inventory(GuiGraphics g,int x,int y){
        for(int r=0;r<3;r++)for(int c=0;c<9;c++)slot(g,x+c*18,y+r*18,EDGE);
        for(int c=0;c<9;c++)slot(g,x+c*18,y+58,EDGE);
    }
    /** A key hint: the player's live key label in a small cap, then what it does. Returns the next free y. */
    static int keyHint(GuiGraphics g,Font font,Component key,Component what,int x,int y,int width){
        int w=chip(g,font,key,x,y,CYAN,Math.min(width/2,font.width(key)+10));text(g,font,what,x+w+6,y+2,MUTED,width-w-6);return y+15;
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
