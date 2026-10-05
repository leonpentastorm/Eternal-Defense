package dev.createarsenal.beacon;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.*;

/** Shared real Create advancement milestones, independent of every workstation Age. */
final class CreateUnlocks {
    static final String[] REQUIRED={"mechanical_press","mechanical_mixer","deployer","precision_mechanism","steam_engine"};
    @SubscribeEvent public void earned(AdvancementEvent.AdvancementEarnEvent event){
        if(event.getEntity() instanceof ServerPlayer player){var id=event.getAdvancement().getId();if(id.getNamespace().equals("create")&&Arrays.asList(REQUIRED).contains(id.getPath())){
            var data=PlatformRegistry.get(player.server.overworld());if(data.createMilestones.add(id.getPath()))data.setDirty();
        }}
    }
    static void sync(ServerPlayer player){
        var data=PlatformRegistry.get(player.server.overworld());
        for(var p:player.server.getPlayerList().getPlayers())scan(p,data);
        scan(player,data); // Existing saves and newly joining teammates keep their earned milestones.
    }
    private static void scan(ServerPlayer p,PlatformRegistry data){for(String id:REQUIRED){var a=p.server.getAdvancements().getAdvancement(new ResourceLocation("create",id));if(a!=null&&p.getAdvancements().getOrStartProgress(a).isDone()&&data.createMilestones.add(id))data.setDirty();}}
    static int level(ServerPlayer p){if(BuildFlavor.STANDALONE&&!net.minecraftforge.fml.ModList.get().isLoaded("create"))return WeaponPlatform.visitedNether(p)?2:1;sync(p);var milestones=PlatformRegistry.get(p.server.overworld()).createMilestones;
        if(!milestones.containsAll(List.of("mechanical_press","mechanical_mixer")))return 0;
        if(!milestones.containsAll(List.of("deployer","precision_mechanism")))return 1;
        return milestones.contains("steam_engine")?3:2;
    }
    static int required(WeaponPlatform.Entry e){
        String id=e.gate().id();
        if(DisplayRacks.entry(e))return 0;
        if(e.gate().kind().equals("supply"))return e.gate().age()<=2?0:e.gate().age()<=3?1:2;
        if(supply(e)){var n=e.output().getOrCreateTag();String component=n.getString("ConsumableId")+n.getString("ThrowableId");return component.contains("c4")||component.contains("rgn")||component.contains("ibuprofen")||component.contains("amoxycillin")?2:n.contains("ThrowableId")||n.contains("ConsumableId")&&!component.contains("condensed_milk")?1:0;}
        if(e.gate().kind().equals("gun"))return id.contains("cannon")||id.contains("gl_revolver")||id.contains("mg_platemag")||id.contains("clockwork")||id.equals("lrl:db_long_super")?3:id.contains("torque")||id.contains("shotgun_db")||id.contains("melee_wrench")?1:2;
        return e.gate().age()<=2?1:e.gate().age()<=4?2:3;
    }
    static boolean supply(WeaponPlatform.Entry e){if(DisplayRacks.entry(e)||e.gate().kind().equals("supply"))return true;if(!e.gate().kind().equals("attachment"))return false;try{e.output().getItem().getClass().getMethod("getAttachmentId",net.minecraft.world.item.ItemStack.class);return false;}catch(NoSuchMethodException ex){return true;}}
    static boolean armory(WeaponPlatform.Entry e){return e.gate().id().startsWith("create_armorer:")||e.gate().id().equals("lrl:db_long_super");}
    static boolean turret(WeaponPlatform.Entry e){return e.gate().kind().equals("turret");}
    static String requirement(int level){if(BuildFlavor.STANDALONE)return level<2?"Available from the start":"Visit the Nether";return switch(level){case 0->"Available from the start";case 1->"Operate a Mechanical Press and Mechanical Mixer";case 2->"Operate a Deployer and make a Precision Mechanism";default->"Operate a Steam Engine";};}
    static String stage(int level){if(BuildFlavor.STANDALONE)return level>=2?"Nether unlocked":"Overworld";return switch(level){case 0->"Not unlocked";case 1->"Workshop";case 2->"Precision";default->"Steam";};}
    static boolean unlocked(ServerPlayer p,WeaponPlatform.Entry e,int age){if(BuildFlavor.STANDALONE)return p.isCreative()||(DisplayRacks.entry(e)?true:turret(e)?WeaponPlatform.visitedNether(p):CreateUnlocks.supply(e)?required(e)<2||WeaponPlatform.visitedNether(p):e.gate().age()<=age);return p.isCreative()|| (MagazineBridge.isMagazine(e.output())?MagazineBridge.unlocked(p,e,age):turret(e)?level(p)>=2:supply(e)?level(p)>=required(e):armory(e)?level(p)>=required(e):e.gate().age()<=age);}
    static String label(WeaponPlatform.Entry e){return turret(e)?"Create: Precision defenses":supply(e)?required(e)==0?"Supplies: Field equipment":"Supplies: Create "+stage(required(e)):armory(e)?"Create: "+stage(required(e)):"Age "+e.gate().age()+": "+WeaponPlatform.AGES[e.gate().age()];}
}
