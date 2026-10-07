package dev.createarsenal.beacon;

import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.*;
import java.util.*;
import java.util.function.Supplier;

/** Server-owned field timer and meal modifiers. Offline time never consumes a meal; death clears it. */
final class PlayerMeals extends SavedData {
    static final String NAME="arsenal_beacon_meals_v1";
    static final class State {
        final MealData meal;int remaining;boolean home;boolean applied;
        State(MealData meal,int remaining){this.meal=meal;this.remaining=remaining;}
    }
    final Map<UUID,State> players=new HashMap<>();
    static PlayerMeals get(ServerLevel level){return level.getServer().overworld().getDataStorage().computeIfAbsent(PlayerMeals::load,PlayerMeals::new,NAME);}
    static PlayerMeals load(CompoundTag n){
        var d=new PlayerMeals();for(var t:n.getList("Players",Tag.TAG_COMPOUND)){var c=(CompoundTag)t;var meal=MealData.load(c.getCompound("Meal"));int ticks=c.getInt("Remaining");if(c.hasUUID("Player")&&meal!=null&&ticks>0)d.players.put(c.getUUID("Player"),new State(meal,Math.min(ticks,meal.duration())));}return d;
    }
    @Override public CompoundTag save(CompoundTag n){var list=new ListTag();players.forEach((id,s)->{var c=new CompoundTag();c.putUUID("Player",id);c.put("Meal",s.meal.save());c.putInt("Remaining",s.remaining);list.add(c);});n.put("Players",list);return n;}
    static Attribute attribute(MealRules.Effect e){return switch(e){case VITALITY->Attributes.MAX_HEALTH;case FORTITUDE->Attributes.ARMOR;case STEADINESS->Attributes.KNOCKBACK_RESISTANCE;case MOBILITY->Attributes.MOVEMENT_SPEED;default->null;};}
    static UUID modifier(MealRules.Effect e){return UUID.nameUUIDFromBytes((ArsenalBeacon.ID+":meal/"+e.id).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    static AttributeInstance instance(ServerPlayer p,MealRules.Effect e){var attribute=attribute(e);return attribute==null?null:p.getAttribute(attribute);}
    static void removeModifiers(ServerPlayer p){for(var e:MealRules.Effect.values()){var a=instance(p,e);if(a!=null)a.removeModifier(modifier(e));}p.setHealth(Math.min(p.getHealth(),p.getMaxHealth()));}
    static void applyModifiers(ServerPlayer p,State s){
        // Remove our UUIDs only; brewing and other mods keep ownership of their bonuses.
        for(var e:MealRules.Effect.values()){var a=instance(p,e);if(a!=null)a.removeModifier(modifier(e));}
        for(var b:s.meal.bonuses()){var a=instance(p,b.effect());if(a!=null)a.addTransientModifier(new AttributeModifier(modifier(b.effect()),"Prepared meal",s.home?b.home():b.field(),b.effect()==MealRules.Effect.MOBILITY?AttributeModifier.Operation.MULTIPLY_BASE:AttributeModifier.Operation.ADDITION));}
        p.setHealth(Math.min(p.getHealth(),p.getMaxHealth()));s.applied=true;
    }
    static void eat(ServerPlayer p,MealData meal){var d=get(p.serverLevel());var s=new State(meal,meal.duration());s.home=BaseZone.problem(p.level(),p.blockPosition())==null;d.players.put(p.getUUID(),s);applyModifiers(p,s);d.setDirty();sync(p,s);}
    static void clear(ServerPlayer p){var d=get(p.serverLevel());d.players.remove(p.getUUID());removeModifiers(p);d.setDirty();sync(p,null);}
    static void refresh(ServerPlayer p){var s=get(p.serverLevel()).players.get(p.getUUID());if(s!=null){s.home=BaseZone.problem(p.level(),p.blockPosition())==null;applyModifiers(p,s);}else removeModifiers(p);sync(p,s);}
    static void tick(ServerPlayer p){
        var d=get(p.serverLevel());var s=d.players.get(p.getUUID());if(s==null)return;
        boolean home=BaseZone.problem(p.level(),p.blockPosition())==null,changed=s.home!=home||!s.applied;s.home=home;
        s.remaining=MealRules.remaining(s.remaining,home);
        if(!home)d.setDirty();
        if(s.remaining==0){clear(p);return;}if(changed)applyModifiers(p,s);
        if(changed||p.tickCount%20==0){d.setDirty();sync(p,s);}
    }
    static void sync(ServerPlayer p,State s){BeaconNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new Sync(s==null?new CompoundTag():s.meal.save(),s==null?0:s.remaining,s!=null&&s.home));}
    record Sync(CompoundTag meal,int remaining,boolean home){
        static void encode(Sync s,FriendlyByteBuf b){b.writeNbt(s.meal);b.writeVarInt(s.remaining);b.writeBoolean(s.home);}
        static Sync decode(FriendlyByteBuf b){var n=b.readNbt();return new Sync(n==null?new CompoundTag():n,b.readVarInt(),b.readBoolean());}
        static void handle(Sync s,Supplier<NetworkEvent.Context> ctx){ctx.get().enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->MealClient.receive(s)));ctx.get().setPacketHandled(true);}
    }
    static final class MealEvents {
        @SubscribeEvent public void tick(TickEvent.PlayerTickEvent e){if(e.phase==TickEvent.Phase.END&&e.player instanceof ServerPlayer p&&p.isAlive())PlayerMeals.tick(p);}
        @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p)refresh(p);}
        @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p)get(p.serverLevel()).setDirty();}
        @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST) public void death(LivingDeathEvent e){if(e.getEntity() instanceof ServerPlayer p)clear(p);}
        @SubscribeEvent public void respawn(PlayerEvent.PlayerRespawnEvent e){if(e.getEntity() instanceof ServerPlayer p)refresh(p);}
        @SubscribeEvent public void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p)refresh(p);}
    }
}
