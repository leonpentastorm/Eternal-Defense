package dev.createarsenal.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.registries.*;
import java.util.*;

@GameTestHolder(ArsenalBeacon.ID)
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
public final class PlatformGameTests {
    // Test-only stand-ins, never registered in a released game's ordinary startup.
    static void registerParts(net.minecraftforge.eventbus.api.IEventBus bus){
        var parts=DeferredRegister.create(ForgeRegistries.ITEMS,"kubejs");
        for(String id:List.of("pressed_receiver","hardened_receiver","exotic_receiver","cartridge_case","propellant"))parts.register(id,()->new Item(new Item.Properties()));parts.register(bus);
    }
    private static ServerPlayer player(GameTestHelper h,String suffix){
        var p=net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.nameUUIDFromBytes(("platform-"+suffix).getBytes(java.nio.charset.StandardCharsets.UTF_8)),"PlatformTests"));
        p.getInventory().clearContent();return p;
    }
    private static void supply(ServerPlayer p,List<WeaponPlatform.Cost> costs){
        for(var cost:costs){var choices=cost.ingredient().getItems();if(choices.length==0||choices[0].isEmpty())throw new IllegalStateException("Missing integration material");int amount=cost.count();while(amount>0){int batch=Math.min(amount,choices[0].getMaxStackSize());p.getInventory().add(choices[0].copyWithCount(batch));amount-=batch;}}
    }
    @GameTest(template="empty3x3x3",batch="platform",timeoutTicks=40)
    public static void squareCornersAndHighestStationAgesDetermineScore(GameTestHelper h){
        var d=new CampaignData();d.beacon=h.absolutePos(new BlockPos(1,1,1));var corner=d.beacon.offset(8,d.above(),8);
        h.assertTrue(d.inside(corner)&&d.inside(d.beacon.offset(-8,-d.below(),-8)),"All inclusive square corners count");
        h.assertTrue(!d.inside(corner.east())&&!d.inside(corner.above()),"Blocks outside the outer face do not count");
        ArsenalBeacon.auditBlock(d,corner,ArsenalBeacon.GUN_PLATFORM.get().defaultBlockState().setValue(WeaponPlatform.AGE,3));
        ArsenalBeacon.auditBlock(d,corner.west(),ArsenalBeacon.GUN_PLATFORM.get().defaultBlockState().setValue(WeaponPlatform.AGE,1));
        ArsenalBeacon.auditBlock(d,corner.east(),ArsenalBeacon.AMMO_PLATFORM.get().defaultBlockState().setValue(WeaponPlatform.AGE,5));
        ArsenalBeacon.auditBlock(d,corner.below(),ArsenalBeacon.GUN_PLATFORM.get().defaultBlockState().setValue(WeaponPlatform.AGE,2));
        int score=d.baseCounts.entrySet().stream().mapToInt(e->Rules.blockScore(e.getKey(),e.getValue())).sum();
        h.assertTrue(score==180&&d.baseCounts.size()==1,"Only the highest Age of each type inside the zone contributes");
        h.assertTrue(CampaignData.load(d.save(new CompoundTag())).baseCounts.equals(d.baseCounts),"Score scan progress persists across save/reload");h.succeed();
    }
    @GameTest(template="empty3x3x3",batch="platform",timeoutTicks=40)
    public static void upgradesRequireCreateMaterialsAndNetherGate(GameTestHelper h){
        var p=player(h,"ages");var pos=h.absolutePos(new BlockPos(1,1,1));h.getLevel().setBlock(pos,ArsenalBeacon.GUN_PLATFORM.get().defaultBlockState(),3);
        h.assertTrue(WeaponPlatform.upgrade(p,pos).contains("required"),"Cannot upgrade empty-handed");
        supply(p,WeaponPlatform.upgrades(1));WeaponPlatform.upgrade(p,pos);
        h.assertTrue(h.getLevel().getBlockState(pos).getValue(WeaponPlatform.AGE)==2,"Basic Create industry reaches World Wars");
        h.assertTrue(WeaponPlatform.upgrades(2).stream().anyMatch(c->c.ingredient().test(new ItemStack(Items.BLAZE_ROD))),"Modern explicitly requires Nether materials");
        var travel=PlatformRegistry.get(p.server.overworld());travel.netherVisited=false;
        var costs=WeaponPlatform.upgrades(2);supply(p,costs.stream().filter(c->!c.ingredient().test(new ItemStack(Items.BLAZE_ROD))).toList());
        var before=p.getInventory().save(new net.minecraft.nbt.ListTag());WeaponPlatform.upgrade(p,pos);
        h.assertTrue(h.getLevel().getBlockState(pos).getValue(WeaponPlatform.AGE)==2&&before.equals(p.getInventory().save(new net.minecraft.nbt.ListTag())),"Missing blaze rods cannot partially consume Create components");
        p.getInventory().add(new ItemStack(Items.BLAZE_ROD,8));before=p.getInventory().save(new net.minecraft.nbt.ListTag());WeaponPlatform.upgrade(p,pos);
        h.assertTrue(h.getLevel().getBlockState(pos).getValue(WeaponPlatform.AGE)==2&&before.equals(p.getInventory().save(new net.minecraft.nbt.ListTag())),"Looted materials cannot bypass the team's actual Nether visit");
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent(p,net.minecraft.world.level.Level.OVERWORLD,net.minecraft.world.level.Level.NETHER));
        h.assertTrue(PlatformRegistry.load(travel.save(new CompoundTag())).netherVisited,"Shared Nether visit persists for co-op and reload");WeaponPlatform.upgrade(p,pos);
        h.assertTrue(h.getLevel().getBlockState(pos).getValue(WeaponPlatform.AGE)==3,"All ingredients unlock Modern");
        for(int age=3;age<5;age++){supply(p,WeaponPlatform.upgrades(age));WeaponPlatform.upgrade(p,pos);h.assertTrue(h.getLevel().getBlockState(pos).getValue(WeaponPlatform.AGE)==age+1,"Advanced and Exotic require their complete Create upgrade recipes");}
        var ammo=pos.east();h.getLevel().setBlock(ammo,ArsenalBeacon.AMMO_PLATFORM.get().defaultBlockState(),3);
        h.assertTrue(h.getLevel().getBlockState(ammo).getValue(WeaponPlatform.AGE)==1,"Each station upgrades independently");
        var saved=net.minecraft.nbt.NbtUtils.writeBlockState(h.getLevel().getBlockState(pos));
        h.assertTrue(net.minecraft.nbt.NbtUtils.readBlockState(h.getLevel().holderLookup(net.minecraft.core.registries.Registries.BLOCK),saved).getValue(WeaponPlatform.AGE)==5,"Age persists as block state");p.getInventory().clearContent();h.succeed();
    }
    @GameTest(template="empty3x3x3",batch="platform",timeoutTicks=40)
    public static void modernPistolCannotBypassAgeAndFrontierCraftsRealGun(GameTestHelper h)throws Exception{
        var p=player(h,"guns");var pos=h.absolutePos(new BlockPos(1,1,1));h.getLevel().setBlock(pos,ArsenalBeacon.GUN_PLATFORM.get().defaultBlockState(),3);
        var entries=WeaponPlatform.entries(p,"gun","");var modern=entries.stream().filter(e->e.gate().id().equals("tacz:p320")).findFirst().orElseThrow();
        h.assertTrue(modern.gate().age()==3&&entries.stream().filter(e->e.gate().id().equals("tacz:m4a1")).allMatch(e->e.gate().age()==3),"Modern P320 and M4A1 are era-gated regardless of DPS");
        supply(p,WeaponPlatform.costs(modern));var before=p.getInventory().save(new net.minecraft.nbt.ListTag());
        h.assertTrue(WeaponPlatform.craft(p,pos,modern.recipeId()).contains("Requires Age 3")&&before.equals(p.getInventory().save(new net.minecraft.nbt.ListTag())),"Even all materials cannot bypass station Age");p.getInventory().clearContent();
        var frontier=entries.stream().filter(e->e.gate().id().equals("qkl:fk15p")).findFirst().orElseThrow();supply(p,WeaponPlatform.costs(frontier));
        h.assertTrue(WeaponPlatform.craft(p,pos,frontier.recipeId()).startsWith("Crafted"),"Frontier station crafts authored flintlock recipe");
        h.assertTrue(p.getInventory().items.stream().anyMatch(s->!s.isEmpty()&&s.getItem().getClass().getName().contains("ModernKineticGunItem")&&s.getOrCreateTag().getString("GunId").equals("qkl:fk15p")),"Crafted gun retains TaCZ GunId NBT");p.getInventory().clearContent();h.succeed();
    }
    @GameTest(template="empty3x3x3",batch="platform",timeoutTicks=40)
    public static void ammoAndAttachmentsUseSeparateStationsAndKeepIndexedOutputs(GameTestHelper h)throws Exception{
        var p=player(h,"ammo");var pos=h.absolutePos(new BlockPos(1,1,1));h.getLevel().setBlock(pos,ArsenalBeacon.AMMO_PLATFORM.get().defaultBlockState().setValue(WeaponPlatform.AGE,5),3);
        equip(p,"qkl:fk15p");var ammo=WeaponPlatform.entries(p,"ammo","").stream().filter(e->e.gate().id().equals("qkl:16mm")).findFirst().orElseThrow();supply(p,WeaponPlatform.costs(ammo));
        String result=WeaponPlatform.craft(p,pos,ammo.recipeId());h.assertTrue(result.startsWith("Crafted"),"Ammo station crafts a complete authored batch: "+result+" held="+p.getMainHandItem()+" recognized="+WeaponPlatform.heldGun(p));
        h.assertTrue(p.getInventory().items.stream().mapToInt(s->s.getOrCreateTag().getString("AmmoId").equals("qkl:16mm")?s.getCount():0).sum()==ammo.output().getCount(),"Ammo ID and batch count are preserved");
        var attachment=WeaponPlatform.entries(p,"attachment","").stream().filter(e->e.gate().id().equals("tacz:scope_98k")).findFirst().orElseThrow();
        h.assertTrue(WeaponPlatform.craft(p,pos,attachment.recipeId()).contains("unavailable"),"Ammo platform cannot craft attachment recipes");
        p.getInventory().clearContent();equip(p,"tacz:kar98");h.getLevel().setBlock(pos,ArsenalBeacon.ATTACHMENT_PLATFORM.get().defaultBlockState().setValue(WeaponPlatform.AGE,2),3);supply(p,WeaponPlatform.costs(attachment));
        h.assertTrue(WeaponPlatform.craft(p,pos,attachment.recipeId()).startsWith("Crafted"),"Historical attachment unlocks at Age 2");
        h.assertTrue(p.getInventory().items.stream().anyMatch(s->s.getOrCreateTag().getString("AttachmentId").equals("tacz:scope_98k")),"Attachment keeps its indexed identity");p.getInventory().clearContent();h.succeed();
    }
    @GameTest(template="empty3x3x3",batch="platform",timeoutTicks=40)
    public static void noPartialPaymentOrOutputOverflowAndAgeRepairsDoNotRefundUpgrades(GameTestHelper h){
        var p=player(h,"atomic");p.getInventory().add(new ItemStack(Items.IRON_INGOT,10));
        var before=p.getInventory().save(new net.minecraft.nbt.ListTag());
        h.assertTrue(!WeaponPlatform.transact(p,List.of(new WeaponPlatform.Cost(Ingredient.of(Items.IRON_INGOT),8),new WeaponPlatform.Cost(Ingredient.of(Items.IRON_INGOT),8)),new ItemStack(Items.DIAMOND))&&before.equals(p.getInventory().save(new net.minecraft.nbt.ListTag())),"Overlapping inputs cannot double-spend or consume partial payment");
        for(int i=0;i<36;i++)p.getInventory().items.set(i,new ItemStack(Items.COBBLESTONE,64));p.getInventory().items.set(0,new ItemStack(Items.IRON_INGOT,64));before=p.getInventory().save(new net.minecraft.nbt.ListTag());
        h.assertTrue(!WeaponPlatform.transact(p,List.of(WeaponPlatform.cost("minecraft:iron_ingot",1)),new ItemStack(Items.DIAMOND))&&before.equals(p.getInventory().save(new net.minecraft.nbt.ListTag())),"Full inventory refuses crafting without consuming anything");
        var pos=h.absolutePos(new BlockPos(1,1,1));var d=new CampaignData();d.beacon=pos.west();d.phase="raid";var state=ArsenalBeacon.GUN_PLATFORM.get().defaultBlockState().setValue(WeaponPlatform.AGE,4);h.getLevel().setBlock(pos,state,3);d.snapshot.put(pos.asLong(),state);ArsenalBeacon.damageBlock(h.getLevel(),d,pos);d.phase="restore";ArsenalBeacon.restore(h.getLevel(),d);
        h.assertTrue(h.getLevel().getBlockState(pos).getValue(WeaponPlatform.AGE)==4,"Victory repair preserves station Age without duplicating upgrade materials");p.getInventory().clearContent();h.succeed();
    }
    @GameTest(template="empty3x3x3",batch="platform",timeoutTicks=80)
    public static void everyValidLoadedGunAmmoAndAttachmentHasAPlatformRecipe(GameTestHelper h)throws Exception{
        var p=player(h,"coverage");int total=0;
        for(String kind:List.of("gun","ammo","attachment")){
            var available=new HashSet<String>();for(var entry:WeaponPlatform.entries(p,kind,""))available.add(entry.gate().id());
            String name=kind.substring(0,1).toUpperCase(Locale.ROOT)+kind.substring(1);var api=Class.forName("com.tacz.guns.api.TimelessAPI");
            @SuppressWarnings("unchecked") var indices=(Set<Map.Entry<ResourceLocation,?>>)api.getMethod("getAllCommon"+name+"Index").invoke(null);
            for(var entry:indices)h.assertTrue(available.contains(entry.getKey().toString()),"No missing platform recipe for valid "+kind+" "+entry.getKey());
            total+=indices.size();com.mojang.logging.LogUtils.getLogger().info("Platform coverage: {} valid {} definitions, {} recipe results",indices.size(),kind,available.size());
        }
        h.assertTrue(total>=680,"All installed gunpack families participate in coverage checks");h.succeed();
    }
    private static void equip(ServerPlayer p,String id)throws Exception{
        var gun=WeaponPlatform.entries(p,"gun","").stream().filter(e->e.gate().id().equals(id)).findFirst().orElseThrow().output().copy();
        p.getInventory().selected=0;p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,gun);
    }
    @GameTest(template="empty3x3x3",batch="platform",timeoutTicks=40)
    public static void liveStationScorePersistsAndRequestsNeedNearbySession(GameTestHelper h)throws Exception{
        var p=player(h,"session");var pos=h.absolutePos(new BlockPos(1,1,1));var d=new CampaignData();d.beacon=pos.west();
        h.getLevel().setBlock(pos,ArsenalBeacon.GUN_PLATFORM.get().defaultBlockState().setValue(WeaponPlatform.AGE,3),3);
        var registry=PlatformRegistry.get(h.getLevel());h.assertTrue(registry.score(d)>=180,"Placed platforms immediately raise the live score preview");
        var saved=PlatformRegistry.load(registry.save(new CompoundTag()));h.assertTrue(saved.score(d)==registry.score(d),"Live contribution persists across reload");
        var recipe=WeaponPlatform.entries(p,"gun","").stream().filter(e->e.gate().id().equals("qkl:fk15p")).findFirst().orElseThrow();supply(p,WeaponPlatform.costs(recipe));var before=p.getInventory().save(new net.minecraft.nbt.ListTag());
        WeaponPlatform.send(p,new WeaponPlatform.Request("craft","",0,recipe.recipeId().toString()),false,"");
        h.assertTrue(before.equals(p.getInventory().save(new net.minecraft.nbt.ListTag())),"Forged request without an opened station cannot consume or craft");
        var station=(WeaponPlatform.Station)ArsenalBeacon.GUN_PLATFORM.get();p.moveTo(pos.getX()+.5,pos.getY()+1,pos.getZ()+.5,0,0);
        station.use(h.getLevel().getBlockState(pos),h.getLevel(),pos,p,net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),net.minecraft.core.Direction.UP,pos,false));
        p.moveTo(pos.getX()+100,pos.getY(),pos.getZ(),0,0);WeaponPlatform.send(p,new WeaponPlatform.Request("craft","",Integer.MAX_VALUE,recipe.recipeId().toString()),false,"");
        h.assertTrue(before.equals(p.getInventory().save(new net.minecraft.nbt.ListTag())),"Moving away invalidates the station session before crafting");p.getInventory().clearContent();h.succeed();
    }
    @GameTest(template="empty3x3x3",batch="platform",timeoutTicks=40)
    public static void nativeWorkbenchesCannotBypassAgesAndPagesAreBounded(GameTestHelper h)throws Exception{
        var p=player(h,"native");var type=Class.forName("com.tacz.guns.inventory.GunSmithTableMenu");
        var menu=(net.minecraft.world.inventory.AbstractContainerMenu)type.getConstructor(int.class,net.minecraft.world.entity.player.Inventory.class,ResourceLocation.class).newInstance(1,p.getInventory(),new ResourceLocation("tacz:gun_smith_table"));
        p.containerMenu=menu;net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.player.PlayerContainerEvent.Open(p,menu));
        h.assertTrue(p.containerMenu==p.inventoryMenu,"Native gunpack menus close server-side before crafting packets can bypass Ages");
        var pos=h.absolutePos(new BlockPos(1,1,1));h.getLevel().setBlock(pos,ArsenalBeacon.GUN_PLATFORM.get().defaultBlockState(),3);
        var n=WeaponPlatform.pageState(p,pos,new WeaponPlatform.Request("browse","",Integer.MAX_VALUE,""));
        h.assertTrue(n.getList("recipes",net.minecraft.nbt.Tag.TAG_COMPOUND).size()<=WeaponPlatform.PAGE_SIZE&&n.getInt("page")<n.getInt("total"),"Huge page requests clamp; only the bounded default page crosses the network");
        var buffer=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());WeaponPlatform.State.encode(new WeaponPlatform.State(n,true,""),buffer);var decoded=WeaponPlatform.State.decode(buffer);buffer.release();
        h.assertTrue(decoded.data().equals(n)&&decoded.open(),"Material icons, counts and Age gates survive packet encoding");h.succeed();
    }
    @GameTest(template="empty3x3x3",batch="platform",timeoutTicks=40)
    public static void movingAPlatformPreservesAgeWithoutDuplicatingTheStation(GameTestHelper h){
        var p=player(h,"pickup");var pos=h.absolutePos(new BlockPos(1,1,1));var state=ArsenalBeacon.GUN_PLATFORM.get().defaultBlockState().setValue(WeaponPlatform.AGE,4);
        h.getLevel().setBlock(pos,state,3);var drops=net.minecraft.world.level.block.Block.getDrops(state,h.getLevel(),pos,null,p,new ItemStack(Items.DIAMOND_PICKAXE));
        h.assertTrue(drops.size()==1&&drops.get(0).getCount()==1&&drops.get(0).getOrCreateTag().getCompound("BlockStateTag").getString("age").equals("4"),"Mining returns one Age 4 platform, not a refund of upgrade parts");
        h.getLevel().setBlock(pos,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);h.getLevel().setBlock(pos.below(),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);p.getInventory().selected=0;p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,drops.get(0));p.moveTo(pos.getX()+5,pos.getY(),pos.getZ(),0,0);
        var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos.below()).add(0,.5,0),net.minecraft.core.Direction.UP,pos.below(),false);
        var item=(BlockItem)drops.get(0).getItem();var result=item.useOn(new net.minecraft.world.item.context.UseOnContext(p,net.minecraft.world.InteractionHand.MAIN_HAND,hit));
        h.assertTrue(result.consumesAction()&&h.getLevel().getBlockState(pos).getValue(WeaponPlatform.AGE)==4&&p.getMainHandItem().isEmpty(),"Replanting consumes the one item and preserves station Age");
        var saved=PlatformRegistry.get(h.getLevel()).save(new CompoundTag());boolean tracked=false;for(var entry:saved.getList("stations",net.minecraft.nbt.Tag.TAG_COMPOUND)){var row=(CompoundTag)entry;if(row.getLong("pos")==pos.asLong()&&row.getInt("value")==4)tracked=true;}
        h.assertTrue(tracked,"Live scoring tracks final placed NBT Age, not the temporary default Age");h.succeed();
    }
}
