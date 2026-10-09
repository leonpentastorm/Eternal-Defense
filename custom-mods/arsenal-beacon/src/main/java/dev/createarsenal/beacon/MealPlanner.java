package dev.createarsenal.beacon;

import java.util.*;

/**
 * The kitchen's recipe logic without Minecraft types: what a table of foods makes for an order, and which foods to fetch
 * so that it makes exactly that order. {@link IngredientTraits#compose} and the Gather button both run this code, so a plan can
 * never promise a meal the preparation step would then refuse.
 */
final class MealPlanner {
    private MealPlanner(){}
    static final int SLOTS=6,BUDGET=12000;
    /** One kind of food: its registry id, its canonical group, the ordinary effects it feeds (never gun effects) and how many fit one slot. */
    record Food(String key,String group,Map<MealRules.Effect,Integer> weights,boolean staple,int stackLimit){
        Food {weights=Map.copyOf(weights);}
        boolean feeds(MealRules.Effect e){return weights.containsKey(e);}
    }
    /** {@code unmet}: chosen effects the table cannot make yet; the meal exists only when nothing is wrong, {@code bonuses} is what the table already makes. */
    record Eval(MealData meal,int occupied,boolean staple,String problem,EnumSet<MealRules.Effect> unmet,List<MealData.Bonus> bonuses){}

    /** {@code selected == null}: ordinary effects ranked by score. Otherwise exactly the chosen effects; legendary ones reserve both their groups. */
    static Eval evaluate(Collection<Food> table,boolean stew,int mk,Set<MealRules.Effect> selected){
        var foods=new LinkedHashMap<String,Map<MealRules.Effect,Integer>>();var groups=new HashMap<String,Set<String>>();boolean staple=false;
        for(var food:table){
            var scores=foods.computeIfAbsent(food.key,k->new EnumMap<>(MealRules.Effect.class));
            groups.computeIfAbsent(food.group,k->new HashSet<>()).add(food.key);
            staple|=food.staple;food.weights.forEach((e,w)->scores.merge(e,w,Math::max));
        }
        int limit=stew?3:2,occupied=foods.size();var bonuses=new ArrayList<MealData.Bonus>();var used=new HashSet<String>();var reserved=new HashSet<String>();
        var unmet=EnumSet.noneOf(MealRules.Effect.class);
        if(selected!=null){
            if(selected.isEmpty()||selected.size()>limit)return new Eval(null,occupied,staple,"selection",unmet,List.of());
            for(var mix:MealRules.MIXES)if(selected.contains(mix.effect())){
                if(!reserved.add(mix.first())||!reserved.add(mix.second()))return new Eval(null,occupied,staple,"selection",unmet,List.of());
                var first=groups.get(mix.first());var second=groups.get(mix.second());
                if(first==null||second==null){unmet.add(mix.effect());continue;}
                used.addAll(first);used.addAll(second);
                bonuses.add(new MealData.Bonus(mix.effect(),1,MealRules.doubles(stew,mk,first.size(),second.size(),true)));
            }
        }
        var scores=new EnumMap<MealRules.Effect,Integer>(MealRules.Effect.class);
        foods.forEach((key,values)->{if(!used.contains(key))values.forEach((e,w)->scores.merge(e,w,Integer::sum));});
        var ordinary=selected==null?MealRules.select(scores,stew):selected.stream().filter(e->!e.gun()).sorted().toList();
        for(var e:ordinary){
            if(!scores.containsKey(e)){unmet.add(e);continue;}
            boolean doubled=groups.values().stream().anyMatch(items->items.stream().filter(key->!used.contains(key)&&foods.get(key).containsKey(e)).count()>=2);
            bonuses.add(new MealData.Bonus(e,1,MealRules.doubles(stew,mk,doubled?2:1,0,false)));
        }
        String problem=!unmet.isEmpty()?"missing":occupied<2?"ingredients":!stew&&occupied>3?"sandwich_types":bonuses.isEmpty()?"effects":"";
        var made=bonuses.stream().limit(limit).toList();
        return new Eval(problem.isEmpty()?new MealData(stew,made,MealRules.FIELD_TICKS):null,occupied,staple,problem,unmet,made);
    }

    /** {@code units} lines up with {@code keys}: what to put in each slot. {@code covered}: the pool can pay the whole batch. */
    record Plan(List<String> keys,int[] units,int doubled,boolean covered,Eval eval){}

    /**
     * Foods that make exactly {@code order}. {@code stock} is every unit of food the cook can reach (table and pack), {@code onTable} the kinds already
     * in the slots. {@code optimize == false} stops at the first valid table, which is what cheap yes/no checks need.
     * Null when the order cannot be made from this stock, or at all (shared groups, too many food types for the tier).
     */
    static Plan plan(Set<MealRules.Effect> order,boolean stew,int mk,Map<String,Food> catalogue,Map<String,Integer> stock,Set<String> onTable,boolean optimize){
        if(order.isEmpty()||order.size()>(stew?3:2))return null;
        var legendary=new ArrayList<MealRules.Mix>();var ordinary=new ArrayList<MealRules.Effect>();var reserved=new HashSet<String>();
        for(var mix:MealRules.MIXES)if(order.contains(mix.effect())){if(!reserved.add(mix.first())||!reserved.add(mix.second()))return null;legendary.add(mix);}
        for(var e:new TreeSet<>(order))if(!e.gun())ordinary.add(e);
        int required=stew?MealRules.tier(mk).ingredients():0,cap=stew?Math.min(SLOTS,required):3;boolean doubling=stew&&mk>=4;
        Comparator<String> rank=(a,b)->{
            int onA=onTable.contains(a)?0:1,onB=onTable.contains(b)?0:1;if(onA!=onB)return onA-onB;
            int sa=usable(catalogue,stock,a),sb=usable(catalogue,stock,b);return sa!=sb?sb-sa:a.compareTo(b);
        };
        var byGroup=new TreeMap<String,List<String>>();
        for(var key:new TreeSet<>(stock.keySet())){var food=catalogue.get(key);if(food!=null&&usable(catalogue,stock,key)>0)byGroup.computeIfAbsent(food.group,g->new ArrayList<>()).add(key);}
        byGroup.values().forEach(list->list.sort(rank));
        var steps=new ArrayList<List<List<String>>>();
        for(var mix:legendary){
            var a=top(byGroup.get(mix.first()),3);var b=top(byGroup.get(mix.second()),3);if(a.isEmpty()||b.isEmpty())return null;
            var options=new ArrayList<List<String>>();
            if(doubling)for(var pa:pairs(a))for(var pb:pairs(b)){var quad=new ArrayList<>(pa);quad.addAll(pb);options.add(quad);}
            for(var x:a)for(var y:b)options.add(List.of(x,y));
            steps.add(options);
        }
        for(var e:ordinary){
            var feeders=new ArrayList<String>();
            byGroup.forEach((group,keys)->{if(!reserved.contains(group))for(var key:keys)if(catalogue.get(key).feeds(e))feeders.add(key);});
            if(feeders.isEmpty())return null;
            var options=new ArrayList<List<String>>();
            if(doubling)byGroup.forEach((group,keys)->{if(!reserved.contains(group))options.addAll(pairs(top(keys.stream().filter(k->catalogue.get(k).feeds(e)).toList(),4)));});
            feeders.sort(rank);for(var key:top(feeders,5))options.add(List.of(key));
            steps.add(options);
        }
        var search=new Search(order,stew,mk,catalogue,stock,onTable,required,cap,steps,rank,optimize);search.walk(0,new LinkedHashSet<>());
        return search.best;
    }
    /** Units of one kind that can actually sit in its single slot. */
    static int usable(Map<String,Food> catalogue,Map<String,Integer> stock,String key){var food=catalogue.get(key);return food==null?0:Math.min(stock.getOrDefault(key,0),food.stackLimit);}
    private static <T> List<T> top(List<T> list,int n){return list==null?List.of():list.subList(0,Math.min(n,list.size()));}
    private static List<List<String>> pairs(List<String> keys){var out=new ArrayList<List<String>>();for(int i=0;i<keys.size();i++)for(int j=i+1;j<keys.size();j++)out.add(List.of(keys.get(i),keys.get(j)));return out;}

    private static final class Search {
        final Set<MealRules.Effect> order;final boolean stew;final int mk,required,cap;final Map<String,Food> catalogue;final Map<String,Integer> stock;final Set<String> onTable;
        final List<List<List<String>>> steps;final Comparator<String> rank;final boolean optimize;Plan best;int leaves;
        Search(Set<MealRules.Effect> order,boolean stew,int mk,Map<String,Food> catalogue,Map<String,Integer> stock,Set<String> onTable,int required,int cap,List<List<List<String>>> steps,Comparator<String> rank,boolean optimize){
            this.order=order;this.stew=stew;this.mk=mk;this.catalogue=catalogue;this.stock=stock;this.onTable=onTable;this.required=required;this.cap=cap;this.steps=steps;this.rank=rank;this.optimize=optimize;
        }
        boolean done(){return leaves>=BUDGET||!optimize&&best!=null;}
        void walk(int step,LinkedHashSet<String> chosen){
            if(done())return;
            if(step==steps.size()){leaf(chosen);return;}
            for(var option:steps.get(step)){
                var next=new LinkedHashSet<>(chosen);next.addAll(option);if(next.size()>cap)continue;
                walk(step+1,next);if(done())return;
            }
        }
        int sum(Collection<String> keys){int total=0;for(var key:keys)total+=usable(catalogue,stock,key);return total;}
        void leaf(LinkedHashSet<String> chosen){
            leaves++;var set=new LinkedHashSet<>(chosen);
            // The table needs two kinds, and a stew needs enough units: add the best remaining fillers. Extra foods never break an explicit order.
            var fillers=catalogue.keySet().stream().filter(k->!set.contains(k)&&usable(catalogue,stock,k)>0).sorted(rank.thenComparing(Comparator.naturalOrder())).toList();
            for(var key:fillers){if(set.size()>=cap||set.size()>=2&&(!stew||sum(set)>=required))break;set.add(key);}
            var eval=evaluate(set.stream().map(catalogue::get).toList(),stew,mk,order);
            if(!eval.unmet().isEmpty()||!eval.problem().isEmpty())return;
            boolean covered=!stew||sum(set)>=required;int doubled=(int)eval.meal().bonuses().stream().filter(MealData.Bonus::pair).count();
            var keys=List.copyOf(set);var units=new int[keys.size()];
            if(stew){
                var counts=new int[keys.size()];var types=new int[keys.size()];for(int i=0;i<counts.length;i++){counts[i]=usable(catalogue,stock,keys.get(i));types[i]=i;}
                var spent=MealRules.plan(counts,types,required);units=spent==null?counts:spent;
            }else Arrays.fill(units,1);
            var plan=new Plan(keys,units,doubled,covered,eval);
            if(best==null||better(plan,best))best=plan;
        }
        /** Covered batches first, then more doubled effects, then fewer kinds, then foods already on the table, then deeper stock. */
        boolean better(Plan a,Plan b){
            if(a.covered!=b.covered)return a.covered;
            if(a.doubled!=b.doubled)return a.doubled>b.doubled;
            if(a.keys.size()!=b.keys.size())return a.keys.size()<b.keys.size();
            int ta=(int)a.keys.stream().filter(onTable::contains).count(),tb=(int)b.keys.stream().filter(onTable::contains).count();if(ta!=tb)return ta>tb;
            int sa=sum(a.keys),sb=sum(b.keys);if(sa!=sb)return sa>sb;
            return a.keys.toString().compareTo(b.keys.toString())<0;
        }
    }
}
