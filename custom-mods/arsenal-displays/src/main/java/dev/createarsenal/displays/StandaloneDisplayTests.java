package dev.createarsenal.displays;

import java.util.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.GameTestHolder;
import static net.minecraft.commands.Commands.literal;

/** Only registered on the isolated standalone test server. */
@GameTestHolder(GunDisplays.ID)
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
public final class StandaloneDisplayTests {
    static ItemStack gun()throws Exception{
        var item=BuiltInRegistries.ITEM.get(new ResourceLocation("tacz:modern_kinetic_gun"));var stack=new ItemStack(item);
        item.getClass().getMethod("setGunId",ItemStack.class,ResourceLocation.class).invoke(item,stack,new ResourceLocation("tacz:ak47"));
        stack.getOrCreateTag().putInt("GunCurrentAmmoCount",7);stack.getOrCreateTag().putString("TestAttachment","kept");
        stack.setHoverName(net.minecraft.network.chat.Component.literal("Stored rifle"));return stack;
    }
    @GameTest(template="empty3x3x3",timeoutTicks=100)
    public static void everyVanillaRecipeWorksWithoutCreateOrBeacon(GameTestHelper h){
        h.assertTrue(!net.minecraftforge.fml.ModList.get().isLoaded("create")&&!net.minecraftforge.fml.ModList.get().isLoaded("arsenal_beacon"),"Standalone test really has no Create or beacon installed");
        for(var registered:GunDisplays.RACKS){
            var id=new ResourceLocation(GunDisplays.ID,registered.getId().getPath());var recipe=h.getLevel().getRecipeManager().byKey(id).orElseThrow();
            for(var wood:List.of(Items.OAK_PLANKS,Items.BIRCH_PLANKS,Items.CRIMSON_PLANKS)){
                var grid=new TransientCraftingContainer(new AbstractContainerMenu(null,0){public ItemStack quickMoveStack(Player p,int slot){return ItemStack.EMPTY;}public boolean stillValid(Player p){return true;}},3,3);
                var ingredients=recipe.getIngredients();int width=recipe instanceof ShapedRecipe shaped?shaped.getWidth():3;
                for(int i=0;i<ingredients.size();i++){
                    var ingredient=ingredients.get(i);if(ingredient.isEmpty())continue;
                    var stack=ingredient.test(new ItemStack(wood))?new ItemStack(wood):ingredient.getItems()[0].copy();grid.setItem(i/width*3+i%width,stack);
                }
                @SuppressWarnings("unchecked") var crafting=(Recipe<CraftingContainer>)recipe;
                h.assertTrue(crafting.matches(grid,h.getLevel())&&crafting.assemble(grid,h.getLevel().registryAccess()).is(registered.get().asItem()),"Vanilla recipe crafts "+id+" using "+wood);
            }
        }h.succeed();
    }
    @GameTest(template="empty3x3x3",timeoutTicks=100)
    public static void oldSavedIdsAndCompleteNativeGunItemStillLoad(GameTestHelper h)throws Exception{
        var l=h.getLevel();var root=h.absolutePos(new BlockPos(1,1,1));var original=gun();
        for(var registered:GunDisplays.RACKS){
            var state=registered.get().defaultBlockState();l.setBlock(root.south(),Blocks.STONE.defaultBlockState(),3);l.setBlock(root.east().south(),Blocks.STONE.defaultBlockState(),3);l.setBlock(root,state,3);
            var legacy=new CompoundTag();legacy.putString("id","arsenal_beacon:gun_display");legacy.putInt("x",root.getX());legacy.putInt("y",root.getY());legacy.putInt("z",root.getZ());legacy.put("Item",original.save(new CompoundTag()));
            var loaded=BlockEntity.loadStatic(root,state,legacy);h.assertTrue(loaded instanceof DisplayRacks.DisplayEntity&&ItemStack.matches(((DisplayRacks.DisplayEntity)loaded).gun(),original),"Legacy saved ID loads exact native item without a beacon mod: "+registered.getId());
            var display=(DisplayRacks.DisplayEntity)l.getBlockEntity(root);display.load(legacy);h.assertTrue(display.getUpdateTag().getCompound("Item").equals(legacy.getCompound("Item")),"Server update packet preserves full stored gun");display.take();l.setBlock(root,Blocks.AIR.defaultBlockState(),3);
        }h.succeed();
    }
    @GameTest(template="empty3x3x3",timeoutTicks=100)
    public static void allElevenModelsInsertSwapAndRetrieveActualGun(GameTestHelper h)throws Exception{
        var l=h.getLevel();var root=h.absolutePos(new BlockPos(1,1,1));var player=FakePlayerFactory.get(l,new GameProfile(UUID.randomUUID(),"display-test"));var original=gun();
        for(var registered:GunDisplays.RACKS){
            var state=registered.get().defaultBlockState();l.setBlock(root.south(),Blocks.STONE.defaultBlockState(),3);l.setBlock(root.east().south(),Blocks.STONE.defaultBlockState(),3);l.setBlock(root,state,3);
            player.getInventory().clearContent();player.setItemInHand(InteractionHand.MAIN_HAND,original.copy());var hit=new BlockHitResult(Vec3.atCenterOf(root),Direction.NORTH,root,false);state.use(l,player,InteractionHand.MAIN_HAND,hit);
            var display=(DisplayRacks.DisplayEntity)l.getBlockEntity(root);h.assertTrue(player.getMainHandItem().isEmpty()&&ItemStack.matches(display.gun(),original),"Insert consumes one real gun for "+registered.getId());
            var changed=original.copy();changed.getOrCreateTag().putInt("GunCurrentAmmoCount",2);player.setItemInHand(InteractionHand.MAIN_HAND,changed.copy());state.use(l,player,InteractionHand.MAIN_HAND,hit);h.assertTrue(ItemStack.matches(display.gun(),changed)&&player.getInventory().items.stream().anyMatch(s->ItemStack.matches(s,original)),"Swap returns previous item");
            player.getInventory().clearContent();state.use(l,player,InteractionHand.MAIN_HAND,hit);h.assertTrue(display.gun().isEmpty()&&player.getInventory().items.stream().filter(s->ItemStack.matches(s,changed)).count()==1,"Retrieve returns exact gun once");l.setBlock(root,Blocks.AIR.defaultBlockState(),3);
        }h.succeed();
    }
    @GameTest(template="empty3x3x3",timeoutTicks=100)
    public static void wideAndHeavyPartnersRotateAndNeverOverwriteContainers(GameTestHelper h){
        var l=h.getLevel();var root=h.absolutePos(new BlockPos(1,1,1));
        for(var registry:GunDisplays.RACKS){var rack=(DisplayRacks.Rack)registry.get();if(!rack.wide())continue;
            for(var facing:Direction.Plane.HORIZONTAL){var state=rack.defaultBlockState().setValue(DisplayRacks.FACING,facing);var partner=DisplayRacks.partner(root,state);l.setBlock(root.relative(facing.getOpposite()),Blocks.STONE.defaultBlockState(),3);l.setBlock(partner.relative(facing.getOpposite()),Blocks.STONE.defaultBlockState(),3);l.setBlock(root,state,3);
                h.assertTrue(l.getBlockState(partner).is(rack)&&DisplayRacks.anchor(l,partner).equals(root)&&l.getBlockEntity(partner)==null,"One anchor per wide display in all rotations");l.setBlock(root,Blocks.AIR.defaultBlockState(),3);h.assertTrue(l.getBlockState(partner).isAir(),"Removing anchor clears partner");l.setBlock(root.relative(facing.getOpposite()),Blocks.AIR.defaultBlockState(),3);l.setBlock(partner.relative(facing.getOpposite()),Blocks.AIR.defaultBlockState(),3);
            }
        }h.succeed();
    }
    public static final class Runner {
        private MultipleTestTracker tracker;
        @SubscribeEvent public void commands(RegisterCommandsEvent event){GameTestRegistry.register(StandaloneDisplayTests.class);event.getDispatcher().register(literal("displays-test").requires(s->s.hasPermission(2)).executes(c->{try{tracker=new MultipleTestTracker(GameTestRunner.runTests(GameTestRegistry.getAllTestFunctions(),new BlockPos(0,100,0),Rotation.NONE,c.getSource().getLevel(),GameTestTicker.SINGLETON,4));return 1;}catch(Exception ex){com.mojang.logging.LogUtils.getLogger().error("Standalone test runner failed",ex);throw ex;}}));}
        @SubscribeEvent public void tick(TickEvent.ServerTickEvent event){if(event.phase!=TickEvent.Phase.END||tracker==null)return;GameTestTicker.SINGLETON.tick();if(!tracker.isDone())return;var logger=com.mojang.logging.LogUtils.getLogger();for(var test:tracker.getFailedRequired())logger.error("Standalone test failed: "+test.getTestName(),test.getError());if(tracker.getFailedRequiredCount()>0)logger.error("{} standalone tests failed",tracker.getFailedRequiredCount());else logger.info("All {} standalone tests passed",tracker.getTotalCount());tracker=null;}
    }
}
