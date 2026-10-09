package dev.createarsenal.beacon;

/**
 * Pure: does a straight line of surface samples cross terrain a ground raider cannot cross? One helper serves two questions: the
 * look-ahead of a marching raider (about ten blocks in front of it, one sample per block) and the check of a gate's destination (the
 * whole way from the destination to the zone, one sample every {@link RaidMarch#DESTINATION_SPACING} blocks).
 * <p>Samples are in the order of the march: index 0 is the start. A sample whose chunk is not loaded, or whose top block is not the
 * natural ground (a tree trunk, a block a player placed), is {@link #UNKNOWN}: it is never "blocked" and it breaks a run of fluid.
 */
final class TerrainProbe {
    private TerrainProbe(){}
    /** Surface height of a sample that could not be read. */
    static final int UNKNOWN=Integer.MIN_VALUE;
    /** What stops a raider, in the order they are reported when several are present. */
    enum Hazard{NONE,LAVA,DROP,FLUID}

    /**
     * @param surface surface height of each sample ({@link #UNKNOWN} when not readable)
     * @param fluid   the surface is water (or another fluid but lava): a raider wades through a short stretch of it
     * @param lava    the surface is lava or a magma block: always a hazard
     * @param spacing blocks between two samples
     */
    static Hazard probe(int[] surface,boolean[] fluid,boolean[] lava,int spacing){
        int n=surface.length;
        for(int i=0;i<n;i++)if(surface[i]!=UNKNOWN&&lava[i])return Hazard.LAVA;
        for(int i=1;i<n;i++)if(surface[i-1]!=UNKNOWN&&surface[i]!=UNKNOWN&&surface[i-1]-surface[i]>=RaidMarch.GATE_DROP_BLOCKS)return Hazard.DROP;
        int run=0;
        for(int i=0;i<n;i++){
            run=surface[i]!=UNKNOWN&&fluid[i]&&!lava[i]?run+1:0;
            if(spanOf(run,spacing)>=RaidMarch.GATE_FLUID_SPAN)return Hazard.FLUID;
        }
        return Hazard.NONE;
    }
    /** Pure: the least width, in blocks, of a stretch of {@code samples} wet samples in a row: the first and last are {@code spacing} apart per step, plus the first block. */
    static int spanOf(int samples,int spacing){return samples<=0?0:(samples-1)*Math.max(1,spacing)+1;}
}
