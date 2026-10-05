package dev.createarsenal.beacon;

import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import java.util.*;

@GameTestHolder(ArsenalBeacon.ID)
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
public final class ReleaseSevenGameTests {
    @GameTest(template="empty3x3x3",batch="releasewood",timeoutTicks=100)
    public static void nativeRecipesAndWeaponPurchasesAcceptMixedWood(GameTestHelper h)throws Exception{
        var p=UpgradeGameTests.player(h,"wood-purchase");var registry=PlatformRegistry.get(h.getLevel());var saved=new HashSet<>(registry.createMilestones);
        try{
            registry.createMilestones.addAll(List.of("mechanical_press","mechanical_mixer","deployer","precision_mechanism","steam_engine"));
            var gun=WeaponPlatform.specialEntries(p,"",7).stream().filter(e->e.gate().id().contains("torque")).findFirst().orElseThrow();
            // Inspect author-native ingredients too: this proves KubeJS replaced the real recipe, not just our UI.
            boolean nativeLogs=false;
            for(Object input:(List<?>)gun.recipe().getClass().getMethod("getInputs").invoke(gun.recipe())){
                var ingredient=(net.minecraft.world.item.crafting.Ingredient)input.getClass().getMethod("getIngredient").invoke(input);
                if(ingredient.test(new ItemStack(Items.DARK_OAK_LOG))){nativeLogs=true;h.assertTrue(ingredient.test(new ItemStack(Items.OAK_LOG))&&ingredient.test(new ItemStack(Items.CHERRY_LOG))&&ingredient.test(new ItemStack(Items.WARPED_STEM)),"Native gunpack recipe accepts all log species, including Nether wood");}
            }h.assertTrue(nativeLogs,"Installed Create Armory recipe has a wood input");
            int logs=0;for(var cost:WeaponPlatform.costs(gun)){
                if(cost.ingredient().test(new ItemStack(Items.DARK_OAK_LOG)))logs+=cost.count();else UpgradeGameTests.supply(p,List.of(cost));
            }
            p.getInventory().add(new ItemStack(Items.OAK_LOG,logs/2));p.getInventory().add(new ItemStack(Items.BIRCH_LOG,logs-logs/2));
            var pos=h.absolutePos(new BlockPos(1,1,1));h.getLevel().setBlock(pos,ArsenalBeacon.GUN_PLATFORM.get().defaultBlockState(),3);
            h.assertTrue(WeaponPlatform.craft(p,pos,gun.recipeId()).startsWith("Crafted"),"Mixed oak and birch pay one log material atomically");
            h.assertTrue(p.getInventory().items.stream().noneMatch(s->s.is(Items.OAK_LOG)||s.is(Items.BIRCH_LOG)),"All required wood was consumed exactly");
            var slabs=h.getLevel().getRecipeManager().byKey(new net.minecraft.resources.ResourceLocation("minecraft:oak_slab")).orElseThrow();
            h.assertTrue(slabs.getIngredients().stream().filter(i->!i.isEmpty()).allMatch(i->i.test(new ItemStack(Items.CHERRY_PLANKS))&&i.test(new ItemStack(Items.CRIMSON_PLANKS))),"Standard crafting recipes accept all corresponding plank types too");
            String raw="{\"type\":\"test\",\"materials\":[{\"item\":{\"item\":\"minecraft:dark_oak_log\"},\"count\":128}],\"key\":{\"P\":{\"tag\":\"minecraft:oak_logs\"}},\"result\":{\"item\":\"minecraft:dark_oak_log\",\"count\":4}}";
            var n=JsonParser.parseString(UniversalWood.normalizeJson(raw)).getAsJsonObject();
            h.assertTrue(n.getAsJsonArray("materials").get(0).getAsJsonObject().get("count").getAsInt()==128&&n.getAsJsonObject("result").equals(JsonParser.parseString(raw).getAsJsonObject().getAsJsonObject("result")),"Recipe expansion preserves output species, output count, and 128-unit input count");
            h.assertTrue(n.getAsJsonObject("key").getAsJsonObject("P").get("tag").getAsString().equals("minecraft:logs"),"Species-specific log tags expand as well as item IDs");
        }finally{registry.createMilestones.clear();registry.createMilestones.addAll(saved);p.getInventory().clearContent();}h.succeed();
    }
    @GameTest(template="empty3x3x3",batch="releasealerts",timeoutTicks=100)
    public static void warningsAreGlobalTransientAndBeamSettingMigrates(GameTestHelper h){
        var d=new CampaignData();d.phase="preparation";d.beacon=h.absolutePos(new BlockPos(1,1,1));long now=h.getLevel().getGameTime();d.lastAttackTick=now;
        var p=UpgradeGameTests.player(h,"far-alert");p.moveTo(10000,100,10000,0,0);
        var state=BeaconNetwork.state(p,d);h.assertTrue(state.getBoolean("underAttack")&&!state.getBoolean("near")&&!state.getBoolean("controller"),"Attack signal reaches a distant player without a recovery shovel");
        var nether=net.minecraftforge.common.util.FakePlayerFactory.get(p.server.getLevel(Level.NETHER),new com.mojang.authlib.GameProfile(UUID.nameUUIDFromBytes("nether-alert".getBytes(java.nio.charset.StandardCharsets.UTF_8)),"NetherAlert"));
        h.assertTrue(BeaconNetwork.state(nether,d).getBoolean("underAttack"),"The same signal reaches another dimension");
        h.assertTrue(d.underAttack(now+99)&&!d.underAttack(now+100),"Warning expires five seconds after the last strike");
        var legacy=d.save(new CompoundTag());legacy.remove("showBeam");h.assertTrue(CampaignData.load(legacy).showBeam,"Old saves enable the beam by default");
        d.showBeam=false;var loaded=CampaignData.load(d.save(new CompoundTag()));h.assertTrue(!loaded.showBeam&&!loaded.underAttack(now),"Beam choice persists but old attacks cannot leave a permanent alarm");
        d.finishDecommission();h.assertTrue(!d.underAttack(now)&&d.showBeam,"Removal clears warnings and resets beam settings");
        var entity=new ArsenalBeacon.DefenseEntity(new BlockPos(7,80,9),ArsenalBeacon.BEACON.get().defaultBlockState());h.assertTrue(entity.getRenderBoundingBox().maxY==592,"Render boundary includes the entire 512-block beam, even when only the sky is in view");h.succeed();
    }
}
