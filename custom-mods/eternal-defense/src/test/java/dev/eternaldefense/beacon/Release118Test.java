package dev.eternaldefense.beacon;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** 1.1.8: elites look right and stand out whatever renders them; other mods' changes switch a feature off instead of crashing. */
final class Release118Test {
    static String source(String name) throws Exception{return Release114Test.source(name);}
    static String mixin(String name) throws Exception{return Files.readString(Path.of("src/main/java/dev/eternaldefense/beacon/mixin/"+name));}
    static String text(String key) throws Exception{return Release114Test.text(key);}

    @Test void theSizeIsAppliedAroundAnyRenderer() throws Exception{
        var config=com.google.gson.JsonParser.parseString(Files.readString(Path.of("src/main/resources/eternal_defense.mixins.json"))).getAsJsonObject();
        assertTrue(config.getAsJsonArray("client").toString().contains("\"EliteScaleMixin\""),"client side only");
        assertFalse(config.getAsJsonArray("mixins").toString().contains("EliteScaleMixin"));
        String scale=mixin("EliteScaleMixin.java");
        assertTrue(scale.contains("@Mixin(EntityRenderDispatcher.class)"));
        assertTrue(scale.contains("Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"),"around the renderer's own draw, vanilla or GeckoLib");
        assertTrue(scale.contains("EliteClient.begin(entity,pose)")&&scale.contains("EliteClient.end(entity,pose)"));
        String client=source("EliteClient.java");
        assertFalse(client.contains("RenderLivingEvent"),"no longer tied to living-entity renderers");
        assertFalse(source("SupportClient.java").contains("RenderLivingEvent"),"the raid mark and parachute are not either");
        assertTrue(client.contains("RenderLevelStageEvent.Stage.AFTER_ENTITIES")&&client.contains("mc.level.entitiesForRendering()"),"marks, auras and bars are drawn for the entities in view");
    }
    @Test void aGiantsNameBarAndMarkFloatHigherInProportion() throws Exception{
        String client=source("EliteClient.java");
        assertTrue(client.contains("e.getPoseStack().scale(1/s,1/s,1/s);e.getPoseStack().translate(0,.5f*(s-1),0);"),"the name at its hitbox plus half a block times the size");
        assertTrue(client.contains("bar(pose,mob,top+.56f*lift,color,facing)")&&client.contains("top+(code!=null?.8f:.75f)*lift"),"the bar and the mark above it, in the same proportion");
        assertTrue(client.contains("static float lift(Entity e){Float s=scale(e);return s==null?1:s;}"),"a mob that is not grown keeps the usual gap");
    }
    @Test void everyEliteKindHasItsOwnColourAndTheCodeRoundTrips(){
        Set<Integer> colours=new HashSet<>();
        for(var kind:Elites.Kind.values()){
            assertTrue(colours.add(kind.color),"one colour per kind: "+kind);
            for(int level=1;level<=5;level++){int code=Elites.code(kind,level);assertEquals(kind,Elites.kindOf(code));assertEquals(level,code&7);}
            assertEquals(1,Elites.code(kind,0)&7,"levels are 1 to 5");assertEquals(5,Elites.code(kind,9)&7);
        }
        assertEquals(Elites.Kind.BADASS.color,Elites.color(Elites.code(Elites.Kind.BADASS,3)));
        assertEquals(0xFF3B30,Elites.color(Elites.code(Elites.Kind.BADASS,4)),"Badass IV and V are red, like their name");
        assertNull(Elites.kindOf(999));assertEquals(0xFFFFFF,Elites.color(999));
    }
    @Test void theMarksCarryEveryEliteAndItsKind() throws Exception{
        var marks=new RaidTypes.Marks(new int[]{4,7},new int[]{7},new int[]{4},new float[]{3f},new int[]{4,9},new int[]{Elites.code(Elites.Kind.BADASS,3),Elites.code(Elites.Kind.HEALER,1)});
        var buf=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        RaidTypes.Marks.encode(marks,buf);var back=RaidTypes.Marks.decode(buf);
        assertArrayEquals(marks.raiders(),back.raiders());assertArrayEquals(marks.chuted(),back.chuted());assertArrayEquals(marks.scaled(),back.scaled());
        assertArrayEquals(marks.scales(),back.scales());assertArrayEquals(marks.elites(),back.elites());assertArrayEquals(marks.codes(),back.codes());
        assertFalse(buf.isReadable());buf.release();
        String types=source("RaidTypes.java");
        assertTrue(types.contains("Elites.KINDS.put(p.elites[i],p.codes[i])"));
        assertTrue(types.contains("elite(m,scaled,scales,elites,codes)"),"a loose elite outside the raid is sent too");
        assertEquals("43",BeaconNetwork.PROTOCOL,"the marks packet changed");
    }
    @Test void auraAndHealthBarAreBrightAndInTheGuide() throws Exception{
        String client=source("EliteClient.java");
        assertTrue(client.contains("RenderType.eyes(RING)"),"the ring glows in the dark");
        assertTrue(client.contains("RenderType.entityTranslucentEmissive(BAR)")&&client.contains("mob.getHealth()/Math.max(1,mob.getMaxHealth())"),"the bar shows its health, full bright");
        for(String png:List.of("elite_ring.png","elite_bar.png"))assertTrue(Files.exists(Path.of("src/main/resources/assets/eternal_defense/textures/entity/"+png)),png);
        assertTrue(text("guide.raids.body").contains("A glowing ring turns at their feet and a health bar floats over their head"));
        assertTrue(text("guide.raids.detail").contains("Elite colours (ring and health bar): Badass gold, red from IV"));
    }
    @Test void anotherModsBossKeepsItsOwnFight() throws Exception{
        String combat=source("BeaconCombat.java");
        assertTrue(combat.contains("if(bossOutsideRaid(mob))return;"),"never sent at the beacon");
        assertTrue(combat.contains("!mob.getPersistentData().getBoolean(\"eternalRaider\")&&(mob.getType().is(net.minecraftforge.common.Tags.EntityTypes.BOSSES)||ArdentEnergy.defaultHealth(mob)>=ArdentEnergy.BOSS_BASE)"),"the raid's own boss still attacks");
    }
    @Test void otherModsChangingSwitchAFeatureOffInsteadOfCrashing() throws Exception{
        for(String file:List.of("RaidRewards.java","HardRaids.java","MealGunBackend.java","MealGunCompat.java","AmmoCoins.java","WeaponTypes.java","SpecialForcesRaids.java","ArmorJeiCategory.java","GunPackSync.java"))
            assertFalse(source(file).contains("throw new IllegalStateException"),file+" no longer stops the game");
        String hard=source("HardRaids.java");
        assertTrue(hard.contains("Mob mob=type.create(l) instanceof Mob boss&&awaken(boss,false)?boss:EntityType.RAVAGER.create(l);"),"a boss that cannot be woken is replaced by a ravager");
        assertTrue(source("RaidRewards.java").contains("if(stack.isEmpty())return;"),"a missing grenade or kit is left out of the cache");
        assertTrue(source("GunPackSync.java").contains("if(bytes==null)return false;"),"TaCZ sends its own packet then");
        assertTrue(source("EternalDefense.java").contains("SpecialForcesRaids.available())return SpecialForcesRaids.spawn("),"no soldiers when their mod changed: the wave sends its usual mobs");
        Compat.warn("a test feature",null);assertTrue(Compat.broken("a test feature"));assertFalse(Compat.broken("another feature"));
    }
    @Test void hooksIntoOtherModsAreOptional() throws Exception{
        for(String file:List.of("MealBashMixin.java","MealExplosionMixin.java","MealReloadContinuityMixin.java","GunPackSyncMixin.java","SoundResourceCacheMixin.java","EliteScaleMixin.java")){
            String m=mixin(file);int hooks=0;for(String a:List.of("@Inject(","@Redirect(","@ModifyVariable("))hooks+=m.split(java.util.regex.Pattern.quote(a),-1).length-1;
            assertTrue(hooks>0,file);assertEquals(hooks,m.split("require=0",-1).length-1,file+": every hook may miss");
        }
        assertTrue(mixin("MealReloadContinuityMixin.java").contains("backend<0?data.reloadTimestamp:backend"),"without the backend's field TaCZ's own reload timing is kept");
    }
}
