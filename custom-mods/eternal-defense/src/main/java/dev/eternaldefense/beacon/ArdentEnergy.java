package dev.eternaldefense.beacon;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Hostile mobs killed by players sometimes drop Ardent Energy */
final class ArdentEnergy {
    /** Drop rule; roll is uniform in [0, 1). */
    static int amount(double roll,double chance,int looting,double lootingBonus,boolean boss,int bossAmount,float maxHealth){
        if(boss)return Math.max(1,bossAmount);
        double p=Math.max(0,Math.min(1,chance+Math.max(0,looting)*lootingBonus));
        if(roll>=p)return 0;
        return maxHealth>=60?2:1;
    }
    /** Pure: a raider that came in a wave of a vanilla raid */
    static boolean vanillaRaider(boolean raider,int wave,boolean ours){return raider&&wave>0&&!ours;}
    static int balance(Player p){
        int count=0;
        for(var s:p.getInventory().items)if(s.is(EternalDefense.ARDENT_ENERGY.get()))count+=s.getCount();
        for(var s:p.getInventory().offhand)if(s.is(EternalDefense.ARDENT_ENERGY.get()))count+=s.getCount();
        return count;
    }
    /** The energy a request can reach: the pack, then the Home Zone's Hubs when the request is made inside the Home */
    static int balance(Supply.Where w){return Supply.count(w,EternalDefense.ARDENT_ENERGY.get());}
    /** Takes energy, from the pack first and then the Home Zone's Hubs (Supply); creative players pay nothing. */
    static boolean spend(Supply.Where w,int cost){
        if(w.player().isCreative()||cost<=0)return true;
        return InventoryPayment.commit(w,java.util.List.of(new WeaponPlatform.Cost(net.minecraft.world.item.crafting.Ingredient.of(EternalDefense.ARDENT_ENERGY.get()),cost)),ItemStack.EMPTY);
    }
    @SubscribeEvent public void drops(LivingDropsEvent event){
        var entity=event.getEntity();
        if(!(entity.level() instanceof ServerLevel level)||!(entity instanceof Enemy))return;
        if(HuntWarband.hunter(entity))return;   // A hunt pays once, into the reward chest: its mobs drop no Ardent Energy
        if(entity instanceof net.minecraft.world.entity.raid.Raider r&&vanillaRaider(true,r.getWave(),r.getPersistentData().getBoolean("eternalRaider")))return;
        boolean credited=entity.getKillCredit() instanceof Player||event.getSource().getEntity() instanceof Player||entity.getPersistentData().getBoolean("eternalRaider");
        if(!credited)return;
        boolean boss=entity.getPersistentData().getBoolean("eternalBoss")||entity.getType().is(Tags.EntityTypes.BOSSES);
        int n=amount(level.random.nextDouble(),DefenseConfig.ENERGY_CHANCE.get(),event.getLootingLevel(),DefenseConfig.LOOTING_BONUS.get(),boss,DefenseConfig.BOSS_ENERGY.get(),entity.getMaxHealth());
        if(n<=0)return;
        var drop=new ItemEntity(level,entity.getX(),entity.getY()+0.3,entity.getZ(),new ItemStack(EternalDefense.ARDENT_ENERGY.get(),n));
        drop.setDeltaMovement((level.random.nextDouble()-.5)*.2,.25,(level.random.nextDouble()-.5)*.2);
        event.getDrops().add(drop);
    }
}
