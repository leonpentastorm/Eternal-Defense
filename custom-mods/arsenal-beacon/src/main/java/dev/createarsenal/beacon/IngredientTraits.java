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
    record Trait(Ingredient ingredient,boolean staple,Map<MealRules.Effect,Integer> effects,String family){}
    static List<Trait> traits=List.of();
    IngredientTraits(){super(new Gson(),"meal_ingredients");}
    @SubscribeEvent public void reload(AddReloadListenerEvent e){e.addListener(this);}
    @Override protected void apply(Map<ResourceLocation,JsonElement> resources,ResourceManager manager,ProfilerFiller profiler){
        var next=new ArrayList<Trait>();
        resources.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry->{try{
            var o=entry.getValue().getAsJsonObject();var effects=new EnumMap<MealRules.Effect,Integer>(MealRules.Effect.class);
            for(var e:o.getAsJsonObject("effects").entrySet()){var effect=MealRules.Effect.of(e.getKey());if(effect==null)throw new IllegalArgumentException("Unknown effect "+e.getKey());effects.put(effect,Math.max(1,Math.min(3,e.getValue().getAsInt())));}
            next.add(new Trait(Ingredient.fromJson(o.get("ingredient")),o.has("staple")&&o.get("staple").getAsBoolean(),Map.copyOf(effects),o.has("family")?o.get("family").getAsString():entry.getKey().getPath()));
        }catch(RuntimeException ex){com.mojang.logging.LogUtils.getLogger().warn("Invalid meal ingredient {}: {}",entry.getKey(),ex.getMessage());}});
        traits=List.copyOf(next);
    }
    static boolean accepts(ItemStack stack){return traits.stream().anyMatch(t->t.ingredient.test(stack));}
    /** Every effect any trait of this stack feeds (for the slot icons of the screen). */
    static EnumSet<MealRules.Effect> effectsOf(ItemStack stack){var out=EnumSet.noneOf(MealRules.Effect.class);if(!stack.isEmpty())for(var t:traits)if(t.ingredient.test(stack))out.addAll(t.effects.keySet());return out;}
    record Preview(MealData meal,int occupied,boolean staple,String problem){}
    static Preview compose(Container ingredients,boolean stew){
        var scores=new EnumMap<MealRules.Effect,Integer>(MealRules.Effect.class);boolean staple=false;var typeScores=new HashMap<net.minecraft.world.item.Item,EnumMap<MealRules.Effect,Integer>>();var families=new EnumMap<MealRules.Effect,Map<String,Set<net.minecraft.world.item.Item>>>(MealRules.Effect.class);
        for(int i=0;i<6;i++){var stack=ingredients.getItem(i);if(stack.isEmpty())continue;boolean valid=false;
            // Validate every stack; overlaps and duplicate slots merge by maximum for each item type.
            var slotScores=new EnumMap<MealRules.Effect,Integer>(MealRules.Effect.class);
            for(var trait:traits)if(trait.ingredient.test(stack)){valid=true;staple|=trait.staple;trait.effects.forEach((e,w)->{slotScores.merge(e,w,Math::max);families.computeIfAbsent(e,k->new HashMap<>()).computeIfAbsent(trait.family,k->new HashSet<>()).add(stack.getItem());});}
            if(!valid)return new Preview(null,typeScores.size(),staple,"ingredient");var merged=typeScores.computeIfAbsent(stack.getItem(),item->new EnumMap<>(MealRules.Effect.class));slotScores.forEach((e,w)->merged.merge(e,w,Math::max));
        }
        int occupied=typeScores.size();typeScores.values().forEach(type->type.forEach((e,w)->scores.merge(e,w,Integer::sum)));
        var selected=MealRules.select(scores,stew);
        String problem=occupied<2?"ingredients":!stew&&occupied>3?"sandwich_types":!stew&&!staple?"staple":selected.isEmpty()?"effects":"";
        var bonuses=selected.stream().map(e->new MealData.Bonus(e,Math.min(3,scores.get(e)),stew&&families.get(e).values().stream().anyMatch(items->items.size()>=2))).toList();
        return new Preview(problem.isEmpty()?new MealData(stew,bonuses,MealRules.FIELD_TICKS):null,occupied,staple,problem);
    }
    record Batch(int required,int available,int[] spent,String problem){}
    static Batch batch(Container ingredients,Preview preview,boolean stew,int mk){
        int[] counts=new int[6],types=new int[6];var ids=new HashMap<net.minecraft.world.item.Item,Integer>();
        for(int i=0;i<6;i++){var stack=ingredients.getItem(i);counts[i]=stack.getCount();types[i]=ids.computeIfAbsent(stack.getItem(),item->ids.size());}
        int required=stew?MealRules.tier(mk).ingredients():preview.occupied(),available=Arrays.stream(counts).sum();
        var spent=MealRules.plan(counts,types,required);
        return new Batch(required,available,spent,preview.occupied()>required?"batch_types":spent==null?"quantity":"");
    }
}
