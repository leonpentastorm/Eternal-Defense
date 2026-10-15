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

/** Ardent Energy from kills: red-marked mobs (raids, operations) sometimes, elites always, big bosses one */
final class ArdentEnergy {
    /** Max health from which a boss outside a raid drops one */
    static final float BOSS_HEALTH=300;
    /** Default health from which a mob counts as a boss (the factory tiers' own boss class) */
    static final double BOSS_BASE=150;
    /** Drop rule for a red-marked mob; roll is uniform in [0, 1). */
    static int amount(double roll,double chance,int looting,double lootingBonus,boolean boss,int bossAmount,float maxHealth){
        if(boss)return Math.max(1,bossAmount);
        double p=Math.max(0,Math.min(1,chance+Math.max(0,looting)*lootingBonus));
        if(roll>=p)return 0;
        return maxHealth>=60?2:1;
    }
    /** Pure: what one kill drops. Unmarked mobs drop only as big bosses; marked ones roll at the multiplied chance; an elite adds its own. */
    static int drop(boolean marked,double roll,double base,double multiplier,int looting,double lootingBonus,boolean boss,int bossAmount,float maxHealth,int elite){
        if(!marked)return boss&&maxHealth>=BOSS_HEALTH?1:0;
        return amount(roll,base*Math.max(0,multiplier),looting,lootingBonus,boss,bossAmount,maxHealth)+Math.max(0,elite);
    }
    /** Pure: the multiplier of marked mobs: the config's, raised by the raid's modifiers and the killer's Luck (+25% a level) */
    static double multiplier(double config,int modifierPercent,int luck){return Math.max(0,config)*(1+Math.max(0,modifierPercent)/100.0)*(1+.25*Math.max(0,luck));}
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
        if(entity instanceof net.minecraft.world.entity.raid.Raider r&&vanillaRaider(true,r.getWave(),r.getPersistentData().getBoolean("eternalRaider")))return;
        boolean raider=entity.getPersistentData().getBoolean("eternalRaider"),marked=raider||HuntWarband.hunter(entity);
        var killer=entity.getKillCredit() instanceof Player p?p:event.getSource().getEntity() instanceof Player p?p:null;
        if(killer==null&&!marked)return;
        boolean boss=entity.getPersistentData().getBoolean("eternalBoss")||entity.getType().is(Tags.EntityTypes.BOSSES)||defaultHealth(entity)>=BOSS_BASE;
        int percent=raider?CampaignData.get(level).dropBonus():0,luck=killer==null||!killer.hasEffect(net.minecraft.world.effect.MobEffects.LUCK)?0:killer.getEffect(net.minecraft.world.effect.MobEffects.LUCK).getAmplifier()+1;
        boolean raidBoss=entity.getPersistentData().getBoolean("eternalBoss");
        int n=drop(marked,level.random.nextDouble(),DefenseConfig.ENERGY_CHANCE.get(),multiplier(DefenseConfig.MARKED_MULTIPLIER.get(),percent,luck),event.getLootingLevel(),DefenseConfig.LOOTING_BONUS.get(),marked?raidBoss:boss,DefenseConfig.BOSS_ENERGY.get(),entity.getMaxHealth(),Elites.reward(entity));
        if(n<=0)return;
        var drop=new ItemEntity(level,entity.getX(),entity.getY()+0.3,entity.getZ(),new ItemStack(EternalDefense.ARDENT_ENERGY.get(),n));
        drop.setDeltaMovement((level.random.nextDouble()-.5)*.2,.25,(level.random.nextDouble()-.5)*.2);
        event.getDrops().add(drop);
    }
    /** The health its kind spawns with, before any scaling */
    static double defaultHealth(net.minecraft.world.entity.LivingEntity e){
        try{return net.minecraft.world.entity.ai.attributes.DefaultAttributes.getSupplier((net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.LivingEntity>)e.getType()).getBaseValue(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);}
        catch(RuntimeException ex){return e.getMaxHealth();}
    }
}
