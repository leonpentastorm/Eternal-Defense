package dev.createarsenal.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraftforge.gametest.GameTestHolder;
import java.util.*;

@GameTestHolder(ArsenalBeacon.ID)
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
public final class ReleaseNineGameTests {
    @GameTest(template="empty3x3x3",batch="design09",timeoutTicks=100)
    public static void expandedArchitectureCanEarnAllFiveBuildingTiers(GameTestHelper h){
        var d=new CampaignData();d.beacon=BlockPos.ZERO;d.core=3;d.vertical=4;var blocks=new HashMap<Long,net.minecraft.world.level.block.state.BlockState>();Block[] palette={Blocks.OAK_PLANKS,Blocks.STONE,Blocks.BRICKS,Blocks.STONE_BRICKS,Blocks.SANDSTONE,Blocks.DEEPSLATE_BRICKS,Blocks.POLISHED_ANDESITE,Blocks.CHERRY_PLANKS};
        for(var p:BlockPos.betweenClosed(new BlockPos(-12,0,-12),new BlockPos(12,25,12))){if(p.getY()%5==0)blocks.put(p.asLong(),palette[Math.floorMod(p.getX()+p.getZ(),8)].defaultBlockState());else if(Math.abs(p.getX())==12||Math.abs(p.getZ())==12)blocks.put(p.asLong(),p.getX()%4==0?Blocks.GLOWSTONE.defaultBlockState():Blocks.GLASS.defaultBlockState());}
        Block[] furniture={Blocks.CHEST,Blocks.BARREL,Blocks.BOOKSHELF,Blocks.CRAFTING_TABLE,Blocks.WHITE_BED,Blocks.RED_CARPET,Blocks.TRAPPED_CHEST};for(int level=0;level<5;level++)for(int i=0;i<furniture.length;i++)for(int j=0;j<6;j++)blocks.put(new BlockPos(-9+i*3,level*5+1,-10+j*4).asLong(),furniture[i].defaultBlockState());
        var expanded=BaseScoring.analyze(d,blocks,blocks.keySet());h.assertTrue(expanded.platforms()==0&&expanded.factory()==0&&Rules.buildingTier(expanded.total())==5,"A large detailed furnished architectural base can earn all five building tiers without machines or expensive material bonuses: "+expanded);
        d.core=0;var cropped=new HashMap<Long,net.minecraft.world.level.block.state.BlockState>();blocks.forEach((p,s)->{if(d.inside(BlockPos.of(p)))cropped.put(p,s);});h.assertTrue(Rules.buildingTier(BaseScoring.analyze(d,cropped,cropped.keySet()).total())<5,"Starting zone cannot receive credit for rooms outside its boundary");h.succeed();
    }
    @GameTest(template="empty3x3x3",batch="everygun09",timeoutTicks=100)
    public static void everyNativeGunCraftsReliablyWithItsDisplayedBill(GameTestHelper h)throws Exception{
        var p=UpgradeGameTests.player(h,"everygun09");p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);var registry=PlatformRegistry.get(h.getLevel());var saved=new HashSet<>(registry.createMilestones);registry.createMilestones.addAll(List.of("mechanical_press","mechanical_mixer","deployer","precision_mechanism","steam_engine"));var pos=h.absolutePos(new BlockPos(1,1,1));h.getLevel().setBlock(pos,ArsenalBeacon.GUN_PLATFORM.get().defaultBlockState().setValue(WeaponPlatform.AGE,5),3);int conversions=0;
        try{for(var gun:WeaponPlatform.entries(p,"gun",""))for(int order=0;order<2;order++){
            p.getInventory().clearContent();var costs=WeaponPlatform.costs(gun);UpgradeGameTests.supply(p,costs);
            for(var stack:p.getInventory().items)if(stack.hasTag()&&stack.getTag().contains("GunId")){stack.getOrCreateTag().putInt("GunCurrentAmmoCount",3);conversions++;}
            if(order==1)Collections.reverse(p.getInventory().items);
            var before=p.getInventory().save(new ListTag());String result=WeaponPlatform.craft(p,pos,gun.recipeId());h.assertTrue(result.startsWith("Crafted"),"Exact displayed materials reliably craft "+gun.gate().id()+" order="+order+": "+result+" before="+before);
            h.assertTrue(p.getInventory().items.stream().anyMatch(s->ItemStack.isSameItemSameTags(s,gun.output())),"Crafted output retains exact native model data");
        }}finally{registry.createMilestones.clear();registry.createMilestones.addAll(saved);p.getInventory().clearContent();}h.assertTrue(conversions>0,"Actual installed donor-gun recipes were included in the all-gun audit");h.succeed();
    }
    @GameTest(template="empty3x3x3",batch="sight09",timeoutTicks=60)
    public static void solidCoverHidesBeaconFromAmbientMobsAndBuildingAboveIsAllowed(GameTestHelper h){
        var l=h.getLevel();var pos=new BlockPos(2304,101,0);UpgradeGameTests.arena(l,pos);var objective=ArsenalBeacon.OBJECTIVE.get().create(l);objective.moveTo(pos.getX()+.5,pos.getY()+1.01,pos.getZ()+.5,0,0);var zombie=net.minecraft.world.entity.EntityType.ZOMBIE.create(l);zombie.moveTo(pos.getX()-4,pos.getY()+1,pos.getZ()+.5,0,0);
        h.assertTrue(BeaconCombat.inDetectionRange(zombie,objective),"Uncovered beacon is detectable within ambient range");for(var block:BlockPos.betweenClosed(pos.offset(-2,0,-2),pos.offset(-2,5,2)))l.setBlock(block,Blocks.STONE.defaultBlockState(),2);
        h.assertTrue(!BeaconCombat.inDetectionRange(zombie,objective),"Solid cover prevents ambient detection instead of attracting cave mobs through walls");zombie.getPersistentData().putBoolean("arsenalRaider",true);h.assertTrue(BeaconCombat.inDetectionRange(zombie,objective),"Owned raid attackers retain their known objective through cover");
        var p=UpgradeGameTests.player(h,"build-above09");p.getInventory().items.set(0,new ItemStack(Items.COBBLESTONE));var result=((ArsenalBeacon.DefenseBlock)ArsenalBeacon.BEACON.get()).use(ArsenalBeacon.BEACON.get().defaultBlockState(),l,pos,p,net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),net.minecraft.core.Direction.UP,pos,false));h.assertTrue(result==net.minecraft.world.InteractionResult.PASS,"Holding a building block passes the beacon click through to normal top-face placement");p.getInventory().clearContent();UpgradeGameTests.release(l,pos);h.succeed();
    }
    @GameTest(template="empty3x3x3",batch="allocation09",timeoutTicks=100)
    public static void overlappingMaterialsAreDeterministicAtomicAndCountOffhand(GameTestHelper h){
        var p=UpgradeGameTests.player(h,"allocation09");p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        var costs=List.of(new WeaponPlatform.Cost(Ingredient.of(Items.IRON_INGOT,Items.GOLD_INGOT),1),new WeaponPlatform.Cost(Ingredient.of(Items.IRON_INGOT,Items.COPPER_INGOT),1));
        for(int n=0;n<36;n++){p.getInventory().clearContent();p.getInventory().items.set(n,new ItemStack(Items.IRON_INGOT));p.getInventory().items.set((n+1)%36,new ItemStack(Items.GOLD_INGOT));h.assertTrue(WeaponPlatform.transact(p,costs,new ItemStack(Items.DIAMOND)),"Overlapping equal-size choices can reroute allocation, independent of slot order");h.assertTrue(p.getInventory().countItem(Items.IRON_INGOT)==0&&p.getInventory().countItem(Items.GOLD_INGOT)==0&&p.getInventory().countItem(Items.DIAMOND)==1,"Exactly one output with exact shared material payment");}
        p.getInventory().clearContent();p.getInventory().items.set(0,new ItemStack(Items.IRON_INGOT,20));var repeated=InventoryPayment.consolidate(List.of(WeaponPlatform.cost("minecraft:iron_ingot",16),WeaponPlatform.cost("minecraft:iron_ingot",16)));var tags=WeaponPlatform.costTags(p,repeated);h.assertTrue(tags.size()==1&&tags.getCompound(0).getInt("count")==32&&tags.getCompound(0).getInt("have")==20,"Repeated material rows show a combined truthful requirement");var before=p.getInventory().save(new ListTag());for(int i=0;i<20;i++)h.assertTrue(!WeaponPlatform.transact(p,repeated,new ItemStack(Items.DIAMOND))&&before.equals(p.getInventory().save(new ListTag())),"Repeated failed clicks leave every slot untouched");
        p.getInventory().clearContent();p.getInventory().offhand.set(0,new ItemStack(Items.IRON_INGOT,2));h.assertTrue(WeaponPlatform.transact(p,List.of(WeaponPlatform.cost("minecraft:iron_ingot",2)),new ItemStack(Items.DIAMOND)),"Offhand material is usable and consumed");
        p.getInventory().clearContent();for(int i=0;i<36;i++)p.getInventory().items.set(i,new ItemStack(Items.COBBLESTONE,64));p.getInventory().items.set(0,new ItemStack(Items.IRON_INGOT,2));before=p.getInventory().save(new ListTag());h.assertTrue(!WeaponPlatform.transact(p,List.of(WeaponPlatform.cost("minecraft:iron_ingot",1)),new ItemStack(Items.DIAMOND))&&before.equals(p.getInventory().save(new ListTag())),"Full inventory cannot consume inputs without room for output");p.getInventory().clearContent();h.succeed();
    }
    @GameTest(template="empty3x3x3",batch="creative09",timeoutTicks=100)
    public static void creativeBypassesPaymentAgeNetherAndCreateButSurvivalDoesNot(GameTestHelper h)throws Exception{
        var p=UpgradeGameTests.player(h,"creative09");var l=h.getLevel();var pos=h.absolutePos(new BlockPos(1,1,1));var d=CampaignData.get(l);d.damage.clear();d.destroyedTurrets.clear();d.resetProgress();d.beacon=pos;d.phase="preparation";l.setBlock(pos,ArsenalBeacon.BEACON.get().defaultBlockState(),3);p.setPos(pos.getX()+.5,pos.getY()+1,pos.getZ()-4);var table=pos.east(3);l.setBlock(table,ArsenalBeacon.GUN_PLATFORM.get().defaultBlockState(),3);
        p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        try{
            var modern=WeaponPlatform.entries(p,"gun","").stream().filter(e->e.gate().id().equals("tacz:m4a1")).findFirst().orElseThrow();h.assertTrue(WeaponPlatform.craft(p,table,modern.recipeId()).startsWith("Crafted"),"Creative crafts a locked Modern gun from no materials");
            for(int i=0;i<4;i++)h.assertTrue(WeaponPlatform.upgrade(p,table).startsWith("Upgraded"),"Creative station upgrade is free, including Nether gate");
            for(String branch:List.of("core","logistics","defense","restoration","reconnaissance"))for(int i=0;i<Rules.branchMaximum(branch);i++)ArsenalBeacon.upgrade(p,branch);
            var state=BeaconNetwork.state(p,d);h.assertTrue(d.logistics==5&&d.radius()==24&&state.getInt("prospectiveTier")==5&&state.getInt("tier")==0,"Live next payout immediately shows all five Logistics tiers, separately from last paid tier");h.assertTrue(Rules.rewardTier(5,2500)==10&&Rules.rewardTier(500,500000)==10,"Five building plus five upgrade levels cap at tier 10");
            p.getInventory().clearContent();p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);h.assertTrue(!WeaponPlatform.transact(p,List.of(WeaponPlatform.cost("minecraft:iron_ingot",1)),new ItemStack(Items.DIAMOND)),"Returning to Survival restores normal payment enforcement");
        }finally{p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);p.getInventory().clearContent();d.phase="unplaced";d.resetProgress();}h.succeed();
    }
    @GameTest(template="empty3x3x3",batch="coins09",timeoutTicks=100)
    public static void coinsBuyRealCompatibleAmmoWithoutMaterialsAndRespectAge(GameTestHelper h)throws Exception{
        var p=UpgradeGameTests.player(h,"coins09");p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);var gun=WeaponPlatform.entries(p,"gun","").stream().filter(e->e.gate().id().equals("tacz:m4a1")).findFirst().orElseThrow();p.getInventory().items.set(0,gun.output().copy());var pos=h.absolutePos(new BlockPos(1,1,1));h.getLevel().setBlock(pos,ArsenalBeacon.AMMO_PLATFORM.get().defaultBlockState().setValue(WeaponPlatform.AGE,5),3);
        var ammo=WeaponPlatform.entries(p,"ammo","").stream().filter(e->WeaponPlatform.compatible(p,e)&&!CreateUnlocks.armory(e)).findFirst().orElseThrow();int price=AmmoCoins.price(p,ammo);p.getInventory().items.set(1,new ItemStack(ArsenalBeacon.AMMO_COIN.get(),price));
        var response=AmmoCoins.buy(p,pos,ammo.recipeId());h.assertTrue(response.startsWith("Purchased")&&AmmoCoins.balance(p)==0,"Exact coin price buys the full native batch: "+response);h.assertTrue(p.getInventory().items.stream().anyMatch(s->ItemStack.isSameItemSameTags(s,ammo.output())&&s.getCount()==ammo.output().getCount()),"Purchased ammunition keeps native indexed data");
        var before=p.getInventory().save(new ListTag());h.assertTrue(!AmmoCoins.buy(p,pos,ammo.recipeId()).startsWith("Purchased")&&before.equals(p.getInventory().save(new ListTag())),"No coins means no second output and no partial change");
        h.assertTrue(AmmoCoins.price(5,64,64)>AmmoCoins.price(1,64,8)&&AmmoCoins.price(1,64,8)>AmmoCoins.price(1,16,8),"Coin cost scales with Age, damage and batch size");
        h.assertTrue(h.getLevel().getRecipeManager().getRecipes().stream().noneMatch(r->r.getResultItem(h.getLevel().registryAccess()).is(ArsenalBeacon.AMMO_COIN.get())),"Ammo Coin has no crafting recipe");
        var zero=RaidRewards.completion(h.getLevel(),0,false);h.assertTrue(zero.stream().anyMatch(s->s.is(ArsenalBeacon.AMMO_COIN.get())&&s.getCount()==8),"Tier zero awards eight raid-exclusive coins");p.getInventory().clearContent();h.succeed();
    }
    @GameTest(template="empty3x3x3",batch="policy09",timeoutTicks=100)
    public static void nativeStationsAreUncraftableAndDbSuperUsesSteamMilestone(GameTestHelper h){
        var p=UpgradeGameTests.player(h,"policy09");p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);h.assertTrue(!RecipePolicy.nativeStations().isEmpty(),"Native author station block classes are discovered");for(var recipe:h.getLevel().getRecipeManager().getRecipes())h.assertTrue(!RecipePolicy.nativeStation(recipe.getResultItem(h.getLevel().registryAccess()).getItem()),"No surviving acquisition recipe produces a native station");
        var db=WeaponPlatform.specialEntries(p,"",7).stream().filter(e->e.gate().id().equals("lrl:db_long_super")).findFirst().orElseThrow();h.assertTrue(WeaponPlatform.weaponEntries(p,"",false).stream().noneMatch(e->e.gate().id().equals("lrl:db_long_super"))&&CreateUnlocks.required(db)==3,"DB LONG-SUPER moves out of normal Ages into Steam armory research");
        String original="{\"type\":\"tacz:gun_smith_table_crafting\",\"materials\":[{\"item\":{\"item\":\"tacz:modern_kinetic_gun\",\"nbt\":\"{GunId:abc}\"},\"count\":1},{\"item\":{\"item\":\"minecraft:iron_ingot\"},\"count\":64}],\"result\":{\"type\":\"gun\",\"id\":\"tacz:test\",\"count\":1}}";
        var changed=com.google.gson.JsonParser.parseString(RecipePolicy.balanceConversion(original)).getAsJsonObject().getAsJsonArray("materials");h.assertTrue(changed.get(0).getAsJsonObject().get("count").getAsInt()==1&&changed.get(1).getAsJsonObject().get("count").getAsInt()==16,"Conversion retains its donor weapon and charges 25% supplementary materials");h.succeed();
    }
    @GameTest(template="empty3x3x3",batch="breach09",timeoutTicks=60)
    public static void doorsArePrioritizedAndHardWallsUseVictoryRestoration(GameTestHelper h){
        var l=h.getLevel();var pos=new BlockPos(2048,101,0);UpgradeGameTests.arena(l,pos);var d=new CampaignData();d.beacon=pos;d.phase="raid";var mob=net.minecraft.world.entity.EntityType.ZOMBIE.create(l);mob.setPos(pos.getX()-3.5,pos.getY(),pos.getZ()+.5);var wall=pos.west(2).above();l.setBlock(wall,Blocks.OBSIDIAN.defaultBlockState(),2);d.snapshot.put(wall.asLong(),l.getBlockState(wall));
        var door=pos.west(2).south();l.setBlock(door,Blocks.OAK_DOOR.defaultBlockState(),2);l.setBlock(door.above(),Blocks.OAK_DOOR.defaultBlockState().setValue(BlockStateProperties.DOUBLE_BLOCK_HALF,DoubleBlockHalf.UPPER),2);d.snapshot.put(door.asLong(),l.getBlockState(door));d.snapshot.put(door.above().asLong(),l.getBlockState(door.above()));
        h.assertTrue(RaidBreaching.choose(l,d,mob).equals(door),"A nearby door is preferred over the hard wall");h.assertTrue(RaidBreaching.breach(l,d,mob)&&l.getBlockState(door).isAir()&&d.damage.size()==2,"Breaching journals both door halves without loose drops");h.assertTrue(RaidBreaching.breach(l,d,mob)&&l.getBlockState(wall).isAir(),"An ordinary melee enemy can breach obsidian when no door remains");
        d.victoryRestoration=true;d.phase="restore";ArsenalBeacon.restore(l,d);h.assertTrue(l.getBlockState(wall).is(Blocks.OBSIDIAN)&&l.getBlockState(door).is(Blocks.OAK_DOOR)&&l.getBlockState(door.above()).is(Blocks.OAK_DOOR),"Winning restores the hard wall and both correct door halves");d.phase="preparation";h.assertTrue(!RaidBreaching.breach(l,d,mob),"Ambient mobs cannot use raid digging between raids");UpgradeGameTests.release(l,pos);h.succeed();
    }
}
