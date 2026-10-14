package dev.eternaldefense.displays;

import java.util.Locale;

/** How a displayed gun sits on its stand: size, position and angle, with their limits. */
public final class DisplayTuning {
    private DisplayTuning(){}
    /** Blocks between a player and the stand they tune. */
    public static final double REACH=8;

    /** One setting: its range, the step of its buttons and the step a slider snaps to. */
    public enum Setting {
        SIZE(.25f,3f,1f,.05f,.01f),RIGHT(-16,16,0,1,.25f),UP(-16,16,0,1,.25f),OUT(-16,16,0,1,.25f),
        TILT(-180,180,0,15,1),TURN(-180,180,0,15,1),ROLL(-180,180,0,15,1);
        public final float min,max,base,step,fine;
        Setting(float min,float max,float base,float step,float fine){this.min=min;this.max=max;this.base=base;this.step=step;this.fine=fine;}
        public String key(){return name().toLowerCase(Locale.ROOT);}
        /** Inside the range and on the fine grid; anything unreadable is the default. */
        public float clamp(float v){
            if(!Float.isFinite(v))return base;
            float snapped=Math.round(v/fine)*fine;
            return Math.max(min,Math.min(max,Math.round(snapped*10000f)/10000f));
        }
        /** Position of a value on a slider, 0 to 1. */
        public double fraction(float v){return (clamp(v)-min)/(double)(max-min);}
        public float at(double fraction){return clamp((float)(min+Math.max(0,Math.min(1,fraction))*(max-min)));}
        public boolean angle(){return this==TILT||this==TURN||this==ROLL;}
    }

    /** Size is a multiple of the stand's own size; moves are in pixels (a sixteenth of a block) as seen from the front; angles in degrees. */
    public record Tune(float size,float right,float up,float out,float tilt,float turn,float roll){
        public static final Tune DEFAULT=new Tune(1,0,0,0,0,0,0);
        public float get(Setting s){return switch(s){case SIZE->size;case RIGHT->right;case UP->up;case OUT->out;case TILT->tilt;case TURN->turn;case ROLL->roll;};}
        public Tune with(Setting s,float v){
            v=s.clamp(v);
            return switch(s){
                case SIZE->new Tune(v,right,up,out,tilt,turn,roll);case RIGHT->new Tune(size,v,up,out,tilt,turn,roll);
                case UP->new Tune(size,right,v,out,tilt,turn,roll);case OUT->new Tune(size,right,up,v,tilt,turn,roll);
                case TILT->new Tune(size,right,up,out,v,turn,roll);case TURN->new Tune(size,right,up,out,tilt,v,roll);
                case ROLL->new Tune(size,right,up,out,tilt,turn,v);
            };
        }
        /** One step up or down, as the buttons beside a slider do. */
        public Tune nudge(Setting s,int direction){return with(s,get(s)+direction*s.step);}
        public Tune clamped(){Tune t=DEFAULT;for(var s:Setting.values())t=t.with(s,get(s));return t;}
        public boolean isDefault(){return clamped().equals(DEFAULT);}
    }
}
