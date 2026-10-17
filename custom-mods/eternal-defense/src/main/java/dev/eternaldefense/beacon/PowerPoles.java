package dev.eternaldefense.beacon;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.ArrayList;
import java.util.List;

/** Power poles: on a dynamo they feed a network; wired to each other they carry it further */
final class PowerPoles {
    private PowerPoles(){}
    static final int MAX_LINKS=8;
    static final VoxelShape POLE=Shapes.box(.3,0,.3,.7,2,.7);

    static final class PoleBlock extends Block implements EntityBlock,DefenseStructures.Footprint {
        PoleBlock(){super(Properties.of().strength(3,6).noOcclusion().sound(SoundType.METAL));registerDefaultState(stateDefinition.any().setValue(DefenseStructures.FACING,Direction.NORTH));}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(DefenseStructures.FACING);}
        @Override public BlockState getStateForPlacement(BlockPlaceContext c){return defaultBlockState().setValue(DefenseStructures.FACING,c.getHorizontalDirection().getOpposite());}
        @Override public List<BlockPos> extra(BlockPos root,BlockState state){return List.of(root.above());}
        @Override public VoxelShape whole(BlockState state){return POLE;}
        @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return DefenseStructures.cell(s,0,0,0);}
        @Override public PushReaction getPistonPushReaction(BlockState s){return PushReaction.BLOCK;}
        @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new PoleEntity(pos,state);}
        @Override public void onPlace(BlockState s,Level l,BlockPos pos,BlockState old,boolean moving){super.onPlace(s,l,pos,old,moving);if(l instanceof ServerLevel)DefenseStructures.install(l,pos,s);}
        @Override public void tick(BlockState s,ServerLevel l,BlockPos pos,net.minecraft.util.RandomSource random){DefenseStructures.install(l,pos,s);}
        @Override public void onRemove(BlockState s,Level l,BlockPos pos,BlockState next,boolean moving){
            if(!s.is(next.getBlock())){if(l.getBlockEntity(pos) instanceof PoleEntity be)refund(l,pos,be.links.size());DefenseStructures.remove(l,pos);}
            super.onRemove(s,l,pos,next,moving);
        }
        @Override public InteractionResult use(BlockState s,Level l,BlockPos pos,Player p,InteractionHand hand,BlockHitResult hit){
            if(p.getItemInHand(hand).getItem() instanceof WireItem)return InteractionResult.PASS;
            if(p.isShiftKeyDown()&&p.getItemInHand(hand).isEmpty()){
                if(p instanceof ServerPlayer sp&&l.getBlockEntity(pos) instanceof PoleEntity be){be.showReach=!be.showReach;be.sync();say(sp,be.showReach?"reach.shown":"reach.hidden");}
                return InteractionResult.sidedSuccess(l.isClientSide);
            }
            if(p instanceof ServerPlayer sp)sp.displayClientMessage(status(sp.serverLevel(),pos),true);
            return InteractionResult.sidedSuccess(l.isClientSide);
        }
    }

    /** A pole's status line: its network, from the last time it was worked out */
    static Component status(ServerLevel l,BlockPos pos){
        var s=PowerGrid.summary(l,pos);
        if(s==null||s.dynamos()==0)return Component.translatable("gui.eternal_defense.power.no_dynamo",PowerKit.AURA).withStyle(ChatFormatting.GOLD);
        if(s.turning()==0)return Component.translatable("gui.eternal_defense.power.still",PowerKit.MIN_RPM).withStyle(ChatFormatting.GOLD);
        if(s.overstressed())return Component.translatable("gui.eternal_defense.power.overstressed",su(s.su()),fmt(s.demand())).withStyle(ChatFormatting.RED);
        return Component.translatable("gui.eternal_defense.power.running",s.turning(),fmt(s.rpm()),su(s.su()),fmt(s.demand()),s.turrets(),s.boxes()).withStyle(ChatFormatting.AQUA);
    }
    /** Stress units as Create writes them: whole numbers with thousands separated */
    static String su(double v){return String.format(java.util.Locale.ROOT,"%,d",Math.round(v));}
    static String fmt(float f){return Math.abs(f-Math.rint(f))<.05f?Integer.toString(Math.round(f)):String.format(java.util.Locale.ROOT,"%.1f",f);}
    /** Wire comes back when what holds it is taken down */
    static void refund(Level l,BlockPos pos,int wires){if(wires>0&&!l.isClientSide)Block.popResource(l,pos,new ItemStack(EternalDefense.ELECTRIC_WIRE.get(),wires));}

    static final class PoleEntity extends BlockEntity {
        final List<BlockPos> links=new ArrayList<>();
        boolean live,showReach;
        PoleEntity(BlockPos pos,BlockState state){super(EternalDefense.POLE_ENTITY.get(),pos,state);}
        @Override public void onLoad(){super.onLoad();if(level instanceof ServerLevel l)PowerGrid.joinPole(l,worldPosition);else if(level!=null&&level.isClientSide)SEEN.add(worldPosition.immutable());}
        @Override public void setRemoved(){super.setRemoved();if(level!=null&&level.isClientSide)SEEN.remove(worldPosition);}
        void live(boolean on){
            // A wire whose far end is gone comes back as an item
            if(level instanceof ServerLevel l){int lost=0;for(var it=links.iterator();it.hasNext();){var t=it.next();if(l.isLoaded(t)&&!endpoint(l.getBlockState(t))){it.remove();lost++;}}if(lost>0){refund(l,worldPosition,lost);sync();}}
            if(live!=on){live=on;sync();}
        }
        boolean link(BlockPos to){if(links.contains(to)||links.size()>=MAX_LINKS)return false;links.add(to.immutable());sync();return true;}
        void sync(){setChanged();if(level!=null)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}
        @Override protected void saveAdditional(CompoundTag tag){super.saveAdditional(tag);tag.put("Links",save(links));tag.putBoolean("Live",live);tag.putBoolean("ShowReach",showReach);}
        @Override public void load(CompoundTag tag){super.load(tag);read(tag.getList("Links",Tag.TAG_COMPOUND),links);live=tag.getBoolean("Live");showReach=tag.getBoolean("ShowReach");}
        @Override public CompoundTag getUpdateTag(){var t=new CompoundTag();saveAdditional(t);return t;}
        @Override public Packet<ClientGamePacketListener> getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
        @Override public AABB getRenderBoundingBox(){return reach(worldPosition,links);}
    }
    /** Poles the client has loaded, for drawing their reach */
    static final java.util.Set<BlockPos> SEEN=java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** Pure: the edges of a pole's reach on one layer, as (x, z, along x) from the pole's column corner */
    static final List<int[]> REACH_EDGES=reachEdges();
    private static List<int[]> reachEdges(){
        var out=new ArrayList<int[]>();int r=PowerKit.AURA;
        for(int dx=-r;dx<=r;dx++)for(int dz=-r;dz<=r;dz++){
            if(!PowerKit.inAura(dx,0,dz))continue;
            if(!PowerKit.inAura(dx,0,dz-1))out.add(new int[]{dx,dz,1});
            if(!PowerKit.inAura(dx,0,dz+1))out.add(new int[]{dx,dz+1,1});
            if(!PowerKit.inAura(dx-1,0,dz))out.add(new int[]{dx,dz,0});
            if(!PowerKit.inAura(dx+1,0,dz))out.add(new int[]{dx+1,dz,0});
        }
        return List.copyOf(out);
    }
    static ListTag save(List<BlockPos> list){var out=new ListTag();for(var p:list)out.add(NbtUtils.writeBlockPos(p));return out;}
    static void read(ListTag tags,List<BlockPos> into){into.clear();for(int i=0;i<tags.size();i++)into.add(NbtUtils.readBlockPos(tags.getCompound(i)));}
    /** The box a block and its wire spans are drawn in */
    static AABB reach(BlockPos pos,List<BlockPos> links){var box=new AABB(pos).expandTowards(0,2,0);for(var t:links)box=box.minmax(new AABB(t).expandTowards(0,2,0));return box;}

    // The wire: joins a pole to a pole or a shock box, and a shock box to barbed wire
    enum End{POLE,BOX,WIRE}
    static End end(BlockState s){return s.getBlock() instanceof PoleBlock?End.POLE:s.getBlock() instanceof ShockBoxes.BoxBlock?End.BOX:s.getBlock() instanceof DefenseWalls.BarbedWire?End.WIRE:null;}
    static boolean endpoint(BlockState s){return end(s)!=null;}
    /** Pure: which end owns a span from a to b (0 the first, 1 the second), or -1 when the two cannot be wired */
    static int owner(End a,End b){
        if(a==End.POLE&&(b==End.POLE||b==End.BOX))return 0;
        if(a==End.BOX&&b==End.POLE)return 1;
        if(a==End.BOX&&b==End.WIRE)return 0;
        if(a==End.WIRE&&b==End.BOX)return 1;
        return -1;
    }
    static final class WireItem extends Item {
        WireItem(){super(new Properties());}
        @Override public void appendHoverText(ItemStack stack,Level level,List<Component> lines,TooltipFlag flag){
            lines.add(Component.translatable("tooltip.eternal_defense.electric_wire",PowerKit.WIRE_MAX).withStyle(ChatFormatting.GRAY));
            var tag=stack.getTag();if(tag!=null&&tag.contains("From"))lines.add(Component.translatable("tooltip.eternal_defense.electric_wire_from").withStyle(ChatFormatting.AQUA));
        }
        @Override public boolean isFoil(ItemStack stack){return stack.hasTag()&&stack.getTag().contains("From");}
        @Override public InteractionResult useOn(UseOnContext c){
            var l=c.getLevel();var at=DefenseStructures.anchor(l,c.getClickedPos());var end=end(l.getBlockState(at));
            if(end==null)return InteractionResult.PASS;
            if(!(l instanceof ServerLevel server)||!(c.getPlayer() instanceof ServerPlayer p))return InteractionResult.SUCCESS;
            var stack=c.getItemInHand();
            if(p.isShiftKeyDown()&&end!=End.WIRE){int n=unlink(server,at);say(p,n>0?"wire.cut":"wire.none",n);stack.removeTagKey("From");return InteractionResult.CONSUME;}
            var tag=stack.getTag();
            if(tag==null||!tag.contains("From")){var t=stack.getOrCreateTag();t.put("From",NbtUtils.writeBlockPos(at));say(p,"wire.start");return InteractionResult.CONSUME;}
            var from=NbtUtils.readBlockPos(tag.getCompound("From"));stack.removeTagKey("From");
            var fromEnd=server.isLoaded(from)?end(server.getBlockState(from)):null;
            if(fromEnd==null){say(p,"wire.gone");return InteractionResult.CONSUME;}
            if(!PowerKit.spanOk(from,at)){say(p,from.equals(at)?"wire.same":"wire.far",PowerKit.WIRE_MAX);return InteractionResult.CONSUME;}
            int owner=owner(fromEnd,end);
            if(owner<0){say(p,"wire.pair");return InteractionResult.CONSUME;}
            BlockPos ownerPos=owner==0?from:at,target=owner==0?at:from;
            boolean linked=server.getBlockEntity(ownerPos) instanceof PoleEntity pole?!twice(server,ownerPos,target)&&pole.link(target)
                :server.getBlockEntity(ownerPos) instanceof ShockBoxes.BoxEntity box&&box.link(target);
            if(!linked){say(p,"wire.full",MAX_LINKS);return InteractionResult.CONSUME;}
            if(!p.isCreative())stack.shrink(1);
            server.playSound(null,at,SoundEvents.CHAIN_PLACE,SoundSource.BLOCKS,1,1.2f);
            say(p,"wire.done");return InteractionResult.CONSUME;
        }
    }
    /** Two poles are wired once, whichever way */
    static boolean twice(ServerLevel l,BlockPos a,BlockPos b){return l.getBlockEntity(b) instanceof PoleEntity other&&other.links.contains(a);}
    /** Takes off every wire a pole or shock box holds; the wire comes back */
    static int unlink(ServerLevel l,BlockPos at){
        int n=0;
        if(l.getBlockEntity(at) instanceof PoleEntity pole){n=pole.links.size();pole.links.clear();pole.sync();}
        else if(l.getBlockEntity(at) instanceof ShockBoxes.BoxEntity box){n=box.links.size();box.links.clear();box.sync();}
        refund(l,at,n);return n;
    }
    static void say(ServerPlayer p,String key,Object... args){p.displayClientMessage(Component.translatable("gui.eternal_defense.power."+key,args),true);}
}
