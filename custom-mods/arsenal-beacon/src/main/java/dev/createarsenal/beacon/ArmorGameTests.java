package dev.createarsenal.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.gametest.GameTestHolder;
import java.util.*;

@GameTestHolder(ArsenalBeacon.ID)
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
public final class ArmorGameTests {
    private static net.minecraft.server.level.ServerPlayer player(GameTestHelper h,String name){var p=UpgradeGameTests.player(h,"armor020-"+name);p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);p.getInventory().clearContent();return p;}
    private static BlockPos bench(GameTestHelper h){var pos=h.absolutePos(new BlockPos(1,1,1));h.getLevel().setBlock(pos,ArsenalBeacon.ARMOR_PLATFORM.get().defaultBlockState(),3);return pos;}
    @GameTest(template="empty3x3x3",batch="armor",timeoutTicks=40)
    public static void armorBrowserWorksBarehandedAndFiltersNativePouches(GameTestHelper h){
        var p=player(h,"browse");var pos=bench(h);var entries=ArmorPlatform.entries(p,"");
        for(String mod:ArmorPlatform.MODS)h.assertTrue(entries.stream().anyMatch(e->e.gate().id().startsWith(mod+":")),"Every installed tactical pack is represented: "+mod);
        h.assertTrue(entries.stream().allMatch(e->ArmorPlatform.armor(e.output()))&&entries.stream().noneMatch(CreateUnlocks::supply),"Armor menu contains only worn protection, never consumables");
        var supplies=WeaponPlatform.weaponEntries(p,"",true);h.assertTrue(supplies.stream().anyMatch(e->e.recipe() instanceof ArmorPlatform.Fabrication),"Moved native supplies are obtainable in Supplies");h.assertTrue(supplies.stream().noneMatch(e->ArmorPlatform.armor(e.output())),"Supplies contains no worn armor");
        h.assertTrue(entries.stream().anyMatch(e->ArmorPlatform.type(e.output()).equals("pouches")),"Real tactical pouches and backpacks are obtainable");
        h.assertTrue(ArmorPlatform.entries(p,"Combat Helmet").stream().anyMatch(e->e.gate().id().equals("marbledsarsenal:combat_helmet")),"Search uses the readable author item name even on a server without client translations");
        var state=WeaponPlatform.pageState(p,pos,new WeaponPlatform.Request("browse","",Integer.MAX_VALUE,"",0,0,"pouches",40));
        h.assertTrue(state.getString("notice").isEmpty()&&state.getBoolean("typeFilterAvailable")&&state.getInt("total")>0,"Armor requires no gun and exposes gear filters");
        for(var row:state.getList("recipes",Tag.TAG_COMPOUND))h.assertTrue(ArmorPlatform.type(ItemStack.of(((CompoundTag)row).getCompound("output"))).equals("pouches"),"Category contains only storage gear");
        h.assertTrue(state.getList("recipes",Tag.TAG_COMPOUND).size()<=40&&state.getInt("armorAge")==1,"Network page and independent Age are bounded");h.succeed();
    }
    @GameTest(template="empty3x3x3",batch="armor",timeoutTicks=40)
    public static void nativeArmorRecipesCannotBypassFactoryProgression(GameTestHelper h){
        var p=player(h,"policy");var entries=ArmorPlatform.entries(p,"");
        var nativeIds=new HashSet<>(ArmorPlatform.managedItems());h.assertTrue(nativeIds.size()>300,"Complete native acquisition policy includes all real gear");
        for(var recipe:h.getLevel().getRecipeManager().getRecipes()){
            if(recipe instanceof net.minecraft.world.item.crafting.CraftingRecipe||recipe instanceof net.minecraft.world.item.crafting.SmithingRecipe)h.assertTrue(!nativeIds.contains(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(recipe.getResultItem(h.getLevel().registryAccess()).getItem()).toString()),"Native crafting/smithing cannot bypass Armor Ages");
        }
        for(var e:entries)try{for(var cost:WeaponPlatform.costs(e))h.assertTrue(cost.count()>0&&cost.ingredient().getItems().length>0&&Arrays.stream(cost.ingredient().getItems()).noneMatch(ItemStack::isEmpty),"Every listed material is real, never Air");}catch(ReflectiveOperationException ex){throw new RuntimeException(ex);}
        h.succeed();
    }
    @GameTest(template="empty3x3x3",batch="armor",timeoutTicks=40)
    public static void armorCraftingIsAtomicAndCreativeIsFree(GameTestHelper h)throws Exception{
        var p=player(h,"craft");var pos=bench(h);var entry=ArmorPlatform.entries(p,"").stream().filter(e->e.gate().age()==1&&e.recipe() instanceof ArmorPlatform.Fabrication).findFirst().orElseThrow();
        var before=p.getInventory().save(new ListTag());WeaponPlatform.craft(p,pos,entry.recipeId());h.assertTrue(before.equals(p.getInventory().save(new ListTag())),"No missing-material partial consumption");
        for(var cost:WeaponPlatform.costs(entry))p.getInventory().add(cost.ingredient().getItems()[0].copyWithCount(cost.count()));
        h.assertTrue(WeaponPlatform.craft(p,pos,entry.recipeId()).startsWith("Crafted")&&p.getInventory().countItem(entry.output().getItem())==entry.output().getCount(),"Paid crafting delivers the native functional item");
        p.getInventory().clearContent();var late=ArmorPlatform.entries(p,"").stream().filter(e->e.gate().age()>=4).findFirst().orElseThrow();
        h.assertTrue(WeaponPlatform.craft(p,pos,late.recipeId()).contains("Requires Age"),"Locked high-end gear cannot be requested directly");
        p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);h.assertTrue(WeaponPlatform.craft(p,pos,late.recipeId()).startsWith("Crafted"),"Creative can test every armor at no cost");p.getInventory().clearContent();h.succeed();
    }
    @GameTest(template="empty3x3x3",batch="armor",timeoutTicks=40)
    public static void armorAgePersistsAndHasItsOwnBaseScore(GameTestHelper h){
        var p=player(h,"score");var pos=bench(h);var state=h.getLevel().getBlockState(pos).setValue(WeaponPlatform.AGE,4);h.getLevel().setBlock(pos,state,3);
        var drops=Block.getDrops(state,h.getLevel(),pos,null,p,new ItemStack(Items.DIAMOND_PICKAXE));h.assertTrue(drops.size()==1&&drops.get(0).getTag().getCompound("BlockStateTag").getString("age").equals("4"),"Mining preserves one upgraded armor bench");
        var registry=new PlatformRegistry();registry.track(pos,state);registry.track(pos.east(),ArsenalBeacon.ATTACHMENT_PLATFORM.get().defaultBlockState().setValue(WeaponPlatform.AGE,3));registry.track(pos.north(),state.setValue(WeaponPlatform.AGE,2));
        var d=new CampaignData();d.beacon=pos;
        h.assertTrue(registry.score(d)==Rules.platformScore(4)+Rules.platformScore(3),"Armor scores independently from attachment; duplicates do not inflate score");
        h.assertTrue(PlatformRegistry.load(registry.save(new CompoundTag())).score(d)==registry.score(d),"New kind survives SavedData reload without changing old codes");h.succeed();
    }
    @GameTest(template="empty3x3x3",batch="armor",timeoutTicks=40)
    public static void weaponWorkshopBuysArmorAndUpgradesNearbyBench(GameTestHelper h){
        var p=player(h,"workshop");var pos=bench(h);var weapon=pos.north();h.getLevel().setBlock(weapon,ArsenalBeacon.GUN_PLATFORM.get().defaultBlockState(),3);p.setPos(pos.getX(),pos.getY()+1,pos.getZ());
        h.assertTrue(WeaponPlatform.purchase(p,"armor").contains("Missing"),"Empty purchase consumes nothing");p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        h.assertTrue(WeaponPlatform.purchase(p,"armor").startsWith("Purchased")&&p.getInventory().countItem(ArsenalBeacon.ARMOR_PLATFORM.get().asItem())==1,"Workshop sells the new station");
        h.assertTrue(WeaponPlatform.manageUpgrade(p,weapon,pos).startsWith("Upgraded")&&h.getLevel().getBlockState(pos).getValue(WeaponPlatform.AGE)==2,"Nearby armor upgrades through the server-owned Workshop");
        h.assertTrue(WeaponPlatform.pageState(p,weapon,new WeaponPlatform.Request("browse","",0,"")).contains("buyArmor"),"Workshop includes verified armor purchase costs");p.getInventory().clearContent();h.succeed();
    }
}
