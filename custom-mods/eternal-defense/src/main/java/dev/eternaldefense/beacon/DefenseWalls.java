package dev.eternaldefense.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Sandbags, the barricade and barbed wire: defenses raiders go for first */
final class DefenseWalls {
    private DefenseWalls(){}
    /** Share of a raider's blow that gets through: sandbags stop shots, the barricade stops blows */
    static final float SANDBAG_SHOT=.25f,BARRICADE_MELEE=.4f;
    /** Barbed wire: damage to a mob in it or hitting it, more when it is live, and how much it slows */
    static final float WIRE_CUT=2,WIRE_SHOCK=5;
    static final double WIRE_SLOW=.45;
    static final int SHOCK_STUN=40;
    static ResourceKey<DamageType> key(String id){return ResourceKey.create(Registries.DAMAGE_TYPE,new ResourceLocation(EternalDefense.ID,id));}

    /** Pure: the share of a blow on this kind of defense that does damage */
    static float through(String kind,boolean ranged){return switch(kind){case "sandbag"->ranged?SANDBAG_SHOT:1;case "barricade"->ranged?1:BARRICADE_MELEE;default->1;};}
    static float through(BlockState s,boolean ranged){return through(s.getBlock() instanceof Wall w?w.kind:"",ranged);}

    /** Sandbags and the barricade: wall blocks (posts, sides and a 1.5-block collision) */
    static final class Wall extends WallBlock {
        final String kind;
        Wall(String kind,float hardness,SoundType sound){super(Properties.of().strength(hardness,12).sound(sound).noOcclusion());this.kind=kind;}
    }

    /** Barbed wire: low health, cuts and slows what crosses it, shocks when a shock box makes it live */
    static final class BarbedWire extends IronBarsBlock {
        static final BooleanProperty POWERED=BlockStateProperties.POWERED;
        BarbedWire(){super(Properties.of().strength(1,3).sound(SoundType.CHAIN).noCollission().noOcclusion().randomTicks());registerDefaultState(defaultBlockState().setValue(POWERED,false));}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){super.createBlockStateDefinition(b);b.add(POWERED);}
        @Override public VoxelShape getCollisionShape(BlockState s,BlockGetter l,BlockPos pos,CollisionContext c){return Shapes.empty();}
        @Override public void entityInside(BlockState s,Level l,BlockPos pos,Entity e){
            if(!(e instanceof LivingEntity living))return;
            living.makeStuckInBlock(s,new Vec3(WIRE_SLOW,.75,WIRE_SLOW));
            if(l instanceof ServerLevel server&&living instanceof Mob mob)cut(server,mob,s.getValue(POWERED));
        }
        /** Live wire throws sparks now and then */
        @Override public void animateTick(BlockState s,Level l,BlockPos pos,RandomSource r){
            if(!s.getValue(POWERED))return;
            for(int i=0;i<3;i++)if(r.nextInt(2)==0)l.addParticle(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,pos.getX()+.1+r.nextDouble()*.8,pos.getY()+.1+r.nextDouble()*.75,pos.getZ()+.1+r.nextDouble()*.8,(r.nextDouble()-.5)*.12,r.nextDouble()*.06,(r.nextDouble()-.5)*.12);
        }
        /** A live wire nobody keeps live any more (after a restart) goes dark */
        @Override public void randomTick(BlockState s,ServerLevel l,BlockPos pos,RandomSource random){if(s.getValue(POWERED)&&!PowerGrid.liveNow(l,pos))l.setBlock(pos,s.setValue(POWERED,false),3);}
    }
    /** The wire's bite on a mob in it or hitting it: a cut, or a shock and a stun when it is live */
    static void cut(ServerLevel l,Mob mob,boolean live){
        var type=l.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(key(live?"electric_shock":"barbed_wire"));
        if(mob.hurt(new DamageSource(type),live?WIRE_SHOCK:WIRE_CUT)&&live)mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,SHOCK_STUN,2));
    }
}
