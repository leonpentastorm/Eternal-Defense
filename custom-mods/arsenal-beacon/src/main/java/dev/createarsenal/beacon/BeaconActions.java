package dev.createarsenal.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import java.util.*;

final class BeaconActions {
    record Pending(String kind,String token,BlockHitResult hit,InteractionHand hand,long expires,long serial){}
    private static final Map<UUID,Pending> pending=new HashMap<>();
    private static boolean inAction;
    static boolean inAction(){return inAction;}
    static void clear(){pending.clear();}
    static String pendingToken(ServerPlayer p){var request=pending.get(p.getUUID());return request==null?"":request.token;}
    static void requestPlacement(ServerPlayer p,UseOnContext context){
        var d=CampaignData.get(p.server.overworld());
        if(p.level().dimension()!=Level.OVERWORLD){placementFailed(p,"Place the beacon in the Overworld.");return;}
        if(!p.isAlive()||p.isSpectator()||!p.mayBuild()||!close(p,context.getClickedPos())||!p.getItemInHand(context.getHand()).is(ArsenalBeacon.BEACON_ITEM.get()))return;
        if(!p.server.overworld().hasChunkAt(context.getClickedPos())){ArsenalBeacon.feedback(p,"[Create Arsenal] Beacon area is still loading. Please wait; placement will retry automatically.");return;}
        if(!p.server.overworld().getWorldBorder().isWithinBounds(context.getClickedPos()))return;
        ArsenalBeacon.reconcileMissing(p.server.overworld(),d);
        if(!d.phase.equals("unplaced")){placementFailed(p,"One shared beacon per world. Use its recovery shovel to remove it first.");return;}
        var existing=pending.get(p.getUUID());
        if(existing!=null&&existing.kind.equals("place")&&existing.expires>=p.server.overworld().getGameTime()&&existing.serial==d.campaignSerial&&existing.hand==context.getHand()&&existing.hit.getBlockPos().equals(context.getClickedPos())&&existing.hit.getDirection()==context.getClickedFace()){
            BeaconNetwork.open(p,"place",existing.token);return;
        }
        String token=UUID.randomUUID().toString();
        var hit=new BlockHitResult(context.getClickLocation(),context.getClickedFace(),context.getClickedPos(),context.isInside());
        pending.put(p.getUUID(),new Pending("place",token,hit,context.getHand(),p.server.overworld().getGameTime()+1200,d.campaignSerial));BeaconNetwork.open(p,"place",token);
    }
    private static void placementFailed(ServerPlayer p,String message){ArsenalBeacon.feedback(p,message);BeaconNetwork.sendState(p,"placementFailed","",message);}
    static void requestRemoval(ServerPlayer p){
        var d=CampaignData.get(p.server.overworld());
        if(!d.installed()||!close(p,d.beacon)||!holding(p)){ArsenalBeacon.feedback(p,"Hold the Beacon Controller and stand within 8 blocks of the beacon.");return;}
        String token=UUID.randomUUID().toString();
        pending.put(p.getUUID(),new Pending("remove",token,null,InteractionHand.MAIN_HAND,p.server.overworld().getGameTime()+1200,d.campaignSerial));BeaconNetwork.open(p,"remove",token);
    }
    static boolean close(ServerPlayer p,BlockPos pos){return p.level().dimension()==Level.OVERWORLD&&p.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<=64;}
    static boolean holding(ServerPlayer p){return p.getMainHandItem().is(ArsenalBeacon.CONTROLLER.get())||p.getOffhandItem().is(ArsenalBeacon.CONTROLLER.get());}
    static boolean confirm(ServerPlayer p,String token){
        Pending request=pending.remove(p.getUUID());
        if(request==null||!request.token.equals(token)||request.expires<p.server.overworld().getGameTime()||p.level().dimension()!=Level.OVERWORLD)return false;
        var l=p.server.overworld();var d=CampaignData.get(l);
        if(request.kind.equals("place")){
            if(!d.phase.equals("unplaced")||!close(p,request.hit.getBlockPos())||!p.getItemInHand(request.hand).is(ArsenalBeacon.BEACON_ITEM.get()))return false;
            return ((BeaconItems.Placement)ArsenalBeacon.BEACON_ITEM.get()).confirmedPlace(new UseOnContext(p,request.hand,request.hit)).consumesAction();
        }
        if(!d.installed()||request.serial!=d.campaignSerial||!close(p,d.beacon)||!holding(p))return false;
        ArsenalBeacon.decommission(l,d,p);return true;
    }
    static void handle(ServerPlayer p,BeaconNetwork.Action action){
        inAction=true;
        try {
            var d=CampaignData.get(p.server.overworld());String a=action.action();
            if(a.equals("cancel")){var request=pending.get(p.getUUID());if(request!=null&&request.token.equals(action.token()))pending.remove(p.getUUID());return;}
            if(a.equals("confirm")){boolean ok=confirm(p,action.token());BeaconNetwork.sendState(p,ok?"close":"","",ok?"":"Confirmation expired or conditions changed. Try again.");return;}
            if(a.equals("rewards")){BeaconNetwork.open(p,"rewards","");return;}
            if(a.equals("survey")){BaseSurvey.request(p.server.overworld());BeaconNetwork.sendState(p,"","","Rescanning loaded base blocks...");return;}
            if(a.equals("guide")){BeaconNetwork.open(p,"guide","");return;}
            if(a.equals("remove")){requestRemoval(p);return;}
            if(!d.installed()||!ArsenalBeacon.near(p,d)){BeaconNetwork.sendState(p,"","","Move near your planted beacon.");return;}
            if(a.equals("outline")){d.showBoundary=!d.showBoundary;d.setDirty();BeaconNetwork.sendState(p,"","",d.showBoundary?"3D base outline enabled.":"3D base outline hidden.");}
            else if(a.equals("hurtbox")){d.showHurtbox=!d.showHurtbox;d.setDirty();BeaconNetwork.sendState(p,"","",d.showHurtbox?"Red beacon damage outline enabled.":"Beacon damage outline hidden.");}
            else if(a.equals("beam")){d.showBeam=!d.showBeam;d.setDirty();BeaconNetwork.sendState(p,"","",d.showBeam?"Beacon beam enabled.":"Beacon beam hidden.");}
            else if(a.equals("claim")){String result=RewardCache.claim(p,d);ArsenalBeacon.feedback(p,result);BeaconNetwork.sendState(p,"","",result);}
            else if(a.equals("repair"))ArsenalBeacon.repair(p);
            else if(a.equals("start-raid"))BeaconNetwork.sendState(p,"","",RaidSelection.start(p));
            else if(a.startsWith("raid-level:"))BeaconNetwork.sendState(p,"","",RaidSelection.select(p,a.substring(11)));
            else if(a.equals("respite"))BeaconNetwork.sendState(p,"","",RaidRespite.buy(p));
            else if(a.startsWith("upgrade:"))ArsenalBeacon.upgrade(p,a.substring(8));
            BeaconNetwork.syncNearby(p.server.overworld(),d);
        } finally {inAction=false;}
    }
}
