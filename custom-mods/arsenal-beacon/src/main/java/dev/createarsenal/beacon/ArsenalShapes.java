package dev.createarsenal.beacon;
import com.google.gson.JsonParser;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
/** Cuboid hitboxes follow the maintained models, including the space under station benches. */
final class ArsenalShapes {
    private static final java.util.Map<String,java.util.List<net.minecraft.world.phys.AABB>> BOXES=new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<String,VoxelShape> CACHE=new java.util.concurrent.ConcurrentHashMap<>();
    static VoxelShape model(String id){return CACHE.computeIfAbsent(id,k->{var shape=Shapes.empty();for(var box:boxes(k))shape=Shapes.joinUnoptimized(shape,Shapes.create(box),BooleanOp.OR);return shape.optimize();});}
    static java.util.List<net.minecraft.world.phys.AABB> boxes(String id){
        if(BOXES.containsKey(id))return BOXES.get(id);
        try(var stream=ArsenalShapes.class.getResourceAsStream("/assets/arsenal_beacon/models/block/"+id+".json")){
            var root=JsonParser.parseReader(new InputStreamReader(java.util.Objects.requireNonNull(stream),StandardCharsets.UTF_8)).getAsJsonObject();java.util.List<net.minecraft.world.phys.AABB> result=new java.util.ArrayList<>();
            if(!root.has("elements"))return boxes(root.get("parent").getAsString().substring("arsenal_beacon:block/".length()));
            for(var element:root.getAsJsonArray("elements")){
                var e=element.getAsJsonObject();var a=e.getAsJsonArray("from");var b=e.getAsJsonArray("to");double[] lo={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY},hi={Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
                for(int mask=0;mask<8;mask++){double[] p=new double[3];for(int i=0;i<3;i++)p[i]=(mask&(1<<i))==0?a.get(i).getAsDouble():b.get(i).getAsDouble();
                    if(e.has("rotation")){var r=e.getAsJsonObject("rotation");int axis="xyz".indexOf(r.get("axis").getAsString()),u=(axis+1)%3,v=(axis+2)%3;var origin=r.getAsJsonArray("origin");double angle=Math.toRadians(r.get("angle").getAsDouble()),c=Math.cos(angle),s=Math.sin(angle),du=p[u]-origin.get(u).getAsDouble(),dv=p[v]-origin.get(v).getAsDouble(),factor=r.has("rescale")&&r.get("rescale").getAsBoolean()?1/c:1;p[u]=origin.get(u).getAsDouble()+(du*c-dv*s)*factor;p[v]=origin.get(v).getAsDouble()+(du*s+dv*c)*factor;}
                    for(int i=0;i<3;i++){lo[i]=Math.min(lo[i],p[i]);hi[i]=Math.max(hi[i],p[i]);}}
                result.add(new net.minecraft.world.phys.AABB(lo[0]/16,lo[1]/16,lo[2]/16,hi[0]/16,hi[1]/16,hi[2]/16));
            }var boxes=java.util.List.copyOf(result);BOXES.put(id,boxes);return boxes;
        }catch(Exception ex){throw new IllegalStateException("Arsenal block model could not load: "+id,ex);}
    }
}
