package dev.createarsenal.beacon;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import java.util.List;

final class BeaconItems {
    static final class Placement extends BlockItem {
        Placement(Block block){super(block,new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));}
        @Override public InteractionResult useOn(UseOnContext context) {
            if(context.getLevel().isClientSide)net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,()->()->BeaconStartup.begin(context));
            if(context.getPlayer() instanceof ServerPlayer p)BeaconActions.requestPlacement(p,context);
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }
        InteractionResult confirmedPlace(UseOnContext context){return super.useOn(context);}
        @Override public void appendHoverText(ItemStack stack,Level level,List<Component> text,TooltipFlag flag){text.add(Component.literal("Plant to start a shared defense campaign. Confirmation required."));}
    }
    static final class Controller extends ShovelItem {
        Controller(){super(Tiers.IRON,1.5f,-3f,new Properties().stacksTo(1).rarity(Rarity.UNCOMMON));}
        @Override public InteractionResult useOn(UseOnContext context) {
            if(context.getPlayer() instanceof ServerPlayer p){
                if(ArsenalStructures.beacon(context.getLevel(),context.getClickedPos()))BeaconActions.requestRemoval(p);
                else return super.useOn(context);
            }
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }
        @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
            if(player instanceof ServerPlayer p)BeaconNetwork.open(p,"menu","");
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand),level.isClientSide);
        }
        @Override public void appendHoverText(ItemStack stack,Level level,List<Component> text,TooltipFlag flag){
            text.add(Component.literal("Hold: beacon HUD. Right-click: control panel."));text.add(Component.literal("Use on beacon: remove and reset. Confirmation required."));
        }
    }
    static final class Guide extends Item {
        Guide(){super(new Properties().stacksTo(1));}
        @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
            if(player instanceof ServerPlayer p)BeaconNetwork.open(p,"guide","");
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand),level.isClientSide);
        }
        @Override public void appendHoverText(ItemStack stack,Level level,List<Component> text,TooltipFlag flag){text.add(Component.literal("Right-click for short campaign instructions."));}
    }
}
