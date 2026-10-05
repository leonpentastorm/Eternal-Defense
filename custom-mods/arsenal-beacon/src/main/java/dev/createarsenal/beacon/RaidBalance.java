package dev.createarsenal.beacon;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.resources.ResourceLocation;

/** Solo-first encounter roles. More defenders buy reinforcement pressure, never tougher ordinary zombies. */
final class RaidBalance {
    static int defenders(ServerLevel level,CampaignData d){
        return Math.max(1,Math.min(8,(int)level.players().stream().filter(p->p.isAlive()&&!p.isSpectator()&&!p.isCreative()&&ArsenalBeacon.near(p,d)).count()));
    }
    static String role(int tier,int roll){return tier>=3&&roll<10?"heavy":tier>=2&&roll<30?"specialist":"grunt";}
    static EntityType<?> type(ServerLevel level,int tier,String role){
        if(role.equals("heavy"))return EntityType.RAVAGER;
        if(role.equals("specialist")){
            String[] ids=tier>=5?new String[]{"born_in_chaos_v1:door_knight","born_in_chaos_v1:dread_hound","cataclysm:koboleton"}:new String[]{"cataclysm:koboleton","born_in_chaos_v1:bone_imp","born_in_chaos_v1:decrepit_skeleton"};
            return BuiltInRegistries.ENTITY_TYPE.getOptional(new ResourceLocation(ids[level.random.nextInt(ids.length)])).orElse(EntityType.PILLAGER);
        }
        return level.random.nextInt(4)==0?EntityType.SKELETON:EntityType.ZOMBIE;
    }
    static int health(String role,int tier){int t=Math.max(0,Math.min(10,tier));return role.equals("heavy")?64+4*t:role.equals("specialist")?32+3*t:20+2*t;}
    static void balance(Mob mob,int tier){
        String role=mob.getPersistentData().getString("arsenalRole");
        if(role.isEmpty()){role="grunt";mob.getPersistentData().putString("arsenalRole",role);} // Safe migration of old armored raids.
        boolean heavy=role.equals("heavy");
        for(EquipmentSlot slot:EquipmentSlot.values())if(slot.getType()==EquipmentSlot.Type.ARMOR){
            Item wanted=heavy&&slot==EquipmentSlot.HEAD?Items.IRON_HELMET:heavy&&slot==EquipmentSlot.CHEST?Items.IRON_CHESTPLATE:Items.AIR;
            if(!mob.getItemBySlot(slot).is(wanted))mob.setItemSlot(slot,wanted==Items.AIR?ItemStack.EMPTY:new ItemStack(wanted));mob.setDropChance(slot,0);
        }
        set(mob,Attributes.ARMOR,heavy?8:role.equals("specialist")?2:0);
        set(mob,Attributes.ARMOR_TOUGHNESS,0);
        set(mob,Attributes.KNOCKBACK_RESISTANCE,heavy?.35:0);
        float current=mob.getHealth();set(mob,Attributes.MAX_HEALTH,health(role,tier));mob.setHealth(Math.min(current,mob.getMaxHealth()));
        set(mob,Attributes.ATTACK_DAMAGE,(heavy?5:role.equals("specialist")?3:2)+Math.max(0,Math.min(10,tier))/3);
        set(mob,Attributes.MOVEMENT_SPEED,heavy?.22:role.equals("specialist")?.28:.23);
        mob.setCanPickUpLoot(false);
        if(mob instanceof net.minecraft.world.entity.monster.Zombie zombie)zombie.setBaby(false);
        if(heavy){mob.setCustomName(Component.literal("HEAVY — ").append(mob.getType().getDescription()));mob.setCustomNameVisible(true);}
    }
    private static void set(Mob mob,net.minecraft.world.entity.ai.attributes.Attribute attribute,double value){var a=mob.getAttribute(attribute);if(a!=null){a.removeModifiers();a.setBaseValue(value);}}
    static boolean canSpawn(ServerLevel level,CampaignData d){
        long alive=d.raiders.stream().map(level::getEntity).filter(e->e!=null&&e.isAlive()&&!SpecialForcesRaids.defeated(e)).count();
        return alive<Rules.concurrentAttackers(d.wavePlayers,d.raidTier);
    }
}
