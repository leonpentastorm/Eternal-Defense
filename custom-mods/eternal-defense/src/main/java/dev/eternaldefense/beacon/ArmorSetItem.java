package dev.eternaldefense.beacon;

import net.minecraft.ChatFormatting;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import java.util.List;
import java.util.function.Consumer;

/** A piece of an Age armor set: its own worn model, one texture per set, and the set bonus in its tooltip. */
public class ArmorSetItem extends ArmorItem {
    private final ArmorSetMaterials set;
    public ArmorSetItem(ArmorSetMaterials set,ArmorItem.Type type){super(set,type,new Properties());this.set=set;}
    public ArmorSetMaterials set(){return set;}
    @Override public String getArmorTexture(ItemStack stack,Entity entity,EquipmentSlot slot,String type){return EternalDefense.ID+":textures/models/armor/"+set.key()+".png";}
    @Override public void initializeClient(Consumer<IClientItemExtensions> consumer){
        consumer.accept(new IClientItemExtensions(){
            @Override public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity,ItemStack stack,EquipmentSlot slot,HumanoidModel<?> original){return ArmorSetClient.model(set.key(),slot,original);}
        });
    }
    @Override public void appendHoverText(ItemStack stack,Level level,List<Component> lines,TooltipFlag flag){
        int worn=level!=null&&level.isClientSide()?ArmorSetBonus.worn(ArmorSetClient.player(),set):0;
        lines.add(Component.translatable("tooltip.eternal_defense.armor_set.age",Component.translatable("gui.eternal_defense.age."+set.age())).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.eternal_defense.armor_set.bonus",worn,Component.translatable("tooltip.eternal_defense.armor_set."+set.key()+".name")).withStyle(worn>=4?ChatFormatting.GOLD:ChatFormatting.DARK_GRAY));
        lines.add(Component.translatable("tooltip.eternal_defense.armor_set."+set.key()+".desc").withStyle(worn>=4?ChatFormatting.YELLOW:ChatFormatting.DARK_GRAY));
    }
}
