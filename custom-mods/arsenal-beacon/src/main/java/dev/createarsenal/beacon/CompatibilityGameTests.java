package dev.createarsenal.beacon;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraftforge.gametest.GameTestHolder;
import java.util.*;

@GameTestHolder(ArsenalBeacon.ID)
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
public final class CompatibilityGameTests {
    private static ServerPlayer player(GameTestHelper h,String suffix){var p=net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.nameUUIDFromBytes(("compat-"+suffix).getBytes(java.nio.charset.StandardCharsets.UTF_8)),"CompatTests"));p.getInventory().clearContent();return p;}
    private static void supply(ServerPlayer p,List<WeaponPlatform.Cost> costs){for(var cost:costs){var item=cost.ingredient().getItems()[0];int amount=cost.count();while(amount>0){int n=Math.min(amount,item.getMaxStackSize());p.getInventory().add(item.copyWithCount(n));amount-=n;}}}
    private static void equip(ServerPlayer p,String id){var gun=WeaponPlatform.entries(p,"gun","").stream().filter(e->e.gate().id().equals(id)).findFirst().orElseThrow().output().copy();p.getInventory().selected=0;p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,gun);}
    @GameTest(template="empty3x3x3",batch="ingredientaudit",timeoutTicks=100)
    public static void everyGunCraftingMaterialResolves(GameTestHelper h)throws Exception{
        var p=player(h,"ingredients");List<String> missing=new ArrayList<>();
        for(var e:WeaponPlatform.entries(p,"gun",""))for(var cost:WeaponPlatform.costs(e)){
            var choices=cost.ingredient().getItems();if(choices.length==0||Arrays.stream(choices).allMatch(ItemStack::isEmpty))missing.add(e.recipeId()+" -> "+cost.ingredient().toJson());
            var encoded=WeaponPlatform.costTags(p,List.of(cost)).getCompound(0);h.assertTrue(!ItemStack.of(encoded.getCompound("item")).isEmpty(),"Display material survives NBT encoding: "+e.recipeId()+" "+cost.ingredient().toJson()+" amount="+cost.count());
        }
        com.mojang.logging.LogUtils.getLogger().info("Gun ingredient audit: {} missing materials: {}",missing.size(),missing);
        h.assertTrue(missing.isEmpty(),"Unresolved gun crafting materials: "+missing);h.succeed();
    }
    @GameTest(template="empty3x3x3",batch="ingredientaudit",timeoutTicks=100)
    public static void flamethrowerIronPreviewKeeps128CostAndPayment(GameTestHelper h)throws Exception{
        var p=player(h,"m2iron");p.getInventory().add(new ItemStack(Items.IRON_INGOT,40));var gun=WeaponPlatform.entries(p,"gun","").stream().filter(e->e.gate().id().equals("bf1:m2_2")).findFirst().orElseThrow();
        var costs=WeaponPlatform.costs(gun);var iron=costs.stream().filter(c->c.count()==128&&c.ingredient().test(new ItemStack(Items.IRON_INGOT))).findFirst().orElseThrow();var row=WeaponPlatform.costTags(p,List.of(iron)).getCompound(0);
        h.assertTrue(ItemStack.of(row.getCompound("item")).is(Items.IRON_INGOT)&&row.getInt("count")==128&&row.getInt("have")==40,"M2 preview shows Iron Ingot 40 / 128, not Air");
        var buffer=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());var state=new CompoundTag();state.put("materials",WeaponPlatform.costTags(p,costs));WeaponPlatform.State.encode(new WeaponPlatform.State(state,true,""),buffer);var decoded=WeaponPlatform.State.decode(buffer);buffer.release();
        for(var tag:decoded.data().getList("materials",Tag.TAG_COMPOUND))h.assertTrue(!ItemStack.of(((CompoundTag)tag).getCompound("item")).isEmpty(),"All M2 ingredient icons survive actual client packet encoding");
        p.getInventory().clearContent();for(var c:costs)if(c!=iron)supply(p,List.of(c));p.getInventory().add(new ItemStack(Items.IRON_INGOT,64));p.getInventory().add(new ItemStack(Items.IRON_INGOT,63));var pos=h.absolutePos(new BlockPos(1,1,1));h.getLevel().setBlock(pos,ArsenalBeacon.GUN_PLATFORM.get().defaultBlockState().setValue(WeaponPlatform.AGE,2),3);var before=p.getInventory().save(new ListTag());
        h.assertTrue(WeaponPlatform.craft(p,pos,gun.recipeId()).startsWith("Need 1 more")&&before.equals(p.getInventory().save(new ListTag())),"127 iron reports the missing one without partially spending inputs");p.getInventory().add(new ItemStack(Items.IRON_INGOT));h.assertTrue(WeaponPlatform.craft(p,pos,gun.recipeId()).startsWith("Crafted"),"Exactly 128 iron pays for the real flamethrower");
        h.assertTrue(p.getInventory().items.stream().filter(s->s.is(Items.IRON_INGOT)).mapToInt(ItemStack::getCount).sum()==0,"Correcting the icon never reduces the actual payment");p.getInventory().clearContent();h.succeed();
    }
    @GameTest(template="empty3x3x3",batch="compatibility",timeoutTicks=100)
    public static void nativeCompatibilityAndServerCraftValidation(GameTestHelper h)throws Exception{
        var p=player(h,"ammo");var pos=h.absolutePos(new BlockPos(1,1,1));h.getLevel().setBlock(pos,ArsenalBeacon.AMMO_PLATFORM.get().defaultBlockState().setValue(WeaponPlatform.AGE,5),3);
        var n=WeaponPlatform.pageState(p,pos,new WeaponPlatform.Request("browse","",0,""));h.assertTrue(n.getString("notice").equals("Equip a gun")&&n.getInt("total")==0,"Bare hands show Equip a gun with zero components");
        for(String gun:List.of("tacz:deagle","tacz:m4a1","qkl:fk15p")){
            equip(p,gun);int matching=0;
            for(var entry:WeaponPlatform.entries(p,"ammo","")){
                String api=entry.output().getOrCreateTag().contains("MagazineFamily")?"isAmmoBoxOfGun":"isAmmoOfGun";
                boolean nativeMatch=(boolean)entry.output().getItem().getClass().getMethod(api,ItemStack.class,ItemStack.class).invoke(entry.output().getItem(),p.getMainHandItem(),entry.output());
                h.assertTrue(nativeMatch==WeaponPlatform.compatible(p,entry),"Native ammo/magazine compatibility across author packs");if(nativeMatch&&CreateUnlocks.unlocked(p,entry,5))matching++;
            }
            n=WeaponPlatform.pageState(p,pos,new WeaponPlatform.Request("browse","",0,""));h.assertTrue(n.getInt("total")==matching&&matching>0,"Only compatible ammo recipes are listed for "+gun+" native="+matching+" state="+n+" held="+p.getMainHandItem()+" recognized="+WeaponPlatform.heldGun(p));
        }
        var wrong=WeaponPlatform.entries(p,"ammo","").stream().filter(e->!WeaponPlatform.compatible(p,e)).findFirst().orElseThrow();supply(p,WeaponPlatform.costs(wrong));var before=p.getInventory().save(new ListTag());
        h.assertTrue(WeaponPlatform.craft(p,pos,wrong.recipeId()).contains("does not fit")&&before.equals(p.getInventory().save(new ListTag())),"Forged incompatible recipes cannot consume inputs or produce output");
        p.getInventory().clearContent();equip(p,"tacz:m4a1");h.getLevel().setBlock(pos,ArsenalBeacon.ATTACHMENT_PLATFORM.get().defaultBlockState().setValue(WeaponPlatform.AGE,3),3);
        int unlocked=0;Set<String> locked=new HashSet<>();
        for(var entry:WeaponPlatform.entries(p,"attachment","")){
            boolean nativeMatch=(boolean)p.getMainHandItem().getItem().getClass().getMethod("allowAttachment",ItemStack.class,ItemStack.class).invoke(p.getMainHandItem().getItem(),p.getMainHandItem(),entry.output());
            h.assertTrue(nativeMatch==WeaponPlatform.compatible(p,entry),"Native authored attachment tags are respected");if(nativeMatch){if(CreateUnlocks.unlocked(p,entry,3))unlocked++;else locked.add(entry.gate().id());}
        }
        n=WeaponPlatform.pageState(p,pos,new WeaponPlatform.Request("browse","",0,""));h.assertTrue(unlocked>0&&n.getInt("total")==unlocked&&n.getInt("locked")==locked.size(),"Unlocked attachments stay available while unique later-Age matches are counted");
        h.getLevel().setBlock(pos,ArsenalBeacon.AMMO_PLATFORM.get().defaultBlockState(),3);n=WeaponPlatform.pageState(p,pos,new WeaponPlatform.Request("browse","",0,""));h.assertTrue(n.getInt("total")==0&&n.getString("notice").equals("Upgrade your ammo table"),"No current-Age match shows upgrade-table guidance");
        p.getInventory().clearContent();h.succeed();
    }
    @GameTest(template="empty3x3x3",batch="compatibility",timeoutTicks=100)
    public static void workshopPurchasesAndTargetedUpgrades(GameTestHelper h){
        var p=player(h,"workshop");var weapon=h.absolutePos(new BlockPos(1,1,1));var ammo=weapon.east();h.getLevel().setBlock(weapon,ArsenalBeacon.GUN_PLATFORM.get().defaultBlockState(),3);h.getLevel().setBlock(ammo,ArsenalBeacon.AMMO_PLATFORM.get().defaultBlockState(),3);p.moveTo(weapon.getX()+.5,weapon.getY()+1,weapon.getZ()+.5,0,0);
        var before=p.getInventory().save(new ListTag());h.assertTrue(WeaponPlatform.purchase(p,"ammo").startsWith("Missing")&&before.equals(p.getInventory().save(new ListTag())),"Missing purchase materials consume nothing");
        WeaponPlatform.open(p,weapon);supply(p,WeaponPlatform.purchaseCosts("ammo"));WeaponPlatform.send(p,new WeaponPlatform.Request("buyAmmo","",0,""),false,"");
        h.assertTrue(p.getInventory().items.stream().filter(s->s.is(ArsenalBeacon.AMMO_PLATFORM.get().asItem())).mapToInt(ItemStack::getCount).sum()==1,"Weapon workshop sells exactly one station");
        supply(p,WeaponPlatform.upgrades(1));WeaponPlatform.send(p,new WeaponPlatform.Request("upgradeChild","",0,"",0,ammo.asLong()),false,"");h.assertTrue(h.getLevel().getBlockState(ammo).getValue(WeaponPlatform.AGE)==2,"Workshop upgrades the actual selected placed station");
        supply(p,WeaponPlatform.upgrades(2));WeaponPlatform.open(p,ammo);before=p.getInventory().save(new ListTag());WeaponPlatform.send(p,new WeaponPlatform.Request("upgrade","",0,""),false,"");h.assertTrue(h.getLevel().getBlockState(ammo).getValue(WeaponPlatform.AGE)==2&&before.equals(p.getInventory().save(new ListTag())),"Component tables reject direct Age upgrade requests");
        h.assertTrue(WeaponPlatform.manageUpgrade(p,weapon,weapon.offset(100,0,0)).startsWith("Place"),"Remote upgrade target is rejected");
        for(String kind:List.of("ammo","attachment"))h.assertTrue(p.level().getRecipeManager().byKey(new ResourceLocation("arsenal_beacon:"+kind+"_platform")).isEmpty(),"Component tables have no crafting-table recipe");
        var n=WeaponPlatform.pageState(p,weapon,new WeaponPlatform.Request("browse","",0,"",2,0));for(var tag:n.getList("recipes",Tag.TAG_COMPOUND))h.assertTrue(((CompoundTag)tag).getInt("age")==2,"Weapon filter returns only the requested Age");
        n=WeaponPlatform.pageState(p,weapon,new WeaponPlatform.Request("browse","",0,"",0,0));h.assertTrue(n.getList("children",Tag.TAG_COMPOUND).size()>=1,"Workshop offers nearby placed tables and their true Ages");p.getInventory().clearContent();h.succeed();
    }
    @GameTest(template="empty3x3x3",batch="specialforces",timeoutTicks=100)
    public static void armedSoldiersOnlyJoinBoundedLateRaids(GameTestHelper h)throws Exception{
        var l=h.getLevel();var d=CampaignData.get(l);var previousPhase=d.phase;var previousTier=d.raidTier;var previousBeacon=d.beacon;var oldRaiders=new HashSet<>(d.raiders);
        try{
            Class<?> type=Class.forName("su.uTa4u.specialforces.entities.SwatEntity");Class<?> specialty=Class.forName("su.uTa4u.specialforces.Specialty");Object assaulter=specialty.getMethod("valueOf",String.class).invoke(null,"ASSAULTER");
            var wild=(net.minecraft.world.entity.Mob)type.getMethod("withSpecialty",net.minecraft.world.level.Level.class,specialty).invoke(null,l,assaulter);wild.moveTo(h.absolutePos(new BlockPos(1,1,1)).getX()+.5,101,1.5,0,0);
            h.assertTrue(!l.addFreshEntity(wild),"Ambient/event soldiers are rejected independently of addon configuration");
            var loaded=new net.minecraftforge.event.entity.EntityJoinLevelEvent(wild,l,true);net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(loaded);h.assertTrue(loaded.isCanceled(),"Unowned soldiers cannot return on chunk reload");
            d.phase="raid";d.beacon=h.absolutePos(new BlockPos(1,1,1));d.raiders.clear();
            for(int tier=0;tier<4;tier++){d.raidTier=tier;h.assertTrue(SpecialForcesRaids.count(tier)==0&&!SpecialForcesRaids.spawn(l,d,d.beacon,false),"Early tiers never spawn gun squads");}
            d.raidTier=4;d.spawnRemaining=1;h.assertTrue(SpecialForcesRaids.spawn(l,d,d.beacon,false),"Late raid accepts an owned real assaulter");
            var mob=(net.minecraft.world.entity.Mob)l.getEntity(d.raiders.iterator().next());h.assertTrue(mob!=null&&SpecialForcesRaids.owned(mob,l)&&mob.getMaxHealth()==64&&mob.getHealth()==64,"Owned soldier is counted and has bounded health: "+(mob==null?"not inserted":mob.getMaxHealth()+"/"+mob.getHealth()+" owned="+SpecialForcesRaids.owned(mob,l)));
            h.assertTrue(mob.getMainHandItem().getOrCreateTag().contains("GunId"),"Native spawn initialization supplies a working TaCZ gun");h.assertTrue(!(boolean)type.getMethod("hasMission").invoke(mob),"Soldier cannot summon autonomous mission reinforcements");mob.discard();d.raiders.clear();
            d.raidTier=7;d.spawnRemaining=1;h.assertTrue(SpecialForcesRaids.spawn(l,d,d.beacon,false),"Top tier accepts a bulldozer");mob=(net.minecraft.world.entity.Mob)l.getEntity(d.raiders.iterator().next());h.assertTrue(mob.getMaxHealth()==110&&mob.getMainHandItem().getOrCreateTag().contains("GunId"),"Heavy role is armed with bounded health");mob.discard();h.succeed();
        }finally{for(var id:d.raiders){var entity=l.getEntity(id);if(entity!=null&&!oldRaiders.contains(id))entity.discard();}d.raiders.clear();d.raiders.addAll(oldRaiders);d.phase=previousPhase;d.raidTier=previousTier;d.beacon=previousBeacon;d.setDirty();}
    }
}
