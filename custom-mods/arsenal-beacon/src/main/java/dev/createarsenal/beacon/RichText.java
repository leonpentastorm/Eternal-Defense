package dev.createarsenal.beacon;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Tiny markup used by the field guide and confirmation screens so long copy stays in language files.
 * <pre>
 * # Heading        cyan sub-heading
 * - Bullet         marker plus indented, wrapped text; a short "Label: " at its start is coloured
 *                  ("Careful:" in red, any other label in the marker colour)
 * ~ Quiet note     muted text
 * (blank line)     paragraph gap
 * anything else    ordinary paragraph
 * </pre>
 */
final class RichText {
    record Row(FormattedCharSequence text,int x,int y,int color,int marker){}
    final List<Row> rows=new ArrayList<>();
    int height;
    /** A label is a few plain words before the first colon: "What it is", "Protein + Grain", "Step 1". */
    private static final Pattern LABEL=Pattern.compile("[A-Z0-9][^:.,;()!?]{0,22}");
    static final String WARNING_LABEL="Careful";

    static RichText layout(Font font,String raw,int width,int markerColor){
        RichText out=new RichText();int y=0;
        for(String line:raw.split("\n",-1)){
            if(line.isBlank()){y+=5;continue;}
            int indent=0,color=Ui.INK,marker=0;String body=line;
            if(line.startsWith("# ")){body=line.substring(2);color=Ui.CYAN;if(y>0)y+=3;}
            else if(line.startsWith("- ")){body=line.substring(2);indent=10;marker=body.startsWith(WARNING_LABEL+":")?Ui.RED:markerColor;}
            else if(line.startsWith("~ ")){body=line.substring(2);color=Ui.MUTED;}
            boolean first=true;
            for(FormattedCharSequence part:font.split(content(body,marker!=0,markerColor),Math.max(16,width-indent))){
                out.rows.add(new Row(part,indent,y,color,first?marker:0));first=false;y+=font.lineHeight+2;
            }
        }
        out.height=y;return out;
    }

    /** The bullet text, with its leading label coloured so a card can be scanned at a glance. */
    private static Component content(String body,boolean bullet,int markerColor){
        int colon=body.indexOf(": ");
        if(!bullet||colon<=0||!LABEL.matcher(body.substring(0,colon)).matches())return Component.literal(body);
        int color=(body.startsWith(WARNING_LABEL+":")?Ui.RED:markerColor)&0xFFFFFF;
        // Siblings inherit their parent's style, so the parent stays plain and only the label carries the colour.
        return Component.empty().append(Component.literal(body.substring(0,colon+1)).withStyle(s->s.withColor(TextColor.fromRgb(color)))).append(Component.literal(body.substring(colon+1)));
    }

    /** Draws rows that intersect [clipTop, clipBottom); the caller supplies the scissor. */
    void draw(GuiGraphics g,Font font,int x,int y,int scroll,int clipTop,int clipBottom){
        for(Row row:rows){
            int ry=y+row.y-scroll;if(ry+font.lineHeight<clipTop||ry>clipBottom)continue;
            if(row.marker!=0){g.fill(x+2,ry+2,x+6,ry+6,row.marker);g.fill(x+2,ry+5,x+6,ry+6,0x55000000);}
            g.drawString(font,row.text,x+row.x,ry,row.color,false);
        }
    }
}
