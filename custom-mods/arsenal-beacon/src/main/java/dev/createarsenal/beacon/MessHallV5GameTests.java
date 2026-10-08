package dev.createarsenal.beacon;

import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.*;

/** Opt-in checks against packaged server JARs, with no client-authored meal data. */
@net.minecraftforge.gametest.GameTestHolder(ArsenalBeacon.ID)
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
public final class MessHallV5GameTests {
    private record Fixture(ServerLevel level,BlockPos floor,BlockPos root,ServerPlayer player,CampaignData campaign){}
    private static Fixture fixture(GameTestHelper h){
        var l=h.getLevel();var floor=new BlockPos(24576,100,0);UpgradeGameTests.arena(l,floor);var root=floor.above();
        var d=CampaignData.get(l);d.resetProgress();d.damage.clear();d.destroyedTurrets.clear();d.core=3;d.phase="preparation";d.beacon=root.west(4);l.setBlock(d.beacon,ArsenalBeacon.BEACON.get().defaultBlockState(),3);
        var p=UpgradeGameTests.player(h,"meal-v5");p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);p.setPos(root.getCenter());PlayerMeals.clear(p);
        return new Fixture(l,floor,root,p,d);
    }
    private static void clean(Fixture f){PlayerMeals.clear(f.player);f.campaign.phase="unplaced";f.campaign.raiders.clear();UpgradeGameTests.release(f.level,f.floor);}
    static SimpleContainer inputs(Item... items){var c=new SimpleContainer(8);for(int i=0;i<items.length;i++)c.setItem(i,new ItemStack(items[i],16));return c;}
    // V6 keeps these backend regressions but chooses their legendary recipes deliberately.
    static IngredientTraits.Preview legendary(net.minecraft.world.Container c,boolean stew,int mk){
        var groups=new HashSet<String>();for(int i=0;i<6;i++)if(!c.getItem(i).isEmpty())groups.add(IngredientTraits.groupOf(c.getItem(i)));
        var effects=EnumSet.noneOf(MealRules.Effect.class);for(var mix:MealRules.mixes(groups))effects.add(mix.effect());return IngredientTraits.compose(c,stew,mk,effects);
    }
    private static final Map<String,List<Item>> FOODS=Map.of("protein",List.of(Items.COOKED_CHICKEN,Items.COOKED_MUTTON),"grain",List.of(Items.BREAD,Items.COOKIE),"fruit",List.of(Items.APPLE,Items.SWEET_BERRIES),"vegetables",List.of(Items.CARROT,Items.POTATO),"fish",List.of(Items.COOKED_COD,Items.COOKED_SALMON),"fungi",List.of(Items.BROWN_MUSHROOM,Items.RED_MUSHROOM));
    @GameTest(template="empty3x3x3",timeoutTicks=100)
    public static void v5VanillaFoodsGiveOrdinaryBonuses(GameTestHelper h){
        var groups=new HashSet<String>();for(var item:BuiltInRegistries.ITEM){
            if(!BuiltInRegistries.ITEM.getKey(item).getNamespace().equals("minecraft")||!item.isEdible()&&item!=Items.MILK_BUCKET)continue;
            var stack=new ItemStack(item);h.assertTrue(IngredientTraits.accepts(stack),"Vanilla food accepted: "+item);
            h.assertTrue(IngredientTraits.effectsOf(stack).stream().noneMatch(MealRules.Effect::gun),"No direct gun buff: "+item);groups.add(IngredientTraits.groupOf(stack));
        }
        h.assertTrue(groups.equals(FOODS.keySet()),"Every vanilla food belongs to the six documented groups");h.succeed();
    }
    @GameTest(template="empty3x3x3",timeoutTicks=100)
    public static void v5LegendaryPairsReplaceNormalAndUseFourDistinctFoods(GameTestHelper h){
        for(var mix:MealRules.MIXES){
            var a=FOODS.get(mix.first());var b=FOODS.get(mix.second());
            var single=legendary(inputs(a.get(0),b.get(0)),true,1).meal();
            h.assertTrue(single!=null&&single.bonuses().size()==1&&single.bonuses().get(0).effect()==mix.effect()&&!single.bonuses().get(0).pair(),"Pair replaces both normal groups: "+mix);
            var c=inputs(a.get(0),a.get(1),b.get(0),b.get(1));
            for(int mk=1;mk<=4;mk++){var meal=legendary(c,true,mk).meal();h.assertTrue(meal.bonuses().size()==1&&meal.bonuses().get(0).pair()==(mk==4),"Four-food doubling only Mk IV: "+mix+" level "+mk);}
            h.assertTrue(!legendary(inputs(a.get(0),a.get(1),b.get(0)),true,4).meal().bonuses().get(0).pair(),"Three distinct foods cannot double legendary "+mix);
        }
        var linkedInputs=inputs(Items.COOKED_CHICKEN,Items.COOKED_MUTTON,Items.BREAD,Items.COOKIE,Items.COOKED_COD,Items.APPLE);
        var links=IngredientTraits.legendaryLinks(linkedInputs,legendary(linkedInputs,true,4).meal());
        h.assertTrue(links.size()==2&&links.get(0).slots().length==4&&links.get(0).doubled()&&links.get(1).slots().length==2,"Visual links use the selected server mixes and all four doubling contributors");
        var six=legendary(inputs(Items.COOKED_CHICKEN,Items.BREAD,Items.CARROT,Items.APPLE,Items.COOKED_COD,Items.BROWN_MUSHROOM),true,4).meal();
        h.assertTrue(six.bonuses().size()==3&&six.bonuses().stream().allMatch(b->b.effect().gun()&&!b.pair()),"Six groups produce three single-strength legendary effects");
        var fourPlusTwo=legendary(inputs(Items.COOKED_CHICKEN,Items.COOKED_MUTTON,Items.BREAD,Items.COOKIE,Items.COOKED_COD,Items.APPLE),true,4).meal();
        h.assertTrue(fourPlusTwo.bonuses().size()==2&&fourPlusTwo.bonuses().get(0).pair()&&!fourPlusTwo.bonuses().get(1).pair(),"Four slots double Firepower; two slots make another legendary effect");h.succeed();
    }
    @GameTest(template="empty3x3x3",timeoutTicks=100)
    public static void v5OrdinaryPairsAndSplitStacksRespectTier(GameTestHelper h){
        for(int mk=1;mk<=4;mk++){var meal=IngredientTraits.compose(inputs(Items.COOKED_CHICKEN,Items.COOKED_MUTTON),true,mk).meal();h.assertTrue(meal.bonuses().get(0).pair()==(mk==4)&&meal.bonuses().get(0).strength()==1,"Two bars, no third tier: "+mk);}
        var split=IngredientTraits.compose(inputs(Items.COOKED_CHICKEN,Items.COOKED_CHICKEN,Items.COOKED_CHICKEN,Items.BREAD),true,4).meal();h.assertTrue(split.bonuses().stream().noneMatch(MealData.Bonus::pair),"Split identical foods do not double");
        var oneSide=legendary(inputs(Items.CARROT,Items.POTATO,Items.BREAD),true,4).meal();h.assertTrue(!oneSide.bonuses().get(0).pair(),"Legendary needs two foods on BOTH sides");
        h.assertTrue(IngredientTraits.compose(inputs(Items.BREAD,Items.COOKIE,Items.COOKED_CHICKEN),false,4).meal().bonuses().stream().noneMatch(MealData.Bonus::pair),"Portable sandwiches retain normal strength");h.succeed();
    }
    @GameTest(template="empty3x3x3",timeoutTicks=100)
    public static void v5ServerPreparationEnforcesTierAndHidesStewOutput(GameTestHelper h){
        var f=fixture(h);try{
            f.level.setBlock(f.root,ArsenalBeacon.MESS_HALL_I.get().defaultBlockState(),3);var hall=(MessHall.HallEntity)f.level.getBlockEntity(f.root);
            var potPos=f.root.east(4);f.level.setBlock(potPos,ArsenalBeacon.COOK_POT.get().defaultBlockState(),3);hall.discover();
            hall.ingredients.setItem(0,new ItemStack(Items.COOKED_CHICKEN,16));hall.ingredients.setItem(1,new ItemStack(Items.COOKED_MUTTON,16));
            var menu=new MessHallMenu(8,f.player.getInventory(),hall.ingredients,f.root,1,hall);menu.selected=potPos;menu.stew=true;
            menu.view.put("Meal",new MealData(true,List.of(new MealData.Bonus(MealRules.Effect.VITALITY,1,true)),36000).save());
            h.assertTrue(menu.prepare(),"Real server menu prepares Mk I stew");var pot=(CookPot.PotEntity)f.level.getBlockEntity(potPos);
            h.assertTrue(!pot.stew.bonuses().get(0).pair(),"Fake doubled preview does not change server composition");
            hall.ingredients.setItem(6,new ItemStack(ArsenalBeacon.SANDWICH.get()));h.assertTrue(!menu.getSlot(6).isActive()&&!menu.getSlot(6).mayPickup(f.player)&&menu.quickMoveStack(f.player,6).isEmpty(),"Stew output is inactive and cannot be shift-taken");
            menu.stew=false;h.assertTrue(menu.getSlot(6).isActive()&&menu.getSlot(6).mayPickup(f.player),"Output remains available in sandwich tab");
        }finally{clean(f);}h.succeed();
    }
    @GameTest(template="empty3x3x3",timeoutTicks=100)
    public static void v5BowlDispenserFiltersPersistsAndDropsOnce(GameTestHelper h){
        var f=fixture(h);try{
            f.level.setBlock(f.root,ArsenalBeacon.BOWL_DISPENSER.get().defaultBlockState(),3);var dispenser=(BowlDispenser.DispenserEntity)f.level.getBlockEntity(f.root);
            h.assertTrue(f.level.getBlockState(f.root.above()).is(ArsenalBeacon.STRUCTURE_PART.get()),"Exactly two tall installation");
            dispenser.stock.setItem(0,new ItemStack(Items.APPLE,64));h.assertTrue(dispenser.stock.isEmpty(),"Non-bowls rejected in storage");
            dispenser.stock.setItem(0,new ItemStack(Items.BOWL,64));var upper=f.level.getBlockState(f.root.above());
            upper.use(f.level,f.player,InteractionHand.MAIN_HAND,new BlockHitResult(f.root.above().getCenter(),Direction.NORTH,f.root.above(),false));
            h.assertTrue(dispenser.stock.getItem(0).getCount()==63&&Economy.have(f.player,Items.BOWL)==1,"Upper-half right-click dispenses exactly one bowl");
            var copy=new BowlDispenser.DispenserEntity(f.root,dispenser.getBlockState());copy.load(dispenser.saveWithoutMetadata());h.assertTrue(copy.stock.getItem(0).getCount()==63,"Stock persists");
            var menu=new BowlDispenser.DispenserMenu(9,f.player.getInventory(),dispenser.stock,f.root,dispenser);h.assertTrue(menu.slots.size()==37&&!menu.getSlot(0).mayPlace(new ItemStack(Items.APPLE)),"One bowl-only inventory slot plus player inventory");
            f.player.getInventory().setItem(1,new ItemStack(Items.APPLE));h.assertTrue(menu.quickMoveStack(f.player,29).isEmpty(),"Shift-click apple cannot enter bowl stock");
            var bounds=new AABB(f.root).inflate(4);int before=f.level.getEntitiesOfClass(ItemEntity.class,bounds).stream().filter(e->e.getItem().is(Items.BOWL)).mapToInt(e->e.getItem().getCount()).sum();
            upper.getBlock().playerWillDestroy(f.level,f.root.above(),upper,f.player);
            h.assertTrue(f.level.getBlockState(f.root).isAir()&&f.level.getBlockState(f.root.above()).isAir(),"Breaking upper half removes installation");
            h.succeedWhen(()->{
                int after=f.level.getEntitiesOfClass(ItemEntity.class,bounds).stream().filter(e->e.getItem().is(Items.BOWL)).mapToInt(e->e.getItem().getCount()).sum();
                h.assertTrue(after-before==63,"Stored bowls drop exactly once after chunk visibility: before="+before+", after="+after);clean(f);
            });
            h.runAtTickTime(99,()->clean(f));
        }catch(RuntimeException e){clean(f);throw e;}
    }
    @GameTest(template="empty3x3x3",timeoutTicks=100)
    public static void v5EveryTableUpgradesItsOwnAge(GameTestHelper h){
        var f=fixture(h);try{
            for(String kind:List.of("gun","ammo","attachment","armor")){
                var block=ArmorPlatform.station(kind).get();f.level.setBlock(f.root,block.defaultBlockState(),3);
                f.player.getInventory().clearContent();UpgradeGameTests.supply(f.player,WeaponPlatform.upgrades(1));WeaponPlatform.open(f.player,f.root);
                WeaponPlatform.send(f.player,new WeaponPlatform.Request("upgrade","",0,""),false,"");
                h.assertTrue(f.level.getBlockState(f.root).getValue(WeaponPlatform.AGE)==2,"Own-table packet upgrades "+kind);
                f.campaign.phase="raid";UpgradeGameTests.supply(f.player,WeaponPlatform.upgrades(2));h.assertTrue(!WeaponPlatform.upgrade(f.player,f.root).startsWith("Upgraded"),"Raid still blocks "+kind+" upgrade");f.campaign.phase="preparation";
            }
        }finally{clean(f);}h.succeed();
    }
    @GameTest(template="empty3x3x3",timeoutTicks=100)
    public static void v5WorkshopPurchasesAreAtomicAndBeaconBound(GameTestHelper h){
        var f=fixture(h);try{
            for(String id:WorkshopFabrication.ITEMS){
                f.player.getInventory().clearContent();var costs=Economy.fabrication(id);h.assertTrue(!costs.isEmpty(),"Concrete price: "+id);
                h.assertTrue(!WorkshopFabrication.buy(f.player,id).startsWith("Fabricated"),"Cannot buy without materials: "+id);UpgradeGameTests.supply(f.player,costs);
                h.assertTrue(WorkshopFabrication.buy(f.player,id).startsWith("Fabricated")&&Economy.have(f.player,WorkshopFabrication.item(id))==1,"Purchases "+id);
            }
            f.player.getInventory().clearContent();UpgradeGameTests.supply(f.player,Economy.fabrication("mess_hall_mk1"));
            // An output that cannot fit must leave material stacks intact.
            for(int slot=0;slot<36;slot++)f.player.getInventory().setItem(slot,new ItemStack(Items.COBBLESTONE,64));
            f.player.getInventory().setItem(0,new ItemStack(ArsenalBeacon.ARDENT_ENERGY.get(),64));f.player.getInventory().setItem(1,new ItemStack(Items.IRON_INGOT,64));
            var before=f.player.getInventory().save(new ListTag());h.assertTrue(!WorkshopFabrication.buy(f.player,"mess_hall_mk1").startsWith("Fabricated")&&before.equals(f.player.getInventory().save(new ListTag())),"No output space: no payment");
            f.player.getInventory().clearContent();UpgradeGameTests.supply(f.player,Economy.fabrication("mess_hall_mk1"));
            f.player.setPos(f.root.east(300).getCenter());int energy=Economy.have(f.player,ArsenalBeacon.ARDENT_ENERGY.get());h.assertTrue(!WorkshopFabrication.buy(f.player,"mess_hall_mk1").startsWith("Fabricated")&&energy==Economy.have(f.player,ArsenalBeacon.ARDENT_ENERGY.get()),"Remote request consumes nothing");
            h.assertTrue(!WorkshopFabrication.buy(f.player,"mess_hall_mk4").startsWith("Fabricated"),"No direct upgraded hall purchase");
        }finally{clean(f);}h.succeed();
    }
    @GameTest(template="empty3x3x3",timeoutTicks=100)
    public static void v5GunBuffAttributesUseOneModifierAndCleanUp(GameTestHelper h){
        var f=fixture(h);var recoil=f.player.getAttribute(com.github.leopoko.tacz_attributes.attribute.CustomAttributes.RECOIL.get());var external=UUID.randomUUID();
        try{
            recoil.addTransientModifier(new AttributeModifier(external,"External recoil",.2,AttributeModifier.Operation.MULTIPLY_TOTAL));
            for(var mix:MealRules.MIXES){
                var effect=mix.effect();PlayerMeals.eat(f.player,new MealData(true,List.of(new MealData.Bonus(effect,1)),36000));
                for(var attr:PlayerMeals.attributes(effect))h.assertTrue(f.player.getAttribute(attr).getModifier(PlayerMeals.modifier(effect))!=null,"Actual backend attribute installed: "+effect);
                PlayerMeals.clear(f.player);for(var attr:PlayerMeals.attributes(effect))h.assertTrue(f.player.getAttribute(attr).getModifier(PlayerMeals.modifier(effect))==null,"Meal cleanup: "+effect);
            }
            PlayerMeals.eat(f.player,new MealData(true,List.of(new MealData.Bonus(MealRules.Effect.RECOIL_CONTROL,1,true)),36000));
            h.assertTrue(Math.abs(recoil.getValue()-1.2*.6)<1e-8,"Mk IV home recoil reduces by 40 percent once, preserves other modifier");
            f.player.setPos(f.root.east(100).getCenter());PlayerMeals.tick(f.player);h.assertTrue(Math.abs(recoil.getValue()-1.2*.8)<1e-8,"Field recoil reduces by 20 percent");PlayerMeals.clear(f.player);h.assertTrue(Math.abs(recoil.getValue()-1.2)<1e-8,"External recoil survives cleanup");
        }finally{recoil.removeModifier(external);clean(f);}h.succeed();
    }
    @GameTest(template="empty3x3x3",timeoutTicks=100)
    public static void v5RecoveryShovelDealsZeroDamageAndKnocksBack(GameTestHelper h){
        var f=fixture(h);var cow=EntityType.COW.create(f.level);try{
            cow.moveTo(f.root.south().getCenter());f.level.addFreshEntity(cow);f.player.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(ArsenalBeacon.CONTROLLER.get()));
            PlayerMeals.eat(f.player,new MealData(true,List.of(new MealData.Bonus(MealRules.Effect.MIGHT,1)),36000));f.player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST,100,2));
            float health=cow.getHealth();f.player.attack(cow);h.assertTrue(cow.getHealth()==health,"No damage despite Might and Strength III");h.assertTrue(Math.abs(cow.getDeltaMovement().z)>=.49,"Knockback II horizontal impulse");
        }finally{cow.discard();clean(f);}h.succeed();
    }
    @GameTest(template="empty3x3x3",timeoutTicks=100)
    public static void v5FuelWeaponsCannotBeCrafted(GameTestHelper h){
        var f=fixture(h);try{
            var ef=new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation("tacz:modern_kinetic_gun")));((com.tacz.guns.api.item.IGun)ef.getItem()).setGunId(ef,new ResourceLocation("bf1:ef46"));h.assertTrue(!WeaponCompatibility.craftable(ef),"Reported gun blocked even without pack");
            int fuel=0;for(var index:com.tacz.guns.api.TimelessAPI.getAllCommonGunIndex())if(index.getValue().getGunData().getReloadData().getType().name().equals("FUEL")){
                fuel++;var stack=ef.copy();((com.tacz.guns.api.item.IGun)stack.getItem()).setGunId(stack,index.getKey());h.assertTrue(!WeaponCompatibility.craftable(stack),"Every loaded fuel weapon blocked: "+index.getKey());
            }
            h.assertTrue(fuel>=2,"Apocalypse pack loaded for actual fuel-weapon audit");
            for(String kind:List.of("gun","attachment"))h.assertTrue(WeaponPlatform.entries(f.player,kind,"").stream().allMatch(e->WeaponCompatibility.craftable(e.output())),"No banned guns in catalogue or Supplies");
            f.level.setBlock(f.root,ArsenalBeacon.GUN_PLATFORM.get().defaultBlockState(),3);h.assertTrue(!WeaponPlatform.craft(f.player,f.root,new ResourceLocation("bf1:gun/ef46")).startsWith("Crafted"),"Direct recipe request rejected");
            h.assertTrue(WeaponCompatibility.craftable(MessHallV4GameTests.gun(f.player,"tacz:ak47")),"Ordinary magazine gun remains usable");
        }catch(Exception e){throw new RuntimeException(e);}finally{clean(f);}h.succeed();
    }
    public static final class KitchenV5Verification {
        private MultipleTestTracker tracker;private String name;
        @SubscribeEvent public void commands(net.minecraftforge.event.RegisterCommandsEvent e){
            GameTestRegistry.register(MessHallV5GameTests.class);
            e.getDispatcher().register(net.minecraft.commands.Commands.literal("mess-hall-v5-test").requires(s->s.hasPermission(2)).then(net.minecraft.commands.Commands.argument("case",com.mojang.brigadier.arguments.StringArgumentType.word()).executes(c->{
                if(tracker!=null)throw new IllegalStateException("A v5 test is already running");name=com.mojang.brigadier.arguments.StringArgumentType.getString(c,"case").toLowerCase(Locale.ROOT);
                var tests=GameTestRegistry.getAllTestFunctions().stream().filter(t->t.getTestName().toLowerCase(Locale.ROOT).endsWith(name)).toList();if(tests.size()!=1){com.mojang.logging.LogUtils.getLogger().error("V5_TEST_FAIL {} matched {} registered={}",name,tests.size(),GameTestRegistry.getAllTestFunctions().stream().map(TestFunction::getTestName).toList());throw new IllegalArgumentException("Expected exactly one test: "+name);}
                tracker=new MultipleTestTracker(GameTestRunner.runTests(tests,new BlockPos(0,180,0),Rotation.NONE,c.getSource().getLevel(),GameTestTicker.SINGLETON,1));return 1;
            })));
        }
        @SubscribeEvent public void tick(net.minecraftforge.event.TickEvent.ServerTickEvent e){
            if(e.phase!=net.minecraftforge.event.TickEvent.Phase.END||tracker==null)return;GameTestTicker.SINGLETON.tick();if(!tracker.isDone())return;
            for(var t:tracker.getFailedRequired())com.mojang.logging.LogUtils.getLogger().error("V5_TEST_FAIL "+name,t.getError());
            if(tracker.getFailedRequiredCount()==0)com.mojang.logging.LogUtils.getLogger().info("V5_TEST_PASS {}",name);tracker=null;
        }
    }
}
