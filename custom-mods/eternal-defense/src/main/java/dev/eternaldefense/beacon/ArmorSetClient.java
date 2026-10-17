package dev.eternaldefense.beacon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.HashMap;
import java.util.Map;

/** Registers the Age armor layers and keeps one baked model per set and slot. */
@Mod.EventBusSubscriber(modid=EternalDefense.ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class ArmorSetClient {
    private ArmorSetClient(){}
    private static final EquipmentSlot[] SLOTS={EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET};
    private static final Map<String,HumanoidModel<?>> MODELS=new HashMap<>();
    @SubscribeEvent public static void layers(EntityRenderersEvent.RegisterLayerDefinitions e){ArmorSetLayers.register(e);}
    @SubscribeEvent public static void bake(EntityRenderersEvent.AddLayers e){
        MODELS.clear();
        for(String set:ArmorSetLayers.SETS)for(var slot:SLOTS)MODELS.put(set+"/"+slot.getName(),new ArmorSetModel(e.getEntityModels().bakeLayer(ArmorSetLayers.layer(set,slot))));
    }
    /** The worn model of a set and slot, or the game's own until the layers are baked */
    static HumanoidModel<?> model(String set,EquipmentSlot slot,HumanoidModel<?> fallback){var m=MODELS.get(set+"/"+slot.getName());return m!=null?m:fallback;}
    static Player player(){return Minecraft.getInstance().player;}
}
