package dev.eternaldefense.beacon;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import java.util.List;

/**
 * 0.0.35 (owner): the Latte, the portable drink (the sandwich of the Drink Station): a Paper Cup and up to two potions, 10 minutes, no
 * sweetener. It stacks to 16, can be drunk at any time and leaves nothing behind. Drinking it makes it the active drink.
 */
final class Latte extends Item {
    Latte(){super(new Item.Properties().stacksTo(DrinkRules.LATTE_STACK));}
    static ItemStack create(DrinkData drink){var stack=new ItemStack(EternalDefense.LATTE.get());stack.getOrCreateTag().put("Drink",drink.save());return stack;}
    static DrinkData drink(ItemStack stack){return stack.hasTag()&&stack.getTag().contains("Drink")?DrinkData.load(stack.getTag().getCompound("Drink")):null;}
    /** The band colour of the cup (tint 0 of the owner's model). */
    static int color(ItemStack stack){var d=drink(stack);return d==null?0xffffffff:0xff000000|d.color();}
    @Override public Component getName(ItemStack stack){var d=drink(stack);return d==null?super.getName(stack):d.name();}
    @Override public UseAnim getUseAnimation(ItemStack stack){return UseAnim.DRINK;}
    @Override public int getUseDuration(ItemStack stack){return 32;}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        if(drink(player.getItemInHand(hand))==null)return InteractionResultHolder.fail(player.getItemInHand(hand));
        return ItemUtils.startUsingInstantly(level,player,hand);
    }
    @Override public ItemStack finishUsingItem(ItemStack stack,Level level,LivingEntity entity){
        var d=drink(stack);
        if(entity instanceof ServerPlayer p&&d!=null){PlayerDrinks.drink(p,d);level.playSound(null,p.blockPosition(),SoundEvents.GENERIC_DRINK,SoundSource.PLAYERS,.6f,1.1f);p.displayClientMessage(Component.translatable("gui.eternal_defense.drinks.served",d.name(),DrinkRules.minutes(d.duration())),true);}
        if(!(entity instanceof Player p&&p.getAbilities().instabuild))stack.shrink(1);
        return stack;
    }
    @Override public void appendHoverText(ItemStack stack,Level level,List<Component> lines,TooltipFlag flag){
        var d=drink(stack);if(d==null)return;
        for(var part:d.parts())lines.add(part.describe().copy().withStyle(net.minecraft.ChatFormatting.AQUA));
        lines.add(Component.translatable("gui.eternal_defense.drinks.duration",DrinkRules.minutes(d.duration())).withStyle(net.minecraft.ChatFormatting.GRAY));
        lines.add(Component.translatable("gui.eternal_defense.drinks.home_hint").withStyle(net.minecraft.ChatFormatting.GOLD));
    }
}
