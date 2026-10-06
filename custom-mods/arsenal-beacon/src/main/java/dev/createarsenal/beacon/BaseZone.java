package dev.createarsenal.beacon;

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

/**
 * Support gear (platform, cannon, Exchange Shop) only exists inside the live zone of the Defense Beacon. It cannot be placed
 * outside it, stops working while the beacon is missing, and is moved for free with the Beacon Recovery Shovel.
 */
final class BaseZone {
    private BaseZone(){}

    /** Null when {@code pos} is inside the zone of a standing beacon, otherwise a language key suffix under {@code support.zone.}. */
    static String problem(Level level,BlockPos pos){
        if(!(level instanceof ServerLevel server))return null;
        if(server.dimension()!=Level.OVERWORLD)return "overworld";
        var data=CampaignData.get(server.getServer().overworld());
        if(!data.installed()||!server.hasChunkAt(data.beacon)||!ArsenalStructures.beacon(server,data.beacon))return "no_beacon";
        if(!data.inside(pos))return "outside";
        return null;
    }
    /** Message for gear that is placed but whose beacon is gone or too far away. */
    static boolean disabled(Level level,BlockPos pos,Player p){
        String problem=problem(level,pos);
        if(problem==null)return false;
        if(p!=null)p.displayClientMessage(Component.translatable("gui.arsenal_beacon.support.zone.disabled"),true);
        return true;
    }
    static void say(Player p,String key,Object... args){if(p!=null)p.displayClientMessage(Component.translatable("gui.arsenal_beacon.support."+key,args),true);}

    /** A block item that refuses to place outside the beacon zone (and applies the subclass' own rules). */
    static class ZoneItem extends BlockItem {
        ZoneItem(Block block){super(block,new Item.Properties());}
        /** Extra rules of the subclass; a language key suffix under {@code support.} or null. */
        String extra(BlockPlaceContext c){return null;}
        @Override public InteractionResult place(BlockPlaceContext c){
            if(!c.getLevel().isClientSide){
                String zone=problem(c.getLevel(),c.getClickedPos());
                if(zone!=null){say(c.getPlayer(),"zone."+zone);return InteractionResult.FAIL;}
                var state=getBlock().defaultBlockState();
                if((ArsenalStructures.big(state)||ArsenalStructures.tallSupport(state))&&!ArsenalStructures.available(c.getLevel(),c.getClickedPos(),state)){say(c.getPlayer(),"no_room");return InteractionResult.FAIL;}
                String extra=extra(c);
                if(extra!=null){say(c.getPlayer(),extra);return InteractionResult.FAIL;}
            }
            return super.place(c);
        }
        @Override public void appendHoverText(ItemStack stack,Level level,List<Component> lines,TooltipFlag flag){
            lines.add(Component.translatable("tooltip.arsenal_beacon.zone_only").withStyle(net.minecraft.ChatFormatting.GRAY));
            lines.add(Component.translatable("tooltip.arsenal_beacon.relocate").withStyle(net.minecraft.ChatFormatting.DARK_AQUA));
            var tag=stack.getTag();
            if(tag!=null&&tag.contains("Mk"))lines.add(Component.translatable("tooltip.arsenal_beacon.platform_mk",tag.getInt("Mk")).withStyle(net.minecraft.ChatFormatting.GOLD));
        }
    }

    /** Beacon Recovery Shovel on support gear: picks it up for free (a platform keeps its Mk level and grid). */
    static boolean relocate(ServerPlayer p,ServerLevel level,BlockPos clicked){
        BlockPos root=ArsenalStructures.anchor(level,clicked);var state=level.getBlockState(root);
        ItemStack stack;UUID owner=null;
        if(state.is(ArsenalBeacon.EXCHANGE_SHOP.get())){stack=new ItemStack(ArsenalBeacon.EXCHANGE_SHOP_ITEM.get());}
        else if(state.is(ArsenalBeacon.SUPPORT_PLATFORM.get())&&level.getBlockEntity(root) instanceof SupportPlatform.PlatformEntity be){
            owner=be.owner;
            if(owner!=null&&!owner.equals(p.getUUID())){say(p,"not_yours",be.ownerName);return true;}
            stack=new ItemStack(ArsenalBeacon.SUPPORT_PLATFORM_ITEM.get());
            var tag=stack.getOrCreateTag();tag.putInt("Mk",state.getValue(SupportPlatform.MK));
            var entity=new CompoundTag();entity.put("Grid",be.gridTag());tag.put("BlockEntityTag",entity);
            be.grid.clearQuietly();
        }
        else if(state.is(ArsenalBeacon.SUPPORT_CANNON.get())&&level.getBlockEntity(root) instanceof SupportCannon.CannonEntity be){
            owner=be.owner;
            if(owner!=null&&!owner.equals(p.getUUID())){say(p,"not_yours",be.ownerName);return true;}
            stack=new ItemStack(ArsenalBeacon.SUPPORT_CANNON_ITEM.get());
        }
        else return false;
        level.removeBlock(root,false);
        if(!p.getInventory().add(stack))p.drop(stack,false);
        say(p,"relocated");
        return true;
    }
}
