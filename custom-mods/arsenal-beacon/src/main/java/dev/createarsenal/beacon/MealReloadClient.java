package dev.createarsenal.beacon;

import com.github.leopoko.tacz_attributes.api.ISpeedModifiable;
import com.github.leopoko.tacz_attributes.client.AnimationSpeedApplier;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.animation.ObjectAnimationRunner;
import com.tacz.guns.api.entity.IGunOperator;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Apply the backend's current rate, including 1, to reload clips only. No numbered tracks or weapon lists. */
@Mod.EventBusSubscriber(modid=ArsenalBeacon.ID,value=Dist.CLIENT)
public final class MealReloadClient {
    private MealReloadClient(){}
    private static final Set<ObjectAnimationRunner> previous=Collections.newSetFromMap(new IdentityHashMap<>());
    private static boolean reload(ObjectAnimationRunner runner){
        var clip=runner.getAnimation();if(clip==null||clip.name==null)return false;
        for(var prefix:AnimationSpeedApplier.RELOAD_ANIMATIONS)if(clip.name.startsWith(prefix))return true;return false;
    }
    private static void speed(ObjectAnimationRunner runner,float rate){if(runner instanceof ISpeedModifiable m)m.tacz_attributes$setSpeedMultiplier(rate);}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;
        var current=Collections.<ObjectAnimationRunner>newSetFromMap(new IdentityHashMap<>());
        var player=Minecraft.getInstance().player;
        if(player!=null){
            float rate=IGunOperator.fromLivingEntity(player).getSynReloadState().getStateType().isReloading()?(float)MealGunBackend.reloadRate(player):1;
            var display=TimelessAPI.getGunDisplay(player.getMainHandItem()).orElse(null);
            var state=display==null?null:display.getAnimationStateMachine();
            if(state!=null&&state.isInitialized()&&state.getContext()!=null&&state.getAnimationController()!=null){
                var tracks=state.getContext().getTrackArray();var controller=state.getAnimationController();
                if(tracks!=null)for(int i=0;i<tracks.getTrackLineSize();i++){
                    var line=tracks.getByIndex(i);if(line==null)continue;
                    for(var pointer:line){
                        var runner=controller.getAnimation(pointer);if(runner==null)continue;
                        if(reload(runner)){current.add(runner);speed(runner,rate);}
                        var next=runner.getTransitionTo();if(next!=null&&reload(next)){current.add(next);speed(next,rate);}
                    }
                }
            }
        }
        for(var runner:previous)if(!current.contains(runner))speed(runner,1);
        previous.clear();previous.addAll(current);
    }
}
