package dev.eternaldefense.beacon;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** The installed Loadout art against the numbers LoadoutModel takes from the pack's README. */
class LoadoutModelTest {
    static final List<String> STATION=List.of("loadout_station","loadout_station_door_left","loadout_station_door_right","loadout_station_ring","loadout_station_arm_a","loadout_station_arm_b","loadout_station_assembled");
    static final List<String> CAPSULE=List.of("loadout_capsule","loadout_capsule_door","loadout_capsule_item");

    @Test void everyCubeStaysInsideTheModelBounds(){
        for(String m:STATION)for(var c:LoadoutModel.cubes(m)){
            assertTrue(c.x0()>=-16&&c.y0()>=-16&&c.z0()>=-16&&c.x1()<=32&&c.y1()<=32&&c.z1()<=32,m+" cube "+c.name()+" leaves -16..32");
        }
        for(String m:CAPSULE)for(var c:LoadoutModel.cubes(m)){
            assertTrue(c.x0()>=0&&c.z0()>=0&&c.y0()>=0&&c.x1()<=16&&c.z1()<=16&&c.y1()<=LoadoutModel.CAPSULE_HEIGHT,m+" cube "+c.name()+" leaves the 1 x 1 x 2 capsule");
        }
    }

    @Test void faceCountsMatchTheReadme(){
        for(var e:LoadoutModel.FACES.entrySet())assertEquals(e.getValue(),LoadoutModel.cubes(e.getKey()).stream().mapToInt(LoadoutModel.Cube::faces).sum(),e.getKey());
        for(var e:LoadoutModel.CAPSULE_FACES.entrySet())assertEquals(e.getValue(),LoadoutModel.cubes(e.getKey()).stream().mapToInt(LoadoutModel.Cube::faces).sum(),e.getKey());
    }

    @Test void pointsLieInsideTheModels(){
        for(var p:LoadoutModel.VENTS)inside(p.x(),p.y(),p.z(),-16,32,p.name());
        for(var p:LoadoutModel.NOZZLES){inside(p.x(),p.y(),p.z(),-16,32,p.name());inside(p.x(),p.y()+LoadoutModel.RING_TRAVEL,p.z(),-16,32,p.name()+" at the floor");}
        for(double[] p:List.of(LoadoutModel.ARM_A_PIVOT,LoadoutModel.ARM_B_PIVOT,LoadoutModel.ARM_A_TIP,LoadoutModel.ARM_B_TIP))inside(p[0],p[1],p[2],-16,32,"arm point");
        inside(LoadoutModel.STAND_X,LoadoutModel.STAND_Y,LoadoutModel.STAND_Z,-16,32,"standing point");
        assertTrue(LoadoutModel.CHUTE_X>=0&&LoadoutModel.CHUTE_X<=16&&LoadoutModel.CHUTE_Z>=0&&LoadoutModel.CHUTE_Z<=16&&LoadoutModel.CHUTE_Y<=LoadoutModel.EYE_TOP&&LoadoutModel.EYE_TOP<=LoadoutModel.CAPSULE_HEIGHT);
        for(var p:LoadoutModel.CAPSULE_VENTS)assertTrue(p.x()>=0&&p.x()<=16&&p.y()>=0&&p.y()<=32&&p.z()>=0&&p.z()<=16,p.name());
    }
    private static void inside(double x,double y,double z,double lo,double hi,String what){assertTrue(x>=lo&&x<=hi&&y>=lo&&y<=hi&&z>=lo&&z<=hi,what+" ("+x+", "+y+", "+z+") is outside "+lo+".."+hi);}

    @Test void thePlayerFitsTheCavityAtTheStandingPoint(){
        double half=LoadoutModel.HITBOX_WIDTH/2;
        double margin=Math.min(Math.min(LoadoutModel.STAND_X-half-LoadoutModel.CAVITY_X0,LoadoutModel.CAVITY_X1-LoadoutModel.STAND_X-half),
            Math.min(LoadoutModel.STAND_Z-half-LoadoutModel.CAVITY_Z0,LoadoutModel.CAVITY_Z1-LoadoutModel.STAND_Z-half));
        assertEquals(2.2,margin,1e-9,"README l.51: 2.2 px clear on every side");
        assertTrue(LoadoutModel.STAND_Y>=LoadoutModel.CAVITY_Y0&&LoadoutModel.STAND_Y+LoadoutModel.HITBOX_HEIGHT<=LoadoutModel.CAVITY_Y1);
        assertEquals(LoadoutModel.STAND_Y,-16+LoadoutModel.FLOOR_TOP,1e-9,"the player stands on the floor box");
        var p=LoadoutModel.standingPoint(Direction.NORTH);
        assertArrayEquals(new double[]{.5,-.84375,.3125},p,1e-9,"README l.50");
    }

    @Test void theCavityHoldsNoCubeInAnyPose(){
        for(String m:List.of("loadout_station","loadout_station_door_left","loadout_station_door_right"))for(var c:LoadoutModel.cubes(m))assertFalse(intersectsCavity(c,0),m+" "+c.name());
        for(double dy=0;dy>=LoadoutModel.RING_TRAVEL;dy-=.5)for(var c:LoadoutModel.cubes("loadout_station_ring"))assertFalse(intersectsCavity(c,dy),"ring "+c.name()+" at "+dy);
        double lowest=LoadoutModel.cubes("loadout_station_ring").stream().mapToDouble(LoadoutModel.Cube::y0).min().orElseThrow()+LoadoutModel.RING_TRAVEL;
        assertEquals(-13.5,lowest,1e-9,"the ring stops on the floor plate");
    }
    private static boolean intersectsCavity(LoadoutModel.Cube c,double dy){
        return c.x1()>LoadoutModel.CAVITY_X0&&c.x0()<LoadoutModel.CAVITY_X1&&c.y1()+dy>LoadoutModel.CAVITY_Y0&&c.y0()+dy<LoadoutModel.CAVITY_Y1&&c.z1()>LoadoutModel.CAVITY_Z0&&c.z0()<LoadoutModel.CAVITY_Z1;
    }

    @Test void theDoorsSwingOutsideTheCavityAndInsideTheRenderBox(){
        double minZ=0;
        for(float a=0;a<=LoadoutModel.DOOR_CHECKED;a+=.5f)for(boolean right:new boolean[]{true,false}){
            for(var p:LoadoutModel.doorCorners(LoadoutModel.cubes(right?"loadout_station_door_right":"loadout_station_door_left"),right,a)){
                assertTrue(p[1]<LoadoutModel.CAVITY_Z0+1e-9,(right?"right":"left")+" leaf enters the cavity at "+a);
                double reach=16*LoadoutModel.RENDER_INFLATE;   // new AABB(anchor).inflate(2)
                assertTrue(p[0]>=-reach&&p[0]<=16+reach&&p[1]>=-reach,"leaf outside the render box at "+a);
                if(a==LoadoutModel.DOOR_OPEN&&right)minZ=Math.min(minZ,p[1]);
            }
        }
        assertEquals(-23.66,minZ,.05,"README l.63 and CHECKS.txt: the open right leaf reaches z -23.7");
    }

    @Test void theCapsuleDoorSlidesInsideTheHull(){
        var door=LoadoutModel.cubes("loadout_capsule_door").get(0);
        assertArrayEquals(new double[]{LoadoutModel.CAPSULE_DOOR_X0,LoadoutModel.CAPSULE_DOOR_Y0,LoadoutModel.CAPSULE_DOOR_Z0,LoadoutModel.CAPSULE_DOOR_X1,LoadoutModel.CAPSULE_DOOR_Y1,LoadoutModel.CAPSULE_DOOR_Z1},
            new double[]{door.x0(),door.y0(),door.z0(),door.x1(),door.y1(),door.z1()},1e-9);
        assertTrue(door.x1()+LoadoutModel.CAPSULE_DOOR_TRAVEL<=16&&door.x0()>=0,"README l.139: the open door stays inside the hull");
    }

    @Test void glowCubesAreTheOnesTheReadmeNames(){
        for(var c:LoadoutModel.cubes("loadout_station"))if(c.glow())assertTrue(LoadoutModel.STATION_GLOW_PREFIXES.stream().anyMatch(c.name()::startsWith),c.name());
        assertEquals(9,LoadoutModel.cubes("loadout_station").stream().filter(c->c.glow()&&c.name().startsWith("Lamp_")).count(),"the 9 coin lamps");
        for(var c:LoadoutModel.cubes("loadout_station_ring"))assertEquals(LoadoutModel.RING_GLOW.contains(c.name()),c.glow(),c.name());
        for(String arm:List.of("loadout_station_arm_a","loadout_station_arm_b"))assertTrue(LoadoutModel.cubes(arm).stream().anyMatch(c->c.glow()&&c.name().equals("Emitter")));
        for(String door:List.of("loadout_station_door_left","loadout_station_door_right"))assertTrue(LoadoutModel.cubes(door).stream().noneMatch(LoadoutModel.Cube::glow),"README l.107: doors have no glow");
    }

    @Test void theCycleFollowsTheReadmeTimeline(){
        assertEquals(LoadoutModel.DOOR_OPEN,LoadoutModel.cycleDoor(0),1e-6);
        assertEquals(0,LoadoutModel.cycleDoor(16),1e-6);assertEquals(0,LoadoutModel.cycleDoor(40),1e-6);
        assertEquals(LoadoutModel.DOOR_OPEN,LoadoutModel.cycleDoor(80),1e-6);
        assertEquals(0,LoadoutModel.cycleRing(16),1e-6);assertEquals(LoadoutModel.RING_TRAVEL,LoadoutModel.cycleRing(64),1e-6);assertEquals(0,LoadoutModel.cycleRing(80),1e-6);
        assertEquals(0,LoadoutModel.cycleArm(16),1e-4);assertEquals(LoadoutModel.ARM_TILT,LoadoutModel.cycleArm(20),1e-4);
        int swings=0;for(int t=16;t<64;t++)if(LoadoutModel.cycleArm(t)<=0&&LoadoutModel.cycleArm(t+1)>0)swings++;
        assertEquals(3,swings,"three swings 16-64");
    }

    @Test void nobodyStandingThereTouchesCollisionInAnyFacing(){
        for(var facing:new Direction[]{Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST}){
            var p=LoadoutModel.standingPoint(facing);double half=LoadoutModel.HITBOX_WIDTH/32;
            double[] box={p[0]-half,p[1],p[2]-half,p[0]+half,p[1]+LoadoutModel.HITBOX_HEIGHT/16,p[2]+half};
            boolean onFloor=false;
            for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)for(int dz=-1;dz<=1;dz++)for(var b:LoadoutModel.cellBoxes(dx,dy,dz,facing)){
                double x0=dx+b[0]/16,y0=dy+b[1]/16,z0=dz+b[2]/16,x1=dx+b[3]/16,y1=dy+b[4]/16,z1=dz+b[5]/16;
                boolean overlap=box[0]<x1-1e-9&&box[3]>x0+1e-9&&box[1]<y1-1e-9&&box[4]>y0+1e-9&&box[2]<z1-1e-9&&box[5]>z0+1e-9;
                assertFalse(overlap,facing+": the player touches cell "+dx+","+dy+","+dz);
                if(Math.abs(y1-box[1])<1e-9&&box[0]<x1&&box[3]>x0&&box[2]<z1&&box[5]>z0)onFloor=true;
            }
            assertTrue(onFloor,facing+": the player stands on the floor box");
            assertTrue(LoadoutModel.cellBoxes(0,0,0,facing).isEmpty(),"the anchor has no collision");
        }
    }

    @Test void theFrontCellsAreClosedByTheShutDoors(){
        for(var facing:new Direction[]{Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST}){
            var front=LoadoutModel.turnVector(0,0,-1,facing);int fx=(int)Math.round(front[0]),fz=(int)Math.round(front[2]);
            assertFalse(LoadoutModel.cellBoxes(fx,0,fz,facing).isEmpty(),facing+": the shut doors make the front cell solid");
        }
    }
}
