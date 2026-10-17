package dev.eternaldefense.beacon;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.ArrayList;
import java.util.List;

/** The shock box: wired from a pole it charges, and it keeps the barbed wire wired to it live */
final class ShockBoxes {
    private ShockBoxes(){}
    static final int MAX_LINKS=4;
    static final VoxelShape BOX=Shapes.box(.125,0,.125,.875,.9,.875);

    static final class BoxBlock extends Block implements EntityBlock {
        BoxBlock(){super(Properties.of().strength(3,6).noOcclusion().sound(SoundType.METAL).lightLevel(s->s.getValue(BlockStateProperties.POWERED)?7:0));
            registerDefaultState(stateDefinition.any().setValue(DefenseStructures.FACING,Direction.NORTH).setValue(BlockStateProperties.POWERED,false));}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(DefenseStructures.FACING,BlockStateProperties.POWERED);}
        @Override public BlockState getStateForPlacement(BlockPlaceContext c){return defaultBlockState().setValue(DefenseStructures.FACING,c.getHorizontalDirection().getOpposite());}
        @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return BOX;}
        @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new BoxEntity(pos,state);}
        @Override public void onRemove(BlockState s,Level l,BlockPos pos,BlockState next,boolean moving){
            if(!s.is(next.getBlock())&&l.getBlockEntity(pos) instanceof BoxEntity be)PowerPoles.refund(l,pos,be.links.size());
            super.onRemove(s,l,pos,next,moving);
        }
        @Override public InteractionResult use(BlockState s,Level l,BlockPos pos,Player p,InteractionHand hand,BlockHitResult hit){
            if(p.getItemInHand(hand).getItem() instanceof PowerPoles.WireItem)return InteractionResult.PASS;
            if(p instanceof ServerPlayer sp&&l.getBlockEntity(pos) instanceof BoxEntity be)
                sp.displayClientMessage(Component.translatable(be.powered?"gui.eternal_defense.power.box_on":be.charged()?"gui.eternal_defense.power.box_battery":"gui.eternal_defense.power.box_off",
                    be.charge*100/PowerKit.SHOCK_CHARGE,be.links.size()).withStyle(be.powered?ChatFormatting.AQUA:ChatFormatting.GOLD),true);
            return InteractionResult.sidedSuccess(l.isClientSide);
        }
    }

    static final class BoxEntity extends BlockEntity implements PowerGrid.Consumer {
        final List<BlockPos> links=new ArrayList<>();
        int charge;boolean powered;
        BoxEntity(BlockPos pos,BlockState state){super(EternalDefense.SHOCK_BOX_ENTITY.get(),pos,state);}
        @Override public void onLoad(){super.onLoad();if(level instanceof ServerLevel l)PowerGrid.joinBox(l,worldPosition);}
        @Override public float stress(){return PowerKit.SHOCK_STRESS;}
        @Override public void power(boolean on){powered=on;if(on)charge=PowerKit.SHOCK_CHARGE;show();}
        /** The battery runs down while the network is off */
        void drain(int ticks){if(!powered&&charge>0){charge=Math.max(0,charge-ticks);setChanged();show();}}
        boolean charged(){return charge>0;}
        private void show(){
            if(level==null)return;var s=getBlockState();boolean on=charged();
            if(s.getValue(BlockStateProperties.POWERED)!=on)level.setBlock(worldPosition,s.setValue(BlockStateProperties.POWERED,on),3);
        }
        boolean link(BlockPos to){if(links.contains(to)||links.size()>=MAX_LINKS)return false;links.add(to.immutable());sync();return true;}
        void sync(){setChanged();if(level!=null)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}
        @Override protected void saveAdditional(CompoundTag tag){super.saveAdditional(tag);tag.put("Links",PowerPoles.save(links));tag.putInt("Charge",charge);}
        @Override public void load(CompoundTag tag){super.load(tag);PowerPoles.read(tag.getList("Links",Tag.TAG_COMPOUND),links);charge=tag.getInt("Charge");}
        @Override public CompoundTag getUpdateTag(){var t=new CompoundTag();saveAdditional(t);return t;}
        @Override public Packet<ClientGamePacketListener> getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
        @Override public AABB getRenderBoundingBox(){return PowerPoles.reach(worldPosition,links);}
    }
}
