package dev.eternaldefense.displays;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.function.Supplier;

/** The displays' own channel: the tuner's changes, checked by the server. */
public final class DisplayNetwork {
    private DisplayNetwork(){}
    public static final String PROTOCOL="1";
    public static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation(GunDisplays.ID,"main"),()->PROTOCOL,PROTOCOL::equals,PROTOCOL::equals);
    static void register(){
        CHANNEL.messageBuilder(Tune.class,0,NetworkDirection.PLAY_TO_SERVER).encoder(Tune::write).decoder(Tune::read).consumerMainThread(Tune::handle).add();
    }

    /** New settings for the gun on the stand at pos. */
    public record Tune(BlockPos pos,DisplayTuning.Tune tune){
        void write(FriendlyByteBuf b){b.writeBlockPos(pos);for(var s:DisplayTuning.Setting.values())b.writeFloat(tune.get(s));}
        static Tune read(FriendlyByteBuf b){
            var pos=b.readBlockPos();var t=DisplayTuning.Tune.DEFAULT;
            for(var s:DisplayTuning.Setting.values())t=t.with(s,b.readFloat());
            return new Tune(pos,t);
        }
        void handle(Supplier<NetworkEvent.Context> context){
            var p=context.get().getSender();if(p==null||p.isSpectator())return;
            var level=p.serverLevel();
            if(!level.isLoaded(pos)||!DisplayTuner.holds(p)||!level.mayInteract(p,pos))return;
            if(p.distanceToSqr(Vec3.atCenterOf(pos))>DisplayTuning.REACH*DisplayTuning.REACH)return;
            if(level.getBlockEntity(pos) instanceof DisplayRacks.DisplayEntity display&&!display.gun().isEmpty())display.tune(tune.clamped());
        }
    }
}
