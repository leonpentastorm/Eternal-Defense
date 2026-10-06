package dev.createarsenal.beacon;

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
    static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation(ArsenalBeacon.ID,"control"),()->BuildFlavor.STANDALONE?"18-standalone":"18-pack",s->s.equals(BuildFlavor.STANDALONE?"18-standalone":"18-pack"),s->s.equals(BuildFlavor.STANDALONE?"18-standalone":"18-pack"));
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
    /** Centre-screen popups. Text is built on the client from language keys, so servers send only ids and numbers. */
    record Announce(String kind,int a,int b,String who,String what){
        static void encode(Announce p,FriendlyByteBuf buf){buf.writeUtf(p.kind,24);buf.writeVarInt(p.a);buf.writeVarInt(p.b);buf.writeUtf(p.who,64);buf.writeUtf(p.what,48);}
        static Announce decode(FriendlyByteBuf buf){return new Announce(buf.readUtf(24),buf.readVarInt(),buf.readVarInt(),buf.readUtf(64),buf.readUtf(48));}
        static void handle(Announce p,Supplier<NetworkEvent.Context> ctx){ctx.get().enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->BeaconPopups.show(p)));ctx.get().setPacketHandled(true);}
    }
    static void announce(ServerLevel l,String kind,int a,int b,String who,String what){CHANNEL.send(PacketDistributor.ALL.noArg(),new Announce(kind,a,b,who,what));}
    record Placement(net.minecraft.world.phys.BlockHitResult hit,net.minecraft.world.InteractionHand hand){
        static void encode(Placement p,FriendlyByteBuf b){b.writeBlockHitResult(p.hit);b.writeEnum(p.hand);}
        static Placement decode(FriendlyByteBuf b){return new Placement(b.readBlockHitResult(),b.readEnum(net.minecraft.world.InteractionHand.class));}
        static void handle(Placement p,Supplier<NetworkEvent.Context> ctx){var c=ctx.get();c.enqueueWork(()->{if(c.getSender()!=null)BeaconActions.requestPlacement(c.getSender(),new net.minecraft.world.item.context.UseOnContext(c.getSender(),p.hand,p.hit));});c.setPacketHandled(true);}
    }
    static void init(){
        GunPackSync.init();
        CHANNEL.registerMessage(0,State.class,State::encode,State::decode,State::handle,Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1,Action.class,Action::encode,Action::decode,Action::handle,Optional.of(NetworkDirection.PLAY_TO_SERVER));
        WeaponPlatform.initNetwork(CHANNEL);
        CHANNEL.registerMessage(7,ExchangeShop.Open.class,ExchangeShop.Open::encode,ExchangeShop.Open::decode,ExchangeShop.Open::handle,Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(8,ExchangeShop.Buy.class,ExchangeShop.Buy::encode,ExchangeShop.Buy::decode,ExchangeShop.Buy::handle,Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(9,SupportShop.Buy.class,SupportShop.Buy::encode,SupportShop.Buy::decode,SupportShop.Buy::handle,Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(10,SupportShop.Upgrade.class,SupportShop.Upgrade::encode,SupportShop.Upgrade::decode,SupportShop.Upgrade::handle,Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(6,Announce.class,Announce::encode,Announce::decode,Announce::handle,Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(5,Placement.class,Placement::encode,Placement::decode,Placement::handle,Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }
    static void action(String action,String token){CHANNEL.sendToServer(new Action(action,token));}
    static void requestPlacement(Placement request){CHANNEL.sendToServer(request);}
    static CompoundTag state(ServerPlayer p,CampaignData d){
        CompoundTag n=new CompoundTag();n.putBoolean("standalone",BuildFlavor.STANDALONE);n.putString("phase",d.phase);n.putLong("beacon",d.beacon.asLong());
        n.putInt("health",d.health);n.putInt("maximum",d.maximumHealth());n.putInt("radius",d.radius());n.putInt("below",d.below());n.putInt("above",d.above());n.putInt("vertical",d.vertical);
        n.putInt("tier",d.rewardTier);n.putInt("score",d.score);n.putInt("wave",d.wave);n.putInt("waves",Rules.waves(d.raidTier));n.putInt("attackers",d.raiders.size()+d.spawnRemaining);
        var survey=BaseSurvey.get(p.server.overworld());var score=survey.score;int live=d.active()||score==null?d.score:score.total(),prospective=d.phase.equals("raid")||d.phase.equals("restore")?d.raidTier:d.introRaid?0:RaidRespite.previewTier(d,Rules.rewardTier(d.logistics,live));n.putBoolean("creative",p.isCreative());n.putInt("buildingTier",Rules.buildingTier(live));n.putInt("liveScore",live);n.putInt("prospectiveTier",prospective);n.putInt("nextScore",Rules.buildingTier(live)>=5?0:(Rules.buildingTier(live)+1)*500);n.putInt("surveyPercent",survey.percent());n.putBoolean("surveyBusy",survey.scanning||score==null);n.putBoolean("surveyIncomplete",survey.incomplete);if(score!=null)n.put("scoreBreakdown",score.tag());n.putInt("raidsStarted",d.raidsStarted);n.putBoolean("nextHard",HardRaids.isHard(d.raidsStarted+1));n.putBoolean("hardRaid",d.hardRaid&&d.active());
        n.putInt("defenders",d.phase.equals("raid")?d.wavePlayers:RaidBalance.defenders(p.server.overworld(),d));n.putInt("veteranPercent",(d.phase.equals("raid")?d.waveVeteran:Rules.veteranPressure(prospective,d.victories))*10);
        n.putInt("platformScore",PlatformRegistry.get(p.server.overworld()).score(d));
        n.putLong("untilRaid",Math.max(0,Rules.intervalDays(d.rewardTier)*24000L-d.preparationTicks)+d.respiteTicks);n.putLong("respiteTicks",d.respiteTicks);n.putInt("respitePurchases",d.respitePurchases);n.putInt("nextRaidBonus",d.nextRaidBonus);n.putBoolean("introCompleted",d.introCompleted);n.put("respiteCosts",WeaponPlatform.costTags(p,RaidRespite.costs(d)));n.putInt("interval",Rules.intervalDays(d.rewardTier));
        n.putInt("raidLimit",d.raidLimit);n.putBoolean("rewardChest",RewardCache.container(p.server.overworld(),d)!=null);n.putInt("core",d.core);n.putInt("logistics",d.logistics);n.putInt("defense",d.defense);n.putInt("restoration",d.restoration);n.putInt("reconnaissance",d.reconnaissance);
        n.putInt("mk",ArsenalStructures.actualMark(p.server.overworld(),d.beacon));n.putBoolean("hurtbox",d.showHurtbox);n.putBoolean("expansionBlocked",d.installed()&&ArsenalStructures.actualMark(p.server.overworld(),d.beacon)!=ArsenalStructures.mark(d.core));n.putBoolean("beam",d.showBeam);n.putBoolean("underAttack",d.underAttack(p.server.overworld().getGameTime()));n.putBoolean("outline",d.showBoundary);n.putBoolean("near",ArsenalBeacon.near(p,d));n.putBoolean("installed",d.installed());n.putBoolean("active",d.active());
        n.putInt("rewards",d.rewards.size());
        n.putBoolean("controller",p.getMainHandItem().is(ArsenalBeacon.CONTROLLER.get())||p.getOffhandItem().is(ArsenalBeacon.CONTROLLER.get()));
        n.putBoolean("canRemove",p.distanceToSqr(d.beacon.getX()+.5,d.beacon.getY()+.5,d.beacon.getZ()+.5)<=64);
        for(String part:java.util.List.of("reinforced_plating","logistics_module","resonance_coil","restoration_matrix")) {
            int count=0;var item=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(new ResourceLocation(ArsenalBeacon.ID,part));
            for(var stack:p.getInventory().items)if(stack.is(item))count+=stack.getCount();n.putInt("stock_"+part,count);
        }
        return n;
    }
    static void open(ServerPlayer p,String screen,String token){sendState(p,screen,token,"");}
    static void sendState(ServerPlayer p,String screen,String token,String message){
        if(p instanceof net.minecraftforge.common.util.FakePlayer)return;
        var n=state(p,CampaignData.get(p.server.overworld()));if(screen.equals("rewards"))n.put("rewardTiers",RaidRewards.previews(p.server.overworld()));
        CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new State(n,screen,token,message));
    }
    static void syncNearby(ServerLevel l,CampaignData d){for(ServerPlayer p:l.getServer().getPlayerList().getPlayers())sendState(p,"","","");}
}
