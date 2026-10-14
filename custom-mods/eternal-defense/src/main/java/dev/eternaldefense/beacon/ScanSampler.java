package dev.eternaldefense.beacon;

/** The Command Table's satellite scan, as pure arithmetic (the world side is TacticalScan). */
final class ScanSampler {
    private ScanSampler(){}
    static final byte BIOME=0,STALE=1,LIVE=2;
    /** Vanilla map brightness ids (MapColor.Brightness): LOW 0, NORMAL 1, HIGH 2. */
    static final int LOW=0,NORMAL=1,HIGH=2;
    /** Vanilla's water colour id (MapColor.WATER), for depth shading. */
    static final int WATER=12;

    /** The world under an image. */
    interface Terrain {
        long column(int x,int z);
        /** The packed colour of the biome at a column (from the generator, never loading anything). */
        int biome(int x,int z);
    }
    /** A loaded column: its top height, the water depth over the floor (0 on land) and its colour id. */
    static long column(int height,int waterDepth,int colorId){return ((long)(height+HEIGHT_OFFSET)<<20)|((long)Math.max(0,Math.min(255,waterDepth))<<8)|(colorId&0xff);}
    /** Heights are stored shifted, so a column below y 0 is still a non-negative number */
    private static final int HEIGHT_OFFSET=4096;
    static int height(long column){return (int)(column>>20)-HEIGHT_OFFSET;}
    static int depth(long column){return (int)((column>>8)&0xff);}
    static int colorId(long column){return (int)(column&0xff);}
    static int pack(int colorId,int brightness){return ((colorId&0x3f)<<2)|(brightness&3);}

    /** Vanilla's land shading: brighter facing a lower northern neighbour, darker facing a higher */
    static int brightness(int height,int northHeight,int scale,int x,int z){
        double d=(height-northHeight)*4.0/(scale+4)+(((x+z)&1)-.5)*.4;
        return d>.6?HIGH:d<-.6?LOW:NORMAL;
    }
    /** Vanilla's water shading: deeper is darker. */
    static int waterBrightness(int depth,int x,int z){double d=depth*.1+((x+z)&1)*.2;return d<.5?HIGH:d>.9?LOW:NORMAL;}

    /** Samples row row of an image centred on (centerX, centerZ) with scale blocks a pixel. */
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
                if(fresh[i]==LIVE||fresh[i]==STALE)fresh[i]=STALE;     // Seen before: keep the last colour, dimmed on screen
                else{colors[i]=(byte)biomes[Math.min(biomes.length-1,col/step)];fresh[i]=BIOME;}
            }
        }
    }
    /** The block column under a pixel of an image (for the screen's labels and the tests). */
    static int worldX(int centerX,int scale,int col){return centerX+(col-TacticalRules.SCAN_SIZE/2)*scale+scale/2;}
}
