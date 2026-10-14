package dev.eternaldefense.beacon;

import java.util.*;

/**
 * 0.0.40: the supply source's rules as plain numbers, so they can be tested alone. A request ("these items for this player, here") is
 * paid from sources: the player's pack first, then the Logistics Center Hubs of the Home Zone, nearest first. {@link Supply} turns real
 * inventories into these numbers and back.
 */
final class SupplyRules {
    private SupplyRules(){}

    /** A unit from a Hub costs this much more than one from the pack, so the pack always pays first; the Hub's rank is added on top. */
    static final long HUB_UNIT=1_000_000L;
    /** Source tier: 0 is the player's pack, 1 the nearest Hub, 2 the next one ... */
    static long weight(int tier){return tier<=0?0:HUB_UNIT+tier;}

    /** {@code taken[s][c]}: units source {@code s} pays toward cost {@code c}; {@code got[c]}: units each cost received. */
    record Allocation(int[][] taken,int[] got,boolean payable){
        /** Units taken from source {@code s} in all. */
        int from(int s){int n=0;for(int v:taken[s])n+=v;return n;}
    }

    /**
     * Pays every cost from the sources it accepts: as many units as there are, with the fewest from Hubs and, among those, the nearest Hubs
     * first (a minimum-cost maximum flow, so overlapping ingredients such as tags never block a request that could be paid). Payable only
     * when every cost gets its full amount; the caller then consumes exactly {@code taken}, otherwise nothing.
     * @param supply units at each source
     * @param tier   each source's tier ({@link #weight})
     * @param demand units each cost needs (0 or less makes the request unpayable)
     * @param accepts {@code accepts[s][c]}: source {@code s} may pay cost {@code c}
     */
    static Allocation allocate(int[] supply,int[] tier,int[] demand,boolean[][] accepts){
        int sources=supply.length,costs=demand.length;
        var taken=new int[sources][costs];var got=new int[costs];
        boolean valid=costs>0;for(int d:demand)if(d<=0)valid=false;
        if(!valid)return new Allocation(taken,got,costs==0);
        // nodes: 0 source, 1..S the sources, S+1..S+C the costs, S+C+1 the sink
        int n=sources+costs+2,sink=n-1;var graph=new Graph(n);
        int[] supplyEdge=new int[sources];int[][] payEdge=new int[sources][costs];
        for(int s=0;s<sources;s++){supplyEdge[s]=graph.add(0,1+s,Math.max(0,supply[s]),weight(tier[s]));for(int c=0;c<costs;c++)payEdge[s][c]=accepts[s][c]?graph.add(1+s,1+sources+c,Math.max(0,supply[s]),0):-1;}
        long total=0;for(int c=0;c<costs;c++){graph.add(1+sources+c,sink,demand[c],0);total+=demand[c];}
        long flow=graph.minCostMaxFlow(0,sink);
        for(int s=0;s<sources;s++)for(int c=0;c<costs;c++)if(payEdge[s][c]>=0){taken[s][c]=graph.flow(payEdge[s][c]);got[c]+=taken[s][c];}
        return new Allocation(taken,got,flow==total);
    }

    /** Successive shortest paths (Bellman-Ford, the graphs are small: a pack, a few Hubs' item kinds and a handful of costs). */
    private static final class Graph {
        final int n;final List<int[]> edges=new ArrayList<>();final List<List<Integer>> out=new ArrayList<>();final List<Long> cost=new ArrayList<>();
        Graph(int n){this.n=n;for(int i=0;i<n;i++)out.add(new ArrayList<>());}
        /** Returns the forward edge's index; edge = {to, capacity, original capacity}. */
        int add(int from,int to,int capacity,long c){
            int id=edges.size();edges.add(new int[]{to,capacity,capacity});cost.add(c);out.get(from).add(id);
            edges.add(new int[]{from,0,0});cost.add(-c);out.get(to).add(id+1);return id;
        }
        int flow(int edge){return edges.get(edge)[2]-edges.get(edge)[1];}
        long minCostMaxFlow(int source,int sink){
            long flow=0;
            while(true){
                long[] dist=new long[n];Arrays.fill(dist,Long.MAX_VALUE);dist[source]=0;int[] via=new int[n];Arrays.fill(via,-1);
                boolean changed=true;
                for(int round=0;round<n&&changed;round++){changed=false;
                    for(int u=0;u<n;u++){if(dist[u]==Long.MAX_VALUE)continue;
                        for(int id:out.get(u)){var e=edges.get(id);if(e[1]<=0)continue;long d=dist[u]+cost.get(id);if(d<dist[e[0]]){dist[e[0]]=d;via[e[0]]=id;changed=true;}}}
                }
                if(dist[sink]==Long.MAX_VALUE)return flow;
                int push=Integer.MAX_VALUE;for(int v=sink;v!=source;v=edges.get(via[v]^1)[0])push=Math.min(push,edges.get(via[v])[1]);
                for(int v=sink;v!=source;v=edges.get(via[v]^1)[0]){edges.get(via[v])[1]-=push;edges.get(via[v]^1)[1]+=push;}
                flow+=push;
            }
        }
    }

    // ---- the Hub's chat line ---------------------------------------------------------------------------------------------------------
    /** How long, in ticks, the same player's pulls keep adding to one running total before the line is sent (2 s). */
    static final int MERGE_TICKS=40;
    /** Item kinds named in one line; the rest become "and N more". */
    static final int NAMED=3;
    /** One kind of item pulled from the Hub: {@code key} tells kinds apart, {@code count} is how many. */
    record Pulled(String key,int count){}
    /** Adds {@code more} to a running total: the same kind adds up, a new kind joins at the end (first pulled, first named). */
    static List<Pulled> merge(List<Pulled> running,List<Pulled> more){
        var map=new LinkedHashMap<String,Integer>();for(var p:running)map.merge(p.key(),p.count(),Integer::sum);for(var p:more)if(p.count()>0)map.merge(p.key(),p.count(),Integer::sum);
        var out=new ArrayList<Pulled>();map.forEach((k,v)->out.add(new Pulled(k,v)));return out;
    }
    /** True while a pull at {@code now} still belongs to the running total last added to at {@code last}. */
    static boolean sameRun(long last,long now){return now-last<=MERGE_TICKS;}
    /** True when the running total last added to at {@code last} is due to be sent at {@code now}. */
    static boolean due(long last,long now){return now-last>MERGE_TICKS;}

    // ---- the Hub's Home Zone check -----------------------------------------------------------------------------------------------------------
    /** A Hub checks once a second whether it may work (inside the Home Zone of a standing beacon). */
    static final int ZONE_CHECK_TICKS=20;
    /** Not checked yet (a Hub just placed or loaded). */
    static final long NEVER=Long.MIN_VALUE;
    /** True when a Hub last checked at {@code checkedAt} must check again at {@code now} (also when the clock went back). */
    static boolean recheck(long checkedAt,long now){return checkedAt==NEVER||now<checkedAt||now-checkedAt>=ZONE_CHECK_TICKS;}
    /** The kinds a line names (at most {@link #NAMED}) and how many more kinds it only counts. */
    record Line(List<Pulled> named,int more){}
    static Line line(List<Pulled> items){
        var named=new ArrayList<Pulled>();for(var p:items)if(p.count()>0&&named.size()<NAMED)named.add(p);
        int kinds=0;for(var p:items)if(p.count()>0)kinds++;
        return new Line(named,kinds-named.size());
    }
}
