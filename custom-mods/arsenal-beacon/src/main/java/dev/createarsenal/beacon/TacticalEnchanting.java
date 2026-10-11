package dev.createarsenal.beacon;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.PacketDistributor;
import java.util.*;

/**
 * The Tactical Enchanting Table (0.0.27, FOB slice 4), the owner's enchanting bench model ({@code models/block/enchanting_platform.json}): 2 wide,
 * 2 tall, 1 deep like the weapon benches; its anchor is the front right block seen from the front, the second column clockwise of its facing,
 * the three other cells invisible structure parts. One per base, in the beacon zone, moved with the Recovery Shovel.
 * <p>A player puts a piece of gear in its slot; the table lists every vanilla enchant (never a curse; the four treasure enchants only once a
 * Warlord Seal unlocks them) that applies, each level with its Ardent Energy price, and applies the one bought directly: no experience, no
 * lapis, no book, no anvil. Higher levels need ACTIVE FOBs ({@link EnchantRules#fobsFor}). Vanilla enchanting, librarians and anvils are untouched.
 */
final class TacticalEnchanting {
    private TacticalEnchanting(){}
    static final DirectionProperty FACING=HorizontalDirectionalBlock.FACING;
    /** Menu buttons: the table's next Mk; unlocking treasure enchant i (one Warlord Seal); buying enchant c (catalogue index) at level l. */
    static final int UPGRADE=1,UNLOCK=10,BUY=1000;
    static int buyButton(int index,int level){return BUY+index*8+Math.max(1,Math.min(7,level));}

    // ---- the team's record ---------------------------------------------------------------------------------------------------------------------
    /** One table per base, its Mk (0 to 3: the discount) and the Warlord Seals spent on each treasure enchant; shared by every player. */
    static final class EnchantData extends net.minecraft.world.level.saveddata.SavedData {
        static final String NAME="arsenal_beacon_enchanting_v1";
        BlockPos table;int mk;final Map<String,Integer> seals=new TreeMap<>();
        static EnchantData get(ServerLevel any){return any.getServer().overworld().getDataStorage().computeIfAbsent(EnchantData::load,EnchantData::new,NAME);}
        static EnchantData load(CompoundTag n){
            var d=new EnchantData();if(n.contains("table"))d.table=BlockPos.of(n.getLong("table"));
            d.mk=Math.max(0,Math.min(EnchantRules.MAX_MK,n.getInt("mk")));
            var s=n.getCompound("seals");for(String id:EnchantRules.TREASURE)if(s.getInt(id)>0)d.seals.put(id,s.getInt(id));
            return d;
        }
        @Override public CompoundTag save(CompoundTag n){
            if(table!=null)n.putLong("table",table.asLong());n.putInt("mk",mk);
            var s=new CompoundTag();seals.forEach(s::putInt);n.put("seals",s);return n;
        }
        int seals(String id){return seals.getOrDefault(id,0);}
    }

    // ---- the block --------------------------------------------------------------------------------------------------------------------------------
    static final class TableBlock extends Block {
        TableBlock(){super(Properties.of().strength(3,6).noOcclusion().lightLevel(s->7).sound(SoundType.WOOD));registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH));}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING);}
        @Override public BlockState getStateForPlacement(BlockPlaceContext c){return defaultBlockState().setValue(FACING,c.getHorizontalDirection().getOpposite());}
        @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return ArsenalStructures.cell(s,0,0,0);}
        @Override public PushReaction getPistonPushReaction(BlockState s){return PushReaction.BLOCK;}
        @Override public void onPlace(BlockState s,Level l,BlockPos pos,BlockState old,boolean moving){super.onPlace(s,l,pos,old,moving);if(l instanceof ServerLevel)ArsenalStructures.install(l,pos,s);}
        @Override public void tick(BlockState s,ServerLevel l,BlockPos pos,net.minecraft.util.RandomSource random){ArsenalStructures.install(l,pos,s);}
        @Override public void setPlacedBy(Level l,BlockPos pos,BlockState s,LivingEntity who,ItemStack stack){super.setPlacedBy(l,pos,s,who,stack);if(l instanceof ServerLevel server){var d=EnchantData.get(server);d.table=pos.immutable();d.setDirty();}}
        @Override public void onRemove(BlockState s,Level l,BlockPos pos,BlockState next,boolean moving){
            if(!s.is(next.getBlock())){ArsenalStructures.remove(l,pos);if(l instanceof ServerLevel server){var d=EnchantData.get(server);if(pos.equals(d.table)){d.table=null;d.setDirty();}}}
            super.onRemove(s,l,pos,next,moving);
        }
        @Override public InteractionResult use(BlockState s,Level l,BlockPos pos,Player p,InteractionHand hand,BlockHitResult hit){
            if(p instanceof ServerPlayer sp&&!BaseZone.disabled(l,pos,sp))open(sp,pos);
            return InteractionResult.sidedSuccess(l.isClientSide);
        }
    }
    /** One table per base, in the beacon zone. */
    static final class TableItem extends BaseZone.ZoneItem {
        TableItem(Block block){super(block);}
        @Override public void appendHoverText(ItemStack stack,Level level,List<Component> lines,TooltipFlag flag){
            lines.add(Component.translatable("tooltip.arsenal_beacon.tactical_enchanting_table").withStyle(ChatFormatting.GRAY));super.appendHoverText(stack,level,lines,flag);
        }
        @Override String extra(BlockPlaceContext c){
            if(!(c.getLevel() instanceof ServerLevel server))return null;var d=EnchantData.get(server);
            return d.table!=null&&server.hasChunkAt(d.table)&&server.getBlockState(d.table).is(ArsenalBeacon.ENCHANTING_TABLE.get())?"only_one_enchanting":null;
        }
    }
    /** The Warlord Seal: paid by a cleared Warlord hunt, spent at the table to unlock a treasure enchant for the whole team. */
    static final class SealItem extends Item {
        SealItem(){super(new Properties().stacksTo(16).rarity(Rarity.EPIC));}
        @Override public boolean isFoil(ItemStack stack){return true;}
        @Override public void appendHoverText(ItemStack stack,Level level,List<Component> lines,TooltipFlag flag){lines.add(Component.translatable("tooltip.arsenal_beacon.warlord_seal").withStyle(ChatFormatting.GRAY));}
    }

    // ---- the menu: one gear slot and the player's inventory ---------------------------------------------------------------------------------------
    static final class EnchantMenu extends AbstractContainerMenu {
        /** Slot positions inside the screen (the screen is a fixed 384 x 236). */
        static final int GEAR_X=26,GEAR_Y=48,INV_X=12,INV_Y=152;
        final BlockPos pos;CompoundTag data;final SimpleContainer gear=new SimpleContainer(1);
        EnchantMenu(int id,Inventory inv,BlockPos pos,CompoundTag data){
            super(ArsenalBeacon.ENCHANT_MENU.get(),id);this.pos=pos;this.data=data;
            addSlot(new Slot(gear,0,GEAR_X,GEAR_Y){
                @Override public boolean mayPlace(ItemStack stack){return enchantable(stack);}
                @Override public int getMaxStackSize(){return 1;}
            });
            for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inv,9+row*9+col,INV_X+col*18,INV_Y+row*18));
            for(int col=0;col<9;col++)addSlot(new Slot(inv,col,INV_X+col*18,INV_Y+58));
        }
        static EnchantMenu client(int id,Inventory inv,FriendlyByteBuf buf){var pos=buf.readBlockPos();var n=buf.readNbt();return new EnchantMenu(id,inv,pos,n==null?new CompoundTag():n);}
        @Override public boolean clickMenuButton(Player p,int id){if(p instanceof ServerPlayer sp)button(sp,this,id);return true;}
        @Override public void removed(Player p){super.removed(p);if(p instanceof ServerPlayer)clearContainer(p,gear);}   // the gear goes back to its owner
        @Override public boolean stillValid(Player p){return p.level().getBlockState(pos).is(ArsenalBeacon.ENCHANTING_TABLE.get())&&p.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<=64;}
        @Override public ItemStack quickMoveStack(Player p,int index){
            if(index<0||index>=slots.size())return ItemStack.EMPTY;var slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;
            var stack=slot.getItem();var copy=stack.copy();
            if(index==0){if(!moveItemStackTo(stack,1,slots.size(),true))return ItemStack.EMPTY;}
            else if(!enchantable(stack)||slots.get(0).hasItem()||!moveItemStackTo(stack,0,1,false))return ItemStack.EMPTY;
            if(stack.isEmpty())slot.set(ItemStack.EMPTY);else slot.setChanged();slot.onTake(p,stack);return copy;
        }
    }

    // ---- what the table offers --------------------------------------------------------------------------------------------------------------------
    private static List<Enchantment> catalogue;
    /** Every enchant the table can sell, the same list on the client and the server: vanilla's, never a curse, treasure ones only the four of {@link EnchantRules#TREASURE}; by id. */
    static List<Enchantment> catalogue(){
        if(catalogue==null){
            var list=new ArrayList<Enchantment>();
            for(var e:BuiltInRegistries.ENCHANTMENT){
                var id=BuiltInRegistries.ENCHANTMENT.getKey(e);
                if(id==null||!id.getNamespace().equals("minecraft")||e.isCurse())continue;
                if(e.isTreasureOnly()&&!EnchantRules.TREASURE.contains(id.toString()))continue;
                list.add(e);
            }
            list.sort(Comparator.comparing(e->BuiltInRegistries.ENCHANTMENT.getKey(e).toString()));
            catalogue=List.copyOf(list);
        }
        return catalogue;
    }
    static String id(Enchantment e){return String.valueOf(BuiltInRegistries.ENCHANTMENT.getKey(e));}
    static boolean treasure(Enchantment e){return EnchantRules.TREASURE.contains(id(e));}
    /** Can this go in the table's slot: a piece of gear that at least one of the table's enchants applies to (never a book). */
    static boolean enchantable(ItemStack stack){
        if(stack.isEmpty()||stack.is(Items.BOOK)||stack.is(Items.ENCHANTED_BOOK)||stack.getCount()!=1)return false;
        for(var e:catalogue())if(e.canEnchant(stack))return true;
        return false;
    }
    static EnchantRules.Rarity rarity(Enchantment e){return EnchantRules.Rarity.values()[Math.min(3,e.getRarity().ordinal())];}
    /** The price of level {@code level} of {@code e} at a table of Mk {@code mk}, in this edition. */
    static int price(Enchantment e,int level,int mk){return EnchantRules.discounted(EnchantRules.price(rarity(e),level,treasure(e),BuildFlavor.STANDALONE),mk);}
    /** Why level {@code level} of {@code e} cannot be bought for {@code gear} now (a key under {@code enchant.locked.}), or null. Shared by the screen and the server. */
    static String problem(ItemStack gear,Enchantment e,int level,int activeFobs,int seals){
        boolean compatible=true;var have=EnchantmentHelper.getEnchantments(gear);
        for(var other:have.keySet())if(other!=e&&!e.isCompatibleWith(other)){compatible=false;break;}
        int unlocked=treasure(e)?EnchantRules.unlockedLevel(id(e),seals,e.getMaxLevel()):e.getMaxLevel();
        return EnchantRules.problem(e.canEnchant(gear),compatible,treasure(e),unlocked,level,e.getMaxLevel(),have.getOrDefault(e,0),activeFobs);
    }
    static int activeFobs(ServerLevel l){return FobData.get(l).count(FobRules.State.ACTIVE);}

    // ---- the screen ------------------------------------------------------------------------------------------------------------------------------
    static void open(ServerPlayer p,BlockPos pos){
        var root=ArsenalStructures.anchor(p.level(),pos);
        NetworkHooks.openScreen(p,new SimpleMenuProvider((id,inv,who)->new EnchantMenu(id,inv,root,new CompoundTag()),Component.translatable("block.arsenal_beacon.tactical_enchanting_table")),
            buf->{buf.writeBlockPos(root);buf.writeNbt(state(p,""));});
    }
    static void refresh(ServerPlayer p,String message){BeaconNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new Hunts.TableState(state(p,message)));}
    /** What the screen needs besides the slot: the ACTIVE FOBs, the table's Mk and next price, the unlocks, the seals and energy carried. */
    static CompoundTag state(ServerPlayer p,String message){
        var l=p.serverLevel();var d=EnchantData.get(l);var n=new CompoundTag();
        n.putBoolean("enchant",true);n.putInt("fobs",activeFobs(l));n.putInt("mk",d.mk);n.putInt("energy",ArdentEnergy.balance(p));n.putBoolean("creative",p.isCreative());
        var next=Economy.enchantingUpgrade(d.mk+1);
        if(next!=null){n.putInt("upgradeAmount",next.amount());n.put("upgradeItem",new ItemStack(next.item()).save(new CompoundTag()));n.putInt("upgradeHave",next.ardent()?ArdentEnergy.balance(p):Economy.have(p,next.item()));}
        var seals=new CompoundTag();d.seals.forEach(seals::putInt);n.put("seals",seals);n.putInt("sealsCarried",Economy.have(p,ArsenalBeacon.WARLORD_SEAL.get()));
        n.putString("message",message);return n;
    }

    // ---- the buttons, checked again here ------------------------------------------------------------------------------------------------------------
    static void button(ServerPlayer p,EnchantMenu menu,int id){
        if(!menu.stillValid(p)||BaseZone.problem(p.level(),menu.pos)!=null)return;
        String result;
        if(id==UPGRADE)result=upgrade(p);
        else if(id>=UNLOCK&&id<UNLOCK+EnchantRules.TREASURE.size())result=unlock(p,EnchantRules.TREASURE.get(id-UNLOCK));
        else if(id>=BUY)result=buy(p,menu,(id-BUY)/8,(id-BUY)%8);
        else return;
        refresh(p,result);
    }
    static String buy(ServerPlayer p,EnchantMenu menu,int index,int level){
        var l=p.serverLevel();var cat=catalogue();if(index<0||index>=cat.size()||level<1)return "enchant.result.gone";
        var e=cat.get(index);var gear=menu.gear.getItem(0);
        if(gear.isEmpty())return "enchant.result.no_gear";
        String problem=problem(gear,e,level,activeFobs(l),EnchantData.get(l).seals(id(e)));
        if(problem!=null)return "enchant.locked."+problem;
        int price=price(e,level,EnchantData.get(l).mk);
        if(!ArdentEnergy.spend(p,price))return "enchant.result.no_energy";
        var map=EnchantmentHelper.getEnchantments(gear);map.put(e,level);EnchantmentHelper.setEnchantments(map,gear);
        menu.gear.setChanged();menu.broadcastChanges();
        l.playSound(null,menu.pos,SoundEvents.ENCHANTMENT_TABLE_USE,SoundSource.BLOCKS,1f,1f);
        com.mojang.logging.LogUtils.getLogger().info("[enchant] {} bought {} {} for {} Ardent Energy",p.getGameProfile().getName(),id(e),level,price);
        return "enchant.result.done";
    }
    /** The table's next Mk: a permanent discount for everyone, paid per edition (pack: resonance coils; standalone: Ardent Energy). */
    static String upgrade(ServerPlayer p){
        var d=EnchantData.get(p.serverLevel());var price=Economy.enchantingUpgrade(d.mk+1);
        if(price==null)return "enchant.result.max_mk";
        if(!Economy.pay(p,price))return price.ardent()?"enchant.result.no_energy":"enchant.result.no_parts";
        d.mk++;d.setDirty();
        Fobs.announce(p.serverLevel(),Component.translatable("gui.arsenal_beacon.enchant.upgraded",p.getDisplayName(),EnchantRules.roman(d.mk),EnchantRules.DISCOUNT[d.mk]));
        return "enchant.result.upgraded";
    }
    /** One Warlord Seal from the player's inventory unlocks treasure enchant {@code id} (or its next level) for the whole team. */
    static String unlock(ServerPlayer p,String id){
        var l=p.serverLevel();var d=EnchantData.get(l);
        var e=BuiltInRegistries.ENCHANTMENT.get(new net.minecraft.resources.ResourceLocation(id));if(e==null)return "enchant.result.gone";
        if(EnchantRules.sealsForNext(id,d.seals(id),e.getMaxLevel())==0)return "enchant.result.unlocked_all";
        if(!p.isCreative()){
            if(Economy.have(p,ArsenalBeacon.WARLORD_SEAL.get())<1)return "enchant.result.no_seal";
            p.getInventory().clearOrCountMatchingItems(st->st.is(ArsenalBeacon.WARLORD_SEAL.get()),1,p.inventoryMenu.getCraftSlots());
        }
        d.seals.merge(id,1,Integer::sum);d.setDirty();
        int level=EnchantRules.unlockedLevel(id,d.seals(id),e.getMaxLevel());
        Fobs.announce(l,Component.translatable("gui.arsenal_beacon.enchant.unlocked_all",p.getDisplayName(),Component.translatable(e.getDescriptionId()),EnchantRules.roman(level)));
        l.playSound(null,p.blockPosition(),SoundEvents.ENDER_EYE_DEATH,SoundSource.PLAYERS,.8f,1.2f);
        return "enchant.result.unlocked";
    }
}
