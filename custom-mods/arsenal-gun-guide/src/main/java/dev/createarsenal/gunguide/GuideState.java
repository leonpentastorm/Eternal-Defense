package dev.createarsenal.gunguide;

/** Five seconds of visible help per login, followed by a short collapse animation. */
public final class GuideState {
    public enum Mode { INTRO, ICON, OPEN, HIDDEN }
    private Mode mode=Mode.ICON;
    private boolean seen;
    private long elapsed,last=-1;
    public void update(boolean held,boolean visible,long now){
        long delta=last<0?0:Math.max(0,Math.min(1000,now-last));last=now;
        if(!held||!visible)return;
        if(!seen){seen=true;mode=Mode.INTRO;elapsed=0;return;}
        if(mode==Mode.INTRO){elapsed+=delta;if(elapsed>=5350)mode=Mode.ICON;}
    }
    public void press(){mode=mode==Mode.OPEN||mode==Mode.INTRO?Mode.HIDDEN:Mode.OPEN;seen=true;}
    public Mode mode(){return mode;}
    public double expansion(){if(mode==Mode.OPEN)return 1;if(mode!=Mode.INTRO)return 0;if(elapsed<=5000)return 1;double t=Math.min(1,(elapsed-5000)/350.0);return 1-t*t*(3-2*t);}
    public void reset(){mode=Mode.ICON;seen=false;elapsed=0;last=-1;}
}
