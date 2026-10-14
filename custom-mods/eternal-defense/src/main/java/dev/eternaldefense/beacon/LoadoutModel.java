package dev.eternaldefense.beacon;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.Direction;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Every coordinate of the Loadout Station and Loadout Capsule art; README means docs/art/Eternal-Defense-Loadout/README.txt. */
final class LoadoutModel {
    private LoadoutModel(){}

    // README l.21-23: model pixels of the anchor block, 16 px a block, front -Z; other facings turn about (8, 8, 8) first
    static final double PX=16,CENTRE=8;
    // README l.26: the station's footprint, -16..32 on every axis (3 x 3 x 3, anchor in the middle)
    static final double MIN=-16,MAX=32;

    // README l.46: the clear cavity, empty in every pose
    static final double CAVITY_X0=1,CAVITY_X1=15,CAVITY_Y0=-13.5,CAVITY_Y1=21.5,CAVITY_Z0=-2,CAVITY_Z1=12;
    // README l.49: the standing point (player feet), facing north (yaw 180)
    static final double STAND_X=8,STAND_Y=-13.5,STAND_Z=5;
    // README l.51: the player hitbox it was checked with
    static final double HITBOX_WIDTH=9.6,HITBOX_HEIGHT=28.8;
    // README l.52: the bottom-centre cell's floor box (the anchor cell has none)
    static final double FLOOR_TOP=2.5;

    // README l.56-57: hinge axes; the right leaf turns +a, the left -a
    static final double HINGE_RIGHT_X=-5,HINGE_LEFT_X=21,HINGE_Z=-11;
    // README l.58: door angle, 0 shut ... 100 open (checked clear up to 105)
    static final float DOOR_OPEN=100f,DOOR_CHECKED=105f;
    // README l.63-64: the open right leaf reaches about half a block out front: the renderer's box grows 2 blocks
    static final double RENDER_INFLATE=2;

    // README l.67-68: the print ring rests at head height and travels down to the floor plate
    static final double RING_REST_Y0=15.5,RING_REST_Y1=18,RING_TRAVEL=-29;
    // README l.73: the arm pivots
    static final double[] ARM_A_PIVOT={4.5,28.25,5},ARM_B_PIVOT={11.5,28.25,5};
    // README l.75: arm tilt range
    static final float ARM_TILT=45f;

    // README l.81-85: the 80-tick cycle
    static final int CYCLE_TICKS=80,DOORS_SHUT_AT=16,PRINT_END=64,ARM_PERIOD=16,STEAM_AT=64;

    /** A point with the way something leaves it (unit vector, or zero). */
    record Point(String name,double x,double y,double z,double dx,double dy,double dz){}
    // README l.89-92: steam vents framing the doors and where they blow
    static final List<Point> VENTS=List.of(
        new Point("top_right",-7.75,24,-10.5,0,-1,0),new Point("top_left",23.75,24,-10.5,0,-1,0),
        new Point("bottom_right",-7.75,-13,-10.5,0,.7071,-.7071),new Point("bottom_left",23.75,-13,-10.5,0,.7071,-.7071));
    // README l.95-98: ring nozzle tips at rest (add the ring's dy), all pointing inward
    static final List<Point> NOZZLES=List.of(
        new Point("west",0,16.5,5,1,0,0),new Point("east",16,16.5,5,-1,0,0),new Point("north",8,16.5,-2,0,0,1),new Point("south",8,16.5,12,0,0,-1));
    // README l.99: the arms' emitter tips (they turn with the arm)
    static final double[] ARM_A_TIP={4.5,22.21,3.95},ARM_B_TIP={11.5,22.21,3.95};

    // README l.101-107: glowing elements
    static final Set<String> STATION_GLOW_PREFIXES=Set.of("Screen","Status_lamps","Lamp_","Beacon_L","Beacon_R","Ceiling_light_f","Ceiling_light_b");
    static final Set<String> RING_GLOW=Set.of("Print_front","Print_back","Print_L","Print_R");
    // README l.120: faces per model
    static final Map<String,Integer> FACES=Map.of("loadout_station",300,"loadout_station_door_right",62,"loadout_station_door_left",65,"loadout_station_ring",81,
        "loadout_station_arm_a",28,"loadout_station_arm_b",28,"loadout_station_assembled",564);

    // README l.125: the capsule, 1 x 1 x 2, anchor at the bottom, front north
    static final double CAPSULE_HEIGHT=32;
    // README l.137-139: the door slides +X into the hull
    static final double CAPSULE_DOOR_TRAVEL=4,CAPSULE_DOOR_X0=6,CAPSULE_DOOR_X1=10.5,CAPSULE_DOOR_Y0=8,CAPSULE_DOOR_Y1=21.5,CAPSULE_DOOR_Z0=3,CAPSULE_DOOR_Z1=4;
    // README l.152: the parachute hangs from the lifting eye
    static final double CHUTE_X=8,CHUTE_Y=30.5,CHUTE_Z=8,EYE_TOP=32;
    // README l.153-154: steam vents high on the flanks
    static final List<Point> CAPSULE_VENTS=List.of(new Point("west",1,19.25,9.5,-1,0,0),new Point("east",15,19.25,9.5,1,0,0));
    // README l.158: faces
    static final Map<String,Integer> CAPSULE_FACES=Map.of("loadout_capsule",148,"loadout_capsule_door",2,"loadout_capsule_item",150);

    // The 80-tick cycle (README l.81-85)
    /** Door angle at cycle tick t: closing 0-16, shut while printing, opening 64-80. */
    static float cycleDoor(float t){
        if(t<DOORS_SHUT_AT)return DOOR_OPEN*(1f-clamp(t/DOORS_SHUT_AT));
        if(t<PRINT_END)return 0f;
        return DOOR_OPEN*clamp((t-PRINT_END)/(CYCLE_TICKS-PRINT_END));
    }
    /** Ring offset (px) at cycle tick t: one sweep down 16-64, back up 64-80. */
    static float cycleRing(float t){
        if(t<DOORS_SHUT_AT)return 0f;
        if(t<PRINT_END)return (float)RING_TRAVEL*clamp((t-DOORS_SHUT_AT)/(PRINT_END-DOORS_SHUT_AT));
        return (float)RING_TRAVEL*(1f-clamp((t-PRINT_END)/(CYCLE_TICKS-PRINT_END)));
    }
    /** Arm A's tilt at cycle tick t (arm B is its negative): three swings 16-64. */
    static float cycleArm(float t){
        if(t<DOORS_SHUT_AT||t>=PRINT_END)return 0f;
        return ARM_TILT*(float)Math.sin(2*Math.PI*(t-DOORS_SHUT_AT)/ARM_PERIOD);
    }
    /** True while sparks fly from the nozzles. */
    static boolean sparking(float t){return t>=DOORS_SHUT_AT&&t<PRINT_END;}
    private static float clamp(float v){return Math.max(0f,Math.min(1f,v));}

    // Facing (README l.23)
    /** Quarter turns clockwise (seen from above) from north to facing, as the blockstates turn the model. */
    static int quarters(Direction facing){return switch(facing){case EAST->1;case SOUTH->2;case WEST->3;default->0;};}
    /** A model point (px, north-facing) turned to facing about the anchor centre; y is unchanged. */
    static double[] turn(double x,double y,double z,Direction facing){
        double dx=x-CENTRE,dz=z-CENTRE;
        for(int i=0;i<quarters(facing);i++){double t=dx;dx=-dz;dz=t;}
        return new double[]{CENTRE+dx,y,CENTRE+dz};
    }
    /** A direction (unit vector, north-facing) turned to facing. */
    static double[] turnVector(double dx,double dy,double dz,Direction facing){
        for(int i=0;i<quarters(facing);i++){double t=dx;dx=-dz;dz=t;}
        return new double[]{dx,dy,dz};
    }
    /** A model point as an offset in blocks from the anchor block's corner. */
    static double[] blocks(double x,double y,double z,Direction facing){var p=turn(x,y,z,facing);return new double[]{p[0]/PX,p[1]/PX,p[2]/PX};}
    /** The standing point as an offset in blocks from the anchor's corner (README l.50: (0.5, -0.84375, 0.3125) facing north). */
    static double[] standingPoint(Direction facing){return blocks(STAND_X,STAND_Y,STAND_Z,facing);}
    /** The yaw a player at the standing point faces: out of the machine (README l.49: north, yaw 180). */
    static float standingYaw(Direction facing){return facing.toYRot();}

    // The model's cubes, for collision and for the tests
    /** A cube in model pixels, as its axis-aligned bounds (a rotated cube's bounds include every corner). */
    record Cube(String name,double x0,double y0,double z0,double x1,double y1,double z1,int faces,boolean glow){}
    private static final Map<String,List<Cube>> CUBES=new HashMap<>();
    /** The cubes of one installed block model, read from the mod's own assets. */
    static synchronized List<Cube> cubes(String model){
        return CUBES.computeIfAbsent(model,m->{
            try(InputStream in=LoadoutModel.class.getResourceAsStream("/assets/"+EternalDefense.ID+"/models/block/"+m+".json")){
                if(in==null)throw new IllegalStateException("No model "+m);
                return cubes(JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject());
            }catch(java.io.IOException e){throw new IllegalStateException("Cannot read model "+m,e);}
        });
    }
    static List<Cube> cubes(JsonObject model){
        var out=new ArrayList<Cube>();
        for(var el:model.getAsJsonArray("elements")){
            var e=el.getAsJsonObject();var from=e.getAsJsonArray("from");var to=e.getAsJsonArray("to");
            double[] a={from.get(0).getAsDouble(),from.get(1).getAsDouble(),from.get(2).getAsDouble()},b={to.get(0).getAsDouble(),to.get(1).getAsDouble(),to.get(2).getAsDouble()};
            double[] lo={Math.min(a[0],b[0]),Math.min(a[1],b[1]),Math.min(a[2],b[2])},hi={Math.max(a[0],b[0]),Math.max(a[1],b[1]),Math.max(a[2],b[2])};
            if(e.has("rotation")){var r=e.getAsJsonObject("rotation");var bounds=rotatedBounds(lo,hi,r.get("axis").getAsString(),r.get("angle").getAsDouble(),r.getAsJsonArray("origin"));lo=bounds[0];hi=bounds[1];}
            boolean glow=e.has("forge_data")&&e.getAsJsonObject("forge_data").has("block_light")&&e.getAsJsonObject("forge_data").get("block_light").getAsInt()>=15;
            out.add(new Cube(e.has("name")?e.get("name").getAsString():"",lo[0],lo[1],lo[2],hi[0],hi[1],hi[2],e.getAsJsonObject("faces").size(),glow));
        }
        return List.copyOf(out);
    }
    private static double[][] rotatedBounds(double[] lo,double[] hi,String axis,double angle,JsonArray origin){
        double[] o={origin.get(0).getAsDouble(),origin.get(1).getAsDouble(),origin.get(2).getAsDouble()};
        double c=Math.cos(Math.toRadians(angle)),s=Math.sin(Math.toRadians(angle));
        double[] min={Double.MAX_VALUE,Double.MAX_VALUE,Double.MAX_VALUE},max={-Double.MAX_VALUE,-Double.MAX_VALUE,-Double.MAX_VALUE};
        for(int i=0;i<8;i++){
            double[] p={(i&1)==0?lo[0]:hi[0],(i&2)==0?lo[1]:hi[1],(i&4)==0?lo[2]:hi[2]};
            double x=p[0]-o[0],y=p[1]-o[1],z=p[2]-o[2];double[] q;
            switch(axis){
                case "x"->q=new double[]{x,y*c-z*s,y*s+z*c};
                case "y"->q=new double[]{x*c+z*s,y,-x*s+z*c};
                default->q=new double[]{x*c-y*s,x*s+y*c,z};
            }
            for(int k=0;k<3;k++){min[k]=Math.min(min[k],q[k]+o[k]);max[k]=Math.max(max[k],q[k]+o[k]);}
        }
        return new double[][]{min,max};
    }
    // Collision (README l.52-53 for the two middle cells; every other cell from the model with the doors shut)
    private static final Map<String,List<double[]>> CELL_BOXES=new HashMap<>();
    /**
     * The collision boxes of one cell of the station, in that cell's own pixels (0..16): the cell is (dx, dy, dz) from the
     * anchor (each -1..1) and the station faces facing. The anchor has none, the bottom-centre cell only the floor plate, the
     * top-centre cell is solid from the cavity ceiling; the rest are the body and the shut doors, clipped to the cell.
     */
    static synchronized List<double[]> cellBoxes(int dx,int dy,int dz,Direction facing){
        return CELL_BOXES.computeIfAbsent(dx+","+dy+","+dz+","+facing,k->{
            if(dx==0&&dz==0){
                if(dy==0)return List.of();
                if(dy==-1)return List.of(new double[]{0,0,0,PX,FLOOR_TOP,PX});
                return List.of(new double[]{0,CAVITY_Y1-PX,0,PX,PX,PX});
            }
            var out=new ArrayList<double[]>();double ox=dx*PX,oy=dy*PX,oz=dz*PX;
            for(String m:List.of("loadout_station","loadout_station_door_left","loadout_station_door_right"))for(var c:cubes(m)){
                var a=turn(c.x0(),c.y0(),c.z0(),facing);var b=turn(c.x1(),c.y1(),c.z1(),facing);
                double x0=Math.max(Math.min(a[0],b[0]),ox),x1=Math.min(Math.max(a[0],b[0]),ox+PX),y0=Math.max(c.y0(),oy),y1=Math.min(c.y1(),oy+PX),z0=Math.max(Math.min(a[2],b[2]),oz),z1=Math.min(Math.max(a[2],b[2]),oz+PX);
                if(x1-x0>1e-6&&y1-y0>1e-6&&z1-z0>1e-6)out.add(new double[]{x0-ox,y0-oy,z0-oz,x1-ox,y1-oy,z1-oz});
            }
            return List.copyOf(out);
        });
    }

    /** The capsule's collision in cell dy (0 the anchor, 1 the cell above), in that cell's pixels: the pod and its shut door. */
    static synchronized List<double[]> capsuleBoxes(int dy,Direction facing){
        return CELL_BOXES.computeIfAbsent("capsule,"+dy+","+facing,k->{
            var out=new ArrayList<double[]>();double oy=dy*PX;
            for(String m:List.of("loadout_capsule","loadout_capsule_door"))for(var c:cubes(m)){
                var a=turn(c.x0(),c.y0(),c.z0(),facing);var b=turn(c.x1(),c.y1(),c.z1(),facing);
                double y0=Math.max(c.y0(),oy),y1=Math.min(c.y1(),oy+PX);
                if(y1-y0>1e-6)out.add(new double[]{Math.min(a[0],b[0]),y0-oy,Math.min(a[2],b[2]),Math.max(a[0],b[0]),y1-oy,Math.max(a[2],b[2])});
            }
            return List.copyOf(out);
        });
    }

    /** A door leaf's cubes turned to angle a (degrees) about its hinge (README l.56-59). */
    static List<double[]> doorCorners(List<Cube> leaf,boolean right,float a){
        double hx=right?HINGE_RIGHT_X:HINGE_LEFT_X,rad=Math.toRadians(right?a:-a),c=Math.cos(rad),s=Math.sin(rad);var out=new ArrayList<double[]>();
        for(var cube:leaf)for(int i=0;i<4;i++){
            double x=(i&1)==0?cube.x0():cube.x1(),z=(i&2)==0?cube.z0():cube.z1(),dx=x-hx,dz=z-HINGE_Z;
            // Axis.YP turns +x toward -z
            out.add(new double[]{hx+dx*c+dz*s,HINGE_Z-dx*s+dz*c});
        }
        return out;
    }
}
