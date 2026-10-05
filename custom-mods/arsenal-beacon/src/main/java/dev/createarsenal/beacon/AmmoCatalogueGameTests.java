package dev.createarsenal.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import java.util.*;

@GameTestHolder(ArsenalBeacon.ID)
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
public final class AmmoCatalogueGameTests {
    @GameTest(template="empty3x3x3",batch="ammocatalogue",timeoutTicks=100)
    public static void identicalCartridgesMergeButMagazineVariantsRemain(GameTestHelper h){
        var p=UpgradeGameTests.player(h,"ammo-identity021");var sample=WeaponPlatform.entries(p,"ammo","").stream().filter(e->e.output().getOrCreateTag().contains("AmmoId")&&!MagazineBridge.isMagazine(e.output())).findFirst().orElseThrow();
        var id=new ResourceLocation(sample.output().getTag().getString("AmmoId"));var gate=new WeaponPlatform.Gate(2,"ammo",id.toString());
        var original=new WeaponPlatform.Entry(new ResourceLocation(id.getNamespace(),"ammo/original"),sample.recipe(),gate,sample.output().copyWithCount(16));
        var alternate=new WeaponPlatform.Entry(new ResourceLocation("addon","ammo/bulk"),sample.recipe(),gate,sample.output().copyWithCount(64));
        h.assertTrue(AmmoCatalogue.canonical(List.of(alternate,original)).equals(List.of(original))&&AmmoCatalogue.canonical(List.of(original,alternate)).equals(List.of(original)),"Recipe order and different batch sizes cannot duplicate a cartridge or choose an alternate author cost");
        var low=new WeaponPlatform.Entry(alternate.recipeId(),alternate.recipe(),new WeaponPlatform.Gate(1,"ammo",id.toString()),alternate.output());h.assertTrue(AmmoCatalogue.canonical(List.of(original,low)).equals(List.of(low)),"Canonical choice preserves the earliest legitimate Age");
        var different=alternate.output().copy();different.getOrCreateTag().putString("AmmoId","other:different_cartridge");var cartridge=new WeaponPlatform.Entry(new ResourceLocation("addon","ammo/different"),sample.recipe(),gate,different);
        h.assertTrue(AmmoCatalogue.canonical(List.of(original,alternate,cartridge)).size()==2,"Different cartridge IDs cannot merge just because item or names are similar");
        var magazine=WeaponPlatform.entries(p,"ammo","").stream().filter(e->MagazineBridge.isMagazine(e.output())).findFirst().orElseThrow();var extended=magazine.output().copy();extended.getOrCreateTag().putInt("MaxCapacity",MagazineBridge.capacity(extended)+10);var family=magazine.output().copy();family.getOrCreateTag().putString("MagazineFamily","different-family");
        h.assertTrue(AmmoCatalogue.canonical(List.of(magazine,new WeaponPlatform.Entry(new ResourceLocation("test","capacity"),null,magazine.gate(),extended),new WeaponPlatform.Entry(new ResourceLocation("test","family"),null,magazine.gate(),family))).size()==3,"Magazine family and capacity are meaningful variants");h.succeed();
    }
    @GameTest(template="empty3x3x3",batch="ammocatalogue",timeoutTicks=100)
    public static void loadedAmmoBrowserHasOneRowPerItemAndCraftsItsExactBatch(GameTestHelper h)throws Exception{
        var p=UpgradeGameTests.player(h,"ammo-catalogue021");p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);p.getInventory().clearContent();var raw=new ArrayList<WeaponPlatform.Entry>();
        for(var recipe:h.getLevel().getRecipeManager().getRecipes())if(recipe.getClass().getName().equals("com.tacz.guns.crafting.GunSmithTableRecipe")){
            var stack=(ItemStack)recipe.getClass().getMethod("getOutput").invoke(recipe);if(stack.isEmpty()||!WeaponPlatform.validIndex(stack))continue;
            var gate=MagazineBridge.isMagazine(stack)?MagazineBridge.gate(stack):WeaponPlatform.gate(recipe.getId().toString());if(gate.kind().equals("ammo"))raw.add(new WeaponPlatform.Entry(recipe.getId(),recipe,gate,stack));
        }
        var actual=WeaponPlatform.entries(p,"ammo","");h.assertTrue(raw.size()>actual.size(),"Installed addons really contain repeated ammo recipes");h.assertTrue(actual.stream().map(AmmoCatalogue::identity).distinct().count()==actual.size(),"All loaded ammo and magazine entries have unique item data");
        var gun=WeaponPlatform.entries(p,"gun","").stream().filter(e->e.gate().id().equals("tacz:deagle")).findFirst().orElseThrow();p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,gun.output().copy());var pos=h.absolutePos(new BlockPos(1,1,1));h.getLevel().setBlock(pos,ArsenalBeacon.AMMO_PLATFORM.get().defaultBlockState().setValue(WeaponPlatform.AGE,5),3);
        var compatible=actual.stream().filter(e->WeaponPlatform.compatible(p,e)&&!MagazineBridge.isMagazine(e.output())).toList();h.assertTrue(compatible.size()==1,"Desert Eagle gets one matching loose-ammo row");var entry=compatible.get(0);UpgradeGameTests.supply(p,WeaponPlatform.costs(entry));
        h.assertTrue(WeaponPlatform.craft(p,pos,entry.recipeId()).startsWith("Crafted"),"Canonical ammo recipe remains craftable with its actual materials");h.assertTrue(p.getInventory().items.stream().filter(s->ItemStack.isSameItemSameTags(s,entry.output())).mapToInt(ItemStack::getCount).sum()==entry.output().getCount(),"Crafting pays the canonical recipe and returns its exact batch size");
        com.mojang.logging.LogUtils.getLogger().info("Ammo catalogue audit: {} authored recipes -> {} unique ammunition/magazine variants",raw.size(),actual.size());p.getInventory().clearContent();h.succeed();
    }
}
