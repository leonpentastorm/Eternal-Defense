package dev.eternaldefense.beacon;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.*;

/** The Loadout Station's print: walk in, the 80-tick cycle with the kit put on at tick 40, walk out. Server side. */
final class LoadoutPrint {
    private LoadoutPrint(){}

    /** One print in progress (not saved: a restarted server starts with every station free). */
    static final class Session {
        final UUID machine,player;final String name;final BlockPos anchor;final Direction facing;final int slot;
        /** The coin kind this print wants for the main hand and the Hub (count 1), or empty. */
        final ItemStack wanted;
        final double[] from,stand,exit;final LoadoutRules.Timeline timeline;final long start;final float standYaw;
        boolean swapped;int corrections;String stowedTo="";
        Session(UUID machine,ServerPlayer p,BlockPos anchor,Direction facing,int slot,ItemStack wanted,double[] from,double[] stand,double[] exit,LoadoutRules.Timeline timeline,long start){
            this.machine=machine;this.player=p.getUUID();this.name=p.getGameProfile().getName();this.anchor=anchor;this.facing=facing;this.slot=slot;this.wanted=wanted;
            this.from=from;this.stand=stand;this.exit=exit;this.timeline=timeline;this.start=start;this.standYaw=LoadoutModel.standingYaw(facing);
        }
        int elapsed(long now){return (int)(now-start);}
        double[] expected(long now){return LoadoutRules.position(timeline,elapsed(now),from,stand,exit);}
    }
    private static final Map<UUID,Session> BY_MACHINE=new LinkedHashMap<>(),BY_PLAYER=new HashMap<>();

    static Session of(UUID player){return BY_PLAYER.get(player);}
    static Session at(UUID machine){return BY_MACHINE.get(machine);}
    /** True while p is held by a print (walking in, inside, walking out). */
    static boolean holding(Player p){if(p==null||p.level().isClientSide)return false;var s=BY_PLAYER.get(p.getUUID());return s!=null&&s.timeline.holding(s.elapsed(now(p.getServer())));}
    private static long now(MinecraftServer server){return server==null?0:server.overworld().getGameTime();}
    static void say(ServerPlayer p,String key,ChatFormatting color,Object... args){p.displayClientMessage(Component.translatable("gui.eternal_defense.loadout."+key,args).withStyle(color),true);}

    // Starting a print
    /** Starts a print of coin slot slot for p, or tells them why not; nothing changes on a refusal. */
    static void request(ServerPlayer p,LoadoutData.Machine m,int slot){
        var level=p.serverLevel();String problem=problem(p,m,slot);
        if(problem!=null){
            var busy=BY_MACHINE.get(m.id);long left=LoadoutRules.secondsLeft(LoadoutData.get(level).players.readyAt(p.getUUID()),now(p.server));
            say(p,"refused."+problem,ChatFormatting.RED,busy==null?"":busy.name,left);
            p.playNotifySound(net.minecraft.sounds.SoundEvents.VILLAGER_NO,SoundSource.PLAYERS,.6f,1.2f);return;
        }
        double[] from=vec(p.position()),stand=vec(LoadoutStation.world(m.anchor,m.facing,LoadoutModel.STAND_X,LoadoutModel.STAND_Y,LoadoutModel.STAND_Z));
        var e=LoadoutRules.exitPoint();double[] exit=vec(LoadoutStation.world(m.anchor,m.facing,e[0],e[1],e[2]));
        var tl=LoadoutRules.timeline(LoadoutRules.distance(from,stand),LoadoutRules.distance(stand,exit));
        long start=now(p.server);
        var s=new Session(m.id,p,m.anchor,m.facing,slot,wanted(p,m,slot),from,stand,exit,tl,start);
        BY_MACHINE.put(m.id,s);BY_PLAYER.put(p.getUUID(),s);
        p.closeContainer();p.stopUsingItem();
        if(level.getBlockEntity(m.anchor) instanceof LoadoutStation.StationEntity be)be.begin(p,start,tl,from,stand,exit);
        sound(level,m.anchor,DefenseConfig.LOADOUT_DOOR_SOUND.get(),1f,.8f);
        com.mojang.logging.LogUtils.getLogger().info("[Loadout Station] {} starts a print of slot {} at {} ({} + {} + {} + {} ticks)",s.name,slot+1,m.anchor.toShortString(),tl.entry(),LoadoutModel.CYCLE_TICKS,tl.exitWalk(),LoadoutRules.CLOSE_TICKS);
    }
    /** Null when p may start a print of slot slot at machine m now; otherwise a key suffix under loadout.refused. */
    static String problem(ServerPlayer p,LoadoutData.Machine m,int slot){
        var level=p.serverLevel();
        String lock=LoadoutRules.lockProblem(BY_MACHINE,BY_PLAYER,m.id,p.getUUID());if(lock!=null)return lock;
        if(slot<0||slot>=LoadoutRules.COIN_SLOTS)return "no_coin";
        if(!p.isAlive()||p.isSpectator()||p.isSleeping()||p.isPassenger()||p.isFallFlying())return "not_now";
        if(level.dimension()!=m.dimension||BaseZone.problem(level,m.anchor)!=null)return "outside";
        var feet=LoadoutStation.local(m.anchor,m.facing,p.position());
        String approach=LoadoutRules.approach(feet,LoadoutRules.leafReach());if(approach!=null)return approach;
        if(!pathClear(level,m,p.position()))return "path";
        if(!exitClear(level,m))return "exit";
        if(LoadoutRules.secondsLeft(LoadoutData.get(level).players.readyAt(p.getUUID()),now(p.server))>0)return "cooldown";
        if(source(p,m,slot,wanted(p,m,slot))==LoadoutRules.CoinSource.NONE)return "no_coin";
        if(!roomToStow(p,m))return "no_room";
        return null;
    }
    private static double[] vec(Vec3 v){return new double[]{v.x,v.y,v.z};}
    private static Vec3 vec(double[] v){return new Vec3(v[0],v[1],v[2]);}
    /** A player's hitbox at feet blocks a block that is not part of station m. */
    private static boolean blocked(ServerLevel level,LoadoutData.Machine m,double[] feet){
        double half=LoadoutModel.HITBOX_WIDTH/32;var box=new AABB(feet[0]-half,feet[1]+.01,feet[2]-half,feet[0]+half,feet[1]+LoadoutModel.HITBOX_HEIGHT/16,feet[2]+half);
        for(var pos:BlockPos.betweenClosed(BlockPos.containing(box.minX,box.minY,box.minZ),BlockPos.containing(box.maxX,box.maxY,box.maxZ))){
            if(!level.hasChunkAt(pos))return true;
            var state=level.getBlockState(pos);
            if(LoadoutStation.isStation(state)&&LoadoutStation.anchor(level,pos).equals(m.anchor))continue;
            var shape=state.getCollisionShape(level,pos);
            if(!shape.isEmpty()&&shape.move(pos.getX(),pos.getY(),pos.getZ()).toAabbs().stream().anyMatch(box::intersects))return true;
        }
        return false;
    }
    /** The straight walk from where the player stands to the standing point is free outside the machine. */
    static boolean pathClear(ServerLevel level,LoadoutData.Machine m,Vec3 from){
        var stand=LoadoutStation.world(m.anchor,m.facing,LoadoutModel.STAND_X,LoadoutModel.STAND_Y,LoadoutModel.STAND_Z);
        return LoadoutRules.clear(vec(from),vec(stand),p->blocked(level,m,p));
    }
    /** The walk out to the cell in front of the doors is free. */
    static boolean exitClear(ServerLevel level,LoadoutData.Machine m){
        var stand=LoadoutStation.world(m.anchor,m.facing,LoadoutModel.STAND_X,LoadoutModel.STAND_Y,LoadoutModel.STAND_Z);
        var e=LoadoutRules.exitPoint();var exit=LoadoutStation.world(m.anchor,m.facing,e[0],e[1],e[2]);
        return LoadoutRules.clear(vec(stand),vec(exit),p->blocked(level,m,p));
    }

    // The coin
    /** The kind this print wants: the slot's remembered kind, else the coin in the main hand, else none. */
    static ItemStack wanted(ServerPlayer p,LoadoutData.Machine m,int slot){
        if(slot>=0&&slot<LoadoutRules.COIN_SLOTS&&!m.kind(slot).isEmpty())return m.kind(slot).copy();
        var hand=p.getMainHandItem();return hand.is(EternalDefense.LOADOUT_COIN.get())?hand.copyWithCount(1):ItemStack.EMPTY;
    }
    private static boolean same(ItemStack a,ItemStack b){return !a.isEmpty()&&!b.isEmpty()&&ItemStack.isSameItemSameTags(a,b);}
    static LoadoutRules.CoinSource source(ServerPlayer p,LoadoutData.Machine m,int slot,ItemStack wanted){
        int stock=slot>=0&&slot<LoadoutRules.COIN_SLOTS?m.coins[slot].getCount():0;
        var hand=p.getMainHandItem();boolean inHand=hand.is(EternalDefense.LOADOUT_COIN.get())&&(wanted.isEmpty()||same(hand,wanted));
        return LoadoutRules.source(stock,inHand,!wanted.isEmpty()&&hubCoin(p,m,wanted)!=null);
    }
    /** A Hub of the Home Zone holding a coin identical to wanted, or null. */
    static HubData.Hub hubCoin(ServerPlayer p,LoadoutData.Machine m,ItemStack wanted){
        var key=HubStorage.Key.of(wanted);for(var h:Supply.hubs(Supply.at(p,m.anchor)))if(h.storage.count(key)>0)return h;return null;
    }

    // Stowing what the player wears and holds
    /** The 14 kit slots of a player: armor (feet first), the off hand, the hotbar. */
    static List<ItemStack> worn(Player p){
        var out=new ArrayList<ItemStack>();var inv=p.getInventory();
        for(int i=0;i<LoadoutRules.ARMOR;i++)out.add(inv.armor.get(i));out.add(inv.offhand.get(0));for(int i=0;i<LoadoutRules.HOTBAR;i++)out.add(inv.items.get(i));return out;
    }
    static void wear(Player p,int i,ItemStack stack){
        var inv=p.getInventory();
        if(i<LoadoutRules.ARMOR)inv.armor.set(i,stack);else if(i==LoadoutRules.OFFHAND_SLOT)inv.offhand.set(0,stack);else inv.items.set(i-LoadoutRules.HOTBAR_FIRST,stack);
    }
    /** Empty slots the stow may use: the Home Zone's Hubs, then the station's locker. */
    private static int room(ServerPlayer p,LoadoutData.Machine m){
        int free=0;for(var h:Supply.hubs(Supply.at(p,m.anchor)))free+=HubStorage.SLOTS-h.storage.usedSlots();
        for(int i=0;i<m.locker.getContainerSize();i++)if(m.locker.getItem(i).isEmpty())free++;
        return free;
    }
    /** Every worn stack fits in a slot of its own (merging can only leave more room). */
    static boolean roomToStow(ServerPlayer p,LoadoutData.Machine m){int n=0;for(var s:worn(p))if(!s.isEmpty())n++;return n<=room(p,m);}
    /** Stows the stacks: the Hubs first (nearest first), what is left in the locker. Returns where it went: hub, locker, both or none. */
    private static String stow(ServerPlayer p,LoadoutData.Machine m,List<ItemStack> stacks){
        boolean hub=false,locker=false;var hubs=Supply.hubs(Supply.at(p,m.anchor));
        for(var s:stacks){
            var rest=s;
            for(var h:hubs){if(rest.isEmpty())break;var left=h.storage.insert(rest,false);if(left.getCount()<rest.getCount())hub=true;rest=left;}
            if(!rest.isEmpty()){var left=m.locker.addItem(rest);if(left.getCount()<rest.getCount())locker=true;rest=left;}
            if(!rest.isEmpty()){EternalDefense.give(p,rest);com.mojang.logging.LogUtils.getLogger().warn("[Loadout Station] no room left for {}: handed back to {}",rest,p.getGameProfile().getName());}
        }
        return hub&&locker?"both":hub?"hub":locker?"locker":"none";
    }

    // The tick-40 swap, in one go
    /** Takes the coin, stows what the player wears and holds, puts the kit on. Null when done, otherwise why not (nothing changed). */
    private static String swap(MinecraftServer server,Session s){
        var p=server.getPlayerList().getPlayer(s.player);var level=server.overworld();var m=LoadoutData.get(level).machine(s.machine);
        boolean machine=m!=null&&m.placed&&level.getBlockEntity(s.anchor) instanceof LoadoutStation.StationEntity;
        var src=p==null||m==null?LoadoutRules.CoinSource.NONE:source(p,m,s.slot,s.wanted);
        String problem=LoadoutRules.swapProblem(p!=null,p!=null&&p.isAlive(),p!=null&&p.level()==level,machine,src!=LoadoutRules.CoinSource.NONE,p!=null&&m!=null&&roomToStow(p,m));
        if(problem!=null)return problem;
        // The coin
        ItemStack coin;List<Supply.Pull> pulls=List.of();
        switch(src){
            case SLOT->coin=m.takeOne(s.slot);
            case HAND->coin=p.getMainHandItem().split(1);
            default->{var hub=hubCoin(p,m,s.wanted);var key=HubStorage.Key.of(s.wanted);if(hub==null||hub.storage.extract(key,1,false)<1)return "no_coin";coin=s.wanted.copyWithCount(1);pulls=List.of(new Supply.Pull(hub,key,1));}
        }
        var kit=LoadoutCoin.kit(coin);
        // Stow, then wear
        var old=new ArrayList<ItemStack>();for(var w:worn(p))if(!w.isEmpty())old.add(w.copy());
        for(int i=0;i<LoadoutRules.KIT_SLOTS;i++)wear(p,i,ItemStack.EMPTY);
        s.stowedTo=stow(p,m,old);
        for(int i=0;i<LoadoutRules.KIT_SLOTS;i++)wear(p,i,kit.get(i).copy());
        p.getInventory().setChanged();p.inventoryMenu.broadcastChanges();
        HubNotices.pulled(p,pulls);
        com.mojang.logging.LogUtils.getLogger().info("[Loadout Station] {} printed a kit of {} stacks from the {} ({} stacks stowed: {})",s.name,LoadoutRules.kitItems(coin.getTag()),src.name().toLowerCase(Locale.ROOT),old.size(),s.stowedTo);
        return null;
    }

    // Each server tick
    static void tick(MinecraftServer server){
        if(BY_MACHINE.isEmpty())return;
        long now=now(server);var level=server.overworld();
        for(var s:List.copyOf(BY_MACHINE.values())){
            int t=s.elapsed(now);var tl=s.timeline;var p=server.getPlayerList().getPlayer(s.player);
            var be=level.isLoaded(s.anchor)&&level.getBlockEntity(s.anchor) instanceof LoadoutStation.StationEntity e?e:null;
            if(be==null){interrupt(server,s,"broken");continue;}
            if(tl.holding(t)){
                if(p==null){interrupt(server,s,"logout");continue;}
                if(!p.isAlive()){interrupt(server,s,"death");continue;}
                if(p.level()!=level){interrupt(server,s,"dimension");continue;}
                hold(p,s,now);
            }
            if(t==tl.cycleStart())sound(level,s.anchor,DefenseConfig.LOADOUT_DOOR_SOUND.get(),.9f,.7f);
            if(t==tl.cycleStart()+LoadoutModel.STEAM_AT){sound(level,s.anchor,DefenseConfig.LOADOUT_STEAM_SOUND.get(),1f,.9f);sound(level,s.anchor,DefenseConfig.LOADOUT_DOOR_SOUND.get(),.9f,.8f);}
            if(t==tl.swapTick()&&!s.swapped){
                String problem=swap(server,s);
                if(problem!=null){interrupt(server,s,problem);continue;}
                s.swapped=true;sound(level,s.anchor,DefenseConfig.LOADOUT_STEAM_SOUND.get(),.7f,1.4f);
            }
            if(t==tl.closeStart()){
                sound(level,s.anchor,DefenseConfig.LOADOUT_DOOR_SOUND.get(),.9f,.7f);
                if(p!=null){say(p,"printed."+(s.stowedTo.isEmpty()?"none":s.stowedTo),ChatFormatting.GREEN);}
            }
            if(t>=tl.end())finish(server,s,be);
        }
    }
    /** Keeps a held player on the path: still, and put back when they drift. */
    private static void hold(ServerPlayer p,Session s,long now){
        p.setDeltaMovement(Vec3.ZERO);p.fallDistance=0;p.resetLastActionTime();
        double drift=LoadoutRules.drift(s.timeline,s.elapsed(now),new double[]{p.getX(),p.getY(),p.getZ()},s.from,s.stand,s.exit);
        if(drift>LoadoutRules.DRIFT){
            var at=s.expected(now);s.corrections++;float yaw=LoadoutRules.yaw(s.timeline,s.elapsed(now),s.from,s.stand,s.standYaw);
            p.connection.teleport(at[0],at[1],at[2],yaw,p.getXRot());
            com.mojang.logging.LogUtils.getLogger().info("[Loadout Station] {} was {} blocks off the path at tick {}: put back",s.name,String.format(Locale.ROOT,"%.2f",drift),s.elapsed(now));
        }
    }
    private static void finish(MinecraftServer server,Session s,LoadoutStation.StationEntity be){
        var data=LoadoutData.get(server.overworld());long now=now(server);
        data.players.setReadyAt(s.player,LoadoutRules.readyAt(now,DefenseConfig.LOADOUT_COOLDOWN.get()));data.setDirty();
        be.end(now,0);remove(s);
        com.mojang.logging.LogUtils.getLogger().info("[Loadout Station] {}'s print ended after {} ticks ({} position corrections)",s.name,s.elapsed(now),s.corrections);
    }
    /** Something stopped the print: before the swap nothing was consumed; the player is let go outside the doors. */
    static void interrupt(MinecraftServer server,Session s,String reason){
        long now=now(server);var level=server.overworld();var p=server.getPlayerList().getPlayer(s.player);
        float door=level.getBlockEntity(s.anchor) instanceof LoadoutStation.StationEntity be?be.door(now,0):0;
        if(level.getBlockEntity(s.anchor) instanceof LoadoutStation.StationEntity be)be.end(now,door);
        remove(s);
        if(p!=null&&p.isAlive()&&p.level()==level&&s.elapsed(now)>0&&s.timeline.holding(s.elapsed(now))){var e=s.exit;p.connection.teleport(e[0],e[1],e[2],s.standYaw,0);}
        if(p!=null&&!s.swapped)say(p,"stopped."+reason,ChatFormatting.RED);
        com.mojang.logging.LogUtils.getLogger().info("[Loadout Station] {}'s print stopped at tick {}: {} ({})",s.name,s.elapsed(now),reason,s.swapped?"kit already on":"nothing consumed");
    }
    private static void remove(Session s){BY_MACHINE.remove(s.machine);BY_PLAYER.remove(s.player);}
    /** The station of m was removed. */
    static void machineGone(ServerLevel level,LoadoutData.Machine m){var s=BY_MACHINE.get(m.id);if(s!=null)interrupt(level.getServer(),s,"broken");}
    static void sound(ServerLevel level,BlockPos at,String id,float volume,float pitch){
        var rl=ResourceLocation.tryParse(id);if(rl==null)return;
        level.playSound(null,at,SoundEvent.createVariableRangeEvent(rl),SoundSource.BLOCKS,volume,pitch);
    }

    /** The events that keep a held player still and safe, and stop a print when they leave. */
    public static final class Events {
        @SubscribeEvent public void printTick(TickEvent.ServerTickEvent e){if(e.phase==TickEvent.Phase.END)LoadoutPrint.tick(net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer());}
        @SubscribeEvent public void printStopped(ServerStoppedEvent e){BY_MACHINE.clear();BY_PLAYER.clear();LoadoutCall.clear();}
        @SubscribeEvent public void printLogout(PlayerEvent.PlayerLoggedOutEvent e){
            var s=BY_PLAYER.get(e.getEntity().getUUID());if(s==null||!(e.getEntity() instanceof ServerPlayer p))return;
            boolean inside=s.timeline.holding(s.elapsed(now(p.server)));
            interrupt(p.server,s,"logout");
            // Saved where they will wake up: outside the doors, never inside the machine
            if(inside&&p.level()==p.server.overworld())p.moveTo(s.exit[0],s.exit[1],s.exit[2],s.standYaw,0);
        }
        @SubscribeEvent public void printDeath(LivingDeathEvent e){var s=BY_PLAYER.get(e.getEntity().getUUID());if(s!=null&&e.getEntity() instanceof ServerPlayer p)interrupt(p.server,s,"death");}
        @SubscribeEvent public void printDimension(PlayerEvent.PlayerChangedDimensionEvent e){var s=BY_PLAYER.get(e.getEntity().getUUID());if(s!=null&&e.getEntity() instanceof ServerPlayer p)interrupt(p.server,s,"dimension");}
        @SubscribeEvent public void printHurt(LivingAttackEvent e){if(e.getEntity() instanceof Player p&&!p.level().isClientSide&&holding(p)&&!e.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY))e.setCanceled(true);}
        @SubscribeEvent public void printAttack(AttackEntityEvent e){if(holding(e.getEntity()))e.setCanceled(true);}
        @SubscribeEvent public void printUseBlock(PlayerInteractEvent.RightClickBlock e){if(!e.getLevel().isClientSide&&holding(e.getEntity()))e.setCanceled(true);}
        @SubscribeEvent public void printUseItem(PlayerInteractEvent.RightClickItem e){if(!e.getLevel().isClientSide&&holding(e.getEntity()))e.setCanceled(true);}
        @SubscribeEvent public void printHit(PlayerInteractEvent.LeftClickBlock e){if(!e.getLevel().isClientSide&&holding(e.getEntity()))e.setCanceled(true);}
        @SubscribeEvent public void printTouch(PlayerInteractEvent.EntityInteract e){if(!e.getLevel().isClientSide&&holding(e.getEntity()))e.setCanceled(true);}
        @SubscribeEvent public void printTouchAt(PlayerInteractEvent.EntityInteractSpecific e){if(!e.getLevel().isClientSide&&holding(e.getEntity()))e.setCanceled(true);}
        @SubscribeEvent public void printBreaking(BlockEvent.BreakEvent e){if(holding(e.getPlayer()))e.setCanceled(true);}
        @SubscribeEvent public void printPlacing(BlockEvent.EntityPlaceEvent e){if(e.getEntity() instanceof Player p&&!p.level().isClientSide&&holding(p))e.setCanceled(true);}
        /** A drop is refused and the stack goes back where it came from. */
        @SubscribeEvent public void printToss(ItemTossEvent e){
            if(e.getPlayer().level().isClientSide||!holding(e.getPlayer()))return;
            e.setCanceled(true);var stack=e.getEntity().getItem().copy();if(!e.getPlayer().getInventory().add(stack))e.getPlayer().drop(stack,true,false);
        }
    }
}
