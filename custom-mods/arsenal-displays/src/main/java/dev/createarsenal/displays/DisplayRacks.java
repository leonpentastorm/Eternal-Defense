package dev.createarsenal.displays;

import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Chunk-baked furniture, with one persistent TaCZ gun and no ticking block entity. */
public final class DisplayRacks {
    public static final BooleanProperty PART=BooleanProperty.create("part");
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static BlockPos partner(BlockPos origin,BlockState s){return origin.relative(s.getValue(FACING).getClockWise());}
    public static BlockPos anchor(BlockGetter l,BlockPos p){var s=l.getBlockState(p);return s.getBlock() instanceof Rack&&s.getValue(PART)?p.relative(s.getValue(FACING).getCounterClockWise()):p;}
    public static boolean isGun(ItemStack stack){
        if(stack.isEmpty())return false;
        try{return stack.getItem().getClass().getMethod("getGunId",ItemStack.class).invoke(stack.getItem(),stack)!=null&&validGun(stack);}
        catch(ReflectiveOperationException ignored){return false;}
    }
    private static boolean validGun(ItemStack stack){
        try{var id=(ResourceLocation)stack.getItem().getClass().getMethod("getGunId",ItemStack.class).invoke(stack.getItem(),stack);return ((Optional<?>)Class.forName("com.tacz.guns.api.TimelessAPI").getMethod("getCommonGunIndex",ResourceLocation.class).invoke(null,id)).isPresent();}
        catch(ReflectiveOperationException ignored){return false;}
    }
    public static final class Rack extends BaseEntityBlock {
        final boolean wide,wall,compact,glass,heavy;
        public boolean wide(){return wide;}
        public boolean glass(){return glass;}
        // Gun origin is near the receiver: keep extra clearance below for magazines.
        float gunScale(){return heavy?.65f:glass?(wide?.48f:.30f):(wide?.8f:.5f);}
        double gunY(){return heavy?13.5:glass?(wall?9.75:wide?20:17.25):(compact?(wall?7.75:17.5):(wall?8.5:19.5));}
        double gunZ(){return glass?(wall?12.25:8):(compact?(wall?13.25:8.5):(wall?12:8));}
        private final Map<Direction,VoxelShape> shapes=new EnumMap<>(Direction.class);
        Rack(boolean wide,boolean wall,boolean compact){this(wide,wall,compact,false);}
        Rack(boolean wide,boolean wall,boolean compact,boolean glass){this(wide,wall,compact,glass,false);}
        Rack(boolean wide,boolean wall,boolean compact,boolean glass,boolean heavy){super(Properties.of().strength(2.5f,6).noOcclusion());this.wide=wide;this.wall=wall;this.compact=compact;this.glass=glass;this.heavy=heavy;
            registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(PART,false));
            for(Direction facing:Direction.Plane.HORIZONTAL){
                VoxelShape s=heavy?Block.box(.25,0,.75,15.75,6.75,15.25):glass?(wall?Block.box(.5,0,8.75,15.5,16,16):Block.box(.5,0,.75,15.5,wide?27:22,15)):(wall?Block.box(0,2,10,16,14,16):Block.box(2,0,2,14,compact?18.5:20.5,14));
                int turns=switch(facing){case EAST->1;case SOUTH->2;case WEST->3;default->0;};
                for(int turn=0;turn<turns;turn++){var box=s.bounds();s=Shapes.box(1-box.maxZ,box.minY,box.minX,1-box.minZ,box.maxY,box.maxX);}shapes.put(facing,s);
            }
        }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING,PART);}
        @Override public RenderShape getRenderShape(BlockState s){return s.getValue(PART)?RenderShape.INVISIBLE:RenderShape.MODEL;}
        @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return shapes.get(s.getValue(FACING));}
        @Override public net.minecraft.world.level.material.PushReaction getPistonPushReaction(BlockState s){return net.minecraft.world.level.material.PushReaction.BLOCK;}
        @Override public BlockState getStateForPlacement(BlockPlaceContext c){
            var state=defaultBlockState().setValue(FACING,c.getHorizontalDirection().getOpposite());BlockPos p=c.getClickedPos();
            if(!canSurvive(state,c.getLevel(),p))return null;
            if(wide){var next=partner(p,state);if(!c.getLevel().getWorldBorder().isWithinBounds(next)||!c.getLevel().hasChunkAt(next)||!c.getLevel().getBlockState(next).canBeReplaced(c)||c.getLevel().getBlockEntity(next)!=null)return null;}
            return state;
        }
        @Override public boolean canSurvive(BlockState s,LevelReader l,BlockPos p){
            if(s.getValue(PART)){var root=p.relative(s.getValue(FACING).getCounterClockWise());var r=l.getBlockState(root);return r.is(this)&&!r.getValue(PART)&&r.getValue(FACING)==s.getValue(FACING);}
            if(!wall)return true;
            var back=s.getValue(FACING).getOpposite();for(BlockPos cell:wide?List.of(p,partner(p,s)):List.of(p))if(!l.getBlockState(cell.relative(back)).isFaceSturdy(l,cell.relative(back),s.getValue(FACING)))return false;return true;
        }
        @Override public BlockState updateShape(BlockState s,Direction dir,BlockState neighbor,LevelAccessor l,BlockPos p,BlockPos np){return canSurvive(s,l,p)?super.updateShape(s,dir,neighbor,l,p,np):Blocks.AIR.defaultBlockState();}
        @Override public void onPlace(BlockState s,Level l,BlockPos p,BlockState old,boolean moving){
            super.onPlace(s,l,p,old,moving);if(!l.isClientSide&&wide&&!s.getValue(PART)){var next=partner(p,s);if(l.getBlockState(next).canBeReplaced()&&l.getBlockEntity(next)==null)l.setBlock(next,s.setValue(PART,true),3);}
        }
        @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return s.getValue(PART)?null:new DisplayEntity(p,s);}
        @Override public InteractionResult use(BlockState s,Level l,BlockPos p,Player player,InteractionHand hand,BlockHitResult hit){
            if(hand!=InteractionHand.MAIN_HAND)return InteractionResult.PASS;
            var entity=l.getBlockEntity(anchor(l,p));if(!(entity instanceof DisplayEntity display))return InteractionResult.PASS;
            var held=player.getItemInHand(hand);if(!held.isEmpty()&&!isGun(held))return InteractionResult.PASS;
            if(!l.isClientSide){
                if(held.isEmpty()){ItemStack gun=display.take();if(!gun.isEmpty()&&!player.getInventory().add(gun))player.drop(gun,false);}
                else {ItemStack previous=display.swap(held.copyWithCount(1));if(!player.isCreative())held.shrink(1);if(!previous.isEmpty()&&!player.getInventory().add(previous))player.drop(previous,false);}
            }return InteractionResult.sidedSuccess(l.isClientSide);
        }
        @Override public List<ItemStack> getDrops(BlockState s,LootParams.Builder b){return s.getValue(PART)?List.of():List.of(new ItemStack(this));}
        @Override public void playerWillDestroy(Level l,BlockPos p,BlockState s,Player player){
            if(!l.isClientSide&&s.getValue(PART))l.destroyBlock(anchor(l,p),!player.isCreative(),player);super.playerWillDestroy(l,p,s,player);
        }
        @Override public void onRemove(BlockState s,Level l,BlockPos p,BlockState next,boolean moving){
            if(!next.is(this)&&!s.getValue(PART)){
                if(!l.isClientSide&&l.getBlockEntity(p) instanceof DisplayEntity display){ItemStack gun=display.take();if(!gun.isEmpty())Containers.dropItemStack(l,p.getX()+.5,p.getY()+.5,p.getZ()+.5,gun);}
                if(wide){var other=partner(p,s);var partnerState=l.getBlockState(other);if(partnerState.is(this)&&partnerState.getValue(PART)&&partnerState.getValue(FACING)==s.getValue(FACING))l.setBlock(other,Blocks.AIR.defaultBlockState(),3);}
            }super.onRemove(s,l,p,next,moving);
        }
    }
    public static final class DisplayEntity extends BlockEntity {
        private ItemStack gun=ItemStack.EMPTY;
        public DisplayEntity(BlockPos p,BlockState s){super(GunDisplays.DISPLAY_ENTITY.get(),p,s);}
        public ItemStack gun(){return gun;}
        public ItemStack swap(ItemStack incoming){ItemStack previous=gun;gun=incoming.copyWithCount(1);changed();return previous;}
        public ItemStack take(){var previous=gun;gun=ItemStack.EMPTY;if(!previous.isEmpty())changed();return previous;}
        private void changed(){setChanged();if(level!=null&&!level.isClientSide)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}
        @Override protected void saveAdditional(CompoundTag n){super.saveAdditional(n);if(!gun.isEmpty())n.put("Item",gun.save(new CompoundTag()));}
        @Override public void load(CompoundTag n){super.load(n);gun=n.contains("Item",Tag.TAG_COMPOUND)?ItemStack.of(n.getCompound("Item")):ItemStack.EMPTY;}
        @Override public CompoundTag getUpdateTag(){return saveWithoutMetadata();}
        @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
        @Override public AABB getRenderBoundingBox(){return new AABB(worldPosition).inflate(2);}
    }
    static final class RackItem extends BlockItem {
        RackItem(Block b){super(b,new Item.Properties());}
        /** Short interaction cues from the language file; the second block describes only what is special about this family. */
        @Override public void appendHoverText(ItemStack s,Level l,List<Component> lines,TooltipFlag flag){
            super.appendHoverText(s,l,lines,flag);
            lines.add(Component.translatable("tooltip.arsenal_displays.insert").withStyle(net.minecraft.ChatFormatting.GRAY));
            lines.add(Component.translatable("tooltip.arsenal_displays.swap").withStyle(net.minecraft.ChatFormatting.GRAY));
            lines.add(Component.translatable("tooltip.arsenal_displays.retrieve").withStyle(net.minecraft.ChatFormatting.GRAY));
            if(getBlock() instanceof Rack rack){
                if(rack.wall)lines.add(Component.translatable("tooltip.arsenal_displays.wall").withStyle(net.minecraft.ChatFormatting.DARK_AQUA));
                if(rack.glass)lines.add(Component.translatable("tooltip.arsenal_displays.glass").withStyle(net.minecraft.ChatFormatting.DARK_AQUA));
                if(rack.heavy)lines.add(Component.translatable("tooltip.arsenal_displays.heavy").withStyle(net.minecraft.ChatFormatting.DARK_AQUA));
                else if(rack.wide)lines.add(Component.translatable("tooltip.arsenal_displays.wide").withStyle(net.minecraft.ChatFormatting.DARK_AQUA));
            }
        }
    }
}
