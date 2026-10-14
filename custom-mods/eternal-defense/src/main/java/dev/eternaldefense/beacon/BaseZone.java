package dev.eternaldefense.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import java.util.List;
import java.util.UUID;

/** Support gear */
final class BaseZone {
    private BaseZone(){}

    /** Null when pos is inside the zone of a standing beacon, otherwise a language key suffix under support.zone.. */
    static String problem(Level level,BlockPos pos){
        if(!(level instanceof ServerLevel server))return null;
        if(server.dimension()!=Level.OVERWORLD)return "overworld";
        var data=CampaignData.get(server.getServer().overworld());
        if(!data.installed()||!server.hasChunkAt(data.beacon)||!DefenseStructures.beacon(server,data.beacon))return "no_beacon";
        if(!data.inside(pos))return "outside";
        return null;
    }
    /** The protected zone of a standing beacon in this dimension as a box, or null (napalm keeps its fire out of it). */
    static net.minecraft.world.phys.AABB box(ServerLevel level){
        if(level.dimension()!=Level.OVERWORLD)return null;
        var data=CampaignData.get(level.getServer().overworld());
        if(!data.installed())return null;
        var b=data.beacon;int r=data.radius();
        return new net.minecraft.world.phys.AABB(b.getX()-r,b.getY()-data.below(),b.getZ()-r,b.getX()+r+1,b.getY()+data.above()+1,b.getZ()+r+1);
    }
    /** True when box overlaps the protected zone of a standing beacon in this dimension */
    static boolean touches(ServerLevel level,net.minecraft.world.phys.AABB box){
        if(level.dimension()!=Level.OVERWORLD)return false;
        var data=CampaignData.get(level.getServer().overworld());
        if(!data.installed())return false;
        var b=data.beacon;int r=data.radius();
        var zone=new net.minecraft.world.phys.AABB(b.getX()-r,b.getY()-data.below(),b.getZ()-r,b.getX()+r+1,b.getY()+data.above()+1,b.getZ()+r+1);
        return zone.intersects(box);
    }
    /** Message for gear that is placed but whose beacon is gone or too far away. */
    static boolean disabled(Level level,BlockPos pos,Player p){
        String problem=problem(level,pos);
        if(problem==null)return false;
        if(p!=null)p.displayClientMessage(Component.translatable("gui.eternal_defense.support.zone.disabled"),true);
        return true;
    }
    static void say(Player p,String key,Object... args){if(p!=null)p.displayClientMessage(Component.translatable("gui.eternal_defense.support."+key,args),true);}

    /** A block item that refuses to place outside the beacon zone (and applies the subclass' own rules). */
    static class ZoneItem extends BlockItem {
        ZoneItem(Block block){super(block,new Item.Properties());}
        /** Extra rules of the subclass; a language key suffix under support. */
        String extra(BlockPlaceContext c){return null;}
        @Override public InteractionResult place(BlockPlaceContext c){
            // Anything in the way of any cell is refused on both sides (the preview is red)
            var state=getBlock().getStateForPlacement(c);
            if(PlacementGuard.blocked(c,state)){if(state!=null&&!c.getLevel().isClientSide)say(c.getPlayer(),"no_room");return PlacementGuard.refuse(c);}
            if(!c.getLevel().isClientSide){
                String zone=problem(c.getLevel(),c.getClickedPos());
                if(zone!=null){say(c.getPlayer(),"zone."+zone);return PlacementGuard.refuse(c);}
                // No cell of a bigger piece (the cannon's 3 x 3 x 2) may stand in a Support Platform's return zone
                if(c.getLevel() instanceof net.minecraft.server.level.ServerLevel server)for(var cell:DefenseStructures.cells(c.getClickedPos(),state))if(ReturnZone.inside(server,cell)){say(c.getPlayer(),"keep_clear");return PlacementGuard.refuse(c);}
                String extra=extra(c);
                if(extra!=null){say(c.getPlayer(),extra);return PlacementGuard.refuse(c);}
            }
            return PlacementGuard.settled(c,super.place(c));
        }
        @Override public void appendHoverText(ItemStack stack,Level level,List<Component> lines,TooltipFlag flag){
            lines.add(Component.translatable("tooltip.eternal_defense.zone_only").withStyle(net.minecraft.ChatFormatting.GRAY));
            lines.add(Component.translatable("tooltip.eternal_defense.relocate").withStyle(net.minecraft.ChatFormatting.DARK_AQUA));
            var tag=stack.getTag();
            if(tag!=null&&tag.contains("Mk"))lines.add(Component.translatable(this instanceof TacticalBlocks.SatelliteItem?"tooltip.eternal_defense.satellite_mk":"tooltip.eternal_defense.platform_mk",tag.getInt("Mk")).withStyle(net.minecraft.ChatFormatting.GOLD));
        }
    }

    /** Beacon Recovery Shovel on support gear: picks it up for free (a platform keeps its Mk level and grid). */
    static boolean relocate(ServerPlayer p,ServerLevel level,BlockPos clicked){
        if(LogisticsHub.isHub(level.getBlockState(clicked)))return LogisticsHub.relocate(p,level,clicked);   // Its items wait for it in the saved data
        BlockPos root=DefenseStructures.anchor(level,clicked);var state=level.getBlockState(root);
        ItemStack stack;UUID owner=null;
        if(state.is(EternalDefense.EXCHANGE_SHOP.get())){stack=new ItemStack(EternalDefense.EXCHANGE_SHOP_ITEM.get());}
        else if(state.is(EternalDefense.SUPPORT_PLATFORM.get())&&level.getBlockEntity(root) instanceof SupportPlatform.PlatformEntity be){
            owner=be.owner;
            if(owner!=null&&!owner.equals(p.getUUID())){say(p,"not_yours",be.ownerName);return true;}
            stack=new ItemStack(EternalDefense.SUPPORT_PLATFORM_ITEM.get());
            var tag=stack.getOrCreateTag();tag.putInt("Mk",state.getValue(SupportPlatform.MK));
            var entity=new CompoundTag();entity.put("Grid",be.gridTag());tag.put("BlockEntityTag",entity);
            be.grid.clearQuietly();
        }
        else if(state.is(EternalDefense.COMMAND_TABLE.get())){stack=new ItemStack(EternalDefense.COMMAND_TABLE_ITEM.get());}
        else if(state.is(EternalDefense.ENCHANTING_TABLE.get())){stack=new ItemStack(EternalDefense.ENCHANTING_TABLE_ITEM.get());}   // Its Mk and unlocks live in the base's record
        else if(state.is(EternalDefense.SATELLITE_BEACON.get())){stack=new ItemStack(EternalDefense.SATELLITE_BEACON_ITEM.get());stack.getOrCreateTag().putInt("Mk",state.getValue(TacticalBlocks.MK));}   // For the tooltip only: the Mk itself is kept in HuntData
        else if(state.is(EternalDefense.SUPPORT_CANNON.get())&&level.getBlockEntity(root) instanceof SupportCannon.CannonEntity be){
            owner=be.owner;
            if(owner!=null&&!owner.equals(p.getUUID())){say(p,"not_yours",be.ownerName);return true;}
            stack=new ItemStack(EternalDefense.SUPPORT_CANNON_ITEM.get());
        }
        else return false;
        level.removeBlock(root,false);
        if(!p.getInventory().add(stack))p.drop(stack,false);
        say(p,"relocated");
        return true;
    }
}
