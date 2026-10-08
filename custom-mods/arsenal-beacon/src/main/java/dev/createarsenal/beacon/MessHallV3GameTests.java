package dev.createarsenal.beacon;

import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import java.util.*;

/** Real-server checks of Mess Hall v3: every vanilla food has a trait, the pair bonus, the vanilla effects that show a meal, and the three new damage and healing hooks. */
@net.minecraftforge.gametest.GameTestHolder(ArsenalBeacon.ID)
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
public final class MessHallV3GameTests {
    private record Fixture(ServerLevel level,BlockPos floor,BlockPos root,ServerPlayer player){}
    private static Fixture fixture(GameTestHelper h,int offset){
        var l=h.getLevel();var floor=new BlockPos(12288+offset*64,100,0);UpgradeGameTests.arena(l,floor);var root=floor.above();
        var d=CampaignData.get(l);d.resetProgress();d.core=3;d.phase="preparation";d.beacon=root.west(4);l.setBlock(d.beacon,ArsenalBeacon.BEACON.get().defaultBlockState(),3);
        var p=UpgradeGameTests.player(h,"meal-v3-"+offset);p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);p.setPos(root.getX()+.5,root.getY(),root.getZ()+.5);
        return new Fixture(l,floor,root,p);
    }
    private static void clean(Fixture f){PlayerMeals.clear(f.player);UpgradeGameTests.release(f.level,f.floor);CampaignData.get(f.level).phase="unplaced";}
    private static MealData meal(boolean stew,MealData.Bonus... bonuses){return new MealData(stew,Arrays.asList(bonuses),MealRules.FIELD_TICKS);}
    private static MealData.Bonus bonus(MealRules.Effect e){return new MealData.Bonus(e,1);}
    private static SimpleContainer inputs(Item... items){var c=new SimpleContainer(7);for(int i=0;i<items.length;i++)c.setItem(i,new ItemStack(items[i],4));return c;}

    @GameTest(template="empty3x3x3",timeoutTicks=100)
    public static void v3EveryVanillaFoodHasATraitAndEveryEffectIsReachable(GameTestHelper h){
        var missing=new ArrayList<String>();var reached=EnumSet.noneOf(MealRules.Effect.class);
        for(var item:BuiltInRegistries.ITEM){
            var key=BuiltInRegistries.ITEM.getKey(item);if(!key.getNamespace().equals("minecraft"))continue;
            if(!item.isEdible()&&item!=Items.MILK_BUCKET)continue;
            var stack=new ItemStack(item);if(!IngredientTraits.accepts(stack))missing.add(key.getPath());reached.addAll(IngredientTraits.effectsOf(stack));
        }
        h.assertTrue(missing.isEmpty(),"Every vanilla food or drink is a meal ingredient; missing: "+missing);
        MealRules.MIXES.forEach(m->reached.add(m.effect()));h.assertTrue(reached.size()==MealRules.Effect.values().length,"Every effect can be had from some vanilla food; reached "+reached);
        h.succeed();
    }
    @GameTest(template="empty3x3x3",timeoutTicks=100)
    public static void v3PairBonusNeedsTwoDifferentFoodsOfOneFamily(GameTestHelper h){
        var chickenMutton=IngredientTraits.compose(inputs(Items.COOKED_CHICKEN,Items.COOKED_MUTTON),true,4).meal();
        h.assertTrue(chickenMutton!=null&&chickenMutton.bonuses().get(0).effect()==MealRules.Effect.VITALITY&&chickenMutton.bonuses().get(0).pair(),"Cooked chicken and cooked mutton pair for Vitality");
        var single=IngredientTraits.compose(inputs(Items.COOKED_CHICKEN,Items.COOKED_BEEF),true).meal();
        h.assertTrue(single!=null&&single.bonuses().stream().noneMatch(MealData.Bonus::pair),"Chicken with beef: one food per family, no pair");
        var eggs=IngredientTraits.compose(inputs(Items.COOKED_CHICKEN,Items.EGG),true,4).meal();
        h.assertTrue(eggs!=null&&eggs.bonuses().get(0).strength()==1&&eggs.bonuses().get(0).pair(),"Chicken and egg feed Vitality from two families: stronger, but no pair");
        var fish=IngredientTraits.compose(inputs(Items.COOKED_COD,Items.COOKED_SALMON),true,4).meal();
        h.assertTrue(fish!=null&&fish.bonuses().get(0).effect()==MealRules.Effect.MOBILITY&&fish.bonuses().get(0).pair(),"Cooked cod and salmon pair for Mobility");
        var sandwich=IngredientTraits.compose(inputs(Items.BREAD,Items.COOKED_CHICKEN,Items.COOKED_MUTTON),false).meal();
        h.assertTrue(sandwich!=null&&sandwich.bonuses().stream().noneMatch(MealData.Bonus::pair),"Sandwiches never pair");
        h.assertTrue(Math.abs(chickenMutton.bonuses().get(0).field()-2*new MealData.Bonus(MealRules.Effect.VITALITY,1).field())<1e-9,"The pair doubles the value");
        var saved=MealData.load(chickenMutton.save());h.assertTrue(saved.equals(chickenMutton),"The pair flag survives saving");
        h.succeed();
    }
    @GameTest(template="empty3x3x3",timeoutTicks=100)
    public static void v3MealShowsAsVanillaEffectsAndFollowsTheHomeZone(GameTestHelper h){
        var f=fixture(h,1);try{
            var effects=List.of(MealRules.Effect.MIGHT,MealRules.Effect.AGILITY,MealRules.Effect.FORTUNE);
            PlayerMeals.eat(f.player,meal(true,bonus(effects.get(0)),new MealData.Bonus(effects.get(1),2,true),bonus(effects.get(2))));
            for(var e:effects){var inst=f.player.getEffect(MealEffects.of(e));h.assertTrue(inst!=null&&inst.isInfiniteDuration(),"At home "+e.id+" shows as an endless vanilla effect");h.assertTrue(inst.getCurativeItems().isEmpty(),"A milk bucket does not cure a meal effect");}
            h.assertTrue(f.player.getEffect(MealEffects.of(MealRules.Effect.AGILITY)).getAmplifier()==(1|4),"Strength II with the pair bonus is amplifier 5");
            h.assertTrue(f.player.hasEffect(MealEffects.HOME.get()),"The Home Zone effect is shown at home");
            var damage=f.player.getAttribute(Attributes.ATTACK_DAMAGE).getModifier(PlayerMeals.modifier(MealRules.Effect.MIGHT));h.assertTrue(damage!=null&&Math.abs(damage.getAmount()-MealRules.Effect.MIGHT.amount*2)<1e-9&&damage.getOperation()==net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_BASE,"Might is a doubled melee damage modifier at home");
            var speed=f.player.getAttribute(Attributes.ATTACK_SPEED).getModifier(PlayerMeals.modifier(MealRules.Effect.AGILITY));h.assertTrue(speed!=null&&Math.abs(speed.getAmount()-MealRules.Effect.AGILITY.amount*1.25*2*2)<1e-9,"Agility strength II with the pair is 2.5 x base, doubled at home");
            var luck=f.player.getAttribute(Attributes.LUCK).getModifier(PlayerMeals.modifier(MealRules.Effect.FORTUNE));h.assertTrue(luck!=null&&luck.getAmount()==2,"Fortune is +2 luck at home");
            f.player.setPos(f.root.getX()+60,f.root.getY(),f.root.getZ());PlayerMeals.tick(f.player);
            for(var e:effects){var inst=f.player.getEffect(MealEffects.of(e));h.assertTrue(inst!=null&&!inst.isInfiniteDuration()&&Math.abs(inst.getDuration()-MealRules.FIELD_TICKS)<=40,"In the field "+e.id+" counts down from about 30 minutes");}
            h.assertTrue(!f.player.hasEffect(MealEffects.HOME.get()),"The Home Zone effect is gone in the field");
            luck=f.player.getAttribute(Attributes.LUCK).getModifier(PlayerMeals.modifier(MealRules.Effect.FORTUNE));h.assertTrue(luck!=null&&luck.getAmount()==1,"Fortune is +1 luck in the field");
            f.player.removeAllEffects();PlayerMeals.tick(f.player);for(int t=0;t<20;t++){f.player.tickCount=20;PlayerMeals.tick(f.player);}
            h.assertTrue(f.player.hasEffect(MealEffects.of(MealRules.Effect.MIGHT)),"Clearing the display does not end the meal: the server restores it");
            PlayerMeals.clear(f.player);for(var e:MealRules.Effect.values())h.assertTrue(!f.player.hasEffect(MealEffects.of(e)),"Clearing the meal removes "+e.id);
            h.assertTrue(f.player.getAttribute(Attributes.LUCK).getModifier(PlayerMeals.modifier(MealRules.Effect.FORTUNE))==null,"and its attribute modifiers");
        }finally{clean(f);}h.succeed();
    }
    @GameTest(template="empty3x3x3",timeoutTicks=100)
    public static void v3RecoveryHearthAndSpringyStepActOnTheirDamage(GameTestHelper h){
        var f=fixture(h,2);try{
            var fire=f.level.damageSources().inFire();var blast=f.level.damageSources().explosion(null,null);var cactus=f.level.damageSources().cactus();
            float baseFire=net.minecraftforge.common.ForgeHooks.onLivingHurt(f.player,fire,10f);h.assertTrue(baseFire==10f,"Without a meal fire damage is unchanged");
            var fall=net.minecraftforge.common.ForgeHooks.onLivingFall(f.player,10f,1f);float baseFall=fall==null?1f:fall[1];
            PlayerMeals.eat(f.player,meal(true,bonus(MealRules.Effect.HEARTH),bonus(MealRules.Effect.SPRINGY),bonus(MealRules.Effect.RECOVERY)));
            float hearth=(float)(1-MealRules.Effect.HEARTH.amount*2);
            h.assertTrue(Math.abs(net.minecraftforge.common.ForgeHooks.onLivingHurt(f.player,fire,10f)-10f*hearth)<1e-4,"Hearth cuts fire damage by the doubled home share");
            h.assertTrue(Math.abs(net.minecraftforge.common.ForgeHooks.onLivingHurt(f.player,blast,10f)-10f*hearth)<1e-4,"and explosion damage");
            h.assertTrue(net.minecraftforge.common.ForgeHooks.onLivingHurt(f.player,cactus,10f)==10f,"but not other damage");
            var withMeal=net.minecraftforge.common.ForgeHooks.onLivingFall(f.player,10f,1f);h.assertTrue(withMeal!=null&&Math.abs(withMeal[1]-baseFall*(1-MealRules.Effect.SPRINGY.amount*2))<1e-4,"Springy Step cuts fall damage by the doubled home share");
            f.player.setHealth(10f);f.player.tickCount=MealRules.RECOVERY_PERIOD;PlayerMeals.tick(f.player);
            h.assertTrue(Math.abs(f.player.getHealth()-(10f+MealRules.Effect.RECOVERY.amount*2))<1e-4,"Recovery heals twice its field amount at home on each pulse");
            f.player.setHealth(10f);f.player.tickCount=MealRules.RECOVERY_PERIOD+1;PlayerMeals.tick(f.player);h.assertTrue(f.player.getHealth()==10f,"and only on the pulse");
            PlayerMeals.clear(f.player);h.assertTrue(net.minecraftforge.common.ForgeHooks.onLivingHurt(f.player,fire,10f)==10f,"A cleared meal protects from nothing");
        }finally{clean(f);}h.succeed();
    }
}
