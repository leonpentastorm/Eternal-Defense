package dev.createarsenal.beacon;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.PacketDistributor;
import java.util.*;

/**
 * Tactical Operations on the server (0.0.20): the uplink, the board (filling it, dawn replacements, Garrison structure lookups), the table's
 * buttons, paying a cleared mission, the map and Return Flare handed to every player, and the mission sync. The warband in the world is
 * {@link HuntWarband}, the satellite scan {@link TacticalScan}. Overworld only.
 */
final class Hunts {
    private Hunts(){}

    // ---- the uplink ----------------------------------------------------------------------------------------------------------------------
    /** The link between the base's table and satellite right now: null when there is none, else the satellite's Mk. Never loads a chunk. */
    static Integer uplink(ServerLevel l,CampaignData d,HuntData h){return uplinkProblem(l,d,h)==null?TacticalRules.mk(h.satelliteMk):null;}
    /** Why there is no uplink (a language key suffix: no_beacon, no_table, no_satellite, zone, too_far, no_sky), or null when there is one. */
    static String uplinkProblem(ServerLevel l,CampaignData d,HuntData h){
        if(l.dimension()!=Level.OVERWORLD||!d.installed())return "no_beacon";
        if(h.table==null||!l.hasChunkAt(h.table)||!l.getBlockState(h.table).is(ArsenalBeacon.COMMAND_TABLE.get()))return "no_table";
        if(h.satellite==null||!l.hasChunkAt(h.satellite)||!l.getBlockState(h.satellite).is(ArsenalBeacon.SATELLITE_BEACON.get()))return "no_satellite";
        boolean zone=d.inside(h.satellite)&&BaseZone.problem(l,h.satellite)==null&&BaseZone.problem(l,h.table)==null;
        if(!zone)return "zone";
        if(!TacticalRules.uplink(h.table.distSqr(h.satellite),true,true))return "too_far";
        // the dish (the block above the satellite's anchor) must see the sky
        boolean sky=l.hasChunkAt(h.satellite.above(2))&&l.canSeeSky(h.satellite.above(2));
        return sky?null:"no_sky";
    }
    static int satelliteMk(ServerLevel l,HuntData h){return TacticalRules.mk(h.satelliteMk);}
    /** The satellite block shows the Mk the server keeps (an old block state is put right when its chunk is loaded). */
    static void showMk(ServerLevel l,HuntData h){
        if(h.satellite==null||!l.hasChunkAt(h.satellite))return;var s=l.getBlockState(h.satellite);
        if(s.is(ArsenalBeacon.SATELLITE_BEACON.get())&&s.getValue(TacticalBlocks.MK)!=TacticalRules.mk(h.satelliteMk))l.setBlock(h.satellite,s.setValue(TacticalBlocks.MK,TacticalRules.mk(h.satelliteMk)),3);
    }

    // ---- the board -----------------------------------------------------------------------------------------------------------------------
    /** Fills the board the first time there is an uplink, and the new slots after an upgrade (see {@link HuntBoard#toFill}). */
    static void fillIfLinked(ServerLevel l){
        var d=CampaignData.get(l.getServer().overworld());var h=HuntData.get(l);var mk=uplink(l,d,h);
        if(mk==null)return;
        int capacity=TacticalRules.offers(mk),wanted=h.board.toFill(capacity);
        if(wanted==0&&h.board.filled)return;
        // a second Garrison lookup in one server tick waits for the next pass (the server tick fills again within a second)
        for(int i=0;i<wanted;i++){var added=addOffer(l,d,h,mk,true);if(added==Added.LATER)return;if(added==Added.NO)break;}
        if(!h.board.filled)h.board.lastDay=TacticalRules.day(l.getDayTime());   // the first fill counts as this day's offers
        h.board.filledTo(capacity);h.setDirty();
    }
    enum Added{YES,NO,LATER}
    /** The game time of the last Garrison lookup: at most one per server tick when the board fills (each costs up to a few tens of ms). */
    private static long lookupAt=Long.MIN_VALUE;
    /**
     * One new offer: a random class the satellite can find; a Garrison needs a structure in range, else it becomes a Patrol. With
     * {@code mayWait}, a Garrison rolled in a tick that already ran a lookup is not made now ({@link Added#LATER}); the next pass rolls again.
     */
    static Added addOffer(ServerLevel l,CampaignData d,HuntData h,int mk,boolean mayWait){
        // a well-mixed seed: java.util.Random's first draws from closely related seeds come out alike (a whole board of one class)
        var random=new Random(TacticalRules.mix(l.getSeed(),h.board.nextId,l.getGameTime()));
        var kinds=TacticalRules.classes(mk);var kind=kinds.get(random.nextInt(kinds.size()));
        int range=TacticalRules.range(mk);var taken=h.board.bearings();
        TacticalRules.Inside inside=(x,z)->l.getWorldBorder().isWithinBounds(x,z);
        TacticalRules.Spot spot=null;
        if(kind==TacticalRules.Kind.GARRISON){
            if(mayWait&&lookupAt==l.getGameTime())return Added.LATER;
            lookupAt=l.getGameTime();
            spot=garrison(l,d.beacon.getX(),d.beacon.getZ(),d.radius(),range,taken,random);if(spot==null)kind=TacticalRules.Kind.PATROL;
        }
        if(spot==null)spot=TacticalRules.place(random,d.beacon.getX(),d.beacon.getZ(),d.radius(),range,taken,inside);
        if(spot==null)return Added.NO;
        long id=h.board.nextId++;
        h.board.offers.add(new HuntBoard.Offer(id,kind,TacticalRules.codename(l.getSeed()*31+id),spot.x(),spot.z(),spot.distance(),spot.bearing()));
        h.setDirty();return Added.YES;
    }
    /** Vanilla surface structures a Garrison may hold (modded structures are ignored). Their loot is never touched. */
    static final Set<ResourceLocation> GARRISON_STRUCTURES=Set.of(new ResourceLocation("pillager_outpost"),new ResourceLocation("desert_pyramid"),new ResourceLocation("jungle_pyramid"),
        new ResourceLocation("swamp_hut"),new ResourceLocation("igloo"),new ResourceLocation("mansion"));
    /** At most this many structure checks per Garrison offer. */
    static final int GARRISON_CHECKS=4;
    /**
     * A Garrison objective: the location of a whitelisted vanilla structure within the satellite's range, looked up once when the offer is made.
     * Candidates come from each structure set's spread (pure arithmetic); only those in the distance window, apart from the other offers,
     * inside the border and in a biome the structure can stand in (the generator's biome, read without loading) are checked, at most
     * {@link #GARRISON_CHECKS}, with vanilla's structure check, which reads a chunk's saved structure
     * data or simulates the generator: it never loads or generates a chunk. The time it took is logged (a warning above 50 ms).
     */
    static TacticalRules.Spot garrison(ServerLevel l,int bx,int bz,int zoneRadius,int range,List<Double> taken,Random random){
        long started=System.nanoTime();
        record Candidate(ChunkPos chunk,Holder<Structure> structure,TacticalRules.Spot spot){}
        var candidates=new ArrayList<Candidate>();
        try{
            long seed=l.getSeed();var state=l.getChunkSource().getGeneratorState();
            for(var set:l.registryAccess().registryOrThrow(Registries.STRUCTURE_SET)){
                if(!(set.placement() instanceof RandomSpreadStructurePlacement spread))continue;
                var wanted=set.structures().stream().map(e->e.structure()).filter(s->s.unwrapKey().map(k->GARRISON_STRUCTURES.contains(k.location())).orElse(false)).toList();
                if(wanted.isEmpty())continue;
                // one candidate per spread region (spacing x spacing chunks); vanilla takes a chunk inside the region and works out the region itself
                int spacing=spread.spacing(),cell=spacing*16;
                for(int rx=Math.floorDiv(bx-range,cell);rx<=Math.floorDiv(bx+range,cell);rx++)for(int rz=Math.floorDiv(bz-range,cell);rz<=Math.floorDiv(bz+range,cell);rz++){
                    var chunk=spread.getPotentialStructureChunk(seed,rx*spacing,rz*spacing);
                    if(!spread.isStructureChunk(state,chunk.x,chunk.z))continue;
                    var at=spread.getLocatePos(chunk);
                    var spot=TacticalRules.garrison(bx,bz,zoneRadius,range,at.getX(),at.getZ(),taken,(x,z)->l.getWorldBorder().isWithinBounds(x,z));
                    if(spot==null)continue;
                    // only structures whose biomes include the generator's biome there (a cheap lookup that loads nothing): the checks are spent where one can stand
                    var biome=l.getNoiseBiome(net.minecraft.core.QuartPos.fromBlock(chunk.getMiddleBlockX()),net.minecraft.core.QuartPos.fromBlock(l.getSeaLevel()),net.minecraft.core.QuartPos.fromBlock(chunk.getMiddleBlockZ()));
                    for(var s:wanted)if(s.value().biomes().contains(biome))candidates.add(new Candidate(chunk,s,spot));
                }
            }
            Collections.shuffle(candidates,random);
            int checks=0;
            for(var c:candidates){
                if(checks>=GARRISON_CHECKS)break;
                checks++;
                // START_PRESENT: the chunk's saved data has the structure; CHUNK_LOAD_NEEDED: the chunk was never generated and vanilla's
                // simulation of the generator says the structure starts there (vanilla's /locate would load the chunk to be sure; we do not)
                var found=l.structureManager().checkStructurePresence(c.chunk(),c.structure().value(),false);
                if(found==StructureCheckResult.START_PRESENT||found==StructureCheckResult.CHUNK_LOAD_NEEDED){log(started,checks,true);return c.spot();}
            }
            log(started,checks,false);
        }catch(RuntimeException ex){com.mojang.logging.LogUtils.getLogger().warn("[hunts] Garrison structure lookup failed; the offer becomes a Patrol",ex);}
        return null;
    }
    private static void log(long started,int checks,boolean found){
        long ms=(System.nanoTime()-started)/1_000_000;var log=com.mojang.logging.LogUtils.getLogger();
        if(ms>50)log.warn("[hunts] Garrison structure lookup took {} ms ({} checks, {})",ms,checks,found?"found":"none, Patrol instead");
        else log.info("[hunts] Garrison structure lookup took {} ms ({} checks, {})",ms,checks,found?"found":"none, Patrol instead");
    }

    // ---- the server tick -----------------------------------------------------------------------------------------------------------------
    static void tick(ServerLevel l,CampaignData d,long clock){
        var h=HuntData.get(l);
        TacticalScan.tick(l,d,h);
        HuntWarband.tick(l,d,h,clock);
        if(clock%20==0&&d.installed()){
            showMk(l,h);
            var mk=uplink(l,d,h);
            if(mk!=null){
                if(!h.board.filled||h.board.toFill(TacticalRules.offers(mk))>0)fillIfLinked(l);
                // completed offers come back one a dawn
                int n=h.board.dawn(TacticalRules.day(l.getDayTime()),TacticalRules.offers(mk));
                for(int i=0;i<n;i++)if(addOffer(l,d,h,mk,false)==Added.YES)h.setDirty();
                if(n>0)refresh(l);
            }
        }
    }

    /** Why no mission can be accepted right now (no_uplink, raid, raid_warning, busy), or null. */
    static String blocked(ServerLevel l,CampaignData d,HuntData h){
        return TacticalRules.acceptBlocked(uplink(l,d,h)!=null,d.active(),TacticalRules.untilRaid(d.rewardTier,d.preparationTicks,d.respiteTicks),h.board.running());
    }

    // ---- the table -----------------------------------------------------------------------------------------------------------------------
    static void open(ServerPlayer p,BlockPos pos){open(p,pos,"");}
    static void open(ServerPlayer p,BlockPos pos,String message){
        var l=p.serverLevel();var h=HuntData.get(l);
        if(!pos.equals(h.table)){h.table=pos.immutable();h.setDirty();}   // a table placed before the data knew it (or after a reset)
        fillIfLinked(l);
        TacticalScan.sendImage(p,h);
        var state=state(p,pos,message);
        if(p.containerMenu instanceof TacticalBlocks.TableMenu menu&&menu.pos.equals(pos)){BeaconNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new TableState(state));return;}
        NetworkHooks.openScreen(p,new SimpleMenuProvider((id,inv,who)->new TacticalBlocks.TableMenu(id,inv,pos,new CompoundTag()),Component.translatable("block.arsenal_beacon.command_table")),buf->{buf.writeBlockPos(pos);buf.writeNbt(state);});
    }
    /** Everything the table screen shows, built fresh for one player. */
    static CompoundTag state(ServerPlayer p,BlockPos pos,String message){
        var l=p.serverLevel();var d=CampaignData.get(l.getServer().overworld());var h=HuntData.get(l);
        var mk=uplink(l,d,h);int sat=mk!=null?mk:satelliteMk(l,h);
        var n=new CompoundTag();
        n.putBoolean("uplink",mk!=null);n.putString("uplinkProblem",Objects.requireNonNullElse(uplinkProblem(l,d,h),""));n.putInt("mk",sat);n.putInt("range",TacticalRules.range(sat));n.putInt("zone",d.radius());
        n.putLong("beacon",d.beacon.asLong());n.putString("phase",d.phase);n.putLong("untilRaid",TacticalRules.untilRaid(d.rewardTier,d.preparationTicks,d.respiteTicks));
        n.putString("blocked",Objects.requireNonNullElse(blocked(l,d,h),""));
        n.putLong("now",l.getGameTime());n.putLong("scannedAt",h.scannedAt);n.putLong("scanReady",h.scanReady);n.putBoolean("scanning",TacticalScan.running());n.putInt("scanVersion",h.scanVersion);n.putInt("scannedRange",h.scannedRange);
        var offers=new ListTag();int tier=d.rewardTier;
        for(int i=0;i<h.board.offers.size();i++){
            var o=h.board.offers.get(i);var c=HuntData.offer(o);var pay=Economy.hunt(o.kind,tier);
            c.putInt("index",i);c.putInt("danger",TacticalRules.danger(o.kind,tier));c.putInt("ardent",pay.ardent());c.putInt("coins",pay.coins());c.putInt("min",TacticalRules.warband(o.kind,0,1));c.putInt("max",TacticalRules.warband(o.kind,o.kind.max-o.kind.min,1));
            c.putBoolean("active",h.board.mission!=null&&h.board.mission.offer.id==o.id);offers.add(c);
        }
        n.put("offers",offers);
        var m=h.board.mission;
        if(m!=null){n.putLong("mission",m.offer.id);n.putString("missionState",m.state.name());n.putInt("total",m.total);n.putInt("remaining",m.remaining);}
        var upgrade=Economy.satellite(sat);
        if(upgrade!=null){n.putString("upgradeItem",net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(upgrade.item()).toString());n.putInt("upgradeAmount",upgrade.amount());n.putInt("upgradeHave",Economy.have(p,upgrade.item()));}
        n.putBoolean("creative",p.isCreative());n.putString("message",message);
        return n;
    }
    /** A table button (vanilla menu click), checked again here: the player's open table, near, in the zone. */
    static void button(ServerPlayer p,BlockPos pos,int id){
        if(!(p.containerMenu instanceof TacticalBlocks.TableMenu menu)||!menu.pos.equals(pos)||!menu.stillValid(p))return;
        int action=id/16,arg=id%16;var l=p.serverLevel();var d=CampaignData.get(l.getServer().overworld());var h=HuntData.get(l);
        String message=switch(action){
            case TacticalBlocks.ACCEPT->accept(p,l,d,h,arg);
            case TacticalBlocks.ABANDON->abandon(p,l,d,h);
            case TacticalBlocks.UPGRADE->upgrade(p,l,d,h);
            case TacticalBlocks.SCAN->TacticalScan.start(p,l,d,h);
            case TacticalBlocks.REPRINT->reprint(p,l,d,h);
            default->"";
        };
        // the open screen is refreshed in place (closing and reopening it would throw the cursor back to the middle of the window)
        refresh(l,p,message);
    }
    /** Fresh table state to one viewer with a message, and to every other viewer without one. */
    static void refresh(ServerLevel l,ServerPlayer actor,String message){
        for(var viewer:TacticalScan.viewers(l)){
            if(!(viewer.containerMenu instanceof TacticalBlocks.TableMenu menu))continue;
            BeaconNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(()->viewer),new TableState(state(viewer,menu.pos,viewer==actor?message:"")));
        }
    }
    static void refresh(ServerLevel l){refresh(l,null,"");}
    static String accept(ServerPlayer p,ServerLevel l,CampaignData d,HuntData h,int index){
        if(index<0||index>=h.board.offers.size())return "tactical.result.gone";
        var offer=h.board.offers.get(index);
        String refused=h.board.accept(offer.id,blocked(l,d,h));
        if(refused!=null){sound(p,ArsenalSounds.TACTICAL_REFUSED.get());return "tactical.result."+refused;}
        h.missionMap=null;h.setDirty();
        for(var player:l.getServer().getPlayerList().getPlayers())handout(player,h,true);
        sync(l,h,null);
        announce(l,Component.translatable("gui.arsenal_beacon.tactical.accepted",p.getDisplayName(),offer.codename,Component.translatable("gui.arsenal_beacon.tactical.class."+offer.kind.id),offer.distance,TacticalRules.compass(offer.bearing)));
        for(var player:l.getServer().getPlayerList().getPlayers())sound(player,ArsenalSounds.TACTICAL_ACCEPT.get());
        return "tactical.result.accepted";
    }
    static String abandon(ServerPlayer p,ServerLevel l,CampaignData d,HuntData h){
        var ended=h.board.abandon();if(ended==null)return "tactical.result.no_mission";
        HuntWarband.discard(l,ended.offer.id);h.missionMap=null;h.setDirty();sync(l,h,null);
        announce(l,Component.translatable("gui.arsenal_beacon.tactical.abandoned",p.getDisplayName(),ended.offer.codename));
        return "tactical.result.abandoned";
    }
    static String upgrade(ServerPlayer p,ServerLevel l,CampaignData d,HuntData h){
        var mk=uplink(l,d,h);if(mk==null)return "tactical.result.no_uplink";
        var price=Economy.satellite(mk);if(price==null)return "tactical.result.maxed";
        if(h.satellite==null||p.distanceToSqr(h.table.getX()+.5,h.table.getY()+.5,h.table.getZ()+.5)>64)return "tactical.result.too_far";
        if(!Economy.pay(p,price)){sound(p,ArsenalSounds.TACTICAL_REFUSED.get());return "tactical.result.short";}
        h.satelliteMk=TacticalRules.mk(mk+1);h.setDirty();showMk(l,h);
        fillIfLinked(l);sound(p,ArsenalSounds.TACTICAL_ACCEPT.get());
        return "tactical.result.upgraded";
    }
    static String reprint(ServerPlayer p,ServerLevel l,CampaignData d,HuntData h){
        if(!h.board.running())return "tactical.result.no_mission";
        give(p,missionMap(l,d,h));return "tactical.result.reprinted";
    }

    // ---- the mission's map and flare -------------------------------------------------------------------------------------------------------
    /**
     * A vanilla filled map centred exactly on (x, z): {@code MapItem.create} would snap the centre to the map grid. The map data is built
     * through vanilla's own loader with the centre we want (everything else as a fresh tracking map) and stored under a new map id.
     */
    static ItemStack centredMap(ServerLevel l,int x,int z,int scale){
        var tag=new CompoundTag();tag.putString("dimension",l.dimension().location().toString());
        tag.putInt("xCenter",x);tag.putInt("zCenter",z);tag.putByte("scale",(byte)scale);
        tag.putBoolean("trackingPosition",true);tag.putBoolean("unlimitedTracking",true);tag.putBoolean("locked",false);
        int id=l.getFreeMapId();l.setMapData(MapItem.makeKey(id),MapItemSavedData.load(tag));
        var stack=new ItemStack(net.minecraft.world.item.Items.FILLED_MAP);stack.getOrCreateTag().putInt("map",id);
        return stack;
    }
    /** The mission's map: a vanilla filled map with a red X on the objective and the codename as its name (made once, copied after). */
    static ItemStack missionMap(ServerLevel l,CampaignData d,HuntData h){
        var o=h.board.mission.offer;
        if(h.missionMap==null||h.missionMap.getCompound("tag").getLong("arsenalMission")!=o.id){
            int scale=TacticalRules.mapScale(d.beacon.getX(),d.beacon.getZ(),o.x,o.z);
            var map=centredMap(l,TacticalRules.mapCentre(d.beacon.getX(),o.x),TacticalRules.mapCentre(d.beacon.getZ(),o.z),scale);
            MapItem.renderBiomePreviewMap(l,map);
            MapItemSavedData.addTargetDecoration(map,new BlockPos(o.x,0,o.z),"+",MapDecoration.Type.RED_X);
            map.setHoverName(Component.literal(o.codename).withStyle(ChatFormatting.GOLD));
            map.getOrCreateTag().putLong("arsenalMission",o.id);
            h.missionMap=map.save(new CompoundTag());h.setDirty();
        }
        return ItemStack.of(h.missionMap);
    }
    /** Hands a player the mission's map (if they carry none for it) and one Return Flare (once per player per mission). */
    static void handout(ServerPlayer p,HuntData h,boolean announce){
        var m=h.board.mission;if(m==null||!h.board.running())return;
        var l=p.server.overworld();var d=CampaignData.get(l);
        if(!carriesMap(p,m.offer.id))give(p,missionMap(l,d,h));
        if(m.flared.add(p.getUUID())){give(p,new ItemStack(ArsenalBeacon.RETURN_FLARE.get()));h.setDirty();}
    }
    static boolean carriesMap(ServerPlayer p,long missionId){
        for(var s:p.getInventory().items)if(s.is(net.minecraft.world.item.Items.FILLED_MAP)&&s.hasTag()&&s.getTag().getLong("arsenalMission")==missionId)return true;
        return p.getOffhandItem().hasTag()&&p.getOffhandItem().getTag().getLong("arsenalMission")==missionId;
    }
    static void give(ServerPlayer p,ItemStack stack){if(!p.getInventory().add(stack)&&!stack.isEmpty())p.drop(stack,false);}

    // ---- the end of a mission ----------------------------------------------------------------------------------------------------------------
    /** The warband is dead: CLEARED first (once), then the pay goes into the beacon's reward chest. */
    static void complete(ServerLevel l,CampaignData d,HuntData h){
        var m=h.board.mission;if(m==null||!h.board.clear())return;   // clear() is true only once: no double payout
        var pay=Economy.hunt(m.offer.kind,d.rewardTier);
        var stacks=new ArrayList<ItemStack>();
        if(pay.ardent()>0)stacks.add(new ItemStack(ArsenalBeacon.ARDENT_ENERGY.get(),pay.ardent()));
        if(pay.coins()>0)stacks.add(new ItemStack(ArsenalBeacon.AMMO_COIN.get(),pay.coins()));
        if(d.installed())RaidRewards.queue(d,stacks);
        HuntWarband.discard(l,m.offer.id);   // a mob spawned again after being lost, still around: the mission is over
        h.missionMap=null;h.board.settle();h.setDirty();sync(l,h,null);
        announce(l,Component.translatable("gui.arsenal_beacon.tactical.cleared",m.offer.codename,pay.ardent(),pay.coins()));
        for(var player:l.getServer().getPlayerList().getPlayers())music(player,ArsenalSounds.MISSION_CLEARED.get());
        com.mojang.logging.LogUtils.getLogger().info("[hunts] {} cleared: {} Ardent Energy and {} coins queued for the reward chest",m.offer.codename,pay.ardent(),pay.coins());
    }
    /** The beacon was taken down: the mission ends and its warband leaves. The board stays for the next beacon. */
    static void cancel(ServerLevel l){
        var h=HuntData.get(l);var ended=h.board.abandon();
        if(ended!=null){HuntWarband.discard(l,ended.offer.id);h.missionMap=null;h.setDirty();sync(l,h,null);}
    }
    /** No ground for the warband at the objective: the mission ends, the offer is replaced right away. */
    static void unreachable(ServerLevel l,CampaignData d,HuntData h){
        var ended=h.board.unreachable();if(ended==null)return;
        h.missionMap=null;h.setDirty();var mk=uplink(l,d,h);if(mk!=null)addOffer(l,d,h,mk,false);
        sync(l,h,null);announce(l,Component.translatable("gui.arsenal_beacon.tactical.unreachable",ended.offer.codename));
    }

    // ---- operator commands --------------------------------------------------------------------------------------------------------------------
    /** {@code /arsenal hunt status|reset|complete} (operators): see the board, start it over, or finish the running mission (it pays). */
    static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> command(){
        return net.minecraft.commands.Commands.literal("hunt").requires(s->s.hasPermission(2))
            .then(net.minecraft.commands.Commands.literal("status").executes(c->{var l=c.getSource().getServer().overworld();c.getSource().sendSuccess(()->Component.literal(status(l)),false);return 1;}))
            .then(net.minecraft.commands.Commands.literal("reset").executes(c->{var l=c.getSource().getServer().overworld();reset(l);c.getSource().sendSuccess(()->Component.literal("Tactical Operations reset: mission and board cleared."),true);return 1;}))
            .then(net.minecraft.commands.Commands.literal("complete").executes(c->{
                var l=c.getSource().getServer().overworld();var h=HuntData.get(l);var m=h.board.mission;
                if(m==null||!h.board.running()){c.getSource().sendFailure(Component.literal("No mission is running."));return 0;}
                m.state=HuntBoard.State.ENGAGED;m.remaining=0;complete(l,CampaignData.get(l),h);
                c.getSource().sendSuccess(()->Component.literal("Mission "+m.offer.codename+" completed and paid."),true);return 1;}));
    }
    static String status(ServerLevel l){
        var d=CampaignData.get(l);var h=HuntData.get(l);var b=h.board;var mk=uplink(l,d,h);var out=new StringBuilder();
        out.append("Uplink: ").append(mk==null?"none":"Mk "+mk).append(" | table ").append(h.table==null?"-":h.table.toShortString()).append(" | satellite ").append(h.satellite==null?"-":h.satellite.toShortString());
        out.append("\nBoard (").append(b.offers.size()).append(", filled ").append(b.filled).append(", last dawn day ").append(b.lastDay).append("):");
        for(var o:b.offers)out.append("\n  #").append(o.id).append(' ').append(o.codename).append(' ').append(o.kind.id).append(" at ").append(o.x).append(' ').append(o.z).append(", ").append(o.distance).append(" m ").append(TacticalRules.compass(o.bearing));
        var m=b.mission;
        if(m==null)out.append("\nMission: none");
        else out.append("\nMission: ").append(m.offer.codename).append(' ').append(m.state).append(" serial ").append(m.serial).append(", ").append(m.remaining).append('/').append(m.total).append(" left, ").append(m.spawned).append(" spawned, away ").append(m.awayTicks).append(" ticks, flares ").append(m.flared.size());
        out.append("\nScan: version ").append(h.scanVersion).append(h.scannedAt<0?" (never)":" at "+h.scannedAt).append(TacticalScan.running()?", running":"");
        return out.toString();
    }
    static void reset(ServerLevel l){
        var h=HuntData.get(l);var ended=h.board.abandon();if(ended!=null)HuntWarband.discard(l,ended.offer.id);
        h.board.offers.clear();h.board.flared.clear();h.board.filled=false;h.board.capacity=0;h.board.lastDay=Long.MIN_VALUE;h.missionMap=null;h.setDirty();
        HuntWarband.forget();sync(l,h,null);
    }

    // ---- telling players ---------------------------------------------------------------------------------------------------------------------
    static void announce(ServerLevel l,Component text){l.getServer().getPlayerList().broadcastSystemMessage(Component.literal("[Tactical] ").withStyle(ChatFormatting.DARK_AQUA).append(text.copy().withStyle(ChatFormatting.AQUA)),false);}
    static void sound(ServerPlayer p,net.minecraft.sounds.SoundEvent s){p.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(net.minecraft.core.Holder.direct(s),net.minecraft.sounds.SoundSource.BLOCKS,p.getX(),p.getY(),p.getZ(),1f,1f,p.getRandom().nextLong()));}
    static void music(ServerPlayer p,net.minecraft.sounds.SoundEvent s){p.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(net.minecraft.core.Holder.direct(s),net.minecraft.sounds.SoundSource.MUSIC,p.getX(),p.getY(),p.getZ(),1f,1f,p.getRandom().nextLong()));}
    /** The mission line of every status card: to one player, or to all ({@code to} null). */
    static void sync(ServerLevel l,HuntData h,ServerPlayer to){
        var m=h.board.mission;
        var msg=m==null||m.state==HuntBoard.State.CLEARED?new MissionSync(false,"","",0,0,0,0,false):new MissionSync(true,m.offer.codename,m.offer.kind.id,m.offer.x,m.offer.z,m.total,m.remaining,m.engaged());
        if(to!=null)BeaconNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(()->to),msg);else{BeaconNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(),msg);refresh(l);}
    }
    /** Players who log in mid-mission get the map and flare if missing, and the mission line. */
    static void login(ServerPlayer p){var h=HuntData.get(p.server.overworld());handout(p,h,false);sync(p.server.overworld(),h,p);}

    /**
     * S2C (id 20): a fresh state for an open Command Table: after one of its buttons, and when the board changes under it (another player
     * accepts, a mission ends, a scan finishes). The table's first state comes in the menu's opening buffer.
     */
    record TableState(CompoundTag data){
        static void encode(TableState m,net.minecraft.network.FriendlyByteBuf b){b.writeNbt(m.data);}
        static TableState decode(net.minecraft.network.FriendlyByteBuf b){var n=b.readNbt();return new TableState(n==null?new CompoundTag():n);}
        static void handle(TableState m,java.util.function.Supplier<net.minecraftforge.network.NetworkEvent.Context> ctx){ctx.get().enqueueWork(()->net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,()->()->TacticalClient.state(m.data)));ctx.get().setPacketHandled(true);}
    }

    /** S2C: the shared mission for the status card (sent on accept, abandon, spawn, every kill, clear and login). */
    record MissionSync(boolean active,String codename,String kind,int x,int z,int total,int remaining,boolean engaged){
        static void encode(MissionSync m,net.minecraft.network.FriendlyByteBuf b){b.writeBoolean(m.active);b.writeUtf(m.codename,64);b.writeUtf(m.kind,16);b.writeVarInt(m.x);b.writeVarInt(m.z);b.writeVarInt(m.total);b.writeVarInt(m.remaining);b.writeBoolean(m.engaged);}
        static MissionSync decode(net.minecraft.network.FriendlyByteBuf b){return new MissionSync(b.readBoolean(),b.readUtf(64),b.readUtf(16),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readBoolean());}
        static void handle(MissionSync m,java.util.function.Supplier<net.minecraftforge.network.NetworkEvent.Context> ctx){ctx.get().enqueueWork(()->net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,()->()->TacticalClient.mission(m)));ctx.get().setPacketHandled(true);}
    }
}
