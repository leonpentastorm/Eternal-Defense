package dev.eternaldefense.beacon;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static dev.eternaldefense.beacon.EnchantRules.*;

/** The Tactical Enchanting Table without a world: the level gate, the prices, the discount, the seals, the model. */
final class EnchantTest {
    static final String SRC="src/main/java/dev/eternaldefense/beacon/",ASSETS="src/main/resources/assets/eternal_defense/";

    @Test void theLevelGateForEveryMaxLevel(){
        for(int max=1;max<=7;max++){
            assertEquals(0,fobsFor(1,max),"level I needs no FOB (max "+max+")");
            if(max>1)assertEquals(5,fobsFor(max,max),"the top level needs all five (max "+max+")");
            for(int level=2;level<=max;level++){
                assertEquals((int)Math.ceil(5.0*(level-1)/(max-1)),fobsFor(level,max),"ceil(5 (L-1) / (M-1)), L "+level+" M "+max);
                assertTrue(fobsFor(level,max)>=fobsFor(level-1,max),"never fewer FOBs for a higher level");
                assertTrue(fobsFor(level,max)<=FobRules.MAX_FOBS,"never more than can be placed");
            }
        }
        assertEquals(0,fobsFor(1,1),"one-level enchants (Mending, Silk Touch) are always available");
        // The brief's table for the common maxima
        assertArrayEquals(new int[]{0,5},new int[]{fobsFor(1,2),fobsFor(2,2)});
        assertArrayEquals(new int[]{0,3,5},new int[]{fobsFor(1,3),fobsFor(2,3),fobsFor(3,3)});
        assertArrayEquals(new int[]{0,2,4,5},new int[]{fobsFor(1,4),fobsFor(2,4),fobsFor(3,4),fobsFor(4,4)});
        assertArrayEquals(new int[]{0,2,3,4,5},new int[]{fobsFor(1,5),fobsFor(2,5),fobsFor(3,5),fobsFor(4,5),fobsFor(5,5)});
        for(int fobs=0;fobs<=5;fobs++)for(int max=1;max<=5;max++){int top=maxLevel(max,fobs);assertTrue(fobsFor(top,max)<=fobs&&(top==max||fobsFor(top+1,max)>fobs),"the highest level allowed, "+fobs+" FOBs, max "+max);}
    }
    @Test void pricesFollowRarityAndLevel(){
        // The base doubled; you pay twice the base (Ardent Energy pays for little else)
        assertEquals(8,price(Rarity.COMMON,1,false));assertEquals(40,price(Rarity.COMMON,5,false),"Sharpness V");
        assertEquals(36,price(Rarity.UNCOMMON,3,false),"Unbreaking III");
        assertEquals(60,price(Rarity.RARE,3,false),"Fortune III");
        assertEquals(32,price(Rarity.VERY_RARE,1,false),"Silk Touch");
        assertEquals(40,price(Rarity.RARE,1,true),"Mending (treasure: twice)");
        for(var r:Rarity.values())assertEquals(new int[]{4,6,10,16}[r.ordinal()],discounted(price(r,1,false),MAX_MK),"a table at Mk III charges the 0.0.27 base price");
        for(var r:Rarity.values())for(int level=1;level<=5;level++)for(boolean t:new boolean[]{false,true}){
            int p=price(r,level,t);
            assertEquals(PACK_FACTOR*PER_LEVEL_BASE[r.ordinal()]*level*(t?TREASURE_FACTOR:1),p);
            assertEquals(p/2,discounted(p,MAX_MK),"a table at Mk III halves it");
            assertTrue(level==1||price(r,level,t)>price(r,level-1,t),"a higher level costs more");
        }
    }
    @Test void theTableMkIsAPermanentDiscount(){
        assertArrayEquals(new int[]{0,17,33,50},DISCOUNT);assertEquals(3,MAX_MK);
        assertEquals(10,discounted(10,0));assertEquals(8,discounted(10,1));assertEquals(6,discounted(10,2));assertEquals(5,discounted(10,3));
        assertEquals(1,discounted(1,3),"never below 1");
        for(int p=1;p<=80;p++)for(int mk=1;mk<=3;mk++){
            assertTrue(discounted(p,mk)<=discounted(p,mk-1),"each Mk is at least as cheap");
            if(p>=2)assertTrue(discounted(p,mk)<p,"every Mk takes something off a price of 2 or more ("+p+" at Mk "+mk+")");
        }
        assertArrayEquals(new int[]{40,33,26,20},new int[]{discounted(40,0),discounted(40,1),discounted(40,2),discounted(40,3)},"the pack's Sharpness V by Mk");
        assertEquals(40,price(Rarity.COMMON,5,false),"Sharpness V");
        String economy=source("Economy.java");
        assertTrue(economy.contains("ENCHANT_UPGRADE_COILS={8,16,32};"),"Mk I to III: resonance coils");
    }
    @Test void warlordSealsUnlockTreasure(){
        assertEquals(List.of("minecraft:mending","minecraft:frost_walker","minecraft:soul_speed","minecraft:swift_sneak"),TREASURE,"never a curse");
        assertEquals(0,unlockedLevel("minecraft:mending",0,1));assertEquals(1,unlockedLevel("minecraft:mending",1,1));
        assertEquals(2,unlockedLevel("minecraft:frost_walker",1,2),"one seal opens Frost Walker I and II");
        assertEquals(1,unlockedLevel("minecraft:soul_speed",1,3));assertEquals(2,unlockedLevel("minecraft:soul_speed",2,3));assertEquals(3,unlockedLevel("minecraft:soul_speed",3,3),"Soul Speed II and III one more seal each");
        assertEquals(3,unlockedLevel("minecraft:swift_sneak",9,3),"never past the top");
        assertEquals(1,sealsForNext("minecraft:swift_sneak",2,3));assertEquals(0,sealsForNext("minecraft:swift_sneak",3,3));assertEquals(0,sealsForNext("minecraft:mending",1,1));
        assertTrue(source("Hunts.java").contains("if(m.offer.kind==TacticalRules.Kind.WARLORD)stacks.add(new ItemStack(EternalDefense.WARLORD_SEAL.get()));"),"a cleared Warlord queues a seal with its pay");
    }
    @Test void whyALevelIsLocked(){
        assertNull(problem(true,true,false,5,3,5,0,3));
        assertEquals("applies",problem(false,true,false,5,1,5,0,5));
        assertEquals("conflict",problem(true,false,false,5,1,5,0,5));
        assertEquals("seal",problem(true,true,true,0,1,1,0,5),"a locked treasure enchant");
        assertEquals("seal_level",problem(true,true,true,1,2,3,0,5),"its next level");
        assertEquals("max",problem(true,true,false,5,6,5,0,5));
        assertEquals("have",problem(true,true,false,5,3,5,3,5),"already on it");
        assertEquals("fobs",problem(true,true,false,5,4,5,0,3),"needs 4 FOBs");
        assertNull(problem(true,true,false,5,4,5,2,4),"an upgrade from II to IV with 4 FOBs");
    }
    @Test void theOwnersModelIsUnchangedAndTwoByTwoByOne() throws Exception{
        var art=Path.of("../../docs/art/Eternal-Defense-FOB-Mixer-Enchanting");
        assertEquals(sha(art.resolve("minecraft-models/block/enchanting_platform.json")),sha(Path.of(ASSETS+"models/block/enchanting_platform.json")),"model unchanged");
        assertEquals(sha(art.resolve("textures/block/enchanting_platform.png")),sha(Path.of(ASSETS+"textures/block/enchanting_platform.png")),"texture unchanged");
        double[] min={99,99,99},max={-99,-99,-99};
        for(var e:JsonParser.parseString(Files.readString(Path.of(ASSETS+"models/block/enchanting_platform.json"))).getAsJsonObject().getAsJsonArray("elements")){
            var o=e.getAsJsonObject();for(var key:List.of("from","to")){var a=o.getAsJsonArray(key);for(int i=0;i<3;i++){min[i]=Math.min(min[i],a.get(i).getAsDouble());max[i]=Math.max(max[i],a.get(i).getAsDouble());}}
        }
        assertEquals(0,min[0]);assertEquals(32,max[0],"2 wide (to the east of the anchor when facing north: clockwise)");
        assertEquals(0,min[1]);assertTrue(max[1]>16&&max[1]<=32,"2 tall");
        assertEquals(0,min[2]);assertEquals(16,max[2],"1 deep");
        String state=Files.readString(Path.of(ASSETS+"blockstates/tactical_enchanting_table.json"));
        assertTrue(state.contains("eternal_defense:block/enchanting_platform"),"the block uses the owner's model");
        assertTrue(Files.exists(Path.of(ASSETS+"textures/item/warlord_seal.png")),"the seal has its icon");
        String structures=source("DefenseStructures.java");
        assertTrue(structures.contains("static boolean enchanting(BlockState state)"),"the table is a structure: anchor and parts");
    }
    @Test void thePricesAndTheGuideAgree() throws Exception{
        String lang=Files.readString(Path.of(ASSETS+"lang/en_us.json")),economy=source("Economy.java");
        assertTrue(economy.contains("ENCHANT_IRON=12,ENCHANT_OBSIDIAN=4,ENCHANT_BOOKS=3,ENCHANT_COILS=2;"));
        assertTrue(lang.contains("12 iron, 4 obsidian, 3 books and 2 resonance coils. Mk I, II and III: 8, 16 and 32 resonance coils."),"pack price in the guide");
        assertTrue(lang.contains("8 for common enchants, 12 for uncommon, 20 for rare and 32 for very rare")&&Arrays.equals(PER_LEVEL_BASE,new int[]{4,6,10,16})&&PACK_FACTOR==2,"per level by rarity");
        assertTrue(lang.contains("Sharpness II needs 2, III needs 3, IV needs 4 and V needs 5")&&fobsFor(2,5)==2&&fobsFor(3,5)==3&&fobsFor(4,5)==4&&fobsFor(5,5)==5,"the level gate's example");
        assertTrue(lang.contains("17, 33 and 50 percent off"),"the discounts");
    }
    private static String sha(Path p) throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));}
    static String source(String name){try{return Files.readString(Path.of(SRC+name));}catch(Exception e){return "";}}
}
