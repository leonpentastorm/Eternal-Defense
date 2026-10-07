package dev.createarsenal.beacon;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import java.util.List;

final class PreparedSandwich extends Item {
    PreparedSandwich(){super(new Properties().stacksTo(16).food(new FoodProperties.Builder().nutrition(8).saturationMod(.6f).alwaysEat().build()));}
    static ItemStack create(MealData meal){var stack=new ItemStack(ArsenalBeacon.SANDWICH.get());stack.getOrCreateTag().put("Meal",meal.save());return stack;}
    static MealData meal(ItemStack stack){return stack.hasTag()?MealData.load(stack.getTag().getCompound("Meal")):null;}
    @Override public Component getName(ItemStack stack){var m=meal(stack);return m==null?super.getName(stack):m.name();}
    @Override public ItemStack finishUsingItem(ItemStack stack,Level level,LivingEntity entity){var m=meal(stack);var result=super.finishUsingItem(stack,level,entity);if(entity instanceof ServerPlayer p&&m!=null&&!m.stew())PlayerMeals.eat(p,m);return result;}
    @Override public void appendHoverText(ItemStack stack,Level level,List<Component> lines,TooltipFlag flag){var m=meal(stack);if(m!=null){for(var b:m.bonuses())lines.add(b.description(false).copy().withStyle(net.minecraft.ChatFormatting.AQUA));lines.add(Component.translatable("gui.arsenal_beacon.meal.duration",m.duration()/1200).withStyle(net.minecraft.ChatFormatting.GRAY));lines.add(Component.translatable("gui.arsenal_beacon.meal.home_hint").withStyle(net.minecraft.ChatFormatting.GOLD));}}
}
