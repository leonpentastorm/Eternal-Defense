package dev.createarsenal.displays;
import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
@Mod(GunDisplays.ID)
public final class GunDisplays {
    public static final String ID="arsenal_displays";
    // World compatibility: old blocks, items and block entities retain their IDs.
    private static final String CONTENT_ID="arsenal_beacon";
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,CONTENT_ID);
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,CONTENT_ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES=DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES,CONTENT_ID);
    private static final DeferredRegister<CreativeModeTab> TABS=DeferredRegister.create(Registries.CREATIVE_MODE_TAB,ID);
    public static final List<RegistryObject<Block>> RACKS=List.of(
        rack("display_stand",false,false,false,false,false),rack("display_rack",false,true,false,false,false),
        rack("display_stand_wide",true,false,false,false,false),rack("display_rack_wide",true,true,false,false,false),
        rack("display_pistol_stand",false,false,true,false,false),rack("display_pistol_rack",false,true,true,false,false),
        rack("display_case",false,false,false,true,false),rack("display_case_wide",true,false,false,true,false),
        rack("wall_case",false,true,false,true,false),rack("wall_case_wide",true,true,false,true,false),
        rack("display_heavy",true,false,false,false,true));
    public static final RegistryObject<BlockEntityType<DisplayRacks.DisplayEntity>> DISPLAY_ENTITY=ENTITIES.register("gun_display",()->BlockEntityType.Builder.of(DisplayRacks.DisplayEntity::new,RACKS.stream().map(RegistryObject::get).toArray(Block[]::new)).build(null));
    public static final RegistryObject<CreativeModeTab> TAB=TABS.register("displays",()->CreativeModeTab.builder().title(Component.translatable("itemGroup.arsenal_displays")).icon(()->new ItemStack(RACKS.get(7).get())).displayItems((p,out)->RACKS.forEach(b->out.accept(b.get()))).build());
    private static RegistryObject<Block> rack(String name,boolean wide,boolean wall,boolean compact,boolean glass,boolean heavy){RegistryObject<Block> block=BLOCKS.register(name,()->new DisplayRacks.Rack(wide,wall,compact,glass,heavy));ITEMS.register(name,()->new DisplayRacks.RackItem(block.get()));return block;}
    public GunDisplays(){var bus=FMLJavaModLoadingContext.get().getModEventBus();BLOCKS.register(bus);ITEMS.register(bus);ENTITIES.register(bus);TABS.register(bus);if(Boolean.getBoolean("arsenal.displayTests"))net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new StandaloneDisplayTests.Runner());}
}
