package dev.eternaldefense.beacon;

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

/** The Supply Delivery Gate and the Supply Receiving Gate, the 4-wide models */
final class RailGates {
    private RailGates(){}
    enum Kind{DELIVERY("supply_delivery_gate"),RECEIVING("supply_receiving_gate");
        final String id;Kind(String id){this.id=id;}
        RouteRules.Gate gate(){return this==DELIVERY?RouteRules.Gate.DELIVERY:RouteRules.Gate.RECEIVING;}
        Block column(){return (this==DELIVERY?EternalDefense.DELIVERY_GATE_LEFT:EternalDefense.RECEIVING_GATE_LEFT).get();}
    }
    /** The traffic light as a block state (red, amber, green), set from RouteRules.signal. */
    enum Lamp implements StringRepresentable{RED,AMBER,GREEN;
        @Override public String getSerializedName(){return name().toLowerCase(Locale.ROOT);}
        static Lamp of(RouteRules.Signal s){return values()[s.ordinal()];}
    }
    static final DirectionProperty FACING=HorizontalDirectionalBlock.FACING;
    static final EnumProperty<Lamp> SIGNAL=EnumProperty.create("signal",Lamp.class);
    /** A beam part's place along the gate: 0 (over the anchor) to 3 (over the column) */
    static final IntegerProperty SIDE=IntegerProperty.create("side",0,3);
    /** From the models: 4 cells wide, 2 tall, 1 deep; the two middle cells of the bottom row are the lanes. */
    static final int WIDE=4,TALL=2,DEEP=1,LANES=2;
    /** The frame without Minecraft types (for the tests): cells are (side, height) from the anchor (0, 0). */
    static final class Frame{private Frame(){}
        /** The beam: the cell above each of the four bottom cells. */
        static final int[][] PARTS={{0,1},{1,1},{2,1},{3,1}};
        /** The left column, a block of its own (it draws the left half). */
        static final int[] COLUMN={3,0};
        /** The two rail lanes: free cells, never the gate's. */
        static final int[][] LANES={{1,0},{2,0}};
        /** Over a lane only the beam collides (pixels in the cell, facing north): a seated rider's head stays clear. */
        static final double[] BEAM={0,9.5,3,16,16,13};
        /** Which lane the clicked rail takes, in the order to try */
        static int[] laneOrder(boolean railClockwise,boolean railCounter,double clickSide){
            if(railClockwise!=railCounter)return railClockwise?new int[]{1,2}:new int[]{2,1};
            return clickSide>=0?new int[]{1,2}:new int[]{2,1};
        }
    }
    static BlockPos cell(BlockPos anchor,Direction facing,int side,int height){return anchor.relative(facing.getClockWise(),side).above(height);}
    static List<BlockPos> parts(BlockPos anchor,Direction facing){var out=new ArrayList<BlockPos>();for(int[] f:Frame.PARTS)out.add(cell(anchor,facing,f[0],f[1]));return out;}
    static BlockPos column(BlockPos anchor,Direction facing){return cell(anchor,facing,Frame.COLUMN[0],Frame.COLUMN[1]);}
    static List<BlockPos> lanes(BlockPos anchor,Direction facing){var out=new ArrayList<BlockPos>();for(int[] f:Frame.LANES)out.add(cell(anchor,facing,f[0],f[1]));return out;}
    /** The lane cell with the lower x and z: with LANES it spans the opening, for RouteRules.crossing. */
    static BlockPos laneCorner(BlockPos anchor,Direction facing){
        var a=cell(anchor,facing,1,0);var b=cell(anchor,facing,2,0);
        return new BlockPos(Math.min(a.getX(),b.getX()),a.getY(),Math.min(a.getZ(),b.getZ()));
    }

    // Collision from the models
    private static final Map<Kind,List<double[]>> BOXES=new EnumMap<>(Kind.class);
    private static final Map<String,VoxelShape> SHAPES=new java.util.concurrent.ConcurrentHashMap<>();
    /** The element boxes of one half-model (pixels, facing north), read from the jar's own resource */
    static List<double[]> boxes(String model,double dx){
        var out=new ArrayList<double[]>();
        try(var in=RailGates.class.getResourceAsStream("/assets/eternal_defense/models/block/"+model+".json")){
            if(in==null)return out;
            for(var e:JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("elements")){
                var o=e.getAsJsonObject();var from=o.getAsJsonArray("from");var to=o.getAsJsonArray("to");
                out.add(new double[]{Math.min(from.get(0).getAsDouble(),to.get(0).getAsDouble())+dx,Math.min(from.get(1).getAsDouble(),to.get(1).getAsDouble()),Math.min(from.get(2).getAsDouble(),to.get(2).getAsDouble()),
                    Math.max(from.get(0).getAsDouble(),to.get(0).getAsDouble())+dx,Math.max(from.get(1).getAsDouble(),to.get(1).getAsDouble()),Math.max(from.get(2).getAsDouble(),to.get(2).getAsDouble())});
            }
        }catch(Exception ex){com.mojang.logging.LogUtils.getLogger().warn("[gates] could not read the {} model for collision: {}",model,ex.toString());}
        return out;
    }
    /** The whole gate in the anchor's frame (x 0..64): the right half as drawn by the anchor */
    static synchronized List<double[]> boxes(Kind kind){
        return BOXES.computeIfAbsent(kind,k->{var out=new ArrayList<double[]>(boxes(k.id+"_right",0));out.addAll(boxes(k.id+"_left",48));return out;});
    }
    /** The outline of one gate cell: the models' boxes clipped to that cell */
    static VoxelShape shape(Kind kind,Direction facing,int side,int height){
        return SHAPES.computeIfAbsent(kind+":"+facing+":"+side+":"+height,key->{
            VoxelShape shape=Shapes.empty();
            double cx0=side*16,cy0=height*16;
            for(double[] b:boxes(kind)){
                double x0=Math.max(b[0],cx0),x1=Math.min(b[3],cx0+16),y0=Math.max(b[1],cy0),y1=Math.min(b[4],cy0+16),z0=Math.max(b[2],0),z1=Math.min(b[5],16);
                if(x1-x0<.01||y1-y0<.01||z1-z0<.01)continue;
                shape=Shapes.joinUnoptimized(shape,box(x0-cx0,y0-cy0,z0,x1-cx0,y1-cy0,z1,facing),BooleanOp.OR);
            }
            shape=shape.optimize();
            return shape.isEmpty()?Block.box(0,0,0,16,1,16):shape;   // Never an unclickable cell
        });
    }
    /** What a cell stops: over a lane only the beam, everywhere else the outline. */
    static VoxelShape collision(Kind kind,Direction facing,int side,int height){
        if(height==1&&(side==1||side==2)){var b=Frame.BEAM;return SHAPES.computeIfAbsent("beam:"+facing,key->box(b[0],b[1],b[2],b[3],b[4],b[5],facing));}
        return shape(kind,facing,side,height);
    }
    private static VoxelShape box(double x0,double y0,double z0,double x1,double y1,double z1,Direction facing){
        double[] p=turn(x0,z0,facing),q=turn(x1,z1,facing);
        return Shapes.box(Math.min(p[0],q[0])/16,y0/16,Math.min(p[1],q[1])/16,Math.max(p[0],q[0])/16,y1/16,Math.max(p[1],q[1])/16);
    }
    /** A point of a cell (pixels) turned about the cell's middle the way the blockstate turns the model */
    static double[] turn(double x,double z,Direction facing){
        return switch(facing){case EAST->new double[]{16-z,x};case SOUTH->new double[]{16-x,16-z};case WEST->new double[]{z,16-x};default->new double[]{x,z};};
    }

    // Placement: across one or two straight rails
    /** Where a gate goes when it is used on a rail: its anchor, its facing */
    record Spot(BlockPos anchor,Direction facing,int lane){}
    /** The facing for a gate across this rail: along the rail, towards the player if the rail runs that way. */
    static Direction facingOver(BlockState rail,Direction look){
        if(!(rail.getBlock() instanceof BaseRailBlock block))return null;
        RailShape shape=rail.getValue(block.getShapeProperty());
        if(shape==RailShape.NORTH_SOUTH)return look.getAxis()==Direction.Axis.Z?look.getOpposite():Direction.NORTH;
        if(shape==RailShape.EAST_WEST)return look.getAxis()==Direction.Axis.X?look.getOpposite():Direction.EAST;
        return null;
    }
    static boolean straight(BlockState s,Direction.Axis axis){
        if(!(s.getBlock() instanceof BaseRailBlock block))return false;
        var shape=s.getValue(block.getShapeProperty());
        return axis==Direction.Axis.Z?shape==RailShape.NORTH_SOUTH:shape==RailShape.EAST_WEST;
    }
    /** The gate across the rail at rail, its front towards the player */
    static Spot spot(Level level,BlockPos rail,Direction look,double clickSide){
        var facing=facingOver(level.getBlockState(rail),look);if(facing==null)return null;
        var cw=facing.getClockWise();
        int[] order=Frame.laneOrder(straight(level.getBlockState(rail.relative(cw)),facing.getAxis()),straight(level.getBlockState(rail.relative(cw.getOpposite())),facing.getAxis()),clickSide);
        Spot first=null;
        for(int lane:order){var s=new Spot(rail.relative(cw,-lane),facing,lane);if(first==null)first=s;if(available(level,s.anchor(),facing))return s;}
        return first;
    }
    /** Where a gate used with this click would stand. */
    static Spot placingSpot(net.minecraft.world.item.context.BlockPlaceContext c){
        var level=c.getLevel();var face=c.getClickedFace();var at=c.getClickLocation();
        var hit=BlockPos.containing(at.x-face.getStepX()*1e-3,at.y-face.getStepY()*1e-3,at.z-face.getStepZ()*1e-3);
        var rail=railFor(level,hit,c.getClickedPos());
        var look=c.getHorizontalDirection();
        var probe=facingOver(level.getBlockState(rail),look);
        double clickSide=probe==null?0:(at.x-(rail.getX()+.5))*probe.getClockWise().getStepX()+(at.z-(rail.getZ()+.5))*probe.getClockWise().getStepZ();
        return spot(level,rail,look,clickSide);
    }
    /** The rail a gate item was used on: the block clicked, or the one the click lands in front of or above. */
    static BlockPos railFor(Level level,BlockPos hit,BlockPos clicked){
        for(var p:new BlockPos[]{hit,clicked,clicked.below()})if(level.getBlockState(p).getBlock() instanceof BaseRailBlock)return p;
        return hit;
    }
    /** Room for the anchor, the column and the beam (replaceable, no block entity, nobody standing there). */
    static boolean available(Level level,BlockPos anchor,Direction facing){
        var all=new ArrayList<BlockPos>(parts(anchor,facing));all.add(anchor);all.add(column(anchor,facing));
        for(var p:all){
            if(!level.isInWorldBounds(p)||!level.getWorldBorder().isWithinBounds(p)||!level.hasChunkAt(p))return false;
            var s=level.getBlockState(p);if((s.getBlock() instanceof Part||s.getBlock() instanceof Column)&&anchor.equals(anchorOf(p,s)))continue;
            if(!s.canBeReplaced()||level.getBlockEntity(p)!=null)return false;
            if(!level.getEntitiesOfClass(LivingEntity.class,new AABB(p),e->!e.isSpectator()).isEmpty())return false;
        }
        return true;
    }
    static void install(Level level,BlockPos anchor,BlockState state){
        if(level.isClientSide||!(state.getBlock() instanceof GateBlock gate))return;var facing=state.getValue(FACING);
        for(int[] f:Frame.PARTS)put(level,cell(anchor,facing,f[0],f[1]),EternalDefense.GATE_PART.get().defaultBlockState().setValue(FACING,facing).setValue(SIDE,f[0]));
        put(level,column(anchor,facing),gate.kind.column().defaultBlockState().setValue(FACING,facing).setValue(SIGNAL,state.getValue(SIGNAL)));
    }
    private static void put(Level level,BlockPos p,BlockState want){
        var s=level.getBlockState(p);
        if(s==want)return;
        if(s.getBlock()==want.getBlock()||s.canBeReplaced()&&level.getBlockEntity(p)==null)level.setBlock(p,want,3);
    }
    static void remove(Level level,BlockPos anchor,Direction facing){
        if(level.isClientSide)return;
        var all=new ArrayList<BlockPos>(parts(anchor,facing));all.add(column(anchor,facing));
        for(var p:all){if(!level.hasChunkAt(p))continue;var s=level.getBlockState(p);if((s.getBlock() instanceof Part||s.getBlock() instanceof Column)&&anchor.equals(anchorOf(p,s)))level.setBlock(p,Blocks.AIR.defaultBlockState(),3);}
    }
    /** The anchor of a beam part or a column. */
    static BlockPos anchorOf(BlockPos pos,BlockState s){
        var back=s.getValue(FACING).getCounterClockWise();
        return s.getBlock() instanceof Column?pos.relative(back,Frame.COLUMN[0]):pos.relative(back,s.getValue(SIDE)).below();
    }
    /** The gate anchor at or behind pos (a part, the column or the anchor itself), or null. */
    static BlockPos anchor(BlockGetter level,BlockPos pos){
        var s=level.getBlockState(pos);
        if(s.getBlock() instanceof GateBlock)return pos;
        if(s.getBlock() instanceof Part||s.getBlock() instanceof Column){var a=anchorOf(pos,s);return level.getBlockState(a).getBlock() instanceof GateBlock?a:null;}
        return null;
    }
    /** Sets the light of a gate: the anchor's lamps and the column's. */
    static void light(Level level,BlockPos anchor,BlockState state,Lamp lamp){
        if(state.getValue(SIGNAL)!=lamp)level.setBlock(anchor,state.setValue(SIGNAL,lamp),2|16);
        var at=column(anchor,state.getValue(FACING));var c=level.getBlockState(at);
        if(c.getBlock() instanceof Column&&anchor.equals(anchorOf(at,c))&&c.getValue(SIGNAL)!=lamp)level.setBlock(at,c.setValue(SIGNAL,lamp),2|16);
    }

    // The anchor: the right column
    static final class GateBlock extends Block implements EntityBlock {
        final Kind kind;
        GateBlock(Kind kind){
            super(Properties.of().strength(-1f,3600000f).noOcclusion().lightLevel(s->4).sound(SoundType.METAL).isSuffocating((s,l,p)->false).isViewBlocking((s,l,p)->false));
            this.kind=kind;registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(SIGNAL,Lamp.RED));
        }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING,SIGNAL);}
        @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return shape(kind,s.getValue(FACING),0,0);}
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
            return l.isClientSide||type!=EternalDefense.GATE_ENTITY.get()?null:(level,pos,state,be)->{if(level.getGameTime()%20==Math.floorMod(pos.asLong(),20L))Routes.light((ServerLevel)level,pos,state,(GateEntity)be);};
        }
    }

    // The left column: draws the left half and its lamps, clicks go to the anchor
    static final class Column extends Block {
        final Kind kind;
        Column(Kind kind){
            super(Properties.of().strength(-1f,3600000f).noOcclusion().lightLevel(s->4).sound(SoundType.METAL).isSuffocating((s,l,p)->false).isViewBlocking((s,l,p)->false));
            this.kind=kind;registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(SIGNAL,Lamp.RED));
        }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING,SIGNAL);}
        @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return shape(kind,s.getValue(FACING),Frame.COLUMN[0],Frame.COLUMN[1]);}
        @Override public RenderShape getRenderShape(BlockState s){return RenderShape.MODEL;}
        @Override public PushReaction getPistonPushReaction(BlockState s){return PushReaction.BLOCK;}
        @Override public boolean canEntityDestroy(BlockState s,BlockGetter l,BlockPos pos,Entity e){return false;}
        @Override public InteractionResult use(BlockState s,Level l,BlockPos pos,Player p,InteractionHand hand,BlockHitResult hit){return Part.forward(s,l,pos,p,hand,hit);}
        @Override public void playerWillDestroy(Level l,BlockPos pos,BlockState s,Player p){Part.takeDown(l,pos,s,p);super.playerWillDestroy(l,pos,s,p);}
        @Override public void neighborChanged(BlockState s,Level l,BlockPos pos,Block block,BlockPos from,boolean moving){if(!l.isClientSide)l.scheduleTick(pos,this,1);}
        @Override public void tick(BlockState s,ServerLevel l,BlockPos pos,net.minecraft.util.RandomSource random){Part.orphan(s,l,pos,this);}
        @Override public void onRemove(BlockState s,Level l,BlockPos pos,BlockState next,boolean moving){Part.gone(s,l,pos,next,this);super.onRemove(s,l,pos,next,moving);}
        @Override public ItemStack getCloneItemStack(BlockGetter l,BlockPos pos,BlockState s){return new ItemStack(kind==Kind.DELIVERY?EternalDefense.DELIVERY_GATE_ITEM.get():EternalDefense.RECEIVING_GATE_ITEM.get());}
    }

    // The beam: four invisible parts
    static final class Part extends Block {
        Part(){super(Properties.of().strength(-1f,3600000f).noOcclusion().dynamicShape().sound(SoundType.METAL));registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(SIDE,0));}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING,SIDE);}
        @Override public RenderShape getRenderShape(BlockState s){return RenderShape.INVISIBLE;}
        @Override public PushReaction getPistonPushReaction(BlockState s){return PushReaction.BLOCK;}
        @Override public boolean canEntityDestroy(BlockState s,BlockGetter l,BlockPos pos,Entity e){return false;}
        @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos pos,CollisionContext c){
            if(!(l.getBlockState(anchorOf(pos,s)).getBlock() instanceof GateBlock gate))return Block.box(0,0,0,16,16,16);
            return shape(gate.kind,s.getValue(FACING),s.getValue(SIDE),1);
        }
        @Override public VoxelShape getCollisionShape(BlockState s,BlockGetter l,BlockPos pos,CollisionContext c){
            if(!(l.getBlockState(anchorOf(pos,s)).getBlock() instanceof GateBlock gate))return Block.box(0,0,0,16,16,16);
            return collision(gate.kind,s.getValue(FACING),s.getValue(SIDE),1);
        }
        @Override public InteractionResult use(BlockState s,Level l,BlockPos pos,Player p,InteractionHand hand,BlockHitResult hit){return forward(s,l,pos,p,hand,hit);}
        @Override public void playerWillDestroy(Level l,BlockPos pos,BlockState s,Player p){takeDown(l,pos,s,p);super.playerWillDestroy(l,pos,s,p);}
        @Override public void neighborChanged(BlockState s,Level l,BlockPos pos,Block block,BlockPos from,boolean moving){if(!l.isClientSide)l.scheduleTick(pos,this,1);}
        @Override public void tick(BlockState s,ServerLevel l,BlockPos pos,net.minecraft.util.RandomSource random){orphan(s,l,pos,this);}
        @Override public void onRemove(BlockState s,Level l,BlockPos pos,BlockState next,boolean moving){gone(s,l,pos,next,this);super.onRemove(s,l,pos,next,moving);}
        @Override public ItemStack getCloneItemStack(BlockGetter l,BlockPos pos,BlockState s){
            return l.getBlockState(anchorOf(pos,s)).getBlock() instanceof GateBlock gate?new ItemStack(gate.asItem()):ItemStack.EMPTY;
        }
        /** A click on a part or the column is a click on the anchor. */
        static InteractionResult forward(BlockState s,Level l,BlockPos pos,Player p,InteractionHand hand,BlockHitResult hit){
            var anchor=anchorOf(pos,s);var owner=l.getBlockState(anchor);
            if(!(owner.getBlock() instanceof GateBlock))return InteractionResult.PASS;
            return owner.use(l,p,hand,new BlockHitResult(hit.getLocation(),hit.getDirection(),anchor,hit.isInside()));
        }
        /** A creative player breaking a part or the column takes the whole gate down. */
        static void takeDown(Level l,BlockPos pos,BlockState s,Player p){var anchor=anchorOf(pos,s);if(!l.isClientSide&&l.getBlockState(anchor).getBlock() instanceof GateBlock)l.destroyBlock(anchor,false,p);}
        /** A part or column whose anchor is gone goes too. */
        static void orphan(BlockState s,ServerLevel l,BlockPos pos,Block self){
            var anchor=anchorOf(pos,s);if(!l.hasChunkAt(anchor)){l.scheduleTick(pos,self,20);return;}
            if(!(l.getBlockState(anchor).getBlock() instanceof GateBlock))l.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
        }
        /** A part or column taken out by a command: the anchor puts it back. */
        static void gone(BlockState s,Level l,BlockPos pos,BlockState next,Block self){
            if(!l.isClientSide&&!next.is(self)){var anchor=anchorOf(pos,s);if(l.getBlockState(anchor).getBlock() instanceof GateBlock)l.scheduleTick(anchor,l.getBlockState(anchor).getBlock(),1);}
        }
    }

    // The anchor's block entity: which FOB, and its name for the plates
    static final class GateEntity extends BlockEntity {
        long fobId;String name="";
        GateEntity(BlockPos pos,BlockState s){super(EternalDefense.GATE_ENTITY.get(),pos,s);}
        @Override public void load(CompoundTag t){super.load(t);fobId=t.getLong("Fob");name=t.getString("Name");}
        @Override protected void saveAdditional(CompoundTag t){super.saveAdditional(t);t.putLong("Fob",fobId);}
        /** The name travels to the client in the update tag: no packet of its own. */
        @Override public CompoundTag getUpdateTag(){var t=new CompoundTag();t.putLong("Fob",fobId);t.putString("Name",level instanceof ServerLevel l?Routes.nameFor(l,fobId):name);return t;}
        @Override public Packet<ClientGamePacketListener> getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
        void link(long id){fobId=id;setChanged();if(level!=null)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}
        @Override public AABB getRenderBoundingBox(){return new AABB(worldPosition).inflate(2);}
    }

    // The items
    /** A gate is used on a straight rail and stands across it and the cell beside it. */
    static class GateItem extends BlockItem {
        final Kind kind;
        /** The spot worked out by place for getPlacementState, on the same thread. */
        private static final ThreadLocal<Spot> PLACING=new ThreadLocal<>();
        GateItem(Block block,Kind kind){super(block,new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));this.kind=kind;}
        @Override public InteractionResult place(BlockPlaceContext c){
            var level=c.getLevel();var spot=placingSpot(c);
            // The client does not guess: the anchor stands beside the clicked cell
            if(!(level instanceof ServerLevel server))return spot!=null&&available(level,spot.anchor(),spot.facing())?InteractionResult.SUCCESS:InteractionResult.FAIL;
            var player=c.getPlayer() instanceof ServerPlayer p?p:null;
            String problem=Routes.placeProblem(server,spot,kind,c.getItemInHand());
            if(problem!=null){if(player!=null)Routes.say(player,"gate.refused."+problem,RouteRules.MIN_SEPARATION);return InteractionResult.FAIL;}
            PLACING.set(spot);
            InteractionResult result;
            try{result=super.place(BlockPlaceContext.at(c,spot.anchor(),Direction.UP));}finally{PLACING.remove();}
            if(result.consumesAction()&&player!=null)player.playNotifySound(net.minecraft.sounds.SoundEvents.METAL_PLACE,net.minecraft.sounds.SoundSource.BLOCKS,1f,.8f);
            return result;
        }
        @Override protected BlockState getPlacementState(BlockPlaceContext c){
            var spot=PLACING.get();
            if(spot==null||!spot.anchor().equals(c.getClickedPos()))return null;
            var s=getBlock().defaultBlockState().setValue(FACING,spot.facing());
            return canPlace(c,s)?s:null;
        }
        @Override public void appendHoverText(ItemStack stack,Level level,List<Component> lines,TooltipFlag flag){
            lines.add(Component.translatable("tooltip.eternal_defense."+kind.id).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("tooltip.eternal_defense.gate.lanes").withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("tooltip.eternal_defense.gate.ride").withStyle(ChatFormatting.DARK_AQUA));
            var tag=stack.getTag();if(tag!=null&&tag.contains("FobName"))lines.add(Component.translatable("tooltip.eternal_defense.gate.linked",tag.getString("FobName")).withStyle(ChatFormatting.GOLD));
        }
    }

    // The gate screen's menu: the Receiving Gate holds its FOB's tribute storage
    /** A Receiving Gate's menu has the linked FOB's storage */
    static final class GateMenu extends AbstractContainerMenu {
        /** Where the slots sit in the screen (its top left corner) */
        static final int STORE_X=12,STORE_Y=40,INV_Y=STORE_Y+6*18+12,HOTBAR_Y=INV_Y+58,STORE_BOTTOM=HOTBAR_Y+18+4;
        final BlockPos pos;final CompoundTag data;final boolean storage;String message="";
        /** The client's screen hides the slots (not drawn, not clickable) while a narrow window shows another tab */
        boolean slotsShown=true;
        GateMenu(int id,Inventory inv,BlockPos pos,CompoundTag data,boolean storage,net.minecraft.world.Container depot){
            super(EternalDefense.GATE_MENU.get(),id);this.pos=pos;this.data=data;this.storage=storage;
            if(!storage)return;
            var box=depot!=null?depot:new net.minecraft.world.SimpleContainer(FobData.DEPOT_SLOTS);
            for(int row=0;row<6;row++)for(int col=0;col<9;col++)addSlot(new TakeOnly(this,box,row*9+col,STORE_X+col*18,STORE_Y+row*18));
            for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Shown(this,inv,9+row*9+col,STORE_X+col*18,INV_Y+row*18));
            for(int col=0;col<9;col++)addSlot(new Shown(this,inv,col,STORE_X+col*18,HOTBAR_Y));
        }
        static GateMenu client(int id,Inventory inv,FriendlyByteBuf buf){
            var pos=buf.readBlockPos();var n=buf.readNbt();boolean storage=buf.readBoolean();
            return new GateMenu(id,inv,pos,n==null?new CompoundTag():n,storage,null);
        }
        @Override public boolean clickMenuButton(Player p,int id){if(p instanceof ServerPlayer sp)Routes.button(sp,pos,id);return true;}
        /** Shift-click takes a stack out of the storage; nothing goes in. */
        @Override public ItemStack quickMoveStack(Player p,int index){
            if(!storage||index<0||index>=FobData.DEPOT_SLOTS)return ItemStack.EMPTY;
            var slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;
            var stack=slot.getItem();var copy=stack.copy();
            if(!moveItemStackTo(stack,FobData.DEPOT_SLOTS,slots.size(),true))return ItemStack.EMPTY;
            if(stack.isEmpty())slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();
            return copy;
        }
        @Override public boolean stillValid(Player p){return p.level().getBlockState(pos).getBlock() instanceof GateBlock&&p.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<=100;}
    }
    /** A slot of the gate's menu: hidden while the screen shows another tab. */
    static class Shown extends net.minecraft.world.inventory.Slot {
        final GateMenu menu;
        Shown(GateMenu menu,net.minecraft.world.Container c,int index,int x,int y){super(c,index,x,y);this.menu=menu;}
        @Override public boolean isActive(){return menu.slotsShown;}
    }
    /** A storage slot: take only. */
    static final class TakeOnly extends Shown {
        TakeOnly(GateMenu menu,net.minecraft.world.Container c,int index,int x,int y){super(menu,c,index,x,y);}
        @Override public boolean mayPlace(ItemStack stack){return false;}
    }
}
