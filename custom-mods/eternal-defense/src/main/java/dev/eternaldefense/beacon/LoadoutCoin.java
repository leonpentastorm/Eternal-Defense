package dev.eternaldefense.beacon;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import java.util.ArrayList;
import java.util.List;

/** The Loadout Coin: a sealed package of real gear (armor, off hand and hotbar), stored exactly as it was packed. */
final class LoadoutCoin extends Item {
    LoadoutCoin(){super(new Item.Properties().stacksTo(LoadoutRules.COIN_STACK).rarity(Rarity.UNCOMMON).fireResistant());}

    /** A coin holding these KIT_SLOTS stacks (empty stacks for empty slots). */
    static ItemStack create(List<ItemStack> kit){
        var tags=new ArrayList<CompoundTag>();for(var s:kit)tags.add(s.isEmpty()?new CompoundTag():s.save(new CompoundTag()));
        var coin=new ItemStack(EternalDefense.LOADOUT_COIN.get());coin.setTag(LoadoutRules.pack(tags));return coin;
    }
    /** The KIT_SLOTS stacks a coin holds, as new stacks. */
    static List<ItemStack> kit(ItemStack coin){
        var out=new ArrayList<ItemStack>();for(var t:LoadoutRules.unpack(coin.getTag()))out.add(t.isEmpty()?ItemStack.EMPTY:ItemStack.of(t));return out;
    }
    /** Null when these stacks may become a coin; otherwise a language key suffix under loadout.problem. */
    static String problem(List<ItemStack> kit,int limitBytes){
        var tags=new ArrayList<CompoundTag>();
        for(var s:kit){
            if(!s.isEmpty()&&carriesItems(s))return "container";
            tags.add(s.isEmpty()?new CompoundTag():s.save(new CompoundTag()));
        }
        return LoadoutRules.kitProblem(tags,limitBytes);
    }
    /** An item that holds other items through its inventory (backpacks and the like), not counting its own parts. */
    static boolean carriesItems(ItemStack s){
        var handler=s.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve();
        if(handler.isEmpty())return false;
        for(int i=0;i<handler.get().getSlots();i++)if(!handler.get().getStackInSlot(i).isEmpty())return true;
        return false;
    }
    static int limitBytes(){return DefenseConfig.LOADOUT_KIT_LIMIT_KB.get()*1024;}

    @Override public boolean isFoil(ItemStack stack){return false;}
    @Override public void appendHoverText(ItemStack stack,Level level,List<Component> lines,TooltipFlag flag){
        var kit=kit(stack);int armor=0,hotbar=0;boolean off=!kit.get(LoadoutRules.OFFHAND_SLOT).isEmpty();var names=new ArrayList<String>();
        for(int i=0;i<LoadoutRules.KIT_SLOTS;i++){var s=kit.get(i);if(s.isEmpty())continue;if(i<LoadoutRules.ARMOR)armor++;else if(i>=LoadoutRules.HOTBAR_FIRST)hotbar++;names.add(s.getHoverName().getString());}
        if(names.isEmpty()){lines.add(Component.translatable("tooltip.eternal_defense.loadout_coin.empty").withStyle(ChatFormatting.GRAY));return;}
        lines.add(Component.translatable("tooltip.eternal_defense.loadout_coin.summary",armor,Component.translatable(off?"tooltip.eternal_defense.loadout_coin.off_yes":"tooltip.eternal_defense.loadout_coin.off_no"),hotbar).withStyle(ChatFormatting.AQUA));
        int shown=Math.min(3,names.size());
        var list=String.join(", ",names.subList(0,shown));
        lines.add(names.size()>shown?Component.translatable("tooltip.eternal_defense.loadout_coin.items_more",list,names.size()-shown).withStyle(ChatFormatting.GRAY):Component.literal(list).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.eternal_defense.loadout_coin.use").withStyle(ChatFormatting.DARK_AQUA));
    }
}
