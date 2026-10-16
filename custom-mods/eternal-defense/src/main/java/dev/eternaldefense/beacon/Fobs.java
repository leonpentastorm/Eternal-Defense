package dev.eternaldefense.beacon;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.PacketDistributor;
import java.util.*;

/** Forward Operating Bases: planting a FOB Beacon in a vanilla village, its activation raid */
final class Fobs {
    private Fobs(){}
    /** Persistent data key on a villager: the id of the FOB whose village it lives */
    static final String VILLAGER_TAG="eternalFob";
    /** Shop button ids (vanilla menu buttons, checked again here). */
    static final int BUY_SUPPLY=1,BUY_RETURN=2,BUY_FIRE=3,CALL_RAID=4,BUY_GATE=5,REPAIR=6,OUTLINE=7,BUY_CATAPULT=8;
    /** Research buttons: 10 + what is given (0 emerald, 1 diamond, 2 Ardent Energy), + 3 for ten at once. */
    static final int RESEARCH=10,RESEARCH_TEN=3;
    static final List<String> RESEARCH_ITEMS=List.of("emerald","diamond","ardent");
    /** The flares the FOB shop sells, at the Support Platform's prices (SupportRules). */
    static final List<String> SHOP=List.of("supply","return","fire");

    // Lookups
    /** The FOB whose zone holds pos (any state), or null. */
    static FobData.Fob zoneAt(ServerLevel l,BlockPos pos){
        if(l.dimension()!=Level.OVERWORLD)return null;
        for(var f:FobData.get(l).fobs.values())if(FobZone.contains(FobZone.of(l,f),pos))return f;
        return null;
    }
    /** The ACTIVE FOB whose zone holds pos (the meal bonus and the shop work only there), or null. */
    static FobData.Fob workingZoneAt(ServerLevel l,BlockPos pos){var f=zoneAt(l,pos);return f!=null&&FobRules.working(f.state)?f:null;}
    static FobData.Fob nearest(ServerLevel l,BlockPos pos){
        FobData.Fob best=null;double d=Double.MAX_VALUE;
        for(var f:FobData.get(l).fobs.values()){double x=f.pos.distSqr(pos);if(x<d){d=x;best=f;}}
        return best;
    }
    static boolean raidsPossible(ServerLevel l){return l.getDifficulty()!=Difficulty.PEACEFUL&&!l.getGameRules().getBoolean(GameRules.RULE_DISABLE_RAIDS)&&l.dimensionType().hasRaids();}

    // Planting
    /** Why a FOB Beacon cannot be planted at pos: a key under fob.refused., or null. */
    static String plantProblem(ServerLevel l,BlockPos pos){return plantProblem(l,pos,l.dimension()==Level.OVERWORLD?FobZone.sections(l,pos):Set.of());}
    /** As above with the spot's zone already worked out (the planting preview, FobOutline). */
    static String plantProblem(ServerLevel l,BlockPos pos,Set<Long> zone){
        if(l.dimension()!=Level.OVERWORLD)return "overworld";
        var d=FobData.get(l);boolean beacon=CampaignData.get(l.getServer().overworld()).installed();
        boolean taken=false;
        for(var f:d.fobs.values())if(FobZone.contains(zone,f.pos)||FobZone.contains(FobZone.of(l,f),pos)){taken=true;break;}
        return FobRules.plantProblem(beacon,d.fobs.size(),raidsPossible(l),!zone.isEmpty(),taken,zone.isEmpty()?0:FobZone.villagers(l,zone).size());
    }
    /** A FOB Beacon was placed by a player: the FOB is made, its villagers taken under protection */
    static void planted(ServerLevel l,BlockPos pos,ServerPlayer p){
        var d=FobData.get(l);if(d.at(pos)!=null)return;
        var f=d.add(pos,SectionPos.asLong(SectionPos.blockToSectionCoord(pos.getX()),SectionPos.blockToSectionCoord(pos.getY()),SectionPos.blockToSectionCoord(pos.getZ())),l.getGameTime());
        FobZone.forget(f.id);int villagers=tagVillagers(l,f);
        announce(l,Component.translatable("gui.eternal_defense.fob.planted",p.getDisplayName(),f.name,villagers));
        horn(l,f);
        log("{} planted at {} by {} ({} villagers)",f.name,pos.toShortString(),p.getGameProfile().getName(),villagers);
        String problem=callRaid(l,f,p);
        if(problem!=null)say(p,"fob.raid."+problem);
    }
    /** The war horn sounds from the FOB Beacon when the FOB is planted and when its raid starts */
    static final float HORN_VOLUME=8f;
    /** The horn is about 12 seconds long: planting starts the activation raid at once, so one horn sounds for both */
    static final int HORN_TICKS=240;
    private static final Map<Long,Long> LAST_HORN=new HashMap<>();
    static void horn(ServerLevel l,FobData.Fob f){
        long now=l.getGameTime();Long last=LAST_HORN.get(f.id);
        if(last!=null&&now-last>=0&&now-last<HORN_TICKS)return;
        LAST_HORN.put(f.id,now);hornsSounded++;
        l.playSound(null,f.pos,DefenseSounds.WAR_HORN.get(),net.minecraft.sounds.SoundSource.HOSTILE,HORN_VOLUME,1f);
    }
    /** Horns sounded since the server started (for tests). */
    static int hornsSounded;
    /** Starts the FOB's raid with this player's Bad Omen I */
    static String callRaid(ServerLevel l,FobData.Fob f,ServerPlayer p){
        if(!FobRules.mayCallRaid(f.state,f.raidRunning()))return "not_now";
        if(!raidsPossible(l))return "impossible";
        if(p.isSpectator()||p.level()!=l||!FobZone.contains(FobZone.of(l,f),p.blockPosition()))return "come_closer";
        p.addEffect(new MobEffectInstance(MobEffects.BAD_OMEN,600,0,false,false,true));
        var raid=l.getRaids().createOrExtendRaid(p);
        if(raid==null){p.removeEffect(MobEffects.BAD_OMEN);return "impossible";}
        f.raidId=raid.getId();f.raidVillagers=tagVillagers(l,f);FobData.get(l).setDirty();
        announce(l,Component.translatable("gui.eternal_defense.fob.raid_started",f.name,f.raidVillagers,(f.raidVillagers+1)/2));
        horn(l,f);
        log("{}: raid {} started at {}, {} villagers",f.name,raid.getId(),raid.getCenter().toShortString(),f.raidVillagers);
        refreshScreens(l,f);return null;
    }

    // The raid, once a second
    static void tick(ServerLevel l,int clock){
        if(clock%20!=0||l.getServer().getPlayerList().getPlayerCount()==0)return;
        var d=FobData.get(l);
        for(var f:List.copyOf(d.fobs.values())){
            boolean loaded=l.isPositionEntityTicking(f.pos);
            if(f.raidRunning()){
                var raid=l.getRaids().get(f.raidId);
                var outcome=FobRules.outcome(raid!=null,raid!=null&&raid.isVictory(),raid!=null&&raid.isLoss(),raid!=null&&raid.isStopped());
                if(outcome==FobRules.Outcome.RUNNING){
                    if(loaded&&FobRules.raidFailed(f.raidVillagers,livingVillagers(l,f))){
                        // The mod's own fail rule: the vanilla raid is stopped (its bar goes away) and the FOB goes down
                        raid.stop();end(l,f,FobRules.Outcome.LOST,true);
                    }
                }else end(l,f,outcome,false);
            }
            if(loaded&&clock%FobRules.ZONE_REFRESH_TICKS==0)tagVillagers(l,f);
        }
        FobSos.second(l,d);   // The SOS clock, the open SOS and its raid
    }
    /** A raid at the FOB ended: won makes it ACTIVE, lost makes it DOWN */
    static void end(ServerLevel l,FobData.Fob f,FobRules.Outcome outcome,boolean villagersLost){
        var d=FobData.get(l);var before=f.state;f.raidId=0;f.state=FobRules.after(f.state,outcome);
        if(outcome==FobRules.Outcome.WON)f.activated=true;
        d.setDirty();
        if(!FobSos.raidEnded(l,f,outcome))switch(outcome){   // An SOS raid is told by the SOS
            case WON->announce(l,Component.translatable("gui.eternal_defense.fob.won",f.name));
            case LOST->announce(l,Component.translatable(villagersLost?"gui.eternal_defense.fob.lost_villagers":"gui.eternal_defense.fob.lost",f.name));
            default->announce(l,Component.translatable("gui.eternal_defense.fob.raid_over",f.name));
        }
        log("{}: raid ended {} ({} -> {})",f.name,outcome,before,f.state);
        refreshScreens(l,f);
    }
    /** The villagers of this FOB still alive in the loaded world */
    static int livingVillagers(ServerLevel l,FobData.Fob f){
        var box=FobZone.box(FobZone.of(l,f));if(box==null)box=new net.minecraft.world.phys.AABB(f.pos);
        return l.getEntitiesOfClass(Villager.class,box.inflate(48),v->v.isAlive()&&v.getPersistentData().getLong(VILLAGER_TAG)==f.id).size();
    }
    /** Puts every villager in the zone under this FOB (newcomers and children too); returns how many it has now. */
    static int tagVillagers(ServerLevel l,FobData.Fob f){
        var d=FobData.get(l);
        for(var v:FobZone.villagers(l,FobZone.of(l,f))){
            long id=v.getPersistentData().getLong(VILLAGER_TAG);
            if(id!=f.id&&(id==0||d.byId(id)==null))v.getPersistentData().putLong(VILLAGER_TAG,f.id);
        }
        int alive=livingVillagers(l,f);
        if(alive!=f.workers){f.workers=alive;d.setDirty();}   // The villagers set what a trip is worth
        return alive;
    }

    // The villagers are immortal, except during a raid at their FOB
    /** Pure: is this hit on a villager cancelled? */
    static boolean protect(boolean fobExists,boolean raidRunning,boolean bypassesInvulnerability){return fobExists&&!raidRunning&&!bypassesInvulnerability;}

    // The shop, renaming, removal
    /** A click on the FOB Beacon: always a fresh screen */
    static void open(ServerPlayer p,BlockPos pos){open(p,pos,"",true);}
    /** After a button or a change: the open screen is refreshed in place. */
    static void open(ServerPlayer p,BlockPos pos,String message){open(p,pos,message,false);}
    static void open(ServerPlayer p,BlockPos pos,String message,boolean fresh){
        var l=p.serverLevel();var f=FobData.get(l).at(pos);
        if(f==null){say(p,"fob.unregistered");return;}
        var state=state(p,f,message);
        if(!fresh&&p.containerMenu instanceof FobBlocks.FobMenu menu&&menu.pos.equals(pos)){menu.message=message;BeaconNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new Hunts.TableState(state));return;}
        NetworkHooks.openScreen(p,new SimpleMenuProvider((id,inv,who)->new FobBlocks.FobMenu(id,inv,pos,new CompoundTag()),Component.literal(f.name)),buf->{buf.writeBlockPos(pos);buf.writeNbt(state);});
        if(p.containerMenu instanceof FobBlocks.FobMenu menu)menu.message=message;
    }
    /** Everything the FOB screen shows, for one player. */
    static CompoundTag state(ServerPlayer p,FobData.Fob f,String message){
        var l=p.serverLevel();var zone=FobZone.of(l,f);var n=new CompoundTag();
        n.putLong("fobId",f.id);n.putString("name",f.name);n.putString("state",f.state.id());n.putBoolean("activated",f.activated);n.putLong("pos",f.pos.asLong());
        int villagers=FobZone.villagers(l,zone).size();int[] places=FobZone.homesAndJobs(l,zone);
        n.putInt("villagers",villagers);n.putInt("beds",places[0]);n.putInt("jobs",places[1]);n.putInt("sections",zone.size());n.putBoolean("showZone",f.showZone);
        n.putBoolean("raid",f.raidRunning());
        if(f.raidRunning()){
            var raid=l.getRaids().get(f.raidId);n.putInt("raidStart",f.raidVillagers);n.putInt("raidAlive",livingVillagers(l,f));
            if(raid!=null){n.putInt("wave",raid.getGroupsSpawned());n.putInt("waves",raid.getNumGroups(l.getDifficulty()));n.putInt("raiders",raid.getTotalRaidersAlive());}
        }
        n.putBoolean("mayCallRaid",FobRules.mayCallRaid(f.state,f.raidRunning()));n.putBoolean("raidsPossible",raidsPossible(l));
        n.putInt("energy",ArdentEnergy.balance(p));n.putBoolean("creative",p.isCreative());
        var base=SupportData.get(l).of(p.getUUID());n.putBoolean("hasPlatform",base!=null&&base.platform!=null);n.putBoolean("hasCannon",base!=null&&base.cannon!=null);
        for(String id:SHOP)n.putInt("price."+id,price(id));
        // The second listing: this FOB's Supply Delivery Gate, one per FOB
        n.putBoolean("hasGate",f.deliveryGate!=null);n.put("gateCost",WeaponPlatform.costTags(Supply.pack(p),Economy.fabrication("supply_delivery_gate")));
        // The Supply Catapult can be built here too, for the same materials as at Workshop Fabrication
        n.put("catapultCost",WeaponPlatform.costTags(Supply.pack(p),Economy.fabrication("supply_catapult")));
        // The tribute: the loot slots, the next delivery, the storage at the Receiving Gate
        Routes.tribute(f,n);n.putBoolean("hasRoute",f.hasRoute()&&f.commissioned);
        n.putInt("villagers",villagers);   // The tribute writes its own villagers (the workers, at most 20); the screen shows the living ones
        for(int i=0;i<RESEARCH_ITEMS.size();i++)n.putInt("carry."+RESEARCH_ITEMS.get(i),carried(p,researchItem(i)));   // For the research tab
        n.putLong("nextIn",f.nextPassive>0?Math.max(0,f.nextPassive-FobData.get(l).onlineTicks):-1);
        FobSos.state(FobData.get(l),f,n);   // The SOS, the lost villagers and the repair
        n.putString("catapult",java.util.Objects.requireNonNullElse(SupplyCatapult.closed(l,f),"online"));   // Online state or the reason it is not
        n.putString("message",message);
        return n;
    }
    static int price(String id){return Economy.fobFlare(id);}
    static net.minecraft.world.item.Item flare(String id){return switch(id){case "supply"->EternalDefense.SUPPLY_FLARE.get();case "return"->EternalDefense.RETURN_FLARE.get();default->EternalDefense.FIRE_FLARE.get();};}
    /** A FOB screen button, checked again here: buy a flare (ACTIVE only) or call the activation raid (PLANTED only). */
    static void button(ServerPlayer p,BlockPos pos,int id){
        var l=p.serverLevel();var f=FobData.get(l).at(pos);
        if(f==null||!(p.containerMenu instanceof FobBlocks.FobMenu menu)||!menu.stillValid(p))return;
        if(id==CALL_RAID){String problem=callRaid(l,f,p);open(p,pos,problem==null?"fob.result.raid_called":"fob.raid."+problem);return;}
        if(id==BUY_GATE){String result=Routes.buyGate(p,f);log("{}: {} asked for the Delivery Gate: {}",f.name,p.getGameProfile().getName(),result);open(p,pos,result);return;}
        if(id==BUY_CATAPULT){String result=buyCatapult(p,f);log("{}: {} asked for a Supply Catapult: {}",f.name,p.getGameProfile().getName(),result);open(p,pos,result);return;}
        if(id==REPAIR){String result=FobSos.repair(p,f);open(p,pos,result);return;}
        if(id>=RESEARCH&&id<RESEARCH+2*RESEARCH_TEN){int kind=(id-RESEARCH)%RESEARCH_TEN;open(p,pos,research(l,f,p,kind,id-RESEARCH>=RESEARCH_TEN?10:1));return;}
        if(id==OUTLINE){   // The zone outline of this FOB, on or off for everyone
            f.showZone=!f.showZone;FobData.get(l).setDirty();log("{}: {} turned the zone outline {}",f.name,p.getGameProfile().getName(),f.showZone?"on":"off");
            refreshScreens(l,f);return;
        }
        int index=id-BUY_SUPPLY;if(index<0||index>=SHOP.size())return;
        String item=SHOP.get(index);
        if(!FobRules.working(f.state)){open(p,pos,"fob.result.closed");return;}
        if(!ArdentEnergy.spend(Supply.pack(p),price(item))){open(p,pos,"fob.result.no_energy");return;}   // Outside the Home Zone, the pack alone
        var stack=new ItemStack(flare(item));if(!p.getInventory().add(stack))p.drop(stack,false);
        log("{}: {} bought a {} flare",f.name,p.getGameProfile().getName(),item);
        open(p,pos,"fob.result.bought."+item);
    }
    /** What one item of a research kind is worth in points, and the item itself. */
    static int researchXp(int kind){return switch(kind){case 0->RouteRules.XP_EMERALD;case 1->RouteRules.XP_DIAMOND;default->RouteRules.XP_ARDENT;};}
    static net.minecraft.world.item.Item researchItem(int kind){return switch(kind){case 0->net.minecraft.world.item.Items.EMERALD;case 1->net.minecraft.world.item.Items.DIAMOND;default->EternalDefense.ARDENT_ENERGY.get();};}
    static int carried(net.minecraft.world.entity.player.Player p,net.minecraft.world.item.Item item){int n=0;for(var s:p.getInventory().items)if(s.is(item))n+=s.getCount();for(var s:p.getInventory().offhand)if(s.is(item))n+=s.getCount();return n;}
    /** A Supply Catapult built at the FOB Beacon, the same materials as at Workshop Fabrication */
    static String buyCatapult(ServerPlayer p,FobData.Fob f){
        if(!FobRules.working(f.state))return "fob.result.closed";
        return WeaponPlatform.transact(Supply.pack(p),Economy.fabrication("supply_catapult"),new ItemStack(EternalDefense.SUPPLY_CATAPULT_ITEM.get()))?"fob.result.bought.catapult":"fob.result.no_materials";
    }
    /** Gives up to count emeralds, diamonds or Ardent Energy to the FOB's research (creative players give for free) */
    static String research(ServerLevel l,FobData.Fob f,ServerPlayer p,int kind,int count){
        if(!FobRules.working(f.state))return "fob.research.closed";
        int before=f.level();if(before>=RouteRules.MAX_LEVEL)return "fob.research.max";
        int per=researchXp(kind),need=(RouteRules.RESEARCH_XP[RouteRules.MAX_LEVEL]-f.researchXp+per-1)/per;
        var item=researchItem(kind);int n=Math.min(count,need);if(!p.isCreative())n=Math.min(n,carried(p,item));
        if(n<=0)return "fob.research.short."+RESEARCH_ITEMS.get(kind);
        if(!p.isCreative()){
            var plan=InventoryPayment.plan(Supply.pack(p),List.of(new WeaponPlatform.Cost(net.minecraft.world.item.crafting.Ingredient.of(item),n)));if(!plan.payable())return "fob.research.short."+RESEARCH_ITEMS.get(kind);
            var slots=plan.inventory();int main=p.getInventory().items.size();
            for(int i=0;i<main;i++)p.getInventory().items.set(i,slots.get(i));
            for(int i=0;i<p.getInventory().offhand.size();i++)p.getInventory().offhand.set(i,slots.get(main+i));
            InventoryPayment.sync(p);
        }
        f.researchXp=Math.min(RouteRules.RESEARCH_XP[RouteRules.MAX_LEVEL],f.researchXp+n*per);FobData.get(l).setDirty();
        log("{}: {} gave {} {} to the research ({} points, level {})",f.name,p.getGameProfile().getName(),n,RESEARCH_ITEMS.get(kind),f.researchXp,f.level());
        int after=f.level();
        if(after>before){
            // Every unlock passed is told (a big gift can pass several)
            var opens=new ArrayList<String>();
            if(after>=RouteRules.UNLOCK_A&&before<RouteRules.UNLOCK_A)opens.add(".a");
            if(after>=RouteRules.UNLOCK_S&&before<RouteRules.UNLOCK_S)opens.add(".s");
            if(after>=CatapultRules.SELLER_LEVEL&&before<CatapultRules.SELLER_LEVEL)opens.add(".seller");
            if(opens.isEmpty())opens.add("");
            for(String o:opens)announce(l,Component.translatable("gui.eternal_defense.fob.research.levelup"+o,f.name,after));
            for(var pos:new BlockPos[]{f.deliveryGate,f.receivingGate})if(pos!=null)for(var viewer:l.getServer().getPlayerList().getPlayers())
                if(viewer.containerMenu instanceof RailGates.GateMenu m&&m.pos.equals(pos))Routes.open(viewer,pos,"");
            refreshScreens(l,f);
            return "fob.research.up";
        }
        return "fob.research.given";
    }
    /** A renamed Name Tag used on the FOB Beacon renames the FOB (the tag is used up) */
    static void rename(ServerPlayer p,BlockPos pos,ItemStack tag){
        var l=p.serverLevel();var d=FobData.get(l);var f=d.at(pos);
        if(f==null){say(p,"fob.unregistered");return;}
        if(!tag.hasCustomHoverName()){say(p,"fob.rename.blank");return;}
        String name=FobRules.clean(tag.getHoverName().getString());String problem=FobRules.nameProblem(name,d.names(f.id));
        if(problem!=null){say(p,"fob.rename."+problem,name);return;}
        String old=f.name;f.name=name;d.setDirty();Routes.refreshNames(l,f);
        if(!p.isCreative())tag.shrink(1);
        announce(l,Component.translatable("gui.eternal_defense.fob.renamed",old,name));
        refreshScreens(l,f);
    }
    /** The Recovery Shovel on a FOB Beacon asks first (the beacon's own confirm screen) */
    static void requestRemoval(ServerPlayer p,BlockPos clicked){
        var l=p.serverLevel();var root=DefenseStructures.anchor(l,clicked);var f=FobData.get(l).at(root);
        if(f==null){l.removeBlock(root,false);give(p);say(p,"fob.removed_plain");return;}
        BeaconActions.requestFobRemoval(p,f.id,f.name);
    }
    /** The confirmed removal: the FOB is deleted, its raid stopped, its villagers mortal again */
    static boolean remove(ServerPlayer p,long id){
        var l=p.server.overworld();var d=FobData.get(l);var f=d.byId(id);
        if(f==null||p.level()!=l||p.distanceToSqr(f.pos.getX()+.5,f.pos.getY()+.5,f.pos.getZ()+.5)>64)return false;
        if(f.raidRunning()){var raid=l.getRaids().get(f.raidId);if(raid!=null)raid.stop();}
        FobSos.removed(l,f);   // An SOS of this FOB is called off
        d.fobs.remove(f.id);d.setDirty();FobZone.forget(f.id);Routes.RUNS.remove(f.id);   // Its gates turn UNLINKED by themselves
        if(l.getBlockState(f.pos).is(EternalDefense.FOB_BEACON.get()))l.removeBlock(f.pos,false);
        give(p);
        int handed=Routes.spill(l,f,p);if(handed>0)say(p,"fob.removed_storage",handed);   // What its storage held goes to the player
        announce(l,Component.translatable("gui.eternal_defense.fob.removed",p.getDisplayName(),f.name));
        log("{} removed by {}",f.name,p.getGameProfile().getName());
        return true;
    }
    /** The FOB Beacon block is gone some other way (creative break, a command): its record goes with it. */
    static void blockGone(ServerLevel l,BlockPos pos){
        var d=FobData.get(l);var f=d.at(pos);if(f==null)return;
        if(f.raidRunning()){var raid=l.getRaids().get(f.raidId);if(raid!=null)raid.stop();}
        FobSos.removed(l,f);
        d.fobs.remove(f.id);d.setDirty();FobZone.forget(f.id);Routes.RUNS.remove(f.id);log("{}: its block was removed; the FOB is deleted",f.name);
        Routes.spill(l,f,null);
    }
    private static void give(ServerPlayer p){var stack=new ItemStack(EternalDefense.FOB_BEACON_ITEM.get());if(!p.getInventory().add(stack))p.drop(stack,false);}
    static void refreshScreens(ServerLevel l,FobData.Fob f){
        for(var p:l.getServer().getPlayerList().getPlayers())if(p.containerMenu instanceof FobBlocks.FobMenu menu&&menu.pos.equals(f.pos))open(p,f.pos,"");
    }

    // The status card
    /** The FOB line of a player's status card: the FOB they stand */
    static void status(ServerPlayer p,CompoundTag n){
        if(p.level().dimension()!=Level.OVERWORLD)return;
        var l=p.serverLevel();var d=FobData.get(l);if(d.fobs.isEmpty())return;
        FobSos.status(d,n);   // An open SOS is on every card
        n.putInt("fobPlaced",d.fobs.size());n.putInt("fobActive",d.count(FobRules.State.ACTIVE));
        var f=zoneAt(l,p.blockPosition());if(f==null)return;
        var here=new CompoundTag();here.putString("name",f.name);here.putString("state",f.state.id());here.putBoolean("raid",f.raidRunning());
        here.putInt("villagers",FobZone.villagers(l,FobZone.of(l,f)).size());n.put("fobHere",here);
    }

    // Operator commands
    /** /eternaldefense fob list|status|complete|reset|deliver|overdue|sos [expire] (operators) */
    static LiteralArgumentBuilder<CommandSourceStack> command(){
        return Commands.literal("fob").requires(s->s.hasPermission(2))
            .then(Commands.literal("list").executes(c->{
                var l=c.getSource().getServer().overworld();var d=FobData.get(l);
                if(d.fobs.isEmpty()){c.getSource().sendSuccess(()->Component.literal("No FOBs placed."),false);return 1;}
                for(var f:d.fobs.values()){String line=describe(l,f);c.getSource().sendSuccess(()->Component.literal(line),false);}
                String sos=FobSos.describe(l,d);c.getSource().sendSuccess(()->Component.literal(sos),false);return d.fobs.size();}))
            .then(Commands.literal("status").executes(c->{
                var l=c.getSource().getServer().overworld();var f=nearest(l,BlockPos.containing(c.getSource().getPosition()));
                String line=f==null?"No FOBs placed.":describe(l,f);c.getSource().sendSuccess(()->Component.literal(line),false);return f==null?0:1;}))
            .then(Commands.literal("complete").executes(c->{
                var l=c.getSource().getServer().overworld();var f=nearest(l,BlockPos.containing(c.getSource().getPosition()));
                if(f==null){c.getSource().sendFailure(Component.literal("No FOBs placed."));return 0;}
                if(f.raidRunning()){var raid=l.getRaids().get(f.raidId);if(raid!=null)raid.stop();}
                end(l,f,FobRules.Outcome.WON,false);c.getSource().sendSuccess(()->Component.literal(f.name+" is ACTIVE."),true);return 1;}))
            .then(Commands.literal("reset").executes(c->{
                var l=c.getSource().getServer().overworld();var f=nearest(l,BlockPos.containing(c.getSource().getPosition()));
                if(f==null){c.getSource().sendFailure(Component.literal("No FOBs placed."));return 0;}
                if(f.raidRunning()){var raid=l.getRaids().get(f.raidId);if(raid!=null)raid.stop();}
                f.raidId=0;f.state=FobRules.State.PLANTED;f.activated=false;FobData.get(l).setDirty();refreshScreens(l,f);
                c.getSource().sendSuccess(()->Component.literal(f.name+" is PLANTED again: call its raid from the FOB Beacon."),true);return 1;}))
            .then(Commands.literal("deliver").executes(c->{
                var l=c.getSource().getServer().overworld();var d=FobData.get(l);var f=nearest(l,BlockPos.containing(c.getSource().getPosition()));
                if(f==null||f.nextPassive==0){c.getSource().sendFailure(Component.literal(f==null?"No FOBs placed.":f.name+" delivers nothing by itself now (no route, inspection overdue, FOB not ACTIVE or the reward chest backed up)."));return 0;}
                f.nextPassive=d.onlineTicks;d.setDirty();c.getSource().sendSuccess(()->Component.literal(f.name+": its next delivery comes within a second."),true);return 1;}))
            .then(Commands.literal("overdue").executes(c->{
                var l=c.getSource().getServer().overworld();var f=nearest(l,BlockPos.containing(c.getSource().getPosition()));
                if(f==null||!f.commissioned||f.clock<=0){c.getSource().sendFailure(Component.literal(f==null?"No FOBs placed.":f.name+" has no running inspection."));return 0;}
                f.clock=20;FobData.get(l).setDirty();c.getSource().sendSuccess(()->Component.literal(f.name+": its route inspection runs out within a second (while the FOB is ACTIVE)."),true);return 1;}))
            .then(Commands.literal("sos").executes(c->{
                var l=c.getSource().getServer().overworld();var d=FobData.get(l);var at=BlockPos.containing(c.getSource().getPosition());
                FobData.Fob f=null;double best=Double.MAX_VALUE;
                for(var o:d.fobs.values())if(o.state==FobRules.State.ACTIVE&&o.pos.distSqr(at)<best){best=o.pos.distSqr(at);f=o;}
                if(d.sosFob!=0){var open=d.byId(d.sosFob);c.getSource().sendFailure(Component.literal("An SOS is already open"+(open==null?"":" at "+open.name)+": only one at a time."));return 0;}
                if(f==null){c.getSource().sendFailure(Component.literal("No ACTIVE FOB can call."));return 0;}
                var called=FobSos.fire(l,d,f);String name=called==null?"":called.name;
                if(called==null){c.getSource().sendFailure(Component.literal("No SOS could be called."));return 0;}
                c.getSource().sendSuccess(()->Component.literal("SOS called at "+name+"."),true);return 1;})
                .then(Commands.literal("expire").executes(c->{
                    var l=c.getSource().getServer().overworld();var d=FobData.get(l);
                    if(d.sosFob==0){c.getSource().sendFailure(Component.literal("No SOS is open."));return 0;}
                    d.sosLeft=20;d.setDirty();c.getSource().sendSuccess(()->Component.literal("The open SOS runs out within a second (unless its raid is running)."),true);return 1;})));
    }
    static String describe(ServerLevel l,FobData.Fob f){
        boolean loaded=l.isPositionEntityTicking(f.pos);
        return f.name+" (#"+f.id+") "+f.state+(f.activated?"":" (never won)")+" at "+f.pos.toShortString()
            +(loaded?", "+FobZone.villagers(l,FobZone.of(l,f)).size()+" villagers, zone "+FobZone.of(l,f).size()+" sections":", not loaded")
            +(f.raidRunning()?", raid "+f.raidId+" running ("+f.raidVillagers+" villagers at its start)":"")
            +(f.hasRoute()?", route "+(f.commissioned?"open (T "+f.baseline+" ticks, inspection "+f.clock+" ticks)":"not ridden yet"):f.deliveryGate!=null||f.receivingGate!=null?", one gate placed":"");
    }

    // Small helpers
    static void announce(ServerLevel l,Component text){
        var line=Component.literal("[FOB] ").withStyle(ChatFormatting.DARK_GREEN).append(text.copy().withStyle(ChatFormatting.GRAY));
        l.getServer().getPlayerList().broadcastSystemMessage(line,false);
    }
    static void say(ServerPlayer p,String key,Object... args){p.displayClientMessage(Component.translatable("gui.eternal_defense."+key,args),true);}
    private static void log(String text,Object... args){com.mojang.logging.LogUtils.getLogger().info("[fob] "+text,args);}

    /** Named FobEvents, not Events: Forge names the class it generates per handler after the class's simple name */
    static final class FobEvents {
        /** Villagers of a FOB cannot be hurt while no raid runs at it */
        @SubscribeEvent(priority=EventPriority.HIGHEST) public void attack(LivingAttackEvent e){
            if(!(e.getEntity() instanceof Villager v)||!(v.level() instanceof ServerLevel l))return;
            long id=v.getPersistentData().getLong(VILLAGER_TAG);if(id==0)return;
            var f=FobData.get(l).byId(id);
            if(protect(f!=null,f!=null&&f.raidRunning(),e.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)))e.setCanceled(true);
        }
        /** Lightning would turn one into a witch without hurting it first (vanilla's thunderHit) */
        @SubscribeEvent(priority=EventPriority.HIGHEST) public void lightning(net.minecraftforge.event.entity.EntityStruckByLightningEvent e){
            if(!(e.getEntity() instanceof Villager v)||!(v.level() instanceof ServerLevel l))return;
            long id=v.getPersistentData().getLong(VILLAGER_TAG);if(id==0)return;
            var f=FobData.get(l).byId(id);
            if(protect(f!=null,f!=null&&f.raidRunning(),false))e.setCanceled(true);
        }
        @SubscribeEvent public void started(net.minecraftforge.event.server.ServerStartedEvent e){FobZone.clear();FobOutline.clear();}
        /** A player who comes back or changes dimension gets the outlines again at once. */
        @SubscribeEvent public void outlineLogin(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p)FobOutline.forget(p);}
        @SubscribeEvent public void outlineTravel(net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p)FobOutline.forget(p);}
        /** A FOB's villager killed while a raid runs at its FOB is recorded for the repair. */
        @SubscribeEvent(priority=EventPriority.LOWEST) public void death(net.minecraftforge.event.entity.living.LivingDeathEvent e){
            if(e.isCanceled()||!(e.getEntity() instanceof Villager v)||!(v.level() instanceof ServerLevel l))return;
            long id=v.getPersistentData().getLong(VILLAGER_TAG);if(id==0)return;
            var f=FobData.get(l).byId(id);if(f!=null&&f.raidRunning())FobSos.lost(l,f,v);
        }
        /** Someone logging in while an SOS is open gets its map; maps of an SOS that ended burn. */
        @SubscribeEvent public void fobLogin(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p)FobSos.login(p);}
    }
}
