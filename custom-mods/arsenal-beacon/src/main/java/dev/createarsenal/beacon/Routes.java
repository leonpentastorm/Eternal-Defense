package dev.createarsenal.beacon;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.PacketDistributor;
import java.util.*;

/**
 * The supply rail of the Forward Operating Bases (0.0.24, FOB slice 2), on the server. Each FOB has at most one pair of gates (its Supply
 * Delivery Gate in its village, a Supply Receiving Gate in the base zone). A ride in a plain minecart through the Receiving Gate, then the
 * Delivery Gate, opens the route (the commissioning ride; its one-way time is the route's T); the route then delivers the FOB's tribute by
 * itself every 2T/0.8 ticks of play while its inspection clock runs, and a round trip (Receiving, Delivery, Receiving) by the same rider in the
 * same cart carries twice a passive trip and renews the clock. Tribute enters the beacon reward chest only through {@link RaidRewards#queue}.
 * Rides live in memory only: a restart, a logout or a dismount ends one, and the stockpile is touched only when goods are delivered.
 */
final class Routes {
    private Routes(){}
    /** The cart a rider was in and where it was last tick (passes are found between two positions, never from one point). */
    record Track(UUID cart,double x,double y,double z){}
    private static final Map<UUID,Track> TRACK=new HashMap<>();
    /** A ride under way on a route (by FOB id). */
    static final class Run{
        final UUID rider,cart;RouteRules.Stage stage=RouteRules.Stage.OUTBOUND;long start,legStart,outLeg;int load;
        Run(UUID rider,UUID cart,long now){this.rider=rider;this.cart=cart;start=legStart=now;}
    }
    static final Map<Long,Run> RUNS=new HashMap<>();
    static void clear(){TRACK.clear();RUNS.clear();}
    static final double SLACK=.2;

    // ---- every server tick: riders and the gates they pass ------------------------------------------------------------------------------------------
    static void tick(ServerLevel l,int clock){
        var server=l.getServer();if(server.getPlayerList().getPlayerCount()==0)return;
        var d=FobData.get(l);
        for(var p:server.getPlayerList().getPlayers()){
            if(p.level()!=l||!(p.getVehicle() instanceof AbstractMinecart cart)||cart.getMinecartType()!=AbstractMinecart.Type.RIDEABLE){TRACK.remove(p.getUUID());continue;}
            var now=cart.position();var last=TRACK.put(p.getUUID(),new Track(cart.getUUID(),now.x,now.y,now.z));
            if(last==null||!last.cart().equals(cart.getUUID()))continue;
            for(var f:List.copyOf(d.fobs.values())){
                for(var kind:RailGates.Kind.values()){
                    var gate=kind==RailGates.Kind.DELIVERY?f.deliveryGate:f.receivingGate;
                    if(gate==null||Math.abs(gate.getX()-now.x)>8||Math.abs(gate.getZ()-now.z)>8||!l.hasChunkAt(gate))continue;
                    var state=l.getBlockState(gate);if(!(state.getBlock() instanceof RailGates.GateBlock))continue;
                    var rail=gate.below();boolean alongX=state.getValue(RailGates.FACING).getAxis()==net.minecraft.core.Direction.Axis.X;
                    if(RouteRules.crossing(last.x(),last.y(),last.z(),now.x,now.y,now.z,rail.getX(),rail.getY(),rail.getZ(),alongX,SLACK)!=0)pass(l,d,f,kind,p,cart);
                }
            }
        }
        if(clock%20==0)second(l,d);
    }

    /** A rider in a plain minecart rolled through a gate of FOB {@code f}. */
    static void pass(ServerLevel l,FobData d,FobData.Fob f,RailGates.Kind kind,ServerPlayer p,AbstractMinecart cart){
        if(!FobRules.working(f.state)){say(p,"route.pass.fob_down",f.name);RUNS.remove(f.id);return;}
        if(!f.hasRoute()){say(p,"route.pass.no_partner",f.name);return;}
        var run=RUNS.get(f.id);boolean same=run!=null&&run.rider.equals(p.getUUID())&&run.cart.equals(cart.getUUID());
        double distance=f.separation();long now=l.getGameTime();
        var step=RouteRules.step(kind.gate(),run==null?null:run.stage,same,f.commissioned,distance);
        switch(step){
            case STARTED->{RUNS.put(f.id,new Run(p.getUUID(),cart.getUUID(),now));say(p,f.commissioned?"route.pass.started":"route.pass.started_first",f.name);sound(p,SoundEvents.NOTE_BLOCK_BELL.value(),1.4f);}
            case RESTARTED->{run.start=run.legStart=now;say(p,"route.pass.restarted",f.name);sound(p,SoundEvents.NOTE_BLOCK_BELL.value(),1.2f);}
            case BUSY->{say(p,"route.pass.busy",f.name);sound(p,SoundEvents.VILLAGER_NO,1f);}
            case START_AT_HOME->{say(p,"route.pass.start_at_home",f.name);sound(p,SoundEvents.VILLAGER_NO,1f);}
            case WRONG_GATE->say(p,"route.pass.wrong_gate");
            case TOO_CLOSE->{RUNS.remove(f.id);say(p,"route.pass.too_close",(int)distance,RouteRules.MIN_SEPARATION);sound(p,SoundEvents.VILLAGER_NO,1f);}
            case COMMISSIONED->{
                accrue(d,f);
                f.commissioned=true;f.baseline=RouteRules.clamp(now-run.start,distance);f.clock=RouteRules.CLOCK;f.nextPassive=d.onlineTicks+RouteRules.period(f.baseline);
                run.outLeg=now-run.start;run.stage=RouteRules.Stage.RETURN;run.legStart=now;run.load=load(l,f,distance);d.setDirty();
                say(p,run.load<0?"route.pass.commissioned_backlog":"route.pass.commissioned",f.name,seconds(f.baseline),Math.max(0,run.load));sound(p,SoundEvents.PLAYER_LEVELUP,1f);
                Fobs.announce(l,Component.translatable("gui.arsenal_beacon.route.opened",f.name,p.getDisplayName(),seconds(f.baseline)));
            }
            case LOADED->{
                accrue(d,f);run.outLeg=now-run.start;run.stage=RouteRules.Stage.RETURN;run.legStart=now;run.load=load(l,f,distance);
                if(run.load<0){say(p,"route.pass.loaded_backlog",f.name);sound(p,SoundEvents.VILLAGER_NO,1f);}
                else{say(p,"route.pass.loaded",run.load,f.name);sound(p,SoundEvents.ITEM_PICKUP,.8f);}
            }
            case COMPLETE->{
                RUNS.remove(f.id);accrue(d,f);
                f.baseline=RouteRules.baseline(run.outLeg,now-run.legStart,distance);f.clock=RouteRules.CLOCK;d.setDirty();
                var campaign=CampaignData.get(l.getServer().overworld());
                if(RouteRules.backlog(campaign.rewards.size())){say(p,"route.pass.complete_backlog",RouteRules.CLOCK/RouteRules.HOUR);sound(p,SoundEvents.VILLAGER_NO,1f);return;}
                int[] taken=RouteRules.take(f.stock,Math.max(0,run.load));
                deliver(l,d,f,taken,"ride");
                say(p,"route.pass.complete",RouteRules.total(taken),RouteRules.CLOCK/RouteRules.HOUR);sound(p,SoundEvents.PLAYER_LEVELUP,1.3f);
                if(RouteRules.total(taken)>0)Fobs.announce(l,Component.translatable("gui.arsenal_beacon.route.delivered_ride",f.name,p.getDisplayName(),goods(taken)));
            }
        }
    }
    /** What a ride takes at the Delivery Gate: twice a passive trip, never more than the stockpile holds; -1 (nothing) while the reward chest is backed up. */
    private static int load(ServerLevel l,FobData.Fob f,double distance){
        if(RouteRules.backlog(CampaignData.get(l.getServer().overworld()).rewards.size()))return -1;
        int whole=0;for(double s:f.stock)whole+=(int)Math.floor(s);return Math.min(RouteRules.activeCapacity(distance),whole);
    }

    // ---- once a second: the clock, passive deliveries, rides that went wrong ----------------------------------------------------------------------
    static void second(ServerLevel l,FobData d){
        d.onlineTicks+=20;long now=d.onlineTicks;boolean dirty=false;
        boolean backlog=RouteRules.backlog(CampaignData.get(l.getServer().overworld()).rewards.size());
        for(var f:d.fobs.values()){
            boolean active=FobRules.working(f.state);
            if(!active)f.accruedAt=now;   // nothing is made while a FOB is not ACTIVE
            if(active&&f.commissioned&&f.clock>0){
                f.clock=Math.max(0,f.clock-20);dirty=true;
                if(f.clock==0){Fobs.announce(l,Component.translatable("gui.arsenal_beacon.route.overdue",f.name));}
            }
            if(f.hasRoute()&&f.baseline>0&&RouteRules.passive(active,f.commissioned,f.clock,backlog)){
                long period=RouteRules.period(f.baseline);
                if(f.nextPassive==0||f.nextPassive>now+period)f.nextPassive=now+period;
                else if(now>=f.nextPassive){
                    accrue(d,f);int[] taken=RouteRules.take(f.stock,RouteRules.capacity(f.separation()));
                    if(RouteRules.total(taken)>0){deliver(l,d,f,taken,"passive");for(int i=0;i<taken.length;i++)f.note[i]+=taken[i];}
                    f.nextPassive=now+period;dirty=true;
                }
            }else f.nextPassive=0;
            int noted=0;for(int n:f.note)noted+=n;
            if(noted>0&&now-f.lastNoteAt>=RouteRules.NOTE_TICKS){
                Fobs.announce(l,Component.translatable("gui.arsenal_beacon.route.delivered",f.name,goods(f.note)));
                Arrays.fill(f.note,0);f.lastNoteAt=now;dirty=true;
            }
        }
        // rides that cannot go on: the rider left, died, changed world or cart, the FOB or a gate is gone, or the leg took far too long
        for(var e:List.copyOf(RUNS.entrySet())){
            var f=d.byId(e.getKey());var run=e.getValue();var rider=l.getServer().getPlayerList().getPlayer(run.rider);
            String reason=null;
            if(f==null||!FobRules.working(f.state))reason="fob_down";
            else if(!f.hasRoute())reason="gate_gone";
            else if(rider==null)reason="logout";
            else if(!rider.isAlive())reason="death";
            else if(rider.level()!=l)reason="dimension";
            else if(!(rider.getVehicle() instanceof AbstractMinecart cart)||!cart.getUUID().equals(run.cart))reason="dismount";
            else if(l.getGameTime()-run.legStart>RouteRules.legTimeout(f.baseline))reason="timeout";
            if(reason!=null){RUNS.remove(e.getKey());if(rider!=null){say(rider,"route.cancel."+reason,f==null?"":f.name);sound(rider,SoundEvents.VILLAGER_NO,1f);}}
        }
        if(dirty)d.setDirty();
    }
    /** Brings the stockpile up to date (lazily, from the ticks of play since it was last done), only for an ACTIVE FOB. */
    static void accrue(FobData d,FobData.Fob f){
        long now=d.onlineTicks;
        if(FobRules.working(f.state)&&f.accruedAt>0&&now>f.accruedAt)f.stock=RouteRules.accrue(f.stock,f.workers,now-f.accruedAt);
        f.accruedAt=now;d.setDirty();
    }
    /** Goods leave the stockpile and enter the reward chest's queue in the same step, so every item is paid exactly once. */
    static void deliver(ServerLevel l,FobData d,FobData.Fob f,int[] amounts,String kind){
        var stacks=new ArrayList<ItemStack>();
        for(int i=0;i<amounts.length;i++){
            if(amounts[i]<=0)continue;
            var item=BuiltInRegistries.ITEM.get(new ResourceLocation(RouteRules.GOODS.get(i).id()));
            f.stock[i]=Math.max(0,f.stock[i]-amounts[i]);stacks.add(new ItemStack(item,amounts[i]));
        }
        if(stacks.isEmpty())return;
        RaidRewards.queue(CampaignData.get(l.getServer().overworld()),stacks);
        f.lastKind=kind;f.lastItems=RouteRules.total(amounts);f.lastAt=d.onlineTicks;d.setDirty();
        com.mojang.logging.LogUtils.getLogger().info("[route] {}: {} delivery of {} items",f.name,kind,f.lastItems);
    }
    /** "3 Redstone Dust, 2 Flint" for the chat lines. */
    static Component goods(int[] amounts){
        var out=Component.empty();boolean first=true;
        for(int i=0;i<amounts.length;i++){
            if(amounts[i]<=0)continue;
            if(!first)out.append(", ");first=false;
            out.append(amounts[i]+" ").append(BuiltInRegistries.ITEM.get(new ResourceLocation(RouteRules.GOODS.get(i).id())).getDescription());
        }
        return out;
    }
    static long seconds(long ticks){return Math.round(ticks/20.0);}

    // ---- the gates ------------------------------------------------------------------------------------------------------------------------------
    /** Why a gate cannot stand here: a key under {@code gate.refused.}, or null. */
    static String placeProblem(ServerLevel l,BlockPos anchor,RailGates.Kind kind,net.minecraft.core.Direction look,ItemStack stack){
        if(l.dimension()!=Level.OVERWORLD)return "overworld";
        var facing=RailGates.facingOver(l.getBlockState(anchor.below()),look);
        if(facing==null)return "rail";
        if(!RailGates.available(l,anchor,facing))return "no_room";
        if(kind==RailGates.Kind.DELIVERY){
            var f=Fobs.zoneAt(l,anchor);if(f==null)return "not_fob";
            if(f.deliveryGate!=null&&!f.deliveryGate.equals(anchor))return "has_gate";
        }else if(BaseZone.problem(l,anchor)!=null)return "zone";
        return null;
    }
    /** A gate was placed: the Delivery Gate links to the FOB whose village it stands in; the Receiving Gate to the FOB it was carried from, or asks. */
    static void placed(ServerLevel l,BlockPos pos,RailGates.Kind kind,ServerPlayer p,ItemStack stack){
        var d=FobData.get(l);var be=l.getBlockEntity(pos) instanceof RailGates.GateEntity g?g:null;if(be==null)return;
        if(kind==RailGates.Kind.DELIVERY){
            var f=Fobs.zoneAt(l,pos);
            if(f!=null&&f.deliveryGate==null){link(l,d,f,RailGates.Kind.DELIVERY,pos,be);if(p!=null)say(p,"gate.linked",f.name);}
            return;
        }
        long carried=stack.hasTag()?stack.getTag().getLong("Fob"):0;var f=d.byId(carried);
        if(f!=null&&f.receivingGate==null){link(l,d,f,RailGates.Kind.RECEIVING,pos,be);if(p!=null)say(p,"gate.linked",f.name);return;}
        if(p!=null)open(p,pos,"",true);
    }
    /** Links a gate to FOB {@code f}. A new pair (or a moved gate) needs a new commissioning ride: the measured time no longer fits the track. */
    static void link(ServerLevel l,FobData d,FobData.Fob f,RailGates.Kind kind,BlockPos pos,RailGates.GateEntity be){
        if(kind==RailGates.Kind.DELIVERY)f.deliveryGate=pos.immutable();else f.receivingGate=pos.immutable();
        f.commissioned=false;f.nextPassive=0;RUNS.remove(f.id);be.link(f.id);d.setDirty();
        log("{}: {} linked at {}",f.name,kind.id,pos.toShortString());
    }
    /** A gate block is gone (shovel, creative break, command): its FOB loses it, and a ride on that route ends. */
    static void gateGone(ServerLevel l,BlockPos pos){
        var d=FobData.get(l);var f=d.byGate(pos);if(f==null)return;
        if(pos.equals(f.deliveryGate))f.deliveryGate=null;
        if(pos.equals(f.receivingGate))f.receivingGate=null;
        RUNS.remove(f.id);d.setDirty();
    }
    static String nameFor(ServerLevel l,long id){var f=id==0?null:FobData.get(l).byId(id);return f==null?"":f.name;}
    /** Sends the gates of FOB {@code f} their name again (after a rename). */
    static void refreshNames(ServerLevel l,FobData.Fob f){
        for(var pos:new BlockPos[]{f.deliveryGate,f.receivingGate})if(pos!=null&&l.hasChunkAt(pos)&&l.getBlockEntity(pos) instanceof RailGates.GateEntity be)be.link(f.id);
    }
    /** The traffic light of one gate, once a second (its block entity's ticker); a gate whose FOB is gone turns UNLINKED. */
    static void light(ServerLevel l,BlockPos pos,BlockState state,RailGates.GateEntity be){
        var d=FobData.get(l);var f=be.fobId==0?null:d.byId(be.fobId);
        boolean linked=f!=null&&(pos.equals(f.deliveryGate)||pos.equals(f.receivingGate));
        if(be.fobId!=0&&!linked)be.link(0);
        var signal=signal(l,d,linked?f:null);
        var lamp=RailGates.Lamp.of(signal);
        if(state.getValue(RailGates.SIGNAL)!=lamp)l.setBlock(pos,state.setValue(RailGates.SIGNAL,lamp),2|16);
    }
    static RouteRules.Signal signal(ServerLevel l,FobData d,FobData.Fob f){
        if(f==null)return RouteRules.Signal.RED;
        boolean backlog=RouteRules.backlog(CampaignData.get(l.getServer().overworld()).rewards.size());
        return RouteRules.signal(true,f.hasRoute(),FobRules.working(f.state),f.commissioned,f.clock,RUNS.containsKey(f.id),backlog);
    }

    // ---- clicks: the screen, the shovel ----------------------------------------------------------------------------------------------------------
    static void use(ServerPlayer p,BlockPos anchor,ItemStack held){
        if(held.is(ArsenalBeacon.CONTROLLER.get())){pickUp(p,anchor);return;}
        open(p,anchor,"",true);
    }
    /** The Recovery Shovel takes a gate back for free; the item remembers its FOB, so the gate links again where it is placed. */
    static void pickUp(ServerPlayer p,BlockPos anchor){
        var l=p.serverLevel();var state=l.getBlockState(anchor);if(!(state.getBlock() instanceof RailGates.GateBlock gate))return;
        long id=l.getBlockEntity(anchor) instanceof RailGates.GateEntity be?be.fobId:0;var f=FobData.get(l).byId(id);
        var stack=new ItemStack(gate.kind==RailGates.Kind.DELIVERY?ArsenalBeacon.DELIVERY_GATE_ITEM.get():ArsenalBeacon.RECEIVING_GATE_ITEM.get());
        if(f!=null){stack.getOrCreateTag().putLong("Fob",f.id);stack.getOrCreateTag().putString("FobName",f.name);}
        l.removeBlock(anchor,false);
        if(!p.getInventory().add(stack))p.drop(stack,false);
        say(p,"gate.picked_up");
    }
    static final int LINK=10;
    static void open(ServerPlayer p,BlockPos anchor,String message){open(p,anchor,message,false);}
    /** {@code fresh}: a click on the gate always opens a new screen; otherwise an open one is refreshed in place. */
    static void open(ServerPlayer p,BlockPos anchor,String message,boolean fresh){
        var l=p.serverLevel();if(!(l.getBlockState(anchor).getBlock() instanceof RailGates.GateBlock))return;
        var state=state(p,anchor,message);
        if(!fresh&&p.containerMenu instanceof RailGates.GateMenu menu&&menu.pos.equals(anchor)){BeaconNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new Hunts.TableState(state));return;}
        var title=Component.translatable("block.arsenal_beacon."+((RailGates.GateBlock)l.getBlockState(anchor).getBlock()).kind.id);
        NetworkHooks.openScreen(p,new SimpleMenuProvider((id,inv,who)->new RailGates.GateMenu(id,inv,anchor,new CompoundTag()),title),buf->{buf.writeBlockPos(anchor);buf.writeNbt(state);});
    }
    /** Everything a gate's screen shows (see {@code GateClient}). */
    static CompoundTag state(ServerPlayer p,BlockPos anchor,String message){
        var l=p.serverLevel();var d=FobData.get(l);var n=new CompoundTag();
        var gate=(RailGates.GateBlock)l.getBlockState(anchor).getBlock();
        long id=l.getBlockEntity(anchor) instanceof RailGates.GateEntity be?be.fobId:0;var f=id==0?null:d.byId(id);
        boolean linked=f!=null&&(anchor.equals(f.deliveryGate)||anchor.equals(f.receivingGate));
        n.putLong("gate",anchor.asLong());n.putString("kind",gate.kind.id);n.putBoolean("linked",linked);n.putString("message",message);
        n.putString("signal",signal(l,d,linked?f:null).id());
        var campaign=CampaignData.get(l.getServer().overworld());n.putInt("queued",campaign.rewards.size());n.putInt("queueCap",RouteRules.BACKLOG_CAP);
        if(linked){
            accrue(d,f);double distance=f.separation();
            n.putString("name",f.name);n.putString("state",f.state.id());n.putBoolean("partner",f.hasRoute());n.putBoolean("commissioned",f.commissioned);
            n.putLong("clock",f.clock);n.putLong("clockMax",RouteRules.CLOCK);n.putBoolean("warn",RouteRules.warning(f.clock));
            n.putInt("distance",(int)Math.round(distance));n.putInt("minDistance",RouteRules.MIN_SEPARATION);n.putLong("baseline",f.baseline);
            n.putInt("capacity",RouteRules.capacity(distance));n.putInt("active",RouteRules.activeCapacity(distance));
            n.putLong("nextIn",f.nextPassive>0?Math.max(0,f.nextPassive-d.onlineTicks):-1);n.putLong("period",f.baseline>0?RouteRules.period(f.baseline):0);
            n.putBoolean("riding",RUNS.containsKey(f.id));
            n.putString("lastKind",f.lastKind);n.putInt("lastItems",f.lastItems);n.putLong("lastAgo",f.lastAt>0?d.onlineTicks-f.lastAt:-1);
            var stock=new ListTag();
            for(int i=0;i<RouteRules.GOODS.size();i++){var g=RouteRules.GOODS.get(i);var row=new CompoundTag();row.putString("id",g.id());row.putInt("amount",(int)Math.floor(f.stock[i]));row.putInt("cap",(int)RouteRules.cap(g,f.workers));stock.add(row);}
            n.put("stock",stock);n.putInt("workers",Math.min(RouteRules.MAX_WORKERS,f.workers));
        }
        if(gate.kind==RailGates.Kind.RECEIVING){
            var options=new ListTag();
            for(var o:d.fobs.values())if(o.receivingGate==null||o.receivingGate.equals(anchor)){var row=new CompoundTag();row.putLong("id",o.id);row.putString("name",o.name);row.putString("state",o.state.id());row.putBoolean("current",o.receivingGate!=null);options.add(row);}
            n.put("options",options);
        }
        return n;
    }
    /** A gate screen button: link a Receiving Gate to a FOB (checked again here). */
    static void button(ServerPlayer p,BlockPos anchor,int id){
        var l=p.serverLevel();if(!(p.containerMenu instanceof RailGates.GateMenu menu)||!menu.stillValid(p))return;
        if(!(l.getBlockState(anchor).getBlock() instanceof RailGates.GateBlock gate)||gate.kind!=RailGates.Kind.RECEIVING||!(l.getBlockEntity(anchor) instanceof RailGates.GateEntity be))return;
        if(id<LINK)return;
        var d=FobData.get(l);var options=new ArrayList<FobData.Fob>();
        for(var o:d.fobs.values())if(o.receivingGate==null||o.receivingGate.equals(anchor))options.add(o);
        int index=id-LINK;if(index<0||index>=options.size()){open(p,anchor,"gate.result.gone");return;}
        var target=options.get(index);
        if(anchor.equals(target.receivingGate)){open(p,anchor,"gate.result.same");return;}
        var old=be.fobId==0?null:d.byId(be.fobId);if(old!=null&&anchor.equals(old.receivingGate)){old.receivingGate=null;old.commissioned=false;RUNS.remove(old.id);}
        link(l,d,target,RailGates.Kind.RECEIVING,anchor,be);
        open(p,anchor,"gate.result.linked");
    }
    /** The FOB shop's second listing: one Supply Delivery Gate per FOB, while it is ACTIVE (paid like any fabricated block). */
    static String buyGate(ServerPlayer p,FobData.Fob f){
        if(!FobRules.working(f.state))return "fob.result.closed";
        if(f.deliveryGate!=null)return "fob.result.has_gate";
        var stack=new ItemStack(ArsenalBeacon.DELIVERY_GATE_ITEM.get());stack.getOrCreateTag().putLong("Fob",f.id);stack.getOrCreateTag().putString("FobName",f.name);
        return WeaponPlatform.transact(p,Economy.fabrication("supply_delivery_gate"),stack)?"fob.result.bought.gate":"fob.result.no_materials";
    }

    static void say(ServerPlayer p,String key,Object... args){p.displayClientMessage(Component.translatable("gui.arsenal_beacon."+key,args),true);}
    private static void sound(ServerPlayer p,net.minecraft.sounds.SoundEvent sound,float pitch){p.playNotifySound(sound,SoundSource.PLAYERS,.7f,pitch);}
    private static void log(String text,Object... args){com.mojang.logging.LogUtils.getLogger().info("[route] "+text,args);}

    static final class RouteEvents {
        @net.minecraftforge.eventbus.api.SubscribeEvent public void loggedOut(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e){
            TRACK.remove(e.getEntity().getUUID());RUNS.values().removeIf(r->r.rider.equals(e.getEntity().getUUID()));
        }
        @net.minecraftforge.eventbus.api.SubscribeEvent public void routeServerStarted(net.minecraftforge.event.server.ServerStartedEvent e){clear();}
    }
}
