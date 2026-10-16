package dev.eternaldefense.beacon;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import java.util.*;

/** Fair armor prices: each piece's protection against the median of its Age, as the guns' damage output is. */
final class ArmorBalance {
    private ArmorBalance(){}

    /** The rules (the armor lines of data/eternal_defense/balance.json) */
    record Settings(double hit,double soften,double min,double max){}
    private static Settings settings;
    static Settings settings(){
        if(settings!=null)return settings;
        var root=CraftBalance.json("/data/eternal_defense/balance.json");
        return settings=new Settings(root.get("armor_reference_hit").getAsDouble(),root.get("armor_soften").getAsDouble(),root.get("armor_price_min").getAsDouble(),root.get("armor_price_max").getAsDouble());
    }
    static void reload(){settings=null;}

    // Pure rules
    /** Pure: armor points of a vanilla diamond piece in each slot (of 20): how a piece is scaled to a full set */
    static int protectionShare(String slot){return slot==null?0:switch(slot){case "head","feet"->3;case "chest"->8;case "legs"->6;default->0;};}
    /** Pure: crafting units of a vanilla piece in each slot (of 24): its share of a set's price */
    static int priceShare(String slot){return slot==null?0:switch(slot){case "head"->5;case "chest"->8;case "legs"->7;case "feet"->4;default->0;};}
    static final int SET_UNITS=24;
    /** Gear that gives no armor (pouches, plates, optics) pays a seventh of a set */
    static final double GEAR_SHARE=1/7.0;
    /** Pure: armor a full set of this piece keeps against one hit of the given damage, as the game counts it (0 to 20), up to a tenth more for knockback resistance */
    static double score(double armor,double toughness,double knockback,String slot,double hit){
        int share=protectionShare(slot);if(share<=0||armor<=0)return 0;
        double a=Math.min(30,armor*20/share),t=Math.min(20,4*Math.max(0,toughness));
        double kept=Math.max(a/5,Math.min(20,a-hit/(2+t/4)));
        return kept*(1+.1*Math.min(1,4*Math.max(0,knockback)));
    }
    /** Pure: a piece against its Age's median, softened so an Age of near-bare gear does not make every real piece an outlier (1 with no median) */
    static double ratio(double score,Double median,double soften){return median==null||median<=0?1:(score+soften)/(median+soften);}
    /** Pure: the share of its set's price a piece pays */
    static double factor(double ratio,double min,double max){return Math.max(min,Math.min(max,ratio));}
    /** Pure: one material of a piece: the set's amount times the piece's share and factor, rounded; the first material is never left out */
    static int amount(int setAmount,double share,double factor,boolean first){int n=(int)Math.floor(setAmount*share*factor+.5);return first?Math.max(1,n):n;}
    /** Pure: the median score of each Age's armor pieces (Ages with fewer than minPeers rated pieces have none) */
    static Map<Integer,Double> medians(List<double[]> ageAndScore,int minPeers){
        var by=new HashMap<Integer,List<Double>>();for(var p:ageAndScore)if(p[1]>0)by.computeIfAbsent((int)p[0],k->new ArrayList<>()).add(p[1]);
        var out=new HashMap<Integer,Double>();by.forEach((age,list)->{if(list.size()>=minPeers)out.put(age,CraftBalance.median(list));});
        return Map.copyOf(out);
    }

    // A piece of armor
    /** Its slot: head, chest, legs or feet (null for gear that is not worn as armor) */
    static String slot(ItemStack stack){
        if(!(stack.getItem() instanceof ArmorItem a))return null;
        return switch(a.getEquipmentSlot()){case HEAD->"head";case CHEST->"chest";case LEGS->"legs";case FEET->"feet";default->null;};
    }
    /** Armor, toughness and knockback resistance it gives in its slot: its attribute modifiers, else what the item says */
    static double[] stats(ItemStack stack){
        if(!(stack.getItem() instanceof ArmorItem a))return new double[3];
        double armor=0,tough=0,kb=0;boolean found=false;
        try{for(var e:stack.getAttributeModifiers(a.getEquipmentSlot()).entries()){var m=e.getValue();if(m.getOperation()!=AttributeModifier.Operation.ADDITION)continue;
            if(e.getKey()==Attributes.ARMOR){armor+=m.getAmount();found=true;}else if(e.getKey()==Attributes.ARMOR_TOUGHNESS)tough+=m.getAmount();else if(e.getKey()==Attributes.KNOCKBACK_RESISTANCE)kb+=m.getAmount();}}
        catch(RuntimeException|LinkageError ex){found=false;}
        return found?new double[]{armor,tough,kb}:new double[]{a.getDefense(),a.getToughness(),a.getMaterial().getKnockbackResistance()};
    }
    static double score(ItemStack stack){var s=stats(stack);return score(s[0],s[1],s[2],slot(stack),settings().hit());}

    /** The table's price for one piece: its share of its Age's set, by its protection against the Age's median, and a rare part when it is far above it */
    static List<WeaponPlatform.Cost> costs(String type,String slot,int age,double score,Double median){
        if(type.equals("vision")&&score<=0)return parts(Economy.vision(age),1,1);
        var s=settings();var balance=CraftBalance.settings();
        double share=slot==null?GEAR_SHARE:priceShare(slot)/(double)SET_UNITS,r=slot==null?1:ratio(score,median,s.soften());
        var out=new ArrayList<WeaponPlatform.Cost>();
        int units=slot==null||median==null?0:CraftBalance.units(r,balance.outlierFrom(),balance.step(),balance.maxUnits());
        int a=Math.max(1,Math.min(5,age));
        if(units>0)out.add(WeaponPlatform.cost(balance.material().getOrDefault(a,"minecraft:diamond"),units*balance.perUnit().getOrDefault(a,1)));   // First, so the reason for the price is seen
        out.addAll(parts(Economy.armorSet(a),share,factor(r,s.min(),s.max())));
        return List.copyOf(out);
    }
    /** How far above its Age's median a piece is (1 when its Age has no median) */
    static double ratio(String slot,double score,Double median){return slot==null?1:ratio(score,median,settings().soften());}
    private static List<WeaponPlatform.Cost> parts(List<Economy.SetPart> set,double share,double factor){
        var out=new ArrayList<WeaponPlatform.Cost>();boolean first=true;
        for(var p:set){int n=amount(p.amount(),share,factor,first);first=false;if(n>0)out.add(cost(p.id(),n));}
        return out;
    }
    static WeaponPlatform.Cost cost(String id,int amount){
        if(id.startsWith("#"))return new WeaponPlatform.Cost(Ingredient.of(TagKey.create(net.minecraft.core.registries.Registries.ITEM,new ResourceLocation(id.substring(1)))),amount);
        return WeaponPlatform.cost(id,amount);
    }
}
