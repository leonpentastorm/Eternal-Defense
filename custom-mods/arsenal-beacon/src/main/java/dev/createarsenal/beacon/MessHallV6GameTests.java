package dev.createarsenal.beacon;

import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraftforge.fluids.*;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.*;

@net.minecraftforge.gametest.GameTestHolder(ArsenalBeacon.ID)
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
public final class MessHallV6GameTests {
    private record Fixture(ServerLevel level,BlockPos floor,BlockPos root,ServerPlayer player){}
    private static Fixture fixture(GameTestHelper h){
        var l=h.getLevel();var floor=new BlockPos(32768,100,0);UpgradeGameTests.arena(l,floor);var root=floor.above();var d=CampaignData.get(l);d.resetProgress();d.damage.clear();d.core=3;d.phase="preparation";d.beacon=root.west(4);l.setBlock(d.beacon,ArsenalBeacon.BEACON.get().defaultBlockState(),3);
        var p=UpgradeGameTests.player(h,"meal-v6");p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);p.setPos(root.getCenter());p.getInventory().clearContent();p.setShiftKeyDown(false);PlayerMeals.clear(p);p.removeAllEffects();return new Fixture(l,floor,root,p);
    }
    private static void clean(Fixture f){f.player.setShiftKeyDown(false);PlayerMeals.clear(f.player);f.player.removeAllEffects();f.player.closeContainer();CampaignData.get(f.level).phase="unplaced";UpgradeGameTests.release(f.level,f.floor);}
    static final Map<MealRules.Effect,List<Item>> PAIRS=Map.ofEntries(
        Map.entry(MealRules.Effect.VITALITY,List.of(Items.COOKED_CHICKEN,Items.COOKED_MUTTON)),Map.entry(MealRules.Effect.FORTITUDE,List.of(Items.CARROT,Items.POTATO)),
        Map.entry(MealRules.Effect.STEADINESS,List.of(Items.BROWN_MUSHROOM,Items.RED_MUSHROOM)),Map.entry(MealRules.Effect.MOBILITY,List.of(Items.COOKED_COD,Items.COOKED_SALMON)),
        Map.entry(MealRules.Effect.MIGHT,List.of(Items.BEEF,Items.COOKED_BEEF)),Map.entry(MealRules.Effect.AGILITY,List.of(Items.COOKIE,Items.PUMPKIN_PIE)),
        Map.entry(MealRules.Effect.FORTUNE,List.of(Items.APPLE,Items.GLOW_BERRIES)),Map.entry(MealRules.Effect.RECOVERY,List.of(Items.HONEY_BOTTLE,Items.GOLDEN_APPLE)),
        Map.entry(MealRules.Effect.HEARTH,List.of(Items.ROTTEN_FLESH,Items.SPIDER_EYE)),Map.entry(MealRules.Effect.SPRINGY,List.of(Items.CHORUS_FRUIT,Items.MELON_SLICE)),
        Map.entry(MealRules.Effect.SWIM,List.of(Items.COD,Items.SALMON)));
    @GameTest(template="empty3x3x3",timeoutTicks=100)
    public static void v6EveryOrdinaryTripleDoublesWithoutLegendary(GameTestHelper h){
        var effects=PAIRS.keySet().stream().sorted().toList();int tested=0;
        for(int a=0;a<effects.size();a++)for(int b=a+1;b<effects.size();b++)for(int c=b+1;c<effects.size();c++){
            var selected=EnumSet.of(effects.get(a),effects.get(b),effects.get(c));var items=selected.stream().flatMap(e->PAIRS.get(e).stream()).toArray(Item[]::new);var inputs=MessHallV5GameTests.inputs(items);
            h.assertTrue(IngredientTraits.compose(inputs,true,4).meal().bonuses().stream().noneMatch(v->v.effect().gun()),"Ordinary default never auto-mixes gun effects");
            for(int mk=1;mk<=4;mk++){final boolean doubled=mk==4;var meal=IngredientTraits.compose(inputs,true,mk,selected).meal();h.assertTrue(meal!=null&&meal.bonuses().size()==3&&meal.bonuses().stream().allMatch(v->selected.contains(v.effect())&&v.pair()==doubled),"Every chosen triple is available, tier "+mk+": "+selected);}
            tested++;
        }h.assertTrue(tested==165,"All 11 choose 3 ordinary combinations covered");h.succeed();
    }
    @GameTest(template="empty3x3x3",timeoutTicks=100)
    public static void v6ExplicitRecipesAndServerSelection(GameTestHelper h){
        var f=fixture(h);try{
            f.level.setBlock(f.root,ArsenalBeacon.MESS_HALL_IV.get().defaultBlockState(),3);var hall=(MessHall.HallEntity)f.level.getBlockEntity(f.root);var potPos=f.root.east(4);f.level.setBlock(potPos,ArsenalBeacon.COOK_POT.get().defaultBlockState(),3);hall.discover();
            for(var effect:MealRules.Effect.values()){
                var buf=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
                try{new net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket(4,40+effect.ordinal()).write(buf);h.assertTrue(new net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket(buf).getButtonId()==40+effect.ordinal(),"Every selection survives native signed-byte packet codec");}finally{buf.release();}
            }
            Item[] items={Items.COOKED_CHICKEN,Items.COOKED_MUTTON,Items.BREAD,Items.WHEAT,Items.COD,Items.SALMON};for(int i=0;i<6;i++)hall.ingredients.setItem(i,new ItemStack(items[i],16));
            var menu=new MessHallMenu(4,f.player.getInventory(),hall.ingredients,f.root,4,hall);menu.stew=true;
            h.assertTrue(menu.clickMenuButton(f.player,40+MealRules.Effect.FIREPOWER.ordinal()),"Explicit Firepower selected");
            h.assertTrue(!menu.clickMenuButton(f.player,40+MealRules.Effect.QUICK_HANDS.ordinal()),"Unavailable Fruit / shared Grain request rejected");
            h.assertTrue(menu.clickMenuButton(f.player,40+MealRules.Effect.SWIM.ordinal()),"Unreserved Fish adds ordinary Swim");
            menu.view.put("Meal",new MealData(true,List.of(new MealData.Bonus(MealRules.Effect.RAPID_FIRE,3,true)),36000).save());h.assertTrue(menu.prepare(),"Server cooks from selected intents and actual foods");
            var pot=(CookPot.PotEntity)f.level.getBlockEntity(potPos);h.assertTrue(pot.stew.bonuses().size()==2&&pot.stew.bonuses().stream().allMatch(b->b.pair()&&(b.effect()==MealRules.Effect.FIREPOWER||b.effect()==MealRules.Effect.SWIM)),"Both real recipes doubled; fake client meal ignored");
            menu.clickMenuButton(f.player,4);h.assertTrue(!menu.explicit()&&menu.order().isEmpty()&&menu.composition().meal().bonuses().stream().noneMatch(b->b.effect().gun()),"Reset restores ordinary defaults");
            menu.clickMenuButton(f.player,40+MealRules.Effect.SWIM.ordinal());hall.ingredients.setItem(4,ItemStack.EMPTY);hall.ingredients.setItem(5,ItemStack.EMPTY);h.assertTrue(menu.composition().meal()==null&&!menu.prepare(),"Removing selected recipe foods invalidates cooking");
        }finally{clean(f);}h.succeed();
    }
    @GameTest(template="empty3x3x3",timeoutTicks=100)
    public static void v6SandwichBaseIsSeparateAndAtomic(GameTestHelper h){
        var f=fixture(h);try{
            f.level.setBlock(f.root,ArsenalBeacon.MESS_HALL_I.get().defaultBlockState(),3);var hall=(MessHall.HallEntity)f.level.getBlockEntity(f.root);var menu=new MessHallMenu(5,f.player.getInventory(),hall.ingredients,f.root,1,hall);
            hall.ingredients.setItem(0,new ItemStack(Items.COOKED_CHICKEN,4));hall.ingredients.setItem(1,new ItemStack(Items.COOKED_MUTTON,4));
            h.assertTrue(!menu.prepare(),"Base bread required by real menu");hall.ingredients.setItem(7,new ItemStack(Items.WHEAT));h.assertTrue(!menu.prepare()&&!menu.getSlot(7).mayPlace(new ItemStack(Items.WHEAT)),"Wheat cannot replace base bread");
            hall.ingredients.setItem(7,new ItemStack(Items.BREAD,2));hall.ingredients.setItem(6,new ItemStack(Items.STONE));var saved=hall.saveWithoutMetadata();h.assertTrue(!menu.prepare()&&saved.equals(hall.saveWithoutMetadata()),"Blocked output consumes no filling or bread");hall.ingredients.setItem(6,ItemStack.EMPTY);
            h.assertTrue(menu.prepare(),"Non-Grain sandwich made");var meal=PreparedSandwich.meal(hall.ingredients.getItem(6));h.assertTrue(meal.bonuses().size()==1&&meal.bonuses().get(0).effect()==MealRules.Effect.VITALITY&&hall.ingredients.getItem(7).getCount()==1&&hall.ingredients.getItem(0).getCount()==3,"Bread outside composition consumed exactly once");
            var loaded=new MessHall.HallEntity(f.root,hall.getBlockState());loaded.load(hall.saveWithoutMetadata());h.assertTrue(loaded.ingredients.getContainerSize()==8&&loaded.ingredients.getItem(7).getCount()==1,"Separate base slot persists");
            hall.ingredients.setItem(2,new ItemStack(Items.BREAD));h.assertTrue(menu.composition().meal().bonuses().stream().anyMatch(b->b.effect()==MealRules.Effect.STEADINESS),"Bread intentionally put in mix still grants Grain / Steadiness");
            menu.stew=true;h.assertTrue(!menu.getSlot(7).isActive()&&menu.quickMoveStack(f.player,7).isEmpty(),"Stew base slot inactive");
        }finally{clean(f);}h.succeed();
    }
    @GameTest(template="empty3x3x3",timeoutTicks=100)
    public static void v6MilkRefillsReturnEmptiesWithoutLoss(GameTestHelper h){
        var f=fixture(h);try{
            f.level.setBlock(f.root,ArsenalBeacon.MILK_DISPENSER.get().defaultBlockState(),3);var d=(MilkDispenser.DispenserEntity)f.level.getBlockEntity(f.root);var menu=new MilkDispenser.DispenserMenu(6,f.player.getInventory(),d.stock,f.root,d);
            h.assertTrue(menu.slots.size()==38&&!menu.getSlot(0).mayPlace(new ItemStack(Items.WATER_BUCKET))&&!menu.getSlot(1).mayPlace(new ItemStack(Items.BUCKET)),"Milk input and output filters");
            d.stock.setItem(0,new ItemStack(Items.MILK_BUCKET));h.assertTrue(d.refill()&&d.tank.getFluidAmount()==1000&&d.stock.getItem(1).is(Items.BUCKET),"Full bucket returns one bucket");
            d.stock.setItem(0,new ItemStack(ArsenalBeacon.MILK_BOTTLE.get(),4));var before=d.saveWithoutMetadata();h.assertTrue(!d.refill()&&before.equals(d.saveWithoutMetadata()),"Blocked empty output changes nothing");d.stock.setItem(1,ItemStack.EMPTY);
            for(int i=0;i<4;i++)h.assertTrue(d.refill(),"Bottle refilled");h.assertTrue(d.tank.getFluidAmount()==2000&&d.stock.getItem(1).is(Items.GLASS_BOTTLE)&&d.stock.getItem(1).getCount()==4,"Four bottles exactly equal one bucket");
            d.tank.fill(new FluidStack(ForgeMod.MILK.get(),5750),FluidAction.EXECUTE);d.stock.setItem(0,new ItemStack(Items.MILK_BUCKET));d.stock.setItem(1,ItemStack.EMPTY);before=d.saveWithoutMetadata();h.assertTrue(!d.refill()&&before.equals(d.saveWithoutMetadata()),"No partial bucket accepted when only bottle volume fits");
            h.assertTrue(d.tank.fill(new FluidStack(net.minecraft.world.level.material.Fluids.WATER,250),FluidAction.EXECUTE)==0,"Fluid capability rejects water");
            var copy=new MilkDispenser.DispenserEntity(f.root,d.getBlockState());copy.load(d.saveWithoutMetadata());h.assertTrue(copy.tank.getFluidAmount()==7750&&copy.stock.getItem(0).is(Items.MILK_BUCKET),"Tank and pending container persist");
            h.assertTrue(FluidUtil.getFluidContained(new ItemStack(Items.MILK_BUCKET)).orElse(FluidStack.EMPTY).getAmount()==1000,"Native Forge milk-fluid integration enabled");
            var recipe=f.level.getRecipeManager().byKey(new net.minecraft.resources.ResourceLocation(ArsenalBeacon.ID,"milk_bottle")).orElseThrow();h.assertTrue(recipe.getResultItem(f.level.registryAccess()).is(ArsenalBeacon.MILK_BOTTLE.get())&&recipe.getResultItem(f.level.registryAccess()).getCount()==4,"Bottle crafting returns four exact doses");
        }finally{clean(f);}h.succeed();
    }
    @GameTest(template="empty3x3x3",timeoutTicks=100)
    public static void v6MilkClearsSavedMealsAndEveryEffect(GameTestHelper h){
        var f=fixture(h);var external=UUID.randomUUID();var recoil=f.player.getAttribute(com.github.leopoko.tacz_attributes.attribute.CustomAttributes.RECOIL.get());try{
            f.level.setBlock(f.root,ArsenalBeacon.MILK_DISPENSER.get().defaultBlockState(),3);var d=(MilkDispenser.DispenserEntity)f.level.getBlockEntity(f.root);d.tank.fill(new FluidStack(ForgeMod.MILK.get(),500),FluidAction.EXECUTE);
            recoil.addTransientModifier(new AttributeModifier(external,"External",.1,AttributeModifier.Operation.ADDITION));
            PlayerMeals.eat(f.player,new MealData(true,List.of(new MealData.Bonus(MealRules.Effect.VITALITY,1,true),new MealData.Bonus(MealRules.Effect.RECOIL_CONTROL,1,true),new MealData.Bonus(MealRules.Effect.HEARTH,1,true)),36000));f.player.addEffect(new MobEffectInstance(MobEffects.POISON,1000));f.player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,1000));
            var upper=f.level.getBlockState(f.root.above());upper.getBlock().use(upper,f.level,f.root.above(),f.player,InteractionHand.MAIN_HAND,new net.minecraft.world.phys.BlockHitResult(f.root.above().getCenter(),Direction.NORTH,f.root.above(),false));
            h.assertTrue(d.tank.getFluidAmount()==250&&f.player.getActiveEffects().isEmpty()&&f.player.getMaxHealth()==20&&!PlayerMeals.get(f.level).players.containsKey(f.player.getUUID()),"Upper half cleanses all effects and saved meal with one dose");
            PlayerMeals.tick(f.player);PlayerMeals.refresh(f.player);h.assertTrue(f.player.getActiveEffects().isEmpty()&&recoil.getModifier(PlayerMeals.modifier(MealRules.Effect.RECOIL_CONTROL))==null&&recoil.getModifier(external)!=null,"Meal cannot reappear; unrelated attributes survive");
            h.assertTrue(!PlayerMeals.load(PlayerMeals.get(f.level).save(new CompoundTag())).players.containsKey(f.player.getUUID()),"Cleared timer absent from persisted data");
            h.assertTrue(d.tank.getFluidAmount()==250&&!d.dispense(f.player)&&d.tank.getFluidAmount()==250,"With nothing to clear the milk is not spent");
            f.player.addEffect(new MobEffectInstance(MobEffects.POISON,1000));h.assertTrue(d.dispense(f.player)&&d.tank.isEmpty()&&!f.player.hasEffect(MobEffects.POISON),"The last dose clears the poison");
            f.player.addEffect(new MobEffectInstance(MobEffects.POISON,1000));h.assertTrue(!d.dispense(f.player)&&f.player.hasEffect(MobEffects.POISON),"Empty tank has no cleansing or negative drain");
            var bottle=new ItemStack(ArsenalBeacon.MILK_BOTTLE.get());h.assertTrue(bottle.finishUsingItem(f.level,f.player).is(Items.GLASS_BOTTLE)&&f.player.getActiveEffects().isEmpty(),"Portable milk bottle cleanses and returns glass");
        }finally{recoil.removeModifier(external);clean(f);}h.succeed();
    }
    @GameTest(template="empty3x3x3",timeoutTicks=100)
    public static void v6DispenserStockRequiresEmptyHands(GameTestHelper h){
        var f=fixture(h);try{
            for(var block:List.of(ArsenalBeacon.BOWL_DISPENSER.get(),ArsenalBeacon.MILK_DISPENSER.get())){
                f.level.setBlock(f.root,block.defaultBlockState(),3);f.player.setShiftKeyDown(true);f.player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.APPLE));var prior=f.player.containerMenu;
                block.use(f.level.getBlockState(f.root),f.level,f.root,f.player,InteractionHand.MAIN_HAND,new net.minecraft.world.phys.BlockHitResult(f.root.getCenter(),Direction.NORTH,f.root,false));h.assertTrue(f.player.containerMenu==prior,"Held main-hand item prevents stock menu");
                f.player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);f.player.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.SHIELD));h.assertTrue(!BowlDispenser.emptyHands(f.player),"Offhand also must be empty");
                block.use(f.level.getBlockState(f.root),f.level,f.root,f.player,InteractionHand.MAIN_HAND,new net.minecraft.world.phys.BlockHitResult(f.root.getCenter(),Direction.NORTH,f.root,false));h.assertTrue(f.player.containerMenu==prior,"Offhand-held item prevents stock menu");f.player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
                block.use(f.level.getBlockState(f.root),f.level,f.root,f.player,InteractionHand.MAIN_HAND,new net.minecraft.world.phys.BlockHitResult(f.root.getCenter(),Direction.NORTH,f.root,false));h.assertTrue(f.player.containerMenu!=prior,"Empty-hand crouch opens real stock menu");f.player.closeContainer();f.player.setShiftKeyDown(false);
            }
        }finally{clean(f);}h.succeed();
    }
    public static final class KitchenV6Verification {
        private MultipleTestTracker tracker;private String name;
        @SubscribeEvent public void registerV6(net.minecraftforge.event.RegisterCommandsEvent e){
            GameTestRegistry.register(MessHallV6GameTests.class);e.getDispatcher().register(net.minecraft.commands.Commands.literal("mess-hall-v6-test").requires(s->s.hasPermission(2)).then(net.minecraft.commands.Commands.argument("case",com.mojang.brigadier.arguments.StringArgumentType.word()).executes(c->{
                if(tracker!=null)throw new IllegalStateException("A v6 test is already running");name=com.mojang.brigadier.arguments.StringArgumentType.getString(c,"case").toLowerCase(Locale.ROOT);var tests=GameTestRegistry.getAllTestFunctions().stream().filter(t->t.getTestName().toLowerCase(Locale.ROOT).endsWith(name)).toList();if(tests.size()!=1)throw new IllegalArgumentException("Expected one v6 case: "+name);
                tracker=new MultipleTestTracker(GameTestRunner.runTests(tests,new BlockPos(0,190,0),Rotation.NONE,c.getSource().getLevel(),GameTestTicker.SINGLETON,1));return 1;
            })));
        }
        @SubscribeEvent public void tickV6(net.minecraftforge.event.TickEvent.ServerTickEvent e){
            if(e.phase!=net.minecraftforge.event.TickEvent.Phase.END||tracker==null)return;GameTestTicker.SINGLETON.tick();if(!tracker.isDone())return;
            for(var t:tracker.getFailedRequired())com.mojang.logging.LogUtils.getLogger().error("V6_TEST_FAIL "+name,t.getError());if(tracker.getFailedRequiredCount()==0)com.mojang.logging.LogUtils.getLogger().info("V6_TEST_PASS {}",name);tracker=null;
        }
    }
}
