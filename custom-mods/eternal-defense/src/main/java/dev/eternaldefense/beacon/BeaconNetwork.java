package dev.eternaldefense.beacon;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.Optional;
import java.util.function.Supplier;

final class BeaconNetwork {
    /** Protocol 41: one edition only (the standalone edition is discontinued) */
    static final String PROTOCOL="43";
    static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation(EternalDefense.ID,"control"),()->PROTOCOL,PROTOCOL::equals,PROTOCOL::equals);
    record State(CompoundTag data,String screen,String token,String message){
        static void encode(State p,FriendlyByteBuf b){b.writeNbt(p.data);b.writeUtf(p.screen,24);b.writeUtf(p.token,64);b.writeUtf(p.message,256);}
        static State decode(FriendlyByteBuf b){CompoundTag n=b.readNbt();return new State(n==null?new CompoundTag():n,b.readUtf(24),b.readUtf(64),b.readUtf(256));}
        static void handle(State p,Supplier<NetworkEvent.Context> ctx){ctx.get().enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->BeaconClient.receive(p)));ctx.get().setPacketHandled(true);}
    }
    record Action(String action,String token){
        static void encode(Action p,FriendlyByteBuf b){b.writeUtf(p.action,64);b.writeUtf(p.token,64);}
        static Action decode(FriendlyByteBuf b){return new Action(b.readUtf(64),b.readUtf(64));}
        static void handle(Action p,Supplier<NetworkEvent.Context> ctx){var c=ctx.get();c.enqueueWork(()->{if(c.getSender()!=null)BeaconActions.handle(c.getSender(),p);});c.setPacketHandled(true);}
    }
    /** Centre-screen popups. */
    record Announce(String kind,int a,int b,String who,String what){
        static void encode(Announce p,FriendlyByteBuf buf){buf.writeUtf(p.kind,24);buf.writeVarInt(p.a);buf.writeVarInt(p.b);buf.writeUtf(p.who,64);buf.writeUtf(p.what,48);}
        static Announce decode(FriendlyByteBuf buf){return new Announce(buf.readUtf(24),buf.readVarInt(),buf.readVarInt(),buf.readUtf(64),buf.readUtf(48));}
        static void handle(Announce p,Supplier<NetworkEvent.Context> ctx){ctx.get().enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->BeaconPopups.show(p)));ctx.get().setPacketHandled(true);}
    }
    static void announce(ServerLevel l,String kind,int a,int b,String who,String what){CHANNEL.send(PacketDistributor.ALL.noArg(),new Announce(kind,a,b,who,what));}
    /** The same, to everyone but the player who caused it (they see it on their screen) */
    static void announceOthers(ServerPlayer from,String kind,int a,int b,String who,String what){for(var p:from.server.getPlayerList().getPlayers())if(p!=from)CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new Announce(kind,a,b,who,what));}
    record Placement(net.minecraft.world.phys.BlockHitResult hit,net.minecraft.world.InteractionHand hand){
        static void encode(Placement p,FriendlyByteBuf b){b.writeBlockHitResult(p.hit);b.writeEnum(p.hand);}
        static Placement decode(FriendlyByteBuf b){return new Placement(b.readBlockHitResult(),b.readEnum(net.minecraft.world.InteractionHand.class));}
        static void handle(Placement p,Supplier<NetworkEvent.Context> ctx){var c=ctx.get();c.enqueueWork(()->{if(c.getSender()!=null)BeaconActions.requestPlacement(c.getSender(),new net.minecraft.world.item.context.UseOnContext(c.getSender(),p.hand,p.hit));});c.setPacketHandled(true);}
    }
    /** A menu button, its id as a full number. */
    record MenuButton(int container,int id){
        static void encode(MenuButton p,FriendlyByteBuf b){b.writeVarInt(p.container);b.writeVarInt(p.id);}
        static MenuButton decode(FriendlyByteBuf b){return new MenuButton(b.readVarInt(),b.readVarInt());}
        static void handle(MenuButton p,Supplier<NetworkEvent.Context> ctx){var c=ctx.get();c.enqueueWork(()->{
            var player=c.getSender();if(player==null)return;player.resetLastActionTime();
            var menu=player.containerMenu;if(menu.containerId!=p.container||player.isSpectator())return;
            if(!menu.stillValid(player))return;
            if(menu.clickMenuButton(player,p.id))menu.broadcastChanges();
        });c.setPacketHandled(true);}
    }
    /** Sends a menu button of the open screen (see MenuButton). */
    static void menuButton(int container,int id){CHANNEL.sendToServer(new MenuButton(container,id));}
    static void init(){
        GunPackSync.init();
        CHANNEL.registerMessage(0,State.class,State::encode,State::decode,State::handle,Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1,Action.class,Action::encode,Action::decode,Action::handle,Optional.of(NetworkDirection.PLAY_TO_SERVER));
        WeaponPlatform.initNetwork(CHANNEL);
        CHANNEL.registerMessage(7,ExchangeShop.Open.class,ExchangeShop.Open::encode,ExchangeShop.Open::decode,ExchangeShop.Open::handle,Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(8,ExchangeShop.Buy.class,ExchangeShop.Buy::encode,ExchangeShop.Buy::decode,ExchangeShop.Buy::handle,Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(9,SupportShop.Buy.class,SupportShop.Buy::encode,SupportShop.Buy::decode,SupportShop.Buy::handle,Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(10,SupportShop.Upgrade.class,SupportShop.Upgrade::encode,SupportShop.Upgrade::decode,SupportShop.Upgrade::handle,Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(11,CannonControl.Open.class,CannonControl.Open::encode,CannonControl.Open::decode,CannonControl.Open::handle,Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(12,CannonControl.Act.class,CannonControl.Act::encode,CannonControl.Act::decode,CannonControl.Act::handle,Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(13,SupportHud.Sync.class,SupportHud.Sync::encode,SupportHud.Sync::decode,SupportHud.Sync::handle,Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(14,RaidTypes.Marks.class,RaidTypes.Marks::encode,RaidTypes.Marks::decode,RaidTypes.Marks::handle,Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(16,MessHallMenu.Preview.class,MessHallMenu.Preview::encode,MessHallMenu.Preview::decode,MessHallMenu.Preview::handle,Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        // Tactical Operations: no new client-to-server packet (the table's buttons are vanilla menu buttons)
        CHANNEL.registerMessage(17,Hunts.MissionSync.class,Hunts.MissionSync::encode,Hunts.MissionSync::decode,Hunts.MissionSync::handle,Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(18,TacticalScan.ScanImage.class,TacticalScan.ScanImage::encode,TacticalScan.ScanImage::decode,TacticalScan.ScanImage::handle,Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(19,TacticalScan.ScanProgress.class,TacticalScan.ScanProgress::encode,TacticalScan.ScanProgress::decode,TacticalScan.ScanProgress::handle,Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        // Forward Operating Bases: no new packet; id 20 refreshes the open FOB screen too (its state carries "fobId")
        CHANNEL.registerMessage(21,FobOutline.Sync.class,FobOutline.Sync::encode,FobOutline.Sync::decode,FobOutline.Sync::handle,Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        // Protocol 36: menu buttons as full numbers
        CHANNEL.registerMessage(23,SupplyCatapult.View.class,SupplyCatapult.View::encode,SupplyCatapult.View::decode,SupplyCatapult.View::handle,Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(24,DrinkStationMenu.View.class,DrinkStationMenu.View::encode,DrinkStationMenu.View::decode,DrinkStationMenu.View::handle,Optional.of(NetworkDirection.PLAY_TO_CLIENT));   // Protocol 40
        CHANNEL.registerMessage(22,MenuButton.class,MenuButton::encode,MenuButton::decode,MenuButton::handle,Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(20,Hunts.TableState.class,Hunts.TableState::encode,Hunts.TableState::decode,Hunts.TableState::handle,Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(6,Announce.class,Announce::encode,Announce::decode,Announce::handle,Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(5,Placement.class,Placement::encode,Placement::decode,Placement::handle,Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }
    static void action(String action,String token){CHANNEL.sendToServer(new Action(action,token));}
    static void requestPlacement(Placement request){CHANNEL.sendToServer(request);}
    static CompoundTag state(ServerPlayer p,CampaignData d){
        CompoundTag n=new CompoundTag();n.putString("phase",d.phase);n.putLong("beacon",d.beacon.asLong());
        n.putInt("health",d.health);n.putInt("maximum",d.maximumHealth());n.putInt("radius",d.radius());n.putInt("below",d.below());n.putInt("above",d.above());n.putInt("vertical",d.vertical);
        n.putInt("tier",d.rewardTier);n.putInt("score",d.score);n.putInt("wave",d.wave);n.putInt("waves",Rules.waves(d.raidTier));n.putInt("attackers",d.raiders.size()+d.spawnRemaining);
        var survey=BaseSurvey.get(p.server.overworld());var score=survey.score;int live=liveScore(p.server,d),prospective=prospectiveTier(p.server,d);n.putBoolean("creative",p.isCreative());n.putInt("buildingTier",Rules.buildingTier(live));n.putInt("liveScore",live);n.putInt("prospectiveTier",prospective);n.putInt("nextScore",Rules.buildingTier(live)>=5?0:(Rules.buildingTier(live)+1)*500);n.putInt("surveyPercent",survey.percent());n.putBoolean("surveyBusy",survey.scanning||score==null);n.putBoolean("surveyIncomplete",survey.incomplete);if(score!=null)n.put("scoreBreakdown",score.tag());n.putInt("raidsStarted",d.raidsStarted);n.putBoolean("nextHard",HardRaids.isHard(d.raidsStarted+1));n.putBoolean("hardRaid",d.hardRaid&&d.active());
        n.putInt("defenders",d.phase.equals("raid")?d.wavePlayers:RaidBalance.defenders(p.server.overworld(),d));n.putInt("veteranPercent",(d.phase.equals("raid")?d.waveVeteran:Rules.veteranPressure(prospective,d.victories))*10);
        n.putInt("platformScore",PlatformRegistry.get(p.server.overworld()).score(d));
        n.putLong("untilRaid",Math.max(0,Rules.intervalDays(d.rewardTier)*24000L-d.preparationTicks)+d.respiteTicks);n.putLong("respiteTicks",d.respiteTicks);n.putInt("respitePurchases",d.respitePurchases);n.putInt("nextRaidBonus",d.nextRaidBonus);n.putBoolean("introCompleted",d.introCompleted);n.put("respiteCosts",WeaponPlatform.costTags(Supply.at(p,d.beacon),RaidRespite.costs(d)));n.putInt("interval",Rules.intervalDays(d.rewardTier));
        n.putInt("raidLimit",d.raidLimit);n.putBoolean("raidRequested",d.raidRequested);n.putBoolean("rewardChest",!d.rewardBox.isEmpty());n.putInt("core",d.core);n.putInt("logistics",d.logistics);n.putInt("defense",d.defense);n.putInt("restoration",d.restoration);n.putInt("reconnaissance",d.reconnaissance);
        n.putInt("mk",DefenseStructures.actualMark(p.server.overworld(),d.beacon));n.putBoolean("hurtbox",d.showHurtbox);n.putBoolean("expansionBlocked",d.installed()&&DefenseStructures.actualMark(p.server.overworld(),d.beacon)!=DefenseStructures.mark(d.core));n.putBoolean("beam",d.showBeam);n.putBoolean("underAttack",d.underAttack(p.server.overworld().getGameTime()));n.putBoolean("outline",d.showBoundary);n.putBoolean("near",EternalDefense.near(p,d));n.putBoolean("installed",d.installed());n.putBoolean("active",d.active());
        n.putInt("livesLeft",Rules.livesLeft(d.deaths));n.putInt("hubs",HubZone.active(p.serverLevel()));   // The raid skull, the Hub count
        n.putInt("rewards",d.rewards.size());n.putString("raidType",d.raidType);n.putString("nextRaidType",d.nextRaidType);
        // Raid Control: what the next raid brings and pays, and the modifiers chosen for it
        int defenders=n.getInt("defenders"),veteran=Rules.veteranPressure(prospective,d.victories);
        n.putInt("baseTier",d.introRaid?0:Rules.rewardTier(d.logistics,live));n.putInt("nextWaves",Rules.waves(prospective));
        boolean prep=d.phase.equals("preparation");var mods=prep?d.chosenMods:d.activeMods;String type=prep?d.nextRaidType:d.raidType;
        n.putInt("firstWave",RaidModifiers.attackers(Rules.waveEnemies(prospective,1,defenders,veteran,false),type,mods));n.putInt("lastWave",RaidModifiers.attackers(Rules.waveEnemies(prospective,Rules.waves(prospective),defenders,veteran,false),type,mods));
        n.putFloat("elitesPerWave",(float)RaidModifiers.elites(prospective,mods));n.putInt("raidArdent",RaidModifiers.raised(Rules.raidArdent(prospective),RaidModifiers.payoutPercent(mods)));n.putInt("specialBonus",RaidTypes.bonusEnergy(prospective));
        var chosen=new net.minecraft.nbt.ListTag();for(String id:d.chosenMods)chosen.add(net.minecraft.nbt.StringTag.valueOf(id));n.put("chosenMods",chosen);
        var active=new net.minecraft.nbt.ListTag();for(String id:d.activeMods)active.add(net.minecraft.nbt.StringTag.valueOf(id));n.put("activeMods",active);
        n.putInt("modPayout",RaidModifiers.payoutPercent(mods));n.putInt("modDrops",RaidModifiers.dropPercent(mods,type));
        n.putInt("eliteWaves",d.eliteWaves);
        n.putBoolean("controller",p.getMainHandItem().is(EternalDefense.CONTROLLER.get())||p.getOffhandItem().is(EternalDefense.CONTROLLER.get()));
        n.putBoolean("canRemove",p.distanceToSqr(d.beacon.getX()+.5,d.beacon.getY()+.5,d.beacon.getZ()+.5)<=64);
        for(String part:java.util.List.of("reinforced_plating","logistics_module","resonance_coil","restoration_matrix","ardent_energy")) {
            // What the upgrade and repair buttons can reach: the pack, then the Home Zone's Hubs
            var item=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(new ResourceLocation(EternalDefense.ID,part));
            n.putInt("stock_"+part,Supply.count(Supply.at(p,d.beacon),item));
        }
        Fobs.status(p,n);   // The FOB line of the status card
        n.put("fabrications",WorkshopFabrication.state(p));return n;
    }
    static void open(ServerPlayer p,String screen,String token){sendState(p,screen,token,"");}
    static void sendState(ServerPlayer p,String screen,String token,String message){
        if(p instanceof net.minecraftforge.common.util.FakePlayer)return;
        var n=state(p,CampaignData.get(p.server.overworld()));if(screen.equals("rewards"))n.put("rewardTiers",RaidRewards.previews(p.server.overworld()));
        CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new State(n,screen,token,message));
    }
    /** The base score now: the raid's snapshot while a raid runs */
    static int liveScore(net.minecraft.server.MinecraftServer server,CampaignData d){var score=BaseSurvey.get(server.overworld()).score;return d.active()||score==null?d.score:score.total();}
    /** The tier the beacon shows as "Next payout": the running raid's tier, else the live preview */
    static int prospectiveTier(net.minecraft.server.MinecraftServer server,CampaignData d){
        return d.phase.equals("raid")||d.phase.equals("restore")?d.raidTier:d.introRaid?0:RaidRespite.previewTier(d,Rules.rewardTier(d.logistics,liveScore(server,d)));
    }
    static void syncNearby(ServerLevel l,CampaignData d){for(ServerPlayer p:l.getServer().getPlayerList().getPlayers())sendState(p,"","","");}
}
