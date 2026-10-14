package dev.eternaldefense.beacon;

import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.Nullable;
import java.util.List;
import java.util.UUID;

/** Pack edition with Create loaded: a Logistics Center Hub part Create's belts */
final class CreateHubPart extends SmartBlockEntity implements LogisticsHub.Cell {
    private UUID hubId;
    CreateHubPart(BlockPos pos,BlockState state){super(EternalDefense.LOGISTICS_HUB_PART_ENTITY.get(),pos,state);}

    /** Belt items go straight into the Hub's storage (onto the same kind first, then empty slots), whole or in part */
    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours){
        behaviours.add(new DirectBeltInputBehaviour(this).setInsertionHandler((item,side,simulate)->{
            if(!(level instanceof ServerLevel server)||hubId==null)return item.stack;
            var hub=HubData.get(server).hub(hubId);
            if(hub==null||!hub.working(server))return item.stack;
            return hub.storage.insert(item.stack.copy(),simulate);
        }));
    }
    @Override public <T> LazyOptional<T> getCapability(Capability<T> cap,@Nullable Direction side){
        return cap==ForgeCapabilities.ITEM_HANDLER?LogisticsHub.itemHandler(level,hubId):super.getCapability(cap,side);
    }
    @Override public UUID hubId(){return hubId;}
    @Override public void link(UUID id){hubId=id;setChanged();}
    @Override protected void write(CompoundTag tag,boolean clientPacket){super.write(tag,clientPacket);if(hubId!=null&&!clientPacket)tag.putUUID("Hub",hubId);}
    @Override protected void read(CompoundTag tag,boolean clientPacket){super.read(tag,clientPacket);if(!clientPacket)hubId=tag.hasUUID("Hub")?tag.getUUID("Hub"):null;}
}
