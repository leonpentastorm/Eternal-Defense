package dev.createarsenal.beacon;

/** Ordinary gun sounds obey vanilla resource names; Effekseer names are left alone. */
public final class SoundResourcePaths {
    private SoundResourcePaths(){}
    public static boolean malformedCachedPath(String path){
        String name=path.replace('\\','/');
        if(name.startsWith("effeks/")||name.contains("/effeks/"))return false;
        return !name.matches("[a-z0-9/._-]+");
    }
    public static boolean malformedSoundPath(String path){
        String name=path.replace('\\','/');
        return (name.startsWith("tacz_sounds/")||name.contains("/tacz_sounds/"))&&!name.matches("[a-z0-9/._-]+");
    }
}
