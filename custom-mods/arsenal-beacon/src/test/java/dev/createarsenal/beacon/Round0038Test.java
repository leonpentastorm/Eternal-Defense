package dev.createarsenal.beacon;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** The owner's 0.0.38 MRE Station: the MRE holds exactly what its foods give, up to 20 nutrition, with no effects; Marines Fuel seals it. */
final class Round0038Test {
    static final String RES=Round0035Test.RES;
    static String source(String name) throws Exception{return Round0035Test.source(name);}
    static String body(String src,String from,String to){return Round0036Test.body(src,from,to);}
    static MreRules.Food f(int n,double m){return new MreRules.Food(n,(float)m);}

    /** Vanilla 1.20.1's foods (net.minecraft.world.food.Foods): nutrition and saturation modifier. */
    static final Map<String,MreRules.Food> VANILLA=new LinkedHashMap<>();
    static{
        Object[][] t={{"apple",4,.3},{"baked_potato",5,.6},{"beef",3,.3},{"beetroot",1,.6},{"beetroot_soup",6,.6},{"bread",5,.6},{"carrot",3,.6},{"chicken",2,.3},
            {"chorus_fruit",4,.3},{"cod",2,.1},{"cooked_beef",8,.8},{"cooked_chicken",6,.6},{"cooked_cod",5,.6},{"cooked_mutton",6,.8},{"cooked_porkchop",8,.8},{"cooked_rabbit",5,.6},
            {"cooked_salmon",6,.8},{"cookie",2,.1},{"dried_kelp",1,.3},{"enchanted_golden_apple",4,1.2},{"golden_apple",4,1.2},{"glow_berries",2,.1},{"golden_carrot",6,1.2},
            {"honey_bottle",6,.1},{"melon_slice",2,.3},{"mushroom_stew",6,.6},{"mutton",2,.3},{"poisonous_potato",2,.3},{"porkchop",3,.3},{"potato",1,.3},{"pufferfish",1,.1},
            {"pumpkin_pie",8,.3},{"rabbit",3,.3},{"rabbit_stew",10,.6},{"rotten_flesh",4,.1},{"salmon",2,.1},{"spider_eye",2,.8},{"suspicious_stew",6,.6},{"sweet_berries",2,.1},{"tropical_fish",1,.1}};
        for(var row:t)VANILLA.put((String)row[0],f((Integer)row[1],(Double)row[2]));
    }
    /** Every pack of up to six vanilla foods (with repeats) within 20 nutrition, from a fixed seed: 20,000 of them. */
    static List<List<MreRules.Food>> packs(){
        var all=new ArrayList<>(VANILLA.values());var rnd=new Random(38);var packs=new ArrayList<List<MreRules.Food>>();
        while(packs.size()<20000){
            var pack=new ArrayList<MreRules.Food>();int size=1+rnd.nextInt(MreRules.FOOD_SLOTS);
            for(int i=0;i<size;i++){var food=all.get(rnd.nextInt(all.size()));if(MreRules.fits(MreRules.nutrition(pack),food.nutrition()))pack.add(food);}
            if(!pack.isEmpty())packs.add(pack);
        }
        return packs;
    }
    /** Vanilla's FoodData.eat(int, float), copied: the food bar takes the nutrition (to 20), saturation the product, capped at the food bar. */
    static float[] eat(float[] bar,int nutrition,float modifier){
        int food=Math.min(nutrition+(int)bar[0],20);float sat=Math.min(bar[1]+(float)nutrition*modifier*2.0F,(float)food);return new float[]{food,sat};
    }

    // ---- no gain, no loss -------------------------------------------------------------------------------------------------------------
    @Test void theMreHoldsTheExactSumOfWhatEachFoodGivesAlone(){
        for(var pack:packs()){
            var t=MreRules.sum(pack);
            int n=0;double s=0;for(var food:pack){n+=food.nutrition();s+=(float)food.nutrition()*food.modifier()*2.0F;}   // each food's value by vanilla's own formula, in float
            assertEquals(n,t.nutrition(),"nutrition is the plain sum");
            assertEquals((float)Math.min(s,20),t.saturation(),0f,"saturation is the plain sum (the nearest float), at most 20: "+pack);
            assertTrue(Math.abs(t.saturation()-Math.min(s,20))<=Math.ulp(t.saturation()),"no gain and no loss beyond storing it as a float");
        }
        // the owner's cases by hand: 2 bread 10 / 12; a golden carrot and a cooked beef 14 / 27.2; 4 cooked beef 32 / 51.2 would pass 20 nutrition
        assertEquals(new MreRules.Totals(10,(float)((double)(5*.6f*2)+(double)(5*.6f*2))),MreRules.sum(List.of(VANILLA.get("bread"),VANILLA.get("bread"))));
        assertEquals(12f,MreRules.sum(List.of(VANILLA.get("bread"),VANILLA.get("bread"))).saturation(),1e-5f);
        assertEquals(14,MreRules.sum(List.of(VANILLA.get("golden_carrot"),VANILLA.get("cooked_beef"))).nutrition());
        assertEquals(20f,MreRules.sum(List.of(VANILLA.get("golden_carrot"),VANILLA.get("cooked_beef"))).saturation(),0f,"27.2 is stored as 20");
        assertEquals(1,MreRules.sum(List.of(VANILLA.get("dried_kelp"))).nutrition(),"one food is enough");
        assertEquals(.6f,MreRules.sum(List.of(VANILLA.get("dried_kelp"))).saturation(),1e-6f);
    }
    @Test void theOrderOfTheSlotsNeverChangesTheTotals(){
        var rnd=new Random(7);
        for(var pack:packs().subList(0,4000)){var shuffled=new ArrayList<>(pack);Collections.shuffle(shuffled,rnd);assertEquals(MreRules.sum(pack),MreRules.sum(shuffled),"bit for bit, so the MREs stack");}
    }
    @Test void eatenItRestoresExactlyWhatItHoldsThroughVanillasFormula(){
        int exact=0,stepOff=0;
        for(var pack:packs()){
            var t=MreRules.sum(pack);float m=MreRules.modifier(t.nutrition(),t.saturation());
            float back=MreRules.saturation(t.nutrition(),m);
            assertTrue(Math.abs(back-t.saturation())<=Math.ulp(t.saturation()),"vanilla's nutrition x modifier x 2 gives back the stored saturation, to the float step: "+t+" -> "+back);
            assertTrue(back<=t.saturation(),"never more than it holds: "+t);
            if(back==t.saturation())exact++;else stepOff++;
            // from an empty bar: the food bar gets the exact nutrition, saturation the stored amount up to the food bar (vanilla's cap)
            var bar=eat(new float[]{0,0},t.nutrition(),m);
            assertEquals(t.nutrition(),(int)bar[0]);assertEquals(Math.min(back,t.nutrition()),bar[1],0f);
            // with room (the bar at 20 - nutrition, no saturation) every bit of the stored saturation lands
            var roomy=eat(new float[]{20-t.nutrition(),0},t.nutrition(),m);
            assertEquals(20,(int)roomy[0]);assertEquals(back,roomy[1],0f);
            // the foods one by one from the same bar: the same food, and the same saturation unless vanilla's cap (the food bar) cut in
            // between two of them; then the MRE gives what eating them in the best order would, never less
            float[] b={20-t.nutrition(),0};boolean cut=false;
            for(var food:pack){float before=b[1];b=eat(b,food.nutrition(),food.modifier());cut|=b[1]<before+food.saturation()-1e-4f;}
            assertEquals((int)roomy[0],(int)b[0],"the food bar: "+pack);
            if(!cut)assertEquals(b[1],roomy[1],Math.ulp(20f)*4,"the same as eating the foods one by one: "+pack);
            else assertTrue(roomy[1]>=b[1]-Math.ulp(20f)*4,"never less than one by one: "+pack);
        }
        assertEquals(0f,MreRules.modifier(0,0),"nothing to restore");
        System.out.println("MRE modifier: "+exact+" packs give back the exact float, "+stepOff+" land one float step below it");
        assertTrue(exact>stepOff*4,"nearly always exact");
    }

    // ---- the 20 cap ------------------------------------------------------------------------------------------------------------------
    @Test void anMreHoldsAtMostTwentyNutritionAndTwentySaturation(){
        assertEquals(20,MreRules.MAX_NUTRITION);assertEquals(20f,MreRules.MAX_SATURATION);assertEquals(6,MreRules.FOOD_SLOTS);
        assertTrue(MreRules.fits(14,6),"14 + 6 = 20 fits");assertFalse(MreRules.fits(15,6),"15 + 6 = 21 does not");assertTrue(MreRules.fits(0,20));assertFalse(MreRules.fits(0,21));assertFalse(MreRules.fits(3,-1));
        var four=MreRules.sum(Collections.nCopies(4,VANILLA.get("bread")));assertEquals(20,four.nutrition());assertTrue(four.valid());
        var carrots=MreRules.sum(Collections.nCopies(3,VANILLA.get("golden_carrot")));assertEquals(18,carrots.nutrition());assertEquals(20f,carrots.saturation(),0f,"43.2 is stored as 20");
        assertFalse(new MreRules.Totals(21,5).valid());assertFalse(new MreRules.Totals(0,0).valid());assertFalse(new MreRules.Totals(5,20.5f).valid());assertFalse(new MreRules.Totals(5,Float.NaN).valid());
        assertFalse(f(-2,.6).valid(),"negative nutrition is refused");assertFalse(new MreRules.Food(4,Float.NaN).valid());assertTrue(f(0,.5).valid());
    }
    @Test void theSlotsAndTheServerBothRefuseAnOverflow() throws Exception{
        var station=source("MreStation.java");
        assertTrue(station.contains("@Override public boolean mayPlace(ItemStack stack){return accepts(items,getContainerSlot(),stack,player);}"),"each food slot asks the rule (its own slot left out)");
        assertTrue(body(station,"static boolean accepts(","\n    }").contains("MreRules.fits(nutritionExcept(c,slot,who),f.nutrition())"));
        var check=body(station,"static Check check(Container c,LivingEntity who){","\n    }");
        assertTrue(check.contains("if(totals.nutrition()>MreRules.MAX_NUTRITION)return new Check(\"over\",totals);"),"the server counts again on Make");
        var make=body(station,"boolean make(ServerPlayer p){","\n        }");
        assertTrue(make.indexOf("var check=check();")<make.indexOf("if(!check.ready())")&&make.indexOf("return false;")<make.indexOf("items.removeItem("),"every check before anything is used up");
        assertEquals(1,make.split("return false;",-1).length-1,"one way out, before any change");
        assertTrue(make.indexOf("items.removeItem(FUEL,1);items.removeItem(PAPER,1);")>0&&make.contains("if(!items.getItem(i).isEmpty())items.removeItem(i,1);"),"one of each food, 1 fuel, 1 paper");
        var click=body(station,"public boolean clickMenuButton(Player p,int button){","\n        }");
        assertTrue(click.contains("if(!(p instanceof ServerPlayer sp)||p!=player||!stillValid(p))return false;"),"only the server, only its own player, only at the station");
        assertTrue(source("MreStationScreen.java").contains("Ui.t(\"mre.over_tip\""),"the screen says why a food does not fit");
    }

    // ---- stacking -------------------------------------------------------------------------------------------------------------------
    @Test void mresWithTheSameTotalsStackToSixtyFour() throws Exception{
        assertEquals(64,MreRules.STACK);
        var mre=source("Mre.java");
        assertTrue(mre.contains("super(new Item.Properties().stacksTo(MreRules.STACK).food(EMPTY));"));
        var create=body(mre,"static ItemStack create(MreRules.Totals totals){","\n    }");
        assertTrue(create.contains("tag.putInt(\"Nutrition\",totals.nutrition());tag.putFloat(\"Saturation\",totals.saturation());")&&!create.contains("UUID")&&!create.contains("Time"),"the tag is the totals and nothing else, so equal totals stack");
        var a=MreRules.sum(List.of(VANILLA.get("bread"),VANILLA.get("apple")));var b=MreRules.sum(List.of(VANILLA.get("apple"),VANILLA.get("bread")));
        assertEquals(a,b);assertTrue(MreRules.outputTakes(a,63,b),"one more fits on 63");assertFalse(MreRules.outputTakes(a,64,b),"not on 64");
        assertFalse(MreRules.outputTakes(a,5,MreRules.sum(List.of(VANILLA.get("bread")))),"other totals do not");assertTrue(MreRules.outputTakes(null,0,a),"an empty output takes any");
        var check=body(source("MreStation.java"),"static Check check(Container c,LivingEntity who){","\n    }");
        assertTrue(check.contains("!ItemStack.isSameItemSameTags(out,Mre.create(totals))")&&check.contains("\"output_full\""),"the output stacks by vanilla's own same-item-same-tag rule");
    }

    // ---- food only, nothing else ------------------------------------------------------------------------------------------------------
    @Test void everyEffectAndAlwaysEdibleAreStripped() throws Exception{
        var mre=source("Mre.java");
        var props=body(mre,"public FoodProperties getFoodProperties(ItemStack stack,LivingEntity entity){","\n    }");
        assertFalse(props.contains(".effect(")||props.contains("alwaysEat")||props.contains("meat()"),"no effect, never always-edible");
        assertTrue(props.contains(".nutrition(t.nutrition()).saturationMod(MreRules.modifier(t.nutrition(),t.saturation())).fast()"),"the totals and 16 ticks only");
        assertTrue(body(mre,"static final FoodProperties EMPTY=","\n").contains(".fast().build()")&&!body(mre,"static final FoodProperties EMPTY=","\n").contains("alwaysEat"));
        assertFalse(mre.contains("finishUsingItem"),"eaten by vanilla's own path: Player.eat -> FoodData.eat with this stack's properties, nothing comes back");
        assertTrue(body(mre,"public InteractionResultHolder<ItemStack> use(","\n    }").contains("return super.use(level,player,hand);"),"vanilla's use: only when hungry (canEat(false))");
        var station=source("MreStation.java");
        assertFalse(station.contains("finishUsingItem")||station.contains(".eat("),"the foods are never eaten, so none of their own code runs (golden apples, honey, chorus fruit, suspicious stew)");
        assertTrue(body(station,"static MreRules.Food food(","\n    }").contains("stack.is(ArsenalBeacon.MRE.get())"),"an MRE does not go into an MRE");
        assertEquals(16,MreRules.EAT_TICKS);
    }
    @Test void bowlsAndBottlesComeBack() throws Exception{
        var station=source("MreStation.java");var back=body(station,"static ItemStack containerOf(ItemStack food){","\n    }");
        assertTrue(back.contains("food.hasCraftingRemainingItem()")&&back.contains("food.getCraftingRemainingItem().copy()"),"the honey bottle's glass bottle, modded bowls");
        assertTrue(back.contains("BowlFoodItem")&&back.contains("SuspiciousStewItem")&&back.contains("Items.BOWL"),"the stews' bowls");
        var make=body(station,"boolean make(ServerPlayer p){","\n        }");
        assertTrue(make.contains("for(var c:back)ArsenalBeacon.give(p,c);"),"into the pack, or dropped at the player's feet");
        assertTrue(source("ArsenalBeacon.java").contains("static void give(ServerPlayer p,ItemStack stack){if(!p.getInventory().add(stack)&&!stack.isEmpty())p.drop(stack,false);}"));
        assertTrue(body(station,"boolean takeBack(ServerPlayer p){","\n        }").contains("for(int i=0;i<SLOTS;i++)"),"Take back empties every slot");
    }

    // ---- block, items, workshop, recipe, art --------------------------------------------------------------------------------------------
    @Test void theStationSitsEighthInTheKitchenGroup() throws Exception{
        var items=WorkshopFabrication.ITEMS;int kitchen=items.indexOf("mess_hall_mk1"),support=items.indexOf("support_platform");
        assertEquals(kitchen+7,items.indexOf("mre_maker"),"the 8th of the Kitchen group");assertEquals(items.indexOf("cup_dispenser")+1,items.indexOf("mre_maker"));assertEquals(support,items.indexOf("mre_maker")+1,"its last");
        assertTrue(source("BeaconClient.java").contains("FAB_GROUP_START={0,4,12}"));
        assertEquals(4,Economy.MRE_STATION_KEY);assertEquals(8,Economy.MRE_STATION_IRON);
        var price=body(source("Economy.java"),"case \"mre_maker\"->","\n");
        assertTrue(price.contains("WeaponPlatform.cost(\"arsenal_beacon:reinforced_plating\",MRE_STATION_KEY)")&&price.contains("minecraft:smoker"),"the Mess Hall's style, plus a smoker");
        var reg=source("ArsenalBeacon.java");
        assertTrue(reg.contains("BLOCKS.register(\"mre_maker\",MreStation.StationBlock::new)")&&reg.contains("ITEMS.register(\"mre\",Mre::new)")&&reg.contains("ITEMS.register(\"marines_fuel\",Mre.MarinesFuel::new)"));
        assertTrue(source("MreStation.java").contains("StationBlock(){super(false);}"),"1 x 1 x 2 like the pots and dispensers (the upper cell a filler)");
        assertTrue(source("CreativeCatalog.java").contains("id.equals(\"mre\")"),"an MRE with no totals is not in the creative tab");
    }
    @Test void marinesFuelIsTwoFromAClayBallAndAnyDye() throws Exception{
        for(var dir:List.of(RES+"data/arsenal_beacon/recipes/")){
            var r=com.google.gson.JsonParser.parseString(Files.readString(Path.of(dir+"marines_fuel.json"))).getAsJsonObject();
            assertEquals("minecraft:crafting_shapeless",r.get("type").getAsString());
            assertEquals("[{\"item\":\"minecraft:clay_ball\"},{\"tag\":\"forge:dyes\"}]",r.get("ingredients").toString());
            assertEquals(2,r.getAsJsonObject("result").get("count").getAsInt());assertEquals("arsenal_beacon:marines_fuel",r.getAsJsonObject("result").get("item").getAsString());
        }
        assertTrue(source("Mre.java").contains("MarinesFuel(){super(new Item.Properties());}"),"stacks to 64, not food");
    }
    @Test void theOwnersArtIsInstalledAsSupplied() throws Exception{
        assertTrue(Files.readString(Path.of(RES+"assets/arsenal_beacon/models/block/mre_maker.json")).contains("\"credit\": \"Create Arsenal - remodel\""));
        assertTrue(Files.readString(Path.of(RES+"assets/arsenal_beacon/models/item/mre.json")).contains("arsenal_beacon:item/mre"));
        assertTrue(Files.readString(Path.of(RES+"assets/arsenal_beacon/models/item/mre_maker.json")).contains("arsenal_beacon:block/mre_maker"));
        assertTrue(Files.readString(Path.of(RES+"assets/arsenal_beacon/blockstates/mre_maker.json")).contains("arsenal_beacon:block/mre_maker"));
        for(var t:List.of("block/mre_maker","item/mre","item/marines_fuel"))assertTrue(Files.exists(Path.of(RES+"assets/arsenal_beacon/textures/"+t+".png")),t);
        assertTrue(Files.readString(Path.of(RES+"data/minecraft/tags/blocks/mineable/pickaxe.json")).contains("arsenal_beacon:mre_maker"));
        assertTrue(Files.exists(Path.of(RES+"data/arsenal_beacon/loot_tables/blocks/mre_maker.json")));
    }
    @Test void theArmorPlatformNeverSellsTheMre() throws Exception{
        var allowed=body(source("ArmorPlatform.java"),"static boolean allowed(Item item){","\n    }");
        // only the listed gear mods' items are sold, and this mod is not one of them (the MRE's id contains "mre", which the gear words match)
        assertTrue(allowed.contains("{\n        if(!MODS.contains(BuiltInRegistries.ITEM.getKey(item).getNamespace())"),"the first check: a listed gear mod");
        assertFalse(ArmorPlatform.MODS.contains(ArsenalBeacon.ID));
    }

    // ---- what the player reads ---------------------------------------------------------------------------------------------------------
    @Test void theGuideAndTheScreenSayWhatTheCodeDoes() throws Exception{
        var json=com.google.gson.JsonParser.parseString(Round0035Test.lang()).getAsJsonObject();
        java.util.function.Function<String,String> t=k->json.get(k).getAsString();
        assertEquals("MRE Station",t.apply("block.arsenal_beacon.mre_maker"));assertEquals("MRE",t.apply("item.arsenal_beacon.mre"));assertEquals("Marines Fuel",t.apply("item.arsenal_beacon.marines_fuel"));
        assertTrue(List.of(GuideText.IDS).contains("mre"));
        var guide=t.apply("gui.arsenal_beacon.guide.mre.body");var detail=t.apply("gui.arsenal_beacon.guide.mre.detail");
        for(var h:List.of("# MRE Station","# MRE","# Marines Fuel"))assertTrue(guide.contains(h+"\n"),h);
        assertTrue(guide.contains("at most "+MreRules.MAX_NUTRITION+" nutrition")&&guide.contains("go past "+MreRules.MAX_NUTRITION),"the cap");
        assertTrue(guide.contains("up to six foods")&&guide.contains("1 Marines Fuel and 1 Paper"),"what one MRE uses");
        assertTrue(guide.contains("Craft 2 from a clay ball and any dye"),"the recipe");
        assertTrue(guide.contains("stack to "+MreRules.STACK)&&guide.contains("only when you are hungry")&&guide.contains("Every effect is lost"));
        assertTrue(detail.contains(MreRules.EAT_TICKS+" ticks")&&detail.contains("at most "+(int)MreRules.MAX_SATURATION)&&detail.contains("nutrition x saturation modifier x 2"));
        assertTrue(detail.contains("- MRE Station: "+Economy.MRE_STATION_KEY+" Reinforced Beacon Plating, "+Economy.MRE_STATION_IRON+" iron ingots and a smoker"));
        for(var k:List.of("no_food","not_food","over","no_nutrition","no_fuel","no_paper","output_other","output_full"))assertTrue(json.has("gui.arsenal_beacon.mre.problem."+k),k);
        var station=source("MreStation.java");
        for(var k:List.of("\"not_food\"","\"no_food\"","\"over\"","\"no_nutrition\"","\"no_fuel\"","\"no_paper\"","\"output_other\"","\"output_full\""))assertTrue(station.contains(k),k);
    }
    @Test void theNumbersReadTheSameEverywhere(){
        assertEquals("16.8",MreRules.number(16.8f));assertEquals("12",MreRules.number(12.000001f));assertEquals("0.35",MreRules.number(.35f));assertEquals("20",MreRules.number(20f));assertEquals("0",MreRules.number(0f));
    }
}
