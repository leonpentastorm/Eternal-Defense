package dev.eternaldefense.beacon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.ArrayList;
import java.util.List;

/** The FOB Beacon's placement preview for every block of the mod. */
@Mod.EventBusSubscriber(modid=EternalDefense.ID,value=Dist.CLIENT)
public final class PlacementPreviewClient {
    private PlacementPreviewClient(){}
    private static final MultiBufferSource.BufferSource BUFFER=MultiBufferSource.immediate(new com.mojang.blaze3d.vertex.BufferBuilder(1<<17));
    /** The base zone's cyan. */
    static final int[] CYAN={70,235,221};
    /** A block placed now: where, which state, what the outline holds (block-space boxes) */
    record Ghost(List<BlockPos> cells,List<BlockState> states,AABB box,boolean beacon,boolean blocked){}

    /** The mod's own placeables, but the FOB Beacon (it has its own preview, with its village). */
    static boolean previewed(ItemStack stack){
        if(!(stack.getItem() instanceof BlockItem item))return false;
        if(stack.is(EternalDefense.FOB_BEACON_ITEM.get()))return false;
        String ns=BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
        return ns.equals(EternalDefense.ID)||ns.equals("eternal_defense_displays");
    }
    /** The ghost for the item in hand and the block looked at, or null. */
    static Ghost ghost(){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return null;
        if(!(mc.hitResult instanceof BlockHitResult hit)||hit.getType()!=HitResult.Type.BLOCK)return null;
        var hand=previewed(mc.player.getMainHandItem())?InteractionHand.MAIN_HAND:previewed(mc.player.getOffhandItem())?InteractionHand.OFF_HAND:null;
        if(hand==null)return null;
        var stack=mc.player.getItemInHand(hand);var item=(BlockItem)stack.getItem();
        var ctx=new BlockPlaceContext(mc.player,hand,stack,hit);
        try{
            if(item instanceof RailGates.GateItem){
                // A gate stands across the rail it is used on, as the item works it out
                var spot=RailGates.placingSpot(ctx);if(spot==null)return null;
                var facing=spot.facing();var anchor=spot.anchor();var column=RailGates.column(anchor,facing);
                var cells=new ArrayList<BlockPos>();var states=new ArrayList<BlockState>();
                cells.add(anchor);states.add(item.getBlock().defaultBlockState().setValue(RailGates.FACING,facing));
                if(item.getBlock() instanceof RailGates.GateBlock gate){cells.add(column);states.add(gate.kind.column().defaultBlockState().setValue(RailGates.FACING,facing));}
                for(int[] f:RailGates.Frame.PARTS){cells.add(RailGates.cell(anchor,facing,f[0],f[1]));states.add(EternalDefense.GATE_PART.get().defaultBlockState().setValue(RailGates.FACING,facing).setValue(RailGates.SIDE,f[0]));}
                AABB box=new AABB(anchor);for(var c:cells)box=box.minmax(new AABB(c));
                return new Ghost(cells,states,box,false,!RailGates.available(mc.level,anchor,facing));
            }
            if(item instanceof LogisticsHub.HubItem){
                // The Hub stands 3 x 3 x 3 on the spot looked at; its model is drawn from the middle block
                var anchor=LogisticsHub.HubItem.anchorFor(ctx);var state=item.getBlock().defaultBlockState().setValue(DefenseStructures.FACING,ctx.getHorizontalDirection().getOpposite());
                return new Ghost(List.of(anchor),List.of(state),new AABB(anchor).inflate(1),false,!LogisticsHub.HubItem.free(mc.level,anchor));
            }
            if(item instanceof LoadoutStation.StationItem){
                // The Loadout Station stands 3 x 3 x 3 above the spot looked at, facing the player
                var anchor=LoadoutStation.StationItem.anchorFor(ctx);var state=item.getBlock().defaultBlockState().setValue(DefenseStructures.FACING,LoadoutStation.StationItem.facingFor(ctx));
                return new Ghost(List.of(anchor),List.of(state),new AABB(anchor).inflate(1),false,!LoadoutStation.StationItem.free(mc.level,anchor));
            }
            // A spot the block refuses (no state) or with anything in the way of a cell is shown in red where it would stand
            var pos=ctx.getClickedPos();BlockState wanted=ctx.canPlace()?item.getBlock().getStateForPlacement(ctx):null;
            boolean blocked=PlacementGuard.blocked(ctx,wanted);
            BlockState state=wanted!=null?wanted:facing(item.getBlock().defaultBlockState(),ctx);
            AABB box=footprint(state,pos);
            return new Ghost(List.of(pos),List.of(state),box,stack.is(EternalDefense.BEACON_ITEM.get()),blocked);
        }catch(RuntimeException e){return null;}   // A block whose placement needs the server: no preview
    }
    /** A refused piece still faces the player, as it would when placed. */
    static BlockState facing(BlockState state,BlockPlaceContext ctx){
        var property=net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING;
        return state.hasProperty(property)?state.setValue(property,ctx.getHorizontalDirection().getOpposite()):state;
    }
    /** The space a block takes: the whole structure for the mod's multi-block ones, else its own shape. */
    static AABB footprint(BlockState state,BlockPos pos){
        var mc=Minecraft.getInstance();
        boolean structure=state.getBlock() instanceof KitchenBlock||state.getBlock() instanceof WeaponPlatform.Station||DefenseStructures.big(state)||DefenseStructures.tallSupport(state)||DefenseStructures.table(state)||DefenseStructures.enchanting(state);
        var shape=structure?DefenseStructures.full(state):state.getShape(mc.level,pos);
        return shape.isEmpty()?new AABB(pos):shape.bounds().move(pos);
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent e){
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)return;
        var ghost=ghost();if(ghost==null)return;
        var mc=Minecraft.getInstance();var camera=e.getCamera().getPosition();var pose=e.getPoseStack();
        pose.pushPose();pose.translate(-camera.x,-camera.y,-camera.z);
        VertexConsumer lines=BUFFER.getBuffer(RenderType.lines());int[] tint=ghost.blocked()?PlacementGuard.RED:CYAN;
        LevelRenderer.renderLineBox(pose,lines,ghost.box().inflate(.004),tint[0]/255f,tint[1]/255f,tint[2]/255f,.85f);
        if(ghost.beacon()&&!ghost.blocked())zone(lines,pose.last(),ghost.cells().get(0),mc.level.getGameTime()+e.getPartialTick());
        BUFFER.endBatch(RenderType.lines());
        var consumer=ghost.blocked()?FobOutlineClient.Faint.blocked(BUFFER.getBuffer(RenderType.translucent())):new FobOutlineClient.Faint(BUFFER.getBuffer(RenderType.translucent()),.45f);
        for(int i=0;i<ghost.cells().size();i++){
            var at=ghost.cells().get(i);var state=ghost.states().get(i);
            pose.pushPose();pose.translate(at.getX(),at.getY(),at.getZ());
            float[] rgb=ghostTint(ghost.blocked());
            // No render layer given: a multipart model (walls, wire) then draws every part, whatever its layer
            mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(),consumer,state,mc.getBlockRenderer().getBlockModel(state),
                rgb[0],rgb[1],rgb[2],LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,ModelData.EMPTY,null);
            // The moving parts are no longer in the block's model; the ghost shows them at rest
            if(Animations.animated(state))Animations.drawRest(state,pose,consumer,rgb[0],rgb[1],rgb[2]);
            pose.popPose();
        }
        BUFFER.endBatch(RenderType.translucent());
        pose.popPose();
    }
    /** The ghost's tint: the model's own colours washed toward cyan */
    static float[] ghostTint(boolean blocked){return PlacementGuard.tint(blocked,CYAN);}
    /** The base zone a Defense Beacon planted here would get, drawn like a planted one. */
    static void zone(VertexConsumer v,PoseStack.Pose p,BlockPos at,double time){
        int[] size=BeaconClient.zoneSize();int r=size[0];
        double x0=at.getX()-r,x1=at.getX()+r+1,z0=at.getZ()-r,z1=at.getZ()+r+1,ground=at.getY(),bottom=ground-size[1],top=ground+size[2]+1;
        for(double y:new double[]{bottom,ground,top}){line(v,p,x0,y,z0,x1,y,z0,255);line(v,p,x1,y,z0,x1,y,z1,255);line(v,p,x1,y,z1,x0,y,z1,255);line(v,p,x0,y,z1,x0,y,z0,255);}
        for(double x:new double[]{x0,x1})for(double z:new double[]{z0,z1})line(v,p,x,bottom,z,x,top,z,255);
        for(double i=0;i<=2*r+1;i++){
            line(v,p,x0+i,ground,z0,x0+i,ground,z0+.35,230);line(v,p,x0+i,ground,z1,x0+i,ground,z1-.35,230);
            line(v,p,x0,ground,z0+i,x0+.35,ground,z0+i,230);line(v,p,x1,ground,z0+i,x1-.35,ground,z0+i,230);
        }
        double scan=bottom+(time*.12)%(top-bottom);
        line(v,p,x0,scan,z0,x1,scan,z0,200);line(v,p,x1,scan,z0,x1,scan,z1,200);line(v,p,x1,scan,z1,x0,scan,z1,200);line(v,p,x0,scan,z1,x0,scan,z0,200);
    }
    private static void line(VertexConsumer v,PoseStack.Pose p,double x,double y,double z,double xx,double yy,double zz,int alpha){
        float dx=(float)(xx-x),dy=(float)(yy-y),dz=(float)(zz-z),length=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);if(length==0)return;
        v.vertex(p.pose(),(float)x,(float)y,(float)z).color(CYAN[0],CYAN[1],CYAN[2],alpha).normal(p.normal(),dx/length,dy/length,dz/length).endVertex();
        v.vertex(p.pose(),(float)xx,(float)yy,(float)zz).color(CYAN[0],CYAN[1],CYAN[2],alpha).normal(p.normal(),dx/length,dy/length,dz/length).endVertex();
    }
}
