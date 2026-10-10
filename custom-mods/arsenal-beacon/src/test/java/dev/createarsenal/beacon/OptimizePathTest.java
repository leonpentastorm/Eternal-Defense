package dev.createarsenal.beacon;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** The pure parts of the Optimize Path (0.0.15): each faster check must give exactly the answer of the code it replaced. */
final class OptimizePathTest {
    /** The light/furniture grid of the base score answers "anything within 12 blocks?" exactly like a check against the whole list. */
    @Test void theAnchorGridFindsExactlyTheAnchorsTheListFound(){
        var r=new Random(15);
        for(int round=0;round<40;round++){
            int ox=r.nextInt(4000)-2000,oy=r.nextInt(300)-64,oz=r.nextInt(4000)-2000,spread=8+r.nextInt(60);
            var grid=new BaseScoring.Anchors();var list=new ArrayList<BlockPos>();
            int count=r.nextInt(5)==0?0:1+r.nextInt(120);
            for(int i=0;i<count;i++){var a=new BlockPos(ox+r.nextInt(spread)-spread/2,oy+r.nextInt(spread)-spread/2,oz+r.nextInt(spread)-spread/2);grid.add(a.asLong());list.add(a);}
            assertEquals(list.isEmpty(),grid.isEmpty());assertEquals(list.size(),grid.size());
            for(int q=0;q<400;q++){
                var p=new BlockPos(ox+r.nextInt(spread+30)-(spread+30)/2,oy+r.nextInt(spread+30)-(spread+30)/2,oz+r.nextInt(spread+30)-(spread+30)/2);
                assertEquals(list.stream().anyMatch(a->a.distSqr(p)<=144),grid.near(p),"round "+round+" at "+p);
            }
        }
    }
    /** Exactly 12 blocks away along an axis is near, 13 is not; a corner-to-corner distance is measured like BlockPos.distSqr, across cell borders and zero. */
    @Test void theAnchorGridEdgesMatchDistSqr(){
        var grid=new BaseScoring.Anchors();var a=new BlockPos(-1,0,-1);grid.add(a.asLong());
        assertTrue(grid.near(new BlockPos(11,0,-1)));assertFalse(grid.near(new BlockPos(12,0,-1)));
        assertTrue(grid.near(new BlockPos(-13,0,-1)));assertFalse(grid.near(new BlockPos(-14,0,-1)));
        assertTrue(grid.near(new BlockPos(7,7,-1)));   // 64+49+0=113
        assertFalse(grid.near(new BlockPos(7,7,6)));   // 64+49+49=162
        for(int x=-20;x<=20;x++)for(int y=-20;y<=20;y++){var p=new BlockPos(x,y,3);assertEquals(a.distSqr(p)<=144,grid.near(p),p.toString());}
    }
}
