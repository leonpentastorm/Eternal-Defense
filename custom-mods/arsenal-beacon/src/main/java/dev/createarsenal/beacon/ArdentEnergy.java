package dev.createarsenal.beacon;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Hostile mobs killed by players sometimes drop Ardent Energy, the currency for the exchange shop and base support. */
final class ArdentEnergy {
    /** Pure drop rule, so it can be tested: {@code roll} is a uniform random number in [0,1). */
    static int amount(double roll,double chance,int looting,double lootingBonus,boolean boss,int bossAmount,float maxHealth){
        if(boss)return Math.max(1,bossAmount);
        double p=Math.max(0,Math.min(1,chance+Math.max(0,looting)*lootingBonus));
        if(roll>=p)return 0;
        return maxHealth>=60?2:1;
    }
    /**
     * Pure (0.0.23): a raider that came in a wave of a vanilla raid (a FOB's raid or any Bad Omen raid) and is not one of the beacon's own raid
     * mobs drops no Ardent Energy, so village raids cannot be farmed. Vanilla sets the wave only when a raider joins a raid, and keeps it after
     * the raider dies; the beacon's own raid mobs never join one (wave 0) and carry the {@code arsenalRaider} tag.
     */
    static boolean vanillaRaider(boolean raider,int wave,boolean ours){return raider&&wave>0&&!ours;}
    /** The standalone edition has no factory to fall back on, so target farming pays a little better there. */
    static final double STANDALONE_DROP_FACTOR=1.3;
    static int balance(Player p){
        int count=0;
        for(var s:p.getInventory().items)if(s.is(ArsenalBeacon.ARDENT_ENERGY.get()))count+=s.getCount();
        for(var s:p.getInventory().offhand)if(s.is(ArsenalBeacon.ARDENT_ENERGY.get()))count+=s.getCount();
        return count;
    }
    /** 0.0.40: the energy a request can reach: the pack, then the Home Zone's Hubs when the request is made inside the Home Zone. */
    static int balance(Supply.Where w){return Supply.count(w,ArsenalBeacon.ARDENT_ENERGY.get());}
    /** Takes energy, from the pack first and then the Home Zone's Hubs ({@link Supply}); creative players pay nothing. False, and nothing taken, if short. */
    static boolean spend(Supply.Where w,int cost){
        if(w.player().isCreative()||cost<=0)return true;
        return InventoryPayment.commit(w,java.util.List.of(new WeaponPlatform.Cost(net.minecraft.world.item.crafting.Ingredient.of(ArsenalBeacon.ARDENT_ENERGY.get()),cost)),ItemStack.EMPTY);
    }
    @SubscribeEvent public void drops(LivingDropsEvent event){
        var entity=event.getEntity();
        if(!(entity.level() instanceof ServerLevel level)||!(entity instanceof Enemy))return;
        if(HuntWarband.hunter(entity))return;   // a hunt pays once, into the reward chest: its mobs drop no Ardent Energy
        if(entity instanceof net.minecraft.world.entity.raid.Raider r&&vanillaRaider(true,r.getWave(),r.getPersistentData().getBoolean("arsenalRaider")))return;
        boolean credited=entity.getKillCredit() instanceof Player||event.getSource().getEntity() instanceof Player||entity.getPersistentData().getBoolean("arsenalRaider");
        if(!credited)return;
        boolean boss=entity.getPersistentData().getBoolean("arsenalBoss")||entity.getType().is(Tags.EntityTypes.BOSSES);
        int n=amount(level.random.nextDouble(),ArsenalConfig.ENERGY_CHANCE.get()*(BuildFlavor.STANDALONE?STANDALONE_DROP_FACTOR:1.0),event.getLootingLevel(),ArsenalConfig.LOOTING_BONUS.get(),boss,ArsenalConfig.BOSS_ENERGY.get(),entity.getMaxHealth());
        if(n<=0)return;
        var drop=new ItemEntity(level,entity.getX(),entity.getY()+0.3,entity.getZ(),new ItemStack(ArsenalBeacon.ARDENT_ENERGY.get(),n));
        drop.setDeltaMovement((level.random.nextDouble()-.5)*.2,.25,(level.random.nextDouble()-.5)*.2);
        event.getDrops().add(drop);
    }
}
