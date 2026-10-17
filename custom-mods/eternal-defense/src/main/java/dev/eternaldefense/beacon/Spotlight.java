package dev.eternaldefense.beacon;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.List;

/** The Industrial Spotlight: a work light, 2 blocks tall, that lights its surroundings while a powered Power Pole reaches it */
final class Spotlight {
    private Spotlight(){}
    static final BooleanProperty LIT=BlockStateProperties.LIT;
    /** Stress it asks for, per RPM of the dynamos */
    static final int STRESS=2;

    static final class LightBlock extends Block implements EntityBlock,DefenseStructures.Footprint {
        LightBlock(){super(Properties.of().strength(3,6).noOcclusion().sound(SoundType.METAL).lightLevel(s->s.getValue(LIT)?15:0));registerDefaultState(stateDefinition.any().setValue(DefenseStructures.FACING,Direction.NORTH).setValue(LIT,false));}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(DefenseStructures.FACING,LIT);}
        @Override public BlockState getStateForPlacement(BlockPlaceContext c){return defaultBlockState().setValue(DefenseStructures.FACING,c.getHorizontalDirection().getOpposite());}
        @Override public List<BlockPos> extra(BlockPos root,BlockState state){return List.of(root.above());}
        @Override public VoxelShape whole(BlockState state){return Shapes.box(.03,0,.03,.97,1.98,.97);}
        @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return DefenseStructures.cell(s,0,0,0);}
        @Override public PushReaction getPistonPushReaction(BlockState s){return PushReaction.BLOCK;}
        @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new LightEntity(pos,state);}
        @Override public void onPlace(BlockState s,Level l,BlockPos pos,BlockState old,boolean moving){super.onPlace(s,l,pos,old,moving);if(l instanceof ServerLevel)DefenseStructures.install(l,pos,s);}
        @Override public void tick(BlockState s,ServerLevel l,BlockPos pos,RandomSource random){DefenseStructures.install(l,pos,s);}
        @Override public void onRemove(BlockState s,Level l,BlockPos pos,BlockState next,boolean moving){if(!s.is(next.getBlock()))DefenseStructures.remove(l,pos);super.onRemove(s,l,pos,next,moving);}
        @Override public InteractionResult use(BlockState s,Level l,BlockPos pos,Player p,InteractionHand hand,BlockHitResult hit){
            if(p.getItemInHand(hand).getItem() instanceof PowerPoles.WireItem)return InteractionResult.PASS;
            if(p instanceof ServerPlayer sp)sp.displayClientMessage(s.getValue(LIT)?Component.translatable("gui.eternal_defense.spotlight.on",getName()).withStyle(ChatFormatting.AQUA)
                :Component.translatable("gui.eternal_defense.spotlight.off",getName(),PowerKit.AURA).withStyle(ChatFormatting.GOLD),true);
            return InteractionResult.sidedSuccess(l.isClientSide);
        }
    }
    /** Draws a little power from the pole that reaches it, like a turret */
    static final class LightEntity extends BlockEntity implements PowerGrid.Consumer {
        LightEntity(BlockPos pos,BlockState state){super(EternalDefense.SPOTLIGHT_ENTITY.get(),pos,state);}
        @Override public void onLoad(){super.onLoad();if(level instanceof ServerLevel l)PowerGrid.joinTurret(l,worldPosition);}
        @Override public float stress(){return STRESS;}
        @Override public void power(boolean on){var s=getBlockState();if(level!=null&&s.hasProperty(LIT)&&s.getValue(LIT)!=on)level.setBlock(worldPosition,s.setValue(LIT,on),3);}
    }
}
