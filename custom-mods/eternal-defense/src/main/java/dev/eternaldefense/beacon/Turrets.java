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
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;
import java.util.*;

/** Defense turrets: machine gun, shotgun and flamethrower lines, three Marks each, run on power and their own ammunition */
final class Turrets {
    private Turrets(){}
    enum Line {
        MG("mg"),SHOTGUN("shotgun"),FLAME("flame");
        final String id;Line(String id){this.id=id;}
    }
    /** Range in blocks, ticks between shots, damage a hit (a pellet, a burst), pellets a shot, spread or cone in degrees, stress per RPM, seconds of fire, block hardness */
    record Stats(int range,int cooldown,float damage,int pellets,float spread,int stress,int burn,float hardness){}
    static Stats stats(Line line,int mk){
        return switch(line){
            case MG->switch(mk){case 1->new Stats(18,6,3,1,1.5f,8,0,6);case 2->new Stats(22,4,3.5f,1,1.5f,12,0,8);default->new Stats(26,3,4,1,2,20,0,10);};
            case SHOTGUN->switch(mk){case 1->new Stats(10,24,1.5f,6,12,8,0,6);case 2->new Stats(12,16,1.6f,7,11,12,0,8);default->new Stats(14,10,1.7f,8,12,20,0,10);};
            case FLAME->switch(mk){case 1->new Stats(7,5,1.5f,1,25,12,4,6);case 2->new Stats(8,5,2,1,25,16,5,8);default->new Stats(10,5,2.5f,1,35,24,6,10);};
        };
    }
    /** Rounds one ammunition item loads: a can of belted rounds, a box of shells, a fuel bottle's bursts */
    static int perItem(Line line){return switch(line){case MG->64;case SHOTGUN->12;case FLAME->40;};}
    static final int SLOTS=4;
    static Item ammo(Line line){return switch(line){case MG->EternalDefense.MG_AMMO.get();case SHOTGUN->EternalDefense.SHOTGUN_AMMO.get();case FLAME->EternalDefense.FLAME_FUEL.get();};}
    static ResourceKey<DamageType> damage(Line line){return DefenseWalls.key(switch(line){case MG->"turret_bullet";case SHOTGUN->"turret_pellet";case FLAME->"turret_flame";});}
    /** Pure: the yaw (radians, about +Y) that turns a model facing north toward (dx, dz), and the pitch that lifts it toward dy */
    static float yaw(double dx,double dz){return (float)Math.atan2(-dx,-dz);}
    static float pitch(double dy,double flat){return (float)Math.atan2(dy,flat);}
    /** Pure: the muzzle's height and the turret's middle, from the anchor block's corner */
    static Vec3 middle(int mk){return mk>=3?new Vec3(1,1.25,1):new Vec3(.5,1.2,.5);}

    static final class TurretBlock extends Block implements EntityBlock,DefenseStructures.Footprint {
        final Line line;final int mk;
        TurretBlock(Line line,int mk){super(Properties.of().strength(stats(line,mk).hardness(),12).noOcclusion().sound(SoundType.METAL));this.line=line;this.mk=mk;}
        @Override public List<BlockPos> extra(BlockPos root,BlockState state){
            if(mk<3)return List.of(root.above());
            var out=new ArrayList<BlockPos>();for(int y=0;y<2;y++)for(int x=0;x<2;x++)for(int z=0;z<2;z++)if(x!=0||y!=0||z!=0)out.add(root.offset(x,y,z));return out;
        }
        @Override public VoxelShape whole(BlockState state){return mk>=3?Shapes.box(0,0,0,2,1.75,2):Shapes.box(.0625,0,.0625,.9375,1.75,.9375);}
        @Override public boolean ports(){return true;}
        @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return DefenseStructures.cell(s,0,0,0);}
        @Override public PushReaction getPistonPushReaction(BlockState s){return PushReaction.BLOCK;}
        @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new TurretEntity(pos,state);}
        @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l,BlockState s,BlockEntityType<T> type){
            return l.isClientSide||type!=EternalDefense.TURRET_ENTITY.get()?null:(level,pos,state,be)->((TurretEntity)be).serverTick();
        }
        @Override public void onPlace(BlockState s,Level l,BlockPos pos,BlockState old,boolean moving){super.onPlace(s,l,pos,old,moving);if(l instanceof ServerLevel)DefenseStructures.install(l,pos,s);}
        @Override public void tick(BlockState s,ServerLevel l,BlockPos pos,net.minecraft.util.RandomSource random){DefenseStructures.install(l,pos,s);}
        @Override public void onRemove(BlockState s,Level l,BlockPos pos,BlockState next,boolean moving){
            if(!s.is(next.getBlock())){if(l.getBlockEntity(pos) instanceof TurretEntity be)for(int i=0;i<SLOTS;i++)Containers.dropItemStack(l,pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,be.ammo.getStackInSlot(i));DefenseStructures.remove(l,pos);}
            super.onRemove(s,l,pos,next,moving);
        }
        @Override public InteractionResult use(BlockState s,Level l,BlockPos pos,Player p,InteractionHand hand,BlockHitResult hit){
            if(!(l.getBlockEntity(pos) instanceof TurretEntity be))return InteractionResult.PASS;
            if(p.getItemInHand(hand).getItem() instanceof PowerPoles.WireItem)return InteractionResult.PASS;
            if(p instanceof ServerPlayer sp){
                var held=p.getItemInHand(hand);
                if(held.is(ammo(line))){var rest=net.minecraftforge.items.ItemHandlerHelper.insertItem(be.ammo,held.copy(),false);int put=held.getCount()-rest.getCount();if(!p.isCreative())held.shrink(put);
                    if(put>0)l.playSound(null,pos,SoundEvents.ARMOR_EQUIP_IRON,SoundSource.BLOCKS,.8f,1.2f);}
                sp.displayClientMessage(be.status(),true);
            }
            return InteractionResult.sidedSuccess(l.isClientSide);
        }
    }

    static final class TurretEntity extends BlockEntity implements PowerGrid.Consumer {
        final ItemStackHandler ammo=new ItemStackHandler(SLOTS){
            @Override public boolean isItemValid(int slot,ItemStack stack){return stack.is(Turrets.ammo(block().line));}
            @Override protected void onContentsChanged(int slot){setChanged();}
        };
        /** Belts, chutes and funnels load it; nothing takes the ammunition back out */
        private final IItemHandler loader=new IItemHandler(){
            @Override public int getSlots(){return ammo.getSlots();}
            @Override public ItemStack getStackInSlot(int slot){return ammo.getStackInSlot(slot);}
            @Override public ItemStack insertItem(int slot,ItemStack stack,boolean simulate){return ammo.insertItem(slot,stack,simulate);}
            @Override public ItemStack extractItem(int slot,int amount,boolean simulate){return ItemStack.EMPTY;}
            @Override public int getSlotLimit(int slot){return ammo.getSlotLimit(slot);}
            @Override public boolean isItemValid(int slot,ItemStack stack){return ammo.isItemValid(slot,stack);}
        };
        private LazyOptional<IItemHandler> loaderCap=LazyOptional.of(()->loader);
        int rounds,cooldown,targetId=-1;boolean powered;
        /** Client side: the time of the last shot (for the recoil) and the aim drawn */
        long shotAt=-100;float drawYaw,drawPitch,drawnAt;
        TurretEntity(BlockPos pos,BlockState state){super(EternalDefense.TURRET_ENTITY.get(),pos,state);}
        TurretBlock block(){return (TurretBlock)getBlockState().getBlock();}
        Stats stats(){return Turrets.stats(block().line,block().mk);}
        @Override public void onLoad(){super.onLoad();if(level instanceof ServerLevel l)PowerGrid.joinTurret(l,worldPosition);}
        @Override public float stress(){return stats().stress();}
        @Override public void power(boolean on){if(powered!=on){powered=on;if(!on)aimAt(null);sync();}}
        int loaded(){int n=rounds;for(int i=0;i<SLOTS;i++)n+=ammo.getStackInSlot(i).getCount()*perItem(block().line);return n;}
        Component status(){
            var name=block().getName();
            if(!powered)return Component.translatable("gui.eternal_defense.turret.unpowered",name,loaded(),PowerKit.AURA).withStyle(ChatFormatting.GOLD);
            if(loaded()==0)return Component.translatable("gui.eternal_defense.turret.empty",name,ammo(block().line).getDescription()).withStyle(ChatFormatting.GOLD);
            return Component.translatable("gui.eternal_defense.turret.ready",name,loaded(),stats().range()).withStyle(ChatFormatting.AQUA);
        }
        Vec3 muzzle(){return Vec3.atLowerCornerOf(worldPosition).add(middle(block().mk));}
        /** A mob it may shoot: a hostile or raid mob, alive, in range and in sight */
        boolean valid(Mob m,Vec3 from,int range){
            if(!foe(m))return false;
            var at=aim(m);if(at.distanceToSqr(from)>range*range)return false;
            return level.clip(new Sight(from,at,worldPosition)).getType()==HitResult.Type.MISS;
        }
        static Vec3 aim(Mob m){return m.position().add(0,m.getBbHeight()*.55,0);}
        void serverTick(){
            if(!(level instanceof ServerLevel l))return;
            if(cooldown>0)cooldown--;
            if(!powered)return;
            var s=stats();var from=muzzle();
            var current=targetId<0?null:l.getEntity(targetId) instanceof Mob m?m:null;
            if(current!=null&&!valid(current,from,s.range()))current=null;
            if(current==null&&l.getGameTime()%4==0){
                Mob best=null;double d=Double.MAX_VALUE;
                for(var m:l.getEntitiesOfClass(Mob.class,new AABB(from,from).inflate(s.range()),m->true)){double dist=aim(m).distanceToSqr(from);if(dist<d&&valid(m,from,s.range())){d=dist;best=m;}}
                current=best;
            }
            aimAt(current);
            if(current==null||cooldown>0)return;
            if(rounds<=0&&!reload()){if(l.getGameTime()%40==0)l.playSound(null,worldPosition,SoundEvents.DISPENSER_FAIL,SoundSource.BLOCKS,.6f,1.4f);return;}
            rounds--;cooldown=s.cooldown();setChanged();
            fire(l,from,current,s);
            l.blockEvent(worldPosition,getBlockState().getBlock(),1,0);
        }
        private boolean reload(){for(int i=0;i<SLOTS;i++)if(!ammo.extractItem(i,1,false).isEmpty()){rounds+=perItem(block().line);return true;}return false;}
        private void aimAt(@Nullable Mob m){int id=m==null?-1:m.getId();if(id!=targetId){targetId=id;sync();}}
        private void fire(ServerLevel l,Vec3 from,Mob target,Stats s){
            var line=block().line;var type=l.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getHolderOrThrow(damage(line));
            var source=new DamageSource(type,null,null,from);var to=aim(target);var dir=to.subtract(from).normalize();
            switch(line){
                case FLAME->{
                    double cone=Math.cos(Math.toRadians(s.spread()/2));
                    for(var m:l.getEntitiesOfClass(Mob.class,new AABB(from,from).inflate(s.range()),m->true)){
                        var v=aim(m).subtract(from);double len=v.length();
                        if(len>s.range()||len<1e-3||v.scale(1/len).dot(dir)<cone||!valid(m,from,s.range()))continue;
                        m.invulnerableTime=0;if(m.hurt(source,s.damage()))m.setSecondsOnFire(s.burn());
                    }
                    for(int i=0;i<10;i++){var jitter=dir.add(l.random.nextGaussian()*.12,l.random.nextGaussian()*.08,l.random.nextGaussian()*.12).normalize().scale(.35+l.random.nextDouble()*.35);
                        l.sendParticles(ParticleTypes.FLAME,from.x+dir.x*.6,from.y+dir.y*.6,from.z+dir.z*.6,0,jitter.x,jitter.y,jitter.z,1);}
                    if(l.getGameTime()%10==0)l.playSound(null,worldPosition,SoundEvents.FIRECHARGE_USE,SoundSource.BLOCKS,.5f,.8f+l.random.nextFloat()*.2f);
                }
                default->{
                    var hits=new HashMap<Mob,Float>();
                    for(int p=0;p<s.pellets();p++){
                        var ray=spread(dir,s.spread(),l.random);var end=from.add(ray.scale(s.range()));
                        var block=l.clip(new Sight(from,end,worldPosition));if(block.getType()!=HitResult.Type.MISS)end=block.getLocation();
                        Mob hit=null;double best=Double.MAX_VALUE;
                        for(var m:l.getEntitiesOfClass(Mob.class,new AABB(from,end).inflate(1),Turrets::foe)){
                            var box=m.getBoundingBox().inflate(.2).clip(from,end);if(box.isPresent()){double dd=box.get().distanceToSqr(from);if(dd<best){best=dd;hit=m;}}
                        }
                        if(hit!=null){hits.merge(hit,s.damage(),Float::sum);end=from.add(ray.scale(Math.sqrt(best)));}
                        tracer(l,from,end);
                    }
                    for(var e:hits.entrySet()){e.getKey().invulnerableTime=0;e.getKey().hurt(source,e.getValue());}
                    l.sendParticles(ParticleTypes.SMOKE,from.x+dir.x*.7,from.y+dir.y*.7,from.z+dir.z*.7,2,.03,.03,.03,.01);
                    if(line==Line.MG)l.playSound(null,worldPosition,SoundEvents.FIREWORK_ROCKET_BLAST,SoundSource.BLOCKS,.7f,1.3f+l.random.nextFloat()*.2f);
                    else l.playSound(null,worldPosition,SoundEvents.GENERIC_EXPLODE,SoundSource.BLOCKS,.45f,1.7f+l.random.nextFloat()*.2f);
                }
            }
        }
        private static Vec3 spread(Vec3 dir,float degrees,net.minecraft.util.RandomSource r){
            double s=Math.toRadians(degrees)/2;return dir.add(r.nextGaussian()*s*.5,r.nextGaussian()*s*.5,r.nextGaussian()*s*.5).normalize();
        }
        private static void tracer(ServerLevel l,Vec3 from,Vec3 to){var v=to.subtract(from);int n=(int)Math.min(24,v.length()/1.5);for(int i=1;i<=n;i++){var p=from.add(v.scale(i/(double)n));l.sendParticles(ParticleTypes.CRIT,p.x,p.y,p.z,1,0,0,0,0);}}
        @Override public boolean triggerEvent(int id,int arg){if(id==1){if(level!=null&&level.isClientSide)shotAt=level.getGameTime();return true;}return super.triggerEvent(id,arg);}
        void sync(){setChanged();if(level!=null)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}
        @Override public <T> LazyOptional<T> getCapability(Capability<T> cap,@Nullable Direction side){return cap==ForgeCapabilities.ITEM_HANDLER?loaderCap.cast():super.getCapability(cap,side);}
        @Override public void invalidateCaps(){super.invalidateCaps();loaderCap.invalidate();}
        @Override public void reviveCaps(){super.reviveCaps();loaderCap=LazyOptional.of(()->loader);}
        @Override protected void saveAdditional(CompoundTag tag){super.saveAdditional(tag);tag.put("Ammo",ammo.serializeNBT());tag.putInt("Rounds",rounds);}
        @Override public void load(CompoundTag tag){super.load(tag);ammo.deserializeNBT(tag.getCompound("Ammo"));rounds=tag.getInt("Rounds");
            if(tag.contains("Target"))targetId=tag.getInt("Target");if(tag.contains("Powered"))powered=tag.getBoolean("Powered");}
        @Override public CompoundTag getUpdateTag(){var t=new CompoundTag();t.putInt("Target",targetId);t.putBoolean("Powered",powered);t.putInt("Rounds",rounds);return t;}
        @Override public void handleUpdateTag(CompoundTag tag){targetId=tag.getInt("Target");powered=tag.getBoolean("Powered");rounds=tag.getInt("Rounds");}
        @Override public Packet<ClientGamePacketListener> getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
        @Override public void onDataPacket(net.minecraft.network.Connection net,ClientboundBlockEntityDataPacket packet){if(packet.getTag()!=null)handleUpdateTag(packet.getTag());}
        @Override public AABB getRenderBoundingBox(){return new AABB(worldPosition).inflate(2);}
    }
    /** Pure: damage a second against one target in the open (pellets that all hit; a flamethrower burst on each mob in its cone) */
    static float dps(Line line,int mk){var s=stats(line,mk);return s.damage()*s.pellets()*20f/s.cooldown();}
    /** A turret's item: what its line does, its Mark's numbers and where it may stand */
    static final class TurretItem extends PowerKit.KitItem {
        final Line line;final int mk;
        TurretItem(Block block,Line line,int mk){super(block,line.id+"_turret");this.line=line;this.mk=mk;}
        @Override public void appendHoverText(ItemStack stack,Level level,List<Component> lines,TooltipFlag flag){
            super.appendHoverText(stack,level,lines,flag);var s=stats(line,mk);
            lines.add(Math.max(1,lines.size()-1),Component.translatable("tooltip.eternal_defense.turret_stats",s.range(),PowerPoles.fmt(dps(line,mk)),s.stress(),perItem(line)).withStyle(ChatFormatting.GOLD));
        }
    }
    /** Turret ammunition: one item loads perItem rounds into a turret of its line */
    static final class AmmoItem extends Item {
        final Line line;
        AmmoItem(Line line){super(new Properties());this.line=line;}
        @Override public void appendHoverText(ItemStack stack,Level level,List<Component> lines,TooltipFlag flag){lines.add(Component.translatable("tooltip.eternal_defense."+line.id+"_turret_ammo",perItem(line)).withStyle(ChatFormatting.GRAY));}
    }
    /** A sight line from the muzzle that the turret's own blocks do not stop */
    static final class Sight extends ClipContext {
        final BlockPos root;
        Sight(Vec3 from,Vec3 to,BlockPos root){super(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,null);this.root=root;}
        @Override public VoxelShape getBlockShape(BlockState s,BlockGetter l,BlockPos p){
            return p.equals(root)||s.getBlock() instanceof DefenseStructures.Part&&DefenseStructures.anchor(l,p).equals(root)?Shapes.empty():super.getBlockShape(s,l,p);
        }
    }
    /** A mob a turret shoots: a living hostile or raid mob, never the beacon's or a defense's stand-in */
    static boolean foe(Mob m){return m.isAlive()&&!m.isRemoved()&&(m instanceof Enemy||m.getPersistentData().getBoolean("eternalRaider"))&&!(m instanceof RaidTargets.Point)&&!(m instanceof BeaconCombat.Objective);}
    static String id(Line line,int mk){return line.id+"_turret_mk"+mk;}
    /** Pure: the gun's drawn pitch, inside what the mount allows (degrees) */
    static float clampPitch(float degrees){return Mth.clamp(degrees,-15,20);}
}
