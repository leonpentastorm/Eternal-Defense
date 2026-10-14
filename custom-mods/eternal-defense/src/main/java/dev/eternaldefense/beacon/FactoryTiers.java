package dev.eternaldefense.beacon;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.List;
import java.util.Map;

/** World-wide factory tier: hostile mobs grow tougher once a player holds a better receiver. */
final class FactoryTiers {
    private FactoryTiers(){}
    static final Map<String,Integer> MILESTONES=Map.of("brass_receiver",1,"hardened_receiver",2,"exotic_receiver",3);
    static final int MAX=3;

    /** Pure: the scaled max health of a mob with this base health at a tier. */
    static double health(double base,double multiplier,int ordinaryCap,int eliteCap,int bossCap){
        int cap=base>=150?bossCap:base>=50?eliteCap:ordinaryCap;
        return Math.max(base,Math.min(cap,base*multiplier));
    }

    static int tier(MinecraftServer server){return Data.get(server).tier;}

    static final class Data extends SavedData {
        static final String NAME="eternal_defense_factory_tier";
        int tier;
        static Data load(CompoundTag n){var d=new Data();d.tier=Math.max(0,Math.min(MAX,n.getInt("tier")));return d;}
        @Override public CompoundTag save(CompoundTag n){n.putInt("tier",tier);return n;}
        static Data get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(Data::load,Data::new,NAME);}
    }

    static final class Events {
        @SubscribeEvent public void tick(TickEvent.ServerTickEvent e){
            if(e.phase!=TickEvent.Phase.END||e.getServer().getTickCount()%20!=0)return;
            var data=Data.get(e.getServer());if(data.tier>=MAX)return;
            int best=data.tier;
            for(ServerPlayer p:e.getServer().getPlayerList().getPlayers())for(ItemStack stack:p.getInventory().items)best=Math.max(best,milestone(stack));
            if(best<=data.tier)return;
            data.tier=best;data.setDirty();
            e.getServer().getPlayerList().broadcastSystemMessage(Component.translatable("gui.eternal_defense.factory_tier",best+1).withStyle(ChatFormatting.GOLD),false);
        }
        @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent e){
            if(!(e.getEntity() instanceof ServerPlayer p)||p.getPersistentData().getBoolean("eternalWelcomed"))return;
            p.getPersistentData().putBoolean("eternalWelcomed",true);
            p.sendSystemMessage(Component.translatable("gui.eternal_defense.welcome").withStyle(ChatFormatting.GOLD));
        }
        @SubscribeEvent public void join(EntityJoinLevelEvent e){
            if(e.getLevel().isClientSide()||!(e.getEntity() instanceof LivingEntity mob)||mob instanceof Player)return;
            var data=mob.getPersistentData();
            if(data.getBoolean("eternalScaled")||data.getBoolean("eternalRaider")||mob.getType().getCategory().isFriendly())return;
            var server=mob.getServer();if(server==null)return;
            var hp=mob.getAttribute(Attributes.MAX_HEALTH);if(hp==null)return;
            int tier=Math.max(0,Math.min(MAX,tier(server)));
            double base=hp.getBaseValue();
            hp.setBaseValue(health(base,value(DefenseConfig.TIER_HEALTH.get(),tier),DefenseConfig.ORDINARY_CAP.get(),DefenseConfig.ELITE_CAP.get(),DefenseConfig.BOSS_CAP.get()));
            mob.setHealth(mob.getMaxHealth());
            var attack=mob.getAttribute(Attributes.ATTACK_DAMAGE);if(attack!=null)attack.setBaseValue(attack.getBaseValue()*value(DefenseConfig.TIER_DAMAGE.get(),tier));
            data.putBoolean("eternalScaled",true);
        }
    }

    private static int milestone(ItemStack stack){
        if(stack.isEmpty())return 0;var id=BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id.getNamespace().equals(EternalDefense.ID)?MILESTONES.getOrDefault(id.getPath(),0):0;
    }
    private static double value(List<? extends Double> list,int tier){return list.isEmpty()?1:list.get(Math.min(tier,list.size()-1));}
}
