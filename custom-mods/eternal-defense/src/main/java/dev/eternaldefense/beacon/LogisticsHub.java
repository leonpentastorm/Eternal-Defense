package dev.eternaldefense.beacon;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.Nullable;
import java.util.*;

/** The Logistics Center Hub, a 3 x 3 x 3 depot. */
final class LogisticsHub {
    private LogisticsHub(){}
    /** A part's place in the cube, each axis stored as offset + 1 (0..2); the anchor is (1, 1, 1). */
    static final IntegerProperty DX=IntegerProperty.create("dx",0,2),DY=IntegerProperty.create("dy",0,2),DZ=IntegerProperty.create("dz",0,2);

    static boolean isHub(BlockState s){return s.is(EternalDefense.LOGISTICS_HUB.get())||s.is(EternalDefense.LOGISTICS_HUB_PART.get());}
    /** The anchor of the Hub this block belongs to (itself for the anchor). */
    static BlockPos anchor(BlockGetter level,BlockPos pos){var s=level.getBlockState(pos);return s.is(EternalDefense.LOGISTICS_HUB_PART.get())?pos.offset(1-s.getValue(DX),1-s.getValue(DY),1-s.getValue(DZ)):pos;}
    /** All 27 blocks, the anchor first. */
    static List<BlockPos> cells(BlockPos anchor){var out=new ArrayList<BlockPos>();out.add(anchor);for(int y=-1;y<=1;y++)for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)if(x!=0||y!=0||z!=0)out.add(anchor.offset(x,y,z));return out;}
    static BlockState part(int x,int y,int z){return EternalDefense.LOGISTICS_HUB_PART.get().defaultBlockState().setValue(DX,x+1).setValue(DY,y+1).setValue(DZ,z+1);}
    /** True when the Hub holds items. */
    static boolean occupied(BlockGetter level,BlockPos pos){
        if(level instanceof ServerLevel server){var hub=hub(server,pos);return hub!=null&&!hub.storage.isEmpty();}
        return level.getBlockEntity(anchor(level,pos)) instanceof HubEntity be&&be.occupied;
    }
    static HubData.Hub hub(Level level,BlockPos pos){
        if(!(level instanceof ServerLevel server)||!(level.getBlockEntity(anchor(level,pos)) instanceof HubEntity be))return null;
        return HubData.get(server).hub(be.hubId);
    }
    static void say(Player p,String key,Object... args){if(p!=null)p.displayClientMessage(Component.translatable("gui.eternal_defense.hub."+key,args),true);}

    // Shared behaviour of the anchor and its parts
    static InteractionResult open(Level level,BlockPos pos,Player player,InteractionHand hand){
        // The Recovery Shovel picks the Hub up (the shovel's own use, which runs only when the block lets it through)
        if(player.getItemInHand(hand).is(EternalDefense.CONTROLLER.get()))return InteractionResult.PASS;
        if(!(player instanceof ServerPlayer sp))return InteractionResult.SUCCESS;
        var anchor=anchor(level,pos);var hub=hub(level,anchor);
        if(hub==null)return InteractionResult.CONSUME;
        String problem=BaseZone.problem(level,anchor);
        if(problem!=null){say(sp,"outside."+problem);return InteractionResult.CONSUME;}
        net.minecraftforge.network.NetworkHooks.openScreen(sp,new SimpleMenuProvider((id,inv,p)->new HubMenu(id,inv,hub,anchor),Component.translatable("block.eternal_defense.logistics_hub")),b->b.writeBlockPos(anchor));
        return InteractionResult.CONSUME;
    }
    /** Mining a Hub that holds items: no progress at all (survival), and the reason in the action bar. */
    static boolean guarded(BlockGetter level,BlockPos pos,Player p){return p!=null&&!p.isCreative()&&occupied(level,pos);}

    static class HubBlock extends Block implements EntityBlock {
        HubBlock(){super(Properties.of().strength(5f,3600000f).noOcclusion().sound(SoundType.METAL).lightLevel(s->6));registerDefaultState(stateDefinition.any().setValue(DefenseStructures.FACING,Direction.NORTH));}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(DefenseStructures.FACING);}
        @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new HubEntity(pos,state);}
        @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return level.isClientSide||type!=EternalDefense.LOGISTICS_HUB_ENTITY.get()?null:(l,p,s,be)->((HubEntity)be).serverTick();}
        @Override public InteractionResult use(BlockState s,Level l,BlockPos p,Player player,InteractionHand hand,BlockHitResult hit){return open(l,p,player,hand);}
        @Override public PushReaction getPistonPushReaction(BlockState s){return PushReaction.BLOCK;}
        @Override public float getDestroyProgress(BlockState s,Player p,BlockGetter l,BlockPos pos){return guarded(l,pos,p)?0:super.getDestroyProgress(s,p,l,pos);}
        @Override public void attack(BlockState s,Level l,BlockPos pos,Player p){if(!l.isClientSide&&guarded(l,pos,p))say(p,"empty_first");}
        @Override public boolean onDestroyedByPlayer(BlockState s,Level l,BlockPos pos,Player p,boolean willHarvest,FluidState fluid){
            if(guarded(l,pos,p)){if(!l.isClientSide)say(p,"empty_first");return false;}
            return super.onDestroyedByPlayer(s,l,pos,p,willHarvest,fluid);
        }
        @Override public boolean canEntityDestroy(BlockState s,BlockGetter l,BlockPos pos,net.minecraft.world.entity.Entity e){return false;}
        @Override public boolean canDropFromExplosion(BlockState s,BlockGetter l,BlockPos pos,Explosion e){return false;}
        @Override public void onBlockExploded(BlockState s,Level l,BlockPos pos,Explosion e){}
        @Override public boolean isFlammable(BlockState s,BlockGetter l,BlockPos pos,Direction d){return false;}
        @Override public ItemStack getCloneItemStack(BlockGetter l,BlockPos pos,BlockState s){return new ItemStack(EternalDefense.LOGISTICS_HUB_ITEM.get());}
        @Override public void tick(BlockState s,ServerLevel l,BlockPos pos,net.minecraft.util.RandomSource r){if(l.getBlockEntity(pos) instanceof HubEntity be)be.heal();}
        @Override public void onRemove(BlockState s,Level l,BlockPos pos,BlockState next,boolean moving){
            if(!l.isClientSide&&!s.is(next.getBlock())&&l instanceof ServerLevel server){
                var data=HubData.get(server);var hub=l.getBlockEntity(pos) instanceof HubEntity be?data.hub(be.hubId):null;
                if(hub!=null&&hub.placed&&hub.anchor.equals(pos)){
                    // A creative player's break, or a removal by commands or other mods
                    var items=hub.storage.drain();
                    for(var stack:items)Containers.dropItemStack(l,pos.getX()+.5,pos.getY()+.2,pos.getZ()+.5,stack);
                    if(!items.isEmpty())com.mojang.logging.LogUtils.getLogger().info("[Logistics Hub] the Hub at {} was removed while holding {} stacks: dropped them",pos.toShortString(),items.size());
                    data.remove(hub);
                }
                for(var p:cells(pos))if(!p.equals(pos)&&l.hasChunkAt(p)&&l.getBlockState(p).is(EternalDefense.LOGISTICS_HUB_PART.get())&&anchor(l,p).equals(pos))l.setBlock(p,Blocks.AIR.defaultBlockState(),3);
            }
            super.onRemove(s,l,pos,next,moving);
        }
    }

    static class PartBlock extends Block implements EntityBlock {
        PartBlock(){super(Properties.of().strength(5f,3600000f).noOcclusion().sound(SoundType.METAL));registerDefaultState(stateDefinition.any().setValue(DX,1).setValue(DY,1).setValue(DZ,1));}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(DX,DY,DZ);}
        @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return partFactory.create(pos,state);}
        @Override public RenderShape getRenderShape(BlockState s){return RenderShape.INVISIBLE;}
        @Override public PushReaction getPistonPushReaction(BlockState s){return PushReaction.BLOCK;}
        @Override public InteractionResult use(BlockState s,Level l,BlockPos p,Player player,InteractionHand hand,BlockHitResult hit){return open(l,p,player,hand);}
        @Override public float getDestroyProgress(BlockState s,Player p,BlockGetter l,BlockPos pos){return guarded(l,pos,p)?0:super.getDestroyProgress(s,p,l,pos);}
        @Override public void attack(BlockState s,Level l,BlockPos pos,Player p){if(!l.isClientSide&&guarded(l,pos,p))say(p,"empty_first");}
        @Override public boolean onDestroyedByPlayer(BlockState s,Level l,BlockPos pos,Player p,boolean willHarvest,FluidState fluid){
            if(guarded(l,pos,p)){if(!l.isClientSide)say(p,"empty_first");return false;}
            // Breaking any block of the Hub breaks the Hub: the anchor drops the item (not for creative players)
            var anchor=anchor(l,pos);if(!l.isClientSide&&l.getBlockState(anchor).is(EternalDefense.LOGISTICS_HUB.get()))l.destroyBlock(anchor,!p.isCreative(),p);
            return super.onDestroyedByPlayer(s,l,pos,p,willHarvest,fluid);
        }
        @Override public boolean canEntityDestroy(BlockState s,BlockGetter l,BlockPos pos,net.minecraft.world.entity.Entity e){return false;}
        @Override public boolean canDropFromExplosion(BlockState s,BlockGetter l,BlockPos pos,Explosion e){return false;}
        @Override public void onBlockExploded(BlockState s,Level l,BlockPos pos,Explosion e){}
        @Override public boolean isFlammable(BlockState s,BlockGetter l,BlockPos pos,Direction d){return false;}
        @Override public ItemStack getCloneItemStack(BlockGetter l,BlockPos pos,BlockState s){return new ItemStack(EternalDefense.LOGISTICS_HUB_ITEM.get());}
        /** A part removed by something other than a player (a command, another mod) */
        @Override public void onRemove(BlockState s,Level l,BlockPos pos,BlockState next,boolean moving){
            if(!l.isClientSide&&!s.is(next.getBlock())){var anchor=pos.offset(1-s.getValue(DX),1-s.getValue(DY),1-s.getValue(DZ));if(l.hasChunkAt(anchor)&&l.getBlockState(anchor).is(EternalDefense.LOGISTICS_HUB.get()))l.scheduleTick(anchor,l.getBlockState(anchor).getBlock(),2);}
            super.onRemove(s,l,pos,next,moving);
        }
    }

    // Block entities: every one of the 27 hands out the Hub's inventory
    /** One of a Hub's 27 block entities: which Hub it belongs to (saved as "Hub"). */
    interface Cell {UUID hubId();void link(UUID id);}
    /** The item handler every block of Hub id hands out, on every face */
    static <T> LazyOptional<T> itemHandler(@Nullable Level level,@Nullable UUID id){
        if(level instanceof ServerLevel server&&id!=null){var hub=HubData.get(server).hub(id);if(hub!=null)return hub.handler(server).cast();}
        return LazyOptional.empty();
    }
    /** Makes a part's block entity: a plain one, or, in the pack edition with Create loaded */
    static BlockEntityType.BlockEntitySupplier<BlockEntity> partFactory=PartEntity::new;
    abstract static class CellEntity extends BlockEntity implements Cell {
        UUID hubId;
        CellEntity(BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state);}
        @Override public <T> LazyOptional<T> getCapability(Capability<T> cap,@Nullable Direction side){
            return cap==ForgeCapabilities.ITEM_HANDLER?itemHandler(level,hubId):super.getCapability(cap,side);
        }
        @Override protected void saveAdditional(CompoundTag tag){super.saveAdditional(tag);if(hubId!=null)tag.putUUID("Hub",hubId);}
        @Override public void load(CompoundTag tag){super.load(tag);hubId=tag.hasUUID("Hub")?tag.getUUID("Hub"):null;}
        @Override public UUID hubId(){return hubId;}
        @Override public void link(UUID id){hubId=id;setChanged();}
    }
    static final class HubEntity extends CellEntity {
        /** Synced to the client: the Hub holds items (it cannot be mined). */
        boolean occupied;private int ticks;
        HubEntity(BlockPos pos,BlockState state){super(EternalDefense.LOGISTICS_HUB_ENTITY.get(),pos,state);}
        void serverTick(){
            if(!(level instanceof ServerLevel server)||++ticks%20!=0)return;
            var data=HubData.get(server);var hub=data.hub(hubId);
            // Placed by a command, copied by a structure, or its record lost: it gets a fresh, empty inventory
            if(hub==null||!hub.anchor.equals(worldPosition)||!hub.placed){hub=data.place(null,server.dimension(),worldPosition,getBlockState().getValue(DefenseStructures.FACING));link(hub.id);}
            hub.working(server);
            boolean now=!hub.storage.isEmpty();if(now!=occupied){occupied=now;setChanged();server.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}
            if(ticks%40==0)heal();
        }
        /** Puts back any of its 26 parts that is missing (and the space is free), and points every part at this Hub. */
        void heal(){
            if(!(level instanceof ServerLevel server)||hubId==null)return;
            for(var p:cells(worldPosition)){if(p.equals(worldPosition)||!server.hasChunkAt(p))continue;
                var s=server.getBlockState(p);var want=part(p.getX()-worldPosition.getX(),p.getY()-worldPosition.getY(),p.getZ()-worldPosition.getZ());
                if(s!=want){if(!s.canBeReplaced()||server.getBlockEntity(p)!=null)continue;server.setBlock(p,want,3);}
                if(server.getBlockEntity(p) instanceof Cell part&&!hubId.equals(part.hubId()))part.link(hubId);
            }
        }
        @Override protected void saveAdditional(CompoundTag tag){super.saveAdditional(tag);tag.putBoolean("Occupied",occupied);}
        @Override public void load(CompoundTag tag){super.load(tag);occupied=tag.getBoolean("Occupied");}
        @Override public CompoundTag getUpdateTag(){var t=new CompoundTag();t.putBoolean("Occupied",occupied);return t;}
        @Override public void handleUpdateTag(CompoundTag tag){occupied=tag.getBoolean("Occupied");}
        @Override public Packet<ClientGamePacketListener> getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
        @Override public void onDataPacket(net.minecraft.network.Connection net,ClientboundBlockEntityDataPacket pkt){if(pkt.getTag()!=null)occupied=pkt.getTag().getBoolean("Occupied");}
        /** The model reaches a block past the anchor on every side. */
        @Override public net.minecraft.world.phys.AABB getRenderBoundingBox(){return new net.minecraft.world.phys.AABB(worldPosition).inflate(1);}
    }
    static final class PartEntity extends CellEntity {
        PartEntity(BlockPos pos,BlockState state){super(EternalDefense.LOGISTICS_HUB_PART_ENTITY.get(),pos,state);}
    }

    // The item: placed as the whole 3 x 3 x 3 Hub, centred on the block looked at
    static final class HubItem extends BlockItem {
        HubItem(Block block){super(block,new Item.Properties().stacksTo(1).fireResistant());}
        /** The anchor for a Hub placed with this context: the block above the clicked spot */
        static BlockPos anchorFor(BlockPlaceContext c){return c.getClickedPos().above();}
        /** Is every one of the 27 cells free (nothing solid, no block entity, no creature)? */
        static boolean free(Level level,BlockPos anchor){
            for(var p:cells(anchor)){
                if(!level.isInWorldBounds(p)||!level.getWorldBorder().isWithinBounds(p)||!level.hasChunkAt(p)||!level.getBlockState(p).canBeReplaced()||level.getBlockEntity(p)!=null
                    ||!level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,new net.minecraft.world.phys.AABB(p),e->!e.isSpectator()).isEmpty())return false;
            }
            return true;
        }
        @Override public InteractionResult place(BlockPlaceContext c){
            var level=c.getLevel();var player=c.getPlayer();var anchor=anchorFor(c);var facing=c.getHorizontalDirection().getOpposite();
            if(level.isClientSide)return free(level,anchor)?InteractionResult.SUCCESS:InteractionResult.FAIL;   // The server places it; the client only does not swing at a red preview
            for(var p:cells(anchor)){
                String zone=BaseZone.problem(level,p);if(zone!=null){BaseZone.say(player,"zone."+zone);return InteractionResult.FAIL;}
                if(level instanceof ServerLevel server&&ReturnZone.inside(server,p)){BaseZone.say(player,"keep_clear");return InteractionResult.FAIL;}
            }
            if(!free(level,anchor)){BaseZone.say(player,"no_room");return InteractionResult.FAIL;}
            var server=(ServerLevel)level;var stack=c.getItemInHand();
            UUID wanted=stack.hasTag()&&stack.getTag().hasUUID("HubId")?stack.getTag().getUUID("HubId"):null;
            var hub=HubData.get(server).place(wanted,server.dimension(),anchor,facing);
            server.setBlock(anchor,getBlock().defaultBlockState().setValue(DefenseStructures.FACING,facing),3);
            if(server.getBlockEntity(anchor) instanceof HubEntity be){be.link(hub.id);be.occupied=!hub.storage.isEmpty();be.heal();}
            server.playSound(null,anchor,SoundEvents.METAL_PLACE,SoundSource.BLOCKS,1f,.8f);
            server.playSound(null,anchor,SoundEvents.IRON_DOOR_CLOSE,SoundSource.BLOCKS,.8f,.7f);
            if(player==null||!player.getAbilities().instabuild)stack.shrink(1);
            com.mojang.logging.LogUtils.getLogger().info("[Logistics Hub] {} placed a Hub at {} ({} stacks inside)",player==null?"?":player.getGameProfile().getName(),anchor.toShortString(),hub.storage.usedSlots());
            return InteractionResult.CONSUME;
        }
        @Override public void appendHoverText(ItemStack stack,Level level,List<Component> lines,TooltipFlag flag){
            lines.add(Component.translatable("tooltip.eternal_defense.zone_only").withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("tooltip.eternal_defense.hub").withStyle(ChatFormatting.GRAY));
            if(stack.hasTag()&&stack.getTag().contains("HubStacks"))lines.add(Component.translatable("tooltip.eternal_defense.hub_moved",stack.getTag().getInt("HubStacks")).withStyle(ChatFormatting.GOLD));
            lines.add(Component.translatable("tooltip.eternal_defense.relocate").withStyle(ChatFormatting.DARK_AQUA));
        }
    }

    /** The Beacon Recovery Shovel on any block of a Hub: picked up as an item that remembers */
    static boolean relocate(ServerPlayer p,ServerLevel level,BlockPos clicked){
        if(!isHub(level.getBlockState(clicked)))return false;
        var anchor=anchor(level,clicked);var hub=hub(level,anchor);
        var stack=new ItemStack(EternalDefense.LOGISTICS_HUB_ITEM.get());
        if(hub!=null){HubData.get(level).pickUp(hub);var tag=stack.getOrCreateTag();tag.putUUID("HubId",hub.id);tag.putInt("HubStacks",hub.storage.usedSlots());
            com.mojang.logging.LogUtils.getLogger().info("[Logistics Hub] {} picked up the Hub at {} with the Recovery Shovel ({} stacks wait inside)",p.getGameProfile().getName(),anchor.toShortString(),hub.storage.usedSlots());}
        level.removeBlock(anchor,false);
        if(!p.getInventory().add(stack))p.drop(stack,false);
        BaseZone.say(p,"relocated");return true;
    }
}
