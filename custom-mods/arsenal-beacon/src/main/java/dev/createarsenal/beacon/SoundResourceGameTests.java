package dev.createarsenal.beacon;

import java.util.*;
import java.nio.file.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.resource.PathPackResources;

@GameTestHolder(ArsenalBeacon.ID)
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
public final class SoundResourceGameTests {
    @GameTest(template="empty3x3x3",batch="sounds0131",timeoutTicks=100)
    public static void nativeSoundEnumerationSkipsMalformedNamesWithoutDisablingParticles(GameTestHelper h)throws Exception{
        h.assertTrue(ModList.get().isLoaded("modernfix")&&ModList.get().isLoaded("aaa_particles"),"Reproduce the actual client resource-cache mod combination");
        Path root=ModList.get().getModFileById("lradd").getFile().findResource("assets","lradd","custom","lradd_default_gun");
        Path broken=root.resolve("assets/lrl/tacz_sounds/p320_doctor/foley_classes_props_c101_medicalgun_s_magin_02_r1 .ogg");
        h.assertTrue(Files.exists(broken),"The original unmodified author archive contains the reported malformed sound");
        var lrl=new PathPackResources("arsenal-lrl-sound-regression",false,root);
        Path daffas=ModList.get().getModFileById("daffas_arsenal").getFile().findResource("addon","daffas");
        var daffasPack=new PathPackResources("arsenal-daffas-sound-regression",false,daffas);
        var helldivers=new FilePackResources("arsenal-helldivers-sound-regression",Path.of("tacz","[eof]helldivers_gun_pack.zip").toFile(),false);
        try(var manager=new MultiPackResourceManager(PackType.CLIENT_RESOURCES,List.of(lrl,daffasPack,helldivers))){
            // This is the exact common FallbackResourceManager path used by the client SoundManager.
            var sounds=manager.listResources("tacz_sounds",id->id.getPath().endsWith(".ogg"));
            h.assertTrue(sounds.size()>100,"Valid sounds from all three native author packs remain available: "+sounds.size());
            h.assertTrue(sounds.keySet().stream().noneMatch(id->SoundResourcePaths.malformedSoundPath(id.getPath())),"No malformed sound reaches vanilla metadata lookup");
            int checked=0;for(var entry:sounds.entrySet()){try(var stream=entry.getValue().open()){h.assertTrue(stream.read()>=0,"Sound audio bytes remain readable");}entry.getValue().metadata();if(++checked==10)break;}
            com.mojang.logging.LogUtils.getLogger().info("0.13.1 native resource regression: {} valid gun sounds; malformed filenames safely excluded",sounds.size());
        }
        var engine=Class.forName("org.embeddedt.modernfix.resources.PackResourcesCacheEngine");var validation=engine.getDeclaredMethod("isValidCachedResourcePath",Path.class);validation.setAccessible(true);
        h.assertTrue(!(boolean)validation.invoke(null,Path.of("lrl/tacz_sounds/p320_doctor/bad name.ogg")),"Actual transformed cache method rejects the offending path");
        h.assertTrue(!(boolean)validation.invoke(null,Path.of("ww/recipes/bayonet_m1930 - Copy.json")),"Malformed recipe filenames cannot crash world datapack loading either");
        h.assertTrue((boolean)validation.invoke(null,Path.of("zeta/effeks/ARC Flash/Texture 01.png")),"AAA Particles still accepts its special effect filenames through ModernFix");
        h.succeed();
    }
}
