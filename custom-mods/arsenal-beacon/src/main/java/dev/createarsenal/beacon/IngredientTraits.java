package dev.createarsenal.beacon;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.*;

/** Datapacks add food traits under data/<namespace>/meal_ingredients/*.json. No recipe explosion. */
final class IngredientTraits extends SimpleJsonResourceReloadListener {
    record Trait(Ingredient ingredient,boolean staple,Map<MealRules.Effect,Integer> effects){}
    static List<Trait> traits=List.of();
    IngredientTraits(){super(new Gson(),"meal_ingredients");}
    @SubscribeEvent public void reload(AddReloadListenerEvent e){e.addListener(this);}
    @Override protected void apply(Map<ResourceLocation,JsonElement> resources,ResourceManager manager,ProfilerFiller profiler){
        var next=new ArrayList<Trait>();
        resources.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry->{try{
            var o=entry.getValue().getAsJsonObject();var effects=new EnumMap<MealRules.Effect,Integer>(MealRules.Effect.class);
            for(var e:o.getAsJsonObject("effects").entrySet()){var effect=MealRules.Effect.of(e.getKey());if(effect==null)throw new IllegalArgumentException("Unknown effect "+e.getKey());effects.put(effect,Math.max(1,Math.min(3,e.getValue().getAsInt())));}
            next.add(new Trait(Ingredient.fromJson(o.get("ingredient")),o.has("staple")&&o.get("staple").getAsBoolean(),Map.copyOf(effects)));
        }catch(RuntimeException ex){com.mojang.logging.LogUtils.getLogger().warn("Invalid meal ingredient {}: {}",entry.getKey(),ex.getMessage());}});
        traits=List.copyOf(next);
    }
    static boolean accepts(ItemStack stack){return traits.stream().anyMatch(t->t.ingredient.test(stack));}
    record Preview(MealData meal,int occupied,boolean staple,String problem){}
    static Preview compose(Container ingredients,boolean stew){
        var scores=new EnumMap<MealRules.Effect,Integer>(MealRules.Effect.class);int occupied=0;boolean staple=false;
        for(int i=0;i<6;i++){var stack=ingredients.getItem(i);if(stack.isEmpty())continue;occupied++;boolean valid=false;
            // Overlapping definitions merge by maximum, avoiding accidental double scoring of item + tag rules.
            var slotScores=new EnumMap<MealRules.Effect,Integer>(MealRules.Effect.class);
            for(var trait:traits)if(trait.ingredient.test(stack)){valid=true;staple|=trait.staple;trait.effects.forEach((e,w)->slotScores.merge(e,w,Math::max));}
            if(!valid)return new Preview(null,occupied,staple,"ingredient");slotScores.forEach((e,w)->scores.merge(e,w,Integer::sum));
        }
        var selected=MealRules.select(scores,stew);
        String problem=occupied<2?"ingredients":!stew&&!staple?"staple":selected.isEmpty()?"effects":"";
        var bonuses=selected.stream().map(e->new MealData.Bonus(e,Math.min(3,scores.get(e)))).toList();
        return new Preview(problem.isEmpty()?new MealData(stew,bonuses,MealRules.FIELD_TICKS):null,occupied,staple,problem);
    }
}
