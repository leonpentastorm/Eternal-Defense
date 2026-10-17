package dev.eternaldefense.beacon;

import com.simibubi.create.api.registry.CreateRegistries;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPointType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;

/** Create's mechanical arm only works with blocks that have a registered interaction point type */
public final class CreateArmCompat {
    private CreateArmCompat(){}
    public static void register(IEventBus modBus){
        LogisticsHub.partFactory=CreateHubPart::new;   // Belts deliver to the Hub's parts directly
        var types=DeferredRegister.create(CreateRegistries.ARM_INTERACTION_POINT_TYPE,EternalDefense.ID);
        types.register("logistics_hub",HubType::new);
        types.register(modBus);
        CreatePower.register(modBus);   // The dynamos turn on Create's networks
    }
    static final class HubType extends ArmInteractionPointType {
        @Override public boolean canCreatePoint(Level level,BlockPos pos,BlockState state){return LogisticsHub.isHub(state);}
        @Override public ArmInteractionPoint createPoint(Level level,BlockPos pos,BlockState state){return new ArmInteractionPoint(this,level,pos,state);}
    }
}
