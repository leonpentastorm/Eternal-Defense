package dev.createarsenal.beacon;

import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * The Supply Delivery Gate and the Supply Receiving Gate (0.0.24, FOB slice 2), the owner's models: a gantry arch 3 wide, 3 tall and 1 deep
 * over a straight rail. The ANCHOR is the middle cell of the opening, directly above the rail (the model is drawn from it: y -16 to 32,
 * x -16 to 32); it has no collision and no outline, so a minecart with a seated rider rolls through. The rail cell below it belongs to the
 * player. The seven other cells (the two columns and the beam) are invisible {@link Part}s that take the clicks; their collision is the owner's
 * model itself, cut per cell (read from the model JSON), so nothing reaches into the opening. Both gates cannot be mined or blown up; the
 * Recovery Shovel moves them and keeps their FOB. The traffic light is the {@code signal} state, drawn by an overlay model over the owner's
 * three lamps (the owner's files are not changed). The FOB's name on the two plates is drawn by {@code GateClient.GateRenderer}.
 */
final class RailGates {
    private RailGates(){}
    enum Kind{DELIVERY("supply_delivery_gate"),RECEIVING("supply_receiving_gate");
        final String id;Kind(String id){this.id=id;}
        RouteRules.Gate gate(){return this==DELIVERY?RouteRules.Gate.DELIVERY:RouteRules.Gate.RECEIVING;}
    }
    /** The traffic light as a block state (red, amber, green), set from {@link RouteRules#signal}. */
    enum Lamp implements StringRepresentable{RED,AMBER,GREEN;
        @Override public String getSerializedName(){return name().toLowerCase(Locale.ROOT);}
        static Lamp of(RouteRules.Signal s){return values()[s.ordinal()];}
    }
    static final DirectionProperty FACING=HorizontalDirectionalBlock.FACING;
    static final EnumProperty<Lamp> SIGNAL=EnumProperty.create("signal",Lamp.class);
    /** A part's place in the frame, stored +1: side -1..1 (towards the facing's clockwise side) and height -1..1. */
    static final IntegerProperty SIDE=IntegerProperty.create("side",0,2),HEIGHT=IntegerProperty.create("height",0,2);
    /** From the owner's model: the frame is 3 cells wide and 3 tall, 1 deep, around the anchor; the opening is the anchor's column (x 0..16, y -16 to 15.85). */
    static final int WIDE=3,TALL=3,DEEP=1;
    /** The seven part cells: (side, height) around the anchor; the rail cell (0, -1) and the anchor (0, 0) are not among them. (A class of its own: no Minecraft types, for the tests.) */
    static final class Frame{private Frame(){}static final int[][] CELLS={{-1,-1},{-1,0},{-1,1},{0,1},{1,-1},{1,0},{1,1}};}
    static BlockPos cell(BlockPos anchor,Direction facing,int side,int height){return anchor.relative(facing.getClockWise(),side).above(height);}
    static List<BlockPos> parts(BlockPos anchor,Direction facing){var out=new ArrayList<BlockPos>();for(int[] f:Frame.CELLS)out.add(cell(anchor,facing,f[0],f[1]));return out;}

    // ---- collision from the owner's model -----------------------------------------------------------------------------------------------------
    private static final Map<Kind,List<double[]>> BOXES=new EnumMap<>(Kind.class);
    private static final Map<String,VoxelShape> SHAPES=new java.util.concurrent.ConcurrentHashMap<>();
    /** The element boxes of the gate's model (pixels, in the anchor's frame, facing north), read once from the jar's own resource. */
    static synchronized List<double[]> boxes(Kind kind){
        return BOXES.computeIfAbsent(kind,k->{
            var out=new ArrayList<double[]>();
            try(var in=RailGates.class.getResourceAsStream("/assets/arsenal_beacon/models/block/"+k.id+".json")){
                if(in==null)return out;
                for(var e:JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("elements")){
                    var o=e.getAsJsonObject();var from=o.getAsJsonArray("from");var to=o.getAsJsonArray("to");
                    out.add(new double[]{Math.min(from.get(0).getAsDouble(),to.get(0).getAsDouble()),Math.min(from.get(1).getAsDouble(),to.get(1).getAsDouble()),Math.min(from.get(2).getAsDouble(),to.get(2).getAsDouble()),
                        Math.max(from.get(0).getAsDouble(),to.get(0).getAsDouble()),Math.max(from.get(1).getAsDouble(),to.get(1).getAsDouble()),Math.max(from.get(2).getAsDouble(),to.get(2).getAsDouble())});
                }
            }catch(Exception ex){com.mojang.logging.LogUtils.getLogger().warn("[gates] could not read the {} model for collision: {}",k.id,ex.toString());}
            return out;
        });
    }
    /** The collision and outline of one frame cell: the model's boxes clipped to that cell, turned with the facing (as the blockstate turns the model). */
    static VoxelShape shape(Kind kind,Direction facing,int side,int height){
        return SHAPES.computeIfAbsent(kind+":"+facing+":"+side+":"+height,key->{
            VoxelShape shape=Shapes.empty();
            double cx0=side*16,cy0=height*16;
            for(double[] b:boxes(kind)){
                double x0=Math.max(b[0],cx0),x1=Math.min(b[3],cx0+16),y0=Math.max(b[1],cy0),y1=Math.min(b[4],cy0+16),z0=Math.max(b[2],0),z1=Math.min(b[5],16);
                if(x1-x0<.01||y1-y0<.01||z1-z0<.01)continue;
                double[] p=turn(x0-cx0,z0,facing),q=turn(x1-cx0,z1,facing);
                shape=Shapes.joinUnoptimized(shape,Shapes.box(Math.min(p[0],q[0])/16,(y0-cy0)/16,Math.min(p[1],q[1])/16,Math.max(p[0],q[0])/16,(y1-cy0)/16,Math.max(p[1],q[1])/16),BooleanOp.OR);
            }
            shape=shape.optimize();
            return shape.isEmpty()?Block.box(0,0,0,16,1,16):shape;   // never an unclickable cell
        });
    }
    /** A point of a cell (pixels) turned about the cell's middle the way the blockstate turns the model (north 0, east 90, south 180, west 270). */
    static double[] turn(double x,double z,Direction facing){
        return switch(facing){case EAST->new double[]{16-z,x};case SOUTH->new double[]{16-x,16-z};case WEST->new double[]{z,16-x};default->new double[]{x,z};};
    }

    // ---- placement: over a straight rail -----------------------------------------------------------------------------------------------------------
    /** The facing for a gate over this rail: along the rail, towards the player if the rail runs that way. Null when it is no straight rail. */
    static Direction facingOver(BlockState rail,Direction look){
        if(!(rail.getBlock() instanceof BaseRailBlock block))return null;
        RailShape shape=rail.getValue(block.getShapeProperty());
        if(shape==RailShape.NORTH_SOUTH)return look.getAxis()==Direction.Axis.Z?look.getOpposite():Direction.NORTH;
        if(shape==RailShape.EAST_WEST)return look.getAxis()==Direction.Axis.X?look.getOpposite():Direction.EAST;
        return null;
    }
    /** Room for the seven parts and the anchor (replaceable, no block entity, nobody standing there). */
    static boolean available(Level level,BlockPos anchor,Direction facing){
        var all=new ArrayList<BlockPos>(parts(anchor,facing));all.add(anchor);
        for(var p:all){
            if(!level.isInWorldBounds(p)||!level.getWorldBorder().isWithinBounds(p)||!level.hasChunkAt(p))return false;
            var s=level.getBlockState(p);if(s.getBlock() instanceof Part&&anchor.equals(anchorOf(p,s)))continue;
            if(!s.canBeReplaced()||level.getBlockEntity(p)!=null)return false;
            if(!level.getEntitiesOfClass(LivingEntity.class,new AABB(p),e->!e.isSpectator()).isEmpty())return false;
        }
        return true;
    }
    static void install(Level level,BlockPos anchor,BlockState state){
        if(level.isClientSide)return;var facing=state.getValue(FACING);
        for(int[] f:Frame.CELLS){
            var p=cell(anchor,facing,f[0],f[1]);var want=ArsenalBeacon.GATE_PART.get().defaultBlockState().setValue(FACING,facing).setValue(SIDE,f[0]+1).setValue(HEIGHT,f[1]+1);
            var s=level.getBlockState(p);
            if(s==want)continue;
            if(s.canBeReplaced()&&level.getBlockEntity(p)==null)level.setBlock(p,want,3);
        }
    }
    static void remove(Level level,BlockPos anchor,Direction facing){
        if(level.isClientSide)return;
        for(var p:parts(anchor,facing))if(level.hasChunkAt(p)&&level.getBlockState(p).getBlock() instanceof Part&&anchor.equals(anchorOf(p,level.getBlockState(p))))level.setBlock(p,Blocks.AIR.defaultBlockState(),3);
    }
    static BlockPos anchorOf(BlockPos part,BlockState s){var facing=s.getValue(FACING);return part.relative(facing.getClockWise(),-(s.getValue(SIDE)-1)).below(s.getValue(HEIGHT)-1);}
    /** The gate anchor at or behind {@code pos} (a part or the anchor itself), or null. */
    static BlockPos anchor(BlockGetter level,BlockPos pos){
        var s=level.getBlockState(pos);
        if(s.getBlock() instanceof GateBlock)return pos;
        if(s.getBlock() instanceof Part){var a=anchorOf(pos,s);return level.getBlockState(a).getBlock() instanceof GateBlock?a:null;}
        return null;
    }

    // ---- the anchor ----------------------------------------------------------------------------------------------------------------------------
    static final class GateBlock extends Block implements EntityBlock {
        final Kind kind;
        GateBlock(Kind kind){
            super(Properties.of().strength(-1f,3600000f).noOcclusion().noCollission().lightLevel(s->4).sound(SoundType.METAL).isSuffocating((s,l,p)->false).isViewBlocking((s,l,p)->false));
            this.kind=kind;registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(SIGNAL,Lamp.RED));
        }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING,SIGNAL);}
        /** No outline and no collision: the anchor is the opening above the rail. */
        @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return Shapes.empty();}
        @Override public VoxelShape getCollisionShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return Shapes.empty();}
        @Override public RenderShape getRenderShape(BlockState s){return RenderShape.MODEL;}
        @Override public PushReaction getPistonPushReaction(BlockState s){return PushReaction.BLOCK;}
        @Override public boolean canEntityDestroy(BlockState s,BlockGetter l,BlockPos pos,Entity e){return false;}
        @Override public boolean canBeReplaced(BlockState s,BlockPlaceContext c){return false;}
        @Override public void onPlace(BlockState s,Level l,BlockPos pos,BlockState old,boolean moving){super.onPlace(s,l,pos,old,moving);if(l instanceof ServerLevel)install(l,pos,s);}
        @Override public void tick(BlockState s,ServerLevel l,BlockPos pos,net.minecraft.util.RandomSource random){install(l,pos,s);}
        @Override public void neighborChanged(BlockState s,Level l,BlockPos pos,Block block,BlockPos from,boolean moving){if(!l.isClientSide)l.scheduleTick(pos,this,1);}
        @Override public void setPlacedBy(Level l,BlockPos pos,BlockState s,LivingEntity who,ItemStack stack){
            super.setPlacedBy(l,pos,s,who,stack);
            if(l instanceof ServerLevel server)Routes.placed(server,pos,kind,who instanceof ServerPlayer p?p:null,stack);
        }
        @Override public void onRemove(BlockState s,Level l,BlockPos pos,BlockState next,boolean moving){
            if(!s.is(next.getBlock())){remove(l,pos,s.getValue(FACING));if(l instanceof ServerLevel server)Routes.gateGone(server,pos);}
            super.onRemove(s,l,pos,next,moving);
        }
        @Override public InteractionResult use(BlockState s,Level l,BlockPos pos,Player p,InteractionHand hand,BlockHitResult hit){
            if(p instanceof ServerPlayer sp)Routes.use(sp,pos,sp.getItemInHand(hand));
            return InteractionResult.sidedSuccess(l.isClientSide);
        }
        @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState s){return new GateEntity(pos,s);}
        @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l,BlockState s,BlockEntityType<T> type){
            return l.isClientSide||type!=ArsenalBeacon.GATE_ENTITY.get()?null:(level,pos,state,be)->{if(level.getGameTime()%20==Math.floorMod(pos.asLong(),20L))Routes.light((ServerLevel)level,pos,state,(GateEntity)be);};
        }
    }

    // ---- the seven parts -------------------------------------------------------------------------------------------------------------------------
    static final class Part extends Block {
        Part(){super(Properties.of().strength(-1f,3600000f).noOcclusion().dynamicShape().sound(SoundType.METAL));registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(SIDE,0).setValue(HEIGHT,0));}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING,SIDE,HEIGHT);}
        @Override public RenderShape getRenderShape(BlockState s){return RenderShape.INVISIBLE;}
        @Override public PushReaction getPistonPushReaction(BlockState s){return PushReaction.BLOCK;}
        @Override public boolean canEntityDestroy(BlockState s,BlockGetter l,BlockPos pos,Entity e){return false;}
        @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos pos,CollisionContext c){
            var anchor=anchorOf(pos,s);var owner=l.getBlockState(anchor);
            if(!(owner.getBlock() instanceof GateBlock gate))return Block.box(0,0,0,16,16,16);
            return shape(gate.kind,s.getValue(FACING),s.getValue(SIDE)-1,s.getValue(HEIGHT)-1);
        }
        @Override public InteractionResult use(BlockState s,Level l,BlockPos pos,Player p,InteractionHand hand,BlockHitResult hit){
            var anchor=anchorOf(pos,s);var owner=l.getBlockState(anchor);
            if(!(owner.getBlock() instanceof GateBlock))return InteractionResult.PASS;
            return owner.use(l,p,hand,new BlockHitResult(hit.getLocation(),hit.getDirection(),anchor,hit.isInside()));
        }
        /** A creative player breaking a part takes the whole gate down. */
        @Override public void playerWillDestroy(Level l,BlockPos pos,BlockState s,Player p){var anchor=anchorOf(pos,s);if(!l.isClientSide&&l.getBlockState(anchor).getBlock() instanceof GateBlock)l.destroyBlock(anchor,false,p);super.playerWillDestroy(l,pos,s,p);}
        @Override public void neighborChanged(BlockState s,Level l,BlockPos pos,Block block,BlockPos from,boolean moving){if(!l.isClientSide)l.scheduleTick(pos,this,1);}
        @Override public void tick(BlockState s,ServerLevel l,BlockPos pos,net.minecraft.util.RandomSource random){
            var anchor=anchorOf(pos,s);if(!l.hasChunkAt(anchor)){l.scheduleTick(pos,this,20);return;}
            if(!(l.getBlockState(anchor).getBlock() instanceof GateBlock))l.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
        }
        @Override public void onRemove(BlockState s,Level l,BlockPos pos,BlockState next,boolean moving){
            if(!l.isClientSide&&!next.is(this)){var anchor=anchorOf(pos,s);if(l.getBlockState(anchor).getBlock() instanceof GateBlock)l.scheduleTick(anchor,l.getBlockState(anchor).getBlock(),1);}
            super.onRemove(s,l,pos,next,moving);
        }
    }

    // ---- the anchor's block entity: which FOB, and its name for the plates ------------------------------------------------------------------------
    static final class GateEntity extends BlockEntity {
        long fobId;String name="";
        GateEntity(BlockPos pos,BlockState s){super(ArsenalBeacon.GATE_ENTITY.get(),pos,s);}
        @Override public void load(CompoundTag t){super.load(t);fobId=t.getLong("Fob");name=t.getString("Name");}
        @Override protected void saveAdditional(CompoundTag t){super.saveAdditional(t);t.putLong("Fob",fobId);}
        /** The name travels to the client in the update tag: no packet of its own. An empty name shows UNLINKED. */
        @Override public CompoundTag getUpdateTag(){var t=new CompoundTag();t.putLong("Fob",fobId);t.putString("Name",level instanceof ServerLevel l?Routes.nameFor(l,fobId):name);return t;}
        @Override public Packet<ClientGamePacketListener> getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
        void link(long id){fobId=id;setChanged();if(level!=null)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}
        @Override public AABB getRenderBoundingBox(){return new AABB(worldPosition).inflate(2);}
    }

    // ---- the items ---------------------------------------------------------------------------------------------------------------------------------
    /** A gate is used on a straight rail and stands over it. The Delivery Gate goes in a FOB's village; the Receiving Gate in your base's zone. */
    static class GateItem extends BlockItem {
        final Kind kind;
        GateItem(Block block,Kind kind){super(block,new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));this.kind=kind;}
        @Override public InteractionResult place(BlockPlaceContext c){
            if(c.getLevel() instanceof ServerLevel server){
                var player=c.getPlayer() instanceof ServerPlayer p?p:null;
                String problem=Routes.placeProblem(server,c.getClickedPos(),kind,c.getHorizontalDirection(),c.getItemInHand());
                if(problem!=null){if(player!=null)Routes.say(player,"gate.refused."+problem,RouteRules.MIN_SEPARATION);return InteractionResult.FAIL;}
            }
            return super.place(c);
        }
        @Override protected BlockState getPlacementState(BlockPlaceContext c){
            var facing=facingOver(c.getLevel().getBlockState(c.getClickedPos().below()),c.getHorizontalDirection());
            if(facing==null)return null;
            var s=getBlock().defaultBlockState().setValue(FACING,facing);
            return canPlace(c,s)?s:null;
        }
        @Override public void appendHoverText(ItemStack stack,Level level,List<Component> lines,TooltipFlag flag){
            lines.add(Component.translatable("tooltip.arsenal_beacon."+kind.id).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("tooltip.arsenal_beacon.gate.ride").withStyle(ChatFormatting.DARK_AQUA));
            var tag=stack.getTag();if(tag!=null&&tag.contains("FobName"))lines.add(Component.translatable("tooltip.arsenal_beacon.gate.linked",tag.getString("FobName")).withStyle(ChatFormatting.GOLD));
        }
    }

    // ---- the gate screen's menu: no slots ---------------------------------------------------------------------------------------------------------
    static final class GateMenu extends AbstractContainerMenu {
        final BlockPos pos;final CompoundTag data;
        GateMenu(int id,Inventory inv,BlockPos pos,CompoundTag data){super(ArsenalBeacon.GATE_MENU.get(),id);this.pos=pos;this.data=data;}
        static GateMenu client(int id,Inventory inv,FriendlyByteBuf buf){var pos=buf.readBlockPos();var n=buf.readNbt();return new GateMenu(id,inv,pos,n==null?new CompoundTag():n);}
        @Override public boolean clickMenuButton(Player p,int id){if(p instanceof ServerPlayer sp)Routes.button(sp,pos,id);return true;}
        @Override public ItemStack quickMoveStack(Player p,int index){return ItemStack.EMPTY;}
        @Override public boolean stillValid(Player p){return p.level().getBlockState(pos).getBlock() instanceof GateBlock&&p.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<=100;}
    }
}
