package dev.eternaldefense.displays;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import java.util.List;

/** Weapon Display Tuner: opens the tuning screen of a stand that holds a gun. */
public final class DisplayTuner extends Item {
    DisplayTuner(){super(new Item.Properties().stacksTo(1));}
    static boolean holds(Player p){return p.getMainHandItem().is(GunDisplays.TUNER.get())||p.getOffhandItem().is(GunDisplays.TUNER.get());}
    /** Use on a stand: the tuning screen on the client, a hint when the stand is empty. */
    static InteractionResult tune(Level level,BlockPos clicked,Player player){
        var anchor=DisplayRacks.anchor(level,clicked);
        if(!(level.getBlockEntity(anchor) instanceof DisplayRacks.DisplayEntity display))return InteractionResult.PASS;
        if(display.gun().isEmpty()){if(!level.isClientSide)player.displayClientMessage(Component.translatable("message.eternal_defense_displays.tuner.empty"),true);}
        else if(level.isClientSide)DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->TunerScreen.open(anchor));
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public InteractionResult useOn(UseOnContext c){
        if(c.getPlayer()==null||c.getHand()!=InteractionHand.MAIN_HAND||!(c.getLevel().getBlockState(c.getClickedPos()).getBlock() instanceof DisplayRacks.Rack))return InteractionResult.PASS;
        return tune(c.getLevel(),c.getClickedPos(),c.getPlayer());
    }
    @Override public void appendHoverText(ItemStack stack,Level level,List<Component> lines,TooltipFlag flag){
        lines.add(Component.translatable("tooltip.eternal_defense_displays.tuner.use").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.eternal_defense_displays.tuner.keeps").withStyle(ChatFormatting.DARK_AQUA));
    }
}
