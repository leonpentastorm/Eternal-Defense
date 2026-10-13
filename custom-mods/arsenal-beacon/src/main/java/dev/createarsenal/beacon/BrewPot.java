package dev.createarsenal.beacon;

import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.*;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/**
 * 0.0.35 (owner): the Brew Pot, the Cook Pot's sibling for drinks (the owner's copper kettle, 1 x 1 x 2). It is linked to a Drink Station
 * within 8 blocks and holds ONE serving: right-click it with a Paper Cup and you drink it at once (the cup is used up, as the bowl at a Cook Pot; 0.0.37). Its
 * chalkboard shows the drink, its effects and its time.
 */
final class BrewPot {
    private BrewPot(){}
    static final class PotBlock extends KitchenBlock {
        PotBlock(){super(false,CookPot.PotBlock.LAMP_LIGHT);}
        @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new PotEntity(pos,state);}
        @Override public void setPlacedBy(net.minecraft.world.level.Level l,BlockPos pos,BlockState state,net.minecraft.world.entity.LivingEntity who,net.minecraft.world.item.ItemStack stack){super.setPlacedBy(l,pos,state,who,stack);if(!l.isClientSide&&l.getBlockEntity(pos) instanceof PotEntity pot)pot.connect();}
        @Override public void onRemove(BlockState s,net.minecraft.world.level.Level l,BlockPos p,BlockState next,boolean moving){if(!s.is(next.getBlock())&&l.getBlockEntity(p) instanceof PotEntity pot)pot.unlink();super.onRemove(s,l,p,next,moving);}
    }
    static final class PotEntity extends BlockEntity {
        DrinkData drink;BlockPos linkedStation;UUID stationIdentity;
        PotEntity(BlockPos pos,BlockState state){super(ArsenalBeacon.BREW_POT_ENTITY.get(),pos,state);}
        boolean belongs(DrinkStation.StationEntity s){return s.getBlockPos().equals(linkedStation)&&s.identity.equals(stationIdentity);}
        boolean waitingForStation(){return linkedStation!=null&&level!=null&&!level.hasChunkAt(linkedStation);}
        DrinkStation.StationEntity validLink(){
            if(linkedStation==null||level==null||waitingForStation())return null;
            if(level.getBlockEntity(linkedStation) instanceof DrinkStation.StationEntity s&&belongs(s)&&s.inRange(worldPosition)&&s.links.contains(worldPosition))return s;
            unlink();return null;
        }
        void unlink(){
            if(level!=null&&!level.isClientSide&&linkedStation!=null&&level.hasChunkAt(linkedStation)&&level.getBlockEntity(linkedStation) instanceof DrinkStation.StationEntity s&&belongs(s)){s.links.remove(worldPosition);s.changed();}
            linkedStation=null;stationIdentity=null;changed();
        }
        DrinkStation.StationEntity connect(){
            var old=validLink();if(old!=null||waitingForStation())return old;
            var stations=new ArrayList<DrinkStation.StationEntity>();int r=DrinkRules.LINK_RANGE;
            for(var p:BlockPos.betweenClosed(worldPosition.offset(-r,-r,-r),worldPosition.offset(r,r,r)))if(level.hasChunkAt(p)&&level.getBlockEntity(p) instanceof DrinkStation.StationEntity s&&s.inRange(worldPosition))stations.add(s);
            stations.sort(Comparator.<DrinkStation.StationEntity>comparingDouble(s->worldPosition.distSqr(s.getBlockPos())).thenComparingLong(s->s.getBlockPos().asLong()));
            for(var s:stations)if(s.link(this))return s;return null;
        }
        boolean empty(){return drink==null;}
        /** {@code replace}: the brewer confirmed that the drink still in the pot is poured away. */
        boolean fill(DrinkStation.StationEntity station,DrinkData next,boolean replace){if(!(replace||empty())||!belongs(station)||validLink()!=station||next.latte())return false;drink=next;changed();return true;}
        void changed(){
            setChanged();if(level==null||level.isClientSide)return;level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);
            // the station's taps show the pots' drinks: let its model know
            if(linkedStation!=null&&level.hasChunkAt(linkedStation))level.sendBlockUpdated(linkedStation,level.getBlockState(linkedStation),level.getBlockState(linkedStation),3);
        }
        void use(ServerPlayer p,InteractionHand hand){
            if(p.isSpectator()||BaseZone.problem(level,worldPosition)!=null||p.distanceToSqr(worldPosition.getX()+.5,worldPosition.getY()+.5,worldPosition.getZ()+.5)>64)return;
            var station=connect();
            if(!p.getItemInHand(hand).is(ArsenalBeacon.PAPER_CUP.get())){
                if(empty())p.sendSystemMessage(Component.translatable("gui.arsenal_beacon.drinks.pot_empty"));
                else{p.sendSystemMessage(Component.translatable("gui.arsenal_beacon.drinks.pot_status",drink.name(),DrinkRules.minutes(drink.duration())));for(var part:drink.parts())p.sendSystemMessage(part.describe());}
                if(station==null)p.sendSystemMessage(Component.translatable("gui.arsenal_beacon.drinks.no_station"));return;
            }
            if(station==null||BaseZone.problem(level,station.getBlockPos())!=null){p.displayClientMessage(Component.translatable("gui.arsenal_beacon.drinks.no_station"),true);return;}
            if(empty()){p.displayClientMessage(Component.translatable("gui.arsenal_beacon.drinks.pot_empty"),true);return;}
            // one serving: drunk at once; the cup is used up (0.0.37, owner: a sink for cups; creative keeps it, as vanilla)
            var served=drink;drink=null;changed();PlayerDrinks.drink(p,served);
            if(!p.getAbilities().instabuild)p.getItemInHand(hand).shrink(1);
            level.playSound(null,p.blockPosition(),SoundEvents.GENERIC_DRINK,SoundSource.PLAYERS,.8f,1f);
            p.displayClientMessage(Component.translatable("gui.arsenal_beacon.drinks.served",served.name(),DrinkRules.minutes(served.duration())),true);
        }
        @Override protected void saveAdditional(CompoundTag n){super.saveAdditional(n);if(drink!=null)n.put("Drink",drink.save());if(linkedStation!=null)n.putLong("Station",linkedStation.asLong());if(stationIdentity!=null)n.putUUID("StationIdentity",stationIdentity);}
        @Override public void load(CompoundTag n){super.load(n);drink=n.contains("Drink")?DrinkData.load(n.getCompound("Drink")):null;if(drink!=null&&drink.latte())drink=null;linkedStation=n.contains("Station")?BlockPos.of(n.getLong("Station")):null;stationIdentity=n.hasUUID("StationIdentity")?n.getUUID("StationIdentity"):null;}
        @Override public CompoundTag getUpdateTag(){var n=new CompoundTag();saveAdditional(n);return n;}
        @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
        @Override public net.minecraft.world.phys.AABB getRenderBoundingBox(){return new net.minecraft.world.phys.AABB(worldPosition).inflate(2);}
    }
}
