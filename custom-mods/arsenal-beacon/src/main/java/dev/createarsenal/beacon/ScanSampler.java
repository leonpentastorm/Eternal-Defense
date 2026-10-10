package dev.createarsenal.beacon;

/**
 * The Command Table's satellite scan, as pure arithmetic (the world side is {@link TacticalScan}). An image is 256 x 256 pixels, centred
 * exactly on the beacon, in vanilla's map palette (a packed colour: colour id times four plus a brightness), with a freshness per pixel:
 * <ul><li>{@link #LIVE}: from a chunk that is loaded right now (no chunk is ever loaded for a scan);</li>
 * <li>{@link #STALE}: not loaded now, but seen live by an earlier scan, which keeps its last colour;</li>
 * <li>{@link #BIOME}: never seen: a coarse preview from the world generator's biome data, as vanilla's explorer maps draw it.</li></ul>
 * Vanilla's own map update cannot be used: it keeps a map on a fixed grid (not centred on the beacon), only updates around whoever holds the
 * map, and asks for chunks in a way that can load them.
 */
final class ScanSampler {
    private ScanSampler(){}
    static final byte BIOME=0,STALE=1,LIVE=2;
    /** Vanilla map brightness ids (MapColor.Brightness): LOW 0, NORMAL 1, HIGH 2. */
    static final int LOW=0,NORMAL=1,HIGH=2;
    /** Vanilla's water colour id (MapColor.WATER), for depth shading. */
    static final int WATER=12;

    /** The world under an image. A column is {@code -1} when its chunk is not loaded, else {@link #column(int, int, int)}. */
    interface Terrain {
        long column(int x,int z);
        /** The packed colour of the biome at a column (from the generator, never loading anything). */
        int biome(int x,int z);
    }
    /** A loaded column: its top height, the water depth over the floor (0 on land) and its colour id. */
    static long column(int height,int waterDepth,int colorId){return ((long)(height+HEIGHT_OFFSET)<<20)|((long)Math.max(0,Math.min(255,waterDepth))<<8)|(colorId&0xff);}
    /** Heights are stored shifted, so a column below y 0 is still a non-negative number (negative means "not loaded"). */
    private static final int HEIGHT_OFFSET=4096;
    static int height(long column){return (int)(column>>20)-HEIGHT_OFFSET;}
    static int depth(long column){return (int)((column>>8)&0xff);}
    static int colorId(long column){return (int)(column&0xff);}
    static int pack(int colorId,int brightness){return ((colorId&0x3f)<<2)|(brightness&3);}

    /** Vanilla's land shading: brighter facing a lower northern neighbour, darker facing a higher one, with a checker dither. */
    static int brightness(int height,int northHeight,int scale,int x,int z){
        double d=(height-northHeight)*4.0/(scale+4)+(((x+z)&1)-.5)*.4;
        return d>.6?HIGH:d<-.6?LOW:NORMAL;
    }
    /** Vanilla's water shading: deeper is darker. */
    static int waterBrightness(int depth,int x,int z){double d=depth*.1+((x+z)&1)*.2;return d<.5?HIGH:d>.9?LOW:NORMAL;}

    /**
     * Samples row {@code row} of an image centred on {@code (centerX, centerZ)} with {@code scale} blocks a pixel. {@code colors}/{@code fresh}
     * hold the last scan and are overwritten; {@code north} keeps the heights of the row above (Integer.MIN_VALUE: unknown) and is updated;
     * {@code biomes} caches one biome colour per four pixels across the current band of four rows.
     */
    static void row(Terrain terrain,int centerX,int centerZ,int scale,int row,byte[] colors,byte[] fresh,int[] north,int[] biomes){
        int size=TacticalRules.SCAN_SIZE,half=size/2,step=TacticalRules.BIOME_STEP;
        int z=centerZ+(row-half)*scale+scale/2;
        if(row%step==0)for(int i=0;i<biomes.length;i++){int col=i*step;biomes[i]=terrain.biome(centerX+(col-half)*scale+scale/2,centerZ+(row-half)*scale+scale/2);}
        for(int col=0;col<size;col++){
            int x=centerX+(col-half)*scale+scale/2,i=row*size+col;
            long c=terrain.column(x,z);int above=north[col];
            if(c>=0){
                int h=height(c);
                int b=colorId(c)==WATER&&depth(c)>0?waterBrightness(depth(c),x,z):brightness(h,above==Integer.MIN_VALUE?h:above,scale,x,z);
                colors[i]=(byte)pack(colorId(c),b);fresh[i]=LIVE;north[col]=h;
            }else{
                north[col]=Integer.MIN_VALUE;
                if(fresh[i]==LIVE||fresh[i]==STALE)fresh[i]=STALE;     // seen before: keep the last colour, dimmed on screen
                else{colors[i]=(byte)biomes[Math.min(biomes.length-1,col/step)];fresh[i]=BIOME;}
            }
        }
    }
    /** The block column under a pixel of an image (for the screen's labels and the tests). */
    static int worldX(int centerX,int scale,int col){return centerX+(col-TacticalRules.SCAN_SIZE/2)*scale+scale/2;}
}
