package dev.eternaldefense.beacon;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import java.util.List;

/** The MRE, packed at the MRE Station. */
final class Mre extends Item {
    static final String TAG="Mre";
    /** The item's own properties: an MRE with no totals restores nothing and cannot be eaten (see use). */
    static final FoodProperties EMPTY=new FoodProperties.Builder().nutrition(0).saturationMod(0f).fast().build();
    Mre(){super(new Item.Properties().stacksTo(MreRules.STACK).food(EMPTY));}

    static ItemStack create(MreRules.Totals totals){
        var stack=new ItemStack(EternalDefense.MRE.get());var tag=new CompoundTag();
        tag.putInt("Nutrition",totals.nutrition());tag.putFloat("Saturation",totals.saturation());
        stack.getOrCreateTag().put(TAG,tag);return stack;
    }
    /** The stored totals, or null for a stack a station did not make. */
    static MreRules.Totals totals(ItemStack stack){
        if(!stack.hasTag()||!stack.getTag().contains(TAG,net.minecraft.nbt.Tag.TAG_COMPOUND))return null;
        var tag=stack.getTag().getCompound(TAG);var t=new MreRules.Totals(tag.getInt("Nutrition"),tag.getFloat("Saturation"));
        return t.valid()?t:null;
    }
    @Override public FoodProperties getFoodProperties(ItemStack stack,LivingEntity entity){
        var t=totals(stack);if(t==null)return EMPTY;
        // No effects and never always-edible: whatever the foods had, the MRE is food and saturation only
        return new FoodProperties.Builder().nutrition(t.nutrition()).saturationMod(MreRules.modifier(t.nutrition(),t.saturation())).fast().build();
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        if(totals(player.getItemInHand(hand))==null)return InteractionResultHolder.fail(player.getItemInHand(hand));
        return super.use(level,player,hand);
    }
    @Override public void appendHoverText(ItemStack stack,Level level,List<Component> lines,TooltipFlag flag){
        var t=totals(stack);
        if(t==null){lines.add(Component.translatable("gui.eternal_defense.mre.tip_empty").withStyle(ChatFormatting.GRAY));return;}
        lines.add(Component.translatable("gui.eternal_defense.mre.tip_food",t.nutrition(),MreRules.MAX_NUTRITION,MreRules.number(t.saturation())).withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable("gui.eternal_defense.mre.tip_eat").withStyle(ChatFormatting.GRAY));
    }

    /** Marines Fuel: a wax crayon, the binder that seals an MRE's paper. */
    static final class MarinesFuel extends Item {
        MarinesFuel(){super(new Item.Properties());}
        @Override public void appendHoverText(ItemStack stack,Level level,List<Component> lines,TooltipFlag flag){lines.add(Component.translatable("gui.eternal_defense.mre.fuel_tip").withStyle(ChatFormatting.GRAY));}
    }
}
