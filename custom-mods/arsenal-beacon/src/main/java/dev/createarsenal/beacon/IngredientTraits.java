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
    static Preview compose(Container ingredients,boolean stew){return compose(ingredients,stew,1);}
    static String groupOf(ItemStack stack){return traits.stream().filter(t->t.ingredient.test(stack)).map(Trait::family).findFirst().orElse("");}
    /** Ordinary by default. Legendary recipes reserve their groups only after explicit selection. */
    static Preview compose(Container ingredients,boolean stew,int mk){return compose(ingredients,stew,mk,null);}
    static Preview compose(Container ingredients,boolean stew,int mk,Set<MealRules.Effect> selected){
        var foods=new LinkedHashMap<net.minecraft.world.item.Item,EnumMap<MealRules.Effect,Integer>>();
        var groups=new HashMap<String,Set<net.minecraft.world.item.Item>>();boolean staple=false;
        for(int i=0;i<6;i++){
            var stack=ingredients.getItem(i);if(stack.isEmpty())continue;
            var matches=traits.stream().filter(t->t.ingredient.test(stack)).toList();
            if(matches.isEmpty())return new Preview(null,foods.size(),staple,"ingredient");
            var scores=foods.computeIfAbsent(stack.getItem(),item->new EnumMap<>(MealRules.Effect.class));
            groups.computeIfAbsent(matches.get(0).family,k->new HashSet<>()).add(stack.getItem());
            for(var trait:matches){staple|=trait.staple;trait.effects.forEach((e,w)->{if(!e.gun())scores.merge(e,w,Math::max);});}
        }
        int limit=stew?3:2;var bonuses=new ArrayList<MealData.Bonus>();var used=new HashSet<net.minecraft.world.item.Item>();var reserved=new HashSet<String>();
        if(selected!=null){
            if(selected.isEmpty()||selected.size()>limit)return new Preview(null,foods.size(),staple,"selection");
            for(var mix:MealRules.MIXES)if(selected.contains(mix.effect())){
                var first=groups.get(mix.first());var second=groups.get(mix.second());
                if(first==null||second==null||!reserved.add(mix.first())||!reserved.add(mix.second()))return new Preview(null,foods.size(),staple,"selection");
                used.addAll(first);used.addAll(second);
                bonuses.add(new MealData.Bonus(mix.effect(),1,MealRules.doubles(stew,mk,first.size(),second.size(),true)));
            }
        }
        var scores=new EnumMap<MealRules.Effect,Integer>(MealRules.Effect.class);
        foods.forEach((item,values)->{if(!used.contains(item))values.forEach((e,w)->scores.merge(e,w,Integer::sum));});
        var ordinary=selected==null?MealRules.select(scores,stew):selected.stream().filter(e->!e.gun()).sorted().toList();
        for(var e:ordinary){
            if(!scores.containsKey(e))return new Preview(null,foods.size(),staple,"selection");
            boolean doubled=groups.values().stream().anyMatch(items->items.stream().filter(item->!used.contains(item)&&foods.get(item).containsKey(e)).count()>=2);
            bonuses.add(new MealData.Bonus(e,1,MealRules.doubles(stew,mk,doubled?2:1,0,false)));
        }
        int occupied=foods.size();
        String problem=occupied<2?"ingredients":!stew&&occupied>3?"sandwich_types":bonuses.isEmpty()?"effects":"";
        return new Preview(problem.isEmpty()?new MealData(stew,bonuses.stream().limit(limit).toList(),MealRules.FIELD_TICKS):null,occupied,staple,problem);
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
