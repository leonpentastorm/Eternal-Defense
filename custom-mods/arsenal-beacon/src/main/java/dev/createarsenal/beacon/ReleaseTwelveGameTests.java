package dev.createarsenal.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import java.util.*;

@GameTestHolder(ArsenalBeacon.ID)
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
public final class ReleaseTwelveGameTests {
    @GameTest(template="empty3x3x3",batch="magazines012",timeoutTicks=100)
    public static void everyNativeMagazineFamilyHasCraftableEmptyOutputAndNoFreeAmmo(GameTestHelper h)throws Exception{
        h.assertTrue(net.minecraftforge.fml.ModList.get().isLoaded("taczmagazines"),"Production suite loads the real magazine addon");
        var p=UpgradeGameTests.player(h,"magazine-coverage012");p.setGameMode(GameType.SURVIVAL);
        var all=WeaponPlatform.entries(p,"ammo","");var magazines=all.stream().filter(e->MagazineBridge.isMagazine(e.output())).toList();
        Set<String> nativeFamilies=new HashSet<>((Set<String>)MagazineBridge.families().getMethod("getAllFamilies").invoke(null));
        Set<String> seen=new HashSet<>();int crafted=0;
        for(var e:magazines){
            seen.add(MagazineBridge.family(e.output()));h.assertTrue(e.gate().kind().equals("ammo")&&WeaponPlatform.validIndex(e.output()),"Empty native magazines are usable despite having no AmmoId");
            p.getInventory().clearContent();var costs=WeaponPlatform.costs(e);UpgradeGameTests.supply(p,costs);
            h.assertTrue(costs.stream().noneMatch(c->Arrays.stream(c.ingredient().getItems()).anyMatch(s->s.getDescriptionId().contains("propellant")||s.getDescriptionId().contains("cartridge_case"))),"Reusable empty magazines do not charge cartridge powder/cases");
            h.assertTrue(WeaponPlatform.transact(p,costs,e.output()),"Every native family crafts from its declared materials");
            var output=p.getInventory().items.stream().filter(MagazineBridge::isMagazine).findFirst().orElseThrow();
            h.assertTrue(MagazineBridge.family(output).equals(MagazineBridge.family(e.output()))&&MagazineBridge.capacity(output)==MagazineBridge.capacity(e.output())&&output.getOrCreateTag().getInt("AmmoCount")==0,"Family/capacity survives crafting; no ammunition is manufactured for free");crafted++;
        }
        h.assertTrue(seen.equals(nativeFamilies)&&crafted>100,"Every live native base/extended magazine family is in the ammo table: native="+nativeFamilies.size()+" table="+seen.size());
        h.assertTrue(WeaponPlatform.entries(p,"attachment","").stream().noneMatch(e->MagazineBridge.isMagazine(e.output()))&&WeaponPlatform.specialEntries(p,"",9).stream().noneMatch(e->MagazineBridge.isMagazine(e.output())),"Magazines never leak into attachments or supplies");
        com.mojang.logging.LogUtils.getLogger().info("0.12 native magazine coverage: {} families; all empty outputs crafted",crafted);p.getInventory().clearContent();h.succeed();
    }
    @GameTest(template="empty3x3x3",batch="magazines012",timeoutTicks=100)
    public static void magazineMenuFitsHeldGunRespectsAgeAndCoinsKeepMagazinesEmpty(GameTestHelper h)throws Exception{
        var p=UpgradeGameTests.player(h,"magazine-menu012");p.setGameMode(GameType.SURVIVAL);var pos=h.absolutePos(new BlockPos(1,1,1));
        var ak=WeaponPlatform.entries(p,"gun","").stream().filter(e->e.gate().id().equals("tacz:ak47")).findFirst().orElseThrow().output().copy();
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,ak);
        var entries=WeaponPlatform.entries(p,"ammo","");var mag=entries.stream().filter(e->MagazineBridge.isMagazine(e.output())&&WeaponPlatform.compatible(p,e)).findFirst().orElseThrow();
        var nativeClass=MagazineBridge.families();String family=MagazineBridge.family(mag.output());
        h.assertTrue((boolean)nativeClass.getMethod("isMagazineCompatibleWithGun",String.class,net.minecraft.resources.ResourceLocation.class).invoke(null,family,new net.minecraft.resources.ResourceLocation("tacz:ak47")),"The addon agrees that the table's magazine fits the AK");
        h.getLevel().setBlock(pos,ArsenalBeacon.AMMO_PLATFORM.get().defaultBlockState(),3);var before=p.getInventory().save(new ListTag());
        h.assertTrue(!WeaponPlatform.craft(p,pos,mag.recipeId()).startsWith("Crafted")&&before.equals(p.getInventory().save(new ListTag())),"Frontier station cannot craft a later magazine or consume material");
        h.getLevel().setBlock(pos,ArsenalBeacon.AMMO_PLATFORM.get().defaultBlockState().setValue(WeaponPlatform.AGE,5),3);UpgradeGameTests.supply(p,WeaponPlatform.costs(mag));
        h.assertTrue(WeaponPlatform.craft(p,pos,mag.recipeId()).startsWith("Crafted"),"Real survival ammo-table path crafts compatible magazine");
        var page=WeaponPlatform.pageState(p,pos,new WeaponPlatform.Request("browse","",0,"",11,0));
        h.assertTrue(page.getInt("ageFilter")==11&&page.getInt("total")>0,"Dedicated compatible-magazine filter is populated");
        for(var n:page.getList("recipes",Tag.TAG_COMPOUND)){var row=(CompoundTag)n;var stack=ItemStack.of(row.getCompound("output"));h.assertTrue(row.getBoolean("magazine")&&MagazineBridge.compatible(ak,stack),"Every displayed magazine truly fits the held gun and installed extension");}
        var loose=WeaponPlatform.pageState(p,pos,new WeaponPlatform.Request("browse","",0,"",10,0));for(var n:loose.getList("recipes",Tag.TAG_COMPOUND))h.assertTrue(!((CompoundTag)n).getBoolean("magazine"),"Loose-ammo filter excludes magazines");
        p.getInventory().clearContent();p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,ak);int price=AmmoCoins.price(p,mag);p.getInventory().add(new ItemStack(ArsenalBeacon.AMMO_COIN.get(),price));
        h.assertTrue(AmmoCoins.buy(p,pos,mag.recipeId()).startsWith("Purchased")&&AmmoCoins.balance(p)==0,"Coin transaction charges exactly the visible magazine price");
        var bought=p.getInventory().items.stream().filter(MagazineBridge::isMagazine).findFirst().orElseThrow();h.assertTrue(bought.getOrCreateTag().getInt("AmmoCount")==0,"Buying a magazine shell does not grant free rounds");
        // Loading through the author's API yields a usable IAmmoBox with the same native family.
        var ammo=entries.stream().filter(e->!MagazineBridge.isMagazine(e.output())&&WeaponPlatform.compatible(p,e)).findFirst().orElseThrow();
        var ammoId=(net.minecraft.resources.ResourceLocation)ammo.output().getItem().getClass().getMethod("getAmmoId",ItemStack.class).invoke(ammo.output().getItem(),ammo.output());
        bought.getItem().getClass().getMethod("setAmmoId",ItemStack.class,net.minecraft.resources.ResourceLocation.class).invoke(bought.getItem(),bought,ammoId);bought.getItem().getClass().getMethod("setAmmoCount",ItemStack.class,int.class).invoke(bought.getItem(),bought,MagazineBridge.capacity(bought));
        h.assertTrue(MagazineBridge.compatible(ak,bought)&&((Number)bought.getItem().getClass().getMethod("getAmmoCount",ItemStack.class).invoke(bought.getItem(),bought)).intValue()==MagazineBridge.capacity(bought),"Crafted shell can hold the gun's actual rounds and remains reload-compatible");
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,ItemStack.EMPTY);h.assertTrue(!WeaponPlatform.compatible(p,mag),"Bare hands cannot bypass compatibility");p.getInventory().clearContent();h.succeed();
    }
    @GameTest(template="empty3x3x3",batch="balance012",timeoutTicks=100)
    public static void wavePlayersExcludeCreativeSpectatorsAndDistantPlayersAndPersist(GameTestHelper h)throws Exception{
        var l=h.getLevel();var d=new CampaignData();d.beacon=h.absolutePos(new BlockPos(1,1,1));d.phase="raid";d.raidTier=6;d.victories=100;
        var players=new ArrayList<net.minecraft.server.level.ServerPlayer>();
        try{
            for(int i=0;i<4;i++){var p=UpgradeGameTests.player(h,"scaling012-"+i);p.moveTo(d.beacon.getX()+2,d.beacon.getY(),d.beacon.getZ()+2,0,0);p.setGameMode(i==1?GameType.CREATIVE:i==2?GameType.SPECTATOR:GameType.SURVIVAL);if(i==3)p.setPos(d.beacon.getX()+500,d.beacon.getY(),d.beacon.getZ());l.addNewPlayer(p);players.add(p);}
            h.assertTrue(RaidBalance.defenders(l,d)==1,"Only nearby living survival/adventure defenders count");players.get(3).setPos(d.beacon.getX()+3,d.beacon.getY(),d.beacon.getZ());h.assertTrue(RaidBalance.defenders(l,d)==2,"Second nearby survival player raises defender count");
            var next=ArsenalBeacon.class.getDeclaredMethod("nextWave",net.minecraft.server.level.ServerLevel.class,CampaignData.class);next.setAccessible(true);next.invoke(null,l,d);
            h.assertTrue(d.wavePlayers==2&&d.waveVeteran==3&&d.spawnRemaining==Rules.waveEnemies(6,1,2,3,false),"Wave locks a bounded, visible reinforcement budget");
            players.get(3).setPos(d.beacon.getX()+500,d.beacon.getY(),d.beacon.getZ());h.assertTrue(d.wavePlayers==2,"Leaving during a wave cannot erase an already announced budget");next.invoke(null,l,d);h.assertTrue(d.wavePlayers==1,"Following wave adjusts back to solo");
            var saved=CampaignData.load(d.save(new CompoundTag()));h.assertTrue(saved.wavePlayers==1&&saved.waveVeteran==3&&saved.spawnRemaining==d.spawnRemaining,"Reload preserves the active wave budget");
        }finally{for(var p:players)l.removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);}h.succeed();
    }
    @GameTest(template="empty3x3x3",batch="balance012",timeoutTicks=100)
    public static void oldNetheriteZombiesBecomeBoundedAndActiveSpawnCapUsesLivingEnemies(GameTestHelper h){
        var l=h.getLevel();var d=new CampaignData();d.raidTier=6;d.wavePlayers=1;var mobs=new ArrayList<Mob>();
        try{
            for(int i=0;i<Rules.concurrentAttackers(1,6);i++){var mob=EntityType.ZOMBIE.create(l);mob.moveTo(h.absolutePos(new BlockPos(1,1,1)),0,0);mob.setNoAi(true);mob.setItemSlot(EquipmentSlot.CHEST,new ItemStack(Items.NETHERITE_CHESTPLATE));mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);mob.setHealth(1000);l.addFreshEntity(mob);mobs.add(mob);d.raiders.add(mob.getUUID());RaidBalance.balance(mob,6);}
            h.assertTrue(mobs.stream().allMatch(m->m.getMaxHealth()==32&&m.getHealth()==32&&m.getArmorValue()==0&&m.getItemBySlot(EquipmentSlot.CHEST).isEmpty()),"Old in-progress netherite raids migrate to 32 HP unarmored ordinary zombies without healing or loot duplication");
            h.assertTrue(!RaidBalance.canSpawn(l,d),"Solo live-enemy cap stops reinforcements");mobs.get(0).discard();h.assertTrue(RaidBalance.canSpawn(l,d),"A removed/dead enemy immediately frees a reinforcement slot");
        }finally{for(var m:mobs)m.discard();}h.succeed();
    }
    @GameTest(template="empty3x3x3",batch="ak012",timeoutTicks=100)
    public static void actualAkBodyBulletsKillTierSixOrdinaryZombieWithinSixHits(GameTestHelper h)throws Exception{
        var p=UpgradeGameTests.player(h,"ak-damage012");p.setGameMode(GameType.SURVIVAL);var l=h.getLevel();var gun=WeaponPlatform.entries(p,"gun","").stream().filter(e->e.gate().id().equals("tacz:ak47")).findFirst().orElseThrow().output().copy();p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,gun);
        var id=new net.minecraft.resources.ResourceLocation("tacz:ak47");var api=Class.forName("com.tacz.guns.api.TimelessAPI");var index=((Optional<?>)api.getMethod("getCommonGunIndex",net.minecraft.resources.ResourceLocation.class).invoke(null,id)).orElseThrow();var data=index.getClass().getMethod("getGunData").invoke(index);var bulletData=index.getClass().getMethod("getBulletData").invoke(index);var ammo=data.getClass().getMethod("getAmmoId").invoke(data);
        var cacheClass=Class.forName("com.tacz.guns.resource.modifier.AttachmentCacheProperty");Object cache=cacheClass.getConstructor().newInstance();cacheClass.getMethod("eval",ItemStack.class,data.getClass()).invoke(cache,gun,data);
        var operatorClass=Class.forName("com.tacz.guns.api.entity.IGunOperator");var operator=operatorClass.getMethod("fromLivingEntity",LivingEntity.class).invoke(null,p);operatorClass.getMethod("updateCacheProperty",cacheClass).invoke(operator,cache);
        var bulletClass=Class.forName("com.tacz.guns.entity.EntityKineticBullet");var ctor=Arrays.stream(bulletClass.getConstructors()).filter(c->c.getParameterCount()==8).findFirst().orElseThrow();
        var resultClass=Class.forName("com.tacz.guns.entity.EntityKineticBullet$EntityResult");var hitClass=Class.forName("com.tacz.guns.util.TacHitResult");var hit=bulletClass.getDeclaredMethod("onHitEntity",hitClass,Vec3.class,Vec3.class);hit.setAccessible(true);
        var zombie=EntityType.ZOMBIE.create(l);zombie.moveTo(h.absolutePos(new BlockPos(1,1,1)),0,0);zombie.setNoAi(true);l.addFreshEntity(zombie);RaidBalance.balance(zombie,6);zombie.setHealth(zombie.getMaxHealth());int shots=0;
        try{
            p.setPos(zombie.getX()-10,zombie.getY(),zombie.getZ());
            while(zombie.isAlive()&&shots<8){Object bullet=ctor.newInstance(l,p,gun,ammo,id,false,data,bulletData);Vec3 body=zombie.position().add(0,.8,0);((Entity)bullet).setPos(body.add(-10,0,0));Object result=resultClass.getConstructor(Entity.class,Vec3.class,boolean.class).newInstance(zombie,body,false);Object impact=hitClass.getConstructor(resultClass).newInstance(result);hit.invoke(bullet,impact,body.add(-1,0,0),body);((Entity)bullet).discard();shots++;}
            h.assertTrue(!zombie.isAlive()&&shots>=3&&shots<=6,"Native AK damage, armor bypass and body-hit events kill an ordinary tier-6 zombie in 3–6 shots; shots="+shots+" remaining="+zombie.getHealth());com.mojang.logging.LogUtils.getLogger().info("0.12 actual TaCZ AK-47 body-hit benchmark: {} bullets / tier-6 ordinary zombie",shots);
        }finally{zombie.discard();p.getInventory().clearContent();}h.succeed();
    }
}
