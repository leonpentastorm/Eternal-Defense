package dev.eternaldefense.beacon;

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
 * Rides live in memory only: a restart, a logout or a dismount ends one. 0.0.31 (owner): the tribute is the FOB's four loot slots, delivered
 * into the Receiving Gate's own storage (a double chest's worth, take only) instead of the beacon's reward chest.
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
                    var facing=state.getValue(RailGates.FACING);var lanes=RailGates.laneCorner(gate,facing);boolean alongX=facing.getAxis()==net.minecraft.core.Direction.Axis.X;
                    if(RouteRules.crossing(last.x(),last.y(),last.z(),now.x,now.y,now.z,lanes.getX(),lanes.getY(),lanes.getZ(),alongX,RailGates.LANES,SLACK)!=0)pass(l,d,f,kind,p,cart);
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
            case WRONG_GATE->{if(now-run.legStart>RouteRules.WRONG_GATE_QUIET)say(p,"route.pass.wrong_gate");}   // not on the way back through the gate's other lane
            case TOO_CLOSE->{RUNS.remove(f.id);say(p,"route.pass.too_close",(int)distance,RouteRules.MIN_SEPARATION);sound(p,SoundEvents.VILLAGER_NO,1f);}
            case COMMISSIONED->{
                f.commissioned=true;f.baseline=RouteRules.clamp(now-run.start,distance);f.clock=RouteRules.CLOCK;f.nextPassive=d.onlineTicks+RouteRules.period(f.baseline);
                run.outLeg=now-run.start;run.stage=RouteRules.Stage.RETURN;run.legStart=now;run.load=load(d,f,run.outLeg);d.setDirty();
                say(p,"route.pass.commissioned",f.name,seconds(f.baseline),run.load);sound(p,SoundEvents.PLAYER_LEVELUP,1f);
                Fobs.announce(l,Component.translatable("gui.eternal_defense.route.opened",f.name,p.getDisplayName(),seconds(f.baseline)));
            }
            case LOADED->{
                run.outLeg=now-run.start;run.stage=RouteRules.Stage.RETURN;run.legStart=now;run.load=load(d,f,run.outLeg);
                say(p,"route.pass.loaded",run.load,f.name);sound(p,SoundEvents.ITEM_PICKUP,.8f);
            }
            case COMPLETE->{
                RUNS.remove(f.id);
                f.baseline=RouteRules.baseline(run.outLeg,now-run.legStart,distance);f.clock=RouteRules.CLOCK;
                long period=RouteRules.period(f.baseline);
                f.nextPassive=d.onlineTicks+period;d.setDirty();   // the ride carried the trips: the next passive one waits a whole period
                if(full(f)){say(p,"route.pass.complete_full",RouteRules.CLOCK/RouteRules.HOUR);sound(p,SoundEvents.VILLAGER_NO,1f);return;}
                var brought=haul(f,RouteRules.rideTrips(sinceLast(d,f,0),period),d.onlineTicks);int stored=store(d,f,brought,"ride");
                say(p,"route.pass.complete",stored,RouteRules.CLOCK/RouteRules.HOUR);sound(p,SoundEvents.PLAYER_LEVELUP,1.3f);
                if(stored>0)Fobs.announce(l,Component.translatable("gui.eternal_defense.route.delivered_ride",f.name,p.getDisplayName(),goods(brought)));
            }
        }
    }
    /** Ticks of play since the route's last delivery, {@code ahead} ticks from now; a route that never delivered gets a full ride. */
    static long sinceLast(FobData d,FobData.Fob f,long ahead){return f.lastTrip==0?Long.MAX_VALUE/4:d.onlineTicks+ahead-f.lastTrip;}
    /** What a ride will bring home if the way back takes as long as the way out (the tallies looked at, not spent: the Receiving Gate takes it). */
    static int load(FobData d,FobData.Fob f,long outLeg){
        double trips=RouteRules.rideTrips(sinceLast(d,f,outLeg),RouteRules.period(f.baseline));
        return RouteRules.total(RouteRules.trip(f.tally.clone(),f.loots(),f.yield(),trips));
    }
    // ---- once a second: the clock, passive deliveries, rides that went wrong ----------------------------------------------------------------------
    static void second(ServerLevel l,FobData d){
        d.onlineTicks+=20;long now=d.onlineTicks;boolean dirty=false;
        for(var f:d.fobs.values()){
            boolean active=FobRules.working(f.state),full=full(f);
            if(active&&f.commissioned&&f.clock>0){
                f.clock=Math.max(0,f.clock-20);dirty=true;
                if(f.clock==0){Fobs.announce(l,Component.translatable("gui.eternal_defense.route.overdue",f.name));}
            }
            if(f.hasRoute()&&f.baseline>0&&RouteRules.passive(active,f.commissioned,f.clock,full)){
                long period=RouteRules.period(f.baseline);
                if(f.nextPassive==0||f.nextPassive>now+period)f.nextPassive=now+period;
                else if(now>=f.nextPassive){
                    var brought=haul(f,1,now);store(d,f,brought,"passive");
                    for(var e:brought.entrySet())f.note.merge(e.getKey(),e.getValue(),Integer::sum);
                    f.nextPassive=now+period;dirty=true;
                }
            }else f.nextPassive=0;
            if(!f.note.isEmpty()&&now-f.lastNoteAt>=RouteRules.NOTE_TICKS){
                Fobs.announce(l,Component.translatable("gui.eternal_defense.route.delivered",f.name,goods(f.note)));
                f.note.clear();f.lastNoteAt=now;dirty=true;
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
        // an open gate screen counts down with the route (its state again, once a second), and so does the FOB screen's tribute column (0.0.31)
        for(var p:l.getServer().getPlayerList().getPlayers()){
            if(p.containerMenu instanceof RailGates.GateMenu menu&&menu.stillValid(p))open(p,menu.pos,menu.message);
            else if(p.containerMenu instanceof FobBlocks.FobMenu menu&&menu.stillValid(p))Fobs.open(p,menu.pos,menu.message);
        }
    }
    // ---- the tribute (0.0.31): the slots' tallies into the Receiving Gate's storage ---------------------------------------------------------------
    /** What {@code trips} trips bring (item id to count, the slots in order), taken out of the tallies; {@code now} is the delivery's time. */
    static Map<String,Integer> haul(FobData.Fob f,double trips,long now){
        f.lastTrip=now;
        var loots=f.loots();int[] n=RouteRules.trip(f.tally,loots,f.yield(),trips);
        var out=new LinkedHashMap<String,Integer>();
        for(int i=0;i<n.length;i++)if(n[i]>0)out.merge(loots[i].id(),n[i],Integer::sum);
        return out;
    }
    static ItemStack stack(String id,int count){return new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(id)),count);}
    /** No room in the storage for any item the slots that are on would send: deliveries wait (amber). */
    static boolean full(FobData.Fob f){
        for(var slot:RouteRules.Slot.values())if(f.slotOn[slot.ordinal()]&&f.depot().canAddItem(stack(f.loot(slot).id(),1)))return false;
        return true;
    }
    /** Puts a haul into the storage; what does not fit goes back into its slot's tally (nothing is lost). Returns the items stored. */
    static int store(FobData d,FobData.Fob f,Map<String,Integer> haul,String kind){
        int stored=0;var loots=f.loots();
        for(var e:haul.entrySet()){
            int left=e.getValue();var item=stack(e.getKey(),1);int max=Math.max(1,item.getMaxStackSize());
            while(left>0){int n=Math.min(max,left);var rest=f.depot().addItem(stack(e.getKey(),n));int put=n-rest.getCount();stored+=put;left-=n;
                if(rest.getCount()>0){left+=rest.getCount();break;}}
            if(left>0)for(int i=0;i<loots.length;i++)if(loots[i].id().equals(e.getKey())){f.tally[i]+=left*loots[i].value();break;}
        }
        if(stored>0){f.lastKind=kind;f.lastItems=stored;f.lastAt=d.onlineTicks;}
        d.setDirty();
        if(stored>0)log("{}: {} delivery of {} items into the storage",f.name,kind,stored);
        return stored;
    }
    /**
     * A FOB is deleted: its storage is handed to the player who took it back (what does not fit drops at their feet), or, without one, dropped
     * at its Receiving Gate (or the FOB Beacon) if loaded. Returns the stacks handed over.
     */
    static int spill(ServerLevel l,FobData.Fob f,ServerPlayer p){
        // no player: dropped at the Receiving Gate, or at the FOB Beacon when the gate's land is not loaded (the FOB's always is: its block just went)
        int stacks=0;var at=f.receivingGate!=null&&l.hasChunkAt(f.receivingGate)?f.receivingGate:f.pos;
        for(int i=0;i<f.depot().getContainerSize();i++){var s=f.depot().removeItemNoUpdate(i);if(s.isEmpty())continue;stacks++;
            if(p!=null){if(!p.getInventory().add(s))p.drop(s,false);}
            else if(l.hasChunkAt(at))net.minecraft.world.Containers.dropItemStack(l,at.getX()+.5,at.getY()+1,at.getZ()+.5,s);}
        return stacks;
    }
    /** "3 Redstone Dust, 2 Flint" for the chat lines. */
    static Component goods(Map<String,Integer> amounts){
        var out=Component.empty();boolean first=true;
        for(var e:amounts.entrySet()){if(e.getValue()<=0)continue;if(!first)out.append(", ");first=false;out.append(e.getValue()+" ").append(stack(e.getKey(),1).getHoverName());}
        return out;
    }
    static long seconds(long ticks){return Math.round(ticks/20.0);}

    // ---- the gates ------------------------------------------------------------------------------------------------------------------------------
    /** Why a gate cannot stand here: a key under {@code gate.refused.}, or null. */
    static String placeProblem(ServerLevel l,RailGates.Spot spot,RailGates.Kind kind,ItemStack stack){
        if(l.dimension()!=Level.OVERWORLD)return "overworld";
        if(spot==null)return "rail";
        var anchor=spot.anchor();
        if(!RailGates.available(l,anchor,spot.facing()))return "no_room";
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
        RailGates.light(l,pos,state,RailGates.Lamp.of(signal));
    }
    static RouteRules.Signal signal(ServerLevel l,FobData d,FobData.Fob f){
        if(f==null)return RouteRules.Signal.RED;
        return RouteRules.signal(true,f.hasRoute(),FobRules.working(f.state),f.commissioned,f.clock,RUNS.containsKey(f.id),full(f));
    }

    // ---- clicks: the screen, the shovel ----------------------------------------------------------------------------------------------------------
    static void use(ServerPlayer p,BlockPos anchor,ItemStack held){
        if(held.is(EternalDefense.CONTROLLER.get())){pickUp(p,anchor);return;}
        open(p,anchor,"",true);
    }
    /** The Recovery Shovel takes a gate back for free; the item remembers its FOB, so the gate links again where it is placed. */
    static void pickUp(ServerPlayer p,BlockPos anchor){
        var l=p.serverLevel();var state=l.getBlockState(anchor);if(!(state.getBlock() instanceof RailGates.GateBlock gate))return;
        long id=l.getBlockEntity(anchor) instanceof RailGates.GateEntity be?be.fobId:0;var f=FobData.get(l).byId(id);
        var stack=new ItemStack(gate.kind==RailGates.Kind.DELIVERY?EternalDefense.DELIVERY_GATE_ITEM.get():EternalDefense.RECEIVING_GATE_ITEM.get());
        if(f!=null){stack.getOrCreateTag().putLong("Fob",f.id);stack.getOrCreateTag().putString("FobName",f.name);}
        l.removeBlock(anchor,false);
        if(!p.getInventory().add(stack))p.drop(stack,false);
        say(p,"gate.picked_up");
    }
    static final int LINK=10;
    static void open(ServerPlayer p,BlockPos anchor,String message){open(p,anchor,message,false);}
    /** {@code fresh}: a click on the gate always opens a new screen; otherwise an open one is refreshed in place. */
    static void open(ServerPlayer p,BlockPos anchor,String message,boolean fresh){
        var l=p.serverLevel();if(!(l.getBlockState(anchor).getBlock() instanceof RailGates.GateBlock gate))return;
        var state=state(p,anchor,message);
        if(!fresh&&p.containerMenu instanceof RailGates.GateMenu menu&&menu.pos.equals(anchor)){menu.message=message;BeaconNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new Hunts.TableState(state));return;}
        var title=Component.translatable("block.eternal_defense."+gate.kind.id);
        // the Receiving Gate's screen holds the linked FOB's storage (take only); an unlinked one shows an empty, locked one
        long id=l.getBlockEntity(anchor) instanceof RailGates.GateEntity be?be.fobId:0;var f=id==0?null:FobData.get(l).byId(id);
        boolean storage=gate.kind==RailGates.Kind.RECEIVING;
        net.minecraft.world.Container depot=storage&&f!=null&&anchor.equals(f.receivingGate)?f.depot():null;
        NetworkHooks.openScreen(p,new SimpleMenuProvider((cid,inv,who)->new RailGates.GateMenu(cid,inv,anchor,new CompoundTag(),storage,depot),title),buf->{buf.writeBlockPos(anchor);buf.writeNbt(state);buf.writeBoolean(storage);});
        if(p.containerMenu instanceof RailGates.GateMenu menu)menu.message=message;
    }
    /** Everything a gate's screen shows (see {@code GateClient}). */
    static CompoundTag state(ServerPlayer p,BlockPos anchor,String message){
        var l=p.serverLevel();var d=FobData.get(l);var n=new CompoundTag();
        var gate=(RailGates.GateBlock)l.getBlockState(anchor).getBlock();
        long id=l.getBlockEntity(anchor) instanceof RailGates.GateEntity be?be.fobId:0;var f=id==0?null:d.byId(id);
        boolean linked=f!=null&&(anchor.equals(f.deliveryGate)||anchor.equals(f.receivingGate));
        n.putLong("gate",anchor.asLong());n.putString("kind",gate.kind.id);n.putBoolean("linked",linked);n.putString("message",message);
        n.putString("signal",signal(l,d,linked?f:null).id());
        if(linked){
            double distance=f.separation();
            n.putString("name",f.name);n.putString("state",f.state.id());n.putBoolean("partner",f.hasRoute());n.putBoolean("commissioned",f.commissioned);
            n.putLong("clock",f.clock);n.putLong("clockMax",RouteRules.CLOCK);n.putBoolean("warn",RouteRules.warning(f.clock));
            n.putInt("distance",(int)Math.round(distance));n.putInt("minDistance",RouteRules.MIN_SEPARATION);n.putLong("baseline",f.baseline);
            long period=f.baseline>0?RouteRules.period(f.baseline):0;
            n.putLong("nextIn",f.nextPassive>0?Math.max(0,f.nextPassive-d.onlineTicks):-1);n.putLong("period",period);
            n.putBoolean("riding",RUNS.containsKey(f.id));
            n.putString("lastKind",f.lastKind);n.putInt("lastItems",f.lastItems);n.putLong("lastAgo",f.lastAt>0?d.onlineTicks-f.lastAt:-1);
            tribute(f,n);
        }
        if(gate.kind==RailGates.Kind.RECEIVING){
            var options=new ListTag();
            for(var o:d.fobs.values())if(o.receivingGate==null||o.receivingGate.equals(anchor)){var row=new CompoundTag();row.putLong("id",o.id);row.putString("name",o.name);row.putString("state",o.state.id());row.putBoolean("current",o.receivingGate!=null);options.add(row);}
            n.put("options",options);
        }
        return n;
    }
    /** The FOB's four loot slots and what they bring, for the gate screens and the FOB screen (0.0.31). */
    static void tribute(FobData.Fob f,CompoundTag n){
        double distance=f.separation();long period=f.baseline>0?RouteRules.period(f.baseline):0;
        n.putString("specialty",f.specialty.id());n.putInt("villagers",Math.min(RouteRules.MAX_WORKERS,f.workers));
        n.putDouble("bonus",RouteRules.distanceBonus(distance));n.putDouble("tripsPerHour",RouteRules.tripsPerHour(period));
        // 0.0.32: the value an hour (every slot together, before research) and the research
        n.putDouble("valuePerHour",RouteRules.perHour(distance,f.workers));
        n.putInt("level",f.level());n.putInt("researchXp",f.researchXp);
        n.putBoolean("full",full(f));int used=0;for(int i=0;i<f.depot().getContainerSize();i++)if(!f.depot().getItem(i).isEmpty())used++;n.putInt("stored",used);n.putInt("depotSlots",f.depot().getContainerSize());
        var slots=new ListTag();
        for(var slot:RouteRules.Slot.values()){
            var row=new CompoundTag();var loot=f.loot(slot);boolean on=f.slotOn[slot.ordinal()];
            row.putString("slot",slot.id());row.putBoolean("on",on);row.putInt("choice",f.choice(slot));row.putString("item",loot.id());
            row.putDouble("perTrip",on?RouteRules.itemsPerTrip(slot,loot,f.yield()):0);row.putBoolean("mayToggle",RouteRules.mayToggle(f.slotOn,slot));
            row.putDouble("research",RouteRules.slotMultiplier(slot,f.level()));
            row.putBoolean("fallback",RouteRules.effectiveChoice(slot,f.specialty,f.choice(slot),f.level())!=Math.floorMod(f.choice(slot),RouteRules.options(slot,f.specialty).size()));
            slots.add(row);
        }
        n.put("slots",slots);
    }
    /** A gate screen button: link a Receiving Gate to a FOB (checked again here). */
    static void button(ServerPlayer p,BlockPos anchor,int id){
        var l=p.serverLevel();if(!(p.containerMenu instanceof RailGates.GateMenu menu)||!menu.stillValid(p))return;
        if(!(l.getBlockState(anchor).getBlock() instanceof RailGates.GateBlock gate)||!(l.getBlockEntity(anchor) instanceof RailGates.GateEntity be))return;
        if(id>=TOGGLE&&id<TOGGLE+RouteRules.Slot.values().length||id>=CHOOSE){choose(p,anchor,be,id);return;}
        if(gate.kind!=RailGates.Kind.RECEIVING||id<LINK||id>=TOGGLE)return;
        var d=FobData.get(l);var options=new ArrayList<FobData.Fob>();
        for(var o:d.fobs.values())if(o.receivingGate==null||o.receivingGate.equals(anchor))options.add(o);
        int index=id-LINK;if(index<0||index>=options.size()){open(p,anchor,"gate.result.gone");return;}
        var target=options.get(index);
        if(anchor.equals(target.receivingGate)){open(p,anchor,"gate.result.same");return;}
        var old=be.fobId==0?null:d.byId(be.fobId);if(old!=null&&anchor.equals(old.receivingGate)){old.receivingGate=null;old.commissioned=false;RUNS.remove(old.id);}
        link(l,d,target,RailGates.Kind.RECEIVING,anchor,be);
        p.closeContainer();open(p,anchor,"gate.result.linked",true);   // a fresh screen: its storage is the new FOB's
    }
    /** Menu buttons of the loot slots: switch a slot on or off, choose its item (0.0.31). */
    static final int TOGGLE=20,CHOOSE=1000,CHOICES=100;
    static int chooseButton(RouteRules.Slot slot,int index){return CHOOSE+slot.ordinal()*CHOICES+index;}
    static void choose(ServerPlayer p,BlockPos anchor,RailGates.GateEntity be,int id){
        var l=p.serverLevel();var d=FobData.get(l);var f=be.fobId==0?null:d.byId(be.fobId);
        if(f==null||!(anchor.equals(f.deliveryGate)||anchor.equals(f.receivingGate))){open(p,anchor,"gate.result.gone");return;}
        String result;
        if(id<CHOOSE){
            var slot=RouteRules.Slot.values()[id-TOGGLE];
            if(!RouteRules.mayToggle(f.slotOn,slot))result="gate.result.last_slot";
            else{f.slotOn[slot.ordinal()]=!f.slotOn[slot.ordinal()];result=f.slotOn[slot.ordinal()]?"gate.result.slot_on":"gate.result.slot_off";}
        }else{
            int slotIndex=(id-CHOOSE)/CHOICES,index=(id-CHOOSE)%CHOICES;
            if(slotIndex<0||slotIndex>=RouteRules.Slot.values().length){open(p,anchor,"");return;}
            var slot=RouteRules.Slot.values()[slotIndex];var options=RouteRules.options(slot,f.specialty);
            if(slot==RouteRules.Slot.EMERALD||index<0||index>=options.size()){open(p,anchor,"");return;}
            if(!RouteRules.unlocked(options.get(index),f.level())){open(p,anchor,"gate.result.locked");return;}   // 0.0.32: research opens A and S
            switch(slot){case MINE->f.mine=index;case FARM->f.farm=index;default->f.special=index;}
            result="gate.result.chosen";
        }
        d.setDirty();log("{}: {} set the tribute: {}",f.name,p.getGameProfile().getName(),result);
        for(var pos:new BlockPos[]{f.deliveryGate,f.receivingGate})if(pos!=null)for(var viewer:l.getServer().getPlayerList().getPlayers())
            if(viewer!=p&&viewer.containerMenu instanceof RailGates.GateMenu m&&m.pos.equals(pos))open(viewer,pos,"");
        Fobs.refreshScreens(l,f);
        open(p,anchor,result);
    }
    /** The FOB shop's second listing: one Supply Delivery Gate per FOB, while it is ACTIVE (paid like any fabricated block). */
    static String buyGate(ServerPlayer p,FobData.Fob f){
        if(!FobRules.working(f.state))return "fob.result.closed";
        if(f.deliveryGate!=null)return "fob.result.has_gate";
        var stack=new ItemStack(EternalDefense.DELIVERY_GATE_ITEM.get());stack.getOrCreateTag().putLong("Fob",f.id);stack.getOrCreateTag().putString("FobName",f.name);
        return WeaponPlatform.transact(Supply.pack(p),Economy.fabrication("supply_delivery_gate"),stack)?   // 0.0.40: at the FOB, the pack alone
            "fob.result.bought.gate":"fob.result.no_materials";
    }

    static void say(ServerPlayer p,String key,Object... args){p.displayClientMessage(Component.translatable("gui.eternal_defense."+key,args),true);}
    private static void sound(ServerPlayer p,net.minecraft.sounds.SoundEvent sound,float pitch){p.playNotifySound(sound,SoundSource.PLAYERS,.7f,pitch);}
    private static void log(String text,Object... args){com.mojang.logging.LogUtils.getLogger().info("[route] "+text,args);}

    static final class RouteEvents {
        @net.minecraftforge.eventbus.api.SubscribeEvent public void loggedOut(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e){
            TRACK.remove(e.getEntity().getUUID());RUNS.values().removeIf(r->r.rider.equals(e.getEntity().getUUID()));
        }
        @net.minecraftforge.eventbus.api.SubscribeEvent public void routeServerStarted(net.minecraftforge.event.server.ServerStartedEvent e){clear();}
    }
}
