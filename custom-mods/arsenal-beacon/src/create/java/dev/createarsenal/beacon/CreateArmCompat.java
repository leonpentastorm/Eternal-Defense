package dev.createarsenal.beacon;

import com.simibubi.create.api.registry.CreateRegistries;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPointType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;

/**
 * 0.0.40, pack edition only (this source set is compiled against Create and never goes into the standalone jar): Create's mechanical arm
 * only works with blocks that have a registered interaction point type, and has none for plain inventories. This one lets an arm put items
 * into, and take them out of, any of the Logistics Center Hub's 27 blocks, through the same item handler hoppers and funnels use. It also
 * swaps in the Hub part Create's belts can deliver to ({@link CreateHubPart}). Registered from {@link ArsenalBeacon} by name, only when
 * Create is loaded.
 */
public final class CreateArmCompat {
    private CreateArmCompat(){}
    public static void register(IEventBus modBus){
        LogisticsHub.partFactory=CreateHubPart::new;   // belts deliver to the Hub's parts directly
        var types=DeferredRegister.create(CreateRegistries.ARM_INTERACTION_POINT_TYPE,ArsenalBeacon.ID);
        types.register("logistics_hub",HubType::new);
        types.register(modBus);
    }
    static final class HubType extends ArmInteractionPointType {
        @Override public boolean canCreatePoint(Level level,BlockPos pos,BlockState state){return LogisticsHub.isHub(state);}
        @Override public ArmInteractionPoint createPoint(Level level,BlockPos pos,BlockState state){return new ArmInteractionPoint(this,level,pos,state);}
    }
}
