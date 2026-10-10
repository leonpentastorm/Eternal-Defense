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
    /** 0.0.15 replaced the regular expression with a plain character check: it must answer exactly like the pattern did. */
    @Test void thePlainCheckAnswersExactlyLikeTheOldPattern(){
        var pattern=java.util.regex.Pattern.compile("[a-z0-9/._-]+");
        assertFalse(SoundResourcePaths.plain(""));assertFalse(pattern.matcher("").matches());
        for(int c=0;c<=0xFFFF;c++){String one=String.valueOf((char)c);assertEquals(pattern.matcher(one).matches(),SoundResourcePaths.plain(one),"character "+c);}
        String alphabet="abcxyz0189/._-AZ:\\ é`{@[]~\uD83D\uDE00";var r=new java.util.Random(15);
        for(int i=0;i<50_000;i++){
            var b=new StringBuilder();int n=r.nextInt(40);for(int k=0;k<n;k++)b.append(alphabet.charAt(r.nextInt(r.nextInt(4)==0?alphabet.length():17)));
            String path=b.toString();assertEquals(pattern.matcher(path).matches(),SoundResourcePaths.plain(path),path);
        }
        for(String path:java.util.List.of("lrl/tacz_sounds/p320_doctor/reload.ogg","ww/recipes/bayonet_m1930 - Copy.json","zeta/effeks/ARC Flash/Texture 01.png","a","-","/","Ab"))
            assertEquals(pattern.matcher(path).matches(),SoundResourcePaths.plain(path),path);
    }
}
