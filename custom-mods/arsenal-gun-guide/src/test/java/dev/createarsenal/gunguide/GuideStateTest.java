package dev.createarsenal.gunguide;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GuideStateTest {
    private void advance(GuideState s,long from,long to){for(long t=from+50;t<=to;t+=50)s.update(true,true,t);}
    @Test void firstDrawHasFiveSecondsOfFullHelpThenAnimatedCollapse(){var s=new GuideState();s.update(true,true,0);advance(s,0,5000);assertEquals(1,s.expansion());s.update(true,true,5175);assertEquals(.5,s.expansion(),.001);s.update(true,true,5350);assertEquals(GuideState.Mode.ICON,s.mode());}
    @Test void menusAndPuttingTheGunAwayDoNotConsumeTheVisibleIntro(){var s=new GuideState();s.update(true,true,0);advance(s,0,1000);for(long t=1050;t<=10000;t+=50)s.update(true,false,t);s.update(false,true,10050);assertEquals(1,s.expansion());advance(s,10050,14050);assertEquals(1,s.expansion());s.update(true,true,14225);assertEquals(.5,s.expansion(),.001);}
    @Test void drawingAgainKeepsIconUntilUserOpensThenHidesIt(){var s=new GuideState();s.update(true,true,0);advance(s,0,5400);s.update(false,true,5500);s.update(true,true,5600);assertEquals(GuideState.Mode.ICON,s.mode());s.press();assertEquals(GuideState.Mode.OPEN,s.mode());advance(s,5600,12000);assertEquals(1,s.expansion());s.press();assertEquals(GuideState.Mode.HIDDEN,s.mode());s.update(false,true,12100);s.update(true,true,12200);assertEquals(GuideState.Mode.HIDDEN,s.mode());s.press();assertEquals(GuideState.Mode.OPEN,s.mode());}
    @Test void NewLoginAllowsIntroAgainWithoutChangingTheControlBinding(){var s=new GuideState();s.press();s.press();s.reset();s.update(true,true,20000);assertEquals(GuideState.Mode.INTRO,s.mode());assertEquals(1,s.expansion());}
}
