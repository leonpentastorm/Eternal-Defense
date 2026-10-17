package dev.eternaldefense.beacon;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.function.Supplier;

/** The five Age armor sets (numbers in ArmorSetRules) */
public enum ArmorSetMaterials implements ArmorMaterial {
    FRONTIER("frontier",SoundEvents.ARMOR_EQUIP_LEATHER,"create:iron_sheet"),
    WORLDWARS("worldwars",SoundEvents.ARMOR_EQUIP_CHAIN,"create:iron_sheet"),
    MODERN("modern",SoundEvents.ARMOR_EQUIP_IRON,"eternal_defense:reinforced_plating"),
    ADVANCED("advanced",SoundEvents.ARMOR_EQUIP_NETHERITE,"eternal_defense:reinforced_plating"),
    EXOTIC("exotic",SoundEvents.ARMOR_EQUIP_DIAMOND,"eternal_defense:resonance_coil");

    /** Vanilla's durability per slot: helmet, chestplate, leggings, boots */
    private static final int[] BASE_DURABILITY={11,16,15,13};
    private final String key;private final int age,durability,enchantability;private final int[] defense;private final float toughness;
    private final SoundEvent sound;private final Supplier<Ingredient> repair;
    ArmorSetMaterials(String key,SoundEvent sound,String repair){
        this.key=key;int i=ArmorSetRules.SETS.indexOf(key);age=i+1;durability=ArmorSetRules.DURABILITY[i];defense=ArmorSetRules.DEFENSE[i];toughness=ArmorSetRules.TOUGHNESS[i];enchantability=ArmorSetRules.ENCHANTABILITY[i];this.sound=sound;
        this.repair=com.google.common.base.Suppliers.memoize(()->Ingredient.of(ForgeRegistries.ITEMS.getValue(new ResourceLocation(repair))));
    }
    public String key(){return key;}
    /** The Age the armor table sells this set at (1 Frontier to 5 Exotic) */
    public int age(){return age;}
    static ArmorSetMaterials of(String key){for(var m:values())if(m.key.equals(key))return m;return null;}
    private static int index(ArmorItem.Type type){return switch(type){case HELMET->0;case CHESTPLATE->1;case LEGGINGS->2;case BOOTS->3;};}
    @Override public int getDurabilityForType(ArmorItem.Type type){return BASE_DURABILITY[index(type)]*durability;}
    @Override public int getDefenseForType(ArmorItem.Type type){return defense[index(type)];}
    @Override public int getEnchantmentValue(){return enchantability;}
    @Override public SoundEvent getEquipSound(){return sound;}
    @Override public Ingredient getRepairIngredient(){return repair.get();}
    @Override public String getName(){return EternalDefense.ID+":"+key;}
    @Override public float getToughness(){return toughness;}
    @Override public float getKnockbackResistance(){return 0;}
}
