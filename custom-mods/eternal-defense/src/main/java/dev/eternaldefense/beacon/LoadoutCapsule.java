package dev.eternaldefense.beacon;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;
import java.util.*;

/** The Loadout Capsule: a shell-shaped pod (1 x 1 x 2) that a Loadout Flare's call drops, holding the kit for its owner. */
final class LoadoutCapsule {
    private LoadoutCapsule(){}
    static void say(Player p,String key,Object... args){if(p!=null)p.displayClientMessage(Component.translatable("gui.eternal_defense.loadout.capsule."+key,args),true);}

    // Where it lands
    /** Two free cells over a sturdy floor, no liquid. */
    static boolean fits(Level level,BlockPos c){
        if(!level.hasChunkAt(c)||!level.isInWorldBounds(c.above()))return false;
        var feet=level.getBlockState(c);var head=level.getBlockState(c.above());
        if(!feet.canBeReplaced()||!head.canBeReplaced()||!feet.getFluidState().isEmpty()||!head.getFluidState().isEmpty())return false;
        if(level.getBlockEntity(c)!=null||level.getBlockEntity(c.above())!=null)return false;
        return level.getBlockState(c.below()).isFaceSturdy(level,c.below(),Direction.UP);
    }
    /** The cell itself when it fits, else the nearest that does within CAPSULE_SEARCH blocks, else null. */
    static BlockPos landingCell(Level level,BlockPos at){
        if(fits(level,at))return at;
        BlockPos best=null;double bestD=Double.MAX_VALUE;int r=LoadoutRules.CAPSULE_SEARCH;
        for(var c:BlockPos.betweenClosed(at.offset(-r,-r,-r),at.offset(r,r,r))){
            double d=c.distSqr(at);if(d>r*r||d>=bestD||!fits(level,c))continue;bestD=d;best=c.immutable();
        }
        return best;
    }
    /** The cannon's delivery: a capsule falls under a parachute outdoors or stands up at the spot; without room, the old parcel. */
    static void deliver(ServerLevel level,ServerPlayer caller,UUID owner,Vec3 flare,ListTag kit,boolean outside,int clear){
        var cell=landingCell(level,BlockPos.containing(flare));
        if(cell==null){
            SupportCalls.deliver(level,caller,flare,kit,outside,clear);
            if(caller!=null)caller.sendSystemMessage(Component.translatable("gui.eternal_defense.loadout.capsule.no_room").withStyle(ChatFormatting.YELLOW));
            return;
        }
        if(outside){
            var drop=new Drop(EternalDefense.CAPSULE_DROP.get(),level);drop.owner=owner;drop.kit=kit.copy();
            drop.setPos(cell.getX()+.5,flare.y+Math.max(1,clear)-.5,cell.getZ()+.5);drop.setFalling(true);
            level.sendParticles(ParticleTypes.CLOUD,flare.x,flare.y+1,flare.z,12,.3,.2,.3,.02);level.addFreshEntity(drop);
        }else{
            place(level,cell,owner,kit);
            level.sendParticles(ParticleTypes.END_ROD,cell.getX()+.5,cell.getY()+1,cell.getZ()+.5,18,.3,.6,.3,.05);
            level.playSound(null,cell,SoundEvents.ENDERMAN_TELEPORT,SoundSource.PLAYERS,.8f,1.4f);
        }
    }
    /** Stands a capsule at cell, turned to its owner, holding the kit. */
    static void place(ServerLevel level,BlockPos cell,UUID owner,ListTag kit){
        var p=owner==null?null:level.getServer().getPlayerList().getPlayer(owner);
        var facing=Direction.NORTH;
        if(p!=null&&p.level()==level){double dx=p.getX()-(cell.getX()+.5),dz=p.getZ()-(cell.getZ()+.5);if(dx*dx+dz*dz>1e-4)facing=Direction.getNearest(dx,0,dz);}
        level.setBlock(cell,EternalDefense.LOADOUT_CAPSULE.get().defaultBlockState().setValue(DefenseStructures.FACING,facing),3);
        level.setBlock(cell.above(),EternalDefense.LOADOUT_CAPSULE_PART.get().defaultBlockState().setValue(DefenseStructures.FACING,facing),3);
        if(level.getBlockEntity(cell) instanceof CapsuleEntity be){be.owner=owner;be.ownerName=p==null?"":p.getGameProfile().getName();be.fill(kit);be.sync();}
        level.playSound(null,cell,SoundEvents.ANVIL_LAND,SoundSource.BLOCKS,.6f,.8f);
        com.mojang.logging.LogUtils.getLogger().info("[Loadout Station] a capsule stands at {} facing {}",cell.toShortString(),facing.getSerializedName());
    }

    // Collision from the pod's model
    private static final Map<String,VoxelShape> SHAPES=new HashMap<>();
    static VoxelShape shape(int dy,Direction facing){
        synchronized(SHAPES){
            return SHAPES.computeIfAbsent(dy+","+facing,k->{VoxelShape s=Shapes.empty();for(var b:LoadoutModel.capsuleBoxes(dy,facing))s=Shapes.or(s,Block.box(b[0],b[1],b[2],b[3],b[4],b[5]));return s.optimize();});
        }
    }
    static BlockPos anchor(BlockGetter level,BlockPos pos){return level.getBlockState(pos).is(EternalDefense.LOADOUT_CAPSULE_PART.get())?pos.below():pos;}
    static InteractionResult use(Level level,BlockPos pos,Player player){
        if(!(player instanceof ServerPlayer sp))return InteractionResult.SUCCESS;
        if(!(level.getBlockEntity(anchor(level,pos)) instanceof CapsuleEntity be))return InteractionResult.CONSUME;
        if(be.owner!=null&&!be.owner.equals(sp.getUUID())){say(sp,"not_yours",be.ownerName);return InteractionResult.CONSUME;}
        if(be.folding())return InteractionResult.CONSUME;
        be.open();
        NetworkHooks.openScreen(sp,new SimpleMenuProvider((id,inv,p)->new CapsuleMenu(id,inv,be,be.items),Component.translatable("block.eternal_defense.loadout_capsule")),b->b.writeBlockPos(be.getBlockPos()));
        return InteractionResult.CONSUME;
    }
    static boolean guarded(Player p){return p==null||!p.isCreative();}

    static class CapsuleBlock extends Block implements EntityBlock {
        CapsuleBlock(){super(Properties.of().strength(-1f,3600000f).noOcclusion().sound(SoundType.METAL).noLootTable().isSuffocating((s,l,p)->false).isViewBlocking((s,l,p)->false));registerDefaultState(stateDefinition.any().setValue(DefenseStructures.FACING,Direction.NORTH));}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(DefenseStructures.FACING);}
        @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new CapsuleEntity(pos,state);}
        @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return level.isClientSide||type!=EternalDefense.LOADOUT_CAPSULE_ENTITY.get()?null:(l,p,s,be)->((CapsuleEntity)be).serverTick();}
        @Override public RenderShape getRenderShape(BlockState s){return RenderShape.INVISIBLE;}
        @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return shape(0,s.getValue(DefenseStructures.FACING));}
        @Override public InteractionResult use(BlockState s,Level l,BlockPos p,Player player,InteractionHand hand,BlockHitResult hit){return LoadoutCapsule.use(l,p,player);}
        @Override public PushReaction getPistonPushReaction(BlockState s){return PushReaction.BLOCK;}
        @Override public boolean canEntityDestroy(BlockState s,BlockGetter l,BlockPos pos,Entity e){return false;}
        @Override public boolean canDropFromExplosion(BlockState s,BlockGetter l,BlockPos pos,Explosion e){return false;}
        @Override public void onBlockExploded(BlockState s,Level l,BlockPos pos,Explosion e){}
        @Override public boolean isFlammable(BlockState s,BlockGetter l,BlockPos pos,Direction d){return false;}
        @Override public boolean propagatesSkylightDown(BlockState s,BlockGetter l,BlockPos p){return true;}
        @Override public void onRemove(BlockState s,Level l,BlockPos pos,BlockState next,boolean moving){
            if(!l.isClientSide&&!s.is(next.getBlock())){
                // A creative player's break or a command: the kit drops rather than vanish
                if(l.getBlockEntity(pos) instanceof CapsuleEntity be)Containers.dropContents(l,pos,be.items);
                if(l.getBlockState(pos.above()).is(EternalDefense.LOADOUT_CAPSULE_PART.get()))l.setBlock(pos.above(),Blocks.AIR.defaultBlockState(),3);
            }
            super.onRemove(s,l,pos,next,moving);
        }
    }
    static class CapsulePart extends Block {
        CapsulePart(){super(Properties.of().strength(-1f,3600000f).noOcclusion().sound(SoundType.METAL).noLootTable().isSuffocating((s,l,p)->false).isViewBlocking((s,l,p)->false));registerDefaultState(stateDefinition.any().setValue(DefenseStructures.FACING,Direction.NORTH));}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(DefenseStructures.FACING);}
        @Override public RenderShape getRenderShape(BlockState s){return RenderShape.INVISIBLE;}
        @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return shape(1,s.getValue(DefenseStructures.FACING));}
        @Override public InteractionResult use(BlockState s,Level l,BlockPos p,Player player,InteractionHand hand,BlockHitResult hit){return LoadoutCapsule.use(l,p,player);}
        @Override public PushReaction getPistonPushReaction(BlockState s){return PushReaction.BLOCK;}
        @Override public boolean canEntityDestroy(BlockState s,BlockGetter l,BlockPos pos,Entity e){return false;}
        @Override public boolean canDropFromExplosion(BlockState s,BlockGetter l,BlockPos pos,Explosion e){return false;}
        @Override public void onBlockExploded(BlockState s,Level l,BlockPos pos,Explosion e){}
        @Override public boolean isFlammable(BlockState s,BlockGetter l,BlockPos pos,Direction d){return false;}
        @Override public boolean propagatesSkylightDown(BlockState s,BlockGetter l,BlockPos p){return true;}
        @Override public boolean onDestroyedByPlayer(BlockState s,Level l,BlockPos pos,Player p,boolean willHarvest,FluidState fluid){
            if(!l.isClientSide&&p.isCreative()&&l.getBlockState(pos.below()).is(EternalDefense.LOADOUT_CAPSULE.get()))l.destroyBlock(pos.below(),false,p);
            return super.onDestroyedByPlayer(s,l,pos,p,willHarvest,fluid);
        }
        @Override public void onRemove(BlockState s,Level l,BlockPos pos,BlockState next,boolean moving){
            if(!l.isClientSide&&!s.is(next.getBlock())&&l.getBlockState(pos.below()).is(EternalDefense.LOADOUT_CAPSULE.get()))l.scheduleTick(pos.below(),l.getBlockState(pos.below()).getBlock(),1);
            super.onRemove(s,l,pos,next,moving);
        }
    }

    /** The pod's block entity: its owner, the kit in its 14 slots, the door and the fold, synced for every client. */
    static final class CapsuleEntity extends BlockEntity {
        UUID owner;String ownerName="";
        final SimpleContainer items=new SimpleContainer(LoadoutRules.KIT_SLOTS){@Override public void setChanged(){super.setChanged();CapsuleEntity.this.setChanged();}};
        /** Synced game times: door opened, door closed, folding (-1: not). */
        long openAt=-1,closeAt=-1,foldAt=-1;
        private int age;private boolean viewed;
        CapsuleEntity(BlockPos pos,BlockState state){super(EternalDefense.LOADOUT_CAPSULE_ENTITY.get(),pos,state);}
        Direction facing(){return getBlockState().getValue(DefenseStructures.FACING);}
        boolean folding(){return foldAt>=0;}
        void fill(ListTag kit){
            items.clearContent();int next=LoadoutRules.HOTBAR_FIRST;
            for(Tag t:kit){var c=(CompoundTag)t;var s=ItemStack.of(c);if(s.isEmpty())continue;int slot=LoadoutCall.slotOf(c);
                if(slot<0||slot>=LoadoutRules.KIT_SLOTS||!items.getItem(slot).isEmpty()){while(next<LoadoutRules.KIT_SLOTS&&!items.getItem(next).isEmpty())next++;slot=next<LoadoutRules.KIT_SLOTS?next:-1;}
                if(slot>=0)items.setItem(slot,s);else Containers.dropItemStack(level,worldPosition.getX()+.5,worldPosition.getY()+1,worldPosition.getZ()+.5,s);
            }
            setChanged();
        }
        void open(){if(level==null)return;viewed=true;openAt=level.getGameTime();closeAt=-1;sync();level.playSound(null,worldPosition,SoundEvents.PISTON_EXTEND,SoundSource.BLOCKS,.6f,1.4f);}
        void closed(){
            if(level==null||folding())return;viewed=false;closeAt=level.getGameTime();sync();level.playSound(null,worldPosition,SoundEvents.PISTON_CONTRACT,SoundSource.BLOCKS,.6f,1.4f);
            if(items.isEmpty())foldAt=closeAt+LoadoutRules.CAPSULE_DOOR_TICKS;
        }
        void sync(){setChanged();if(level!=null)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}
        void serverTick(){
            if(!(level instanceof ServerLevel server))return;
            long now=server.getGameTime();
            // Left unopened it lasts as long as a supply parcel, then the kit spills out and it folds away
            if(!viewed&&++age>SupportRules.PARCEL_LIFETIME_TICKS&&!folding()){
                for(int i=0;i<items.getContainerSize();i++)if(!items.getItem(i).isEmpty())Containers.dropItemStack(server,worldPosition.getX()+.5,worldPosition.getY()+.5,worldPosition.getZ()+.5,items.removeItemNoUpdate(i));
                foldAt=now;sync();
            }
            if(foldAt>=0&&now==foldAt){
                server.playSound(null,worldPosition,SoundEvents.FIRE_EXTINGUISH,SoundSource.BLOCKS,.8f,1.1f);
                for(var v:LoadoutModel.CAPSULE_VENTS){var p=LoadoutModel.blocks(v.x(),v.y(),v.z(),facing());var d=LoadoutModel.turnVector(v.dx(),v.dy(),v.dz(),facing());
                    for(int i=0;i<6;i++)server.sendParticles(ParticleTypes.CLOUD,worldPosition.getX()+p[0],worldPosition.getY()+p[1],worldPosition.getZ()+p[2],0,d[0],d[1]+.05,d[2],.12);}
                sync();
            }
            if(foldAt>=0&&now>=foldAt+LoadoutRules.CAPSULE_FOLD_TICKS){
                server.sendParticles(ParticleTypes.POOF,worldPosition.getX()+.5,worldPosition.getY()+.6,worldPosition.getZ()+.5,14,.25,.4,.25,.02);
                server.removeBlock(worldPosition.above(),false);server.removeBlock(worldPosition,false);
            }
        }
        @Override protected void saveAdditional(CompoundTag tag){
            super.saveAdditional(tag);if(owner!=null)tag.putUUID("Owner",owner);tag.putString("OwnerName",ownerName);tag.putInt("Age",age);
            var list=new ListTag();for(int i=0;i<items.getContainerSize();i++)if(!items.getItem(i).isEmpty()){var c=items.getItem(i).save(new CompoundTag());c.putByte("Slot",(byte)i);list.add(c);}
            tag.put("Kit",list);tag.putLong("FoldAt",foldAt);
        }
        @Override public void load(CompoundTag tag){
            super.load(tag);owner=tag.hasUUID("Owner")?tag.getUUID("Owner"):null;ownerName=tag.getString("OwnerName");age=tag.getInt("Age");
            items.clearContent();for(Tag t:tag.getList("Kit",Tag.TAG_COMPOUND)){var c=(CompoundTag)t;int i=c.getByte("Slot");if(i>=0&&i<LoadoutRules.KIT_SLOTS)items.setItem(i,ItemStack.of(c));}
            foldAt=tag.contains("FoldAt")?tag.getLong("FoldAt"):-1;
        }
        @Override public CompoundTag getUpdateTag(){var t=new CompoundTag();t.putLong("OpenAt",openAt);t.putLong("CloseAt",closeAt);t.putLong("FoldAt",foldAt);t.putString("OwnerName",ownerName);return t;}
        @Override public void handleUpdateTag(CompoundTag t){openAt=t.getLong("OpenAt");closeAt=t.getLong("CloseAt");foldAt=t.getLong("FoldAt");ownerName=t.getString("OwnerName");}
        @Override public Packet<ClientGamePacketListener> getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
        @Override public void onDataPacket(net.minecraft.network.Connection net,ClientboundBlockEntityDataPacket pkt){if(pkt.getTag()!=null)handleUpdateTag(pkt.getTag());}
        @Override public AABB getRenderBoundingBox(){return new AABB(worldPosition).expandTowards(0,1,0).inflate(.5);}
    }

    /** The capsule's menu: the kit in its own slots (take only) and Equip. */
    static final class CapsuleMenu extends AbstractContainerMenu {
        static final int W=332,H=200,INV_X=142,INV_Y=118,ROW_A=40,ROW_B=62,OFFHAND_X=224,EQUIP=0;
        final CapsuleEntity capsule;final Container items;final Player player;final BlockPos pos;
        CapsuleMenu(int id,Inventory inv,CapsuleEntity capsule,Container items){this(id,inv,capsule,items,capsule.getBlockPos());}
        private CapsuleMenu(int id,Inventory inv,CapsuleEntity capsule,Container items,BlockPos pos){
            super(EternalDefense.LOADOUT_CAPSULE_MENU.get(),id);this.capsule=capsule;this.items=items;this.player=inv.player;this.pos=pos;
            for(int i=0;i<LoadoutRules.ARMOR;i++)addSlot(new TakeOnly(items,LoadoutRules.ARMOR-1-i,INV_X+i*18,ROW_A));
            addSlot(new TakeOnly(items,LoadoutRules.OFFHAND_SLOT,OFFHAND_X,ROW_A));
            for(int i=0;i<LoadoutRules.HOTBAR;i++)addSlot(new TakeOnly(items,LoadoutRules.HOTBAR_FIRST+i,INV_X+i*18,ROW_B));
            for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inv,9+row*9+col,INV_X+col*18,INV_Y+row*18));
            for(int col=0;col<9;col++)addSlot(new Slot(inv,col,INV_X+col*18,INV_Y+58));
        }
        static CapsuleMenu client(int id,Inventory inv,FriendlyByteBuf buf){return new CapsuleMenu(id,inv,null,new SimpleContainer(LoadoutRules.KIT_SLOTS),buf.readBlockPos());}
        private static final class TakeOnly extends Slot {TakeOnly(Container c,int i,int x,int y){super(c,i,x,y);}@Override public boolean mayPlace(ItemStack s){return false;}}
        @Override public boolean stillValid(Player p){return capsule!=null&&!capsule.isRemoved()&&!capsule.folding()&&p.distanceToSqr(Vec3.atCenterOf(pos))<=64;}
        @Override public void removed(Player p){super.removed(p);if(capsule!=null&&!p.level().isClientSide)capsule.closed();}
        /** Equip: each kit item goes to its own slot when that slot is empty; nothing is replaced and the rest stays. */
        @Override public boolean clickMenuButton(Player p,int button){
            if(button!=EQUIP||capsule==null||!(p instanceof ServerPlayer sp))return false;
            var worn=LoadoutPrint.worn(sp);int moved=0,left=0;
            for(int i=0;i<LoadoutRules.KIT_SLOTS;i++){var s=items.getItem(i);if(s.isEmpty())continue;
                if(worn.get(i).isEmpty()){LoadoutPrint.wear(sp,i,s.copy());items.setItem(i,ItemStack.EMPTY);moved++;}else left++;}
            sp.getInventory().setChanged();sp.inventoryMenu.broadcastChanges();
            if(moved>0)sp.level().playSound(null,sp,SoundEvents.ARMOR_EQUIP_GENERIC,SoundSource.PLAYERS,1f,1f);
            say(sp,left>0?"equipped_some":"equipped_all",moved,left);
            return true;
        }
        @Override public ItemStack quickMoveStack(Player p,int index){
            if(index<0||index>=LoadoutRules.KIT_SLOTS)return ItemStack.EMPTY;var slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;
            var stack=slot.getItem();var copy=stack.copy();
            if(!moveItemStackTo(stack,LoadoutRules.KIT_SLOTS,LoadoutRules.KIT_SLOTS+36,true))return ItemStack.EMPTY;
            if(stack.isEmpty())slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();
            return copy;
        }
    }

    /** A capsule falling under the supply parachute; it stands up where it lands (or becomes a parcel without room). */
    static final class Drop extends Entity {
        private static final EntityDataAccessor<Boolean> FALLING=SynchedEntityData.defineId(Drop.class,EntityDataSerializers.BOOLEAN);
        UUID owner;ListTag kit=new ListTag();private int age;
        Drop(EntityType<? extends Drop> type,Level level){super(type,level);}
        void setFalling(boolean falling){entityData.set(FALLING,falling);}
        boolean falling(){return entityData.get(FALLING);}
        @Override protected void defineSynchedData(){entityData.define(FALLING,true);}
        @Override public AABB getBoundingBoxForCulling(){return getBoundingBox().inflate(3,0,3).expandTowards(0,7,0);}
        @Override public boolean isPickable(){return false;}
        @Override public boolean isPushable(){return false;}
        @Override public void tick(){
            super.tick();age++;
            setDeltaMovement(0,-SupportRules.PARCEL_FALL_SPEED,0);move(MoverType.SELF,getDeltaMovement());
            if(level().isClientSide)return;
            if(onGround()||verticalCollision||age>2400)land((ServerLevel)level());
        }
        private void land(ServerLevel level){
            var cell=landingCell(level,blockPosition());
            if(cell!=null)place(level,cell,owner,kit);
            else{
                // No two free blocks here: the old one-block parcel with the same items
                var parcel=new SupportCrate.ParcelEntity(EternalDefense.PARCEL.get(),level);parcel.setContents(kit);parcel.setPos(getX(),getY(),getZ());parcel.setFalling(false);level.addFreshEntity(parcel);
                var p=owner==null?null:level.getServer().getPlayerList().getPlayer(owner);if(p!=null)p.sendSystemMessage(Component.translatable("gui.eternal_defense.loadout.capsule.no_room").withStyle(ChatFormatting.YELLOW));
            }
            level.playSound(null,blockPosition(),SoundEvents.BARREL_CLOSE,SoundSource.NEUTRAL,1.4f,.6f);
            kit=new ListTag();discard();
        }
        @Override protected void readAdditionalSaveData(CompoundTag n){owner=n.hasUUID("Owner")?n.getUUID("Owner"):null;kit=n.getList("Kit",Tag.TAG_COMPOUND);age=n.getInt("Age");setFalling(n.getBoolean("Falling"));}
        @Override protected void addAdditionalSaveData(CompoundTag n){if(owner!=null)n.putUUID("Owner",owner);n.put("Kit",kit);n.putInt("Age",age);n.putBoolean("Falling",falling());}
        @Override public Packet<ClientGamePacketListener> getAddEntityPacket(){return NetworkHooks.getEntitySpawningPacket(this);}
    }
}
