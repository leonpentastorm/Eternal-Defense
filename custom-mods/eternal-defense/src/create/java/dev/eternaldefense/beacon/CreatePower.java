package dev.eternaldefense.beacon;

import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.gearbox.GearboxBlock;
import com.simibubi.create.content.kinetics.gearbox.GearboxBlockEntity;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import java.util.List;

/** The two dynamos: Create kinetic blocks that put a power network's stress on the Create network turning them */
public final class CreatePower {
    private CreatePower(){}
    static RegistryObject<Block> SHAFT_DYNAMO,COG_DYNAMO;
    static RegistryObject<BlockEntityType<ShaftEntity>> SHAFT_ENTITY;
    static RegistryObject<BlockEntityType<CogEntity>> COG_ENTITY;

    public static void register(IEventBus bus){
        var blocks=DeferredRegister.create(ForgeRegistries.BLOCKS,EternalDefense.ID);
        var items=DeferredRegister.create(ForgeRegistries.ITEMS,EternalDefense.ID);
        var entities=DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES,EternalDefense.ID);
        SHAFT_DYNAMO=blocks.register("shaft_dynamo",ShaftDynamo::new);
        COG_DYNAMO=blocks.register("cog_dynamo",CogDynamo::new);
        items.register("shaft_dynamo",()->new PowerKit.KitItem(SHAFT_DYNAMO.get(),"shaft_dynamo"));
        items.register("cog_dynamo",()->new PowerKit.KitItem(COG_DYNAMO.get(),"cog_dynamo"));
        SHAFT_ENTITY=entities.register("shaft_dynamo",()->BlockEntityType.Builder.of((pos,state)->new ShaftEntity(SHAFT_ENTITY.get(),pos,state),SHAFT_DYNAMO.get()).build(null));
        COG_ENTITY=entities.register("cog_dynamo",()->BlockEntityType.Builder.of((pos,state)->new CogEntity(COG_ENTITY.get(),pos,state),COG_DYNAMO.get()).build(null));
        blocks.register(bus);items.register(bus);entities.register(bus);
        if(net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient())CreatePowerClient.register(bus);
    }
    static Block.Properties props(){return Block.Properties.of().strength(3,6).sound(SoundType.METAL).noOcclusion();}
    /** The stress a dynamo carries: the share of its power network, per RPM */
    static float stress(float demand){return Math.max(0,demand);}

    /** Shafts on its four sides, like a gearbox standing on its axis; a pole socket on top */
    static final class ShaftDynamo extends GearboxBlock {
        ShaftDynamo(){super(props());}
        @Override public BlockState getStateForPlacement(BlockPlaceContext c){return defaultBlockState().setValue(AXIS,Direction.Axis.Y);}
        @Override public BlockState getRotatedBlockState(BlockState state,Direction face){return state;}
        @Override public List<ItemStack> getDrops(BlockState state,LootParams.Builder builder){return List.of(new ItemStack(this));}
        @Override public ItemStack getCloneItemStack(BlockState s,net.minecraft.world.phys.HitResult t,net.minecraft.world.level.BlockGetter l,BlockPos p,net.minecraft.world.entity.player.Player who){return new ItemStack(this);}
        @Override public Class<GearboxBlockEntity> getBlockEntityClass(){return GearboxBlockEntity.class;}
        @Override public BlockEntityType<? extends GearboxBlockEntity> getBlockEntityType(){return SHAFT_ENTITY.get();}
    }
    static final class ShaftEntity extends GearboxBlockEntity implements PowerKit.Dynamo {
        float demand;
        ShaftEntity(BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state);}
        @Override public float calculateStressApplied(){lastStressApplied=stress(demand);return lastStressApplied;}
        @Override public float rpm(){return Math.abs(getTheoreticalSpeed());}
        @Override public boolean overstressed(){return isOverStressed();}
        @Override public void demand(float perRpm){if(Math.abs(perRpm-demand)<1e-4f)return;demand=perRpm;setChanged();if(hasNetwork()&&level!=null&&!level.isClientSide)getOrCreateNetwork().updateStressFor(this,calculateStressApplied());}
        @Override protected void write(CompoundTag tag,boolean client){super.write(tag,client);tag.putFloat("Demand",demand);}
        @Override protected void read(CompoundTag tag,boolean client){super.read(tag,client);demand=tag.getFloat("Demand");}
    }

    /** A small cogwheel band like the mechanical arm's: it meshes with Create cogwheels beside it */
    static final class CogDynamo extends KineticBlock implements IBE<CogEntity>,ICogWheel {
        CogDynamo(){super(props());}
        @Override public Direction.Axis getRotationAxis(BlockState state){return Direction.Axis.Y;}
        @Override public boolean hasShaftTowards(LevelReader world,BlockPos pos,BlockState state,Direction face){return false;}
        @Override public List<ItemStack> getDrops(BlockState state,LootParams.Builder builder){return List.of(new ItemStack(this));}
        @Override public Class<CogEntity> getBlockEntityClass(){return CogEntity.class;}
        @Override public BlockEntityType<? extends CogEntity> getBlockEntityType(){return COG_ENTITY.get();}
    }
    static final class CogEntity extends KineticBlockEntity implements PowerKit.Dynamo {
        float demand;
        CogEntity(BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state);}
        @Override public float calculateStressApplied(){lastStressApplied=stress(demand);return lastStressApplied;}
        @Override public float rpm(){return Math.abs(getTheoreticalSpeed());}
        @Override public boolean overstressed(){return isOverStressed();}
        @Override public void demand(float perRpm){if(Math.abs(perRpm-demand)<1e-4f)return;demand=perRpm;setChanged();if(hasNetwork()&&level!=null&&!level.isClientSide)getOrCreateNetwork().updateStressFor(this,calculateStressApplied());}
        @Override protected void write(CompoundTag tag,boolean client){super.write(tag,client);tag.putFloat("Demand",demand);}
        @Override protected void read(CompoundTag tag,boolean client){super.read(tag,client);demand=tag.getFloat("Demand");}
    }
    static Item item(Block b){return b.asItem();}
}
