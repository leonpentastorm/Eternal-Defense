package dev.createarsenal.beacon;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class SoundResourcePathsTest {
    @Test void malformedSoundPathsNeverEnterTheCache(){
        assertTrue(SoundResourcePaths.malformedSoundPath("lrl/tacz_sounds/p320_doctor/foley_classes_props_c101_medicalgun_s_magin_02_r1 .ogg"));
        assertTrue(SoundResourcePaths.malformedSoundPath("zeta/tacz_sounds/arc3/File0010.ogg"));
        assertTrue(SoundResourcePaths.malformedSoundPath("daffas_arsenal/tacz_sounds/mg3/daffas_mg3_reload_RATATATA.ogg"));
    }
    @Test void validSoundsAndParticleNamingRemainUnaffected(){
        assertFalse(SoundResourcePaths.malformedSoundPath("lrl/tacz_sounds/p320_doctor/reload.ogg"));
        assertFalse(SoundResourcePaths.malformedSoundPath("zeta/effeks/ARC Flash/Texture 01.png"));
        assertFalse(SoundResourcePaths.malformedCachedPath("zeta/effeks/ARC Flash/Texture 01.png"));
        assertTrue(SoundResourcePaths.malformedCachedPath("ww/recipes/bayonet_m1930 - Copy.json"));
        assertFalse(SoundResourcePaths.malformedCachedPath("ww/recipes/bayonet_no4.json"));
        assertTrue(SoundResourcePaths.malformedSoundPath("lrl\\tacz_sounds\\p320_doctor\\Reload.ogg"));
    }
}
