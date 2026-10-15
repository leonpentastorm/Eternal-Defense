package dev.eternaldefense.beacon;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

/** Where a grown elite's head is for TaCZ's headshots */
public final class GiantHeads {
    private GiantHeads(){}
    /** Pure: a hit this high above the feet is in TaCZ's head band (a quarter block around the eyes), widened with the size */
    static boolean band(double hitY,float eyeHeight,float size){double half=.25*size;return hitY>eyeHeight-half&&hitY<eyeHeight+half;}
    /** TaCZ's answer for a mob of its usual size; for a grown one its configured head box or the eye band, both grown */
    public static boolean headshot(Entity target,Vec3 hit,boolean tacz){
        float s=Elites.size(target);if(s==1)return tacz;
        var rel=hit.subtract(target.position());var key=ForgeRegistries.ENTITY_TYPES.getKey(target.getType());
        var box=key==null?null:com.tacz.guns.config.util.HeadShotAABBConfigRead.getAABB(key);
        return box!=null?box.contains(rel.scale(1/s)):band(rel.y,target.getEyeHeight(),s);
    }
}
