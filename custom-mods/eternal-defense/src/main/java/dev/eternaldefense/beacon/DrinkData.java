package dev.eternaldefense.beacon;

import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

/** One drink, saved on a Brew Pot, a Latte and a player's active drink. */
record DrinkData(List<Part> parts,int duration,boolean latte){
    record Part(ResourceLocation effect,int amplifier){
        MobEffect mobEffect(){return ForgeRegistries.MOB_EFFECTS.getValue(effect);}
        /** "Speed II": the effect's own name and the level, as vanilla writes it. */
        Component describe(){
            var e=mobEffect();var name=e==null?Component.literal(effect.toString()):e.getDisplayName();
            return amplifier>0?Component.translatable("potion.withAmplifier",name,Component.translatable("potion.potency."+amplifier)):name;
        }
    }
    static final int MAX_PARTS=3;
    DrinkData {parts=List.copyOf(parts);}
    Component name(){return Component.translatable("gui.eternal_defense.drink.name."+(latte?"latte":"brew"),parts.isEmpty()?Component.literal("?"):parts.get(0).describe());}
    /** The colour of the liquid and of the cup's band: vanilla's potion colour of these effects. */
    int color(){
        var list=new ArrayList<MobEffectInstance>();for(var p:parts){var e=p.mobEffect();if(e!=null)list.add(new MobEffectInstance(e,duration,p.amplifier()));}
        return list.isEmpty()?0x385dc6:PotionUtils.getColor(list);
    }
    CompoundTag save(){
        var n=new CompoundTag();n.putInt("Duration",duration);if(latte)n.putBoolean("Latte",true);var list=new ListTag();
        for(var p:parts){var t=new CompoundTag();t.putString("Effect",p.effect().toString());t.putInt("Amplifier",p.amplifier());list.add(t);}n.put("Parts",list);return n;
    }
    /** Bounded: unknown effects are dropped, at most three parts, one per effect; null when nothing is left. */
    static DrinkData load(CompoundTag n){
        if(n==null)return null;var out=new ArrayList<Part>();var seen=new HashSet<ResourceLocation>();
        for(var t:n.getList("Parts",Tag.TAG_COMPOUND)){
            var c=(CompoundTag)t;var id=ResourceLocation.tryParse(c.getString("Effect"));
            if(id==null||!ForgeRegistries.MOB_EFFECTS.containsKey(id)||!seen.add(id)||out.size()>=MAX_PARTS)continue;
            out.add(new Part(id,Math.max(0,Math.min(9,c.getInt("Amplifier")))));
        }
        int max=DrinkRules.duration(DrinkRules.MAX_STEPS);
        return out.isEmpty()?null:new DrinkData(out,Math.max(20,Math.min(max,n.getInt("Duration"))),n.getBoolean("Latte"));
    }

    // Which potions mix
    /** Drinkable: a plain potion bottle, not a splash or lingering one (any mod's potion that is not thrown counts). */
    static boolean drinkable(ItemStack stack){return stack.getItem() instanceof net.minecraft.world.item.PotionItem&&!(stack.getItem() instanceof net.minecraft.world.item.ThrowablePotionItem);}
    static boolean isPotion(ItemStack stack){return stack.getItem() instanceof net.minecraft.world.item.PotionItem;}
    /** The effects a potion gives (its own and custom ones). */
    static List<MobEffectInstance> effects(ItemStack stack){return isPotion(stack)?PotionUtils.getMobEffects(stack):List.of();}
    /** Why this potion cannot be mixed (see DrinkRules.refusal), "" when it can */
    static String refusal(ItemStack stack,Collection<MobEffect> others){
        if(!isPotion(stack))return "thrown";
        var fx=effects(stack);var one=fx.size()==1?fx.get(0).getEffect():null;
        return DrinkRules.refusal(drinkable(stack),fx.size(),one==MobEffects.REGENERATION,one!=null&&one.getCategory()==MobEffectCategory.BENEFICIAL,one!=null&&one.isInstantenous(),one!=null&&others.contains(one));
    }
    static boolean mixable(ItemStack stack){return refusal(stack,List.of()).isEmpty();}
    /** The one effect of a mixable potion, or null. */
    static MobEffectInstance effectOf(ItemStack stack){var fx=effects(stack);return fx.size()==1?fx.get(0):null;}
    /** A drinkable potion whose only effect is Regeneration: the sweetener. */
    static int sweetener(ItemStack stack){
        if(!drinkable(stack))return 0;var fx=effects(stack);
        return fx.size()==1&&fx.get(0).getEffect()==MobEffects.REGENERATION?DrinkRules.sweetener(fx.get(0).getAmplifier()):0;
    }
    static ResourceLocation id(MobEffect effect){return ForgeRegistries.MOB_EFFECTS.getKey(effect);}
    /** A vanilla potion bottle that gives this effect at this level, for ghost slots and tiles */
    static ItemStack sample(MobEffect effect,int amplifier){
        ItemStack best=ItemStack.EMPTY;
        for(var potion:ForgeRegistries.POTIONS){
            var fx=potion.getEffects();if(fx.size()!=1||fx.get(0).getEffect()!=effect)continue;
            var stack=PotionUtils.setPotion(new ItemStack(Items.POTION),potion);
            if(best.isEmpty()||fx.get(0).getAmplifier()==amplifier)best=stack;
        }
        return best;
    }
    private static List<MobEffect> catalogue;
    /** The effects a drink can carry, for the tiles: every beneficial effect with a duration that some registered */
    static List<MobEffect> catalogue(){
        if(catalogue!=null)return catalogue;
        var set=new LinkedHashSet<MobEffect>();
        for(var potion:ForgeRegistries.POTIONS){var fx=potion.getEffects();if(fx.size()!=1)continue;var e=fx.get(0).getEffect();if(e.getCategory()==MobEffectCategory.BENEFICIAL&&!e.isInstantenous()&&e!=MobEffects.REGENERATION)set.add(e);}
        var list=new ArrayList<>(set);
        list.sort(Comparator.<MobEffect,Boolean>comparing(e->!id(e).getNamespace().equals("minecraft")).thenComparing(e->id(e).toString()));
        return catalogue=List.copyOf(list);
    }
}
