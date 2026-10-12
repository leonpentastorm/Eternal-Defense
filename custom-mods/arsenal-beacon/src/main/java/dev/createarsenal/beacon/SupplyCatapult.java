package dev.createarsenal.beacon;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;
import java.util.ArrayList;
import java.util.List;

/**
 * The Supply Catapult (0.0.33, owner): the owner's field-built onager, 3 x 3 cells like the Support Cannon, placed only in a FOB's village (one
 * per FOB). It works once that FOB's research is at its top level: at the Command Table the FOB's specialty seller sells every item of its
 * specialty for Ardent Energy; the catapult turns toward the Defense Beacon, throws the box, and the box parachutes down beside the beacon (the
 * Supply Flare's parcel). The rules without a world are in {@link CatapultRules}.
 */
final class SupplyCatapult {
    private SupplyCatapult(){}

    static final class CatapultBlock extends Block implements EntityBlock {
        CatapultBlock(){super(Properties.of().strength(3,6).noOcclusion().dynamicShape().sound(SoundType.WOOD));}
        @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return ArsenalStructures.cell(s,0,0,0);}
        @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState s){return new CatapultEntity(pos,s);}
        /** A plain Block ignores block events: hand them to the block entity (the server only sends an event to the clients when this accepts it). */
        @Override public boolean triggerEvent(BlockState s,Level l,BlockPos pos,int id,int param){super.triggerEvent(s,l,pos,id,param);var be=l.getBlockEntity(pos);return be!=null&&be.triggerEvent(id,param);}
        @Override public void onPlace(BlockState s,Level l,BlockPos pos,BlockState old,boolean moving){
            super.onPlace(s,l,pos,old,moving);
            if(l instanceof ServerLevel)ArsenalStructures.install(l,pos,s);
        }
        @Override public void setPlacedBy(Level l,BlockPos pos,BlockState s,LivingEntity who,ItemStack stack){
            super.setPlacedBy(l,pos,s,who,stack);
            if(l instanceof ServerLevel server){var f=Fobs.zoneAt(server,pos);if(f!=null&&(f.catapult==null||!present(server,f))){link(server,f,pos);if(who instanceof ServerPlayer sp)say(sp,"catapult.linked",f.name);}}
        }
        @Override public void onRemove(BlockState s,Level l,BlockPos pos,BlockState next,boolean moving){
            if(!s.is(next.getBlock())){
                ArsenalStructures.remove(l,pos);
                if(l instanceof ServerLevel server){var d=FobData.get(server);var f=d.byCatapult(pos);if(f!=null){f.catapult=null;d.setDirty();}}
            }
            super.onRemove(s,l,pos,next,moving);
        }
        @Override public void tick(BlockState s,ServerLevel l,BlockPos pos,RandomSource random){ArsenalStructures.install(l,pos,s);}
        @Override public InteractionResult use(BlockState s,Level l,BlockPos pos,Player p,InteractionHand hand,BlockHitResult hit){
            if(!l.isClientSide&&p instanceof ServerPlayer sp)status(sp,(ServerLevel)l,pos);
            return InteractionResult.sidedSuccess(l.isClientSide);
        }
        @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l,BlockState s,BlockEntityType<T> type){
            return type==ArsenalBeacon.CATAPULT_ENTITY.get()?(lv,pos,st,be)->((CatapultEntity)be).tick(lv):null;
        }
    }

    /** Placed only in a FOB's village, one per FOB, with room for its 3 x 3 x 2 body. */
    static final class CatapultItem extends BlockItem {
        CatapultItem(Block block){super(block,new Item.Properties());}
        @Override public InteractionResult place(BlockPlaceContext c){
            if(c.getLevel() instanceof ServerLevel l){
                String problem=placeProblem(l,c.getClickedPos());
                if(problem!=null){if(c.getPlayer()!=null)c.getPlayer().displayClientMessage(Component.translatable("gui.arsenal_beacon.catapult.refused."+problem).withStyle(ChatFormatting.RED),true);return InteractionResult.FAIL;}
            }
            return super.place(c);
        }
        @Override public void appendHoverText(ItemStack stack,Level level,List<Component> lines,TooltipFlag flag){
            lines.add(Component.translatable("tooltip.arsenal_beacon.catapult").withStyle(ChatFormatting.GRAY));
        }
    }
    /** Why a catapult cannot stand at {@code pos}: a key under {@code catapult.refused.}, or null. */
    static String placeProblem(ServerLevel l,BlockPos pos){
        if(l.dimension()!=Level.OVERWORLD)return "overworld";
        var f=Fobs.zoneAt(l,pos);if(f==null)return "not_fob";
        if(f.catapult!=null&&!f.catapult.equals(pos)&&present(l,f))return "has_catapult";
        if(!ArsenalStructures.available(l,pos,ArsenalBeacon.SUPPLY_CATAPULT.get().defaultBlockState()))return "no_room";
        return null;
    }
    static void link(ServerLevel l,FobData.Fob f,BlockPos pos){f.catapult=pos.immutable();FobData.get(l).setDirty();}
    /** The FOB's catapult still stands (a catapult whose land is not loaded is taken as standing). */
    static boolean present(ServerLevel l,FobData.Fob f){
        if(f.catapult==null)return false;
        if(!l.hasChunkAt(f.catapult))return true;
        return l.getBlockState(f.catapult).is(ArsenalBeacon.SUPPLY_CATAPULT.get());
    }
    /** A right-click on the catapult says how it stands: whose it is, whether it works, its queue. */
    static void status(ServerPlayer p,ServerLevel l,BlockPos pos){
        var f=FobData.get(l).byCatapult(pos);
        if(f==null){say(p,"catapult.status.unlinked");return;}
        var base=CampaignData.get(l.getServer().overworld());
        String closed=CatapultRules.closed(FobRules.working(f.state),f.level(),true,base.installed());
        if(closed!=null)say(p,"catapult.status.closed."+closed,f.name,f.level(),CatapultRules.SELLER_LEVEL);
        else say(p,"catapult.status.ready",f.name,f.shipments.size(),CatapultRules.MAX_QUEUE);
    }

    static final class CatapultEntity extends BlockEntity {
        /** Degrees, unwrapped, as the support cannon's turret: {@code target} is synced, the motor turns {@code yaw} to it on every side. */
        float target,yaw,prevYaw,speed;
        /** Ticks into the current throw (-1: none); the box shows in the cup while {@code loaded} (synced). */
        int throwAge=-1,settled;boolean loaded;
        /** Throws since the chunk loaded (not saved), for tests. */
        int throwsSeen;
        private final float[] motor=new float[2];
        CatapultEntity(BlockPos pos,BlockState s){super(ArsenalBeacon.CATAPULT_ENTITY.get(),pos,s);}
        /** The arm's pitch now (degrees, 0 cocked), between ticks. */
        float armAngle(float partial){return throwAge<0?0:CatapultRules.armAngle(throwAge+partial);}
        boolean payloadShown(){return loaded&&(throwAge<0||throwAge<CatapultRules.RELEASE_TICK);}
        boolean ready(){return Math.abs(target-yaw)<0.6f&&Math.abs(speed)<0.2f;}
        void aim(float goal){
            float diff=goal-yaw;diff=((diff%360)+540)%360-180;float next=yaw+diff;
            if(Math.abs(next-target)>0.3f){target=next;settled=0;sync();}
        }
        void sync(){setChanged();if(level instanceof ServerLevel s)s.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}
        void tick(Level level){
            motor[0]=yaw;motor[1]=speed;prevYaw=yaw;SupportCannon.spin(motor,target,.8f);yaw=motor[0];speed=motor[1];
            if(throwAge>=0&&++throwAge>=CatapultRules.CYCLE)throwAge=-1;
            if(level.isClientSide)return;
            var server=(ServerLevel)level;
            if(throwAge==CatapultRules.RELEASE_TICK){release(server);return;}
            if(throwAge>=0)return;
            var d=FobData.get(server);var f=d.byCatapult(worldPosition);
            var ship=f==null?null:waiting(f);
            if(ship==null){if(loaded){loaded=false;sync();}return;}
            var base=CampaignData.get(server.getServer().overworld());if(!base.installed())return;
            if(!loaded){loaded=true;sync();server.playSound(null,worldPosition,SoundEvents.CROSSBOW_LOADING_START,SoundSource.BLOCKS,1.5f,.6f);}
            aim(CatapultRules.yawTo(worldPosition.getX()+.5,worldPosition.getZ()+.5,base.beacon.getX()+.5,base.beacon.getZ()+.5));
            settled=ready()?settled+1:0;
            if(settled>=CatapultRules.SETTLE_TICKS){
                settled=0;throwAge=0;throwsSeen++;ship.putString("stage","throwing");d.setDirty();
                server.blockEvent(worldPosition,getBlockState().getBlock(),1,0);
                server.playSound(null,worldPosition,SoundEvents.CROSSBOW_SHOOT,SoundSource.BLOCKS,2.5f,.5f);
                server.playSound(null,worldPosition,SoundEvents.PLAYER_ATTACK_SWEEP,SoundSource.BLOCKS,1.5f,.6f);
            }
        }
        /** The box leaves the cup: a thrown box to see, and the shipment is in the air (it lands beside the beacon, {@link #second}). */
        private void release(ServerLevel server){
            var d=FobData.get(server);var f=d.byCatapult(worldPosition);loaded=false;sync();
            var at=CatapultRules.release(yaw,1.6);
            var box=new ThrownSupply(ArsenalBeacon.THROWN_SUPPLY.get(),server);
            box.setPos(worldPosition.getX()+at[0],worldPosition.getY()+at[1],worldPosition.getZ()+at[2]);box.setDeltaMovement(at[3],at[4],at[5]);
            server.addFreshEntity(box);
            server.playSound(null,worldPosition,SoundEvents.WOODEN_TRAPDOOR_CLOSE,SoundSource.BLOCKS,2f,.5f);
            if(f!=null)for(var ship:f.shipments)if(ship.getString("stage").equals("throwing")){thrown(server,f,ship);d.setDirty();break;}
        }
        @Override public boolean triggerEvent(int id,int param){if(id==1){throwAge=0;return true;}return super.triggerEvent(id,param);}
        @Override protected void saveAdditional(CompoundTag n){super.saveAdditional(n);n.putFloat("Target",target);n.putFloat("Yaw",yaw);}
        @Override public void load(CompoundTag n){super.load(n);target=n.getFloat("Target");yaw=prevYaw=n.contains("Yaw")?n.getFloat("Yaw"):target;speed=0;}
        @Override public CompoundTag getUpdateTag(){var n=new CompoundTag();n.putFloat("Target",target);n.putBoolean("Loaded",loaded);return n;}
        @Override public void handleUpdateTag(CompoundTag n){target=n.getFloat("Target");loaded=n.getBoolean("Loaded");if(!synced){synced=true;yaw=prevYaw=target;}}
        @Override public void onDataPacket(net.minecraft.network.Connection net,ClientboundBlockEntityDataPacket pkt){var tag=pkt.getTag();if(tag!=null)handleUpdateTag(tag);}
        private boolean synced;
        @Override public Packet<ClientGamePacketListener> getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
        @Override public net.minecraft.world.phys.AABB getRenderBoundingBox(){return new net.minecraft.world.phys.AABB(worldPosition).inflate(3);}
    }
    /** The first shipment not yet thrown. */
    static CompoundTag waiting(FobData.Fob f){for(var ship:f.shipments)if(ship.getString("stage").equals("wait"))return ship;return null;}
    /** A shipment leaves the catapult: it flies home, the buyer is told when it lands. */
    static void thrown(ServerLevel l,FobData.Fob f,CompoundTag ship){
        var base=CampaignData.get(l.getServer().overworld());
        double distance=Math.hypot(f.pos.getX()-base.beacon.getX(),f.pos.getZ()-base.beacon.getZ());int flight=CatapultRules.flightTicks(distance);
        ship.putString("stage","flying");ship.putLong("due",l.getGameTime()+flight);
        var buyer=ship.hasUUID("buyer")?l.getServer().getPlayerList().getPlayer(ship.getUUID("buyer")):null;
        if(buyer!=null)buyer.sendSystemMessage(Component.translatable("gui.arsenal_beacon.catapult.thrown",f.name,Math.round(flight/20.0)).withStyle(ChatFormatting.GOLD));
    }

    // ---- once a second (from Fobs.tick): shipments no loaded catapult throws, and boxes landing -------------------------------------------------------
    static void second(ServerLevel l,FobData d){
        long now=l.getGameTime();
        for(var f:d.fobs.values()){
            if(f.shipments.isEmpty())continue;
            boolean seen=f.catapult!=null&&l.isPositionEntityTicking(f.catapult)&&l.getBlockState(f.catapult).is(ArsenalBeacon.SUPPLY_CATAPULT.get());
            for(var ship:List.copyOf(f.shipments)){
                String stage=ship.getString("stage");
                if(stage.equals("wait")&&!seen){
                    // nobody sees the catapult: it throws after a short while all the same
                    if(ship.getLong("due")==0){ship.putLong("due",now+CatapultRules.UNSEEN_THROW_TICKS);d.setDirty();}
                    else if(now>=ship.getLong("due")){thrown(l,f,ship);d.setDirty();}
                }else if(stage.equals("throwing")&&!seen){thrown(l,f,ship);d.setDirty();}   // the catapult's land unloaded mid-throw
                else if(stage.equals("flying")&&now>=ship.getLong("due")&&land(l,f,ship)){f.shipments.remove(ship);d.setDirty();}
            }
        }
    }
    /** The box comes down beside the Defense Beacon (the Supply Flare's parcel, its parachute when the sky is open). False while the base's land is not loaded. */
    static boolean land(ServerLevel l,FobData.Fob f,CompoundTag ship){
        var home=l.getServer().overworld();var base=CampaignData.get(home);
        if(!base.installed()||!home.hasChunkAt(base.beacon))return false;
        BlockPos spot=landing(home,base.beacon);
        ListTag items=ship.getList("items",Tag.TAG_COMPOUND);
        if(spot==null)SupportCalls.deliver(home,null,Vec3.atBottomCenterOf(base.beacon.above(2)),items,false,0);
        else{int clear=SupportCalls.clearAbove(home,spot);SupportCalls.deliver(home,null,Vec3.atBottomCenterOf(spot),items,clear>=SupportRules.PARCEL_MIN_DROP,clear);}
        var buyer=ship.hasUUID("buyer")?l.getServer().getPlayerList().getPlayer(ship.getUUID("buyer")):null;
        if(buyer!=null)buyer.sendSystemMessage(Component.translatable("gui.arsenal_beacon.catapult.landed",f.name,ship.getInt("count"),Component.translatable(ship.getString("name"))).withStyle(ChatFormatting.GOLD));
        log("{}: a box of {} {} landed at the beacon",f.name,ship.getInt("count"),ship.getString("item"));
        return true;
    }
    /** A free cell two to five blocks from the beacon, the one with the most open sky (a parcel needs room to come down). */
    static BlockPos landing(ServerLevel home,BlockPos beacon){
        BlockPos best=null;int bestClear=-1;
        for(int r=2;r<=5;r++)for(var dir:net.minecraft.core.Direction.Plane.HORIZONTAL)for(int dy:new int[]{0,1,-1,2,-2}){
            var cell=beacon.relative(dir,r).above(dy);
            if(!home.hasChunkAt(cell)||!SupportCalls.standable(home,cell))continue;
            int clear=SupportCalls.clearAbove(home,cell);if(clear>bestClear){best=cell;bestClear=clear;}
            if(clear>=SupportRules.PARCEL_DROP_HEIGHT)return cell;
        }
        return best;
    }

    // ---- the specialty seller (the Command Table's FOBs tab) -------------------------------------------------------------------------------------
    /** Button ids from here go to the seller: {@code SELL + ((fob index x 256 + item) x 2 + lots kind)}; they travel as VarInts. */
    static final int SELL=1<<20;
    static int sellButton(int fob,int item,int lotsKind){return SELL+((fob*256+item)*2+lotsKind);}
    /** {fob index, item, lots kind} of a seller button. */
    static int[] decode(int id){int code=id-SELL;return new int[]{code/2/256,(code/2)%256,code%2};}
    /** The FOBs in the order the table lists them by index. */
    static List<FobData.Fob> list(FobData d){return new ArrayList<>(d.fobs.values());}
    /** Why this FOB's seller is closed (a key suffix under {@code tactical.seller.closed.}), or null. */
    static String closed(ServerLevel l,FobData.Fob f){
        var base=CampaignData.get(l.getServer().overworld());
        return CatapultRules.closed(FobRules.working(f.state),f.level(),present(l,f),base.installed());
    }
    /** A purchase: Ardent Energy paid, the shipment queued at the FOB's catapult. Returns the table's message key. */
    static String buy(ServerPlayer p,ServerLevel l,int id){
        int[] sell=decode(id);int index=sell[0],item=sell[1],kind=sell[2];
        var d=FobData.get(l);var fobs=list(d);
        if(index<0||index>=fobs.size()||kind>=CatapultRules.LOTS.length)return "tactical.seller.gone";
        var f=fobs.get(index);
        String closed=closed(l,f);if(closed!=null)return "tactical.seller.refused."+closed;
        var options=RouteRules.options(RouteRules.Slot.SPECIALTY,f.specialty);if(item<0||item>=options.size())return "tactical.seller.gone";
        if(f.shipments.size()>=CatapultRules.MAX_QUEUE)return "tactical.seller.busy";
        var loot=options.get(item);var type=BuiltInRegistries.ITEM.get(new ResourceLocation(loot.id()));
        if(type==net.minecraft.world.item.Items.AIR)return "tactical.seller.gone";
        int lots=CatapultRules.LOTS[kind],count=CatapultRules.lotSize(loot)*lots,price=CatapultRules.price(lots);
        if(!ArdentEnergy.spend(p,price))return "tactical.seller.short";
        var items=new ListTag();int left=count,max=Math.max(1,new ItemStack(type).getMaxStackSize());
        while(left>0){int n=Math.min(max,left);items.add(new ItemStack(type,n).save(new CompoundTag()));left-=n;}
        var ship=new CompoundTag();ship.put("items",items);ship.putUUID("buyer",p.getUUID());ship.putString("buyerName",p.getGameProfile().getName());
        ship.putString("stage","wait");ship.putLong("due",0);ship.putString("item",loot.id());ship.putString("name",type.getDescriptionId());ship.putInt("count",count);
        f.shipments.add(ship);d.setDirty();
        log("{}: {} bought {} {} for {} Ardent Energy",f.name,p.getGameProfile().getName(),count,loot.id(),price);
        return "tactical.seller.bought";
    }

    static void say(ServerPlayer p,String key,Object... args){p.displayClientMessage(Component.translatable("gui.arsenal_beacon."+key,args),true);}
    private static void log(String text,Object... args){com.mojang.logging.LogUtils.getLogger().info("[catapult] "+text,args);}

    /** The box in the air after the throw: only to be seen (it climbs out of sight and is gone), never saved. */
    static final class ThrownSupply extends Entity {
        int life;
        ThrownSupply(EntityType<? extends ThrownSupply> type,Level level){super(type,level);noPhysics=true;}
        @Override protected void defineSynchedData(){}
        @Override public void tick(){
            super.tick();life++;
            var v=getDeltaMovement();setDeltaMovement(v.x*.99,v.y*.99-.02,v.z*.99);setPos(getX()+v.x,getY()+v.y,getZ()+v.z);
            if(!level().isClientSide&&life>=60){
                if(level() instanceof ServerLevel s)s.sendParticles(ParticleTypes.CLOUD,getX(),getY(),getZ(),8,.3,.3,.3,.02);
                discard();
            }
        }
        @Override protected void readAdditionalSaveData(CompoundTag n){}
        @Override protected void addAdditionalSaveData(CompoundTag n){}
        @Override public boolean shouldBeSaved(){return false;}
        @Override public Packet<ClientGamePacketListener> getAddEntityPacket(){return NetworkHooks.getEntitySpawningPacket(this);}
    }
}
