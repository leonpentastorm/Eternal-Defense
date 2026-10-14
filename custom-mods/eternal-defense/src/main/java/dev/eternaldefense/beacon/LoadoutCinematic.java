package dev.eternaldefense.beacon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The camera shots of the player being printed: entry from behind, the print from inside the cavity, the pull-back exit
 * ending in their own view. Client only; it follows the station's synced state and changes no setting: the camera is placed
 * each frame (LoadoutCameraMixin), the HUD and hand are held back by render events, so ending it is just stopping.
 */
@Mod.EventBusSubscriber(modid=EternalDefense.ID,value=Dist.CLIENT)
public final class LoadoutCinematic {
    private LoadoutCinematic(){}
    /** A camera placement for one frame. */
    record Shot(Vec3 pos,float yaw,float pitch,float fov){}
    /** Wide lens of the shots, and the ticks over which the last shot settles into the player's own view. */
    static final float WIDE=100f,PRINT_FOV=110f;static final int SETTLE=10,FLASH=8;
    /** The print skipped or stopped by a fault (its start time), so it does not come back. */
    private static long skipped=Long.MIN_VALUE;private static boolean faulted;private static long cueFor=Long.MIN_VALUE;
    /** The print during which sneak has been up once: only a fresh sneak skips (a quick print starts with sneak held). */
    private static long sneakFreeFor=Long.MIN_VALUE;
    private static Shot current;

    /** True while a shot is on screen. */
    static boolean running(){return current!=null;}
    /** For the hand mixin: true while a shot is on screen. */
    public static boolean hidingHands(){return current!=null;}
    private static LoadoutStation.StationEntity station(){
        var mc=Minecraft.getInstance();var be=LoadoutClient.held();
        if(be==null||mc.player==null||mc.level==null||faulted||!DefenseClientConfig.get(DefenseClientConfig.LOADOUT_CINEMATIC))return null;
        if(be.start==skipped||!mc.player.isAlive()||mc.player.isDeadOrDying())return null;
        float t=be.elapsed(mc.level.getGameTime(),0);
        return be.timeline().holding(t)?be:null;
    }
    /** For the camera mixin: this frame's camera {x, y, z, yaw, pitch}, or null for the normal view. */
    public static double[] frame(Entity camera,float partial){var s=shot(camera,partial);return s==null?null:new double[]{s.pos().x,s.pos().y,s.pos().z,s.yaw(),s.pitch()};}
    /** This frame's shot, or null for the normal view. */
    static Shot shot(Entity camera,float partial){
        current=null;
        try{
            var be=station();var mc=Minecraft.getInstance();if(be==null||camera!=mc.player)return null;
            current=compute(be,mc.player.getPosition(partial),mc.player.getEyePosition(partial),mc.player.getYRot(),mc.player.getXRot(),be.elapsed(mc.level.getGameTime(),partial));
            return current;
        }catch(RuntimeException ex){
            faulted=true;current=null;com.mojang.logging.LogUtils.getLogger().warn("[Loadout Station] camera shots stopped: {}",ex.toString());return null;
        }
    }
    private static Shot compute(LoadoutStation.StationEntity be,Vec3 feet,Vec3 eye,float playerYaw,float playerPitch,float t){
        var tl=be.timeline();var anchor=be.getBlockPos();var facing=be.facing();
        Vec3 doors=LoadoutStation.world(anchor,facing,LoadoutModel.CENTRE,0,LoadoutModel.HINGE_Z);
        if(t<tl.entry()){
            // Entry: from behind and above, looking past the player at the doors as they open
            var stand=new Vec3(be.stand[0],be.stand[1],be.stand[2]);var from=new Vec3(be.from[0],be.from[1],be.from[2]);
            var forward=stand.subtract(from).multiply(1,0,1);forward=forward.lengthSqr()<1e-4?Vec3.directionFromRotation(0,LoadoutModel.standingYaw(facing)+180f):forward.normalize();
            var want=eye.subtract(forward.scale(2.8)).add(0,1.1,0);
            var pos=clear(eye,want);var look=eye.add(forward.scale(1.2)).lerp(doors,.5);
            return aim(pos,look,WIDE);
        }
        float c=tl.cycleTick(t);
        if(c<LoadoutModel.STEAM_AT){
            // The print: high in a front corner of the clear cavity, looking down across the player
            var pos=LoadoutStation.world(anchor,facing,LoadoutModel.CAVITY_X0+.5,LoadoutModel.CAVITY_Y1-.5,LoadoutModel.CAVITY_Z0+.5);
            var look=LoadoutStation.world(anchor,facing,LoadoutModel.STAND_X,LoadoutModel.STAND_Y+LoadoutModel.HITBOX_HEIGHT*.6,LoadoutModel.STAND_Z);
            return aim(pos,look,PRINT_FOV);
        }
        // The exit: pulling back from the doors as they open and the player walks out, settling into their own view
        float span=Math.max(1f,tl.closeStart()-(tl.cycleStart()+LoadoutModel.STEAM_AT)),u=Math.min(1f,(c-LoadoutModel.STEAM_AT)/span);
        double back=36+44*Math.min(1f,u/.75f);
        var want=LoadoutStation.world(anchor,facing,LoadoutModel.CENTRE,20+6*u,LoadoutModel.HINGE_Z-back);
        var pos=clear(LoadoutStation.world(anchor,facing,LoadoutModel.CENTRE,8,LoadoutModel.HINGE_Z-16),want);
        var wide=aim(pos,feet.add(0,1.1,0),WIDE);
        float left=tl.closeStart()-t;if(left>=SETTLE)return wide;
        float s=1f-Math.max(0f,left)/SETTLE;s=s*s*(3-2*s);
        return new Shot(wide.pos().lerp(eye,s),wide.yaw()+LoadoutRules.wrap(playerYaw-wide.yaw())*s,wide.pitch()+(playerPitch-wide.pitch())*s,wide.fov()+(fov()-wide.fov())*s);
    }
    private static float fov(){return Minecraft.getInstance().options.fov().get().floatValue();}
    private static Shot aim(Vec3 pos,Vec3 look,float fov){
        var d=look.subtract(pos);double flat=Math.sqrt(d.x*d.x+d.z*d.z);
        return new Shot(pos,(float)Math.toDegrees(Math.atan2(-d.x,d.z)),(float)-Math.toDegrees(Math.atan2(d.y,flat)),fov);
    }
    /** The camera pulled in toward from until it is clear of solid blocks (as the game's own third-person camera). */
    private static Vec3 clear(Vec3 from,Vec3 want){
        var mc=Minecraft.getInstance();var hit=mc.level.clip(new ClipContext(from,want,ClipContext.Block.VISUAL,ClipContext.Fluid.NONE,mc.player));
        if(hit.getType()==HitResult.Type.MISS)return want;
        var d=want.subtract(from);double len=d.length();if(len<1e-4)return from;
        return from.add(d.scale(Math.max(0,(hit.getLocation().distanceTo(from)-.25)/len)));
    }

    // The frame: lens, no HUD, no hand, the flash at the swap
    @SubscribeEvent public static void cinematicFov(ViewportEvent.ComputeFov e){if(current!=null&&e.usedConfiguredFov())e.setFOV(current.fov());}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void cinematicHud(RenderGuiOverlayEvent.Pre e){if(current!=null)e.setCanceled(true);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void cinematicHand(RenderHandEvent e){if(current!=null)e.setCanceled(true);}
    @SubscribeEvent public static void cinematicFlash(RenderGuiEvent.Post e){
        var be=LoadoutClient.held();var mc=Minecraft.getInstance();if(current==null||be==null||mc.level==null)return;
        float since=be.timeline().cycleTick(be.elapsed(mc.level.getGameTime(),e.getPartialTick()))-LoadoutRules.SWAP_AT;
        if(since<0||since>FLASH)return;
        int alpha=(int)(230*(1f-since/FLASH));var g=e.getGuiGraphics();
        g.fill(0,0,g.guiWidth(),g.guiHeight(),(alpha<<24)|0xE8FFFF);
    }

    // Each client tick: the cue at the swap, skipping, and every way it must end
    @SubscribeEvent public static void cinematicTick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();var be=LoadoutClient.held();
        if(be==null||mc.player==null||mc.level==null){current=null;return;}
        if(station()==null){current=null;return;}
        if(!mc.options.keyShift.isDown())sneakFreeFor=be.start;else if(sneakFreeFor==be.start){skip(be);return;}
        float c=be.timeline().cycleTick(be.elapsed(mc.level.getGameTime(),0));
        if(c>=LoadoutRules.SWAP_AT&&cueFor!=be.start){
            cueFor=be.start;
            var p=mc.player.position();
            for(int i=0;i<24;i++)mc.level.addParticle(ParticleTypes.CLOUD,p.x+(mc.level.random.nextDouble()-.5)*.6,p.y+mc.level.random.nextDouble()*1.8,p.z+(mc.level.random.nextDouble()-.5)*.6,0,.04,0);
            String sound;try{sound=DefenseConfig.LOADOUT_STEAM_SOUND.get();}catch(IllegalStateException notLoaded){sound=DefenseConfig.LOADOUT_STEAM_SOUND.getDefault();}
            var cue=ResourceLocation.tryParse(sound);
            if(cue!=null)mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(cue),1.3f,.9f));
        }
    }
    /** Sneak or Esc: back to the player's own view at once (the print itself goes on). */
    static void skip(LoadoutStation.StationEntity be){skipped=be.start;current=null;}
    @SubscribeEvent public static void cinematicEscape(ScreenEvent.Opening e){
        var be=LoadoutClient.held();if(current!=null&&be!=null&&e.getNewScreen() instanceof PauseScreen){skip(be);e.setCanceled(true);}
    }
    @SubscribeEvent public static void cinematicLeave(ClientPlayerNetworkEvent.LoggingOut e){current=null;faulted=false;skipped=Long.MIN_VALUE;sneakFreeFor=Long.MIN_VALUE;}
    @SubscribeEvent public static void cinematicRespawn(ClientPlayerNetworkEvent.Clone e){current=null;}
}
