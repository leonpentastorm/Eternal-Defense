package dev.createarsenal.beacon;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.*;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Factory gear recipes stay discoverable after removing their bypass crafting recipes. */
final class ArmorJeiCategory implements IRecipeCategory<WeaponPlatform.Entry> {
    static final RecipeType<WeaponPlatform.Entry> TYPE=new RecipeType<>(new ResourceLocation(ArsenalBeacon.ID,"armor_fabrication"),WeaponPlatform.Entry.class);
    static final RecipeType<WeaponPlatform.Entry> SUPPLIES=new RecipeType<>(new ResourceLocation(ArsenalBeacon.ID,"supplies_fabrication"),WeaponPlatform.Entry.class);
    private final boolean supplies;
    private final IDrawable background,icon;
    ArmorJeiCategory(IGuiHelper h){this(h,false);}
    ArmorJeiCategory(IGuiHelper h,boolean supplies){this.supplies=supplies;background=h.createBlankDrawable(176,118);icon=h.createDrawableIngredient(VanillaTypes.ITEM_STACK,new ItemStack(supplies?ArsenalBeacon.GUN_PLATFORM.get():ArsenalBeacon.ARMOR_PLATFORM.get()));}
    @Override public RecipeType<WeaponPlatform.Entry> getRecipeType(){return supplies?SUPPLIES:TYPE;}
    @Override public Component getTitle(){return Component.literal(supplies?"Weapon Platform / Supplies":"Universal Armor Platform");}
    @Override public IDrawable getBackground(){return background;}
    @Override public IDrawable getIcon(){return icon;}
    @Override public void setRecipe(IRecipeLayoutBuilder b,WeaponPlatform.Entry recipe,IFocusGroup focuses){
        try{int i=0;for(var cost:WeaponPlatform.costs(recipe)){
            var choices=Arrays.stream(cost.ingredient().getItems()).map(s->s.copyWithCount(cost.count())).toList();
            b.addSlot(RecipeIngredientRole.INPUT,8+(i%3)*21,30+(i/3)*21).addItemStacks(choices);i++;
        }}catch(ReflectiveOperationException ex){throw new IllegalStateException(ex);}
        b.addSlot(RecipeIngredientRole.OUTPUT,140,48).addItemStack(recipe.output());
    }
    @Override public void draw(WeaponPlatform.Entry recipe,IRecipeSlotsView slots,GuiGraphics g,double mx,double my){
        var font=Minecraft.getInstance().font;g.drawString(font,"Age "+recipe.gate().age()+": "+WeaponPlatform.AGES[recipe.gate().age()],6,5,0x459899,false);
        g.drawString(font,supplies?"Weapon Platform > Supplies":"Craft at the Armor Platform",6,18,0x555555,false);g.drawString(font,"->",105,53,0x555555,false);
        int y=94;for(var line:font.split(Component.literal(supplies?CreateUnlocks.requirement(CreateUnlocks.required(recipe)):recipe.gate().age()>=3?"Modern+: Nether visit + table upgrades":"Upgrade its Age at the Weapon Platform"),164)){g.drawString(font,line,6,y,0x555555,false);y+=10;}
    }
}
