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
    /** {@code unmet}: chosen effects the table cannot make yet (the problem is then "missing"). */
    record Preview(MealData meal,int occupied,boolean staple,String problem,EnumSet<MealRules.Effect> unmet,List<MealData.Bonus> bonuses){
        Preview(MealData meal,int occupied,boolean staple,String problem){this(meal,occupied,staple,problem,EnumSet.noneOf(MealRules.Effect.class),List.of());}
    }
    static Preview compose(Container ingredients,boolean stew){return compose(ingredients,stew,1);}
    static String groupOf(ItemStack stack){return traits.stream().filter(t->t.ingredient.test(stack)).map(Trait::family).findFirst().orElse("");}
    static String key(ItemStack stack){return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();}
    /** The planner's view of one stack: group of the first matching trait, effect weights merged by maximum, gun effects never score. Null without a trait. */
    static MealPlanner.Food foodOf(ItemStack stack){
        if(stack.isEmpty())return null;
        var matches=traits.stream().filter(t->t.ingredient.test(stack)).toList();if(matches.isEmpty())return null;
        var weights=new EnumMap<MealRules.Effect,Integer>(MealRules.Effect.class);boolean staple=false;
        for(var trait:matches){staple|=trait.staple;trait.effects.forEach((e,w)->{if(!e.gun())weights.merge(e,w,Math::max);});}
        return new MealPlanner.Food(key(stack),matches.get(0).family,weights,staple,stack.getMaxStackSize());
    }
    /** Ordinary by default. Legendary recipes reserve their groups only after explicit selection. */
    static Preview compose(Container ingredients,boolean stew,int mk){return compose(ingredients,stew,mk,null);}
    static Preview compose(Container ingredients,boolean stew,int mk,Set<MealRules.Effect> selected){
        var table=new ArrayList<MealPlanner.Food>();var kinds=new HashSet<String>();boolean staple=false;
        for(int i=0;i<MealRules.tier(mk).slots();i++){
            var stack=ingredients.getItem(i);if(stack.isEmpty())continue;
            var food=foodOf(stack);
            if(food==null)return new Preview(null,kinds.size(),staple,"ingredient");
            table.add(food);kinds.add(food.key());staple|=food.staple();
        }
        var eval=MealPlanner.evaluate(table,stew,mk,selected);
        return new Preview(eval.meal(),eval.occupied(),eval.staple(),eval.problem(),eval.unmet(),eval.bonuses());
    }
    /** Every food any trait accepts, built once per datapack reload by testing the item registry. The kitchen sends it to the client for its recipe tooltips. */
    record Catalogue(Map<String,MealPlanner.Food> foods,Map<String,net.minecraft.world.item.Item> items){}
    private static List<Trait> catalogued;private static Catalogue catalogue=new Catalogue(Map.of(),Map.of());
    static synchronized Catalogue catalogue(){
        if(catalogued==traits)return catalogue;
        var foods=new TreeMap<String,MealPlanner.Food>();var items=new HashMap<String,net.minecraft.world.item.Item>();
        for(var item:net.minecraft.core.registries.BuiltInRegistries.ITEM){
            var stack=new ItemStack(item);var food=foodOf(stack);if(food!=null){foods.put(food.key(),food);items.put(food.key(),item);}
        }
        catalogued=traits;catalogue=new Catalogue(Collections.unmodifiableMap(foods),Map.copyOf(items));return catalogue;
    }
    /** All distinct contributors, grouped by the actual selected legendary effect. */
    record Link(MealRules.Mix mix,int[] slots,boolean doubled){}
    static List<Link> legendaryLinks(Container ingredients,MealData meal){
        if(meal==null)return List.of();var byGroup=new HashMap<String,List<Integer>>();var seen=new HashSet<net.minecraft.world.item.Item>();
        for(int i=0;i<6;i++){var stack=ingredients.getItem(i);if(!stack.isEmpty()&&seen.add(stack.getItem()))byGroup.computeIfAbsent(groupOf(stack),key->new ArrayList<>()).add(i);}
        var out=new ArrayList<Link>();for(var bonus:meal.bonuses())for(var mix:MealRules.MIXES)if(bonus.effect()==mix.effect()&&byGroup.containsKey(mix.first())&&byGroup.containsKey(mix.second())){
            var slots=new ArrayList<>(byGroup.get(mix.first()));slots.addAll(byGroup.get(mix.second()));out.add(new Link(mix,slots.stream().mapToInt(Integer::intValue).toArray(),bonus.pair()));
        }
        return List.copyOf(out);
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
