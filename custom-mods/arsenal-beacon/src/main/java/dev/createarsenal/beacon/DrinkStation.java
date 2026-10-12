package dev.createarsenal.beacon;

import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.brewing.BrewingRecipeRegistry;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.*;

/**
 * 0.0.35 (owner): the Drink Station, the Mess Hall's sibling for potions, on the owner's four models (2 wide, 1 deep, 2 tall, like the hall).
 * Mk I is a brewing stand with a 54-slot potion storage; from Mk II it mixes drinks into Brew Pots and Lattes ({@link DrinkStationMenu}).
 * The storage counts bottles inside the station (up to 64 of one kind a slot) instead of holding real stacks, so potions stay unstackable
 * everywhere else. It has no hopper or pipe access in this version.
 */
final class DrinkStation {
    private DrinkStation(){}
    static final class StationBlock extends KitchenBlock {
        final int mk;
        StationBlock(int mk){super(true);this.mk=mk;}
        @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new StationEntity(pos,state);}
        @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){
            return level.isClientSide||type!=ArsenalBeacon.DRINK_STATION_ENTITY.get()?null:(l,p,s,be)->((StationEntity)be).serverTick();
        }
        @Override public void onRemove(BlockState s,Level l,BlockPos p,BlockState next,boolean moving){
            if(!s.is(next.getBlock())&&!(next.getBlock() instanceof StationBlock)&&l.getBlockEntity(p) instanceof StationEntity st)st.dropAll();
            super.onRemove(s,l,p,next,moving);
        }
    }
    static final class StationEntity extends BlockEntity implements MenuProvider {
        UUID identity=UUID.randomUUID();final List<BlockPos> links=new ArrayList<>();
        // ---- the potion storage: one kind (a single bottle as its pattern) and a count per slot -------------------------------------
        final ItemStack[] kinds=new ItemStack[DrinkRules.STORAGE_SLOTS];final int[] counts=new int[DrinkRules.STORAGE_SLOTS];
        { Arrays.fill(kinds,ItemStack.EMPTY); }
        // ---- the brewing stand: bottles 0-2, ingredient 3, blaze powder 4 ---------------------------------------------------------------
        final SimpleContainer brew=new SimpleContainer(5){@Override public void setChanged(){super.setChanged();StationEntity.this.setChanged();}};
        int brewTime,fuel;private net.minecraft.world.item.Item ingredient;
        /** The bottles the last brew made, by identity: they move to the storage when there is room; a bottle the player put there stays. */
        private final ItemStack[] fresh=new ItemStack[3];
        // ---- the mixing table: potions 0-2, the Latte's cup 3, the Latte out 4, the sweetener in 5 -----------------------------------
        final SimpleContainer mix=new SimpleContainer(6){@Override public void setChanged(){super.setChanged();StationEntity.this.setChanged();}};
        int tank;
        /** The standing order (kept with the station, like the hall's): Latte or Brew Pot, the effects chosen, the sweetener steps. */
        boolean latteMode;final List<ResourceLocation> order=new ArrayList<>();int steps;
        StationEntity(BlockPos pos,BlockState state){super(ArsenalBeacon.DRINK_STATION_ENTITY.get(),pos,state);}
        int mk(){return getBlockState().getBlock() instanceof StationBlock b?b.mk:1;}
        DrinkRules.Tier tier(){return DrinkRules.tier(mk());}
        int limit(){return latteMode?DrinkRules.latteEffects(mk()):tier().effects();}
        void setMode(boolean latte){if(latteMode!=latte){latteMode=latte;while(order.size()>limit())order.remove(order.size()-1);setChanged();}}

        // ---- storage ------------------------------------------------------------------------------------------------------------------
        int total(){int n=0;for(int c:counts)n+=c;return n;}
        static boolean storable(ItemStack stack){return DrinkData.isPotion(stack);}
        /** Puts up to {@code amount} bottles like {@code stack} in: first with their own kind, then into empty slots. Returns how many went in. */
        int insert(ItemStack stack,int amount){
            if(!storable(stack)||amount<=0)return 0;int left=amount;
            for(int i=0;i<kinds.length&&left>0;i++)if(!kinds[i].isEmpty()&&ItemStack.isSameItemSameTags(kinds[i],stack)){int n=Math.min(left,DrinkRules.PER_SLOT-counts[i]);counts[i]+=n;left-=n;}
            for(int i=0;i<kinds.length&&left>0;i++)if(kinds[i].isEmpty()){kinds[i]=stack.copyWithCount(1);int n=Math.min(left,DrinkRules.PER_SLOT);counts[i]=n;left-=n;}
            if(left!=amount)setChanged();return amount-left;
        }
        /** How many more bottles like this would fit. */
        int room(ItemStack stack){
            if(!storable(stack))return 0;int n=0;
            for(int i=0;i<kinds.length;i++)n+=kinds[i].isEmpty()?DrinkRules.PER_SLOT:ItemStack.isSameItemSameTags(kinds[i],stack)?DrinkRules.PER_SLOT-counts[i]:0;return n;
        }
        /** Takes one bottle out of slot {@code i}. */
        ItemStack takeOne(int i){
            if(i<0||i>=kinds.length||kinds[i].isEmpty()||counts[i]<=0)return ItemStack.EMPTY;
            var out=kinds[i].copyWithCount(1);if(--counts[i]<=0){counts[i]=0;kinds[i]=ItemStack.EMPTY;}setChanged();return out;
        }
        /** The slot holding a mixable potion of this effect, the strongest first; -1 when none. */
        int find(net.minecraft.world.effect.MobEffect effect){
            int best=-1,level=-1;
            for(int i=0;i<kinds.length;i++){if(kinds[i].isEmpty()||!DrinkData.mixable(kinds[i]))continue;var fx=DrinkData.effectOf(kinds[i]);if(fx.getEffect()==effect&&fx.getAmplifier()>level){best=i;level=fx.getAmplifier();}}
            return best;
        }

        // ---- the brewing stand ---------------------------------------------------------------------------------------------------------
        private static final int[] BOTTLES={0,1,2};
        private net.minecraft.core.NonNullList<ItemStack> brewList(){var l=net.minecraft.core.NonNullList.withSize(5,ItemStack.EMPTY);for(int i=0;i<5;i++)l.set(i,brew.getItem(i));return l;}
        private boolean brewable(){var ing=brew.getItem(3);return !ing.isEmpty()&&BrewingRecipeRegistry.canBrew(brewList(),ing,BOTTLES);}
        /** Vanilla's brewing stand, on Forge's brewing registry so other mods' recipes brew here too. */
        void serverTick(){
            if(level==null)return;var fuelStack=brew.getItem(4);
            if(fuel<=0&&fuelStack.is(Items.BLAZE_POWDER)){fuel=DrinkRules.FUEL_USES;fuelStack.shrink(1);brew.setChanged();}
            boolean brewable=brewable();var ing=brew.getItem(3);
            if(brewTime>0){
                brewTime--;
                if(brewTime==0&&brewable)doBrew();
                else if(!brewable||!ing.is(ingredient)){brewTime=0;setChanged();}
            }else if(brewable&&fuel>0){fuel--;brewTime=DrinkRules.BREW_TICKS;ingredient=ing.getItem();setChanged();}
            if(level.getGameTime()%10==0)storeFresh();
        }
        private void doBrew(){
            var items=brewList();if(net.minecraftforge.event.ForgeEventFactory.onPotionAttemptBrew(items))return;
            var ing=items.get(3);BrewingRecipeRegistry.brewPotions(items,ing,BOTTLES);net.minecraftforge.event.ForgeEventFactory.onPotionBrewed(items);
            if(ing.hasCraftingRemainingItem()){var rest=ing.getCraftingRemainingItem();ing.shrink(1);if(ing.isEmpty())ing=rest;else Containers.dropItemStack(level,worldPosition.getX(),worldPosition.getY()+1,worldPosition.getZ(),rest);}
            else ing.shrink(1);
            items.set(3,ing);for(int i=0;i<5;i++)brew.setItem(i,items.get(i));
            for(int i=0;i<3;i++)fresh[i]=brew.getItem(i);
            level.levelEvent(1035,worldPosition,0);storeFresh();setChanged();
        }
        /** Finished bottles of the last brew (a potion with an effect that the ingredient in the stand cannot brew further) go to the storage. */
        void storeFresh(){
            var ing=brew.getItem(3);
            for(int i=0;i<3;i++){
                var bottle=brew.getItem(i);if(fresh[i]==null||bottle!=fresh[i]||bottle.isEmpty()){fresh[i]=null;continue;}
                if(DrinkData.effects(bottle).isEmpty()||!ing.isEmpty()&&BrewingRecipeRegistry.hasOutput(bottle,ing))continue;
                if(insert(bottle,1)==1){brew.setItem(i,ItemStack.EMPTY);fresh[i]=null;}
            }
        }

        // ---- the sweetener --------------------------------------------------------------------------------------------------------------
        /** Pours a Regeneration potion from the sweetener slot into the tank when it fits; the empty bottle goes back to {@code who}. */
        boolean sweeten(ServerPlayer who){
            var in=mix.getItem(5);int mb=DrinkData.sweetener(in);if(mb==0||tank+mb>DrinkRules.TANK_MB)return false;
            tank+=mb;mix.removeItem(5,1);ArsenalBeacon.give(who,new ItemStack(Items.GLASS_BOTTLE));setChanged();return true;
        }

        // ---- Brew Pots ------------------------------------------------------------------------------------------------------------------
        boolean inRange(BlockPos pos){return worldPosition.distSqr(pos)<=DrinkRules.LINK_RANGE*DrinkRules.LINK_RANGE;}
        void prune(){if(level==null||level.isClientSide)return;boolean changed=links.removeIf(p->level.hasChunkAt(p)&&(!(level.getBlockEntity(p) instanceof BrewPot.PotEntity pot)||!pot.belongs(this)));if(changed)changed();}
        boolean link(BrewPot.PotEntity pot){
            prune();if(!inRange(pot.getBlockPos())||pot.getLevel()!=level||BaseZone.problem(level,worldPosition)!=null)return false;
            if(links.contains(pot.getBlockPos())&&pot.belongs(this))return true;
            if(links.size()>=tier().pots()||pot.validLink()!=null||pot.waitingForStation())return false;
            pot.linkedStation=worldPosition;pot.stationIdentity=identity;pot.changed();links.add(pot.getBlockPos().immutable());changed();return true;
        }
        List<BrewPot.PotEntity> pots(){prune();var out=new ArrayList<BrewPot.PotEntity>();for(var p:links)if(level.hasChunkAt(p)&&level.getBlockEntity(p) instanceof BrewPot.PotEntity pot&&pot.belongs(this))out.add(pot);out.sort(Comparator.comparingLong(p->p.getBlockPos().asLong()));return out;}
        void discover(){
            if(level==null||level.isClientSide)return;prune();var candidates=new ArrayList<BrewPot.PotEntity>();int r=DrinkRules.LINK_RANGE;
            for(var p:BlockPos.betweenClosed(worldPosition.offset(-r,-r,-r),worldPosition.offset(r,r,r)))if(inRange(p)&&level.hasChunkAt(p)&&level.getBlockEntity(p) instanceof BrewPot.PotEntity pot)candidates.add(pot);
            candidates.sort(Comparator.<BrewPot.PotEntity>comparingDouble(p->worldPosition.distSqr(p.getBlockPos())).thenComparingLong(p->p.getBlockPos().asLong()));
            for(var pot:candidates){if(links.size()>=tier().pots())break;link(pot);}
        }
        void removed(){if(level!=null&&!level.isClientSide)for(var p:List.copyOf(links))if(level.hasChunkAt(p)&&level.getBlockEntity(p) instanceof BrewPot.PotEntity pot&&pot.belongs(this))pot.unlink();}
        boolean upgrade(ServerPlayer p){
            var price=Economy.drinkStation(mk());if(price==null||BaseZone.problem(level,worldPosition)!=null)return false;
            var next=switch(mk()){case 1->ArsenalBeacon.DRINK_STATION_II.get();case 2->ArsenalBeacon.DRINK_STATION_III.get();default->ArsenalBeacon.DRINK_STATION_IV.get();};
            var state=next.defaultBlockState().setValue(ArsenalStructures.FACING,getBlockState().getValue(ArsenalStructures.FACING));
            if(!ArsenalStructures.available(level,worldPosition,state)||!Economy.pay(p,price))return false;
            var saved=saveWithoutMetadata();level.setBlock(worldPosition,state,3);var replacement=(StationEntity)level.getBlockEntity(worldPosition);replacement.load(saved);replacement.changed();replacement.discover();
            net.minecraftforge.network.NetworkHooks.openScreen(p,replacement,b->{b.writeBlockPos(worldPosition);b.writeVarInt(replacement.mk());});return true;
        }
        void dropAll(){
            if(level==null||level.isClientSide)return;removed();
            Containers.dropContents(level,worldPosition,brew);Containers.dropContents(level,worldPosition,mix);
            for(int i=0;i<kinds.length;i++){for(int n=0;n<counts[i];n++)Containers.dropItemStack(level,worldPosition.getX(),worldPosition.getY(),worldPosition.getZ(),kinds[i].copyWithCount(1));counts[i]=0;kinds[i]=ItemStack.EMPTY;}
        }
        void changed(){setChanged();if(level!=null&&!level.isClientSide)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}

        // ---- saving ---------------------------------------------------------------------------------------------------------------------
        @Override protected void saveAdditional(CompoundTag n){super.saveAdditional(n);saveShared(n);
            var store=new ListTag();for(int i=0;i<kinds.length;i++)if(!kinds[i].isEmpty()&&counts[i]>0){var t=kinds[i].save(new CompoundTag());t.putByte("Slot",(byte)i);t.putInt("Bottles",counts[i]);store.add(t);}n.put("Storage",store);
            n.put("Brew",items(brew));n.put("Mix",items(mix));n.putInt("BrewTime",brewTime);n.putInt("Fuel",fuel);if(ingredient!=null)n.putString("Ingredient",net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(ingredient).toString());
            n.putInt("Tank",tank);n.putBoolean("Latte",latteMode);var chosen=new ListTag();for(var e:order)chosen.add(StringTag.valueOf(e.toString()));n.put("Order",chosen);n.putInt("Steps",steps);
        }
        /** What the client needs for the model: identity and the linked pots (the icons on the taps). */
        private void saveShared(CompoundTag n){n.putUUID("Identity",identity);var list=new ListTag();for(var p:links){var t=new CompoundTag();t.putLong("Pos",p.asLong());list.add(t);}n.put("Pots",list);}
        private static ListTag items(SimpleContainer c){var l=new ListTag();for(int i=0;i<c.getContainerSize();i++)if(!c.getItem(i).isEmpty()){var t=c.getItem(i).save(new CompoundTag());t.putByte("Slot",(byte)i);l.add(t);}return l;}
        private static void items(SimpleContainer c,ListTag l){c.clearContent();for(var t:l){var tag=(CompoundTag)t;int slot=tag.getByte("Slot");if(slot>=0&&slot<c.getContainerSize())c.setItem(slot,ItemStack.of(tag));}}
        @Override public void load(CompoundTag n){
            super.load(n);if(n.hasUUID("Identity"))identity=n.getUUID("Identity");links.clear();
            for(var t:n.getList("Pots",Tag.TAG_COMPOUND)){var p=BlockPos.of(((CompoundTag)t).getLong("Pos"));if(inRange(p)&&!links.contains(p)&&links.size()<3)links.add(p);}
            if(!n.contains("Storage"))return; // the client's copy carries only the links
            Arrays.fill(kinds,ItemStack.EMPTY);Arrays.fill(counts,0);
            for(var t:n.getList("Storage",Tag.TAG_COMPOUND)){var tag=(CompoundTag)t;int slot=tag.getByte("Slot");var stack=ItemStack.of(tag);if(slot<0||slot>=kinds.length||!storable(stack))continue;kinds[slot]=stack.copyWithCount(1);counts[slot]=Math.max(1,Math.min(DrinkRules.PER_SLOT,tag.getInt("Bottles")));}
            items(brew,n.getList("Brew",Tag.TAG_COMPOUND));items(mix,n.getList("Mix",Tag.TAG_COMPOUND));brewTime=Math.max(0,Math.min(DrinkRules.BREW_TICKS,n.getInt("BrewTime")));fuel=Math.max(0,Math.min(DrinkRules.FUEL_USES,n.getInt("Fuel")));
            var ing=ResourceLocation.tryParse(n.getString("Ingredient"));ingredient=ing==null?null:net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(ing);
            tank=Math.max(0,Math.min(DrinkRules.TANK_MB,n.getInt("Tank")));latteMode=n.getBoolean("Latte");steps=DrinkRules.clampSteps(n.getInt("Steps"));
            order.clear();for(var t:n.getList("Order",Tag.TAG_STRING)){var id=ResourceLocation.tryParse(t.getAsString());if(id!=null&&net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.containsKey(id)&&!order.contains(id)&&order.size()<3)order.add(id);}
        }
        @Override public CompoundTag getUpdateTag(){var n=new CompoundTag();saveShared(n);return n;}
        @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
        @Override public net.minecraft.world.phys.AABB getRenderBoundingBox(){return new net.minecraft.world.phys.AABB(worldPosition).inflate(2);}
        @Override public Component getDisplayName(){return getBlockState().getBlock().getName();}
        @Override public AbstractContainerMenu createMenu(int id,Inventory inv,Player player){return new DrinkStationMenu(id,inv,brew,mix,worldPosition,mk(),this);}
    }
    /** A station holding more than 256 bottles refuses to be broken by hand: empty the storage first. */
    static final class GuardEvents {
        @SubscribeEvent public void breaking(BlockEvent.BreakEvent e){
            if(!(e.getLevel() instanceof Level level))return;var root=ArsenalStructures.anchor(level,e.getPos());
            if(level.getBlockEntity(root) instanceof StationEntity st&&DrinkRules.guarded(st.total())){
                e.setCanceled(true);e.getPlayer().displayClientMessage(Component.translatable("gui.arsenal_beacon.drinks.guard",st.total(),DrinkRules.BREAK_GUARD).withStyle(net.minecraft.ChatFormatting.GOLD),true);
            }
        }
    }
}
