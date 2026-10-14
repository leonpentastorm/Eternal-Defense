package dev.eternaldefense.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import java.util.*;
import java.util.function.Supplier;

/** Where a FOB counts: the part of a village a FOB Beacon would take, shown before planting. */
final class FobOutline {
    private FobOutline(){}
    /** Ticks between looks (while holding a FOB Beacon) and between zone updates otherwise */
    static final int PREVIEW_TICKS=10,ZONES_TICKS=40,NEAR=160;
    /** A sent state is sent again at least this often (ticks) */
    static final int RESEND_TICKS=200;
    private record Sent(int hash,long at){}
    private static final Map<UUID,Sent> SENT=new HashMap<>();
    /** The preview's zone, kept a few seconds per village section looked */
    private record Looked(Set<Long> sections,long at){}
    private static final Map<Long,Looked> LOOKED=new HashMap<>();
    static void clear(){SENT.clear();LOOKED.clear();}
    static void forget(ServerPlayer p){SENT.remove(p.getUUID());}

    static boolean holds(net.minecraft.world.entity.player.Player p){
        return p.getMainHandItem().is(EternalDefense.FOB_BEACON_ITEM.get())||p.getOffhandItem().is(EternalDefense.FOB_BEACON_ITEM.get());
    }
    static void tick(ServerLevel l,int clock){
        if(clock%PREVIEW_TICKS!=0)return;
        if(clock%RESEND_TICKS==0)LOOKED.entrySet().removeIf(e->l.getGameTime()-e.getValue().at()>RESEND_TICKS||l.getGameTime()<e.getValue().at());
        for(var p:l.getServer().getPlayerList().getPlayers()){
            if(p instanceof net.minecraftforge.common.util.FakePlayer)continue;
            boolean holding=holds(p);
            if(!holding&&clock%ZONES_TICKS!=0)continue;
            var data=state(p,holding);
            var last=SENT.get(p.getUUID());long now=l.getGameTime();int hash=data.hashCode();
            if(last!=null&&last.hash()==hash&&now-last.at()<RESEND_TICKS&&now>=last.at())continue;
            SENT.put(p.getUUID(),new Sent(hash,now));
            BeaconNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new Sync(data));
        }
    }
    /** What one player sees: the FOB zones near them and, while they hold a FOB Beacon */
    static CompoundTag state(ServerPlayer p,boolean holding){
        var out=new CompoundTag();var zones=new ListTag();out.put("zones",zones);
        if(p.level().dimension()!=Level.OVERWORLD)return out;
        var l=p.serverLevel();var d=FobData.get(l);
        for(var f:d.fobs.values()){
            if(!f.showZone&&!holding)continue;
            if(Math.abs(f.pos.getX()-p.getX())>NEAR||Math.abs(f.pos.getZ()-p.getZ())>NEAR)continue;
            var sections=FobZone.of(l,f);if(sections.isEmpty())continue;
            var z=new CompoundTag();z.putLong("id",f.id);z.putString("name",f.name);z.putString("state",f.state.id());z.putInt("ground",f.pos.getY());   // The FOB Beacon's ground level, marked on the walls
            z.putLongArray("sections",sorted(sections));zones.add(z);
        }
        if(holding){var at=target(p);if(at!=null)out.put("preview",preview(l,at));}
        return out;
    }
    /** Where a FOB Beacon used now would stand (as the item works it out) */
    static BlockPos target(ServerPlayer p){
        var hit=p.pick(p.getBlockReach(),1f,false);
        if(hit.getType()!=HitResult.Type.BLOCK||!(hit instanceof BlockHitResult block))return null;
        var hand=p.getMainHandItem().is(EternalDefense.FOB_BEACON_ITEM.get())?InteractionHand.MAIN_HAND:InteractionHand.OFF_HAND;
        return new BlockPlaceContext(p,hand,p.getItemInHand(hand),block).getClickedPos();
    }
    /** The spot's zone, its villagers, beds and workstations, and why a FOB cannot be planted there (empty: it can). */
    static CompoundTag preview(ServerLevel l,BlockPos at){
        var key=SectionPos.asLong(SectionPos.blockToSectionCoord(at.getX()),SectionPos.blockToSectionCoord(at.getY()),SectionPos.blockToSectionCoord(at.getZ()));
        var looked=LOOKED.get(key);long now=l.getGameTime();
        Set<Long> zone;
        if(looked!=null&&now-looked.at()<ZONES_TICKS&&now>=looked.at())zone=looked.sections();
        else{zone=FobZone.sections(l,at);LOOKED.put(key,new Looked(zone,now));}
        var n=new CompoundTag();n.putLong("pos",at.asLong());
        String problem=Fobs.plantProblem(l,at,zone);n.putString("problem",problem==null?"":problem);
        n.putInt("villagers",zone.isEmpty()?0:FobZone.villagers(l,zone).size());
        int[] places=zone.isEmpty()?new int[2]:FobZone.homesAndJobs(l,zone);n.putInt("beds",places[0]);n.putInt("jobs",places[1]);
        n.putLongArray("sections",sorted(zone));
        return n;
    }
    private static long[] sorted(Set<Long> s){var a=s.stream().mapToLong(Long::longValue).toArray();Arrays.sort(a);return a;}

    /** Server to client: the outlines and the preview (protocol 35). */
    record Sync(CompoundTag data){
        static void encode(Sync p,FriendlyByteBuf b){b.writeNbt(p.data);}
        static Sync decode(FriendlyByteBuf b){var n=b.readNbt();return new Sync(n==null?new CompoundTag():n);}
        static void handle(Sync p,Supplier<NetworkEvent.Context> ctx){ctx.get().enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->FobOutlineClient.receive(p.data)));ctx.get().setPacketHandled(true);}
    }
}
