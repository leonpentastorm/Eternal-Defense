package dev.eternaldefense.beacon;

/** Ordinary gun sounds obey vanilla resource names; Effekseer names are left alone. */
public final class SoundResourcePaths {
    private SoundResourcePaths(){}
    public static boolean malformedCachedPath(String path){
        String name=path.replace('\\','/');
        if(name.startsWith("effeks/")||name.contains("/effeks/"))return false;
        return !plain(name);
    }
    public static boolean malformedSoundPath(String path){
        String name=path.replace('\\','/');
        return (name.startsWith("tacz_sounds/")||name.contains("/tacz_sounds/"))&&!plain(name);
    }
    /** Pure: is name a non-empty run of a-z, 0-9, '/', '.', '_' and '-' (what vanilla allows in a resource path)? */
    static boolean plain(String name){
        if(name.isEmpty())return false;
        for(int i=0;i<name.length();i++){char c=name.charAt(i);if(!(c>='a'&&c<='z'||c>='0'&&c<='9'||c=='/'||c=='.'||c=='_'||c=='-'))return false;}
        return true;
    }
}
