package dev.createarsenal.beacon;

/** Baked into each release. A missing pack cannot silently change saved progression. */
public final class BuildFlavor {
    public static final boolean STANDALONE;
    static {
        var properties=new java.util.Properties();
        try(var in=BuildFlavor.class.getResourceAsStream("/arsenal-build.properties")){
            if(in==null)throw new IllegalStateException("Missing release flavor");
            properties.load(in);
        }catch(java.io.IOException ex){throw new IllegalStateException(ex);}
        STANDALONE=properties.getProperty("flavor").equals("standalone");
    }
}
