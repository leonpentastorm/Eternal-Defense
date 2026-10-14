package dev.eternaldefense.beacon;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.entity.Entity;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.*;

/** The Loadout Station: a 3 x 3 x 3 machine whose middle block (the anchor) draws it; the other 26 are invisible cells. */
final class LoadoutStation {
    private LoadoutStation(){}
    /** A cell's place in the cube, each axis stored as offset + 1 (0..2); the anchor is (1, 1, 1). */
    static final IntegerProperty DX=IntegerProperty.create("dx",0,2),DY=IntegerProperty.create("dy",0,2),DZ=IntegerProperty.create("dz",0,2);

    static boolean isStation(BlockState s){return s.is(EternalDefense.LOADOUT_STATION.get())||s.is(EternalDefense.LOADOUT_STATION_PART.get());}
    /** True for any of the station's 27 cells. */
    static boolean cell(BlockGetter level,BlockPos pos){var s=level.getBlockState(pos);return s.is(EternalDefense.LOADOUT_STATION.get())||s.is(EternalDefense.LOADOUT_STATION_PART.get());}
    static BlockPos anchor(BlockGetter level,BlockPos pos){var s=level.getBlockState(pos);return s.is(EternalDefense.LOADOUT_STATION_PART.get())?pos.offset(1-s.getValue(DX),1-s.getValue(DY),1-s.getValue(DZ)):pos;}
    /** All 27 blocks, the anchor first. */
    static List<BlockPos> cells(BlockPos anchor){var out=new ArrayList<BlockPos>();out.add(anchor);for(int y=-1;y<=1;y++)for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)if(x!=0||y!=0||z!=0)out.add(anchor.offset(x,y,z));return out;}
    static BlockState part(int x,int y,int z,Direction facing){return EternalDefense.LOADOUT_STATION_PART.get().defaultBlockState().setValue(DX,x+1).setValue(DY,y+1).setValue(DZ,z+1).setValue(DefenseStructures.FACING,facing);}
    static LoadoutData.Machine machine(Level level,BlockPos pos){
        if(!(level instanceof ServerLevel server)||!(level.getBlockEntity(anchor(level,pos)) instanceof StationEntity be))return null;
        var m=LoadoutData.get(server).machine(be.machineId);return m!=null&&m.placed?m:null;
    }
    static StationEntity entity(BlockGetter level,BlockPos anyCell){return level.getBlockEntity(anchor(level,anyCell)) instanceof StationEntity be?be:null;}
    static void say(Player p,String key,Object... args){if(p!=null)p.displayClientMessage(Component.translatable("gui.eternal_defense.loadout."+key,args),true);}
    /** World offset (dx, dz) of a cell turned back to the station's own north-facing frame. */
    static int[] local(int dx,int dz,Direction facing){
        for(int i=0;i<(4-LoadoutModel.quarters(facing))%4;i++){int t=dx;dx=-dz;dz=t;}
        return new int[]{dx,dz};
    }
    /** True while coins wait in the station's slots: then it cannot be mined. */
    static boolean holdsCoins(BlockGetter level,BlockPos pos){
        if(level instanceof ServerLevel server){var m=machine(server,pos);return m!=null&&m.hasCoins();}
        var be=entity(level,pos);return be!=null&&be.coins;
    }
    static boolean guarded(BlockGetter level,BlockPos pos,Player p){return p!=null&&!p.isCreative()&&holdsCoins(level,pos);}

    // Right-click opens the menu; sneak and right-click prints the active coin
    static InteractionResult use(Level level,BlockPos pos,Player player,InteractionHand hand){
        if(player.getItemInHand(hand).is(EternalDefense.CONTROLLER.get()))return InteractionResult.PASS;
        if(!(player instanceof ServerPlayer sp))return InteractionResult.SUCCESS;
        if(hand!=InteractionHand.MAIN_HAND)return InteractionResult.CONSUME;
        if(LoadoutPrint.holding(sp))return InteractionResult.CONSUME;
        var anchor=anchor(level,pos);var machine=machine(level,anchor);
        if(machine==null)return InteractionResult.CONSUME;
        String zone=BaseZone.problem(level,anchor);
        if(zone!=null){say(sp,"outside");return InteractionResult.CONSUME;}
        if(sp.isSecondaryUseActive()){LoadoutPrint.request(sp,machine,LoadoutData.get(sp.serverLevel()).players.active(sp.getUUID()));return InteractionResult.CONSUME;}
        open(sp,machine);
        return InteractionResult.CONSUME;
    }
    static void open(ServerPlayer sp,LoadoutData.Machine machine){
        net.minecraftforge.network.NetworkHooks.openScreen(sp,new SimpleMenuProvider((id,inv,p)->new LoadoutMenu(id,inv,machine),Component.translatable("block.eternal_defense.loadout_station")),b->b.writeBlockPos(machine.anchor));
    }

    // Collision: the shut machine for everybody, nothing for the player being printed
    private static final Map<String,VoxelShape> SHAPES=new HashMap<>();
    static VoxelShape cellShape(int dx,int dy,int dz,Direction facing){
        synchronized(SHAPES){
            return SHAPES.computeIfAbsent(dx+","+dy+","+dz+","+facing,k->{
                VoxelShape shape=Shapes.empty();
                for(var b:LoadoutModel.cellBoxes(dx,dy,dz,facing))shape=Shapes.or(shape,Block.box(b[0],b[1],b[2],b[3],b[4],b[5]));
                return shape.optimize();
            });
        }
    }
    /** True when ctx is the player this station is printing. */
    static boolean occupant(BlockGetter level,BlockPos anchor,CollisionContext ctx){
        if(!(ctx instanceof EntityCollisionContext ec)||!(ec.getEntity() instanceof Player p))return false;
        if(!(level instanceof Level l)||!l.isLoaded(anchor))return false;
        return l.getBlockEntity(anchor) instanceof StationEntity be&&p.getUUID().equals(be.occupant);
    }

    static class StationBlock extends Block implements EntityBlock {
        StationBlock(){super(Properties.of().strength(5f,3600000f).noOcclusion().sound(SoundType.METAL).lightLevel(s->7).isSuffocating((s,l,p)->false).isViewBlocking((s,l,p)->false));registerDefaultState(stateDefinition.any().setValue(DefenseStructures.FACING,Direction.NORTH));}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(DefenseStructures.FACING);}
        @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new StationEntity(pos,state);}
        @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){
            if(type!=EternalDefense.LOADOUT_STATION_ENTITY.get())return null;
            return level.isClientSide?(l,p,s,be)->LoadoutClientHooks.tick((StationEntity)be):(l,p,s,be)->((StationEntity)be).serverTick();
        }
        @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return Shapes.empty();}
        @Override public VoxelShape getCollisionShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return Shapes.empty();}
        @Override public boolean propagatesSkylightDown(BlockState s,BlockGetter l,BlockPos p){return true;}
        @Override public float getShadeBrightness(BlockState s,BlockGetter l,BlockPos p){return 1f;}
        @Override public InteractionResult use(BlockState s,Level l,BlockPos p,Player player,InteractionHand hand,BlockHitResult hit){return LoadoutStation.use(l,p,player,hand);}
        @Override public PushReaction getPistonPushReaction(BlockState s){return PushReaction.BLOCK;}
        @Override public float getDestroyProgress(BlockState s,Player p,BlockGetter l,BlockPos pos){return guarded(l,pos,p)?0:super.getDestroyProgress(s,p,l,pos);}
        @Override public void attack(BlockState s,Level l,BlockPos pos,Player p){if(!l.isClientSide&&guarded(l,pos,p))say(p,"empty_first");}
        @Override public boolean onDestroyedByPlayer(BlockState s,Level l,BlockPos pos,Player p,boolean willHarvest,FluidState fluid){
            if(guarded(l,pos,p)){if(!l.isClientSide)say(p,"empty_first");return false;}
            return super.onDestroyedByPlayer(s,l,pos,p,willHarvest,fluid);
        }
        @Override public boolean canEntityDestroy(BlockState s,BlockGetter l,BlockPos pos,Entity e){return false;}
        @Override public boolean canDropFromExplosion(BlockState s,BlockGetter l,BlockPos pos,Explosion e){return false;}
        @Override public void onBlockExploded(BlockState s,Level l,BlockPos pos,Explosion e){}
        @Override public boolean isFlammable(BlockState s,BlockGetter l,BlockPos pos,Direction d){return false;}
        @Override public int getFlammability(BlockState s,BlockGetter l,BlockPos pos,Direction d){return 0;}
        @Override public ItemStack getCloneItemStack(BlockGetter l,BlockPos pos,BlockState s){return new ItemStack(EternalDefense.LOADOUT_STATION_ITEM.get());}
        @Override public void tick(BlockState s,ServerLevel l,BlockPos pos,net.minecraft.util.RandomSource r){if(l.getBlockEntity(pos) instanceof StationEntity be)be.heal();}
        @Override public void onRemove(BlockState s,Level l,BlockPos pos,BlockState next,boolean moving){
            if(!l.isClientSide&&!s.is(next.getBlock())&&l instanceof ServerLevel server){
                var data=LoadoutData.get(server);var m=l.getBlockEntity(pos) instanceof StationEntity be?data.machine(be.machineId):null;
                if(m!=null&&m.anchor.equals(pos)){
                    LoadoutPrint.machineGone(server,m);
                    // A creative player's break, or a removal by commands or other mods: coins and locker drop
                    var drops=new ArrayList<ItemStack>();for(var c:m.coins)if(!c.isEmpty())drops.add(c);for(int i=0;i<m.locker.getContainerSize();i++)if(!m.locker.getItem(i).isEmpty())drops.add(m.locker.getItem(i));
                    for(var stack:drops)Containers.dropItemStack(l,pos.getX()+.5,pos.getY()-.5,pos.getZ()+.5,stack);
                    if(!drops.isEmpty())com.mojang.logging.LogUtils.getLogger().info("[Loadout Station] the station at {} was removed holding {} stacks: dropped them",pos.toShortString(),drops.size());
                    m.placed=false;data.remove(m);
                }
                for(var p:cells(pos))if(!p.equals(pos)&&l.hasChunkAt(p)&&l.getBlockState(p).is(EternalDefense.LOADOUT_STATION_PART.get())&&anchor(l,p).equals(pos))l.setBlock(p,Blocks.AIR.defaultBlockState(),3);
            }
            super.onRemove(s,l,pos,next,moving);
        }
    }

    static class PartBlock extends Block {
        PartBlock(){super(Properties.of().strength(5f,3600000f).noOcclusion().sound(SoundType.METAL).isSuffocating((s,l,p)->false).isViewBlocking((s,l,p)->false));
            registerDefaultState(stateDefinition.any().setValue(DX,1).setValue(DY,1).setValue(DZ,1).setValue(DefenseStructures.FACING,Direction.NORTH));}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(DX,DY,DZ,DefenseStructures.FACING);}
        @Override public RenderShape getRenderShape(BlockState s){return RenderShape.INVISIBLE;}
        private static VoxelShape shape(BlockState s){return cellShape(s.getValue(DX)-1,s.getValue(DY)-1,s.getValue(DZ)-1,s.getValue(DefenseStructures.FACING));}
        @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return shape(s);}
        @Override public VoxelShape getCollisionShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){
            if(occupant(l,p.offset(1-s.getValue(DX),1-s.getValue(DY),1-s.getValue(DZ)),c))return Shapes.empty();
            return shape(s);
        }
        @Override public boolean propagatesSkylightDown(BlockState s,BlockGetter l,BlockPos p){return true;}
        @Override public float getShadeBrightness(BlockState s,BlockGetter l,BlockPos p){return 1f;}
        @Override public PushReaction getPistonPushReaction(BlockState s){return PushReaction.BLOCK;}
        @Override public InteractionResult use(BlockState s,Level l,BlockPos p,Player player,InteractionHand hand,BlockHitResult hit){return LoadoutStation.use(l,p,player,hand);}
        @Override public float getDestroyProgress(BlockState s,Player p,BlockGetter l,BlockPos pos){return guarded(l,pos,p)?0:super.getDestroyProgress(s,p,l,pos);}
        @Override public void attack(BlockState s,Level l,BlockPos pos,Player p){if(!l.isClientSide&&guarded(l,pos,p))say(p,"empty_first");}
        @Override public boolean onDestroyedByPlayer(BlockState s,Level l,BlockPos pos,Player p,boolean willHarvest,FluidState fluid){
            if(guarded(l,pos,p)){if(!l.isClientSide)say(p,"empty_first");return false;}
            // Breaking any block of the station breaks the station: the anchor drops the item (not for creative players)
            var anchor=anchor(l,pos);if(!l.isClientSide&&l.getBlockState(anchor).is(EternalDefense.LOADOUT_STATION.get()))l.destroyBlock(anchor,!p.isCreative(),p);
            return super.onDestroyedByPlayer(s,l,pos,p,willHarvest,fluid);
        }
        @Override public boolean canEntityDestroy(BlockState s,BlockGetter l,BlockPos pos,Entity e){return false;}
        @Override public boolean canDropFromExplosion(BlockState s,BlockGetter l,BlockPos pos,Explosion e){return false;}
        @Override public void onBlockExploded(BlockState s,Level l,BlockPos pos,Explosion e){}
        @Override public boolean isFlammable(BlockState s,BlockGetter l,BlockPos pos,Direction d){return false;}
        @Override public int getFlammability(BlockState s,BlockGetter l,BlockPos pos,Direction d){return 0;}
        @Override public ItemStack getCloneItemStack(BlockGetter l,BlockPos pos,BlockState s){return new ItemStack(EternalDefense.LOADOUT_STATION_ITEM.get());}
        /** A cell removed by something other than a player (a command, another mod) */
        @Override public void onRemove(BlockState s,Level l,BlockPos pos,BlockState next,boolean moving){
            if(!l.isClientSide&&!s.is(next.getBlock())){var anchor=pos.offset(1-s.getValue(DX),1-s.getValue(DY),1-s.getValue(DZ));if(l.hasChunkAt(anchor)&&l.getBlockState(anchor).is(EternalDefense.LOADOUT_STATION.get()))l.scheduleTick(anchor,l.getBlockState(anchor).getBlock(),2);}
            super.onRemove(s,l,pos,next,moving);
        }
    }

    /** The anchor's block entity: which station it is, and the print in progress (synced, so every client animates it). */
    static final class StationEntity extends BlockEntity {
        UUID machineId;
        /** Synced: coins wait in the slots (it cannot be mined). */
        boolean coins;
        /** Synced: the print in progress (occupant null when idle). */
        UUID occupant;String occupantName="";long start;int entry,exitWalk;
        double[] from=new double[3],stand=new double[3],exit=new double[3];
        /** Synced: doors closing after a print was called off (game time, -1 when not), from this angle. */
        long abortAt=-1;float abortDoor;
        /** Client only: the print whose steam burst has been shown. */
        long steamFor=-1;
        private int ticks;
        StationEntity(BlockPos pos,BlockState state){super(EternalDefense.LOADOUT_STATION_ENTITY.get(),pos,state);}
        Direction facing(){return getBlockState().getValue(DefenseStructures.FACING);}
        LoadoutRules.Timeline timeline(){return new LoadoutRules.Timeline(Math.max(1,entry),Math.max(1,exitWalk));}
        boolean busy(){return occupant!=null;}
        /** Ticks since the print started (with partial), or -1 when idle. */
        float elapsed(long gameTime,float partial){return occupant==null?-1f:gameTime-start+partial;}
        float door(long gameTime,float partial){
            if(occupant!=null)return timeline().door(elapsed(gameTime,partial));
            if(abortAt>=0)return abortDoor*Math.max(0f,1f-(gameTime-abortAt+partial)/LoadoutRules.CLOSE_TICKS);
            return 0f;
        }
        void serverTick(){
            if(!(level instanceof ServerLevel server)||++ticks%20!=0)return;
            var data=LoadoutData.get(server);var m=data.machine(machineId);
            // Placed by a command, copied by a structure, or its record lost: it gets fresh, empty slots
            if(m==null||!m.anchor.equals(worldPosition)||!m.placed){m=data.place(server.dimension(),worldPosition,facing());machineId=m.id;setChanged();}
            boolean now=m.hasCoins();if(now!=coins){coins=now;sync();}
            if(abortAt>=0&&server.getGameTime()-abortAt>LoadoutRules.CLOSE_TICKS){abortAt=-1;sync();}
            if(ticks%40==0)heal();
        }
        void sync(){setChanged();if(level!=null)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}
        /** Puts back any of its 26 cells that is missing (and the space is free). */
        void heal(){
            if(!(level instanceof ServerLevel server))return;
            for(var p:cells(worldPosition)){if(p.equals(worldPosition)||!server.hasChunkAt(p))continue;
                var s=server.getBlockState(p);var want=part(p.getX()-worldPosition.getX(),p.getY()-worldPosition.getY(),p.getZ()-worldPosition.getZ(),facing());
                if(s!=want&&s.canBeReplaced()&&server.getBlockEntity(p)==null)server.setBlock(p,want,3);
            }
        }
        void begin(ServerPlayer p,long gameTime,LoadoutRules.Timeline tl,double[] from,double[] stand,double[] exit){
            occupant=p.getUUID();occupantName=p.getGameProfile().getName();start=gameTime;entry=tl.entry();exitWalk=tl.exitWalk();
            this.from=from.clone();this.stand=stand.clone();this.exit=exit.clone();abortAt=-1;sync();
        }
        /** The print is over; doors still open close from angle door. */
        void end(long gameTime,float door){occupant=null;occupantName="";if(door>0){abortAt=gameTime;abortDoor=door;}else abortAt=-1;sync();}
        @Override protected void saveAdditional(CompoundTag tag){super.saveAdditional(tag);if(machineId!=null)tag.putUUID("Machine",machineId);tag.putBoolean("Coins",coins);}
        @Override public void load(CompoundTag tag){super.load(tag);machineId=tag.hasUUID("Machine")?tag.getUUID("Machine"):null;coins=tag.getBoolean("Coins");}
        @Override public CompoundTag getUpdateTag(){
            var t=new CompoundTag();t.putBoolean("Coins",coins);
            if(occupant!=null){t.putUUID("Occupant",occupant);t.putString("Name",occupantName);t.putLong("Start",start);t.putInt("Entry",entry);t.putInt("ExitWalk",exitWalk);
                put(t,"From",from);put(t,"Stand",stand);put(t,"Exit",exit);}
            if(abortAt>=0){t.putLong("AbortAt",abortAt);t.putFloat("AbortDoor",abortDoor);}
            return t;
        }
        private static void put(CompoundTag t,String key,double[] v){var list=new net.minecraft.nbt.ListTag();for(double d:v)list.add(net.minecraft.nbt.DoubleTag.valueOf(d));t.put(key,list);}
        private static double[] get(CompoundTag t,String key){var list=t.getList(key,net.minecraft.nbt.Tag.TAG_DOUBLE);var out=new double[3];for(int i=0;i<3&&i<list.size();i++)out[i]=list.getDouble(i);return out;}
        @Override public void handleUpdateTag(CompoundTag t){
            coins=t.getBoolean("Coins");
            occupant=t.hasUUID("Occupant")?t.getUUID("Occupant"):null;occupantName=t.getString("Name");start=t.getLong("Start");entry=t.getInt("Entry");exitWalk=t.getInt("ExitWalk");
            from=get(t,"From");stand=get(t,"Stand");exit=get(t,"Exit");
            abortAt=t.contains("AbortAt")?t.getLong("AbortAt"):-1;abortDoor=t.getFloat("AbortDoor");
        }
        @Override public Packet<ClientGamePacketListener> getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
        @Override public void onDataPacket(net.minecraft.network.Connection net,ClientboundBlockEntityDataPacket pkt){if(pkt.getTag()!=null)handleUpdateTag(pkt.getTag());}
        /** The open right door reaches about half a block out front (README l.63). */
        @Override public AABB getRenderBoundingBox(){return new AABB(worldPosition).inflate(LoadoutModel.RENDER_INFLATE);}
    }

    // The station's item: places the 3 x 3 x 3 machine above the targeted block
    static final class StationItem extends BlockItem {
        StationItem(Block block){super(block,new Item.Properties().stacksTo(1).fireResistant());}
        /** The anchor for a station placed with this context: the block above the clicked spot (the bottom-centre cell is the spot). */
        static BlockPos anchorFor(BlockPlaceContext c){return c.getClickedPos().above();}
        static Direction facingFor(BlockPlaceContext c){return c.getHorizontalDirection().getOpposite();}
        static boolean free(Level level,BlockPos anchor){
            for(var p:cells(anchor)){
                if(!level.isInWorldBounds(p)||!level.getWorldBorder().isWithinBounds(p)||!level.hasChunkAt(p)||!level.getBlockState(p).canBeReplaced()||level.getBlockEntity(p)!=null
                    ||!level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,new AABB(p),e->!e.isSpectator()).isEmpty())return false;
            }
            return true;
        }
        @Override public InteractionResult place(BlockPlaceContext c){
            var level=c.getLevel();var player=c.getPlayer();var anchor=anchorFor(c);var facing=facingFor(c);
            if(level.isClientSide)return free(level,anchor)?InteractionResult.SUCCESS:InteractionResult.FAIL;
            for(var p:cells(anchor)){
                String zone=BaseZone.problem(level,p);if(zone!=null){BaseZone.say(player,"zone."+zone);return InteractionResult.FAIL;}
                if(level instanceof ServerLevel server&&ReturnZone.inside(server,p)){BaseZone.say(player,"keep_clear");return InteractionResult.FAIL;}
            }
            if(!free(level,anchor)){BaseZone.say(player,"no_room");return InteractionResult.FAIL;}
            var server=(ServerLevel)level;var stack=c.getItemInHand();
            var m=LoadoutData.get(server).place(server.dimension(),anchor,facing);
            server.setBlock(anchor,getBlock().defaultBlockState().setValue(DefenseStructures.FACING,facing),3);
            if(server.getBlockEntity(anchor) instanceof StationEntity be){be.machineId=m.id;be.setChanged();be.heal();}
            server.playSound(null,anchor,SoundEvents.METAL_PLACE,SoundSource.BLOCKS,1f,.8f);
            server.playSound(null,anchor,SoundEvents.PISTON_EXTEND,SoundSource.BLOCKS,.8f,.7f);
            server.sendParticles(ParticleTypes.CLOUD,anchor.getX()+.5,anchor.getY(),anchor.getZ()+.5,16,1.2,.8,1.2,.02);
            if(player==null||!player.getAbilities().instabuild)stack.shrink(1);
            com.mojang.logging.LogUtils.getLogger().info("[Loadout Station] {} placed a station at {} facing {}",player==null?"?":player.getGameProfile().getName(),anchor.toShortString(),facing.getSerializedName());
            return InteractionResult.CONSUME;
        }
        @Override public void appendHoverText(ItemStack stack,Level level,List<Component> lines,TooltipFlag flag){
            lines.add(Component.translatable("tooltip.eternal_defense.zone_only").withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("tooltip.eternal_defense.loadout_station").withStyle(ChatFormatting.GRAY));
        }
    }

    /** World position of a station-local point (px) for the anchor and facing. */
    static Vec3 world(BlockPos anchor,Direction facing,double x,double y,double z){var b=LoadoutModel.blocks(x,y,z,facing);return new Vec3(anchor.getX()+b[0],anchor.getY()+b[1],anchor.getZ()+b[2]);}
    /** Station-local px of a world position. */
    static double[] local(BlockPos anchor,Direction facing,Vec3 at){
        double x=(at.x-anchor.getX())*LoadoutModel.PX,y=(at.y-anchor.getY())*LoadoutModel.PX,z=(at.z-anchor.getZ())*LoadoutModel.PX;
        double dx=x-LoadoutModel.CENTRE,dz=z-LoadoutModel.CENTRE;
        for(int i=0;i<(4-LoadoutModel.quarters(facing))%4;i++){double t=dx;dx=-dz;dz=t;}
        return new double[]{LoadoutModel.CENTRE+dx,y,LoadoutModel.CENTRE+dz};
    }
}
