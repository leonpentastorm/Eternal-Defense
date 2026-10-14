package dev.eternaldefense.beacon;

/** Pure: does a straight line of surface samples cross terrain a ground raider cannot cross? */
final class TerrainProbe {
    private TerrainProbe(){}
    /** Surface height of a sample that could not be read. */
    static final int UNKNOWN=Integer.MIN_VALUE;
    /** What stops a raider, in the order they are reported when several are present. */
    enum Hazard{NONE,LAVA,DROP,FLUID}

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
    /** Pure: the least width, in blocks, of a stretch of samples wet samples in a row */
    static int spanOf(int samples,int spacing){return samples<=0?0:(samples-1)*Math.max(1,spacing)+1;}
}
