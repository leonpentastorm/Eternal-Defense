package dev.eternaldefense.beacon;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** 1.1.12: armor prices follow protection against the Age's median, Frontier dearer, Advanced and Exotic cheaper. */
final class Release1112Test {
    static final double HIT=10;
    /** Rough worth of each material in iron ingots, to compare set prices */
    static final Map<String,Double> WORTH=Map.of("create:iron_sheet",1.0,"minecraft:leather",.3,"#minecraft:wool",.1,"create:andesite_alloy",.3,
        "eternal_defense:reinforced_plating",6.0,"create:precision_mechanism",4.0,"minecraft:netherite_scrap",12.0,"eternal_defense:resonance_coil",2.5);
    static double worth(List<Economy.SetPart> set){return set.stream().mapToDouble(p->WORTH.get(p.id())*p.amount()).sum();}
    static List<Economy.SetPart> set(Object... idAndAmount){var out=new ArrayList<Economy.SetPart>();for(int i=0;i<idAndAmount.length;i+=2)out.add(new Economy.SetPart((String)idAndAmount[i],(Integer)idAndAmount[i+1]));return out;}

    @Test void theScoreIsTheArmorAFullSetKeepsAsTheGameCountsIt(){
        double leather=ArmorBalance.score(1,0,0,"head",HIT),iron=ArmorBalance.score(6,0,0,"chest",HIT),diamond=ArmorBalance.score(8,2,0,"chest",HIT),netherite=ArmorBalance.score(8,3,.1,"chest",HIT);
        assertEquals(20/3.0-5,leather,1e-9,"a leather cap: 6.7 armor as a set, 5 of it lost to a 10-damage hit");
        assertEquals(10,iron,1e-9);assertEquals(17.5,diamond,1e-9);assertEquals(18*1.04,netherite,1e-9,"knockback resistance adds up to a tenth");
        assertTrue(leather<iron&&iron<diamond&&diamond<netherite);
        assertEquals(ArmorBalance.score(3,2,0,"head",HIT),ArmorBalance.score(8,2,0,"chest",HIT),1e-9,"a piece is scaled to a full set: a diamond helmet equals a diamond chestplate");
        assertEquals(ArmorBalance.score(6,2,0,"legs",HIT),ArmorBalance.score(3,2,0,"feet",HIT),1e-9);
        assertEquals(20,ArmorBalance.score(14,2,0,"chest",HIT),1e-9,"the game keeps at most 20 armor");
        assertEquals(20*1.1,ArmorBalance.score(10,2,2,"chest",HIT),1e-9,"knockback counts once full");
        assertEquals(0,ArmorBalance.score(0,3,.1,"chest",HIT),"toughness without armor keeps nothing");
        assertEquals(0,ArmorBalance.score(5,0,0,null,HIT),"gear that is not worn as armor has no score");
        assertTrue(ArmorBalance.score(6,0,0,"chest",20)<iron,"a heavier reference hit strips more armor");
    }
    @Test void aPieceIsComparedWithItsAgesMedianLikeAGun(){
        assertEquals((8.3+5)/(1.7+5),ArmorBalance.ratio(8.3,1.7,5),1e-9,"softened: an Age of near-bare gear does not make every real piece an outlier");
        assertEquals(1,ArmorBalance.ratio(20,null,5),"no median: priced as a typical piece");
        assertEquals(1,ArmorBalance.ratio(7,7.0,5),1e-9);
        assertEquals(.25,ArmorBalance.factor(.1,.25,1.5));assertEquals(1.5,ArmorBalance.factor(3,.25,1.5));assertEquals(.8,ArmorBalance.factor(.8,.25,1.5));
        var m=ArmorBalance.medians(List.of(new double[]{1,1},new double[]{1,2},new double[]{1,9},new double[]{2,5},new double[]{2,6},new double[]{3,0},new double[]{3,0},new double[]{3,0}),3);
        assertEquals(2,m.get(1));assertNull(m.get(2),"too few pieces to judge");assertNull(m.get(3),"bare gear is not counted");
        // The rare part comes at the gun's threshold, one more for each step
        var b=JsonParser.parseString(read("src/main/resources/data/eternal_defense/balance.json")).getAsJsonObject();
        double from=b.get("outlier_from").getAsDouble(),step=b.get("step").getAsDouble();int max=b.get("max_units").getAsInt();
        assertEquals(0,CraftBalance.units(1.39,from,step,max));assertEquals(1,CraftBalance.units(ArmorBalance.ratio(8.3,1.7,5),from,step,max));
    }
    @Test void aPiecePaysItsShareOfTheSet(){
        assertEquals(8,ArmorBalance.amount(24,8/24.0,1,true),"a typical chestplate of Frontier: 8 iron sheets, as a vanilla iron chestplate");
        assertEquals(5,ArmorBalance.amount(24,5/24.0,1,true));assertEquals(7,ArmorBalance.amount(24,7/24.0,1,true));assertEquals(4,ArmorBalance.amount(24,4/24.0,1,true));
        assertEquals(1,ArmorBalance.amount(24,4/24.0,.01,true),"the first material is never left out");
        assertEquals(0,ArmorBalance.amount(4,4/24.0,.25,false),"others may round away on a weak piece");
        assertEquals(24,ArmorBalance.priceShare("head")+ArmorBalance.priceShare("chest")+ArmorBalance.priceShare("legs")+ArmorBalance.priceShare("feet"));
        assertEquals(20,ArmorBalance.protectionShare("head")+ArmorBalance.protectionShare("chest")+ArmorBalance.protectionShare("legs")+ArmorBalance.protectionShare("feet"));
    }
    @Test void frontierCostsMoreAndAdvancedAndExoticLess(){
        // The full sets of 1.1.11 (helmet, chest, legs and boots at 1, 3, 2 and 1 shares)
        var old=List.of(set("#minecraft:wool",14,"minecraft:leather",14,"create:iron_sheet",7),
            set("#minecraft:wool",14,"minecraft:leather",14,"create:iron_sheet",14,"create:andesite_alloy",14),
            set("#minecraft:wool",14,"minecraft:leather",14,"eternal_defense:reinforced_plating",21,"create:andesite_alloy",14,"create:precision_mechanism",7),
            set("#minecraft:wool",14,"minecraft:leather",14,"eternal_defense:reinforced_plating",28,"create:andesite_alloy",14,"create:precision_mechanism",7,"minecraft:netherite_scrap",7),
            set("#minecraft:wool",14,"minecraft:leather",14,"eternal_defense:reinforced_plating",35,"create:andesite_alloy",14,"create:precision_mechanism",7,"minecraft:netherite_scrap",7,"eternal_defense:resonance_coil",14));
        double[] now=new double[5],before=new double[5];for(int a=1;a<=5;a++){now[a-1]=worth(Economy.armorSet(a));before[a-1]=worth(old.get(a-1));}
        assertTrue(now[0]>1.5*before[0],"Frontier: "+now[0]+" against "+before[0]);
        assertTrue(now[1]>now[0]&&now[1]<1.5*before[1],"World Wars rises only to stay above Frontier: "+now[1]+" against "+before[1]);
        assertEquals(before[2],now[2],before[2]*.05,"Modern about the same");
        assertTrue(now[3]<.8*before[3],"Advanced: "+now[3]+" against "+before[3]);
        assertTrue(now[4]<.8*before[4],"Exotic: "+now[4]+" against "+before[4]);
        for(int a=1;a<5;a++)assertTrue(now[a]>now[a-1],"each Age dearer than the last");
        assertEquals(24,Economy.armorSet(1).get(0).amount(),"a Frontier set: 24 iron sheets, as a vanilla iron set");
        for(int a=1;a<=5;a++)assertEquals(a>=3?"eternal_defense:reinforced_plating":"create:iron_sheet",Economy.armorSet(a).get(0).id(),"the main material leads");
        assertEquals(2,Economy.vision(5).get(3).amount(),"Exotic optics take 2 precision mechanisms (4 before)");assertEquals(1,Economy.vision(3).get(3).amount());
    }
    @Test void theTablesUseIt() throws Exception{
        var b=JsonParser.parseString(read("src/main/resources/data/eternal_defense/balance.json")).getAsJsonObject();
        assertEquals(10,b.get("armor_reference_hit").getAsInt());assertEquals(5,b.get("armor_soften").getAsInt());assertEquals(.25,b.get("armor_price_min").getAsDouble());assertEquals(1.5,b.get("armor_price_max").getAsDouble());
        String armor=Release114Test.source("ArmorPlatform.java"),weapons=Release114Test.source("WeaponPlatform.java");
        assertTrue(armor.contains("ArmorBalance.costs(")&&armor.contains("ArmorBalance.medians("),"armor table gear is priced by protection");
        assertTrue(armor.contains("if(!(stack.getItem() instanceof ArmorItem)&&matches(id,\"armorplate\""),"a worn chestplate_ piece is a vest, not a plate");
        assertEquals(1,armor.split("\"steel_plate\"\\)\\)return \"plates\"",-1).length-1,"one plate rule");
        assertTrue(weapons.contains("ArmorPlatform.Fabrication f&&f.ratio()>=CraftBalance.settings().outlierFrom()"),"the page marks a strong piece");
        assertTrue(Release114Test.source("PlatformScreen.java").contains("armor()?\"platform.strong_armor\":\"platform.strong\""));
        assertTrue(Release114Test.text("platform.strong_armor").contains("protection"));
        assertTrue(Release114Test.text("guide.stations.body").contains("The price follows how well a piece protects you."));
        assertTrue(Release114Test.source("CraftBalance.java").contains("ArmorBalance.reload();ArmorPlatform.reset();"),"the reload command reprices armor too");
    }
    static String read(String path){try{return Files.readString(Path.of(path));}catch(Exception e){throw new IllegalStateException(e);}}
}
