package dev.eternaldefense.beacon;

/** The idle animations' motions, as plain numbers so they can be tested alone. */
final class AnimationRules {
    private AnimationRules(){}
    static final double TAU=Math.PI*2;

    /** Fraction in [0, 1) from a seed: each block's own phase. */
    static double phase(long seed){long h=seed*0x9E3779B97F4A7C15L;h^=h>>>31;h*=0xBF58476D1CE4E5B9L;h^=h>>>29;return (h>>>11)*0x1.0p-53;}
    /** Another independent fraction for the same seed (salted). */
    static double phase(long seed,int salt){return phase(seed*31+salt*0x632BE59BD9B4E019L);}
    static double smooth(double x){x=Math.max(0,Math.min(1,x));return x*x*(3-2*x);}

    // The FOB flag: a cloth of four strips hinged one to the next
    static final int FLAG_STRIPS=4;
    /** Swing of each strip on its hinge: small at the mast, larger toward the free end. */
    static final double[] FLAG_SWING={6,9,12,15};
    /** The angle (about the vertical) of each strip relative to the one before */
    static double[] flag(double t,long seed){
        double p=phase(seed)*TAU,gust=.75+.25*Math.sin(t*TAU/7.3+p*1.7);
        var out=new double[FLAG_STRIPS];
        for(int j=0;j<FLAG_STRIPS;j++)out[j]=gust*FLAG_SWING[j]*Math.sin(t*TAU*.55+p-j*.9)+(j==FLAG_STRIPS-1?2.5*Math.sin(t*TAU*2.3+p*3):0);
        return out;
    }

    // Whips and antennas: a slow sway with a second, faster one on top
    /** {about x, about z} for a thin whip of this height in pixels: taller ones sway further. */
    static double[] sway(double t,long seed,double height){
        double p=phase(seed,7)*TAU,amp=Math.min(4,height*.35);
        return new double[]{amp*(.7*Math.sin(t*TAU*.31+p)+.3*Math.sin(t*TAU*1.13+p*2)),amp*(.7*Math.sin(t*TAU*.23+p*1.3)+.3*Math.sin(t*TAU*.97+p*.5))};
    }

    // Spinning and indexing parts
    /** A steady turn, one round every period seconds; negative periods turn the other way. */
    static double spin(double t,long seed,double period){double turns=t/period+phase(seed,3);turns-=Math.floor(turns);return turns*360;}
    /** A machine that turns a quarter at a time: every period seconds it turns 90 degrees over move seconds */
    static double index(double t,long seed,double period,double move,int direction){
        double cycles=t/period+phase(seed,5);long whole=(long)Math.floor(cycles);double inCycle=(cycles-whole)*period;
        double quarters=(whole%4)+smooth(inCycle/move);return ((direction*quarters*90)%360+360)%360;
    }
    /** Up and down by amp pixels, once every period seconds. */
    static double bob(double t,long seed,double period,double amp,int salt){return amp*Math.sin(t*TAU/period+phase(seed,salt)*TAU);}

    // The satellite dish: it picks a new direction now and then and turns to it
    /** Where the dish may point, clear of its own whip antenna: turn and tilt from its rest pose. */
    static final double AZIMUTH_MIN=-110,AZIMUTH_MAX=50,ELEVATION_MIN=-15,ELEVATION_MAX=20,DISH_STEP=9,DISH_MOVE=3.2;
    /** {azimuth, elevation}: holds a heading, then eases to the next one over DISH_MOVE seconds */
    static double[] dish(double t,long seed){
        double steps=t/DISH_STEP+phase(seed,11);long k=(long)Math.floor(steps);double f=smooth((steps-k)*DISH_STEP/DISH_MOVE);
        return new double[]{target(seed,k-1,0,AZIMUTH_MIN,AZIMUTH_MAX)+(target(seed,k,0,AZIMUTH_MIN,AZIMUTH_MAX)-target(seed,k-1,0,AZIMUTH_MIN,AZIMUTH_MAX))*f,
            target(seed,k-1,1,ELEVATION_MIN,ELEVATION_MAX)+(target(seed,k,1,ELEVATION_MIN,ELEVATION_MAX)-target(seed,k-1,1,ELEVATION_MIN,ELEVATION_MAX))*f};
    }
    static double target(long seed,long step,int axis,double min,double max){return min+(max-min)*phase(seed^(step*0x2545F4914F6CDD1DL),axis+13);}

    // Lights, flames, presses
    /** A warning light that flashes on for on of every period seconds. */
    static boolean flash(double t,long seed,double period,double on){double c=t/period+phase(seed,17);return c-Math.floor(c)<on/period;}
    /** {height, width}: a flame's flicker, as scales around its base */
    static double[] flicker(double t,long seed){
        double p=phase(seed,19)*TAU;
        double h=1+.12*Math.sin(t*TAU*1.7+p)+.07*Math.sin(t*TAU*4.3+p*2.1)+.04*Math.sin(t*TAU*9.1+p*.7);
        double w=1-.05*Math.sin(t*TAU*2.9+p*1.3)+.03*Math.sin(t*TAU*6.7+p);
        return new double[]{h,w};
    }
    /** The MRE Station's heat press: 0 up, 1 down on the pouches. */
    static final double PRESS_CYCLE=4,PRESS_TRAVEL=2.6;
    static double press(double t,long seed){
        double c=t/PRESS_CYCLE+phase(seed,23);double s=(c-Math.floor(c))*PRESS_CYCLE;
        if(s<1.6)return 0;if(s<2.0)return smooth((s-1.6)/.4);if(s<2.8)return 1;if(s<3.4)return 1-smooth((s-2.8)/.6);return 0;
    }
    /** A slow stir back and forth, in degrees about the pot's middle. */
    static double stir(double t,long seed){return 28*Math.sin(t*TAU/5+phase(seed,29)*TAU);}
    /** The Enchanting Table's book: {float in pixels, turn in degrees, how far the covers open beyond rest}. */
    static double[] book(double t,long seed){
        double p=phase(seed,31)*TAU;
        return new double[]{.6*Math.sin(t*TAU/3.2+p),12*Math.sin(t*TAU/11+p*.7),1+5*Math.sin(t*TAU/4.1+p*1.3)};
    }
    /** A spark's twinkle: {rise in pixels, scale}. */
    static double[] spark(double t,long seed,int i){
        double p=phase(seed,37+i)*TAU;
        return new double[]{.5*Math.sin(t*TAU/2.3+p),.85+.35*Math.sin(t*TAU/(.9+.13*i)+p*2)};
    }
    /** The crane hook on the cannon: {about x, about z}, a slow pendulum */
    static final double HOOK_SWING_X=10,HOOK_SWING_Z=6;
    static double[] hook(double t,long seed){double p=phase(seed,41)*TAU;return new double[]{HOOK_SWING_X*Math.sin(t*TAU/3.3+p),HOOK_SWING_Z*Math.sin(t*TAU/4.1+p*1.4)};}
    /** The beacon's rings rise and fall around the core, one after another, like a pulse running up it: pixels. */
    static final double RING_RISE=.7;
    static double ringRise(double t,long seed,int ring){return RING_RISE*Math.sin(t*TAU/3.2+phase(seed,43)*TAU-ring*.9);}
}
