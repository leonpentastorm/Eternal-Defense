package dev.eternaldefense.beacon;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/** Fair prices for any gun pack: each gun's damage output against the median of its Age, a rare part for one far above it, and every Age set per item in one file. */
final class CraftBalance {
    private CraftBalance(){}

    /** The rules (data/eternal_defense/balance.json) */
    record Settings(double outlierFrom,double step,int maxUnits,int minPeers,List<String> rare,Map<Integer,String> material,Map<Integer,Integer> perUnit,Set<String> keptPacks){}
    private static Settings settings;
    static Settings settings(){
        if(settings!=null)return settings;
        var root=json("/data/eternal_defense/balance.json");
        var rare=new ArrayList<String>();root.getAsJsonArray("rare_items").forEach(e->rare.add(e.getAsString()));
        var material=new HashMap<Integer,String>();root.getAsJsonObject("rare_by_age").entrySet().forEach(e->material.put(Integer.parseInt(e.getKey()),e.getValue().getAsString()));
        var perUnit=new HashMap<Integer,Integer>();root.getAsJsonObject("per_unit_by_age").entrySet().forEach(e->perUnit.put(Integer.parseInt(e.getKey()),e.getValue().getAsInt()));
        var kept=new HashSet<String>();if(root.has("damage_attachments_kept_from"))root.getAsJsonArray("damage_attachments_kept_from").forEach(e->kept.add(e.getAsString()));
        return settings=new Settings(root.get("outlier_from").getAsDouble(),root.get("step").getAsDouble(),root.get("max_units").getAsInt(),root.get("min_peers").getAsInt(),List.copyOf(rare),Map.copyOf(material),Map.copyOf(perUnit),Set.copyOf(kept));
    }

    /** Ages set by hand: data/eternal_defense/age_overrides.json, then config/eternal-defense-ages.json; one line an item ("bf1:sw_model3": 1) */
    private static Map<String,Integer> overrides;
    static Map<String,Integer> overrides(){
        if(overrides!=null)return overrides;
        var all=new HashMap<String,Integer>();read(json("/data/eternal_defense/age_overrides.json"),all);
        try{var file=net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().resolve("eternal-defense-ages.json");
            if(Files.isRegularFile(file))try(var in=Files.newBufferedReader(file,StandardCharsets.UTF_8)){read(JsonParser.parseReader(in).getAsJsonObject(),all);}}
        catch(Exception|LinkageError ex){com.mojang.logging.LogUtils.getLogger().warn("[Eternal Defense] eternal-defense-ages.json could not be read: {}",ex.toString());}
        return overrides=Map.copyOf(all);
    }
    /** Reads the Age lines and the balance file again (the test command); the damage figures are counted anew */
    static int reload(){settings=null;overrides=null;countedFor=null;knownFor=null;known=null;POWER.clear();medians=null;peers=null;ArmorBalance.reload();ArmorPlatform.reset();return overrides().size();}
    /** The guns counted in each Age's median (after medians()) */
    static Map<Integer,Integer> peers(){return peers==null?Map.of():peers;}
    private static void read(JsonObject root,Map<String,Integer> into){for(var e:root.entrySet())if(!e.getKey().startsWith("_")&&e.getValue().isJsonPrimitive())into.put(e.getKey(),Math.max(1,Math.min(5,e.getValue().getAsInt())));}
    /** The Age set by hand for any of these ids (item, gun, ammo or recipe id), or null */
    static Integer override(String... ids){var o=overrides();for(String id:ids)if(id!=null&&o.containsKey(id))return o.get(id);return null;}
    static JsonObject json(String path){
        try(var in=CraftBalance.class.getResourceAsStream(path)){return JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(in),StandardCharsets.UTF_8)).getAsJsonObject();}
        catch(Exception e){throw new IllegalStateException("Cannot load "+path,e);}
    }

    // Damage output (pure)
    /** Pure: damage a second over a full magazine and its reload: shots of shot damage at rpm, a bolt to work between shots, then the reload */
    static double sustained(double shot,double rpm,int magazine,double reload,boolean manualAction,double boltTime){
        if(shot<=0||rpm<=0)return 0;
        double interval=60/rpm;if(manualAction)interval=Math.max(interval,boltTime);
        int mag=magazine<=0?1:magazine>1000?100:magazine;   // Endless feeds count as a large drum
        return mag*shot/(mag*interval+Math.max(.2,reload));
    }
    /** Pure: the median of the values (0 for none) */
    static double median(List<Double> values){
        if(values.isEmpty())return 0;var v=new ArrayList<>(values);Collections.sort(v);int n=v.size();
        return n%2==1?v.get(n/2):(v.get(n/2-1)+v.get(n/2))/2;
    }
    /** Pure: rare parts for a gun this many times its Age's median: none below the threshold, one more each step, at most the cap */
    static int units(double ratio,double from,double step,int max){return ratio<from?0:Math.min(max,1+(int)Math.floor((ratio-from)/Math.max(.01,step)));}
    /** Pure: the Age of an unknown gun: the latest Age whose median it reaches (Age 1 below them all) */
    static int autoAge(double power,Map<Integer,Double> medians){
        if(medians.isEmpty()||power<=0)return 5;int age=1;
        for(int a=1;a<=5;a++){Double m=medians.get(a);if(m!=null&&m>0&&power>=m)age=a;}
        return age;
    }

    // Damage output of an installed gun (TaCZ data), cached per recipe load
    private static Object countedFor;private static final Map<String,Double> POWER=new HashMap<>();private static Map<Integer,Double> medians;private static Map<Integer,Integer> peers;
    private static void fresh(RecipeManager recipes){if(countedFor!=recipes){countedFor=recipes;POWER.clear();medians=null;peers=null;}}
    static double power(String gunId){
        return POWER.computeIfAbsent(gunId,id->{
            var rl=ResourceLocation.tryParse(id);if(rl==null)return 0d;
            return com.tacz.guns.api.TimelessAPI.getCommonGunIndex(rl).map(index->{
                var data=index.getGunData();var bullet=data.getBulletData();double shot=bullet.getDamageAmount();
                var extra=bullet.getExtraDamage();if(extra!=null&&extra.getDamageAdjust()!=null&&!extra.getDamageAdjust().isEmpty())shot=extra.getDamageAdjust().getFirst().getDamage();
                var ex=bullet.getExplosionData();if(ex!=null&&ex.isExplode())shot+=ex.getDamage();
                var reload=data.getReloadData();double cool=reload==null||reload.getCooldown()==null?2.5:reload.getCooldown().getEmptyTime();
                return sustained(shot,data.getRoundsPerMinute(),data.getAmmoAmount(),cool,data.getBolt()==com.tacz.guns.resource.pojo.data.gun.Bolt.MANUAL_ACTION,data.getBoltActionTime());
            }).orElse(0d);
        });
    }
    static String gunId(WeaponPlatform.Entry e){var t=e.output().getTag();return t!=null&&t.contains("GunId")?t.getString("GunId"):e.gate().id();}
    /** The median damage output of each Age's guns (Ages with too few guns have none) */
    static Map<Integer,Double> medians(RecipeManager recipes){
        fresh(recipes);if(medians!=null)return medians;
        var by=new HashMap<Integer,List<Double>>();
        for(var e:WeaponPlatform.entries(recipes,"gun",""))if(!CreateUnlocks.armory(e)){double p=power(gunId(e));if(p>0)by.computeIfAbsent(e.gate().age(),k->new ArrayList<>()).add(p);}
        var m=new HashMap<Integer,Double>();var n=new HashMap<Integer,Integer>();int min=settings().minPeers();
        by.forEach((age,list)->{n.put(age,list.size());if(list.size()>=min)m.put(age,median(list));});
        peers=Map.copyOf(n);return medians=Map.copyOf(m);
    }
    /** Catalogued guns only, for placing the unknown ones */
    private static Map<Integer,Double> known;private static Object knownFor;
    static Map<Integer,Double> knownMedians(List<WeaponPlatform.Entry> catalogued,RecipeManager recipes){
        if(knownFor==recipes&&known!=null)return known;fresh(recipes);
        var by=new HashMap<Integer,List<Double>>();for(var e:catalogued){double p=power(gunId(e));if(p>0)by.computeIfAbsent(e.gate().age(),k->new ArrayList<>()).add(p);}
        var m=new HashMap<Integer,Double>();by.forEach((a,l)->m.put(a,median(l)));knownFor=recipes;return known=Map.copyOf(m);
    }
    /** How many times its Age's median this gun's damage output is (0 when unknown) */
    static double ratio(WeaponPlatform.Entry e){
        var server=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();if(server==null)return 0;
        var m=medians(server.getRecipeManager()).get(e.gate().age());double p=power(gunId(e));
        return m==null||m<=0||p<=0?0:p/m;
    }
    /** The rare part a gun far above its Age's median needs, unless its author already asks for a rare item; null for none */
    static WeaponPlatform.Cost surcharge(WeaponPlatform.Entry e,List<WeaponPlatform.Cost> authorCosts){
        if(!e.gate().kind().equals("gun"))return null;
        var s=settings();int units=units(ratio(e),s.outlierFrom(),s.step(),s.maxUnits());if(units<=0)return null;
        for(var cost:authorCosts)for(String id:s.rare()){var item=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(new ResourceLocation(id));if(item!=net.minecraft.world.item.Items.AIR&&cost.ingredient().test(new ItemStack(item)))return null;}
        int age=Math.max(1,Math.min(5,e.gate().age()));String material=s.material().getOrDefault(age,"minecraft:diamond");
        return WeaponPlatform.cost(material,units*s.perUnit().getOrDefault(age,1));
    }
    /** The table's list with each entry's final Age: unknown guns placed by their damage output, then the hand-set Ages */
    static List<WeaponPlatform.Entry> aged(List<WeaponPlatform.Entry> list,Set<String> catalogued,String kind,RecipeManager recipes){
        Map<Integer,Double> place=kind.equals("gun")?knownMedians(list.stream().filter(e->catalogued.contains(e.recipeId().toString())).toList(),recipes):Map.of();
        var out=new ArrayList<WeaponPlatform.Entry>(list.size());
        for(var e:list){
            var g=e.gate();int age=g.age();
            if(kind.equals("gun")&&!catalogued.contains(e.recipeId().toString()))age=autoAge(power(gunId(e)),place);
            Integer set=override(g.id(),e.recipeId().toString(),kind.equals("gun")?gunId(e):null);if(set!=null)age=set;
            out.add(age==g.age()?e:new WeaponPlatform.Entry(e.recipeId(),e.recipe(),new WeaponPlatform.Gate(age,g.kind(),g.id(),g.name()),e.output()));
        }
        return out;
    }
}
