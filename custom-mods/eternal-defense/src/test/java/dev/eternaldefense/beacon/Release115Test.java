package dev.eternaldefense.beacon;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** The owner's 1.1.5 list: giant Badass elites, drops at 1.5 times (twice with everything on), red raid buttons, Fog of War, test commands. */
final class Release115Test {
    static String source(String name) throws Exception{return Release114Test.source(name);}
    static String text(String key) throws Exception{return Release114Test.text(key);}

    @Test void badassGrowsIntoAFiveTimesGiant(){
        float[] sizes={1.5f,2.2f,3.0f,4.0f,5.0f};
        for(int l=1;l<=5;l++)assertEquals(sizes[l-1],Elites.scale(Elites.Kind.BADASS,l),1e-6,"Badass "+l);
        assertEquals(5.0f,Elites.scale(Elites.Kind.BADASS,9),1e-6,"never bigger than five times");
        for(int l=2;l<=5;l++)assertTrue(Elites.scale(Elites.Kind.BADASS,l)>=Elites.GIANT,"Badass II and up smash their way");assertTrue(Elites.scale(Elites.Kind.BADASS,1)<Elites.GIANT);
        assertEquals(0,Elites.stride(1),1e-9);assertEquals(2.4,Elites.stride(5),1e-9,"a five times giant climbs three blocks");
        assertTrue(Elites.OURS.contains(Elites.STEP),"the raid's balancing keeps the giant's stride");
    }
    @Test void giantsSmashAndAreDrawnRight() throws Exception{
        String raid=source("EternalDefense.java");assertTrue(raid.contains("if(idle&&Elites.giant(mob))RaidBreaching.smash(l,d,mob);"));
        String smash=source("RaidBreaching.java");assertTrue(smash.contains("static int smash(")&&smash.contains("EternalDefense.damageBlock(l,d,at)"),"inside the base a smashed block goes in the repair journal");
        String client=source("EliteClient.java");
        assertTrue(client.contains("public static void end(Entity e,PoseStack pose)")&&client.contains("pose.popPose()"),"the size is undone right after the mob is drawn");
        assertTrue(client.contains("RenderNameTagEvent")&&client.contains("scale(1/s,1/s,1/s)"),"the name tag sits above the grown hitbox, not five times higher");
    }
    @Test void dropsAreHalfAgainAndTwiceWithEverythingOn(){
        assertEquals(1.5,DefenseConfig.MARKED_MULTIPLIER.getDefault(),1e-9);
        var all=RaidModifiers.MODS.stream().map(RaidModifiers.Mod::id).toList();
        assertEquals(50,RaidModifiers.dropPercent(all,"swarm"),"every modifier and a special raid add half the base chance");
        assertEquals(2.0,ArdentEnergy.multiplier(1.5,RaidModifiers.dropPercent(all,"air"),0),1e-9,"twice the original chance");
        assertEquals(85,RaidModifiers.payoutPercent(all),"the raid payout stays as it was");
        assertEquals(List.of(20,15,15,10,10,15),RaidModifiers.MODS.stream().map(RaidModifiers.Mod::payout).toList());
        assertEquals(1,ArdentEnergy.drop(false,.99,.0667,1.5,0,.01,false,12,20,1),"an elite made by the test command drops its own too");
    }
    @Test void raidButtonsAreRedAndFogOfWarSaysWhatItDoes() throws Exception{
        assertTrue(source("BeaconClient.java").contains("Ui.Look.DANGER,b->BeaconNetwork.action(\"start-raid\""),"Start raid is red");
        assertTrue(source("FobClient.java").contains("Ui.t(\"fob.screen.call\"),Ui.Look.DANGER"),"the FOB's Call the raid is red");
        assertTrue(text("raidmod.fog_of_war.desc").contains("Switches off your Reconnaissance upgrade"));
        assertTrue(text("raidmod.fog_of_war.short").contains("Reconnaissance"));
        String raid=source("EternalDefense.java");assertTrue(raid.contains("static boolean revealed(CampaignData d){return !RaidModifiers.on(d,\"fog_of_war\")"));
        assertFalse(raid.contains("setGlowingTag(Rules.highlightAttackers"),"no raider glows under Fog of War, not even as it spawns");
        assertTrue(source("SpecialForcesRaids.java").contains("EternalDefense.revealed(d)"));
        assertTrue(text("raid.mods_title").contains("HOWEVER IT STARTS")&&text("guide.control.body").contains("whether you press Start raid or its timer runs out"));
    }
    @Test void operatorsHaveTestCommands() throws Exception{
        String commands=source("OperatorCommands.java");
        for(String name:List.of("\"elite\"","\"raid-mod\"","\"raid-timer\"","\"raid-info\"","\"balance\"","\"ages\"","\"reload\""))assertTrue(commands.contains("op("+name+")")||commands.contains("literal("+name+")"),name);
        assertTrue(commands.contains("requires(s->s.hasPermission(2))"),"operators only");
        assertTrue(source("EternalDefense.java").contains("for(var command:OperatorCommands.all())"));
    }
}
