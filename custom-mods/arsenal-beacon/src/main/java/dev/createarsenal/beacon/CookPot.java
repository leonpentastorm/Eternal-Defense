package dev.createarsenal.beacon;

import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

final class CookPot {
    private CookPot(){}
    static final class PotBlock extends KitchenBlock {
        /** The lamp bar over the chalkboard gives some light, so the menu can be read in a dark kitchen. */
        static final int LAMP_LIGHT=10;
        PotBlock(){super(false,LAMP_LIGHT);}
        @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new PotEntity(pos,state);}
        @Override public void setPlacedBy(net.minecraft.world.level.Level l,BlockPos pos,BlockState state,net.minecraft.world.entity.LivingEntity who,net.minecraft.world.item.ItemStack stack){super.setPlacedBy(l,pos,state,who,stack);if(!l.isClientSide&&l.getBlockEntity(pos) instanceof PotEntity pot)pot.connect();}
        @Override public void onRemove(BlockState s,net.minecraft.world.level.Level l,BlockPos p,BlockState next,boolean moving){if(!s.is(next.getBlock())&&l.getBlockEntity(p) instanceof PotEntity pot)pot.unlink();super.onRemove(s,l,p,next,moving);}
    }
    static final class PotEntity extends BlockEntity {
        MealData stew;int servings;BlockPos linkedHall;UUID hallIdentity;
        PotEntity(BlockPos pos,BlockState state){super(ArsenalBeacon.COOK_POT_ENTITY.get(),pos,state);}
        boolean belongs(MessHall.HallEntity h){return h.getBlockPos().equals(linkedHall)&&h.identity.equals(hallIdentity);}
        boolean waitingForHall(){return linkedHall!=null&&level!=null&&!level.hasChunkAt(linkedHall);}
        MessHall.HallEntity validLink(){
            if(linkedHall==null||level==null||waitingForHall())return null;
            if(level.getBlockEntity(linkedHall) instanceof MessHall.HallEntity h&&belongs(h)&&h.inRange(worldPosition)&&h.links.contains(worldPosition))return h;
            unlink();return null;
        }
        void unlink(){
            if(level!=null&&!level.isClientSide&&linkedHall!=null&&level.hasChunkAt(linkedHall)&&level.getBlockEntity(linkedHall) instanceof MessHall.HallEntity h&&belongs(h)){h.links.remove(worldPosition);h.setChanged();}
            linkedHall=null;hallIdentity=null;changed();
        }
        MessHall.HallEntity connect(){
            var old=validLink();if(old!=null||waitingForHall())return old;
            var halls=new ArrayList<MessHall.HallEntity>();int r=MealRules.LINK_RANGE;
            for(var p:BlockPos.betweenClosed(worldPosition.offset(-r,-r,-r),worldPosition.offset(r,r,r)))if(level.hasChunkAt(p)&&level.getBlockEntity(p) instanceof MessHall.HallEntity h&&h.inRange(worldPosition))halls.add(h);
            halls.sort(Comparator.<MessHall.HallEntity>comparingDouble(h->worldPosition.distSqr(h.getBlockPos())).thenComparingLong(h->h.getBlockPos().asLong()));
            for(var h:halls)if(h.link(this))return h;return null;
        }
        boolean empty(){return stew==null||servings<=0;}
        boolean fill(MessHall.HallEntity hall,MealData meal,int count){return fill(hall,meal,count,false);}
        /** {@code replace}: the cook confirmed that the stew still in the pot is thrown away. */
        boolean fill(MessHall.HallEntity hall,MealData meal,int count,boolean replace){if(!(replace||empty())||!belongs(hall)||validLink()!=hall||!meal.stew())return false;stew=meal;servings=count;changed();return true;}
        void changed(){setChanged();if(level!=null&&!level.isClientSide)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}
        void use(ServerPlayer p,InteractionHand hand){
            if(p.isSpectator()||BaseZone.problem(level,worldPosition)!=null||p.distanceToSqr(worldPosition.getX()+.5,worldPosition.getY()+.5,worldPosition.getZ()+.5)>64)return;
            var hall=connect();
            if(!p.getItemInHand(hand).is(Items.BOWL)){p.sendSystemMessage(empty()?Component.translatable("gui.arsenal_beacon.kitchen.pot_empty"):Component.translatable("gui.arsenal_beacon.kitchen.pot_status",stew.name(),servings));if(!empty())for(var b:stew.bonuses())p.sendSystemMessage(b.description(false));if(hall==null)p.sendSystemMessage(Component.translatable("gui.arsenal_beacon.kitchen.no_hall"));return;}
            if(hall==null||BaseZone.problem(level,hall.getBlockPos())!=null){p.displayClientMessage(Component.translatable("gui.arsenal_beacon.kitchen.no_hall"),true);return;}
            if(empty()){p.displayClientMessage(Component.translatable("gui.arsenal_beacon.kitchen.pot_empty"),true);return;}
            // Direct serving consumes the stew immediately and returns the reusable empty bowl.
            PlayerMeals.eat(p,stew);p.getFoodData().eat(10,.8f);servings--;if(servings==0)stew=null;changed();
            p.displayClientMessage(Component.translatable("gui.arsenal_beacon.kitchen.served",servings),true);
        }
        @Override protected void saveAdditional(CompoundTag n){super.saveAdditional(n);n.putInt("Servings",servings);if(stew!=null)n.put("Stew",stew.save());if(linkedHall!=null)n.putLong("Hall",linkedHall.asLong());if(hallIdentity!=null)n.putUUID("HallIdentity",hallIdentity);}
        @Override public void load(CompoundTag n){super.load(n);stew=MealData.load(n.getCompound("Stew"));servings=Math.max(0,Math.min(64,n.getInt("Servings")));if(stew==null||!stew.stew()||servings==0){stew=null;servings=0;}linkedHall=n.contains("Hall")?BlockPos.of(n.getLong("Hall")):null;hallIdentity=n.hasUUID("HallIdentity")?n.getUUID("HallIdentity"):null;}
        @Override public CompoundTag getUpdateTag(){var n=new CompoundTag();saveAdditional(n);return n;}
        @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
        @Override public net.minecraft.world.phys.AABB getRenderBoundingBox(){return new net.minecraft.world.phys.AABB(worldPosition).inflate(2);}
    }
}
