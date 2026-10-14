package dev.eternaldefense.beacon;

import net.minecraft.commands.Commands;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLConstructModEvent;

/** Development runs only: registers the GameTests and test runners enabled by -Deternaldefense.* switches. */
@Mod.EventBusSubscriber(modid=EternalDefense.ID,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class GameTestSetup {
    private GameTestSetup(){}
    static boolean on(String name){return Boolean.getBoolean("eternaldefense."+name);}
    @SubscribeEvent public static void construct(FMLConstructModEvent e){
        var bus=MinecraftForge.EVENT_BUS;
        if(on("integrationTests")){
            bus.register(Integration.class);
        }
        if(on("messHallTests")){
            bus.register(new MessHallGameTests.KitchenTestRunner());bus.register(new MessHallV2GameTests.KitchenV2TestRunner());bus.register(new MessHallV4GameTests.Runner());
            bus.register(new MessHallV5GameTests.KitchenV5Verification());bus.register(new MessHallV6GameTests.KitchenV6Verification());
        }
        if(on("stuckTests"))bus.register(new RaiderGateGameTests.Runner());
        if(on("optimizeTests"))bus.register(new OptimizePathGameTests.Runner());
        if(on("huntTests"))bus.register(new HuntGameTests.Runner());
        if(on("fobTests"))bus.register(new FobGameTests.Runner());
        if(on("v5ClientTests"))bus.register(new MealV5ClientSmoke());
        if(on("v6ClientTests"))bus.register(new MealV6ClientSmoke());
        if(on("v4ClientTests"))bus.register(new MealV4ClientSmoke());
        if(on("kitchenSmoke"))bus.register(new MessHallSmoke());
    }
    /** The integration server: every GameTest class, the test command and its own start command. */
    public static final class Integration {
        private Integration(){}
        @SubscribeEvent public static void commands(RegisterCommandsEvent event){
            if(GameTestRegistry.getAllTestFunctions().isEmpty())GameTestRegistry.register(BeaconGameTests.class);
            for(var c:new Class<?>[]{PlatformGameTests.class,CompatibilityGameTests.class,UpgradeGameTests.class,ReleaseSevenGameTests.class,CampaignEightGameTests.class,
                    ReleaseNineGameTests.class,RemodelGameTests.class,ReleaseElevenGameTests.class,ReleaseTwelveGameTests.class,ReleaseThirteenGameTests.class,
                    SoundResourceGameTests.class,GunPackSyncGameTests.class,BrowserGameTests.class,DisplayRackGameTests.class,SpawnSixteenGameTests.class,
                    ArmorGameTests.class,AmmoCatalogueGameTests.class})GameTestRegistry.register(c);
            net.minecraft.gametest.framework.TestCommand.register(event.getDispatcher());
            event.getDispatcher().register(Commands.literal("eternaldefense-integration").requires(s->s.hasPermission(2)).executes(c->{IntegrationTests.start(c.getSource().getLevel());return 1;}));
        }
        @SubscribeEvent public static void tick(TickEvent.ServerTickEvent e){if(e.phase==TickEvent.Phase.END)IntegrationTests.tick();}
    }
}
