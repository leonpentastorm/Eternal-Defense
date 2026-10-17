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
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
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
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
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

/** Defense towers on power: machine gun, shotgun, flamethrower and tesla coil lines in three Marks, the Heavy Cannon and the Curse Gas Totem */
final class Turrets {
    private Turrets(){}
    enum Line {
        MG("mg",3),SHOTGUN("shotgun",3),FLAME("flame",3),TESLA("tesla",3),CANNON("heavy_cannon",1),CURSE("curse_totem",1);
        final String id;final int marks;
        Line(String id,int marks){this.id=id;this.marks=marks;}
        /** It needs its own ammunition as well as power (the tesla coil and the totem run on power alone) */
        boolean fed(){return this!=TESLA&&this!=CURSE;}
        /** A shot kicks the gun back (and the guns throw out casings) */
        boolean recoils(){return this==MG||this==SHOTGUN||this==CANNON;}
    }
    /** Range in blocks, ticks between shots, damage a hit (a pellet, a burst, a zap's first mob, a shell's centre, the gas a second), pellets a shot (tesla: mobs a zap reaches),
     *  spread in degrees (flame: the jet's width; tesla: a jump's reach; cannon and totem: the blast's or the cloud's radius, all in blocks), stress per RPM, seconds of fire (totem: of a cloud), block hardness */
    record Stats(int range,int cooldown,float damage,int pellets,float spread,int stress,int burn,float hardness){}
    static Stats stats(Line line,int mk){
        return switch(line){
            case MG->switch(mk){case 1->new Stats(18,6,2.4f,1,1.5f,8,0,6);case 2->new Stats(22,4,2.8f,1,1.5f,12,0,8);default->new Stats(26,3,3.2f,1,2,20,0,10);};
            case SHOTGUN->switch(mk){case 1->new Stats(10,24,1.5f,6,12,8,0,6);case 2->new Stats(12,16,1.6f,7,11,12,0,8);default->new Stats(14,10,1.7f,8,12,20,0,10);};
            case FLAME->switch(mk){case 1->new Stats(8,5,3,1,2,12,5,6);case 2->new Stats(10,5,4,1,2.5f,16,6,8);default->new Stats(12,5,5,1,3,24,7,10);};
            case TESLA->switch(mk){case 1->new Stats(10,30,6,3,5,10,0,6);case 2->new Stats(12,26,7,5,5.5f,14,0,8);default->new Stats(14,22,8,7,6,22,0,10);};
            case CANNON->new Stats(40,80,30,1,4,32,0,10);
            case CURSE->new Stats(16,40,2,1,3,16,5,8);
        };
    }
    /** Rounds one ammunition item loads: a can of belted rounds, a box of shells, a fuel bottle's bursts, a crate of cannon shells (none for the towers that need none) */
    static int perItem(Line line){return switch(line){case MG->64;case SHOTGUN->12;case FLAME->40;case CANNON->4;default->0;};}
    static final int SLOTS=4;
    /** A line's ammunition item, or null for a tower that needs none */
    static Item ammo(Line line){return switch(line){case MG->EternalDefense.MG_AMMO.get();case SHOTGUN->EternalDefense.SHOTGUN_AMMO.get();case FLAME->EternalDefense.FLAME_FUEL.get();case CANNON->EternalDefense.CANNON_SHELLS.get();default->null;};}
    static ResourceKey<DamageType> damage(Line line){return DefenseWalls.key(switch(line){case MG->"turret_bullet";case SHOTGUN->"turret_pellet";case FLAME->"turret_flame";case TESLA->"turret_tesla";case CANNON->"turret_shell";case CURSE->"turret_curse";});}
    /** Pure: the yaw (radians, about +Y) that turns a model facing north toward (dx, dz), and the pitch that lifts it toward dy */
    static float yaw(double dx,double dz){return (float)Math.atan2(-dx,-dz);}
    static float pitch(double dy,double flat){return (float)Math.atan2(dy,flat);}
    /** Pure: where a tower shoots from, from the anchor block's corner: a gun's muzzle height and middle, a coil's strike ball, the cannon's trunnions, the totem's crystal */
    static Vec3 middle(Line line,int mk){
        return switch(line){
            case TESLA->mk>=3?new Vec3(1,2,1):new Vec3(.5,(mk==2?31.5:30.5)/16,.5);
            case CANNON->new Vec3(1,1.25,1);
            case CURSE->new Vec3(.5,1.85,.5);
            default->mk>=3?new Vec3(1,1.25,1):new Vec3(.5,1.2,.5);
        };
    }
    /** Pure: the tower stands on 2 x 2 blocks, 2 tall (a Mark III and the Heavy Cannon); the others on one block, 2 tall */
    static boolean wide(Line line,int mk){return mk>=3||line==Line.CANNON;}
    static TurretBlock block(Line line,int mk){return switch(line){case TESLA->new TeslaBlock(mk);case CURSE->new TotemBlock();default->new TurretBlock(line,mk);};}

    static class TurretBlock extends Block implements EntityBlock,DefenseStructures.Footprint {
        final Line line;final int mk;
        TurretBlock(Line line,int mk){this(line,mk,Properties.of());}
        TurretBlock(Line line,int mk,Properties properties){super(properties.strength(stats(line,mk).hardness(),12).noOcclusion().sound(SoundType.METAL));this.line=line;this.mk=mk;}
        @Override public List<BlockPos> extra(BlockPos root,BlockState state){
            if(!wide(line,mk))return List.of(root.above());
            var out=new ArrayList<BlockPos>();for(int y=0;y<2;y++)for(int x=0;x<2;x++)for(int z=0;z<2;z++)if(x!=0||y!=0||z!=0)out.add(root.offset(x,y,z));return out;
        }
        @Override public VoxelShape whole(BlockState state){
            if(line==Line.TESLA)return mk>=3?Shapes.box(0,0,0,2,2,2):Shapes.box(.0625,0,.0625,.9375,1.9,.9375);
            if(line==Line.CURSE)return Shapes.box(.0625,0,.0625,.9375,1.9,.9375);
            return wide(line,mk)?Shapes.box(0,0,0,2,1.75,2):Shapes.box(.0625,0,.0625,.9375,1.75,.9375);
        }
        @Override public boolean ports(){return line.fed();}
        @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return DefenseStructures.cell(s,0,0,0);}
        @Override public PushReaction getPistonPushReaction(BlockState s){return PushReaction.BLOCK;}
        @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new TurretEntity(pos,state);}
        @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l,BlockState s,BlockEntityType<T> type){
            if(type!=EternalDefense.TURRET_ENTITY.get())return null;
            if(l.isClientSide)return line!=Line.FLAME?null:(level,pos,state,be)->net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,()->()->TurretEffectsClient.flame((TurretEntity)be));
            return (level,pos,state,be)->((TurretEntity)be).serverTick();
        }
        @Override public void onPlace(BlockState s,Level l,BlockPos pos,BlockState old,boolean moving){super.onPlace(s,l,pos,old,moving);if(l instanceof ServerLevel)DefenseStructures.install(l,pos,s);}
        @Override public void tick(BlockState s,ServerLevel l,BlockPos pos,RandomSource random){DefenseStructures.install(l,pos,s);}
        /** A shot reaches the clients as a block event: recoil and casings, a tesla zap's links */
        @Override public boolean triggerEvent(BlockState s,Level l,BlockPos pos,int id,int param){super.triggerEvent(s,l,pos,id,param);var be=l.getBlockEntity(pos);return be!=null&&be.triggerEvent(id,param);}
        @Override public void onRemove(BlockState s,Level l,BlockPos pos,BlockState next,boolean moving){
            if(!s.is(next.getBlock())){if(l.getBlockEntity(pos) instanceof TurretEntity be)for(int i=0;i<SLOTS;i++)Containers.dropItemStack(l,pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,be.ammo.getStackInSlot(i));DefenseStructures.remove(l,pos);}
            super.onRemove(s,l,pos,next,moving);
        }
        @Override public InteractionResult use(BlockState s,Level l,BlockPos pos,Player p,InteractionHand hand,BlockHitResult hit){
            if(!(l.getBlockEntity(pos) instanceof TurretEntity be))return InteractionResult.PASS;
            if(p.getItemInHand(hand).getItem() instanceof PowerPoles.WireItem)return InteractionResult.PASS;
            if(p instanceof ServerPlayer sp){
                var held=p.getItemInHand(hand);
                if(!line.fed())sp.displayClientMessage(be.status(),true);
                else if(held.is(ammo(line))){var rest=net.minecraftforge.items.ItemHandlerHelper.insertItem(be.ammo,held.copy(),false);int put=held.getCount()-rest.getCount();if(!p.isCreative())held.shrink(put);
                    if(put>0)l.playSound(null,pos,SoundEvents.ARMOR_EQUIP_IRON,SoundSource.BLOCKS,.96f,1.2f);sp.displayClientMessage(be.status(),true);}
                else net.minecraftforge.network.NetworkHooks.openScreen(sp,new net.minecraft.world.SimpleMenuProvider((id,inv,who)->new TurretMenu(id,inv,be.ammo,pos,be,line,mk),getName()),
                    buf->{buf.writeBlockPos(pos);buf.writeByte(line.ordinal());buf.writeByte(mk);});
            }
            return InteractionResult.sidedSuccess(l.isClientSide);
        }
    }
    /** A tesla coil: its arcs glow and crackle on the model while it has power */
    static final class TeslaBlock extends TurretBlock {
        static final BooleanProperty POWERED=BlockStateProperties.POWERED;
        TeslaBlock(int mk){super(Line.TESLA,mk,Properties.of().lightLevel(s->s.getValue(POWERED)?7:0));registerDefaultState(stateDefinition.any().setValue(POWERED,false));}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(POWERED);}
    }
    /** The Curse Gas Totem: it faces whoever placed it, and gas drifts round it while its clouds are down */
    static final class TotemBlock extends TurretBlock {
        static final BooleanProperty ACTIVE=BooleanProperty.create("active");
        TotemBlock(){super(Line.CURSE,1,Properties.of().lightLevel(s->s.getValue(ACTIVE)?6:3));registerDefaultState(stateDefinition.any().setValue(DefenseStructures.FACING,Direction.NORTH).setValue(ACTIVE,false));}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(DefenseStructures.FACING,ACTIVE);}
        @Override public BlockState getStateForPlacement(BlockPlaceContext c){return defaultBlockState().setValue(DefenseStructures.FACING,c.getHorizontalDirection().getOpposite());}
        @Override public void animateTick(BlockState s,Level l,BlockPos pos,RandomSource r){
            if(!s.getValue(ACTIVE))return;
            for(int i=0;i<2;i++)l.addParticle(ParticleTypes.WITCH,pos.getX()+.2+r.nextDouble()*.6,pos.getY()+1.35+r.nextDouble()*.5,pos.getZ()+.2+r.nextDouble()*.6,0,.02,0);
            if(r.nextInt(3)==0)l.addParticle(ParticleTypes.DRAGON_BREATH,pos.getX()+.5,pos.getY()+1.9,pos.getZ()+.5,(r.nextDouble()-.5)*.03,.02,(r.nextDouble()-.5)*.03);
        }
    }

    /** A tesla zap seen by a client: the mobs it jumped through, in order, and when */
    static final class Zap {final List<Integer> mobs=new ArrayList<>();final long at;Zap(long at){this.at=at;}}
    /** A cloud of curse gas on the ground: its middle, its radius and the tick it clears */
    record Cloud(Vec3 at,float radius,long until){}

    static final class TurretEntity extends BlockEntity implements PowerGrid.Consumer {
        final ItemStackHandler ammo=new ItemStackHandler(SLOTS){
            @Override public boolean isItemValid(int slot,ItemStack stack){return block().line.fed()&&stack.is(Turrets.ammo(block().line));}
            @Override protected void onContentsChanged(int slot){setChanged();shown=true;}
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
        /** A flame turret's jet is burning (kept for FLAME_HOLD ticks after the last burst, so its sound runs on between targets) */
        boolean flaming;long lastFlame;
        /** The Heavy Cannon lobs its shells over cover (each client works out the same arc) */
        boolean high;
        /** The totem's clouds on the ground (not saved: they clear in seconds) */
        final List<Cloud> clouds=new ArrayList<>();
        /** Shots loaded changed since the clients last heard (sent at most twice a second) */
        boolean shown;
        /** Client side: the shots loaded, for the status light and the line under the crosshair */
        int loadedShown;
        /** Client side: the time of the last shot (for the recoil) and the aim drawn */
        long shotAt=-100;float drawYaw,drawPitch,drawnAt;
        /** Client side: the tesla zaps still drawn */
        final List<Zap> zaps=new ArrayList<>();
        TurretEntity(BlockPos pos,BlockState state){super(EternalDefense.TURRET_ENTITY.get(),pos,state);}
        TurretBlock block(){return (TurretBlock)getBlockState().getBlock();}
        Stats stats(){return Turrets.stats(block().line,block().mk);}
        @Override public void onLoad(){super.onLoad();if(level instanceof ServerLevel l)PowerGrid.joinTurret(l,worldPosition);}
        @Override public float stress(){return stats().stress();}
        @Override public void power(boolean on){
            if(powered!=on){powered=on;if(!on){aimAt(null);flaming=false;}sync();}
            var s=getBlockState();if(level!=null&&s.hasProperty(TeslaBlock.POWERED)&&s.getValue(TeslaBlock.POWERED)!=on)level.setBlock(worldPosition,s.setValue(TeslaBlock.POWERED,on),3);
        }
        void flaming(boolean on){if(flaming!=on){flaming=on;sync();}}
        int loaded(){int n=rounds;for(int i=0;i<SLOTS;i++)n+=ammo.getStackInSlot(i).getCount()*perItem(block().line);return n;}
        Component status(){return status(loaded());}
        /** How it stands, with the shots loaded the caller knows (the client only hears the count) */
        Component status(int loaded){
            var name=block().getName();boolean fed=block().line.fed();
            if(!powered)return (fed?Component.translatable("gui.eternal_defense.turret.unpowered",name,loaded,PowerKit.AURA):Component.translatable("gui.eternal_defense.turret.unpowered_free",name,PowerKit.AURA)).withStyle(ChatFormatting.GOLD);
            if(fed&&loaded==0)return Component.translatable("gui.eternal_defense.turret.empty",name,ammo(block().line).getDescription()).withStyle(ChatFormatting.GOLD);
            return (fed?Component.translatable("gui.eternal_defense.turret.ready",name,loaded,stats().range()):Component.translatable("gui.eternal_defense.turret.ready_free",name,stats().range())).withStyle(ChatFormatting.AQUA);
        }
        Vec3 muzzle(){return Vec3.atLowerCornerOf(worldPosition).add(middle(block().line,block().mk));}
        /** A mob it may shoot: a hostile or raid mob, alive, in range and in sight */
        boolean valid(Mob m,Vec3 from,int range){
            if(!foe(m))return false;
            var at=aim(m);if(at.distanceToSqr(from)>range*range)return false;
            return level.clip(new Sight(from,at,worldPosition)).getType()==HitResult.Type.MISS;
        }
        /** A mob the Heavy Cannon may keep aiming at: a foe in range, not too close (whether a shell can reach it is worked out when it fires) */
        boolean inReach(Mob m,Vec3 from,int range){
            if(!foe(m))return false;double d=aim(m).distanceToSqr(from);return d<=range*range&&d>=CANNON_MIN*CANNON_MIN;
        }
        static Vec3 aim(Mob m){return m.position().add(0,m.getBbHeight()*.55,0);}
        void serverTick(){
            if(!(level instanceof ServerLevel l))return;
            var line=block().line;
            if(shown&&l.getGameTime()%10==0){shown=false;sync();}
            if(flaming&&l.getGameTime()-lastFlame>FLAME_HOLD)flaming(false);
            if(line==Line.CURSE)clouds(l);
            if(cooldown>0)cooldown--;
            if(!powered)return;
            var s=stats();var from=muzzle();
            var current=targetId<0?null:l.getEntity(targetId) instanceof Mob m?m:null;
            if(current!=null&&!(line==Line.CANNON?inReach(current,from,s.range()):valid(current,from,s.range())))current=null;
            if(current==null&&l.getGameTime()%4==0&&(line!=Line.CANNON||cooldown<=CANNON_SEEK))current=pick(l,from,s);
            aimAt(current);
            if(current==null||cooldown>0)return;
            if(line.fed()&&rounds<=0&&!reload()){if(l.getGameTime()%40==0)l.playSound(null,worldPosition,SoundEvents.DISPENSER_FAIL,SoundSource.BLOCKS,.72f,1.4f);return;}
            if(!fire(l,from,current,s)){aimAt(null);return;}
            if(line.fed()){rounds--;setChanged();shown=true;}
            cooldown=s.cooldown();
            if(line.recoils())l.blockEvent(worldPosition,getBlockState().getBlock(),1,0);
        }
        /** The mob to shoot: the nearest in sight; the cannon's nearest it can land a shell on; the totem's with the most foes round it */
        private Mob pick(ServerLevel l,Vec3 from,Stats s){
            var line=block().line;var near=new ArrayList<Mob>();
            for(var m:l.getEntitiesOfClass(Mob.class,new AABB(from,from).inflate(s.range()),Turrets::foe))if(aim(m).distanceToSqr(from)<=s.range()*s.range())near.add(m);
            near.sort(Comparator.comparingDouble(m->aim(m).distanceToSqr(from)));
            if(line==Line.CANNON){
                int tried=0;
                for(var m:near){if(!inReach(m,from,s.range()))continue;if(++tried>CANNON_TRIES)break;if(CannonShell.plan(l,worldPosition,m)!=null)return m;}
                return null;
            }
            if(line!=Line.CURSE){for(var m:near)if(valid(m,from,s.range()))return m;return null;}
            Mob best=null;int most=0;double r=s.spread()*s.spread();
            for(var m:near){if(!valid(m,from,s.range()))continue;int n=0;for(var o:near)if(o.distanceToSqr(m)<=r)n++;if(n>most){most=n;best=m;}}
            return best;
        }
        private boolean reload(){for(int i=0;i<SLOTS;i++)if(!ammo.extractItem(i,1,false).isEmpty()){rounds+=perItem(block().line);return true;}return false;}
        private void aimAt(@Nullable Mob m){int id=m==null?-1:m.getId();if(id!=targetId){targetId=id;sync();}}
        /** Fires at the target; false when it cannot (no arc for a cannon shell), so nothing is spent */
        private boolean fire(ServerLevel l,Vec3 from,Mob target,Stats s){
            var line=block().line;var type=l.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getHolderOrThrow(damage(line));
            var source=new DamageSource(type,null,null,from);var to=aim(target);var dir=to.subtract(from).normalize();
            switch(line){
                case FLAME->{
                    // One box along the aim, cut short by the first wall: every foe it touches burns
                    var jet=mount(dir);var stop=l.clip(new Sight(from,from.add(jet.scale(s.range())),worldPosition));
                    double length=stop.getType()==HitResult.Type.MISS?s.range():stop.getLocation().distanceTo(from),half=s.spread()/2;
                    for(var m:l.getEntitiesOfClass(Mob.class,new AABB(from,from.add(jet.scale(length))).inflate(half),Turrets::foe)){
                        if(!inJet(m.getBoundingBox(),from,jet,length,half))continue;
                        m.invulnerableTime=0;if(m.hurt(source,s.damage()))m.setSecondsOnFire(s.burn());
                    }
                    lastFlame=l.getGameTime();flaming(true);
                }
                case TESLA->zap(l,target,s,source);
                case CANNON->{
                    var shot=CannonShell.plan(l,worldPosition,target);if(shot==null)return false;
                    CannonShell.fire(l,shot,s.damage(),s.spread(),worldPosition);
                    if(high!=shot.high()){high=shot.high();sync();}
                    l.playSound(null,worldPosition,DefenseSounds.CANNON_SHOT.get(),SoundSource.BLOCKS,1f,.95f+l.random.nextFloat()*.1f);
                    var m=shot.from();var v=shot.velocity().normalize();
                    l.sendParticles(ParticleTypes.EXPLOSION,m.x,m.y,m.z,1,0,0,0,0);
                    l.sendParticles(ParticleTypes.LARGE_SMOKE,m.x,m.y,m.z,10,.15,.15,.15,.02);
                    l.sendParticles(ParticleTypes.CLOUD,m.x+v.x*.4,m.y+v.y*.4,m.z+v.z*.4,6,.2,.2,.2,.05);
                }
                case CURSE->cloud(l,from,target,s);
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
                    float push=knockback(line,block().mk);
                    for(var e:hits.entrySet()){var m=e.getKey();m.invulnerableTime=0;if(m.hurt(source,e.getValue())&&push>0)m.knockback(push,-dir.x,-dir.z);}
                    l.sendParticles(ParticleTypes.SMOKE,from.x+dir.x*.7,from.y+dir.y*.7,from.z+dir.z*.7,2,.03,.03,.03,.01);
                    if(line==Line.MG)l.playSound(null,worldPosition,DefenseSounds.MG_SHOT.get(),SoundSource.BLOCKS,1f,.95f+l.random.nextFloat()*.1f);
                    else l.playSound(null,worldPosition,DefenseSounds.SHOTGUN_SHOT.get(),SoundSource.BLOCKS,1f,.95f+l.random.nextFloat()*.1f);
                }
            }
            return true;
        }
        /** A tesla zap: the target, then the nearest foe in sight within a jump of the last one hit, as many as the Mark reaches, each a little weaker */
        private void zap(ServerLevel l,Mob target,Stats s,DamageSource source){
            var chain=new ArrayList<Mob>();chain.add(target);
            while(chain.size()<s.pellets()){
                var last=aim(chain.get(chain.size()-1));Mob next=null;double best=s.spread()*s.spread();
                for(var m:l.getEntitiesOfClass(Mob.class,new AABB(last,last).inflate(s.spread()),Turrets::foe)){
                    if(chain.contains(m))continue;double d=aim(m).distanceToSqr(last);
                    if(d<=best&&l.clip(new Sight(last,aim(m),worldPosition)).getType()==HitResult.Type.MISS){best=d;next=m;}
                }
                if(next==null)break;chain.add(next);
            }
            var block=getBlockState().getBlock();
            for(int i=0;i<chain.size();i++){
                var m=chain.get(i);l.blockEvent(worldPosition,block,i==0?2:3,m.getId());
                m.invulnerableTime=0;m.hurt(source,chainDamage(s.damage(),i));
                var c=aim(m);l.sendParticles(ParticleTypes.ELECTRIC_SPARK,c.x,c.y,c.z,10,.25,.4,.25,.2);
            }
            l.playSound(null,worldPosition,DefenseSounds.TESLA_ZAP.get(),SoundSource.BLOCKS,1f,.9f+l.random.nextFloat()*.2f);
        }
        /** The totem drops a cloud of curse gas round the target (the oldest goes when there are too many) */
        private void cloud(ServerLevel l,Vec3 from,Mob target,Stats s){
            var at=target.position();
            if(clouds.size()>=CLOUDS)clouds.remove(0);
            clouds.add(new Cloud(at,s.spread(),l.getGameTime()+s.burn()*20L));
            var path=at.add(0,.3,0).subtract(from);int n=(int)Math.min(20,path.length()*1.5);
            for(int i=1;i<=n;i++){double t=i/(double)n;var p=from.add(path.scale(t));l.sendParticles(ParticleTypes.WITCH,p.x,p.y+Math.sin(t*Math.PI)*1.5,p.z,1,.05,.05,.05,0);}
            l.playSound(null,at.x,at.y,at.z,DefenseSounds.CURSE_CLOUD.get(),SoundSource.BLOCKS,1f,.9f+l.random.nextFloat()*.2f);
            setActive(l,true);
        }
        /** The totem's clouds: drawn, cleared when their time is up, and every second they gas, slow and poison the foes in them */
        private void clouds(ServerLevel l){
            long now=l.getGameTime();
            if(clouds.removeIf(c->now>=c.until())&&clouds.isEmpty())setActive(l,false);
            if(clouds.isEmpty())return;
            if(now%4==0)for(var c:clouds){
                double r=c.radius();
                for(int i=0;i<6;i++){double a=l.random.nextDouble()*Math.PI*2,d=Math.sqrt(l.random.nextDouble())*r;l.sendParticles(ParticleTypes.DRAGON_BREATH,c.at().x+Math.cos(a)*d,c.at().y+.15,c.at().z+Math.sin(a)*d,1,0,.05,0,.01);}
                // Potion swirls tinted the curse's violet (with a count of 0 the offsets are the colour)
                for(int i=0;i<8;i++){double a=l.random.nextDouble()*Math.PI*2,d=Math.sqrt(l.random.nextDouble())*r;l.sendParticles(ParticleTypes.ENTITY_EFFECT,c.at().x+Math.cos(a)*d,c.at().y+.1+l.random.nextDouble()*.8,c.at().z+Math.sin(a)*d,0,.55,.15,.75,1);}
                for(int i=0;i<3;i++){double a=l.random.nextDouble()*Math.PI*2;l.sendParticles(ParticleTypes.WITCH,c.at().x+Math.cos(a)*r,c.at().y+.2,c.at().z+Math.sin(a)*r,1,0,.1,0,0);}
            }
            if(now%CURSE_PULSE!=0)return;
            var type=l.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getHolderOrThrow(damage(Line.CURSE));
            var gassed=new HashSet<Mob>();
            for(var c:clouds)for(var m:l.getEntitiesOfClass(Mob.class,new AABB(c.at(),c.at()).inflate(c.radius(),2,c.radius()),Turrets::foe))if(inCloud(m.position(),c.at(),c.radius()))gassed.add(m);
            float gas=stats().damage();
            for(var m:gassed){
                int slow=cursedTicks(CURSE_SLOW_TICKS,RaidAdaptation.resistance(m,RaidAdaptation.Kind.MAGIC));
                if(slow>0)m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,slow,CURSE_SLOW_LEVEL));
                m.addEffect(new MobEffectInstance(MobEffects.POISON,CURSE_POISON_TICKS,0));
                m.invulnerableTime=0;m.hurt(new DamageSource(type,null,null,m.position()),gas);
            }
        }
        private void setActive(ServerLevel l,boolean on){var s=getBlockState();if(s.hasProperty(TotemBlock.ACTIVE)&&s.getValue(TotemBlock.ACTIVE)!=on)l.setBlock(worldPosition,s.setValue(TotemBlock.ACTIVE,on),3);}
        private static Vec3 spread(Vec3 dir,float degrees,RandomSource r){
            double s=Math.toRadians(degrees)/2;return dir.add(r.nextGaussian()*s*.5,r.nextGaussian()*s*.5,r.nextGaussian()*s*.5).normalize();
        }
        private static void tracer(ServerLevel l,Vec3 from,Vec3 to){var v=to.subtract(from);int n=(int)Math.min(24,v.length()/1.5);for(int i=1;i<=n;i++){var p=from.add(v.scale(i/(double)n));l.sendParticles(ParticleTypes.CRIT,p.x,p.y,p.z,1,0,0,0,0);}}
        @Override public boolean triggerEvent(int id,int arg){
            if(id==1){if(level!=null&&level.isClientSide){shotAt=level.getGameTime();net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,()->()->TurretEffectsClient.casing(this));}return true;}
            if(id==2||id==3){if(level!=null&&level.isClientSide){if(id==2||zaps.isEmpty())zaps.add(new Zap(level.getGameTime()));zaps.get(zaps.size()-1).mobs.add(arg);}return true;}
            return super.triggerEvent(id,arg);
        }
        void sync(){setChanged();if(level!=null)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}
        @Override public <T> LazyOptional<T> getCapability(Capability<T> cap,@Nullable Direction side){return cap==ForgeCapabilities.ITEM_HANDLER&&block().line.fed()?loaderCap.cast():super.getCapability(cap,side);}
        @Override public void invalidateCaps(){super.invalidateCaps();loaderCap.invalidate();}
        @Override public void reviveCaps(){super.reviveCaps();loaderCap=LazyOptional.of(()->loader);}
        @Override protected void saveAdditional(CompoundTag tag){super.saveAdditional(tag);tag.put("Ammo",ammo.serializeNBT());tag.putInt("Rounds",rounds);}
        @Override public void load(CompoundTag tag){super.load(tag);ammo.deserializeNBT(tag.getCompound("Ammo"));rounds=tag.getInt("Rounds");
            if(tag.contains("Target"))targetId=tag.getInt("Target");if(tag.contains("Powered"))powered=tag.getBoolean("Powered");}
        @Override public CompoundTag getUpdateTag(){var t=new CompoundTag();t.putInt("Target",targetId);t.putBoolean("Powered",powered);t.putInt("Rounds",rounds);t.putInt("Loaded",loaded());t.putBoolean("Flaming",flaming);t.putBoolean("High",high);return t;}
        @Override public void handleUpdateTag(CompoundTag tag){targetId=tag.getInt("Target");powered=tag.getBoolean("Powered");rounds=tag.getInt("Rounds");loadedShown=tag.getInt("Loaded");flaming=tag.getBoolean("Flaming");high=tag.getBoolean("High");}
        /** Client side: the status light's key: ready, empty or off (a tower that needs no ammo is never empty) */
        String light(){return lightOf(powered,block().line.fed()?loadedShown:1);}
        @Override public Packet<ClientGamePacketListener> getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
        @Override public void onDataPacket(net.minecraft.network.Connection net,ClientboundBlockEntityDataPacket packet){if(packet.getTag()!=null)handleUpdateTag(packet.getTag());}
        /** Drawn while any of it is in view: a tesla's bolts reach its range, the cannon's barrel stands 3.4 blocks up */
        @Override public AABB getRenderBoundingBox(){
            var line=block().line;
            if(line==Line.TESLA)return new AABB(worldPosition).inflate(stats().range()+2);
            if(line==Line.CANNON)return new AABB(worldPosition.getX()-1.5,worldPosition.getY(),worldPosition.getZ()-1.5,worldPosition.getX()+3.5,worldPosition.getY()+4,worldPosition.getZ()+3.5);
            return new AABB(worldPosition).inflate(2);
        }
    }
    /** Pure: damage a second against one target in the open (pellets that all hit; a flamethrower burst on each mob in its cone; a zap's first mob; a shell's centre; the totem's gas on each mob in a cloud) */
    static float dps(Line line,int mk){var s=stats(line,mk);return line==Line.CURSE?s.damage():s.damage()*(line==Line.TESLA?1:s.pellets())*20f/s.cooldown();}
    /** A tesla zap is this much weaker at every jump */
    static final float CHAIN_FALLOFF=.85f;
    /** Pure: what a zap does to the mob it reaches after this many jumps */
    static float chainDamage(float first,int jumps){return first*(float)Math.pow(CHAIN_FALLOFF,jumps);}
    /** The Heavy Cannon: no mob closer than this (blocks), at most this many mobs tried for an arc at a time, and it looks for one only this close to firing (ticks) */
    static final int CANNON_MIN=5,CANNON_TRIES=8,CANNON_SEEK=20;
    /** The totem: at most this many clouds at once; they act every CURSE_PULSE ticks, slow (Slowness II) and poison for these many ticks */
    static final int CLOUDS=4,CURSE_PULSE=20,CURSE_SLOW_TICKS=40,CURSE_SLOW_LEVEL=1,CURSE_POISON_TICKS=60;
    /** Pure: a mob stands in a cloud: within its radius across, at most 2 blocks above or 1 below its middle */
    static boolean inCloud(Vec3 mob,Vec3 at,double radius){double dx=mob.x-at.x,dz=mob.z-at.z;return dx*dx+dz*dz<=radius*radius&&mob.y>=at.y-1&&mob.y<=at.y+2;}
    /** Pure: how long the curse slows a raider that has learned this much of magic traps (none once it is immune) */
    static int cursedTicks(int ticks,double resistance){return (int)Math.round(ticks*(1-Mth.clamp(resistance,0,1)));}
    /** A tower's item: what it does, its numbers and where it may stand */
    static final class TurretItem extends PowerKit.KitItem {
        final Line line;final int mk;
        TurretItem(Block block,Line line,int mk){super(block,line.marks>1?line.id+"_turret":line.id);this.line=line;this.mk=mk;}
        @Override public void appendHoverText(ItemStack stack,Level level,List<Component> lines,TooltipFlag flag){
            super.appendHoverText(stack,level,lines,flag);var s=stats(line,mk);
            var numbers=line.fed()?Component.translatable("tooltip.eternal_defense.turret_stats",s.range(),PowerPoles.fmt(dps(line,mk)),s.stress(),perItem(line))
                :Component.translatable("tooltip.eternal_defense.turret_stats_free",s.range(),PowerPoles.fmt(dps(line,mk)),s.stress());
            lines.add(Math.max(1,lines.size()-1),numbers.withStyle(ChatFormatting.GOLD));
        }
    }
    /** Turret ammunition: one item loads perItem rounds into a turret of its line */
    static final class AmmoItem extends Item {
        final Line line;
        AmmoItem(Line line){super(new Properties());this.line=line;}
        @Override public void appendHoverText(ItemStack stack,Level level,List<Component> lines,TooltipFlag flag){lines.add(Component.translatable("tooltip.eternal_defense."+(line.marks>1?line.id+"_turret":line.id)+"_ammo",perItem(line)).withStyle(ChatFormatting.GRAY));}
    }
    /** A player's punch knocks back this hard; a Mark III shotgun turret pushes each mob it hits a quarter of that, Mark I and II less */
    static final float PUNCH=.4f;
    static float knockback(Line line,int mk){return line==Line.SHOTGUN?PUNCH*(mk+1)/16:0;}
    /** Ticks a flame jet stays lit after its last burst */
    static final int FLAME_HOLD=10;
    /** Pure: a direction turned to the pitch the mount allows */
    static Vec3 mount(Vec3 dir){
        double flat=Math.sqrt(dir.x*dir.x+dir.z*dir.z),pitch=Math.toRadians(clampPitch((float)Math.toDegrees(Math.atan2(dir.y,flat)))),h=Math.cos(pitch);
        return flat<1e-6?new Vec3(0,Math.sin(pitch),-h):new Vec3(dir.x/flat*h,Math.sin(pitch),dir.z/flat*h);
    }
    /** Pure: a box is in the flame jet when the jet's axis, at the point nearest the box's middle, lies within half the jet's width of it */
    static boolean inJet(AABB box,Vec3 from,Vec3 dir,double length,double half){
        var c=box.getCenter();double t=Mth.clamp(c.subtract(from).dot(dir),0,length);return box.inflate(half).contains(from.add(dir.scale(t)));
    }
    /** Pure: a turret's status light: off with no power, empty with no shots, else ready */
    static String lightOf(boolean powered,int loaded){return !powered?"off":loaded<=0?"empty":"ready";}
    /** A turret's screen: its four ammo slots, loaded and emptied by hand, and how it stands */
    static final class TurretMenu extends net.minecraft.world.inventory.AbstractContainerMenu {
        static final int AMMO_X=53,AMMO_Y=108,INV_Y=140;
        final IItemHandler ammo;final BlockPos pos;final TurretEntity owner;final Line line;final int mk;
        final net.minecraft.world.inventory.DataSlot powered=net.minecraft.world.inventory.DataSlot.standalone(),loaded=net.minecraft.world.inventory.DataSlot.standalone();
        TurretMenu(int id,net.minecraft.world.entity.player.Inventory inv,IItemHandler ammo,BlockPos pos,TurretEntity owner,Line line,int mk){
            super(EternalDefense.TURRET_MENU.get(),id);this.ammo=ammo;this.pos=pos;this.owner=owner;this.line=line;this.mk=mk;
            for(int i=0;i<SLOTS;i++)addSlot(new net.minecraftforge.items.SlotItemHandler(ammo,i,AMMO_X+i*18,AMMO_Y){@Override public boolean mayPlace(ItemStack stack){return stack.is(Turrets.ammo(line));}});
            for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new net.minecraft.world.inventory.Slot(inv,9+row*9+col,8+col*18,INV_Y+row*18));
            for(int col=0;col<9;col++)addSlot(new net.minecraft.world.inventory.Slot(inv,col,8+col*18,INV_Y+58));
            addDataSlot(powered);addDataSlot(loaded);
        }
        static TurretMenu client(int id,net.minecraft.world.entity.player.Inventory inv,net.minecraft.network.FriendlyByteBuf buf){
            var pos=buf.readBlockPos();var line=Line.values()[buf.readByte()];int mk=buf.readByte();return new TurretMenu(id,inv,new ItemStackHandler(SLOTS),pos,null,line,mk);
        }
        @Override public void broadcastChanges(){if(owner!=null){powered.set(owner.powered?1:0);loaded.set(Math.min(32767,owner.loaded()));}super.broadcastChanges();}
        @Override public boolean stillValid(Player p){return !p.isSpectator()&&owner!=null&&p.level().getBlockEntity(pos)==owner&&p.distanceToSqr(Vec3.atCenterOf(pos))<=64;}
        @Override public ItemStack quickMoveStack(Player p,int index){
            if(index<0||index>=slots.size())return ItemStack.EMPTY;var slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;
            var stack=slot.getItem();var copy=stack.copy();
            if(index<SLOTS){if(!moveItemStackTo(stack,SLOTS,slots.size(),true))return ItemStack.EMPTY;}
            else if(!stack.is(Turrets.ammo(line))||!moveItemStackTo(stack,0,SLOTS,false))return ItemStack.EMPTY;
            if(stack.isEmpty())slot.set(ItemStack.EMPTY);else slot.setChanged();slot.onTake(p,stack);return copy;
        }
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
    /** A tower's block id: the line and its Mark, or the line alone when it has one Mark */
    static String id(Line line,int mk){return line.marks>1?line.id+"_turret_mk"+mk:line.id;}
    /** Pure: the gun's drawn pitch, inside what the mount allows (degrees) */
    static float clampPitch(float degrees){return Mth.clamp(degrees,-15,20);}
}
